package com.psimandan.neuread.ui.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.audio.AudioBookPlayer
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.data.model.Chapter
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.data.repository.PlayerStateRepository
import com.psimandan.neuread.data.repository.VoiceRepository
import com.psimandan.neuread.domain.usecase.BookmarkUseCase
import com.psimandan.neuread.domain.usecase.PlayerUseCase
import com.psimandan.neuread.services.PlayerService
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.voice.SpeakingCallBack
import com.psimandan.neuread.voice.SpeechBookPlayer
import com.psimandan.neuread.voice.toVoice
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

object TextTimeRelationsTools {
    fun getCurrentWordIndex(
        currentPositionMs: Long,
        words: List<String>,
        currentStartTime: Int,
        nextStartTime: Int
    ): Int {
        val durationBetweenParts = (nextStartTime - currentStartTime).toDouble()

        if (words.isEmpty() || durationBetweenParts <= 0) return 0

        val relativeTimeInSegment = currentPositionMs - currentStartTime
        if (relativeTimeInSegment <= 0) return 0
        if (relativeTimeInSegment >= durationBetweenParts) return words.size - 1

        // Use character-weighted estimation for better accuracy
        val totalChars = words.sumOf { it.length } + (words.size - 1)
        val timePerChar = durationBetweenParts / totalChars
        val targetCharIndex = (relativeTimeInSegment / timePerChar).toInt()

        var currentCharCount = 0
        for (i in words.indices) {
            currentCharCount += words[i].length
            if (targetCharIndex < currentCharCount) return i
            currentCharCount += 1 // space
            if (targetCharIndex < currentCharCount) return i
        }

        return (words.size - 1).coerceAtLeast(0)
    }

    fun getCurrentBookmarkText(
        elapsedSeconds: Double,
        currentText: String,
        currentStartTime: Int,
        nextStartTime: Int,
        nextText: String?,
    ): String {
        // Calculate duration between text parts in seconds
        val durationBetweenPartsMs = (nextStartTime - currentStartTime)

        // Split text into words, removing empty ones
        val words = currentText.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val nextWords = nextText?.split(Regex("\\s+"))?.filter { it.isNotEmpty() } ?: emptyList()

        // Prevent division by zero if words list is empty
        if (words.isEmpty() || durationBetweenPartsMs <= 0) return ""

        // Approximate time per word
        val millisecondsPerWord = durationBetweenPartsMs / words.size

        // Calculate relative elapsed time within the current segment
        val relativeTimeInSegment = (elapsedSeconds * 1000.0) - currentStartTime

        // Compute the word index, ensuring it's within valid bounds
        val wordIndex =
            relativeTimeInSegment.div(millisecondsPerWord).toInt().coerceIn(0, words.size - 1)

        // Bookmark offsets (Replace with actual values from TextToSpeechPlayer)
        val bookmarkOffset = 5
        val bookmarkTextLength = 30
        val extendedText = words + nextWords
        // Determine start and end indices for bookmark text
        val startIndex = (wordIndex - bookmarkOffset).coerceAtLeast(0)
        val endIndex = (startIndex + bookmarkTextLength).coerceAtMost(extendedText.size)

        // Join words into a substring for bookmark preview
        return extendedText.subList(startIndex, endIndex).joinToString(" ")
    }
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val repository: VoiceRepository,
    private val libraryRepository: LibraryRepository,
    private val playerUseCase: PlayerUseCase,
    private val bookmarkUseCase: BookmarkUseCase,
    private val playerStateRepository: PlayerStateRepository,
    private val prefsStore: PrefsStore
) : ViewModel(), SpeakingCallBack {

    override val book: NeuReadBook? get() = _state.value.book
    private var player: BookPlayer? = null

    fun saveBookChanges() {
        viewModelScope.launch {
            book?.let {
                libraryRepository.updateBook(it)
            }
        }
    }

    data class PlayerUIState(
        val book: NeuReadBook? = null,
        val totalTime: Double = 0.0,
        val isSpeaking: Boolean = false,
        val isLoading: Boolean = false,
        val progress: Float = 0f,
        val progressTime: String = "00:00",
        val totalTimeString: String = "00:00",
        val bookmarks: List<Bookmark> = emptyList(),
        val sliderRange: ClosedFloatingPointRange<Float> = 0f..0f,
        val chapters: List<Chapter> = emptyList(),
        val isExtendedTextMode: Boolean = false,
        val isDyslexicFontEnabled: Boolean = false,
        val isHighlightingEnabled: Boolean = true,
        val sleepTimerSecondsRemaining: Int? = null
    )

    data class HighlightingUIState(
        val currentWordIndexInFrame: Int = 0, val currentFrame: List<String> = listOf()
    )


    private val _highlightingState = MutableStateFlow(HighlightingUIState())
    override val highlightingState: StateFlow<HighlightingUIState> get() = _highlightingState.asStateFlow()

    override fun onUpdateHighlightingUI(state: HighlightingUIState) {
        _highlightingState.value = state
    }

    private val _state = MutableStateFlow(PlayerUIState())
    override val viewState: StateFlow<PlayerUIState> get() = _state.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _state.update { it.copy(sleepTimerSecondsRemaining = null) }
            return
        }

        val totalSeconds = minutes * 60
        _state.update { it.copy(sleepTimerSecondsRemaining = totalSeconds) }

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                kotlinx.coroutines.delay(1000)
                remaining--
                _state.update { it.copy(sleepTimerSecondsRemaining = remaining) }
            }
            onPause()
            _state.update { it.copy(sleepTimerSecondsRemaining = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _state.update { it.copy(sleepTimerSecondsRemaining = null) }
    }

    override fun onUpdateUI(state: PlayerUIState) {
        _state.value = state
    }

    fun setUpBook() {
        viewModelScope.launch {
            val existingPlayer = playerUseCase.getBookPlayer()
            val currentBookId = playerStateRepository.getCurrentBook().first()?.id
            val selectedBookId = libraryRepository.getSelectedBook()?.id
            
            if (existingPlayer != null && currentBookId != null && currentBookId == selectedBookId) {
                player = existingPlayer
                val existingBook = playerStateRepository.getCurrentBook().first()
                _state.update { it.copy(book = existingBook, isLoading = false) }
                
                // If player exists, we need to sync the UI state immediately
                // The player is already initialized and possibly playing
                player?.updateCallback(this@PlayerViewModel)
                return@launch
            }

            _highlightingState.value = HighlightingUIState()
            _state.value = PlayerUIState(isLoading = true)

            val selectedBook = withContext(Dispatchers.IO) {
                libraryRepository.getSelectedBook()
            }
            _state.update { it.copy(book = selectedBook, isLoading = true) }

            selectedBook?.let { book ->
                // Ensure voices are loaded in repository
                repository.fetchAvailableVoices()

                player = when (book) {
                    is Book -> {
                        val voice =
                            repository.nameToVoice(book.voiceIdentifier, book.language)
                        SpeechBookPlayer(
                            application,
                            voice = voice.toVoice(),
                            speakingCallback = this@PlayerViewModel,
                            prefsStore = prefsStore
                        )
                    }

                    is AudioBook -> AudioBookPlayer(
                        application,
                        speakingCallback = this@PlayerViewModel
                    )
                }

                // Set player in use cases
                player?.let { playerInstance ->
                    playerUseCase.setBookPlayer(playerInstance)
                    bookmarkUseCase.setBookPlayer(playerInstance)
                }

                // Update repository state
                playerStateRepository.setCurrentBook(book)

                _state.value = _state.value.copy(
                    chapters = when (book) {
                        is Book -> book.chapters
                        is AudioBook -> book.chapters
                    }
                )

                viewModelScope.launch {
                    prefsStore.isDyslexicFontEnabled().collect { enabled ->
                        _state.update { it.copy(isDyslexicFontEnabled = enabled) }
                    }
                }

                viewModelScope.launch {
                    prefsStore.isHighlightingEnabled().collect { enabled ->
                        _state.update { it.copy(isHighlightingEnabled = enabled) }
                    }
                }

                startPlaybackService()
            }
        }
    }


    fun jumpToChapter(chapter: Chapter) {
        player?.onJumpToChapter(chapter.startIndex)
    }

    fun toggleExtendedTextMode() {
        _state.update { it.copy(isExtendedTextMode = !it.isExtendedTextMode) }
    }


    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch {
            bookmarkUseCase.deleteBookmark(bookmark)
        }
    }

    fun playFromBookmark(position: Int) {
        viewModelScope.launch {
            bookmarkUseCase.playFromBookmark(position)
        }
    }

    fun onPlay() {
        viewModelScope.launch {
            playerUseCase.play()
            _state.update { it.copy(isSpeaking = true) }
        }
    }

    fun onPause() {
        viewModelScope.launch {
            playerUseCase.pause()
            _state.update { it.copy(isSpeaking = false) }
        }
    }

    fun onClose() {
        viewModelScope.launch {
            stopPlaybackService()
            player?.onClose()
        }
    }

    fun saveBookmark() {
        viewModelScope.launch {
            bookmarkUseCase.saveBookmark()
        }
    }

    fun fastForward() {
        viewModelScope.launch {
            playerUseCase.fastForward()
        }
    }

    fun fastRewind() {
        viewModelScope.launch {
            playerUseCase.fastRewind()
        }
    }


    fun updateBookmarkNote(bookmark: Bookmark, note: String) {
        viewModelScope.launch {
            bookmarkUseCase.updateBookmarkNote(bookmark, note)
        }
    }

    fun onSliderValueChange(value: Float) {
        viewModelScope.launch {
            playerUseCase.seekTo(value.toLong())
        }
    }

    override fun onCompleted() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSpeaking = false)
        }
    }

    override fun onStop() = resetSpeakingState()

    override fun onReady(uiState: PlayerUIState) {
        viewModelScope.launch {
            _state.value = uiState.copy(
                book = _state.value.book,
                isLoading = false,
                chapters = book?.chapters ?: emptyList(),
                isDyslexicFontEnabled = _state.value.isDyslexicFontEnabled,
                isHighlightingEnabled = _state.value.isHighlightingEnabled
            )
        }
    }

    override fun onStart() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSpeaking = true)

        }
    }

    override fun onProgressUpdate(
        updatedBook: NeuReadBook,
        pUIState: PlayerUIState,
        hUIState: HighlightingUIState
    ) {
        viewModelScope.launch {
            playerStateRepository.setCurrentBook(updatedBook)
            libraryRepository.updateBook(updatedBook)
        }
        _state.value = pUIState.copy(
            book = updatedBook,
            isLoading = false,
            isDyslexicFontEnabled = _state.value.isDyslexicFontEnabled,
            isHighlightingEnabled = _state.value.isHighlightingEnabled
        )
        val hState = _highlightingState.value
        _highlightingState.value = hUIState.copy(
            currentFrame = if (hUIState.currentFrame.isEmpty()) hState.currentFrame else hUIState.currentFrame
        )
        playbackProgressCallBack(
            _state.value.progress.toLong(),
            _state.value.totalTime.toLong(),
            _state.value.isSpeaking
        )
    }

    private fun resetSpeakingState() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSpeaking = false)
        }
    }

    private fun startPlaybackService() {
        val intent = Intent(application, PlayerService::class.java)
        ContextCompat.startForegroundService(application, intent)
    }

    private fun stopPlaybackService() {
        val intent = Intent(
            application, PlayerService::class.java
        ).setAction(PlayerService.ACTION_SERVICE_STOP)
        application.startService(intent)
    }

    var playbackProgressCallBack: (Long, Long, Boolean) -> Unit = { _, _, _ -> }

    fun currentTimeElapsed(): Long {
        return playerUseCase.getCurrentTimeElapsed()
    }
}
