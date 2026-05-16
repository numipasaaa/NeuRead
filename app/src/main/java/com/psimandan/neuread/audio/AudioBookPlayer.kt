package com.psimandan.neuread.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.psimandan.extensions.formatSecondsToHMS
import com.psimandan.neuread.PlaybackSource
import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.ui.player.PlayerViewModel
import com.psimandan.neuread.ui.player.TextTimeRelationsTools.getCurrentBookmarkText
import com.psimandan.neuread.ui.player.TextTimeRelationsTools.getCurrentWordIndex
import com.psimandan.neuread.voice.SpeakingCallBack
import timber.log.Timber
import androidx.core.net.toUri

class AudioBookPlayer(
    context: Context,
    private var speakingCallback: SpeakingCallBack
) : BookPlayer {
    companion object {
        const val SEEK_STEP_AUDIO = 30
    }

    private var mediaPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isPlaying = false

    private var nextPartStartTime = 0
    private var currentStartTime = 0
    private var frame: List<String> = emptyList()

    init {
        val book = speakingCallback.book as AudioBook
        mediaPlayer = ExoPlayer.Builder(context).build().apply {
            val player = this
            val mediaItem = MediaItem.fromUri(book.audioFilePath.toUri())
            setMediaItem(mediaItem)
            prepare()
            pause()
            val startPosMs = book.lastPosition * 1000L
            seekTo(startPosMs)
            playbackParameters = PlaybackParameters(book.voiceRate)

            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    Timber.d("onMediaItemTransition reason=$reason")
                }

                override fun onPlaybackStateChanged(state: Int) {
                    Timber.d("onPlaybackStateChanged: $state")
                    when (state) {
                        Player.STATE_ENDED -> {
                            this@AudioBookPlayer.isPlaying = false
                            speakingCallback.onCompleted()
                        }

                        Player.STATE_READY -> {
                            fun sendReady() {
                                val viewState = book.viewState.value
                                val totalSecs = viewState.totalTimeSeconds
                                val currentPositionMs = player.currentPosition
                                val elapsedSeconds = currentPositionMs / 1000.0
                                val elapsedTimeToShow = elapsedSeconds.formatSecondsToHMS()

                                val textFrame = book.getCurrentText(elapsedMilliseconds = currentPositionMs.toDouble())
                                val words = textFrame.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                                frame = words
                                nextPartStartTime = textFrame.nextStartTime ?: (textFrame.startTimeMms + 30_000)
                                currentStartTime = textFrame.startTimeMms

                                val currentWordIndexInFrame = getCurrentWordIndex(
                                    currentPositionMs,
                                    words,
                                    currentStartTime,
                                    nextPartStartTime
                                )

                                speakingCallback.onReady(uiState = PlayerViewModel.PlayerUIState(
                                    progress = elapsedSeconds.toFloat(),
                                    totalTimeString = viewState.totalTime,
                                    isLoading = false,
                                    isSpeaking = isPlaying,
                                    progressTime = elapsedTimeToShow,
                                    sliderRange = 0f..totalSecs.toFloat(),
                                    totalTime = totalSecs.toDouble(),
                                    bookmarks = book.bookmarks.map {
                                        it.title = titleForAudioBookmark(book, it.position); it
                                    },
                                    chapters = book.chapters
                                ))

                                speakingCallback.onUpdateHighlightingUI(
                                    PlayerViewModel.HighlightingUIState(
                                        currentWordIndexInFrame = currentWordIndexInFrame,
                                        currentFrame = words
                                    )
                                )

                                if (playOnReady) {
                                    playOnReady = false
                                    onPlay(source = PlaybackSource.AUTO_PLAY)
                                }
                            }

                            if (book.viewState.value.totalTimeSeconds == 0L) {
                                book.lazyCalculate { sendReady() }
                            } else {
                                sendReady()
                            }
                        }

                        Player.STATE_BUFFERING -> {

                        }

                        Player.STATE_IDLE -> {

                        }
                    }
                }
            })
        }
    }

    override fun onJumpToChapter(position: Int) {
        onUserChangePosition(position.toFloat())
    }

    override fun onPlay(source: PlaybackSource) {

        mediaPlayer?.apply {
                play()
                this@AudioBookPlayer.isPlaying = true
                speakingCallback.onStart()
                startProgressUpdates()
        } ?: Timber.e("onPlay() called but mediaPlayer is null")
    }

    fun duration(): Float {
        return mediaPlayer?.duration?.toFloat() ?: 0f
    }

    override fun onStopSpeaking() {
        handler.removeCallbacksAndMessages(null) // Always clear pending progress callbacks
        mediaPlayer?.takeIf { this@AudioBookPlayer.isPlaying }?.apply {
            pause()
            this@AudioBookPlayer.isPlaying = false
            speakingCallback.onStop()
        }
    }

    override fun onClose() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
    }

    override fun onUpdateSpeechRate(rate: Float) {
        mediaPlayer?.playbackParameters = PlaybackParameters(rate)
    }

    override fun onFastForward() {
        mediaPlayer?.apply {
            pause()
            playOnReady = true
            val newPosition = (currentPosition + (SEEK_STEP_AUDIO * 1000L)).coerceAtMost(duration)
            Timber.d("fastForward: $newPosition")
            seekTo(newPosition)
        }
    }

    override fun onRewind() {
        mediaPlayer?.apply {
            pause()
            playOnReady = true
            val newPosition = (currentPosition - (SEEK_STEP_AUDIO * 1000L)).coerceAtLeast(0L)
            seekTo(newPosition)
        }
    }

    override fun onUserChangePosition(value: Float) {
        mediaPlayer?.apply {
            val wasPlaying = isPlaying
            pause()
            playOnReady = wasPlaying
            val newPosition = (value.toInt() * 1000L).coerceAtMost(duration)
            seekTo(newPosition)
        }
    }

    private var playOnReady = false
    override fun onPlayFromBookmark(position: Int) {
        frame = emptyList()
        onStopSpeaking()
        mediaPlayer?.apply {
            pause()
            playOnReady = true
            seekTo((position * 1000L).coerceAtMost(mediaPlayer?.duration ?: 0L))
        }
    }

    override fun updateCallback(callback: SpeakingCallBack) {
        this.speakingCallback = callback
        val book = speakingCallback.book as? AudioBook ?: return

        fun sendReady() {
            val currentPos = mediaPlayer?.currentPosition ?: (book.lastPosition * 1000L)
            val elapsedSeconds = currentPos / 1000.0

            val textFrame = book.getCurrentText(elapsedMilliseconds = currentPos.toDouble())
            val wordsInFrame = textFrame.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val nextStart = textFrame.nextStartTime ?: (textFrame.startTimeMms + 30_000)
            val currentStart = textFrame.startTimeMms

            val currentWordIndexInFrame = getCurrentWordIndex(
                currentPos,
                wordsInFrame,
                currentStart,
                nextStart
            )

            val viewState = book.viewState.value
            val totalSecs = viewState.totalTimeSeconds

            val pState = PlayerViewModel.PlayerUIState(
                progress = elapsedSeconds.toFloat(),
                totalTimeString = viewState.totalTime,
                isLoading = false,
                isSpeaking = isPlaying,
                progressTime = elapsedSeconds.formatSecondsToHMS(),
                sliderRange = 0f..totalSecs.toFloat(),
                totalTime = totalSecs.toDouble(),
                bookmarks = book.bookmarks.map {
                    it.title = titleForAudioBookmark(book, it.position); it
                },
                chapters = book.chapters
            )
            speakingCallback.onReady(uiState = pState)

            speakingCallback.onUpdateHighlightingUI(
                PlayerViewModel.HighlightingUIState(
                    currentWordIndexInFrame = currentWordIndexInFrame,
                    currentFrame = wordsInFrame
                )
            )
        }

        if (book.viewState.value.totalTimeSeconds == 0L) {
            book.lazyCalculate { sendReady() }
        } else {
            sendReady()
        }
    }

    override fun onDeleteBookmark(bookmark: Bookmark) {
        val book = speakingCallback.book as AudioBook
        val updatedBookmarks = book.bookmarks.filter { it.position != bookmark.position }.toMutableList()
        book.bookmarks.clear()
        book.bookmarks.addAll(updatedBookmarks)

        speakingCallback.onUpdateUI(
            speakingCallback.viewState.value.copy(
                bookmarks = updatedBookmarks.map {
                    it.title = titleForAudioBookmark(book, it.position); it
                }
            )
        )
        speakingCallback.onProgressUpdate(
            updatedBook = book,
            pUIState = speakingCallback.viewState.value,
            hUIState = speakingCallback.highlightingState.value
        )
    }

    override fun onUpdateBookmarkNote(bookmark: Bookmark, note: String) {
        val book = speakingCallback.book as AudioBook
        book.bookmarks.find { it.position == bookmark.position }?.note = note

        speakingCallback.onUpdateUI(
            speakingCallback.viewState.value.copy(
                bookmarks = book.bookmarks.map {
                    it.title = titleForAudioBookmark(book, it.position); it
                }
            )
        )
        speakingCallback.onProgressUpdate(
            updatedBook = book,
            pUIState = speakingCallback.viewState.value,
            hUIState = speakingCallback.highlightingState.value
        )
    }

    override fun onSaveBookmark() {
        mediaPlayer?.let { player ->
            val book = speakingCallback.book as AudioBook
            val elapsedSeconds = player.currentPosition / 1000
            val b = Bookmark(elapsedSeconds.toInt())
            b.title = titleForAudioBookmark(book, elapsedSeconds.toInt())
            book.bookmarks.add(b)
            speakingCallback.onUpdateUI(speakingCallback.viewState.value.copy(
                bookmarks = book.bookmarks
            ))
            speakingCallback.onProgressUpdate(
                updatedBook = book,
                pUIState = speakingCallback.viewState.value,
                hUIState = speakingCallback.highlightingState.value
            )
        }

    }

    override fun currentTimeElapsed(): Long {
        mediaPlayer?.let {
            return it.currentPosition
        } ?: return 0
    }

    override fun isLoading(): Boolean {
        return mediaPlayer?.isLoading ?: false
    }

    private fun startProgressUpdates() {
        handler.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let { player ->
                    if (this@AudioBookPlayer.isPlaying) {
                        val book = speakingCallback.book as AudioBook

                        val currentPositionMs = player.currentPosition
                        val elapsedSeconds = currentPositionMs / 1000.0
                        val elapsedTimeToShow = elapsedSeconds.formatSecondsToHMS()

                        val textFrame = book.getCurrentText(elapsedMilliseconds = currentPositionMs.toDouble())
                        var hState = speakingCallback.highlightingState.value
                        
                        if (frame.isEmpty() || textFrame.startTimeMms != currentStartTime) {
                            val words = textFrame.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                            frame = words
                            nextPartStartTime = textFrame.nextStartTime ?: player.duration.toInt()
                            currentStartTime = textFrame.startTimeMms
                            hState = hState.copy(currentFrame = words)
                        }
                        
                        val currentWordIndexInFrame = getCurrentWordIndex(
                            currentPositionMs,
                            frame,
                            currentStartTime,
                            nextPartStartTime
                        )
                        
                        speakingCallback.onProgressUpdate(
                            updatedBook = book.copy(
                                lastPosition = elapsedSeconds.toInt(),
                                updated = System.currentTimeMillis()
                            ),
                            speakingCallback.viewState.value.copy(
                                progress = elapsedSeconds.toFloat(),
                                progressTime = elapsedTimeToShow,
                                isSpeaking = isPlaying,
                                isLoading = false
                            ),
                            hState.copy(
                                currentWordIndexInFrame = currentWordIndexInFrame
                            )
                        )
                        handler.postDelayed(this, 30) // Update ~33 times per second
                    }
                }
            }
        })
    }

    private fun titleForAudioBookmark(book: AudioBook, position: Int): String {
        val elapsedSeconds = position.toDouble()
        val forElapsedTimeMilliseconds = (elapsedSeconds * 1000)
        val textFrame = book.getCurrentText(elapsedMilliseconds = forElapsedTimeMilliseconds)
        val bookmarkTitle = getCurrentBookmarkText(
            elapsedSeconds = elapsedSeconds,
            textFrame.text,
            textFrame.startTimeMms,
            textFrame.nextStartTime ?: (textFrame.startTimeMms + 30000),
            textFrame.nextText
        )

        val elapsedTimeToShow = elapsedSeconds.formatSecondsToHMS()
        return "$elapsedTimeToShow | $bookmarkTitle"
    }
}
