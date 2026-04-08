package com.psimandan.neuread.data.repository

import com.psimandan.neuread.data.model.NeuReadBook
import kotlinx.coroutines.flow.Flow

interface PlayerStateRepository {
    fun getCurrentBook(): Flow<NeuReadBook?>
    fun getPlaybackState(): Flow<PlaybackState>
    fun getCurrentPosition(): Flow<Long>
    suspend fun updatePlaybackState(state: PlaybackState)
    suspend fun updateCurrentPosition(position: Long)
    suspend fun setCurrentBook(book: NeuReadBook?)
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val speed: Float = 1.0f
)