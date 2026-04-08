package com.psimandan.neuread.data.datasource

import android.content.Context
import com.psimandan.neuread.data.model.Book
import com.psimandan.neuread.data.model.NeuReadBook
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

interface LibraryDataSource {
    fun loadBooks(): List<NeuReadBook>

    suspend fun addBook(book: NeuReadBook)
    suspend fun updateBook(book: NeuReadBook)
    suspend fun deleteBook(book: NeuReadBook)

     suspend fun selectBook(bookId: String)
     suspend fun getSelectedBook(): NeuReadBook?
     suspend fun unselectBook()
}

@Singleton
class LibraryAssetDataSource @Inject constructor(@ApplicationContext private val context: Context) :
    LibraryDataSource {
    override fun loadBooks(): List<NeuReadBook> {
        val books = mutableListOf<NeuReadBook>()
        val assetManager = context.assets
        val fileNames = assetManager.list("default") ?: emptyArray()

        for (fileName in fileNames) {
            val inputStream = assetManager.open("default/$fileName")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonText = reader.readText()
            reader.close()
            inputStream.close()
            val json = Json { ignoreUnknownKeys = true }
            val book = json.decodeFromString(Book.serializer(), jsonText)
            books.add(book)
        }
        return books
    }

    override suspend fun addBook(book: NeuReadBook) = throw UnsupportedOperationException()
    override suspend fun updateBook(book: NeuReadBook) = throw UnsupportedOperationException()
    override suspend fun deleteBook(book: NeuReadBook) = throw UnsupportedOperationException()

    override suspend fun selectBook(bookId: String) = throw UnsupportedOperationException()
    override suspend fun getSelectedBook(): NeuReadBook? = throw UnsupportedOperationException()
    override suspend fun unselectBook() = throw UnsupportedOperationException()



}