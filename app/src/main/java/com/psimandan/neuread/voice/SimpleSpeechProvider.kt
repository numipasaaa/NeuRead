package com.psimandan.neuread.voice

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.PrefsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.*

interface SimpleSpeakingCallBack {
    fun onError(utteranceId: String?, errorCode: Int)
}

class SimpleSpeechProvider(
    private val context: Context,
    private var currentLocale: Locale = Locale.getDefault(),
    private var currentVoice: Voice,
    private var speechRate: Float = 1.0f,
    private val speakingCallBack: SimpleSpeakingCallBack?,
    private val prefsStore: PrefsStore
) {
    private val apiClient = NeuTTSApiClient(context)
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var mediaPlayer: android.media.MediaPlayer? = null

    private val speechListener = object : UtteranceProgressListener() {
        override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
        }

        override fun onStart(utteranceId: String?) {
        }

        override fun onDone(utteranceId: String?) {
        }

        @Deprecated(
            "Deprecated in Java",
            ReplaceWith("Timber.d(\"textToSpeech=> onError=>\$p0\")", "timber.log.Timber")
        )
        override fun onError(p0: String?) {
            Timber.d("textToSpeech=> onError=>$p0")
            speakingCallBack?.onError(null, 0)
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            super.onError(utteranceId, errorCode)
            speakingCallBack?.onError(utteranceId, errorCode)
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            super.onStop(utteranceId, interrupted)
        }
    }

    private lateinit var textToSpeech: TextToSpeech

    init {
        initSpeechProvider()
    }

    private fun initSpeechProvider() {
        textToSpeech = TextToSpeech(context) { status ->
            Timber.d("textToSpeech.init=>$status")
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.language = currentLocale
                textToSpeech.voice = currentVoice
                textToSpeech.setSpeechRate(speechRate)
                textToSpeech.setOnUtteranceProgressListener(speechListener)
            }
        }
    }

    fun updateLocale(locale: Locale, voice: Voice, rate: Float) {
        currentLocale = locale
        currentVoice = voice
        speechRate = rate
        textToSpeech.language = locale
        textToSpeech.voice = voice
        textToSpeech.setSpeechRate(rate)
    }

    fun speak(text: String) {
        if (currentVoice.isNetworkConnectionRequired) {
            scope.launch {
                // Check if we are playing a sample (short text) and if we have a local preview
                val isSample = text.length < 200 // Threshold for sample vs actual book text
                
                if (isSample) {
                    // 1. Check for Cloned Voice local recording
                    val clonedVoices = prefsStore.getClonedVoices().first()
                    val currentClonedVoice = clonedVoices.find { it.name == currentVoice.name }
                    if (currentClonedVoice?.samplePath != null) {
                        val file = File(currentClonedVoice.samplePath)
                        if (file.exists()) {
                            Timber.d("SimpleSpeechProvider: Playing local recording for cloned voice ${currentVoice.name}")
                            playAudioFile(file, deleteAfter = false)
                            return@launch
                        }
                    }

                    // 2. Check for AI Voice asset sample
                    val normalizedName = currentVoice.name.replace(" (AI)", "").lowercase()
                    val assetPath = "voice_samples/$normalizedName.wav"
                    try {
                        context.assets.open(assetPath).use {
                            Timber.d("SimpleSpeechProvider: Playing local asset sample for ${currentVoice.name} from $assetPath")
                            // We need to copy asset to temp file to use playAudioFile or use a different player
                            val tempFile = File(context.cacheDir, "temp_sample.wav")
                            it.copyTo(tempFile.outputStream())
                            playAudioFile(tempFile)
                            return@launch
                        }
                    } catch (e: Exception) {
                        Timber.d("SimpleSpeechProvider: Local asset sample not found for ${currentVoice.name} (checked $assetPath)")
                    }
                }

                val audioFile = if (!currentVoice.name.contains("NeuTTS", ignoreCase = true)) {
                    val clonedVoices = prefsStore.getClonedVoices().first()
                    val currentClonedVoice = clonedVoices.find { it.name == currentVoice.name }
                    if (currentClonedVoice != null) {
                        apiClient.cloneBatch(
                            sentences = listOf(text),
                            refText = currentClonedVoice.referenceText,
                            refCodes = currentClonedVoice.codes
                        )?.file
                    } else {
                        val romanianVoices = listOf("Adrian", "Andreea", "Mihaela", "Mihai")
                        val isRomanianVoice = romanianVoices.any { currentVoice.name.contains(it, ignoreCase = true) }
                        
                        val voiceParam = if (isRomanianVoice) {
                            romanianVoices.find { currentVoice.name.contains(it, ignoreCase = true) }?.lowercase() ?: "adrian"
                        } else {
                            currentVoice.name.lowercase().split(" ").firstOrNull()
                        }
                        
                        val langParam = if (isRomanianVoice) "ro" else currentLocale.language

                        Timber.d("SimpleSpeechProvider: voiceParam=$voiceParam, langParam=$langParam")
                        apiClient.synthesizeSpeech(text, voiceParam, langParam)
                    }
                } else {
                    apiClient.synthesizeSpeech(text)
                }

                if (audioFile != null && audioFile.exists()) {
                    playAudioFile(audioFile)
                } else {
                    Timber.e("Failed to synthesize speech for sample")
                    speakingCallBack?.onError(null, 0)
                }
            }
        } else {
            val utteranceId = "my_utterance_id"
            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            textToSpeech.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        }
    }

    private fun playAudioFile(file: File, deleteAfter: Boolean = true) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    if (deleteAfter) file.delete()
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: $what, $extra")
                    speakingCallBack?.onError(null, what)
                    if (deleteAfter) file.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing sample audio")
            speakingCallBack?.onError(null, 0)
        }
    }

    fun stop() {
        Timber.d("textToSpeech.stop()=>${textToSpeech.isSpeaking}")
        textToSpeech.stop()
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }

    fun isSpeaking(): Boolean {
        return textToSpeech.isSpeaking || (mediaPlayer?.isPlaying == true)
    }
}