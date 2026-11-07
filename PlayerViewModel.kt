package com.example.offlineplayer

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val positions = PositionStore(app)
    private val playlistStore = PlaylistStore(app)
    private val settings = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // ספרייה ורשימות
    var library by mutableStateOf<List<MediaFile>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var playlists by mutableStateOf(playlistStore.all())
        private set

    // מצב נגן
    var controller by mutableStateOf<MediaController?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentId by mutableStateOf<String?>(null)
        private set
    var currentTitle by mutableStateOf("")
        private set
    var currentArtist by mutableStateOf("")
        private set
    var speed by mutableFloatStateOf(1f)
        private set

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        connect()
    }

    private fun connect() {
        val ctx = getApplication<Application>()
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            c.addListener(listener)
            c.setPlaybackSpeed(settings.getFloat(KEY_SPEED, 1f))
            controller = c
            sync(c)
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync(p: Player) {
        isPlaying = p.isPlaying
        currentId = p.currentMediaItem?.mediaId
        currentTitle = p.mediaMetadata.title?.toString().orEmpty()
        currentArtist = p.mediaMetadata.artist?.toString().orEmpty()
        speed = p.playbackParameters.speed
    }

    fun refresh() {
        if (loading) return
        loading = true
        viewModelScope.launch {
            library = withContext(Dispatchers.IO) { MediaRepository.load(getApplication()) }
            loading = false
        }
    }

    /** מנגן את הרשימה החל מהפריט [index], וממשיך מנקודת העצירה השמורה שלו. */
    fun play(files: List<MediaFile>, index: Int) {
        val c = controller ?: return
        if (index !in files.indices) return
        val file = files[index]
        val saved = positions.get(file.id)
        val start = if (saved > 3_000 && saved < file.durationMs - 5_000) saved else 0L
        c.setMediaItems(files.map { it.toMediaItem() }, index, start)
        c.prepare()
        c.play()
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(c.currentMediaItemIndex, 0L)
            c.play()
        }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setSpeed(value: Float) {
        settings.edit().putFloat(KEY_SPEED, value).apply()
        controller?.setPlaybackSpeed(value)
        speed = value
    }

    // רשימות השמעה
    fun createPlaylist(name: String) {
        playlistStore.create(name)
        playlists = playlistStore.all()
    }

    fun deletePlaylist(name: String) {
        playlistStore.delete(name)
        playlists = playlistStore.all()
    }

    fun addToPlaylist(name: String, mediaId: String) {
        playlistStore.add(name, mediaId)
        playlists = playlistStore.all()
    }

    fun removeFromPlaylist(name: String, mediaId: String) {
        playlistStore.remove(name, mediaId)
        playlists = playlistStore.all()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onCleared()
    }

    private companion object {
        const val KEY_SPEED = "speed"
    }
}
