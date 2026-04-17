package com.psimandan.neuread.voice

import android.content.Context
import timber.log.Timber
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

    data class SynthesisResult(val file: File, val durationsMs: List<Int>)

    private val client = OkHttpClient.Builder()
        .readTimeout(300, TimeUnit.SECONDS) // TTS generation takes time
        .connectTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun synthesizeSpeech(text: String, voice: String? = null): File? = synthesizeBatch(listOf(text), voice)?.file

    suspend fun synthesizeBatch(sentences: List<String>, voice: String? = null): SynthesisResult? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("sentences", JSONArray(sentences))
                put("pause_seconds", 0.3)
                if (voice != null) {
                    put("voice", voice)
                }
            }

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/tts")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            if (response.isSuccessful && response.body != null) {
                val durations = response.header("X-Sentence-Durations-Ms")?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                val tempFile = File(context.cacheDir, "neutts_batch_${System.currentTimeMillis()}.wav")
                response.body!!.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext SynthesisResult(tempFile, durations)
            } else {
                val errorBody = response.body?.string()
                Timber.e("NeuTTS synthesizeBatch failed: ${response.code} - $errorBody")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in synthesizeBatch")
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
                val rawCodes = jsonResponse.get("codes")
                val codes = mutableListOf<Int>()

                if (rawCodes is JSONArray) {
                    // Handle both flat [1,2,3] and nested [[1,2,3]] formats
                    val firstElement = if (rawCodes.length() > 0) rawCodes.get(0) else null
                    if (firstElement is JSONArray) {
                        for (i in 0 until firstElement.length()) {
                            codes.add(firstElement.getInt(i))
                        }
                    } else {
                        for (i in 0 until rawCodes.length()) {
                            codes.add(rawCodes.getInt(i))
                        }
                    }
                }
                return@withContext codes
            } else {
                Timber.e("Encode reference failed: ${response.code}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error encoding reference")
        }
        return@withContext null
    }

    suspend fun cloneWithCodes(
        text: String,
        refText: String,
        refCodes: List<Int>
    ): File? = cloneBatch(listOf(text), refText, refCodes)?.file

    suspend fun cloneBatch(
        sentences: List<String>,
        refText: String,
        refCodes: List<Int>
    ): SynthesisResult? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject()
            json.put("sentences", JSONArray(sentences))
            json.put("ref_text", refText)
            
            // Explicitly build the codes array to ensure it's a flat list of integers
            val codesArray = JSONArray()
            refCodes.forEach { codesArray.put(it) }
            json.put("ref_codes", codesArray)
            
            json.put("pause_seconds", 0.3)

            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("$baseUrl/clone_with_codes")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful && response.body != null) {
                val durations = response.header("X-Sentence-Durations-Ms")?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                val tempFile = File(context.cacheDir, "cloned_batch_${System.currentTimeMillis()}.wav")
                response.body!!.byteStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return@withContext SynthesisResult(tempFile, durations)
            } else {
                val errorBody = response.body?.string()
                Timber.e("NeuTTS cloneBatch failed: ${response.code} - $errorBody")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in cloneBatch")
        }
        return@withContext null
    }
}