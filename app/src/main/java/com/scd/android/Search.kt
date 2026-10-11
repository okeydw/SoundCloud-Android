package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SearchCat { Tracks, Vibe, Artists, Playlists, Albums }

enum class VibeState { Idle, Preparing, Slow, Ready, Unavailable }

object Search {
    var query by mutableStateOf("")
        private set
    var searched by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var cat by mutableStateOf(SearchCat.Tracks)
        private set

    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var trackPage by mutableStateOf(0)
        private set
    var tracksMore by mutableStateOf(false)
        private set

    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set
    var artistPage by mutableStateOf(0)
        private set
    var artistsMore by mutableStateOf(false)
        private set

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var playlistPage by mutableStateOf(0)
        private set
    var playlistsMore by mutableStateOf(false)
        private set

    var vibeTracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var vibeState by mutableStateOf(VibeState.Idle)
        private set
    private var vibeQuery = ""

    var suggestions by mutableStateOf<List<Track>>(emptyList())
        private set
    var userSuggestions by mutableStateOf<List<Artist>>(emptyList())
        private set
    var playlistSuggestions by mutableStateOf<List<Playlist>>(emptyList())
        private set

    private val jobs = mutableMapOf<String, Job>()
    private var suggestJob: Job? = null

    fun type(text: String) {
        query = text
        searched = false
        suggest()
    }

    fun clear() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        suggestJob?.cancel()
        query = ""
        searched = false
        tracks = emptyList()
        tracksMore = false
        vibeTracks = emptyList()
        vibeState = VibeState.Idle
        vibeQuery = ""
        cat = SearchCat.Tracks
        suggestions = emptyList()
        userSuggestions = emptyList()
        playlistSuggestions = emptyList()
        error = null
        loading = false
    }

    private fun run(key: String, reset: Boolean, block: suspend () -> Unit) {
        val running = jobs[key]
        if (running?.isActive == true) {
            if (reset) running.cancel() else return
        }
        val job = App.scope.launch(Dispatchers.Main, start = kotlinx.coroutines.CoroutineStart.LAZY) {
            val self = coroutineContext[Job]
            loading = true
            try {
                block()
            } catch (e: Exception) {
                fail(e)
            } finally {
                if (jobs[key] === self) jobs.remove(key)
                loading = jobs.values.any { it.isActive && it !== self }
            }
        }
        jobs[key] = job
        job.start()
    }

    private fun fail(e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) return
        if (e is ApiHttpException && e.code == 401) SessionState.markExpired() else error = e.message
    }

    fun search(nextPage: Int = 0) {
        val q = query.trim()
        if (q.isEmpty()) return
        suggestJob?.cancel()
        suggestions = emptyList()
        if (nextPage == 0) {
            cat = SearchCat.Tracks
            artists = emptyList()
            playlists = emptyList()
            jobs.remove("artists")?.cancel()
            jobs.remove("playlists")?.cancel()
        }
        run("tracks", reset = nextPage == 0) {
            error = null
            val res = Api.searchTracks(q, nextPage)
            tracks = if (nextPage == 0) res.collection else (tracks + res.collection).distinctBy { it.urn }
            trackPage = res.page
            tracksMore = res.has_more
            searched = true
            if (res.has_more) App.scope.launch { runCatching { Api.searchTracks(q, res.page + 1) } }
        }
    }

    fun loadArtists(nextPage: Int = 0) {
        val q = query.trim()
        if (q.isEmpty()) return
        run("artists", reset = nextPage == 0) {
            val res = Api.searchUsers(q, nextPage)
            artists = if (nextPage == 0) res.collection else (artists + res.collection).distinctBy { it.urn }
            artistPage = res.page
            artistsMore = res.has_more
        }
    }

    fun loadPlaylists(nextPage: Int = 0) {
        val q = query.trim()
        if (q.isEmpty()) return
        run("playlists", reset = nextPage == 0) {
            val res = Api.searchPlaylists(q, nextPage)
            playlists = if (nextPage == 0) res.collection else (playlists + res.collection).distinctBy { it.urn }
            playlistPage = res.page
            playlistsMore = res.has_more
        }
    }

    fun select(next: SearchCat) {
        cat = next
        when (next) {
            SearchCat.Vibe -> if (vibeQuery != query.trim() || vibeState == VibeState.Unavailable) searchVibe()
            SearchCat.Artists -> if (artists.isEmpty()) loadArtists(0)
            SearchCat.Playlists, SearchCat.Albums -> if (playlists.isEmpty()) loadPlaylists(0)
            SearchCat.Tracks -> {}
        }
    }

    fun searchVibe() {
        val q = query.trim()
        if (q.length < 2) return
        suggestJob?.cancel()
        suggestions = emptyList()
        cat = SearchCat.Vibe
        vibeQuery = q
        vibeTracks = emptyList()
        vibeState = VibeState.Preparing
        run("vibe", reset = true) {
            error = null
            searched = true
            var polls = 0
            while (true) {
                val res = try {
                    Api.vibeSearch(q)
                } catch (e: ApiHttpException) {
                    if (e.code == 503) {
                        vibeState = VibeState.Unavailable
                        return@run
                    }
                    throw e
                }
                if (res.status == "preparing") {
                    polls++
                    if (polls > 6) {
                        vibeState = VibeState.Slow
                        return@run
                    }
                    delay(2_500)
                    continue
                }
                vibeTracks = res.items.distinctBy { it.urn }
                vibeState = VibeState.Ready
                return@run
            }
        }
    }

    fun searchGenre(genre: String) {
        type(genre)
        search(0)
    }

    private fun suggest() {
        suggestJob?.cancel()
        val q = query.trim()
        if (q.length < 2 || searched) {
            suggestions = emptyList()
            userSuggestions = emptyList()
            playlistSuggestions = emptyList()
            return
        }
        suggestJob = App.scope.launch(Dispatchers.Main) {
            delay(350)
            runCatching { Api.searchTracks(q, 0, 6) }.onSuccess { suggestions = it.collection.distinctBy { t -> t.urn } }
            coroutineScope {
                launch {
                    runCatching { Api.searchUsers(q, 0, 8) }
                        .onSuccess { userSuggestions = it.collection.distinctBy { u -> u.urn }.take(4) }
                }
                launch {
                    runCatching { Api.searchPlaylists(q, 0, 8) }
                        .onSuccess { playlistSuggestions = it.collection.distinctBy { p -> p.urn }.take(4) }
                }
            }
        }
    }
}
