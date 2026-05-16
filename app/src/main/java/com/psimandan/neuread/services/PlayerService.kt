package com.psimandan.neuread.services

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.psimandan.neuread.MainActivity
import com.psimandan.neuread.data.repository.PlayerStateRepository
import com.psimandan.neuread.domain.usecase.BookmarkUseCase
import com.psimandan.neuread.domain.usecase.PlayerUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlayerService : MediaSessionService() {

    companion object {
        const val ACTION_SERVICE_STOP = "com.psimandan.neuread.ACTION_SERVICE_STOP"
    }

    @Inject
    lateinit var playerUseCase: PlayerUseCase

    @Inject
    lateinit var bookmarkUseCase: BookmarkUseCase

    @Inject
    lateinit var playerStateRepository: PlayerStateRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var playerAdapter: TtsPlayerAdapter

    override fun onCreate() {
        super.onCreate()
        Timber.d("PlayerService: onCreate")
        playerAdapter = TtsPlayerAdapter(playerUseCase, serviceScope)
        
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 
            0, 
            intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, playerAdapter)
            .setSessionActivity(pendingIntent)
            .build()
        addSession(mediaSession!!)

        // Observe book changes to update metadata
        serviceScope.launch {
            playerStateRepository.getCurrentBook().collect { book ->
                book?.let {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist(it.author)
                        .build()
                    val mediaItem = MediaItem.Builder()
                        .setMediaMetadata(metadata)
                        .setMediaId(it.id)
                        .build()
                    playerAdapter.setMediaItem(mediaItem)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SERVICE_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null) {
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }
}
