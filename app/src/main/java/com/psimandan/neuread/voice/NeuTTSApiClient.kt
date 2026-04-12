package com.psimandan.neuread.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeuTTSApiClient(private val context: Context) {
    // Replace with your computer's local IP address (e.g., 192.168.1.100)
    private val serverUrl = "http://192.168.1.117:8000/tts"

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS) // TTS generation takes time
        .build()

    suspend fun synthesizeSpeech(text: String): File? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("max_new_tokens", 1000)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(serverUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful && response.body != null) {
                // Save the incoming stream to a temporary .wav file
                val tempFile = File(context.cacheDir, "neutts_cache_${System.currentTimeMillis()}.wav")
                val fos = FileOutputStream(tempFile)
                fos.write(response.body!!.bytes())
                fos.close()
                return@withContext tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}