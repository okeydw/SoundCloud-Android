package com.scd.android

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastReportedUrn: String? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportHistory(mediaItem)
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun reportHistory(item: MediaItem?) {
        val mediaItem = item ?: return
        val urn = mediaItem.mediaId.takeIf { it.isNotEmpty() } ?: return
        if (urn == lastReportedUrn) return
        lastReportedUrn = urn

        val md = mediaItem.mediaMetadata
        val title = md.title?.toString() ?: return
        scope.launch {
            runCatching {
                Api.postHistory(
                    urn = urn,
                    title = title,
                    artistName = md.artist?.toString() ?: "",
                    artistUrn = md.extras?.getString("artist_urn"),
                    artworkUrl = md.extras?.getString("artwork_url"),
                    duration = md.extras?.getLong("duration") ?: 0L,
                )
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
