package com.psimandan.neuread.ui.player

import com.psimandan.neuread.data.model.Bookmark
import com.psimandan.neuread.data.model.Chapter
import com.psimandan.neuread.data.model.NeuReadBook

sealed class PlayerEvent {
    data object PlayClick : PlayerEvent()
    data object PauseClick : PlayerEvent()
    data object FastForward : PlayerEvent()
    data object FastRewind : PlayerEvent()
    data object AddBookmark : PlayerEvent()
    data object BackToLibrary : PlayerEvent()
    data object Settings : PlayerEvent()
    data class BookmarkClick(val position: Float) : PlayerEvent()
    data class DeleteBookmark(val bookmark: Bookmark) : PlayerEvent()
    data class UpdateBookmarkNote(val bookmark: Bookmark, val note: String) : PlayerEvent()
    data class SliderValueChange(val value: Float) : PlayerEvent()
    data class ChapterClick(val chapter: Chapter) : PlayerEvent()
    data object ToggleExtendedTextMode : PlayerEvent()
    data class SetSleepTimer(val minutes: Int) : PlayerEvent()
    data object CancelSleepTimer : PlayerEvent()
}

fun PlayerEvent.onEvent(
    model: PlayerViewModel,
    onSettings: (NeuReadBook) -> Unit,
    onBackToLibrary: () -> Unit,
    onPlayback: (Float) -> Unit
) {
    when (this) {
        PlayerEvent.PlayClick -> {
            model.onPlay()
            onPlayback(0f)
        }
        PlayerEvent.PauseClick -> {
            model.onPause()
            onPlayback(0f)
        }
        PlayerEvent.FastForward -> model.fastForward()
        PlayerEvent.FastRewind -> model.fastRewind()
        PlayerEvent.AddBookmark -> model.saveBookmark()
        PlayerEvent.BackToLibrary -> onBackToLibrary()
        PlayerEvent.Settings -> model.book?.let(onSettings)
        is PlayerEvent.BookmarkClick -> model.playFromBookmark(this.position.toInt())
        is PlayerEvent.DeleteBookmark -> model.deleteBookmark(this.bookmark)
        is PlayerEvent.UpdateBookmarkNote -> model.updateBookmarkNote(this.bookmark, this.note)
        is PlayerEvent.SliderValueChange -> model.onSliderValueChange(this.value)
        is PlayerEvent.ChapterClick -> model.jumpToChapter(this.chapter)
        PlayerEvent.ToggleExtendedTextMode -> model.toggleExtendedTextMode()
        is PlayerEvent.SetSleepTimer -> model.setSleepTimer(this.minutes)
        PlayerEvent.CancelSleepTimer -> model.cancelSleepTimer()
    }
}
