package com.psimandan.neuread.ui.settings

import android.content.Context
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.data.model.EBookFile
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.data.model.TextPart
import com.psimandan.neuread.voice.NeuTTSApiClient
import com.psimandan.neuread.voice.SimpleSpeakingCallBack
import com.psimandan.neuread.voice.SimpleSpeechProvider
import com.psimandan.neuread.voice.languageId
import com.psimandan.neuread.data.model.Chapter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.SequenceInputStream
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
    val recentSelectionsL: LimitedDictionary = LimitedDictionary(limit = 5U)

    fun payTextSample(language: Locale, voice: Voice?, rate: Float) {
        voice?.let {
            if (bookState.value.book is Book) {
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
                                    // Retry with offline voices or notify the user
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
            }
        } ?: run {
            Toast.makeText(
                application,
                "Please select a voice first!",
                Toast.LENGTH_SHORT
            ).show()
        }


    }

    fun payAudioSample() {
        if (bookState.value.book is AudioBook) {
            val book = (bookState.value.book as AudioBook)
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
        val book = bookState.value.book as? Book ?: return
        val voiceName = bookState.value.voiceIdentifier
        
        viewModelScope.launch {
            _viewState.emit(_viewState.value.copy(loading = true, downloadProgress = 0f))
            
            try {
                val clonedVoices = prefsStore.getClonedVoices().first()
                val currentClonedVoice = clonedVoices.find { it.name == voiceName }
                val apiClient = NeuTTSApiClient(application)
                
                val allSentences = book.text.flatMap { text ->
                    text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
                }
                
                val audioParts = mutableListOf<TextPart>()
                val tempFiles = mutableListOf<File>()
                var currentTotalTimeMs = 0
                
                for ((index, sentence) in allSentences.withIndex()) {
                    val audioFile = if (currentClonedVoice != null) {
                        apiClient.cloneWithCodes(
                            text = sentence,
                            refText = currentClonedVoice.referenceText,
                            refCodes = currentClonedVoice.codes
                        )
                    } else {
                        apiClient.synthesizeSpeech(sentence)
                    }
                    
                    if (audioFile != null && audioFile.exists()) {
                        tempFiles.add(audioFile)
                        audioParts.add(TextPart(currentTotalTimeMs, sentence))
                        
                        val duration = getAudioDuration(audioFile)
                        currentTotalTimeMs += duration.toInt()
                    }
                    _viewState.emit(_viewState.value.copy(downloadProgress = (index + 1).toFloat() / allSentences.size))
                }
                
                if (tempFiles.isNotEmpty()) {
                    val finalAudioFile = mergeAudioFiles(tempFiles)
                    if (finalAudioFile != null) {
                        val audioBook = AudioBook(
                            id = book.id,
                            title = book.title,
                            author = book.author,
                            language = book.language,
                            voiceRate = book.voiceRate,
                            lastPosition = 0,
                            updated = System.currentTimeMillis(),
                            bookmarks = book.bookmarks.toMutableList(),
                            chapters = book.chapters,
                            parts = audioParts,
                            audioFilePath = finalAudioFile.absolutePath,
                            voice = voiceName,
                            model = "NeuTTS",
                            bookSource = "Cloned"
                        )
                        repository.updateBook(audioBook)
                        _state.value = _state.value.copy(book = audioBook)
                        Toast.makeText(application, "Audio downloaded successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error downloading audio")
                Toast.makeText(application, "Error downloading audio: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                _viewState.emit(_viewState.value.copy(loading = false, downloadProgress = null))
            }
        }
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun mergeAudioFiles(files: List<File>): File? {
        val outputFile = File(application.filesDir, "audio_book_${System.currentTimeMillis()}.wav")
        try {
            var totalDataSize = 0L
            var wavHeader: ByteArray? = null

            FileOutputStream(outputFile).use { out ->
                // Placeholder for header
                out.write(ByteArray(44))

                files.forEach { file ->
                    FileInputStream(file).use { input ->
                        val header = ByteArray(44)
                        input.read(header)
                        if (wavHeader == null) wavHeader = header

                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            out.write(buffer, 0, bytesRead)
                            totalDataSize += bytesRead
                        }
                    }
                    file.delete()
                }
            }

            // Update header with correct sizes
            wavHeader?.let { header ->
                val raf = java.io.RandomAccessFile(outputFile, "rw")
                header[4] = ((totalDataSize + 36) and 0xff).toByte()
                header[5] = (((totalDataSize + 36) shr 8) and 0xff).toByte()
                header[6] = (((totalDataSize + 36) shr 16) and 0xff).toByte()
                header[7] = (((totalDataSize + 36) shr 24) and 0xff).toByte()

                header[40] = (totalDataSize and 0xff).toByte()
                header[41] = ((totalDataSize shr 8) and 0xff).toByte()
                header[42] = ((totalDataSize shr 16) and 0xff).toByte()
                header[43] = ((totalDataSize shr 24) and 0xff).toByte()

                raf.write(header)
                raf.close()
            }

            return outputFile
        } catch (e: Exception) {
            Timber.e(e, "Error merging audio files")
            return null
        }
    }

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
        val downloadProgress: Float? = null
    )

    private val _state = MutableStateFlow(BookUIState())
    val bookState: StateFlow<BookUIState> get() = _state.asStateFlow()

    private val _viewState = MutableStateFlow(SettingsUIState())
    val viewState: StateFlow<SettingsUIState> get() = _viewState.asStateFlow()


    fun setUpBook() {
        Timber.d("setUpBook=>")
        viewModelScope.launch {
            if (viewState.value.newBook) {
                return@launch
            }
            _state.value = BookUIState()
            _viewState.emit(SettingsUIState(newBook = false, loading = true)) // Immediate UI update

            val book = withContext(Dispatchers.IO) {
                repository.getSelectedBook()
            }
            val recentSelections = prefsStore.selectedLanguages().first() // Get latest value only
            recentSelections.forEach { selected ->
                recentSelectionsL.push(value = selected)
            }
            Timber.d("setUpBook=>${book?.voiceRate}")

            withContext(Dispatchers.Main) {
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
                        model = ""
                    )
                } else if (book is AudioBook) {
                    _state.value = _state.value.copy(
                        book = book,
                        title = book.title,
                        author = book.author,
                        language = book.language,
                        voiceIdentifier = "",
                        voiceRate = book.voiceRate,
                        text = emptyList(),
                        audioPath = book.audioFilePath,
                        parts = book.parts,
                        voice = book.voice,
                        model = book.model
                    )
                }
                _viewState.emit(_viewState.value.copy(loading = false)) // Ensure loading indicator stops
            }
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
                    bookmarks = emptyList<Bookmark>().toMutableList(),
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
                    updated = System.currentTimeMillis(),
                    bookmarks = emptyList<Bookmark>().toMutableList(),
                    chapters = chapters
                )
            }
            _state.value = _state.value.copy(
                book = book,
                title = ebook.title,
                author = ebook.author,
                language = language,
                voiceIdentifier = "en",
                voiceRate = ebook.rate,
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
        return if (viewState.value.selectedPage <= _state.value.text.size - 1) {
            _state.value.text[viewState.value.selectedPage]
        } else "1, 2, 3, 4, 5, 5, 4, 3, 2, 1!"
    }


    fun updateBookDetails(
        title: String? = null,
        author: String? = null,
        voiceRate: Float? = null,
        language: String? = null,
        voiceIdentifier: String? = null
    ) {
        _state.value = _state.value.copy(
            title = title ?: _state.value.title,
            author = author ?: _state.value.author,
            voiceRate = voiceRate ?: _state.value.voiceRate,
            language = language ?: _state.value.language,
            voiceIdentifier = voiceIdentifier ?: _state.value.voiceIdentifier
        )
        language?.let {
            recentSelectionsL.push(it)
        }
    }

    fun onCancel(onNewBook: () -> Unit, onUpdate: () -> Unit) {
        Timber.d("BookSettingsScreenView.onCancel=>")
        viewModelScope.launch {
            if (_viewState.value.newBook) {
                _viewState.value = _viewState.value.copy(newBook = false)
                onNewBook()
            } else {
                onUpdate()
            }
        }
    }

    fun onSave(completed: () -> Unit) {
        Timber.d("BookSettingsScreenView.onSave=>")
        viewModelScope.launch {
            _viewState.value = _viewState.value.copy(loading = true)

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
                            
                            // Recalculate chapters if text has been trimmed
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
                                chapters = updatedChapters
                            )
                        }

                        is AudioBook -> {
                            it.copy(
                                title = bookState.value.title,
                                author = bookState.value.author,
                                voiceRate = bookState.value.voiceRate,
                            )
                        }
                    }

                    if (viewState.value.newBook) {
                        repository.addBook(b)
                        repository.selectBook(b.id)
                    } else {
                        repository.updateBook(b)
                    }
                }
                prefsStore.saveSelectedLanguages(recentSelectionsL.values)
            }

            // Back to UI thread
            completed()
            _viewState.value = _viewState.value.copy(loading = false)
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