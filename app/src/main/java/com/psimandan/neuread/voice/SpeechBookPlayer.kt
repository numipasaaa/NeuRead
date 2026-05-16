package com.psimandan.neuread.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.psimandan.extensions.formatSecondsToHMS
import com.psimandan.neuread.PlaybackSource
import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.Book.Companion.SECONDS_PER_CHARACTER
import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.data.model.NeuReadBook
import com.psimandan.neuread.ui.player.PlayerViewModel.HighlightingUIState
import com.psimandan.neuread.ui.player.PlayerViewModel.PlayerUIState
import com.psimandan.neuread.ui.player.TextTimeRelationsTools.getCurrentWordIndex
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.math.max
import kotlin.math.min

import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.PrefsStore
import kotlinx.coroutines.flow.first
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

fun String.cleanedForTTS(): String {
    return this.replace(",,", ",").replace("..", ".").filter { char ->
        char.isLetterOrDigit() ||
                char.isWhitespace() ||
                char in listOf('.', ',', '?', '!', '\'', '"', '-')
    }
}


interface SpeakingCallBack {
    fun onReady(uiState: PlayerUIState)
    fun onStart()
    fun onProgressUpdate(
        updatedBook: NeuReadBook,
        pUIState: PlayerUIState,
        hUIState: HighlightingUIState
    )
    fun onStop()
    fun onCompleted()

    val viewState: StateFlow<PlayerUIState>
    fun onUpdateUI(state: PlayerUIState)
    val highlightingState: StateFlow<HighlightingUIState>
    fun onUpdateHighlightingUI(state: HighlightingUIState)

    val book: NeuReadBook?
}

class SpeechBookPlayer(
    private val context: Context,
    private var voice: Voice,
    private var speakingCallback: SpeakingCallBack,
    private val prefsStore: PrefsStore
) : BookPlayer {
    companion object {
        const val FRAME_SIZE = 60
        const val SEEK_STEP_TEXT = 60
    }

    private var textToSpeech: TextToSpeech? = null
    private var neuTtsApiClient: NeuTTSApiClient? = null

    private val playerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
    private var audioTrack: AudioTrack? = null
    private var words = listOf<String>()
    private var selectedSpeechRate: Float = 1f
    private var currentWordIndex = 0
    private var totalWords: Int = 0
    private var isPlaying = false
    private var frameStartIndex = 0
    private var wordOffsets = listOf<Int>()

    init {
        initializeTTS()
    }

    private fun initializeTTS() {
        val book = speakingCallback.book ?: return
        if (book !is Book) {
            Timber.e("Invalid book type")
            return
        }

        // Route the initialization based on the selected voice
        if (voice.isNetworkConnectionRequired) {
            initializeNeuTts(book)
        } else {
            initializeNativeTts(book, Locale(book.language))
        }
    }

    private fun initializeNeuTts(book: Book) {
        // INITIALIZE CLIENT-SERVER IMPLEMENTATION
        neuTtsApiClient = NeuTTSApiClient(context)

        playerScope.launch {
            try {
                // Setup the UI state
                words = book.text.flatMap { it.cleanedForTTS().split("\\s+".toRegex()) }
                    .mapNotNull { it.takeIf { it.isNotEmpty() } }
                totalWords = words.size
                currentWordIndex = book.lastPosition
                selectedSpeechRate = book.voiceRate

                book.lazyCalculate {
                    val viewState = book.viewState.value
                    val secondsElapsed = calculateElapsedTime(currentWordIndex)
                    speakingCallback.onReady(
                        uiState = PlayerUIState(
                            progress = secondsElapsed.toFloat(),
                            totalTimeString = viewState.totalTime,
                            isLoading = false,
                            progressTime = secondsElapsed.formatSecondsToHMS(),
                            sliderRange = 0f..viewState.totalTimeSeconds.toFloat(),
                            totalTime = viewState.totalTimeSeconds.toDouble(),
                            bookmarks = book.bookmarks.map {
                                it.title = titleForBookmark(it.position); it
                            },
                            chapters = book.chapters
                        )
                    )
                }
                Timber.d("NeuTTS Client-Server Ready!")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize NeuTTS Client")
                speakingCallback.onStop()
            }
        }
    }

    private fun initializeNativeTts(book: Book, selectedLanguage: Locale) {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val player = textToSpeech!!

                words = book.text.flatMap { it.cleanedForTTS().split("\\s+".toRegex()) }
                    .mapNotNull { it.takeIf { it.isNotEmpty() } }

                totalWords = words.size
                selectedSpeechRate = book.voiceRate
                currentWordIndex = book.lastPosition

                player.language = selectedLanguage
                player.voice = voice
                player.setSpeechRate(book.voiceRate)
                player.setOnUtteranceProgressListener(ttsListener)

                book.lazyCalculate {
                    val viewState = book.viewState.value
                    val secondsElapsed = calculateElapsedTime(currentWordIndex)
                    speakingCallback.onReady(
                        uiState = PlayerUIState(
                            progress = secondsElapsed.toFloat(),
                            totalTimeString = viewState.totalTime,
                            isLoading = false,
                            progressTime = secondsElapsed.formatSecondsToHMS(),
                            sliderRange = 0f..viewState.totalTimeSeconds.toFloat(),
                            totalTime = viewState.totalTimeSeconds.toDouble(),
                            bookmarks = book.bookmarks.map {
                                it.title = titleForBookmark(it.position); it
                            },
                            chapters = book.chapters
                        )
                    )
                }
            } else {
                Timber.e("TTS Initialization failed with status: $status")
            }
        }
    }

    var tmp_start = 0
    private val ttsListener = object : UtteranceProgressListener() {
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
            Timber.d("onRangeStart: start=$start end=$end frame=$frame")
            val wordIdxInFrame = wordOffsets.indexOfLast { it <= start }.coerceAtLeast(0)
            val globalWordIndex = frameStartIndex + wordIdxInFrame

            val hState = speakingCallback.highlightingState.value
            val book = speakingCallback.book as Book
            val secondsElapsed = calculateElapsedTime(globalWordIndex)
            
            currentWordIndex = globalWordIndex

            speakingCallback.onProgressUpdate(
                updatedBook = book.copy(
                    lastPosition = globalWordIndex,
                    updated = System.currentTimeMillis()
                ),
                pUIState = speakingCallback.viewState.value.copy(
                    progress = secondsElapsed.toFloat(),
                    progressTime = secondsElapsed.formatSecondsToHMS()
                ),
                hUIState = hState.copy(
                    currentWordIndexInFrame = wordIdxInFrame
                )
            )
        }

        override fun onStart(utteranceId: String?) {
            Timber.d("onStart: $utteranceId")
            speakingCallback.onStart()
        }

        override fun onDone(utteranceId: String?) {
            Timber.d("onDone: $utteranceId")
            onNextWord()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(p0: String?) {
            Timber.d("onError: $p0")
            speakingCallback.onStop()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            Timber.e("TTS Error ($errorCode) for utterance: $utteranceId")
            if (errorCode in listOf(
                    TextToSpeech.ERROR_NETWORK,
                    TextToSpeech.ERROR_NETWORK_TIMEOUT
                )
            ) {
                Timber.w("Network error encountered, consider switching to offline voices.")
            }
            speakingCallback.onStop()
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            Timber.d("onStop: $utteranceId")
            speakingCallback.onStop()
            isPlaying = false
        }
    }

    fun onNextWord() {
        Timber.d("onNextWord: index=$currentWordIndex, total=$totalWords")
        if (currentWordIndex < totalWords - 1) {
            playNextFrame()
        } else {
            Timber.d("Reached end of book")
            speakingCallback.onUpdateUI(
                speakingCallback.viewState.value.copy(
                    isSpeaking = false
                )
            )
        }
    }

    private var synthesisJob: kotlinx.coroutines.Job? = null

    fun speak(text: String, frameSize: Int = 0, frame: List<String> = emptyList()) {
        if (voice.isNetworkConnectionRequired) {
            synthesisJob?.cancel()
            // Route to Server API
            synthesisJob = playerScope.launch {
                if (!isPlaying) return@launch

                // Show loading indicator
                speakingCallback.onUpdateUI(
                    speakingCallback.viewState.value.copy(
                        isLoading = true,
                        chapters = speakingCallback.book?.chapters ?: emptyList()
                    )
                )

                // Synthesize on server
                val audioFile = if (!voice.name.contains("NeuTTS", ignoreCase = true)) {
                    val clonedVoices = prefsStore.getClonedVoices().first()
                    val currentClonedVoice = clonedVoices.find { it.name == voice.name }
                    
                    if (currentClonedVoice != null) {
                        neuTtsApiClient?.cloneWithCodes(
                            text = text,
                            refText = currentClonedVoice.referenceText,
                            refCodes = currentClonedVoice.codes
                        )
                    } else {
                        val book = speakingCallback.book as? Book
                        
                        // LOG THE FULL STATE TO SEE WHY IT'S FAILING
                        Timber.d("NeuTTS DECISION: voice.name=${voice.name}, book.lang=${book?.language}")

                        val isRomanianVoice = voice.name.contains("Petra", ignoreCase = true)
                        
                        // Voice name for the server
                        val voiceParam = if (isRomanianVoice) {
                            "petra"
                        } else {
                            voice.name.lowercase().removeSuffix(" (ai)").trim()
                        }

                        // Force language to "ro" for the Romanian Petra voice to ensure finetune activation
                        val langParam = if (isRomanianVoice) "ro" else (book?.language ?: voice.locale.language)
                        
                        Timber.d("NeuTTS FINAL REQUEST: voiceParam=$voiceParam, langParam=$langParam")
                        neuTtsApiClient?.synthesizeSpeech(text, voiceParam, langParam)
                    }
                } else {
                    val book = speakingCallback.book as? Book
                    neuTtsApiClient?.synthesizeSpeech(text, null, book?.language)
                }

                if (!isPlaying) {
                    speakingCallback.onUpdateUI(
                        speakingCallback.viewState.value.copy(isLoading = false, isSpeaking = false)
                    )
                    return@launch
                }

                // Hide loading indicator
                speakingCallback.onUpdateUI(
                    speakingCallback.viewState.value.copy(isLoading = false, isSpeaking = true)
                )

                if (audioFile != null) {
                    playWavFile(audioFile, frameSize, frame)
                } else {
                    Timber.e("Synthesis failed (audioFile is null)")
                    speakingCallback.onStop()
                }
            }
        } else {
            // Route to standard Android TTS
            val utteranceId = "my_utterance_id"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        }
    }

    private fun calculateElapsedTime(progress: Int): Double {
        var totalChars = 0
        for (i in 0 until min(progress, words.size)) {
            totalChars += words[i].length + 1
        }
        val seconds = (totalChars * SECONDS_PER_CHARACTER) / selectedSpeechRate
        return seconds
    }

    private fun isSpeaking(): Boolean = textToSpeech?.isSpeaking == true || isPlaying

    override fun onPlay(source: PlaybackSource) {
        if (isPlaying) return
        isPlaying = true
        playNextFrame()
    }

    private fun playNextFrame() {
        if (words.isEmpty() || currentWordIndex >= words.size) {
            Timber.d("playNextFrame: No more words or empty list")
            return
        }
        val toIndex = minOf(currentWordIndex + FRAME_SIZE, totalWords)
        val hState = speakingCallback.highlightingState.value

        val frame = words.subList(currentWordIndex, toIndex)
        val frameSize = frame.size
        frameStartIndex = currentWordIndex
        wordOffsets = calculateWordOffsets(frame)
        
        Timber.d("playNextFrame: index=$currentWordIndex, toIndex=$toIndex, frameSize=$frameSize")

        // Reset currentWordIndexInFrame to 0 before starting new frame
        speakingCallback.onUpdateHighlightingUI(
            hState.copy(
                currentWordIndexInFrame = 0,
                currentFrame = frame
            )
        )
        speak(frame.joinToString(" "), frameSize, frame)
    }

    override fun onDeleteBookmark(bookmark: Bookmark) {
        val book = speakingCallback.book as Book
        val updatedBookmarks = book.bookmarks.filter { it.position != bookmark.position }.toMutableList()
        book.bookmarks.clear()
        book.bookmarks.addAll(updatedBookmarks)

        speakingCallback.onUpdateUI(
            speakingCallback.viewState.value.copy(
                bookmarks = updatedBookmarks.map {
                    if (it.title.isEmpty()) {
                        it.title = titleForBookmark(it.position)
                    }; it
                }
            )
        )
        speakingCallback.onProgressUpdate(
            updatedBook = book,
            pUIState = speakingCallback.viewState.value,
            hUIState = speakingCallback.highlightingState.value
        )
    }

    override fun onUpdateBookmarkNote(bookmark: Bookmark, note: String) {
        val book = speakingCallback.book as Book
        book.bookmarks.find { it.position == bookmark.position }?.note = note

        speakingCallback.onUpdateUI(
            speakingCallback.viewState.value.copy(
                bookmarks = book.bookmarks.map {
                    if (it.title.isEmpty()) {
                        it.title = titleForBookmark(it.position)
                    }; it
                }
            )
        )
        speakingCallback.onProgressUpdate(
            updatedBook = book,
            pUIState = speakingCallback.viewState.value,
            hUIState = speakingCallback.highlightingState.value
        )
    }

    override fun onSaveBookmark() {
        val book = speakingCallback.book as Book
        book.bookmarks.add(Bookmark(currentWordIndex))
        speakingCallback.onUpdateUI(speakingCallback.viewState.value.copy(
            bookmarks = book.bookmarks.map {
                if (it.title.isEmpty()) {
                    it.title = titleForBookmark(it.position)
                }; it
            }
        ))
        speakingCallback.onProgressUpdate(
            updatedBook = book,
            pUIState = speakingCallback.viewState.value,
            hUIState = speakingCallback.highlightingState.value
        )
    }

    private fun titleForBookmark(position: Int): String {
        val elapsedTimeToShow = calculateElapsedTime(position).formatSecondsToHMS()//
        val from = max(0, position - 5)
        val to = min(words.size - 1, position + 10)

        if (to <= words.size && words.isNotEmpty()) {
            val t = words.subList(from, to)
            return "$elapsedTimeToShow | ${t.joinToString(" ")}"
        } else {
            return "$elapsedTimeToShow | Unknown Bookmark"
        }
    }

    override fun currentTimeElapsed(): Long {
        if (voice.isNetworkConnectionRequired) {
            val baseTime = (calculateElapsedTime(frameStartIndex) * 1000).toLong()
            val frameOffset = mediaPlayer?.let { if (it.isPlaying) it.currentPosition.toLong() else 0L } ?: 0L
            return baseTime + frameOffset
        }
        return (calculateElapsedTime(currentWordIndex) * 1000).toLong()
    }

    override fun onPlayFromBookmark(position: Int) {
        seekToWordIndex(position, PlaybackSource.BOOKMARK)
    }

    override fun updateCallback(callback: SpeakingCallBack) {
        this.speakingCallback = callback
        val book = speakingCallback.book as? Book ?: return
        this.selectedSpeechRate = book.voiceRate
        
        fun sendReady() {
            val viewState = book.viewState.value
            val secondsElapsed = calculateElapsedTime(currentWordIndex)
            val pState = PlayerUIState(
                progress = secondsElapsed.toFloat(),
                totalTimeString = viewState.totalTime,
                isLoading = false,
                isSpeaking = isPlaying,
                progressTime = secondsElapsed.formatSecondsToHMS(),
                sliderRange = 0f..viewState.totalTimeSeconds.toFloat(),
                totalTime = viewState.totalTimeSeconds.toDouble(),
                bookmarks = book.bookmarks.map {
                    it.title = titleForBookmark(it.position); it
                },
                chapters = book.chapters
            )
            speakingCallback.onReady(uiState = pState)
            
            // Sync highlighting state with accurate current word within frame
            val toIndex = minOf(frameStartIndex + FRAME_SIZE, totalWords)
            val frame = if (words.isNotEmpty() && frameStartIndex < words.size) {
                words.subList(frameStartIndex, toIndex)
            } else {
                emptyList()
            }
            
            val wordIdxInFrame = (currentWordIndex - frameStartIndex).coerceIn(0, (frame.size - 1).coerceAtLeast(0))
            
            speakingCallback.onUpdateHighlightingUI(
                HighlightingUIState(
                    currentWordIndexInFrame = wordIdxInFrame,
                    currentFrame = frame
                )
            )
        }

        if (book.viewState.value.totalTimeSeconds == 0L) {
            book.lazyCalculate { sendReady() }
        } else {
            sendReady()
        }
    }

    override fun isLoading(): Boolean = synthesisJob?.isActive == true

    override fun onStopSpeaking() {
        isPlaying = false
        synthesisJob?.cancel()
        synthesisJob = null
        speakingCallback.onUpdateUI(
            speakingCallback.viewState.value.copy(isLoading = false, isSpeaking = false)
        )
        speakingCallback.onStop()
        textToSpeech?.stop()
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping audio track")
        }
    }

    private var mediaPlayer: android.media.MediaPlayer? = null

    private suspend fun playWavFile(file: java.io.File, wordsInFrame: Int, frame: List<String>) {
        withContext(Dispatchers.Main) {
            val startIndexOfFrame = currentWordIndex
            mediaPlayer?.release()
            val mp = android.media.MediaPlayer()
            mediaPlayer = mp
            
            mp.setDataSource(file.absolutePath)
            mp.setOnCompletionListener {
                Timber.d("MediaPlayer: playback completed for frame starting at $startIndexOfFrame")
                if (isPlaying) {
                    // Advance index only AFTER successful playback
                    currentWordIndex = startIndexOfFrame + wordsInFrame
                    onNextWord()
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Timber.e("MediaPlayer error: what=$what, extra=$extra")
                if (isPlaying) {
                    // Skip this frame on error to keep moving
                    currentWordIndex = startIndexOfFrame + wordsInFrame
                    onNextWord()
                }
                true
            }
            mp.prepare()

            // Synchronize UI with the START of the current audio frame
            val book = (speakingCallback.book as? Book) ?: return@withContext
            val secondsElapsed = calculateElapsedTime(startIndexOfFrame)

            speakingCallback.onProgressUpdate(
                updatedBook = book.copy(
                    lastPosition = startIndexOfFrame,
                    updated = System.currentTimeMillis()
                ),
                pUIState = speakingCallback.viewState.value.copy(
                    progress = secondsElapsed.toFloat(),
                    progressTime = secondsElapsed.formatSecondsToHMS(),
                    isSpeaking = true,
                    isLoading = false
                ),
                hUIState = speakingCallback.highlightingState.value.copy(
                    currentFrame = frame,
                    currentWordIndexInFrame = 0
                )
            )

            // Start a timer to update highlighting locally while the frame plays
            val duration = mp.duration
            if (duration > 0) {
                playerScope.launch {
                    while (isPlaying && mediaPlayer == mp && mp.isPlaying) {
                        val elapsedMs = mp.currentPosition.toLong()
                        val wordIdx = getCurrentWordIndex(
                            elapsedMs,
                            frame,
                            0, // Local to frame
                            duration
                        )
                        
                        val currentHState = speakingCallback.highlightingState.value
                        if (currentHState.currentWordIndexInFrame != wordIdx) {
                            speakingCallback.onUpdateHighlightingUI(
                                currentHState.copy(currentWordIndexInFrame = wordIdx)
                            )
                        }
                        kotlinx.coroutines.delay(30)
                    }
                }
            }
            
            mp.start()
        }
    }

    override fun onClose() {
        onStopSpeaking()
        textToSpeech?.shutdown()
        textToSpeech = null

        mediaPlayer?.release()
        mediaPlayer = null

        audioTrack?.release()
        audioTrack = null
    }

    override fun onUpdateSpeechRate(rate: Float) {
        selectedSpeechRate = rate
        textToSpeech?.setSpeechRate(rate)
    }

    override fun onFastForward() {
        val currentSecs = currentTimeElapsed() / 1000.0
        onUserChangePosition((currentSecs + 10).toFloat())
    }

    override fun onRewind() {
        val currentSecs = currentTimeElapsed() / 1000.0
        onUserChangePosition((currentSecs - 10).toFloat())
    }

    fun wordIndexFromSeconds(seconds: Double): Int {
        val targetChars = (seconds * selectedSpeechRate) / SECONDS_PER_CHARACTER
        var currentChars = 0
        for (i in words.indices) {
            if (currentChars >= targetChars) return i
            currentChars += words[i].length + 1 // +1 for space
        }
        return words.size
    }

    override fun onUserChangePosition(value: Float) {
        val wasPlaying = isPlaying
        val index = wordIndexFromSeconds(value.toDouble())
        seekToWordIndex(index, PlaybackSource.SEEK, wasPlaying)
    }

    private fun seekToWordIndex(index: Int, source: PlaybackSource, autoPlay: Boolean = true) {
        isPlaying = false
        onStopSpeaking()

        currentWordIndex = index.coerceIn(0, (totalWords - 1).coerceAtLeast(0))

        val hState = speakingCallback.highlightingState.value
        val book = speakingCallback.book as Book
        val elapsedSeconds = calculateElapsedTime(progress = currentWordIndex)

        speakingCallback.onProgressUpdate(
            updatedBook = book.copy(
                lastPosition = currentWordIndex,
                updated = System.currentTimeMillis()
            ),
            pUIState = speakingCallback.viewState.value.copy(
                progress = elapsedSeconds.toFloat(),
                progressTime = elapsedSeconds.formatSecondsToHMS()
            ),
            hUIState = hState.copy(
                currentWordIndexInFrame = 0
            )
        )
        if (isSpeaking() || !autoPlay) return
        onPlay(source = source)
    }

    override fun onJumpToChapter(position: Int) {
        seekToWordIndex(position, PlaybackSource.OTHER)
    }

    private fun calculateWordOffsets(words: List<String>): List<Int> {
        val offsets = mutableListOf<Int>()
        var currentOffset = 0
        for (word in words) {
            offsets.add(currentOffset)
            currentOffset += word.length + 1 // +1 for space
        }
        return offsets
    }

    /**
     * Reverted to Client-Server implementation. 
     * Offline methods (decodeTokensToAudio, playAudioWaveform) removed.
     */
}
