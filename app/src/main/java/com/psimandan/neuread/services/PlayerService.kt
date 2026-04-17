package com.psimandan.neuread.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import com.psimandan.neuread.R
import com.psimandan.neuread.data.repository.PlayerStateRepository
import com.psimandan.neuread.domain.usecase.BookmarkUseCase
import com.psimandan.neuread.domain.usecase.PlayerUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@AndroidEntryPoint
class PlayerService : Service() {

    @Inject
    lateinit var playerUseCase: PlayerUseCase

    @Inject
    lateinit var bookmarkUseCase: BookmarkUseCase

    @Inject
    lateinit var playerStateRepository: PlayerStateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        const val ACTION_PLAY = "com.psimandan.neuread.ACTION_PLAY"
        const val ACTION_PAUSE = "com.psimandan.neuread.ACTION_PAUSE"
        const val ACTION_FF = "com.psimandan.neuread.ACTION_FF"
        const val ACTION_FR = "com.psimandan.neuread.ACTION_FR"
        const val ACTION_FAVORITE = "com.psimandan.neuread.ACTION_FAVORITE"
        const val ACTION_SERVICE_STOP = "com.psimandan.neuread.ACTION_SERVICE_STOP"
        const val NOTIFICATION_ID = 1974
        const val CHANNEL_ID = "audio_channel"
    }

    private lateinit var mediaSession: MediaSessionCompat

    private fun initMediaSession(context: Context) {
        mediaSession = MediaSessionCompat(context, "TTSPlaybackSession").apply {
            setCallback(mediaSessionCallback)
            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                                PlaybackStateCompat.ACTION_PAUSE or
                                PlaybackStateCompat.ACTION_STOP or
                                PlaybackStateCompat.ACTION_FAST_FORWARD or
                                PlaybackStateCompat.ACTION_REWIND or
                                PlaybackStateCompat.ACTION_SEEK_TO
                    )
                    .setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        0,
                        1f
                    )
                    .build()
            )
            isActive = true
        }
    }


    override fun onCreate() {
        super.onCreate()
        initMediaSession(this)
        createNotificationChannel()

        // Start with a basic notification to satisfy Android 14+ foreground service requirements
        startPlaceholderForeground()

        // Observe state changes instead of callback
        serviceScope.launch {
            playerStateRepository.getPlaybackState().collect { state ->
                updatePlaybackState(state.position, state.duration, state.isPlaying)
                updateNotification(state.isPlaying)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Ensure foreground starts immediately on every startCommand to prevent ANR/Crash on Android 12+
        startPlaceholderForeground()

        when (intent?.action) {
            ACTION_PLAY -> mediaSessionCallback.onPlay()
            ACTION_PAUSE -> mediaSessionCallback.onPause()
            ACTION_FF -> mediaSessionCallback.onFastForward()
            ACTION_FR -> mediaSessionCallback.onRewind()
            ACTION_FAVORITE -> {
                mediaSessionCallback.onCommand(command = null, extras = null, cb = null)
            }

            ACTION_SERVICE_STOP -> stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            Timber.d("PlayerService.onPlay()")
            serviceScope.launch {
                playerUseCase.play()
            }
        }

        override fun onPause() {
            Timber.d("PlayerService.onPause()")
            serviceScope.launch {
                playerUseCase.pause()
            }
        }

        override fun onFastForward() {
            Timber.d("PlayerService.onFastForward()")
            serviceScope.launch {
                playerUseCase.fastForward()
            }
        }

        override fun onRewind() {
            Timber.d("PlayerService.onRewind()")
            serviceScope.launch {
                playerUseCase.fastRewind()
            }
        }

        override fun onCommand(command: String?, extras: Bundle?, cb: ResultReceiver?) {
            super.onCommand(command, extras, cb)
            Timber.d("onCommand()=>${command}")
            serviceScope.launch {
                bookmarkUseCase.saveBookmark()
            }
        }

    }

    private fun updatePlaybackState(position: Long, duration: Long, isPlaying: Boolean) {
        val state =
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
//        Timber.d("updatePlaybackState()=>$position of $duration")
        val durationMsc = duration * 1000
        val positionMsc = position * 1000
        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, positionMsc, 1f) // Position updates
                .setBufferedPosition(durationMsc)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                            PlaybackStateCompat.ACTION_PAUSE or
                            PlaybackStateCompat.ACTION_STOP or
                            PlaybackStateCompat.ACTION_FAST_FORWARD or
                            PlaybackStateCompat.ACTION_REWIND or
                            PlaybackStateCompat.ACTION_SEEK_TO or
                            PlaybackStateCompat.ACTION_SET_RATING // Might be useful for favorite
                )
                .build()
        )

        mediaSession.setExtras(Bundle().apply {
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMsc)
        })
        // Get book info from repository instead of ViewModel
        serviceScope.launch {
            val book = playerStateRepository.getCurrentBook().first()

            mediaSession.setMetadata(
                MediaMetadataCompat.Builder()
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMsc)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, book?.title ?: "Unknown")
                    .putString(MediaMetadataCompat.METADATA_KEY_AUTHOR, book?.author ?: "Unknown")
                    .build()
            )
        }

        mediaSession.isActive = true // Ensure it's active!
    }

    private fun updateNotification(isPlaying: Boolean = true) {
        serviceScope.launch {
            val book = playerStateRepository.getCurrentBook().first()
            val playPauseAction = if (isPlaying) {
                NotificationCompat.Action(R.drawable.ic_pause, "Pause", getServiceIntent(ACTION_PAUSE, 1))
            } else {
                NotificationCompat.Action(R.drawable.ic_play, "Play", getServiceIntent(ACTION_PLAY, 0))
            }

            val ffAction = NotificationCompat.Action(R.drawable.ic_ff, "Fast Forward", getServiceIntent(ACTION_FF, 2))
            val frAction = NotificationCompat.Action(R.drawable.ic_fr, "Rewind", getServiceIntent(ACTION_FR, 3))

            val builder = NotificationCompat.Builder(this@PlayerService, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(book?.title ?: "Unknown")
                .setContentText(book?.author ?: "Unknown")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(isPlaying)
                .setStyle(
                    androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSession.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
                .addAction(frAction)
                .addAction(playPauseAction)
                .addAction(ffAction)

            if (isPlaying) {
                startForegroundCompat(NOTIFICATION_ID, builder.build())
            } else {
                stopForeground(STOP_FOREGROUND_DETACH)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, builder.build())
            }
        }
    }

    private fun startPlaceholderForeground() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(CHANNEL_ID)
            if (channel == null) {
                createNotificationChannel()
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Preparing playback...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)

        startForegroundCompat(NOTIFICATION_ID, builder.build())
    }

    private fun startForegroundCompat(notificationId: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun getServiceIntent(action: String, requestCode: Int): PendingIntent {
        return PendingIntent.getService(
            this, requestCode, Intent(this, PlayerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}
