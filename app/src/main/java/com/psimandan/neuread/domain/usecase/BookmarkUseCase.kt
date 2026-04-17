package com.psimandan.neuread.domain.usecase

import com.psimandan.neuread.BookPlayer
import com.psimandan.neuread.data.model.Bookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkUseCase {
    suspend fun saveBookmark()
    suspend fun deleteBookmark(bookmark: Bookmark)
    suspend fun playFromBookmark(position: Int)
    suspend fun updateBookmarkNote(bookmark: Bookmark, note: String)
    fun getBookmarks(): Flow<List<Bookmark>>
    fun setBookPlayer(player: BookPlayer)
}