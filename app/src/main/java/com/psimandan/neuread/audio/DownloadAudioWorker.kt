package com.psimandan.neuread.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.psimandan.neuread.R
import com.psimandan.neuread.data.datasource.PrefsStore
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.TextPart
import com.psimandan.neuread.data.repository.LibraryRepository
import com.psimandan.neuread.voice.NeuTTSApiClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.BreakIterator
import java.util.Locale

@HiltWorker
class DownloadAudioWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: LibraryRepository,
    private val prefsStore: PrefsStore
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString("bookId") ?: return@withContext Result.failure()
        val bookTitle = inputData.getString("bookTitle") ?: "Book"
        val voiceName = inputData.getString("voiceName") ?: "en"
        
        val book = repository.getBookById(bookId) ?: return@withContext Result.failure()
        val sourceTexts = when (book) {
            is Book -> book.text
            is AudioBook -> book.parts.map { it.text }
        }
        if (sourceTexts.isEmpty()) return@withContext Result.failure()
        val previousAudioPath = (book as? AudioBook)?.audioFilePath

        try {
            setForeground(createForegroundInfo(0, bookTitle))
        } catch (e: Exception) {
            Timber.e(e, "Failed to set foreground info")
        }
        
        try {
            val clonedVoices = prefsStore.getClonedVoices().first()
            val currentClonedVoice = clonedVoices.find { it.name == voiceName }
            val apiClient = NeuTTSApiClient(context)
            
            // 1. Clean and join text parts to avoid splitting in the middle of a sentence
            // Replace newlines and tabs with spaces to prevent artificial sentence breaks
            val fullText = sourceTexts.joinToString(" ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
            
            // 2. Robust sentence splitting using BreakIterator with correct locale
            val allSentences = mutableListOf<String>()
            val locale = try { 
                val tag = book.language.replace("_", "-")
                if (tag.isBlank()) Locale.getDefault() else Locale.forLanguageTag(tag)
            } catch (_: Exception) {
                Locale.getDefault() 
            }
            
            Timber.d("[SPLIT] Using locale: ${locale.displayName} for splitting")
            
            val iterator = BreakIterator.getSentenceInstance(locale)
            iterator.setText(fullText)
            var start = iterator.first()
            var end = iterator.next()
            while (end != BreakIterator.DONE) {
                val sentence = fullText.substring(start, end).trim()
                if (sentence.isNotBlank()) {
                    // Sub-split if sentence is too long (> 50 words)
                    val words = sentence.split(" ")
                    if (words.size > 50) {
                        var subStart = 0
                        while (subStart < words.size) {
                            val subEnd = minOf(subStart + 45, words.size)
                            // Try to find a comma or semicolon to split at naturally if possible
                            var splitAt = subEnd
                            if (subEnd < words.size) {
                                for (i in subEnd downTo maxOf(subStart + 10, subEnd - 15)) {
                                    if (words[i].contains(",") || words[i].contains(";") || words[i].contains(":")) {
                                        splitAt = i + 1
                                        break
                                    }
                                }
                            }
                            allSentences.add(words.subList(subStart, splitAt).joinToString(" "))
                            subStart = splitAt
                        }
                    } else {
                        allSentences.add(sentence)
                    }
                }
                start = end
                end = iterator.next()
            }

            Timber.d("[SPLIT] Total sentences found: ${allSentences.size}")

            val audioParts = mutableListOf<TextPart>()
            val tempFiles = mutableListOf<File>()
            var currentTotalTimeMs = 0
            
            // 3. Group sentences into synthesis chunks (ensure they end with terminal punctuation)
            val chunkSize = 3
            val sentenceChunks = allSentences.chunked(chunkSize)

            for ((chunkIndex, chunk) in sentenceChunks.withIndex()) {
                Timber.d("[SPLIT] Chunk $chunkIndex: \"${chunk.joinToString(" ").take(100)}...\"")
                var result: NeuTTSApiClient.SynthesisResult? = null
                var attempts = 0
                val maxAttempts = 3
                
                while (result == null && attempts < maxAttempts) {
                    attempts++
                    try {
                        result = if (currentClonedVoice != null) {
                            val clonedLang = if (currentClonedVoice.language.lowercase().startsWith("ro")) "ro" else null
                            apiClient.cloneBatch(
                                sentences = chunk,
                                refText = currentClonedVoice.referenceText,
                                refCodes = currentClonedVoice.codes,
                                language = clonedLang
                            )
                        } else {
                            val integratedVoiceName = voiceName.lowercase().split(" ").firstOrNull()
                            apiClient.synthesizeBatch(chunk, integratedVoiceName)
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Attempt $attempts failed for chunk $chunkIndex")
                        if (attempts >= maxAttempts) throw e
                        kotlinx.coroutines.delay(2000L * attempts) // Exponential backoff
                    }
                }
                
                if (result != null && result.file.exists()) {
                    tempFiles.add(result.file)

                    val durations = result.durationsMs
                    chunk.forEachIndexed { index, sentence ->
                        audioParts.add(TextPart(currentTotalTimeMs, sentence))
                        val duration = durations.getOrNull(index) ?: (getAudioDuration(result.file) / chunk.size).toInt()
                        currentTotalTimeMs += duration
                    }
                }
                
                val progress = (chunkIndex + 1).toFloat() / sentenceChunks.size
                val data = workDataOf("progress" to progress)
                setProgress(data)
                setForeground(createForegroundInfo((progress * 100).toInt(), book.title))
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
                    previousAudioPath?.takeIf { it.isNotBlank() && it != finalAudioFile.absolutePath }?.let { oldPath ->
                        runCatching { File(oldPath).delete() }
                    }
                    repository.selectBook(audioBook.id)
                    return@withContext Result.success()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error in DownloadAudioWorker")
            return@withContext Result.failure()
        }
        
        return@withContext Result.failure()
    }

    private fun getAudioDuration(file: File): Long {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Exception) {
            Timber.w("Failed to read audio duration")
            0L
        } finally {
            retriever.release()
        }
    }

    private fun createForegroundInfo(progress: Int, bookTitle: String): ForegroundInfo {
        val channelId = "download_channel"
        val notificationId = 1001

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Progress of book downloads"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading $bookTitle")
            .setContentText("$progress%")
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setSilent(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }

        return ForegroundInfo(notificationId, notification, type)
    }

    private fun mergeAudioFiles(files: List<File>): File? {
        val outputFile = File(context.filesDir, "audio_book_${System.currentTimeMillis()}.wav")
        try {
            var totalDataSize = 0L
            var wavHeader: ByteArray? = null

            FileOutputStream(outputFile).use { out ->
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
}
