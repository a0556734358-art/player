package com.example.offlineplayer

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * שירות נגינה ברקע. Media3 מציג בעצמו התראה עם כפתורי שליטה
 * ומטפל בכפתורי אוזניות / מסך נעול דרך ה-MediaSession.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private lateinit var positions: PositionStore
    private val handler = Handler(Looper.getMainLooper())

    private val periodicSave = object : Runnable {
        override fun run() {
            saveNow()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        positions = PositionStore(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                handler.removeCallbacks(periodicSave)
                if (isPlaying) {
                    handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
                } else {
                    saveNow()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val oldId = oldPosition.mediaItem?.mediaId ?: return
                if (oldId == newPosition.mediaItem?.mediaId) return
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    positions.clear(oldId) // הקובץ הסתיים
                } else {
                    positions.save(oldId, oldPosition.positionMs)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return
                // מעבר אוטומטי או כפתור "הבא/הקודם": ממשיכים מנקודת העצירה השמורה
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    val saved = positions.get(mediaItem.mediaId)
                    if (saved > MIN_RESUME_MS) player.seekTo(saved)
                }
            }
        })

        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicSave)
        saveNow()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private fun saveNow() {
        val item = player.currentMediaItem ?: return
        val pos = player.currentPosition
        val dur = player.duration
        if (dur != C.TIME_UNSET && pos >= dur - END_THRESHOLD_MS) {
            positions.clear(item.mediaId)
        } else {
            positions.save(item.mediaId, pos)
        }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 3_000L
        const val MIN_RESUME_MS = 3_000L
        const val END_THRESHOLD_MS = 5_000L
    }
}
