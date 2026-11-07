package com.example.offlineplayer

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** סורק את קבצי השמע והווידאו שבמכשיר דרך MediaStore. */
object MediaRepository {

    fun load(context: Context): List<MediaFile> =
        runCatching { queryAudio(context) + queryVideo(context) }.getOrDefault(emptyList())

    private fun queryAudio(context: Context): List<MediaFile> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
        )
        // מסנן צלצולים, התראות ואזעקות
        val selection = "${MediaStore.Audio.Media.DURATION} >= 1000 AND " +
            "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val result = ArrayList<MediaFile>()
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val artist = c.getString(artistCol).orEmpty().let { if (it == "<unknown>") "" else it }
                result += MediaFile(
                    id = "a:$id",
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol).orEmpty().ifBlank { "ללא שם" },
                    artist = artist,
                    durationMs = c.getLong(durCol),
                    isVideo = false,
                )
            }
        }
        return result
    }

    private fun queryVideo(context: Context): List<MediaFile> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
        )
        val selection = "${MediaStore.Video.Media.DURATION} >= 1000"
        val sort = "${MediaStore.Video.Media.TITLE} COLLATE NOCASE ASC"

        val result = ArrayList<MediaFile>()
        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                result += MediaFile(
                    id = "v:$id",
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(titleCol).orEmpty().ifBlank { "ללא שם" },
                    artist = "",
                    durationMs = c.getLong(durCol),
                    isVideo = true,
                )
            }
        }
        return result
    }
}
