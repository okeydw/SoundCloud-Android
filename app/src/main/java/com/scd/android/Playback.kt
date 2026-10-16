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

    var source by mutableStateOf<String?>(null)
        private set

    fun shuffledFrom(key: String): Boolean = source == key && NowPlaying.shuffle && NowPlaying.urn != null

    fun offlineReady(t: Track): Boolean = Downloads.isDownloaded(t.urn) || MediaCache.holds(t.urn) || ScDataSource.hasSpooled(t.urn)

    private fun queueOf(list: List<Track>): List<Track> {
        val offline = Prefs.offline
        return list.filter { playable(it) && (!offline || offlineReady(it)) }.distinctBy { it.urn }
    }

    private var generation = 0L
    private var extendJob: Job? = null

    fun play(
        player: Player?,
        list: List<Track>,
        track: Track,
        autoContinue: Boolean = false,
        startMs: Long = 0L,
        source: String? = null,
        more: (suspend () -> List<Track>)? = null,
    ) {
        if (!playable(track)) return
        if (Prefs.offline && !offlineReady(track)) return
        val p = player ?: return
        val queue = queueOf(list)
        this.source = source
        val gen = ++generation
        extendJob?.cancel()
        val index = queue.indexOfFirst { it.urn == track.urn }.coerceAtLeast(0)
        NowPlaying.autoContinue = autoContinue
        LocalRadio.stopTrackRadio()
        if (p.shuffleModeEnabled) p.shuffleModeEnabled = false
        val (arranged, start) = ShuffleQueue.arrange(queue, index)
        p.setMediaItems(arranged.map { it.toMediaItem() }, start, if (arranged.getOrNull(start)?.urn == track.urn) startMs else 0L)
        p.prepare()
        p.play()
        if (more != null) extend(p, gen, more)
    }

    fun playShuffled(player: Player?, list: List<Track>, autoContinue: Boolean = false, source: String? = null, more: (suspend () -> List<Track>)? = null) {
        val queue = queueOf(list)
        if (queue.isEmpty() || player == null) return
        ShuffleQueue.enable()
        play(player, queue, queue.random(), autoContinue, source = source, more = more)
    }

    fun toggleShuffle(player: Player?, key: String, list: List<Track>, autoContinue: Boolean = false, more: (suspend () -> List<Track>)? = null) {
        if (player != null && shuffledFrom(key)) {
            ShuffleQueue.set(player, false)
        } else {
            playShuffled(player, list, autoContinue, key, more)
        }
    }

    private fun extend(p: Player, gen: Long, more: suspend () -> List<Track>) {
        extendJob = App.scope.launch(Dispatchers.Main) {
            val all = try {
                more()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.add("queue", "full list unavailable: ${e.javaClass.simpleName}")
                return@launch
            }
            if (gen != generation) return@launch
            val full = queueOf(all)
            val current = p.currentMediaItem?.mediaId ?: return@launch
            if (full.none { it.urn == current }) return@launch
            val present = (0 until p.mediaItemCount).mapTo(HashSet()) { p.getMediaItemAt(it).mediaId }
            val extra = full.filter { it.urn !in present }
            if (extra.isEmpty()) return@launch
            if (ShuffleQueue.active(p)) {
                val from = p.currentMediaItemIndex + 1
                val upcoming = (from until p.mediaItemCount).map { p.getMediaItemAt(it) }
                val mixed = (upcoming + extra.map { it.toMediaItem() }).shuffled()
                if (from < p.mediaItemCount) p.removeMediaItems(from, p.mediaItemCount)
                p.addMediaItems(mixed)
                ShuffleQueue.widen(full)
            } else {
                p.addMediaItems(extra.map { it.toMediaItem() })
            }
            Logs.add("queue", "queue extended: +${extra.size} tracks")
        }
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
        source = null
        generation++
        extendJob?.cancel()
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
        source = null
        generation++
        extendJob?.cancel()
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

    suspend fun artistRadio(urn: String, fallback: List<Track>): List<Track> {
        fun usable(list: List<Track>) = list.filter { playable(it) && !it.hidden }.distinctBy { it.urn }
        val own = usable(fallback)
        val catalogId = CatalogRepo.findCatalogFor(urn, fallback)?.id
        if (catalogId != null) {
            usable(Api.waveFromArtist(catalogId)).takeIf { it.isNotEmpty() }?.let { return it }
        }
        val seed = own.firstOrNull()
        if (seed != null) {
            val wave = usable(Api.waveFromTrack(seed.urn, budgetMs = 12_000)).filter { it.urn != seed.urn }
            if (wave.isNotEmpty()) {
                Logs.add("wave", "artist radio from ${seed.title.take(24)}: ${wave.size}")
                return listOf(seed) + wave
            }
        }
        return own.shuffled()
    }
}
