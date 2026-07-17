package com.scd.android

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CMD_LIKE = "com.scd.android.LIKE"
private const val CMD_DISLIKE = "com.scd.android.DISLIKE"

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
                updateCustomLayout()
            }
        })

        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra("open_player", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .setCallback(SessionCallback())
            .build()

        updateCustomLayout()
    }

    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                .add(SessionCommand(CMD_DISLIKE, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val track = session.player.currentMediaItem?.toTrackOrNull()
            if (track != null) {
                scope.launch {
                    when (customCommand.customAction) {
                        CMD_LIKE -> Likes.toggle(track)
                        CMD_DISLIKE -> if (Dislikes.toggle(track)) {
                            launch(Dispatchers.Main) { session.player.seekToNext() }
                        }
                    }
                    launch(Dispatchers.Main) { updateCustomLayout() }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    private fun likeButton(): CommandButton {
        val urn = mediaSession?.player?.currentMediaItem?.mediaId
        val liked = urn != null && Likes.isLiked(urn)
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setCustomIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .build()
    }

    private fun dislikeButton(): CommandButton {
        val urn = mediaSession?.player?.currentMediaItem?.mediaId
        val disliked = urn != null && Dislikes.isDisliked(urn)
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName("Dislike")
            .setCustomIconResId(if (disliked) R.drawable.ic_thumb_down_filled else R.drawable.ic_thumb_down)
            .setSessionCommand(SessionCommand(CMD_DISLIKE, Bundle.EMPTY))
            .build()
    }

    private fun updateCustomLayout() {
        mediaSession?.setCustomLayout(ImmutableList.of(likeButton(), dislikeButton()))
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
