package com.scd.android

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val CMD_LIKE = "com.scd.android.LIKE"
private const val CMD_SHUFFLE = "com.scd.android.SHUFFLE"

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private var brain: PlayerBrain? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastReportedUrn: String? = null
    private var libraryParents = emptySet<String>()

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

        val player = ExoPlayer.Builder(this, EqRenderersFactory(this))
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory, AudioExtractors.factory()),
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
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        runCatching {
            player.preloadConfiguration =
                ExoPlayer.PreloadConfiguration(8_000_000L)
        }
        player.repeatMode = Prefs.repeatMode.coerceIn(0, 2)
        ShuffleQueue.onChanged = { scope.launch(Dispatchers.Main) { updateCustomLayout() } }

        player.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                notifyLibraryChanged()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlayerWidgets.publish(this@PlaybackService, player)
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
                if (shuffleModeEnabled) {
                    player.shuffleModeEnabled = false
                    ShuffleQueue.set(player, true)
                }
                updateCustomLayout()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                Prefs.changeRepeatMode(repeatMode)
            }

            override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                PlayerWidgets.publish(this@PlaybackService, player)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                PlayerWidgets.publish(this@PlaybackService, player)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerWidgets.publish(this@PlaybackService, player)
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

        brain = PlayerBrain(this, player, scope).also { it.start() }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("open_player", true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaLibrarySession.Builder(this, player, SessionCallback())
            .setSessionActivity(pending)
            .setBitmapLoader(ScBitmapLoader(Api.http))
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
        scope.launch(Dispatchers.Main) {
            androidx.compose.runtime.snapshotFlow {
                listOf(Downloads.downloaded, LocalPlaylists.all, Prefs.offline, Prefs.star, Prefs.playBlocked, Prefs.language, Dislikes.urns, BlockedArtists.entries)
            }.collect { notifyLibraryChanged() }
        }
        scope.launch(Dispatchers.Main) {
            androidx.compose.runtime.snapshotFlow { Triple(Prefs.playbackRate, Prefs.pitchMode, Prefs.pitchSemitones) }
                .collect { (rate, mode, semi) -> player.playbackParameters = SpeedPitch.parameters(rate, mode, semi) }
        }
        Together.attachPlayer(player)
        fun route() {
            val device = AudioOutput.resolve(this)
            runCatching { player.setPreferredAudioDevice(device) }
                .onFailure { Logs.add("player", "output route failed: ${it.javaClass.simpleName}") }
        }
        AudioOutput.watch(this) { route() }
        scope.launch(Dispatchers.Main) {
            androidx.compose.runtime.snapshotFlow { Prefs.audioOutput }.collect { route() }
        }
        setListener(object : MediaSessionService.Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Logs.add("player", "foreground start not allowed by system")
            }
        })
        Logs.add("player", "service ready")
    }

    private fun browserCatalog(player: Player): BrowserCatalog = BrowserCatalog(
        queue = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).toTrackOrNull() },
        downloads = Downloads.tracks(),
        playlists = LocalPlaylists.visible,
        labels = listOf(R.string.app_name, R.string.browser_queue, R.string.downloads, R.string.playlists)
            .map { applicationContext.getString(it) },
        playable = { track -> Playback.playable(track) && !track.hidden && (!Prefs.offline || Playback.offlineReady(track)) },
    )

    private fun browserRoot() = BrowserEntry(BrowserCatalog.ROOT, applicationContext.getString(R.string.app_name)).toBrowserItem()

    private fun BrowserEntry.toBrowserItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(browsable)
            .setIsPlayable(!browsable)
            .setMediaType(if (browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC)
        track?.let { t ->
            metadata.setArtist(t.user?.username).setDurationMs(t.displayDuration)
            Api.artworkUrl(t.artwork_url)?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                ?.let { metadata.setArtworkUri(android.net.Uri.parse(it)) }
        }
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata.build()).build()
    }

    @OptIn(UnstableApi::class)
    private fun notifyLibraryChanged() {
        val session = mediaSession ?: return
        val parents = setOf(BrowserCatalog.ROOT, BrowserCatalog.QUEUE, BrowserCatalog.DOWNLOADS, BrowserCatalog.PLAYLISTS) +
            LocalPlaylists.visible.map { BrowserCatalog.playlistId(it.id) }
        val subscribed = (libraryParents + parents).filter { session.getSubscribedControllers(it).isNotEmpty() }
        libraryParents = parents
        if (subscribed.isEmpty()) return
        val counts = browserCatalog(session.player).counts()
        subscribed.forEach { parent ->
            session.notifyChildrenChanged(parent, counts[parent] ?: 0, null)
        }
    }

    private inner class SessionCallback : MediaLibrarySession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val builder = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                .remove(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT)
            if (trusted(session, controller)) {
                cmds.add(SessionCommand(CMD_LIKE, Bundle.EMPTY))
                    .add(SessionCommand(CMD_SHUFFLE, Bundle.EMPTY))
            }
            builder.setAvailableSessionCommands(cmds.build())
            return builder.build()
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(browserRoot(), params))

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == BrowserCatalog.ROOT) return Futures.immediateFuture(LibraryResult.ofItem(browserRoot(), null))
            val item = browserCatalog(session.player).item(mediaId)
            return Futures.immediateFuture(
                if (item == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItem(item.toBrowserItem(), null),
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val children = browserCatalog(session.player).children(parentId, page, pageSize)
            return Futures.immediateFuture(
                if (children == null) LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
                else LibraryResult.ofItemList(children.map { it.toBrowserItem() }, params),
            )
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            if (controller.packageName == packageName && mediaItems.all { it.localConfiguration != null }) {
                return Futures.immediateFuture(mediaItems)
            }
            val tracks = browserCatalog(session.player).resolve(mediaItems.map { it.mediaId })
                ?: return Futures.immediateFailedFuture(IllegalArgumentException("Unknown library item"))
            return Futures.immediateFuture(tracks.map { it.toMediaItem() })
        }

        @OptIn(UnstableApi::class)
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = Futures.transform(
            onAddMediaItems(mediaSession, controller, mediaItems),
            { items ->
                if (controller.packageName != packageName) Playback.externalQueue()
                MediaSession.MediaItemsWithStartPosition(items.orEmpty(), startIndex, startPositionMs)
            },
            MoreExecutors.directExecutor(),
        )

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
                    scope.launch(Dispatchers.Main) {
                        ShuffleQueue.toggle(session.player)
                        updateCustomLayout()
                    }
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
            .setDisplayName(getString(if (liked) R.string.notif_unlike else R.string.notif_like))
            .setCustomIconResId(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart)
            .setSessionCommand(SessionCommand(CMD_LIKE, Bundle.EMPTY))
            .setEnabled(true)
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun shuffleButton(): CommandButton {
        val on = ShuffleQueue.active(mediaSession?.player)
        return CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setDisplayName(getString(if (on) R.string.notif_shuffle_on else R.string.notif_shuffle))
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
        if (!isScUrn(prev)) return
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
        if (Prefs.prefetchWifiOnly && NetMonitor.isMetered(this)) return
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
        if (urn == lastReportedUrn || !isScUrn(urn)) return
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        prefetchWriter?.cancel()
        Together.detachPlayer()
        brain?.stop()
        brain = null
        AudioOutput.unwatch(this)
        scope.cancel()
        mediaSession?.run {
            runCatching { player.pause() }
            PlayerWidgets.publish(this@PlaybackService, player)
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
