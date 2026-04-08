package com.psimandan.neuread

import com.psimandan.neuread.data.model.Bookmark

interface BookPlayer {
    fun onPlay(source: Int)
    fun onPlayFromBookmark(position: Int)
    fun onStopSpeaking()
    fun onFastForward()
    fun onRewind()
    fun onUserChangePosition(value: Float)
    fun onSaveBookmark()
    fun onDeleteBookmark(bookmark: Bookmark)
    fun onClose()

    fun currentTimeElapsed(): Long
}