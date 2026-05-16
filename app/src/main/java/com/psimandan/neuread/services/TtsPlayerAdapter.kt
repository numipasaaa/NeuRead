package com.psimandan.neuread.services

import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import com.psimandan.neuread.domain.usecase.PlayerUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@UnstableApi
class TtsPlayerAdapter(
    private val playerUseCase: PlayerUseCase,
    private val scope: CoroutineScope
) : BasePlayer() {

    private val listeners = mutableListOf<Player.Listener>()
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var currentMediaItem: MediaItem? = null
    private var currentDuration: Long = C.TIME_UNSET

    init {
        scope.launch {
            playerUseCase.getPlaybackState().collect { state ->
                val newPlayWhenReady = state.isPlaying
                val newDuration = if (state.duration > 0) state.duration * 1000L else C.TIME_UNSET
                
                // If we have a media item, we should at least be in READY state to show notification
                val newState = if (currentMediaItem != null) Player.STATE_READY else Player.STATE_IDLE
                
                var stateChanged = false
                if (playWhenReady != newPlayWhenReady) {
                    playWhenReady = newPlayWhenReady
                    listeners.forEach { it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
                    listeners.forEach { it.onIsPlayingChanged(playWhenReady) }
                    stateChanged = true
                }
                if (playbackState != newState) {
                    playbackState = newState
                    listeners.forEach { it.onPlaybackStateChanged(playbackState) }
                    stateChanged = true
                }
                
                if (currentDuration != newDuration) {
                    currentDuration = newDuration
                    listeners.forEach { it.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) }
                    stateChanged = true
                }
                
                // If we are playing, periodically notify to ensure the session polls getCurrentPosition
                if (stateChanged || playWhenReady) {
                    val positionMs = getCurrentPosition()
                    val positionInfo = Player.PositionInfo(
                        null, // windowUid
                        0,    // mediaItemIndex
                        currentMediaItem,
                        null, // periodUid
                        0,    // periodIndex
                        positionMs,
                        positionMs, // contentPositionMs
                        -1,   // adGroupIndex
                        -1    // adIndexInAdGroup
                    )
                    listeners.forEach { it.onPositionDiscontinuity(
                        positionInfo,
                        positionInfo,
                        Player.DISCONTINUITY_REASON_INTERNAL
                    ) }
                    listeners.forEach { it.onAvailableCommandsChanged(getAvailableCommands()) }
                }
            }
        }
    }

    override fun addListener(listener: Player.Listener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        listeners.remove(listener)
    }

    override fun getApplicationLooper(): Looper = Looper.getMainLooper()

    override fun prepare() {}

    override fun stop() {
        scope.launch { playerUseCase.pause() }
    }

    override fun release() {
        listeners.clear()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (this.playWhenReady == playWhenReady) return
        this.playWhenReady = playWhenReady
        scope.launch {
            if (playWhenReady) playerUseCase.play() else playerUseCase.pause()
        }
        listeners.forEach { it.onPlayWhenReadyChanged(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) }
        listeners.forEach { it.onIsPlayingChanged(playWhenReady) }
    }

    override fun getPlayWhenReady(): Boolean = playWhenReady

    override fun getPlaybackState(): Int = playbackState

    override fun getPlayerError(): PlaybackException? = null

    override fun seekTo(mediaItemIndex: Int, positionMs: Long, seekCommand: Int, isAutoTransition: Boolean) {
        val currentPos = getCurrentPosition()
        val duration = getDuration()
        
        var targetPosMs = positionMs
        
        when (seekCommand) {
            Player.COMMAND_SEEK_FORWARD -> targetPosMs = currentPos + getSeekForwardIncrement()
            Player.COMMAND_SEEK_BACK -> targetPosMs = currentPos - getSeekBackIncrement()
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> targetPosMs = currentPos + getSeekForwardIncrement()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> targetPosMs = currentPos - getSeekBackIncrement()
            else -> {
                if (targetPosMs == C.TIME_UNSET) targetPosMs = currentPos
            }
        }
        
        if (targetPosMs < 0) targetPosMs = 0
        if (duration != C.TIME_UNSET && targetPosMs > duration) targetPosMs = duration
        
        scope.launch {
            playerUseCase.seekTo(targetPosMs / 1000)
        }
    }

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}

    override fun getCurrentPosition(): Long = playerUseCase.getCurrentTimeElapsed()

    override fun getDuration(): Long = if (currentDuration != C.TIME_UNSET) currentDuration else C.TIME_UNSET

    override fun getBufferedPosition(): Long = getCurrentPosition()

    override fun getTotalBufferedDuration(): Long = 0

    override fun isPlayingAd(): Boolean = false

    override fun getCurrentAdGroupIndex(): Int = -1

    override fun getCurrentAdIndexInAdGroup(): Int = -1

    override fun getContentPosition(): Long = getCurrentPosition()

    override fun getContentBufferedPosition(): Long = getBufferedPosition()

    override fun getCurrentTimeline(): Timeline {
        if (currentMediaItem == null) return Timeline.EMPTY
        
        return object : Timeline() {
            override fun getWindowCount(): Int = 1
            override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
                val durationUs = if (currentDuration != C.TIME_UNSET) currentDuration * 1000L else C.TIME_UNSET
                window.set(
                    Any(),
                    currentMediaItem,
                    null,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    C.TIME_UNSET,
                    true,
                    false,
                    null,
                    0,
                    durationUs,
                    0,
                    0,
                    0
                )
                return window
            }
            override fun getPeriodCount(): Int = 1
            override fun getPeriod(periodIndex: Int, period: Period, setIdentifiers: Boolean): Period {
                val durationUs = if (currentDuration != C.TIME_UNSET) currentDuration * 1000L else C.TIME_UNSET
                period.set(Any(), Any(), 0, durationUs, 0)
                return period
            }
            override fun getIndexOfPeriod(uid: Any): Int = if (uid == Any()) 0 else -1
            override fun getUidOfPeriod(periodIndex: Int): Any = Any()
        }
    }

    override fun getCurrentPeriodIndex(): Int = 0

    override fun getCurrentMediaItemIndex(): Int = 0

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (mediaItems.isNotEmpty()) {
            currentMediaItem = mediaItems[0]
            playbackState = Player.STATE_READY
            listeners.forEach { it.onMediaItemTransition(currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) }
            listeners.forEach { it.onPlaybackStateChanged(playbackState) }
            listeners.forEach { it.onTimelineChanged(getCurrentTimeline(), Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) }
            listeners.forEach { it.onAvailableCommandsChanged(getAvailableCommands()) }
        }
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        setMediaItems(mediaItems, false)
    }

    override fun getAvailableCommands(): Player.Commands = Player.Commands.Builder()
        .addAll(
            COMMAND_PLAY_PAUSE,
            COMMAND_STOP,
            COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            COMMAND_SEEK_TO_NEXT,
            COMMAND_SEEK_TO_PREVIOUS,
            COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            COMMAND_GET_METADATA,
            COMMAND_GET_CURRENT_MEDIA_ITEM,
            COMMAND_SET_MEDIA_ITEM,
            COMMAND_GET_TIMELINE,
            COMMAND_GET_AUDIO_ATTRIBUTES,
            COMMAND_SEEK_FORWARD,
            COMMAND_SEEK_BACK,
            COMMAND_GET_TRACKS
        ).build()

    override fun getCurrentTracks(): Tracks = Tracks.EMPTY
    override fun getTrackSelectionParameters(): TrackSelectionParameters = TrackSelectionParameters.DEFAULT
    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
    override fun getMediaMetadata(): MediaMetadata = currentMediaItem?.mediaMetadata ?: MediaMetadata.EMPTY
    override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
    override fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN
    override fun getDeviceVolume(): Int = 0
    override fun isDeviceMuted(): Boolean = false
    override fun setDeviceVolume(volume: Int) {}
    override fun setDeviceMuted(muted: Boolean) {}
    override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
    override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN

    override fun setDeviceVolume(volume: Int, flags: Int) {}
    override fun setDeviceMuted(muted: Boolean, flags: Int) {}
    override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {}
    override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {}
    override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    override fun setRepeatMode(repeatMode: Int) {}
    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF
    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
    override fun getShuffleModeEnabled(): Boolean = false
    override fun isLoading(): Boolean = playerUseCase.isLoading()
    override fun getSeekBackIncrement(): Long = 10000L
    override fun getSeekForwardIncrement(): Long = 10000L
    override fun getMaxSeekToPreviousPosition(): Long = 3000L
    override fun setVolume(volume: Float) {}
    override fun getVolume(): Float = 1f
    override fun clearVideoSurface() {}
    override fun clearVideoSurface(surface: Surface?) {}
    override fun setVideoSurface(surface: Surface?) {}
    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {}
    override fun setVideoTextureView(textureView: TextureView?) {}
    override fun clearVideoTextureView(textureView: TextureView?) {}
    override fun getSurfaceSize(): Size = Size.UNKNOWN
    override fun increaseDeviceVolume() {}
    override fun increaseDeviceVolume(flags: Int) {}
    override fun decreaseDeviceVolume() {}
    override fun decreaseDeviceVolume(flags: Int) {}
    override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
}
