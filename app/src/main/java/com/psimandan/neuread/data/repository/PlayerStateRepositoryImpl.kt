package com.psimandan.neuread.data.repository

import com.psimandan.neuread.data.model.NeuReadBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerStateRepositoryImpl @Inject constructor() : PlayerStateRepository {
    private val _currentBook = MutableStateFlow<NeuReadBook?>(null)
    private val _playbackState = MutableStateFlow(PlaybackState())
    private val _currentPosition = MutableStateFlow(0L)
    
    override fun getCurrentBook(): Flow<NeuReadBook?> = _currentBook.asStateFlow()
    override fun getPlaybackState(): Flow<PlaybackState> = _playbackState.asStateFlow()
    override fun getCurrentPosition(): Flow<Long> = _currentPosition.asStateFlow()
    
    override suspend fun updatePlaybackState(state: PlaybackState) {
        _playbackState.value = state
    }
    
    override suspend fun updateCurrentPosition(position: Long) {
        _currentPosition.value = position
    }
    
    override suspend fun setCurrentBook(book: NeuReadBook?) {
        _currentBook.value = book
    }
}