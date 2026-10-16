package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WaveState {
    val tracksState = mutableStateOf<List<Track>>(emptyList())
    val cursorState = mutableStateOf("")
    val loadingState = mutableStateOf(false)
    var tracks by tracksState
    var cursor by cursorState
    var loading by loadingState
    var error by mutableStateOf<String?>(null)
    var lastUrn: String? = null
    var lastPositionMs = 0L

    fun remember(urn: String?, positionMs: Long) {
        if (urn == null || !WaveFeedback.isOwned(urn)) return
        lastUrn = urn
        lastPositionMs = positionMs.coerceAtLeast(0L)
    }

    suspend fun more(): List<Track> = withContext(Dispatchers.Main) {
        WaveFeedback.flush(cursor)?.let { cursor = it }
        val known = tracks.map { it.urn }.toHashSet()
        fun usable(list: List<Track>) = list.filter { (!it.unavailable || Prefs.playBlocked) && it.urn !in known }.distinctBy { it.urn }
        var fresh = emptyList<Track>()
        for (attempt in 0 until 4) {
            val batch = when (attempt) {
                0, 1 -> {
                    val (list, next) = Api.waveTracks(cursor.ifEmpty { null })
                    if (next.isNotEmpty()) cursor = next
                    list
                }
                2 -> tracks.lastOrNull()?.urn?.let { Api.waveFromTrack(it) }.orEmpty()
                else -> Api.waveTracks(LocalRadio.CURSOR).first
            }
            fresh = usable(batch)
            if (fresh.isNotEmpty()) break
        }
        if (fresh.isEmpty()) {
            Logs.add("wave", "no new tracks after ${tracks.size}")
            return@withContext fresh
        }
        WaveFeedback.own(fresh)
        tracks = (tracks + fresh).distinctBy { it.urn }
        fresh
    }

    fun restore() {
        if (tracks.isEmpty()) FeedCache.load("wave").let {
            if (it.isNotEmpty()) {
                tracks = it
                WaveFeedback.own(it)
            }
        }
    }

    private fun fail(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) return
        if (e is ApiHttpException && e.code == 401) SessionState.markExpired() else error = e.message
    }

    fun load(more: Boolean) {
        if (loading || Prefs.offline) return
        loading = true
        error = null
        App.scope.launch(Dispatchers.Main) {
            try {
                if (more) {
                    more()
                    return@launch
                }
                val (batch, next) = Api.waveTracks(null)
                if (batch.isEmpty() && tracks.isNotEmpty()) return@launch
                val playable = batch.filter { !it.unavailable || Prefs.playBlocked }
                if (playable.size < batch.size) {
                    Logs.add("wave", "hidden blocked: ${batch.size - playable.size} of ${batch.size}")
                }
                tracks = playable.distinctBy { it.urn }
                WaveFeedback.own(tracks)
                cursor = next
                FeedCache.save("wave", tracks)
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    fun refresh(player: Player?) {
        if (loading || Prefs.offline) return
        loading = true
        error = null
        App.scope.launch(Dispatchers.Main) {
            try {
                val (batch, next) = Api.waveTracks(null)
                val fresh = batch.distinctBy { it.urn }.filter { !it.unavailable || Prefs.playBlocked }
                if (fresh.isEmpty()) return@launch
                WaveFeedback.own(fresh)
                tracks = fresh
                cursor = next
                FeedCache.save("wave", fresh)
                player?.let { c ->
                    if (c.mediaItemCount > 0 && NowPlaying.autoContinue) {
                        val curUrn = c.currentMediaItem?.mediaId
                        val curIndex = c.currentMediaItemIndex
                        if (c.mediaItemCount > curIndex + 1) c.removeMediaItems(curIndex + 1, c.mediaItemCount)
                        c.addMediaItems(fresh.filter { it.urn != curUrn }.map { it.toMediaItem() })
                    }
                }
            } catch (e: Exception) {
                fail(e)
            } finally {
                loading = false
            }
        }
    }

    fun reset() {
        tracks = emptyList()
        cursor = ""
        loading = false
        error = null
    }
}

object Tiles {
    var items by mutableStateOf<List<Track>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    private var page = 0
    private val genrePage = mutableMapOf<String, Int>()
    private val exhausted = mutableSetOf<String>()

    fun restore() {
        if (items.isEmpty()) FeedCache.load("tiles").let { if (it.isNotEmpty()) items = it }
    }

    fun loadMore() = load(page + 1)

    fun load(nextPage: Int) {
        if (loading || Prefs.offline) return
        loading = true
        App.scope.launch(Dispatchers.Main) {
            try {
                if (nextPage == 0) {
                    genrePage.clear()
                    exhausted.clear()
                }
                val known = if (nextPage == 0) emptySet() else items.map { it.urn }.toSet()
                fun good(t: Track) = t.artwork_url != null && t.urn !in known && Playback.playable(t) &&
                    !t.hidden
                val gathered = LinkedHashMap<String, Track>()
                var attempts = 0
                while (gathered.size < 18 && attempts < 4) {
                    attempts++
                    val pool = GENRES.filter { it !in exhausted }.ifEmpty {
                        exhausted.clear()
                        genrePage.clear()
                        GENRES
                    }
                    val picked = pool.shuffled().take(3)
                    val fetched = coroutineScope {
                        picked.map { g ->
                            async { g to runCatching { Api.searchTracks(g, genrePage[g] ?: 0, 20) }.getOrNull() }
                        }.awaitAll()
                    }
                    fetched.forEach { (g, res) ->
                        if (res == null) return@forEach
                        genrePage[g] = (genrePage[g] ?: 0) + 1
                        if (!res.has_more || res.collection.isEmpty()) exhausted += g
                        res.collection.filter(::good).forEach { gathered.putIfAbsent(it.urn, it) }
                    }
                    if (gathered.size < 18) {
                        val base = items + gathered.values
                        if (base.isNotEmpty()) {
                            runCatching { Api.relatedTracks(base.random().urn, 20) }.getOrNull()?.collection
                                ?.filter(::good)
                                ?.forEach { gathered.putIfAbsent(it.urn, it) }
                        }
                    }
                }
                val fresh = gathered.values.toList().shuffled()
                if (fresh.isEmpty()) {
                    Logs.add("tiles", "page $nextPage: nothing new")
                    return@launch
                }
                items = if (nextPage == 0) fresh else items + fresh
                page = nextPage
                if (nextPage == 0) FeedCache.save("tiles", items)
            } finally {
                loading = false
            }
        }
    }

    fun reset() {
        items = emptyList()
        page = 0
    }
}
