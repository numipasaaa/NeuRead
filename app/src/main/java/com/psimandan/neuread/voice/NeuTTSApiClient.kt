package com.psimandan.neuread.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class NeuTTSApiClient(private val context: Context) {
    // Replace with your computer's local IP address (e.g., 192.168.1.100)
    private val baseUrl = "http://192.168.1.131:8000"

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS) // TTS generation takes time
        .connectTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun synthesizeSpeech(text: String): File? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("max_new_tokens", 1000)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/tts")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful && response.body != null) {
                val tempFile = File(context.cacheDir, "neutts_cache_${System.currentTimeMillis()}.wav")
                response.body!!.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun encodeReference(audioFile: File): List<Int>? = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "ref_audio",
                    audioFile.name,
                    audioFile.asRequestBody("audio/wav".toMediaType())
                )
                .build()

            val request = Request.Builder()
                .url("$baseUrl/encode_reference")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val jsonResponse = JSONObject(response.body!!.string())
                val codesArray = jsonResponse.getJSONArray("codes")
                val codes = mutableListOf<Int>()
                for (i in 0 until codesArray.length()) {
                    codes.add(codesArray.getInt(i))
                }
                return@withContext codes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun cloneWithCodes(
        text: String,
        refText: String,
        refCodes: List<Int>
    ): File? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("text", text)
                put("ref_text", refText)
                put("ref_codes", JSONArray(refCodes))
                put("max_new_tokens", 1500)
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/clone_with_codes")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val tempFile = File(context.cacheDir, "cloned_tts_${System.currentTimeMillis()}.wav")
                response.body!!.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext tempFile
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}