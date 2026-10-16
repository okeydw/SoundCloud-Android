package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayerBrain(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var streamRetries = 0
    private var resumeRetries = 0
    private var skipped = 0

    private var curUrn: String? = null
    private var curPos = 0L
    private var positionJob: Job? = null
    private var fillJob: Job? = null
    private var fadeJob: Job? = null

    private val snippetHandled = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val previewUrns = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val healedUrns = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val purgedUrns = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val localFailed = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val localRetried = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val localSwaps = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun swapLocal(urn: String, file: java.io.File, position: Long) {
        val uri = file.toUri()
        for (i in 0 until player.mediaItemCount) {
            val mi = player.getMediaItemAt(i)
            if (mi.mediaId == urn && mi.localConfiguration?.uri != uri) {
                player.replaceMediaItem(i, mi.buildUpon().setUri(uri).build())
            }
        }
        if (player.currentMediaItem?.mediaId == urn) player.seekTo(player.currentMediaItemIndex, position)
        player.prepare()
        player.play()
    }

    private fun redownload(urn: String) {
        val track = Downloads.tracks().firstOrNull { it.urn == urn } ?: return
        localFailed.add(urn)
        scope.launch(Dispatchers.Main) {
            Downloads.redownload(context, listOf(track)) { u, fixed -> scope.launch(Dispatchers.Main) { onRepaired(u, fixed) } }
        }
    }

    private fun onRepaired(urn: String, file: java.io.File) {
        localFailed.remove(urn)
        localRetried.remove(urn)
        val playing = player.currentMediaItem?.mediaId == urn
        val position = if (playing) player.currentPosition else 0L
        Logs.add("player", "repaired download ready${if (playing) ", switching at ${position / 1000}s" else ""}")
        if (!playing) {
            for (i in 0 until player.mediaItemCount) {
                val mi = player.getMediaItemAt(i)
                if (mi.mediaId == urn) player.replaceMediaItem(i, mi.buildUpon().setUri(file.toUri()).build())
            }
            return
        }
        val wasPlaying = player.isPlaying
        swapLocal(urn, file, position)
        if (!wasPlaying) player.pause()
    }

    private fun describe(error: PlaybackException): String {
        val root = generateSequence(error.cause) { it.cause }.take(6).lastOrNull()
        val scheme = player.currentMediaItem?.localConfiguration?.uri?.scheme ?: "?"
        val what = root?.let { " (${it.javaClass.simpleName}${it.message?.let { m -> ": ${m.take(80)}" }.orEmpty()})" }.orEmpty()
        return "${error.errorCodeName}$what $scheme"
    }

    private fun playableFile(urn: String): java.io.File? =
        Downloads.fileFor(urn).takeIf { urn !in localFailed && Downloads.isDownloaded(urn) && Downloads.looksLikeAudio(it) }

    fun start() {
        player.addListener(this)
        curUrn = player.currentMediaItem?.mediaId
        scope.launch(Dispatchers.Main) {
            snapshotFlow { Prefs.crossfade to Prefs.crossfadeSeconds }.collect { (on, _) -> setCrossfade(on) }
        }
    }

    fun stop() {
        player.removeListener(this)
        positionJob?.cancel()
        fillJob?.cancel()
        fadeJob?.cancel()
    }

    private fun trackPosition() {
        positionJob?.cancel()
        positionJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val urn = player.currentMediaItem?.mediaId
                if (urn != null && urn == curUrn) curPos = player.currentPosition
                delay(500)
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            streamRetries = 0
            resumeRetries = 0
            skipped = 0
            trackPosition()
        } else {
            positionJob?.cancel()
            if (player.currentMediaItem?.mediaId == curUrn) curPos = player.currentPosition
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            DurationCache.record(player.currentMediaItem?.mediaId, player.duration)
            retryIfSnippet()
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val prevUrn = curUrn
        val prevPos = curPos
        curUrn = mediaItem?.mediaId
        curPos = 0L
        if (prevUrn != null && prevUrn != curUrn) Downloads.upgradeInBackground(prevUrn)
        if (prevUrn != null && prevUrn != curUrn && previewUrns.remove(prevUrn)) ScDataSource.invalidate(prevUrn)
        val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val manual = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

        if (prevUrn != null && prevUrn != mediaItem?.mediaId) {
            if (manual && prevPos < 30_000L) WaveFeedback.negative(prevUrn)
            if (auto) WaveFeedback.positive(prevUrn)
        }

        if (auto && prevUrn != null && prevUrn != mediaItem?.mediaId && Downloads.isDownloaded(prevUrn) &&
            prevUrn !in healedUrns && prevUrn !in localFailed
        ) {
            val expected = DurationCache.get(prevUrn)
            val cut = prevPos in 1L until 11_500L || prevPos in 28_000L until 31_500L
            if (expected > 45_000L && cut && prevPos < expected - 15_000L) {
                healedUrns.add(prevUrn)
                Logs.add("player", "downloaded copy stopped at ${prevPos / 1000}s of ${expected / 1000}s → re-downloading it")
                redownload(prevUrn)
            }
        }

        if (auto && prevUrn != null && prevUrn != mediaItem?.mediaId &&
            !Downloads.isDownloaded(prevUrn) && prevUrn !in healedUrns
        ) {
            val expected = DurationCache.get(prevUrn)
            if (expected > 60_000L && prevPos in 1L until 10_000L) {
                healedUrns.add(prevUrn)
                Logs.add("player", "track cut off at ${prevPos / 1000}s of ${expected / 1000}s → refetching")
                val back = player.previousMediaItemIndex
                if (back != C.INDEX_UNSET) {
                    player.pause()
                    scope.launch(Dispatchers.Main) {
                        withContext(Dispatchers.IO) { ScDataSource.invalidateNow(prevUrn) }
                        player.seekTo(back, 0L)
                        player.prepare()
                        player.play()
                    }
                    return
                }
                ScDataSource.invalidate(prevUrn)
            }
        }

        val urn = mediaItem?.mediaId ?: return
        if (auto && Dislikes.isDisliked(urn) && player.hasNextMediaItem()) {
            Logs.add("player", "disliked → skip")
            player.seekToNextMediaItem()
            return
        }
        scheduleFill()
    }

    override fun onPlayerError(error: PlaybackException) {
        val cause = error.cause
        val httpCode = (cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
        val noSource = NoPlayableSourceException.matches(error) ||
            ScDataSource.failedRecently(player.currentMediaItem?.mediaId)
        val position = player.currentPosition
        val mixed = generateSequence(error as Throwable) { it.cause }.take(8).any { it is MixedSourceException }

        if (!noSource && !mixed && httpCode == null && position > 3_000 && resumeRetries < 3) {
            resumeRetries++
            Logs.add("player", "${error.errorCodeName} at ${position / 1000}s → resume")
            player.seekTo(position)
            player.prepare()
            player.play()
            return
        }

        val item = player.currentMediaItem
        val urn = item?.mediaId
        val scheme = item?.localConfiguration?.uri?.scheme?.lowercase()
        val local = scheme != null && scheme != "http" && scheme != "https"
        if (urn != null && item != null && local) {
            val path = item.localConfiguration?.uri?.path
            val current = Downloads.localFile(urn)
            if (current != null && path != null && current.path != path && localSwaps.add("$urn ${current.path}")) {
                Logs.add("player", "${describe(error)} → downloaded file was replaced, reopening ${current.name}")
                swapLocal(urn, current, position)
                return
            }
            if (current != null && localRetried.add(urn)) {
                Logs.add("player", "${error.errorCodeName} ${AudioProbe.describe(error)} → reopening downloaded file")
                swapLocal(urn, current, position)
                return
            }
            if (localFailed.add(urn)) {
                val file = Downloads.fileFor(urn)
                Logs.add("player", "${describe(error)} → downloaded file unplayable (${file.length() / 1024} KB, audio ${Downloads.looksLikeAudio(file)}) → stream, re-downloading")
                redownload(urn)
                player.replaceMediaItem(player.currentMediaItemIndex, item.buildUpon().setUri(Api.streamUrl(urn)).build())
                player.prepare()
                player.play()
                return
            }
        }

        if (!noSource && urn != null && !local && purgedUrns.add(urn)) {
            if (purgedUrns.size > 300) purgedUrns.retainAll(setOf(urn))
            val idx = player.currentMediaItemIndex
            val why = describe(error)
            scope.launch(Dispatchers.Main) {
                val file = withContext(Dispatchers.IO) {
                    ScDataSource.invalidateNow(urn)
                    playableFile(urn)
                }
                Logs.add("player", "$why → ${if (file != null) "playing downloaded file" else "clearing track cache and retrying"}")
                if (idx >= player.mediaItemCount || player.getMediaItemAt(idx).mediaId != urn) return@launch
                if (file != null) player.replaceMediaItem(idx, player.getMediaItemAt(idx).buildUpon().setUri(file.toUri()).build())
                player.seekTo(idx, position)
                player.prepare()
                player.play()
            }
            return
        }

        if (!noSource && player.mediaItemCount > 0 && streamRetries < Endpoints.streamHosts.size) {
            streamRetries++
            Logs.add("player", "${describe(error)}${httpCode?.let { " [HTTP $it]" } ?: ""} → rotate stream")
            if (urn != null && !local && streamRetries == 1) {
                ScDataSource.blameSource(urn)
                ScDataSource.invalidateNow(urn)
            }
            Endpoints.rotateStream()
            Logs.add("host", "stream → ${Endpoints.streamBase.removePrefix("https://")}")
            val idx = player.currentMediaItemIndex
            val pos = player.currentPosition
            val rebuilt = (0 until player.mediaItemCount).map { i ->
                val mi = player.getMediaItemAt(i)
                val file = if (Downloads.isDownloaded(mi.mediaId) && mi.mediaId !in localFailed) Downloads.fileFor(mi.mediaId).takeIf { it.exists() } else null
                mi.buildUpon().setUri(file?.toUri() ?: Api.streamUrl(mi.mediaId).toUri()).build()
            }
            player.setMediaItems(rebuilt, idx, pos)
            player.prepare()
            player.play()
            return
        }

        player.currentMediaItem?.mediaId?.let { ScDataSource.invalidate(it) }
        if (player.hasNextMediaItem() && skipped < 3) {
            Logs.add("player", "track won't play → next")
            skipped++
            streamRetries = 0
            resumeRetries = 0
            player.seekToNext()
            player.prepare()
            player.play()
            return
        }

        val detail = buildString {
            append(describe(error))
            error.message?.let { append(": ").append(it) }
            if (httpCode != null) append(" [HTTP ").append(httpCode).append("]")
            else if (cause != null) append(" (").append(cause.javaClass.simpleName).append(")")
        }
        skipped = 0
        player.pause()
        Logs.add("player", detail)
        android.util.Log.e("SCDPlayer", "playback error: $detail", error)
        if (Prefs.streamDebug) {
            android.widget.Toast.makeText(context, detail, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun setCrossfade(on: Boolean) {
        fadeJob?.cancel()
        if (!on) {
            player.volume = 1f
            return
        }
        val seconds = Prefs.crossfadeSeconds.coerceIn(1, 30) * 1000f
        fadeJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val dur = player.duration
                val pos = player.currentPosition
                val vol = if (dur <= 0 || dur == C.TIME_UNSET) {
                    1f
                } else {
                    val out = minOf(seconds, dur / 3f).coerceAtLeast(300f)
                    val inLen = (out / 2f).coerceIn(400f, 6000f)
                    val fadeIn = (pos / inLen).coerceIn(0f, 1f)
                    val fadeOut = if (player.hasNextMediaItem()) ((dur - pos) / out).coerceIn(0f, 1f) else 1f
                    minOf(fadeIn, fadeOut)
                }
                player.volume = vol
                delay(if (player.isPlaying) 60 else 500)
            }
        }
    }

    private fun retryIfSnippet() {
        val item = player.currentMediaItem ?: return
        val urn = item.mediaId.takeIf { it.isNotEmpty() } ?: return
        val uri = item.localConfiguration?.uri
        val local = uri != null && uri.scheme != "http" && uri.scheme != "https"

        val expected = maxOf(item.mediaMetadata.extras?.getLong("duration") ?: 0L, DurationCache.get(urn))
        val actual = player.duration
        if (actual <= 0L || actual == C.TIME_UNSET) return
        if (!ScDataSource.isSnippetLength(actual)) return
        if (local) {
            if (expected <= 45_000L || actual >= expected - 5_000L || !healedUrns.add(urn)) return
            val file = uri?.path?.let { java.io.File(it) }
            scope.launch(Dispatchers.Main) {
                val info = withContext(Dispatchers.IO) { file?.let { AudioProbe.inspect(it) } }
                Logs.add(
                    "player",
                    "downloaded file plays as ${actual / 1000}s of ${expected / 1000}s " +
                        "(${file?.name}, ${(file?.length() ?: 0L) / 1024} KB, samples ${info?.ms?.div(1000) ?: "?"}s, header ${info?.declaredMs?.div(1000) ?: "?"}s) → stream, re-downloading",
                )
                redownload(urn)
                val i = player.currentMediaItemIndex
                val cur = player.currentMediaItem
                if (cur?.mediaId == urn) {
                    player.replaceMediaItem(i, cur.buildUpon().setUri(Api.streamUrl(urn)).build())
                    player.prepare()
                    player.play()
                }
            }
            return
        }
        val goPlus = item.mediaMetadata.extras?.getBoolean("go_plus") == true
        if (expected > 45_000L) {
            if (actual >= expected - 5_000L) return
        } else if (!goPlus) {
            return
        }

        if (!Prefs.star || !Prefs.hqStreaming || urn in snippetHandled) {
            if (previewUrns.add(urn)) {
                Logs.add("player", "only a ${actual / 1000}s preview of ${expected / 1000}s is available, not keeping it cached")
                android.widget.Toast.makeText(context, context.getString(R.string.preview_only), android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        snippetHandled.add(urn)
        Logs.add("player", "snippet ${actual / 1000}s vs ${expected / 1000}s → retry via hq")
        ScDataSource.forceHq(urn, true)
        ScDataSource.invalidate(urn)
        val index = player.currentMediaItemIndex
        player.replaceMediaItem(index, item.buildUpon().setUri(Api.streamUrl(urn, hq = true)).build())
        player.prepare()
        player.play()

        scope.launch(Dispatchers.Main) {
            delay(30_000)
            if (player.currentMediaItem?.mediaId == urn && !player.isPlaying) {
                Logs.add("player", "hq retry timed out → back to normal path")
                ScDataSource.forceHq(urn, false)
                val i = player.currentMediaItemIndex
                player.currentMediaItem?.let {
                    player.replaceMediaItem(i, it.buildUpon().setUri(Api.streamUrl(urn)).build())
                }
                player.prepare()
                player.play()
            }
        }
    }

    private fun scheduleFill() {
        fillJob?.cancel()
        fillJob = scope.launch(Dispatchers.Main) { fillQueue() }
    }

    private suspend fun fillQueue() {
        if (Prefs.offline) return
        if (player.repeatMode == Player.REPEAT_MODE_ONE) return
        val count = player.mediaItemCount
        val urn = player.currentMediaItem?.mediaId ?: return
        val wave = WaveFeedback.isOwned(urn)
        if (player.repeatMode == Player.REPEAT_MODE_ALL && !wave) return
        val tail = if (wave) 5 else 2
        if (count == 0 || player.currentMediaItemIndex < count - tail) return
        val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        fun usable(list: List<Track>) = list.filter {
            it.urn !in existing && !it.starLocked && (!it.unavailable || Prefs.playBlocked) &&
                !it.hidden
        }
        var add = emptyList<Track>()
        var source = ""
        if (LocalRadio.trackRadioSeed != null) {
            add = usable(LocalRadio.trackBatch(20))
            source = "track radio"
        }
        if (add.isEmpty() && wave && !WaveState.loading) {
            WaveState.loading = true
            add = try {
                usable(runCatching { WaveState.more() }.getOrDefault(emptyList()))
            } finally {
                WaveState.loading = false
            }
            source = "wave cursor"
        }
        if (add.isEmpty()) {
            add = usable(Api.waveFromTrack(urn))
            source = "wave from track"
        }
        if (add.isEmpty()) {
            add = usable(runCatching { Api.relatedTracks(urn, 20).collection }.getOrDefault(emptyList()))
            source = "related"
        }
        if (add.isEmpty()) {
            add = usable(withContext(Dispatchers.IO) { ScAnon.related(urn, 20) })
            source = "soundcloud related"
        }
        if (add.isEmpty()) {
            Logs.add("queue", "autopilot: nothing found after ${urn.substringAfterLast(':')}")
            return
        }
        if (player.currentMediaItem?.mediaId != urn) return
        Logs.add("queue", "autopilot +${add.size} from $source")
        player.addMediaItems(add.map { it.toMediaItem() })
    }
}
