package com.psimandan.neuread.domain.usecase

import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.PlaybackSource
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.data.repository.PlaybackState
import com.psimandan.neuread.data.repository.PlayerStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerUseCaseImpl @Inject constructor(
    private val playerStateRepository: PlayerStateRepository,
    private val libraryRepository: LibraryRepository
) : PlayerUseCase {
    
    private var bookPlayer: BookPlayer? = null
    
    override suspend fun play() {
        bookPlayer?.onPlay(source = PlaybackSource.USER_ACTION)
        playerStateRepository.updatePlaybackState(
            playerStateRepository.getPlaybackState().first().copy(isPlaying = true)
        )
    }
    
    override suspend fun pause() {
        bookPlayer?.onStopSpeaking()
        playerStateRepository.updatePlaybackState(
            playerStateRepository.getPlaybackState().first().copy(isPlaying = false)
        )
    }
    
    override suspend fun fastForward() {
        bookPlayer?.onFastForward()
    }
    
    override suspend fun fastRewind() {
        bookPlayer?.onRewind()
    }
    
    override suspend fun seekTo(position: Long) {
        bookPlayer?.onUserChangePosition(position.toFloat())
    }
    
    override fun getCurrentPosition(): Flow<Long> = 
        playerStateRepository.getCurrentPosition()
    
    override fun getPlaybackState(): Flow<PlaybackState> = 
        playerStateRepository.getPlaybackState()
    
    override fun setBookPlayer(player: BookPlayer) {
        this.bookPlayer = player
    }

    override fun getBookPlayer(): BookPlayer? {
        return bookPlayer
    }

    override fun getCurrentTimeElapsed(): Long {
        return bookPlayer?.currentTimeElapsed() ?: 0
    }
}