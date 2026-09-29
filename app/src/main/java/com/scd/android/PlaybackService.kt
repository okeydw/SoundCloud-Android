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
import androidx.media3.session.SessionError
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
private const val CMD_SHUFFLE = "com.scd.android.SHUFFLE"

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastReportedUrn: String? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val local = androidx.media3.datasource.DefaultDataSource.Factory(this)
        val network =
            androidx.media3.datasource.DefaultDataSource.Factory(this, ScDataSource.Factory(Api.http))

        val dataSourceFactory: androidx.media3.datasource.DataSource.Factory = runCatching {
            val cache = MediaCache.get(this)
            scope.launch { runCatching { MediaCache.prune() } }
            val cached = androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(network)
                .setCacheKeyFactory { spec -> MediaCache.keyOf(spec.uri.toString()) }
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            prefetchFactory = cached
            Logs.add("player", "media cache on")
            RoutingDataSource.Factory(local, cached)
        }.getOrElse { e ->
            Logs.add("player", "media cache off: ${e.javaClass.simpleName}: ${e.message}")
            RoutingDataSource.Factory(local, network)
        }
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 180_000, 1_500, 3_000)
            .setBackBuffer(30_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory),
            )
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        runCatching {
            player.preloadConfiguration =
                ExoPlayer.PreloadConfiguration(8_000_000L)
        }

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                reportListenEvent(mediaItem?.mediaId, reason)
                historyJob?.cancel()
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) lastReportedUrn = null
                updateCustomLayout()
                if (player.isPlaying) {
                    watchForHistory(player)
                    prefetchNext(player)
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateCustomLayout()
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                AudioFx.attach(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    watchForHistory(player)
                    prefetchNext(player)
                    startTracker(player)
                } else {
                    historyJob?.cancel()
                    trackerJob?.cancel()
                    if (player.currentMediaItem?.mediaId == evUrn) {
                        evPos = player.currentPosition
                        evDur = player.duration.coerceAtLeast(0L)
                    }
                }
                updateCustomLayout()
            }
        })

        val openIntent = Intent(this, MainActivity::class.java).apply {
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
            .setBitmapLoader(
                androidx.media3.session.CacheBitmapLoader(ScBitmapLoader(Api.http)),
            )
            .build()

        runCatching {
            setMediaNotificationProvider(
                androidx.media3.session.DefaultMediaNotificationProvider.Builder(this)
                    .setNotificationId(1101)
                    .setChannelId("playback")
                    .build()
                    .apply { setSmallIcon(R.drawable.ic_music) },
            )
        }

        updateCustomLayout()
        scope.launch(Dispatchers.Main) {
            androidx.compose.runtime.snapshotFlow { Likes.urns }.collect { updateCustomLayout() }
        }
        AudioFx.attach(player.audioSessionId)
        scope.launch(Dispatchers.Main) {
            androidx.compose.runtime.snapshotFlow { Triple(Prefs.eqEnabled, Prefs.eqPreset, Prefs.eqBands) }
                .collect { AudioFx.apply() }
        }
        Logs.add("player", "service ready")
    }

    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            if (trusted(session, controller)) {
                val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
                    .build()
                builder.setAvailableSessionCommands(cmds)
            }
            return builder.build()
        }

        @OptIn(UnstableApi::class)
        private fun trusted(session: MediaSession, controller: MediaSession.ControllerInfo): Boolean =
            controller.packageName == packageName ||
                session.isMediaNotificationController(controller) ||
                session.isAutoCompanionController(controller) ||
                session.isAutomotiveController(controller)

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (!trusted(session, controller)) {
                return Futures.immediateFuture(SessionResult(SessionError.ERROR_PERMISSION_DENIED))
            }
            when (customCommand.customAction) {
                CMD_SHUFFLE -> {
                    session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                    scope.launch(Dispatchers.Main) { updateCustomLayout() }
                }
                CMD_LIKE -> {
                    val track = session.player.currentMediaItem?.toTrackOrNull()
                    if (track != null) {
                        scope.launch(Dispatchers.Main) {
                            Likes.toggle(track)
                            updateCustomLayout()
                        }
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    @OptIn(UnstableApi::class)
    private fun likeButton(): CommandButton {
        val urn = mediaSession?.player?.currentMediaItem?.mediaId
        val liked = urn != null && Likes.isLiked(urn)
        return CommandButton.Builder(
            if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
        )
            .setDisplayName(if (liked) "Unlike" else "Like")
            .setCustomIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .setEnabled(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun shuffleButton(): CommandButton {
        val on = mediaSession?.player?.shuffleModeEnabled == true
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(if (on) "Перемешивание включено" else "Перемешать")
            .setCustomIconResId(if (on) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
            .setSessionCommand(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            .setEnabled(true)
            .build()
    }

    private fun updateCustomLayout() {
        mediaSession?.setCustomLayout(ImmutableList.of(shuffleButton(), likeButton()))
    }

    private var historyJob: kotlinx.coroutines.Job? = null

    private var evUrn: String? = null
    private var evPos = 0L
    private var evDur = 0L
    private var trackerJob: kotlinx.coroutines.Job? = null

    private fun startTracker(player: Player) {
        trackerJob?.cancel()
        trackerJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val urn = player.currentMediaItem?.mediaId
                if (urn != null && urn == evUrn) {
                    evPos = player.currentPosition
                    evDur = player.duration.coerceAtLeast(0L)
                } else if (urn != null && evUrn == null) {
                    evUrn = urn
                }
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    private fun reportListenEvent(nextUrn: String?, reason: Int) {
        val prev = evUrn
        val pos = evPos
        val dur = evDur
        evUrn = nextUrn
        evPos = 0L
        evDur = 0L
        if (prev == null || prev == nextUrn && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) return
        when (reason) {
            Player.MEDIA_ITEM_TRANSITION_REASON_AUTO,
            Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> Events.fullPlay(prev)
            Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> {
                if (dur <= 0L) return
                val pct = pos.toDouble() / dur
                if (pct >= 0.9) Events.fullPlay(prev) else Events.skip(prev, pct)
            }
        }
    }

    private var prefetchFactory: androidx.media3.datasource.cache.CacheDataSource.Factory? = null
    private var prefetchJob: kotlinx.coroutines.Job? = null

    @Volatile
    private var prefetchWriter: androidx.media3.datasource.cache.CacheWriter? = null
    private var prefetchedUrn: String? = null

    @OptIn(UnstableApi::class)
    private fun prefetchNext(player: Player) {
        val factory = prefetchFactory ?: return
        if (Prefs.offline) return
        val idx = player.nextMediaItemIndex
        if (idx == C.INDEX_UNSET) return
        val item = player.getMediaItemAt(idx)
        val urn = item.mediaId
        if (urn.isEmpty() || urn == prefetchedUrn || Downloads.isDownloaded(urn)) return
        val uri = item.localConfiguration?.uri ?: return
        if (uri.scheme != "http" && uri.scheme != "https") return
        prefetchJob?.cancel()
        prefetchWriter?.cancel()
        prefetchedUrn = urn
        prefetchJob = scope.launch {
            kotlinx.coroutines.delay(5_000)
            val writer = androidx.media3.datasource.cache.CacheWriter(
                factory.createDataSource(),
                androidx.media3.datasource.DataSpec(uri),
                null,
                null,
            )
            prefetchWriter = writer
            val started = System.currentTimeMillis()
            runCatching { writer.cache() }
                .onSuccess { Logs.add("player", "next track cached in ${(System.currentTimeMillis() - started) / 1000}s") }
                .onFailure { if (it !is InterruptedException) prefetchedUrn = null }
            prefetchWriter = null
        }
    }

    private fun watchForHistory(player: Player) {
        val urn = player.currentMediaItem?.mediaId?.takeIf { it.isNotEmpty() } ?: return
        if (urn == lastReportedUrn) return
        historyJob?.cancel()
        historyJob = scope.launch(Dispatchers.Main) {
            while (true) {
                kotlinx.coroutines.delay(2_000)
                if (player.currentMediaItem?.mediaId != urn) return@launch
                val pos = player.currentPosition
                val dur = player.duration
                if (pos >= 30_000L || (dur > 0L && pos >= dur / 2)) {
                    reportHistory(player.currentMediaItem)
                    return@launch
                }
            }
        }
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
        prefetchWriter?.cancel()
        AudioFx.release()
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
