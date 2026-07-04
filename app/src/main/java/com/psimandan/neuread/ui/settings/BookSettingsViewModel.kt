package com.psimandan.neuread.ui.settings

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.data.model.EBookFile
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.data.model.TextPart
import com.psimandan.neuread.voice.SimpleSpeakingCallBack
import com.psimandan.neuread.voice.SimpleSpeechProvider
import com.psimandan.neuread.voice.languageId
import com.psimandan.neuread.data.model.Chapter
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.psimandan.neuread.audio.DownloadAudioWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min

class LimitedDictionary(val limit: UInt) {
    var values: MutableList<String> = mutableListOf()

    fun push(value: String) {
        val currentValueIndex = values.indexOf(value)

        if (currentValueIndex != -1) {
            val tmp = values[0]
            values[0] = value
            values[currentValueIndex] = tmp
        } else if (values.size < limit.toInt()) {
            values.add(0, value)
        } else {
            values.removeAt(values.lastIndex)
            values.add(0, value)
        }
    }
}

@HiltViewModel
class BookSettingsViewModel @Inject constructor(
    @ApplicationContext private val application: Context,
    private val repository: LibraryRepository,
    private val prefsStore: PrefsStore
) : ViewModel() {

    private var textToSpeech: SimpleSpeechProvider? = null
    private var mediaPlayer: MediaPlayer? = null
    private var audioVoiceChangedSinceLastSave: Boolean = false
    val recentSelectionsL: LimitedDictionary = LimitedDictionary(limit = 5U)

    data class BookUIState(
        val book: NeuReadBook? = null,
        val title: String = "",
        val author: String = "",
        val language: String = "",
        val voiceIdentifier: String = "",
        val voiceRate: Float = 1.0f,
        val text: List<String> = emptyList(),
        val audioPath: String = "",
        val parts: List<TextPart> = emptyList(),
        val voice: String = "",
        val model: String = "",
        val bookSource: String = ""
    )

    data class SettingsUIState(
        val newBook: Boolean = false,
        val loading: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val isSpeaking: Boolean = false,
        val selectedPage: Int = 0,
        val showVoiceError: Boolean = false,
        val downloadProgress: Float? = null,
        val dyslexicFontEnabled: Boolean = false,
        val highlightingEnabled: Boolean = true,
        val isEditingText: Boolean = false,
        val editingText: String = ""
    )

    private val _state = MutableStateFlow(BookUIState())
    val bookState: StateFlow<BookUIState> get() = _state.asStateFlow()

    private val _viewState = MutableStateFlow(SettingsUIState())
    val viewState: StateFlow<SettingsUIState> get() = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.libraryChanges.collect {
                // If the currently displayed book matches an update, refresh it
                val currentBookId = _state.value.book?.id
                val updatedSelectedBook = repository.getSelectedBook()
                if (updatedSelectedBook?.id == currentBookId) {
                    Timber.d("BookSettingsViewModel: Library change detected for current book, refreshing state")
                    setUpBook()
                }
            }
        }
    }

    fun playTextSample(language: Locale, voice: Voice?, rate: Float) {
        voice?.let {
            // Stop book audio if playing
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
            mediaPlayer = null

            val sampleText = currentPage().substring(0, min(currentPage().length, 100))

            if (textToSpeech == null) {
                textToSpeech = SimpleSpeechProvider(
                    application,
                    currentLocale = language,
                    currentVoice = voice,
                    speechRate = rate,
                    speakingCallBack = object : SimpleSpeakingCallBack {
                        override fun onError(utteranceId: String?, errorCode: Int) {
                            Timber.d("textToSpeech=>1 onError=>$errorCode")
                            if (errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT ||
                                errorCode == TextToSpeech.ERROR_NETWORK ||
                                errorCode == TextToSpeech.ERROR_SERVICE
                            ) {
                                Timber.e("Network error1, retrying with offline voice...")
                                viewModelScope.launch {
                                    _viewState.emit(_viewState.value.copy(showVoiceError = true))
                                }
                            }
                        }
                    },
                    prefsStore = prefsStore
                )
            }
            if (textToSpeech?.isSpeaking() == true) {
                textToSpeech?.stop()
            } else {
                textToSpeech?.updateLocale(language, voice, rate)
                textToSpeech?.speak(sampleText)
            }
        } ?: run {
            Toast.makeText(application, "Please select a voice first!", Toast.LENGTH_SHORT).show()
        }
    }

    fun playAudioSample() {
        if (bookState.value.book is AudioBook) {
            val book = (bookState.value.book as AudioBook)
            
            // Stop voice sample if playing
            textToSpeech?.stop()

            if (mediaPlayer == null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(book.audioFilePath)
                    prepare()
                }
            }

            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.apply {
                    stop()
                    release()
                }
                mediaPlayer = null
            } else {
                mediaPlayer?.apply {
                    playbackParams = playbackParams.setSpeed(bookState.value.voiceRate)
                    start()
                }
            }
        }
    }

    fun dismissVoiceError() {
        viewModelScope.launch {
            _viewState.emit(_viewState.value.copy(showVoiceError = false))
        }
    }

    fun downloadAudio() {
        val book = bookState.value.book ?: return
        val voiceName = bookState.value.voiceIdentifier.ifBlank {
            when (book) {
                is Book -> book.voiceIdentifier
                is AudioBook -> book.voice
            }
        }

        val textParts = when (book) {
            is Book -> book.text
            is AudioBook -> book.parts.map { it.text }
        }
        if (textParts.isEmpty()) return

        // Immediate UI feedback
        _viewState.update { it.copy(downloadProgress = 0f) }
        
        val workManager = WorkManager.getInstance(application)
        val workRequest = OneTimeWorkRequestBuilder<DownloadAudioWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(
                Data.Builder()
                    .putString("bookId", book.id)
                    .putString("bookTitle", book.title)
                    .putString("voiceName", voiceName)
                    .build()
            )
            .addTag("download_${book.id}")
            .build()
            
        workManager.enqueue(workRequest)
        trackActiveDownload(book.id)
    }

    fun cancelDownload() {
        val book = bookState.value.book as? Book ?: return
        val workManager = WorkManager.getInstance(application)
        workManager.cancelAllWorkByTag("download_${book.id}")
        _viewState.update { it.copy(downloadProgress = null) }
    }

    private var downloadJob: kotlinx.coroutines.Job? = null

    private fun trackActiveDownload(bookId: String) {
        downloadJob?.cancel()
        val workManager = WorkManager.getInstance(application)
        downloadJob = viewModelScope.launch {
            workManager.getWorkInfosByTagFlow("download_$bookId")
                .distinctUntilChanged()
                .collect { workInfos ->
                    Timber.d("trackActiveDownload=> $bookId, workInfos size: ${workInfos.size}")
                    val activeWork = workInfos.firstOrNull { 
                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED 
                    }
                    
                    if (activeWork != null) {
                        Timber.d("trackActiveDownload=> activeWork found: ${activeWork.state}")
                        val progress = activeWork.progress.getFloat("progress", 0f)
                        Timber.d("trackActiveDownload=> progress: $progress")
                        _viewState.update { it.copy(loading = false, downloadProgress = progress) }
                    } else {
                        val latestWork = workInfos.maxByOrNull { it.id } 
                        Timber.d("trackActiveDownload=> no active work. Latest state: ${latestWork?.state}")
                        
                        if (latestWork != null) {
                            if (latestWork.state == WorkInfo.State.SUCCEEDED) {
                                if (_viewState.value.downloadProgress != null) {
                                    Timber.d("trackActiveDownload=> download succeeded, refreshing")
                                    _viewState.update { it.copy(downloadProgress = null) }
                                    setUpBook()
                                }
                            } else if (latestWork.state == WorkInfo.State.FAILED) {
                                if (_viewState.value.downloadProgress != null) {
                                    _viewState.update { it.copy(downloadProgress = null) }
                                    Toast.makeText(application, "Download failed", Toast.LENGTH_SHORT).show()
                                }
                            } else if (latestWork.state == WorkInfo.State.CANCELLED) {
                                _viewState.update { it.copy(downloadProgress = null) }
                            }
                        }
                    }
                }
        }
    }

    fun deleteAudio() {
        val audioBook = bookState.value.book as? AudioBook ?: return
        
        viewModelScope.launch {
            _viewState.emit(_viewState.value.copy(loading = true))
            try {
                withContext(Dispatchers.IO) {
                    val file = File(audioBook.audioFilePath)
                    if (file.exists()) {
                        file.delete()
                    }

                    val book = Book(
                        id = audioBook.id,
                        title = audioBook.title,
                        author = audioBook.author,
                        language = audioBook.language,
                        voiceIdentifier = "en",
                        voiceRate = audioBook.voiceRate,
                        text = audioBook.parts.map { it.text },
                        lastPosition = audioBook.lastPosition,
                        updated = System.currentTimeMillis(),
                        bookmarks = audioBook.bookmarks,
                        chapters = audioBook.chapters
                    )

                    repository.updateBook(book)

                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            book = book,
                            voiceIdentifier = "en",
                            text = book.text,
                            audioPath = "",
                            parts = emptyList()
                        )
                        Toast.makeText(application, "Audio deleted, reverted to text book", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting audio")
                Toast.makeText(application, "Error deleting audio: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                _viewState.emit(_viewState.value.copy(loading = false))
            }
        }
    }

    fun setUpBook() {
        Timber.d("setUpBook=>")
        viewModelScope.launch {
            audioVoiceChangedSinceLastSave = false
            if (viewState.value.newBook) {
                Timber.d("setUpBook=>newBook skipping")
                return@launch
            }
            _viewState.update { it.copy(loading = true, selectedPage = 0) }

            val book = withContext(Dispatchers.IO) {
                repository.getSelectedBook()
            }
            Timber.d("setUpBook=>loaded book: ${book?.id}")

            val recentSelections = try {
                withContext(Dispatchers.IO) {
                    prefsStore.selectedLanguages().first()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading recent selections")
                emptyList()
            }
            recentSelections.forEach { selected ->
                recentSelectionsL.push(value = selected)
            }
            Timber.d("setUpBook=>voiceRate: ${book?.voiceRate}")

            if (book is Book) {
                _state.value = _state.value.copy(
                    book = book,
                    title = book.title,
                    author = book.author,
                    language = book.language,
                    voiceIdentifier = book.voiceIdentifier,
                    voiceRate = book.voiceRate,
                    text = book.text,
                    audioPath = "",
                    parts = emptyList(),
                    voice = "",
                    model = "",
                    bookSource = book.bookSource
                )
                trackActiveDownload(book.id)
            } else if (book is AudioBook) {
                _state.value = _state.value.copy(
                    book = book,
                    title = book.title,
                    author = book.author,
                    language = book.language,
                    voiceIdentifier = book.voice,
                    voiceRate = book.voiceRate,
                    text = emptyList(),
                    audioPath = book.audioFilePath,
                    parts = book.parts,
                    voice = book.voice,
                    model = book.model
                )
            }
            _viewState.update { it.copy(loading = false) }
            Timber.d("setUpBook=>done")
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            prefsStore.isDyslexicFontEnabled().collectLatest { enabled ->
                _viewState.update { it.copy(dyslexicFontEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            prefsStore.isHighlightingEnabled().collectLatest { enabled ->
                _viewState.update { it.copy(highlightingEnabled = enabled) }
            }
        }
    }

    fun toggleDyslexicFont(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.saveDyslexicFontEnabled(enabled)
        }
    }

    fun toggleHighlighting(enabled: Boolean) {
        viewModelScope.launch {
            prefsStore.saveHighlightingEnabled(enabled)
        }
    }

    fun createANewBook(ebook: EBookFile) {
        Timber.d("BookSettingsScreenView.createANewBook=>")
        viewModelScope.launch {
            val language = if (ebook.audioPath.isNotEmpty()) {
                ebook.language
            } else {
                Locale.getDefault().languageId()
            }
            val chapters = ebook.chapters.ifEmpty {
                val generatedChapters = mutableListOf<Chapter>()
                var currentWordIndex = 0
                ebook.content.forEachIndexed { index, chapterText ->
                    generatedChapters.add(Chapter("Chapter ${index + 1}", currentWordIndex))
                    currentWordIndex += chapterText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                }
                generatedChapters
            }

            val book = if (ebook.audioPath.isNotEmpty()) {
                AudioBook(
                    title = ebook.title,
                    author = ebook.author,
                    language = language,
                    voiceRate = ebook.rate,
                    audioFilePath = ebook.audioPath,
                    parts = ebook.text,
                    lastPosition = 0,
                    voice = ebook.voice,
                    model = ebook.model,
                    bookSource = ebook.bookSource,
                    updated = System.currentTimeMillis(),
                    bookmarks = mutableListOf(),
                    chapters = chapters
                )
            } else {
                Book(
                    title = ebook.title,
                    author = ebook.author,
                    language = language,
                    voiceIdentifier = "en",
                    voiceRate = 1.0f,
                    text = ebook.content,
                    lastPosition = 0,
                    bookSource = ebook.bookSource,
                    updated = System.currentTimeMillis(),
                    bookmarks = mutableListOf(),
                    chapters = chapters
                )
            }
            _state.value = _state.value.copy(
                book = book,
                title = ebook.title,
                author = ebook.author,
                language = language,
                voiceIdentifier = if (ebook.audioPath.isNotEmpty()) ebook.voice else "en",
                voiceRate = 1.0f,
                text = ebook.content,
                audioPath = ebook.audioPath,
                parts = ebook.text,
                voice = ebook.voice,
                model = ebook.model,
                bookSource = ebook.bookSource
            )
            _viewState.value = SettingsUIState(newBook = true)
        }
    }

    private fun currentPage(): String {
        val state = _state.value
        val selectedPage = viewState.value.selectedPage
        
        return when {
            state.text.isNotEmpty() && selectedPage >= 0 && selectedPage < state.text.size -> state.text[selectedPage]
            state.parts.isNotEmpty() && selectedPage >= 0 && selectedPage < state.parts.size -> state.parts[selectedPage].text
            else -> "This is a sample text for voice preview."
        }
    }


    fun updateBookDetails(
        title: String? = null,
        author: String? = null,
        voiceRate: Float? = null,
        language: String? = null,
        voiceIdentifier: String? = null
    ) {
        val current = _state.value
        if (voiceIdentifier != null && current.book is AudioBook && voiceIdentifier != current.book.voice) {
            audioVoiceChangedSinceLastSave = true
        }
        val updatedBook = when {
            voiceIdentifier != null && current.book is AudioBook -> {
                current.book.copy(voice = voiceIdentifier)
            }

            else -> current.book
        }

        _state.value = current.copy(
            book = updatedBook,
            title = title ?: current.title,
            author = author ?: current.author,
            voiceRate = voiceRate ?: current.voiceRate,
            language = language ?: current.language,
            voiceIdentifier = voiceIdentifier ?: current.voiceIdentifier,
            voice = if (voiceIdentifier != null && current.book is AudioBook) voiceIdentifier else current.voice
        )
        language?.let {
            recentSelectionsL.push(it)
        }
    }

    fun onCancel(onNewBook: () -> Unit, onUpdate: () -> Unit) {
        viewModelScope.launch {
            audioVoiceChangedSinceLastSave = false
            if (_viewState.value.newBook) {
                _viewState.value = _viewState.value.copy(newBook = false)
                onNewBook()
            } else {
                onUpdate()
            }
        }
    }

    fun onSave(completed: () -> Unit) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(loading = true)
            var savedBook: NeuReadBook? = null
            var shouldRegenerateAudio = false

            withContext(Dispatchers.IO) {
                _state.value.book?.let {
                    val b = when (it) {
                        is Book -> {
                            val text = if (bookState.value.text.size > 1) {
                                val from = _viewState.value.selectedPage
                                val to = bookState.value.text.size
                                bookState.value.text.subList(from, to)
                            } else {
                                bookState.value.text
                            }
                            
                            val updatedChapters = if (text.size != it.text.size) {
                                val newChapters = mutableListOf<Chapter>()
                                var currentWordIndex = 0
                                text.forEachIndexed { index, chapterText ->
                                    newChapters.add(Chapter("Chapter ${index + 1}", currentWordIndex))
                                    currentWordIndex += chapterText.split(Regex("\\s+")).filter { it.isNotEmpty() }.size
                                }
                                newChapters
                            } else {
                                it.chapters
                            }

                            it.copy(
                                title = bookState.value.title,
                                author = bookState.value.author,
                                language = bookState.value.language,
                                voiceIdentifier = bookState.value.voiceIdentifier,
                                voiceRate = bookState.value.voiceRate,
                                text = text,
                                chapters = updatedChapters,
                                bookSource = it.bookSource
                            )
                        }

                        is AudioBook -> {
                            shouldRegenerateAudio = audioVoiceChangedSinceLastSave &&
                                    bookState.value.voiceIdentifier.isNotBlank()
                            it.copy(
                                title = bookState.value.title,
                                author = bookState.value.author,
                                voiceRate = bookState.value.voiceRate,
                                voice = bookState.value.voiceIdentifier.ifBlank { it.voice },
                            )
                        }
                    }

                    if (viewState.value.newBook) {
                        repository.addBook(b)
                        repository.selectBook(b.id)
                    } else {
                        repository.updateBook(b)
                    }
                    savedBook = b
                }
                prefsStore.saveSelectedLanguages(recentSelectionsL.values)
            }

            savedBook?.let { updated ->
                _state.value = when (updated) {
                    is Book -> _state.value.copy(
                        book = updated,
                        title = updated.title,
                        author = updated.author,
                        language = updated.language,
                        voiceIdentifier = updated.voiceIdentifier,
                        voiceRate = updated.voiceRate,
                        text = updated.text,
                        audioPath = "",
                        parts = emptyList(),
                        voice = "",
                        model = ""
                    )

                    is AudioBook -> _state.value.copy(
                        book = updated,
                        title = updated.title,
                        author = updated.author,
                        language = updated.language,
                        voiceIdentifier = updated.voice,
                        voiceRate = updated.voiceRate,
                        text = emptyList(),
                        audioPath = updated.audioFilePath,
                        parts = updated.parts,
                        voice = updated.voice,
                        model = updated.model
                    )
                }
            }

            completed()
            _viewState.value = _viewState.value.copy(loading = false)

            if (shouldRegenerateAudio) {
                // Rebuild the downloaded file with the newly selected voice.
                downloadAudio()
            }
            audioVoiceChangedSinceLastSave = false

            mediaPlayer?.apply {
                stop()
                release()
            }
            mediaPlayer = null
        }
    }


    fun onPageSelected(page: Int) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(selectedPage = page)
        }
    }

    fun onOpenTextEditor() {
        val currentState = _state.value
        val currentText = if (currentState.bookSource == "clipboard") {
            currentState.text.joinToString("\n")
        } else {
            currentPage()
        }
        _viewState.update { it.copy(isEditingText = true, editingText = currentText) }
    }

    fun onSaveEditedText(newText: String) {
        val index = _viewState.value.selectedPage
        val currentState = _state.value
        
        if (currentState.bookSource == "clipboard") {
            val updatedText = newText.split("\n")
            _state.value = currentState.copy(text = updatedText)
        } else if (currentState.text.isNotEmpty()) {
            val updatedText = currentState.text.toMutableList()
            if (index in updatedText.indices) {
                updatedText[index] = newText
                _state.value = currentState.copy(text = updatedText)
            }
        } else if (currentState.parts.isNotEmpty()) {
            val updatedParts = currentState.parts.toMutableList()
            if (index in updatedParts.indices) {
                updatedParts[index] = updatedParts[index].copy(text = newText)
                _state.value = currentState.copy(parts = updatedParts)
            }
        }
        
        _viewState.update { it.copy(isEditingText = false, editingText = "") }
    }

    fun onCloseTextEditor() {
        _viewState.update { it.copy(isEditingText = false, editingText = "") }
    }

    fun onShowDelete(show: Boolean) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(showDeleteDialog = show)
        }
    }

    fun onDelete(onBookDeleted: () -> Unit) {
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(showDeleteDialog = false)
            withContext(Dispatchers.IO) {
                _state.value.book?.let {
                    repository.deleteBook(it)
                }
                withContext(Dispatchers.Main) {
                    onBookDeleted()
                }
            }
        }
    }
}
