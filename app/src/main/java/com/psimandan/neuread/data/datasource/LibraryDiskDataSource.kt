package com.psimandan.neuread.data.datasource

import android.content.Context
import com.psimandan.neuread.data.model.AudioBook
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.BookPlayerType
import com.psimandan.neuread.data.model.NeuReadBook
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Singleton

@Singleton
class LibraryDiskDataSource @Inject constructor(@ApplicationContext private val context: Context) :
    LibraryDataSource {
    private val libraryDir: File
        get() = File(context.filesDir, "library").apply { if (!exists()) mkdirs() }

    private val audiobooksDir: File
        get() = File(context.filesDir, "audiobooks").apply { if (!exists()) mkdirs() }


    override fun loadBooks(): List<NeuReadBook> {
        val booksMap = mutableMapOf<String, NeuReadBook>()
        val json = Json { ignoreUnknownKeys = true }
        
        // Load regular books
        libraryDir.listFiles()?.forEach { file ->
            try {
                val jsonText = file.readText()
                val book = json.decodeFromString(Book.serializer(), jsonText)
                booksMap[book.id] = book
            } catch (e: Exception) {
                Timber.e(e, "Error loading book: ${file.name}")
            }
        }
        
        // Load audiobooks (priority over regular books)
        audiobooksDir.listFiles()?.forEach { file ->
            try {
                val jsonText = file.readText()
                val book = json.decodeFromString(AudioBook.serializer(), jsonText)
                booksMap[book.id] = book
            } catch (e: Exception) {
                Timber.e(e, "Error loading audiobook: ${file.name}")
            }
        }
        return booksMap.values.toList()
    }

    override suspend fun addBook(book: NeuReadBook) {
        if (book is AudioBook) {
            val bookFile = File(audiobooksDir, "${book.id}.json")
            bookFile.writeText(Json.encodeToString(book))
            // Ensure we remove any old text-only version
            File(libraryDir, "${book.id}.json").delete()
        } else {
            val bookFile = File(libraryDir, "${book.id}.json")
            bookFile.writeText(Json.encodeToString(book))
            // Ensure we remove any old audio version
            File(audiobooksDir, "${book.id}.json").delete()
        }
    }

    override suspend fun updateBook(book: NeuReadBook) {
        if (book is AudioBook) {
            val audioFile = File(audiobooksDir, "${book.id}.json")
            audioFile.writeText(Json.encodeToString(book))
            // Clean up text-only version if it exists
            File(libraryDir, "${book.id}.json").delete()
        } else {
            val textFile = File(libraryDir, "${book.id}.json")
            textFile.writeText(Json.encodeToString(book))
            // Clean up audio version if it exists
            File(audiobooksDir, "${book.id}.json").delete()
        }
    }

    override suspend fun deleteBook(book: NeuReadBook) {
        try {
            if (book is AudioBook) {
                val mp3File = File(book.audioFilePath)
                val bookFile = File(audiobooksDir, "${book.id}.json")
                Timber.d("deleteBook=>${bookFile.absolutePath}")
                mp3File.delete()
                Timber.d("${book.audioFilePath} - has been deleted")
                bookFile.delete()
                Timber.d("${bookFile.absolutePath} - has been deleted")
            } else {
                val bookFile = File(libraryDir, "${book.id}.json")
                Timber.d("deleteBook=>${bookFile.absolutePath}")
                bookFile.delete()
                Timber.d("${bookFile.absolutePath} - has been deleted")
            }
        } catch (e: Exception) {
            Timber.e(e)
        }

        unselectBook()
    }

    private val selectedDir: File
        get() = File(context.filesDir, "selected").apply { if (!exists()) mkdirs() }

    private val selectedFile: File
        get() = File(selectedDir, "selected_book.txt")

    override suspend fun selectBook(bookId: String) {
        selectedFile.writeText(bookId)
    }

    override suspend fun getSelectedBook(): NeuReadBook? = withContext(Dispatchers.IO) {
        if (!selectedFile.exists()) return@withContext null

        val selectedId = async {
            selectedFile.inputStream().bufferedReader().use { it.readText().trim() }
        }.await()
        val selectedBookFile1 = File(libraryDir, "$selectedId.json")
        val selectedBookFile2 = File(audiobooksDir, "$selectedId.json")
        val json = Json { ignoreUnknownKeys = true }
        val book = if (selectedBookFile2.exists()) {
            val jsonText = async {
                selectedBookFile2.inputStream().bufferedReader().use { it.readText() }
            }.await()
            async { json.decodeFromString(AudioBook.serializer(), jsonText) }.await()
        } else if (selectedBookFile1.exists()) {
            val jsonText = async {
                selectedBookFile1.inputStream().bufferedReader().use { it.readText() }
            }.await()
            async { json.decodeFromString(Book.serializer(), jsonText) }.await()
        } else {
            null
        }
        return@withContext book
    }

    override suspend fun unselectBook() {
        if (selectedFile.exists()) {
            selectedFile.delete()
        }
    }

    override suspend fun getBookById(bookId: String): NeuReadBook? {
        return loadBooks().find { it.id == bookId }
    }
}
