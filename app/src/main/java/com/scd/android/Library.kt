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
        PlaylistSnapshots.clear()
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
        PlaylistPrefetch.run()
    }

    suspend fun likedPage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val since = Likes.snapshot()
        val live = fresh && !offline
        val res = if (page > 0 && !offline) {
            runCatching { Api.likedTracks(page, fresh = true) }.getOrElse { Api.likedTracks(page) }
        } else {
            Api.likedTracks(page, fresh = live)
        }
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
            val local = (if (page == 0) own.tracks else emptyList()) to false
            if (offline && page == 0 && own.tracks.isEmpty() && !own.tracksLoaded && own.remoteCount > 0) throw NotSavedOfflineException()
            if (page > 0 || offline || !LocalPlaylists.pullable(own)) return local
            if (!fresh && (own.tracksLoaded || own.tracks.isNotEmpty())) return local
            val pulled = try {
                LocalPlaylists.pull(urn, fresh)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (own.tracks.isNotEmpty() || own.tracksLoaded) return local
                throw e
            }
            return (pulled ?: own.tracks) to false
        }
        if (page == 0 && offline) snapshot(urn)?.let { return it }
        val res = try {
            if (page > 0 && !offline) {
                runCatching { Api.playlistTracks(urn, page, fresh = true) }.getOrElse { Api.playlistTracks(urn, page) }
            } else {
                Api.playlistTracks(urn, page, fresh = fresh && !offline)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            if (page == 0) snapshot(urn)?.let { return it }
            if (page == 0 && (offline || Prefs.offline)) throw NotSavedOfflineException()
            throw e
        }
        PlaylistSnapshots.record(urn, page, res.collection, res.has_more)
        return res.collection to res.has_more
    }

    suspend fun playlistAll(urn: String, offline: Boolean): List<Track> {
        val out = mutableListOf<Track>()
        var page = 0
        while (page < 200) {
            val (batch, more) = playlistPage(urn, page, fresh = false, offline = offline)
            out += batch
            if (!more || batch.isEmpty()) break
            page++
        }
        return out.distinctBy { it.urn }
    }

    suspend fun playlistDownloads(urn: String, loaded: List<Track>, offline: Boolean): List<String> {
        val all = try {
            playlistAll(urn, offline)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        return (loaded + all).map { it.urn }.distinct().filter { Downloads.isDownloaded(it) }
    }

    class NotSavedOfflineException : Exception("playlist is not saved for offline")

    private suspend fun snapshot(urn: String): Pair<List<Track>, Boolean>? =
        PlaylistSnapshots.get(urn)?.let { it to false }

}
