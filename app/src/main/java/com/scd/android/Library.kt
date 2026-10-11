package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Library {
    var likedPlaylists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var soundprint by mutableStateOf<List<Track>>(emptyList())
        private set

    fun reset() {
        likedPlaylists = emptyList()
        soundprint = emptyList()
    }

    suspend fun refreshProfile(offline: Boolean) {
        runCatching { Api.authStatus() }.onSuccess {
            if (!it.authenticated && !offline) {
                SessionState.markExpired()
            } else if (!it.username.isNullOrEmpty()) {
                Prefs.saveUsername(it.username)
            }
        }
        if (offline) return
        runCatching { Api.subscription() }.onSuccess { Prefs.saveStar(it.premium) }
        runCatching { Api.me() }.onSuccess {
            if (it.username.isNotEmpty()) Prefs.saveUsername(it.username)
            Prefs.saveAvatar(it.avatar_url)
            it.urn?.takeIf { u -> u.isNotEmpty() }?.let { u -> Prefs.saveUserUrn(u) }
        }
    }

    suspend fun loadSoundprint() {
        val all = mutableListOf<Track>()
        for (p in 0 until 6) {
            val res = runCatching { Api.likedTracks(p, 50) }.getOrNull() ?: break
            all += res.collection
            if (!res.has_more) break
        }
        if (all.isNotEmpty()) soundprint = all.distinctBy { it.urn }
    }

    suspend fun refreshPlaylists(offline: Boolean) {
        runCatching { Api.likedPlaylists(fresh = false) }.onSuccess { likedPlaylists = it.collection }
        if (offline) return
        runCatching { Api.likedPlaylists(fresh = true) }.onSuccess {
            likedPlaylists = it.collection
            LikedPlaylists.seed(force = true)
        }
        LocalPlaylists.sync()
    }

    suspend fun likedPage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val since = Likes.snapshot()
        val live = fresh && !offline
        val res = Api.likedTracks(page, fresh = live)
        if (page == 0 && live && !res.has_more) Likes.replaceAll(res.collection, since) else Likes.seed(res.collection)
        return res.collection to res.has_more
    }

    suspend fun genrePage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val res = Api.likedTracks(page, fresh = fresh && !offline)
        Likes.seed(res.collection)
        return res.collection to res.has_more
    }

    suspend fun historyPage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val res = Api.history(offset = page * 50, limit = 50, fresh = fresh && !offline)
        val batch = res.collection.map { it.toTrack() }.distinctBy { it.urn }
        return batch to (page * 50 + res.collection.size < res.total)
    }

    fun downloadedPage(page: Int): Pair<List<Track>, Boolean> =
        (if (page == 0) Downloads.tracks() else emptyList()) to false

    suspend fun artistPage(urn: String, page: Int): Pair<List<Track>, Boolean> {
        val res = Api.userTracks(urn, page)
        return res.collection to res.has_more
    }

    suspend fun playlistPage(urn: String, page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val own = LocalPlaylists.find(urn)
        if (own != null) {
            if (own.isLocalOrigin && (own.remoteUrn == null || own.tracks.isNotEmpty() || offline)) {
                return (if (page == 0) own.tracks else emptyList()) to false
            }
            if (!fresh && own.tracksLoaded) return (if (page == 0) own.tracks else emptyList()) to false
            if (offline) return (if (page == 0) own.tracks else emptyList()) to false
        }
        if (own == null && page > 0 && PlaylistOrder.has(urn)) return emptyList<Track>() to false
        val remoteUrn = own?.remoteUrn ?: urn
        val res = if (page > 0 && !offline) {
            runCatching { Api.playlistTracks(remoteUrn, page, fresh = true) }.getOrElse { Api.playlistTracks(remoteUrn, page) }
        } else {
            Api.playlistTracks(remoteUrn, page, fresh = fresh && !offline)
        }
        if (own != null && page == 0) {
            val all = if (res.has_more) collectAll(remoteUrn, res) else res.collection
            LocalPlaylists.storeTracks(urn, all, complete = true)
            val stored = LocalPlaylists.find(urn)?.tracks ?: all
            return stored to false
        }
        if (page == 0 && PlaylistOrder.has(urn)) {
            val all = if (res.has_more) collectAll(remoteUrn, res) else res.collection
            return PlaylistOrder.apply(listOf(urn), all) to false
        }
        return res.collection to res.has_more
    }

    private suspend fun collectAll(urn: String, first: PagedTracks): List<Track> {
        val all = first.collection.toMutableList()
        var page = 0
        var more = first.has_more
        while (more && all.size < 1000) {
            page++
            val next = runCatching { Api.playlistTracks(urn, page, fresh = true) }.getOrNull() ?: break
            all += next.collection
            more = next.has_more
        }
        return all.distinctBy { it.urn }
    }
}
