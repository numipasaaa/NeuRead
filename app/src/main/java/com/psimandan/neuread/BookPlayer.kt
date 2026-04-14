package com.psimandan.neuread

import com.psimandan.neuread.data.model.Bookmark

enum class PlaybackSource {
    USER_ACTION,
    AUTO_PLAY,
    BOOKMARK,
    SEEK,
    OTHER
}

interface BookPlayer {
    fun onPlay(source: PlaybackSource = PlaybackSource.OTHER)
    fun onPlayFromBookmark(position: Int)
    fun onStopSpeaking()
    fun onFastForward()
    fun onRewind()
    fun onUserChangePosition(value: Float)
    fun onJumpToChapter(position: Int)
    fun onSaveBookmark()
    fun onDeleteBookmark(bookmark: Bookmark)
    fun onClose()

    fun currentTimeElapsed(): Long
}