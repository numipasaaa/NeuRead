package com.psimandan.neuread.voice

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class NeuTTSSpeechProvider(
    private val context: Context,
    private val speakingCallBack: SimpleSpeakingCallBack?
) {
    private val apiClient = NeuTTSApiClient(context)
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    var isSpeaking = false
        private set

    fun speak(text: String) {
        if (text.isBlank()) {
            speakingCallBack?.onError(null, 0)
            return
        }

        isSpeaking = true

        scope.launch {
            // 1. Fetch audio from FastAPI server
            val audioFile = apiClient.synthesizeSpeech(text)

            if (audioFile != null && audioFile.exists()) {
                playAudioFile(audioFile)
            } else {
                Timber.e("Failed to synthesize speech from NeuTTS API")
                isSpeaking = false
                speakingCallBack?.onError(null, 0)
            }
        }
    }

    private fun playAudioFile(file: File) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    isSpeaking = false
                    // Notify the player to move to the next frame
                    // In the native TTS this is handled by UtteranceProgressListener.onDone
                    file.delete() // Clean up cache
                }
                setOnErrorListener { _, what, extra ->
                    Timber.e("MediaPlayer error: $what, $extra")
                    isSpeaking = false
                    speakingCallBack?.onError(null, what)
                    file.delete()
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error playing NeuTTS audio")
            isSpeaking = false
            speakingCallBack?.onError(null, 0)
        }
    }

    fun stop() {
        isSpeaking = false
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
        }
        mediaPlayer = null
    }
}