package com.cyprienbrisset.myportal.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat
import com.cyprienbrisset.myportal.system.MediaListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NowPlayingController(private val context: Context) {
    private val _state = MutableStateFlow<NowPlaying?>(null)
    val state: StateFlow<NowPlaying?> = _state

    private val component = ComponentName(context, MediaListenerService::class.java)
    private val msm = context.getSystemService(MediaSessionManager::class.java)
    private var controller: MediaController? = null

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refreshFromController() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refreshFromController() }
        override fun onSessionDestroyed() { refresh() }
    }

    fun hasAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun refresh() {
        if (!hasAccess()) { detach(); controller = null; _state.value = null; return }
        val sessions = try { msm.getActiveSessions(component) } catch (e: SecurityException) { emptyList() }
        val playingFlags = sessions.map { isPlaying(it.playbackState?.state ?: PlaybackState.STATE_NONE) }
        val idx = indexOfActive(playingFlags)
        val next = if (idx >= 0) sessions.getOrNull(idx) else null
        if (next?.sessionToken != controller?.sessionToken) {
            detach()
            controller = next
            controller?.registerCallback(cb)
        }
        refreshFromController()
    }

    private fun refreshFromController() {
        val c = controller
        if (c == null) { _state.value = null; return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val playing = isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE)
        _state.value = if (title.isBlank() && artist.isBlank()) null else NowPlaying(title, artist, playing, art)
    }

    fun toggle() {
        val c = controller ?: return
        if (isPlaying(c.playbackState?.state ?: PlaybackState.STATE_NONE)) c.transportControls.pause()
        else c.transportControls.play()
    }
    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    private fun detach() { controller?.unregisterCallback(cb) }
    fun dispose() { detach(); controller = null }
}
