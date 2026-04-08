package com.psimandan.neuread.domain.usecase

import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.data.repository.PlaybackState
import kotlinx.coroutines.flow.Flow

interface PlayerUseCase {
    suspend fun play()
    suspend fun pause()
    suspend fun fastForward()
    suspend fun fastRewind()
    suspend fun seekTo(position: Long)
    fun getCurrentPosition(): Flow<Long>
    fun getPlaybackState(): Flow<PlaybackState>
    fun setBookPlayer(player: BookPlayer)
    fun getCurrentTimeElapsed(): Long
}