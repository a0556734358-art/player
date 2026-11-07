package com.example.offlineplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/** קובץ מדיה מהמכשיר. מזהה מתחיל ב-"a:" לשמע וב-"v:" לווידאו. */
data class MediaFile(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isVideo: Boolean,
) {
    fun toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.ifBlank { null })
                    .build()
            )
            .build()
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
