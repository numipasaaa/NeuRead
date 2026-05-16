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
    fun getSpeechRate(): Flow<Float?>
    fun updateSpeechRate(rate: Float)
}

data class PlaybackState(
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val speed: Float = 1.0f,
    val formattedProgressTime: String = "00:00" // ADDED THIS LINE
)