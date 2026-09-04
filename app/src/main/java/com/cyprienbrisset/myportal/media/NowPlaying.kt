package com.cyprienbrisset.myportal.media

import android.graphics.Bitmap
import android.media.session.PlaybackState

data class NowPlaying(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap? = null,
)

fun isPlaying(state: Int): Boolean = state == PlaybackState.STATE_PLAYING

/** First "active" (playing) index, else 0 if any exist, else -1. */
fun indexOfActive(playing: List<Boolean>): Int {
    if (playing.isEmpty()) return -1
    val i = playing.indexOfFirst { it }
    return if (i >= 0) i else 0
}
