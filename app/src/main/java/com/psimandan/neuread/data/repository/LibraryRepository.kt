package com.psimandan.neuread.data.repository

import com.psimandan.neuread.data.datasource.LibraryDataSource
import com.psimandan.neuread.data.datasource.LibraryDiskDataSource
import com.psimandan.neuread.data.model.NeuReadBook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

interface LibraryRepository {
    fun getLibraryBooks(): Flow<List<NeuReadBook>>
    suspend fun addBook(book: NeuReadBook)
    suspend fun updateBook(book: NeuReadBook)
    suspend fun deleteBook(book: NeuReadBook)

    suspend fun selectBook(bookId: String)
    suspend fun getSelectedBook(): NeuReadBook?
    suspend fun unselectBook()
    suspend fun getBookById(bookId: String): NeuReadBook?
}

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val diskDataSource: LibraryDiskDataSource,
    private val assetDataSource: LibraryDataSource
) : LibraryRepository {

    override fun getLibraryBooks(): Flow<List<NeuReadBook>> = flow {
        val books = diskDataSource.loadBooks()
        if (books.isEmpty()) {
            // Load from assets if disk library is empty
            val defaultBooks = assetDataSource.loadBooks()
            defaultBooks.forEach { book -> diskDataSource.addBook(book) }
            emit(defaultBooks)
        } else {
            emit(books)
        }
    }

    override suspend fun addBook(book: NeuReadBook) = diskDataSource.addBook(book)
    override suspend fun updateBook(book: NeuReadBook) = diskDataSource.updateBook(book)
    override suspend fun deleteBook(book: NeuReadBook) = diskDataSource.deleteBook(book)

    override suspend fun selectBook(bookId: String) = diskDataSource.selectBook(bookId)
    override suspend fun getSelectedBook(): NeuReadBook? = diskDataSource.getSelectedBook()
    override suspend fun unselectBook() = diskDataSource.unselectBook()

    override suspend fun getBookById(bookId: String): NeuReadBook? = diskDataSource.getBookById(bookId)
}