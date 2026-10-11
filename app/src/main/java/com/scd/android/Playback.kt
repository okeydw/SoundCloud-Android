package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object Playback {

    fun playable(t: Track) = !t.starLocked && (!t.unavailable || Prefs.playBlocked) && (!t.external || Downloads.isDownloaded(t.urn))

    fun play(player: Player?, list: List<Track>, track: Track, autoContinue: Boolean = false) {
        if (!playable(track)) return
        val p = player ?: return
        val queue = list.filter(::playable)
        val index = queue.indexOfFirst { it.urn == track.urn }.coerceAtLeast(0)
        NowPlaying.autoContinue = autoContinue
        LocalRadio.stopTrackRadio()
        p.setMediaItems(queue.map { it.toMediaItem() }, index, 0)
        p.prepare()
        p.play()
    }

    suspend fun playSingle(player: () -> Player?, track: Track): Boolean = withContext(Dispatchers.Main) {
        var p = player()
        var waited = 0
        while (p == null && waited < 100) {
            delay(100)
            waited++
            p = player()
        }
        if (p == null) return@withContext false
        NowPlaying.autoContinue = false
        LocalRadio.stopTrackRadio()
        p.setMediaItems(listOf(track.toMediaItem()), 0, 0)
        p.prepare()
        p.play()
        true
    }

    @Volatile
    private var radioJob: Job? = null

    var radioBusy by mutableStateOf(false)
        private set

    fun startTrackRadio(player: Player, seed: Track, onDone: (Int) -> Unit = {}) {
        radioJob?.cancel()
        radioBusy = true
        radioJob = App.scope.launch {
            val added = runCatching { buildRadio(player, seed) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Logs.add("radio", "failed: ${it.javaClass.simpleName} ${it.message?.take(80) ?: ""}")
                0
            }
            withContext(Dispatchers.Main) {
                radioBusy = false
                onDone(added)
            }
        }
    }

    private suspend fun buildRadio(player: Player, seed: Track): Int {
        fun usable(list: List<Track>) = list.distinctBy { it.urn }.filter {
            it.urn != seed.urn && playable(it) && !it.hidden
        }
        LocalRadio.startFrom(seed, listOf(seed.urn))
        val started = System.currentTimeMillis()
        val (add, source) = coroutineScope {
            val server = async { usable(Api.waveFromTrack(seed.urn, budgetMs = 12_000)) }
            val local = async { withTimeoutOrNull(25_000) { usable(LocalRadio.trackBatch(20)) } ?: emptyList() }
            val fromServer = server.await()
            Logs.add("radio", "server: ${fromServer.size} in ${(System.currentTimeMillis() - started) / 1000}s")
            if (fromServer.isNotEmpty()) {
                local.cancel()
                fromServer to "server"
            } else {
                val fromLocal = local.await()
                Logs.add("radio", "local: ${fromLocal.size} in ${(System.currentTimeMillis() - started) / 1000}s")
                fromLocal to "local"
            }
        }
        if (add.isEmpty()) {
            Logs.add("radio", "nothing found for ${seed.title.take(30)}")
            LocalRadio.stopTrackRadio()
            return 0
        }
        withContext(Dispatchers.Main) {
            NowPlaying.autoContinue = true
            val cur = player.currentMediaItemIndex
            if (player.currentMediaItem?.mediaId != seed.urn) {
                player.setMediaItems(listOf(seed.toMediaItem()) + add.map { it.toMediaItem() }, 0, 0)
                player.prepare()
                player.play()
            } else {
                if (player.mediaItemCount > cur + 1) player.removeMediaItems(cur + 1, player.mediaItemCount)
                player.addMediaItems(add.map { it.toMediaItem() })
            }
        }
        Logs.add("radio", "+${add.size} from $source")
        return add.size
    }

    fun playNext(player: Player?, track: Track): Boolean {
        val p = player ?: return false
        if (!playable(track)) return false
        if (p.mediaItemCount == 0 || p.currentMediaItem == null) {
            play(p, listOf(track), track)
            return true
        }
        val current = p.currentMediaItemIndex
        if (p.currentMediaItem?.mediaId == track.urn) return true
        val existing = (0 until p.mediaItemCount).firstOrNull { it != current && p.getMediaItemAt(it).mediaId == track.urn }
        val target = current + 1
        if (existing != null) {
            p.moveMediaItem(existing, if (existing < target) target - 1 else target)
        } else {
            p.addMediaItem(target, track.toMediaItem())
        }
        return true
    }

    fun radioNow(player: Player, seed: Track, onDone: (Int) -> Unit = {}) {
        if (!playable(seed)) return
        if (player.currentMediaItem?.mediaId != seed.urn) {
            NowPlaying.autoContinue = true
            player.setMediaItems(listOf(seed.toMediaItem()), 0, 0)
            player.prepare()
            player.play()
        }
        startTrackRadio(player, seed, onDone)
    }

    suspend fun artistRadio(urn: String, fallback: List<Track>): List<Track> =
        Api.waveFromArtist(urn)
            .filter { playable(it) && !it.hidden }
            .ifEmpty { fallback.filter(::playable).shuffled() }
}
