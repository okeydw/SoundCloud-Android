package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
    private val healedUrns = java.util.Collections.synchronizedSet(mutableSetOf<String>())

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
        val auto = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        val manual = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK

        if (prevUrn != null && prevUrn != mediaItem?.mediaId) {
            if (manual && prevPos < 30_000L) WaveFeedback.negative(prevUrn)
            if (auto) WaveFeedback.positive(prevUrn)
        }

        if (auto && prevUrn != null && prevUrn != mediaItem?.mediaId &&
            !Downloads.isDownloaded(prevUrn) && prevUrn !in healedUrns
        ) {
            val expected = DurationCache.get(prevUrn)
            if (expected > 60_000L && prevPos in 1L until 10_000L) {
                healedUrns.add(prevUrn)
                Logs.add("player", "трек оборвался на ${prevPos / 1000}с из ${expected / 1000}с → перекачиваю")
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
            Logs.add("player", "дизлайк → пропуск")
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

        if (!noSource && httpCode == null && position > 3_000 && resumeRetries < 3) {
            resumeRetries++
            Logs.add("player", "${error.errorCodeName} at ${position / 1000}s → resume")
            player.seekTo(position)
            player.prepare()
            player.play()
            return
        }

        if (!noSource && player.mediaItemCount > 0 && streamRetries < Endpoints.streamHosts.size) {
            streamRetries++
            Logs.add("player", "${error.errorCodeName}${httpCode?.let { " [HTTP $it]" } ?: ""} → rotate stream")
            Endpoints.rotateStream()
            Logs.add("host", "stream → ${Endpoints.streamBase.removePrefix("https://")}")
            val idx = player.currentMediaItemIndex
            val pos = player.currentPosition
            val rebuilt = (0 until player.mediaItemCount).map { i ->
                val mi = player.getMediaItemAt(i)
                if (Downloads.isDownloaded(mi.mediaId)) mi
                else mi.buildUpon().setUri(Api.streamUrl(mi.mediaId)).build()
            }
            player.setMediaItems(rebuilt, idx, pos)
            player.prepare()
            player.play()
            return
        }

        player.currentMediaItem?.mediaId?.let { ScDataSource.invalidate(it) }
        if (player.hasNextMediaItem() && skipped < 3) {
            Logs.add("player", "трек не играется → следующий")
            skipped++
            streamRetries = 0
            resumeRetries = 0
            player.seekToNext()
            player.prepare()
            player.play()
            return
        }

        val detail = buildString {
            append(error.errorCodeName)
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
        if (Downloads.isDownloaded(urn)) return
        if (!Prefs.star || !Prefs.hqStreaming) return
        if (urn in snippetHandled) return

        val expected = maxOf(item.mediaMetadata.extras?.getLong("duration") ?: 0L, DurationCache.get(urn))
        val actual = player.duration
        if (actual <= 0L || actual == C.TIME_UNSET) return
        val snippet = kotlin.math.abs(actual - 10_000L) < 1_500L || kotlin.math.abs(actual - 30_000L) < 1_500L
        if (!snippet) return
        val goPlus = item.mediaMetadata.extras?.getBoolean("go_plus") == true
        if (expected > 45_000L) {
            if (actual >= expected - 5_000L) return
        } else if (!goPlus) {
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
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        val count = player.mediaItemCount
        val urn = player.currentMediaItem?.mediaId ?: return
        val tail = if (WaveFeedback.isOwned(urn)) 5 else 2
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
        if (add.isEmpty() && WaveFeedback.isOwned(urn) && !WaveState.loading) {
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
