package com.psimandan.neuread.ui.voicecloning

import android.annotation.SuppressLint
import android.app.Application
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.psimandan.neuread.data.datasource.ClonedVoice
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.voice.NeuTTSApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class VoiceCloningViewModel @Inject constructor(
    application: Application,
    private val prefsStore: PrefsStore
) : AndroidViewModel(application) {

    private val apiClient = NeuTTSApiClient(application)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _recordingCompleted = MutableStateFlow(false)
    val recordingCompleted: StateFlow<Boolean> = _recordingCompleted

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading

    private val _uploadSuccess = MutableStateFlow<Boolean?>(null)
    val uploadSuccess: StateFlow<Boolean?> = _uploadSuccess

    private var audioRecord: AudioRecord? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentOutputFile: File? = null

    private val sampleRate = 44100
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    fun resetRecording() {
        _recordingCompleted.value = false
        _uploadSuccess.value = null
        _isRecording.value = false
        stopPlayback()
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        _recordingCompleted.value = false
        _uploadSuccess.value = null
        val context = getApplication<Application>().applicationContext
        val outputDir = context.cacheDir
        val outputFile = File(outputDir, "voice_sample_${System.currentTimeMillis()}.wav")
        currentOutputFile = outputFile

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Timber.e("AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        _isRecording.value = true

        viewModelScope.launch(Dispatchers.IO) {
            writeAudioDataToFile(outputFile)
        }
    }

    private suspend fun writeAudioDataToFile(file: File) {
        val data = ByteArray(bufferSize)
        try {
            FileOutputStream(file).use { os ->
                // Write placeholder for WAV header
                writeWavHeader(os, channelConfig, sampleRate, audioFormat, 0)
                
                while (_isRecording.value) {
                    val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                    if (read > 0) {
                        os.write(data, 0, read)
                    }
                }
            }
            // Update WAV header with actual size
            updateWavHeader(file)
        } catch (e: IOException) {
            Timber.e(e, "Error writing audio data to file")
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        channelConfig: Int,
        sampleRate: Int,
        audioFormat: Int,
        dataLength: Long
    ) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val bitDepth = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val byteRate = (sampleRate * channels * bitDepth / 8).toLong()
        val totalDataLen = dataLength + 36
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate.toLong() and 0xff).toByte()
        header[25] = (sampleRate.toLong() shr 8 and 0xff).toByte()
        header[26] = (sampleRate.toLong() shr 16 and 0xff).toByte()
        header[27] = (sampleRate.toLong() shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitDepth / 8).toByte() // block align
        header[33] = 0
        header[34] = bitDepth.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (dataLength and 0xff).toByte()
        header[41] = (dataLength shr 8 and 0xff).toByte()
        header[42] = (dataLength shr 16 and 0xff).toByte()
        header[43] = (dataLength shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File) {
        val dataLength = file.length() - 44
        val totalDataLen = dataLength + 36
        val raf = RandomAccessFile(file, "rw")
        try {
            raf.seek(4) // Size of RIFF chunk
            raf.write(
                byteArrayOf(
                    (totalDataLen and 0xff).toByte(),
                    (totalDataLen shr 8 and 0xff).toByte(),
                    (totalDataLen shr 16 and 0xff).toByte(),
                    (totalDataLen shr 24 and 0xff).toByte()
                )
            )
            raf.seek(40) // Size of data chunk
            raf.write(
                byteArrayOf(
                    (dataLength and 0xff).toByte(),
                    (dataLength shr 8 and 0xff).toByte(),
                    (dataLength shr 16 and 0xff).toByte(),
                    (dataLength shr 24 and 0xff).toByte()
                )
            )
        } finally {
            raf.close()
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        try {
            audioRecord?.apply {
                stop()
                release()
            }
            _recordingCompleted.value = true
        } catch (e: Exception) {
            Timber.e(e, "Error stopping AudioRecord")
        } finally {
            audioRecord = null
            Timber.d("Recording stopped. File: ${currentOutputFile?.absolutePath}")
        }
    }

    fun playRecording() {
        val file = currentOutputFile ?: return
        if (!file.exists()) return

        stopPlayback()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                _isPlaying.value = true
                setOnCompletionListener {
                    _isPlaying.value = false
                    stopPlayback()
                }
            } catch (e: IOException) {
                Timber.e(e, "MediaPlayer prepare() failed")
            }
        }
    }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        _isPlaying.value = false
    }

    fun uploadVoice(name: String, language: String, referenceText: String) {
        val file = currentOutputFile ?: return
        if (!file.exists()) return

        viewModelScope.launch {
            _isUploading.value = true
            try {
                val codes = apiClient.encodeReference(file)
                if (codes != null) {
                    val persistentFile = File(
                        getApplication<Application>().filesDir,
                        "voice_sample_${System.currentTimeMillis()}.wav"
                    )
                    file.inputStream().use { input ->
                        persistentFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    val voice = ClonedVoice(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        language = language,
                        referenceText = referenceText,
                        codes = codes,
                        samplePath = persistentFile.absolutePath
                    )
                    prefsStore.saveClonedVoice(voice)
                    _uploadSuccess.value = true
                } else {
                    _uploadSuccess.value = false
                }
            } catch (e: Exception) {
                Timber.e(e, "Upload failed")
                _uploadSuccess.value = false
            } finally {
                _isUploading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (_isRecording.value) {
            stopRecording()
        }
        stopPlayback()
    }
}


