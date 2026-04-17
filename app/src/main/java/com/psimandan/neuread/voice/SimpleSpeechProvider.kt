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
        textToSpeech.language = locale
        textToSpeech.voice = voice
        textToSpeech.setSpeechRate(rate)
    }

    fun speak(text: String) {
        if (currentVoice.isNetworkConnectionRequired) {
            scope.launch {
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
                        // Check if it's one of the integrated voices (Jo, Dave)
                        val voiceName = currentVoice.name.lowercase().split(" ").firstOrNull()
                        apiClient.synthesizeSpeech(text, voiceName)
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

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    file.delete()
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: $what, $extra")
                    speakingCallBack?.onError(null, what)
                    file.delete()
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