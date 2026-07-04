package com.psimandan.neuread.data.model

import com.psimandan.extensions.formatSecondsToHMS
import com.psimandan.neuread.voice.languageId
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class Bookmark(
    val position: Int,
    var title: String = "",
    var note: String? = null
)

@Serializable
data class Chapter(
    val title: String,
    val startIndex: Int
)

@Serializable
@SerialName("textbook")
data class Book(
    override val id: String = UUID.randomUUID().toString(),
    override val title: String,
    override val author: String,
    override val language: String = Locale.getDefault().languageId(),
    val voiceIdentifier: String = "",
    override val voiceRate: Float,
    val text: List<String>,
    override val lastPosition: Int,
    @SerialName("book_source")
    val bookSource: String = "",
    @SerialName("created")
    override val updated: Long,
    override val bookmarks: MutableList<Bookmark> = mutableListOf(),
    override val chapters: List<Chapter> = emptyList()
) : NeuReadBook() {

    override fun playerType(): BookPlayerType = BookPlayerType.TTS

    override fun lazyCalculate(completion: () -> Unit) {
        _state.value = _state.value.copy(isCalculating = true)
        coroutineScope.launch(Dispatchers.IO) {
            val words = text.flatMap { it.split(Regex("\\s+")) }.filter { it.isNotEmpty() }
            val totalSeconds = calculateTotalDuration(words)
            val elapsedSeconds = calculateElapsedTime(words, lastPosition)

            withContext(Dispatchers.Main) {
                _state.value = BookUIState(
                    isCompleted = (lastPosition + 5) >= words.size,
                    isCalculating = false,
                    progressTime = elapsedSeconds.formatSecondsToHMS(),
                    totalTime = totalSeconds.formatSecondsToHMS(),
                    totalTimeSeconds = totalSeconds.toLong()
                )
                completion()
            }
        }
    }

    private fun calculateTotalDuration(words: List<String>): Double {
        var totalChars = 0
        for (word in words) {
            totalChars += word.length + 1
        }
        return (totalChars * SECONDS_PER_CHARACTER) / voiceRate
    }


    private fun calculateElapsedTime(words: List<String>, progress: Int): Double {
        var totalChars = 0
        for (i in 0 until minOf(progress, words.size)) {
            totalChars += words[i].length + 1
        }
        return (totalChars * SECONDS_PER_CHARACTER) / voiceRate
    }

    companion object {
        const val SECONDS_PER_CHARACTER = 0.080
    }
}

