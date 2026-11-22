package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

object Library {
    var likedPlaylists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var soundprint by mutableStateOf<List<Track>>(emptyList())
        private set

    private val likedGate = Mutex()
    private data class LikedSnapshot(val session: String, val tracks: List<Track>, val savedAt: kotlin.time.TimeMark)
    @kotlin.concurrent.Volatile private var likedSnapshot: LikedSnapshot? = null

    internal fun recentLikes(): List<Track>? = likedSnapshot?.takeIf {
        it.session == Api.sessionId && it.savedAt.elapsedNow().inWholeSeconds < 15
    }?.let { Likes.visible(it.tracks) }

    fun reset() {
        likedSnapshot = null
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
        val session = Api.sessionId ?: return
        val cached = mutableListOf<Track>()
        for (p in 0 until 6) {
            val batch = cachedPage(LibraryPages.ListKind.LIKES, p) ?: break
            cached += batch.tracks
            if (!batch.more) break
        }
        if (session != Api.sessionId) return
        if (cached.isNotEmpty()) soundprint = Likes.visible(cached.distinctBy { it.urn }).take(300)
        if (!Prefs.offline && soundprint.isEmpty()) {
            runCatching { refreshLikes() }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    suspend fun refreshPlaylists(offline: Boolean) {
        runCatching { Api.likedPlaylists(fresh = false) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onSuccess { likedPlaylists = if (it.complete) it.collection else (it.collection + likedPlaylists).distinctBy { p -> p.urn } }
        if (offline) return
        runCatching { Api.likedPlaylists(fresh = true) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }.onSuccess {
            likedPlaylists = if (it.complete) it.collection else (it.collection + likedPlaylists).distinctBy { p -> p.urn }
            LikedPlaylists.seed(force = true)
        }
        LocalPlaylists.sync()
        PlaylistPrefetch.run()
    }

    private suspend fun cachedPage(kind: LibraryPages.ListKind, page: Int): LibraryPages.Page? {
        LibraryPages.read(kind, page)?.let { return it }
        val sid = Api.sessionId ?: return null
        val previous = Api.cachedLibraryPage(kind, page, sid) ?: return null
        if (sid != Api.sessionId) return null
        LibraryPages.save(kind, page, previous.tracks, previous.more, sid)
        return previous
    }

    suspend fun cachedRows(likes: Boolean, update: (List<Track>) -> Unit) {
        val kind = if (likes) LibraryPages.ListKind.LIKES else LibraryPages.ListKind.HISTORY
        val tracks = linkedMapOf<String, Track>()
        var page = 0
        while (true) {
            val cached = LibraryPages.read(kind, page) ?: break
            cached.tracks.forEach { if (it.urn !in tracks) tracks[it.urn] = it }
            page++
            if (page == 1 || page % 8 == 0 || !cached.more) update(tracks.values.toList())
            if (!cached.more || cached.tracks.isEmpty()) return
        }
        if (tracks.isNotEmpty()) update(tracks.values.toList())
    }

    suspend fun refreshLikes(): List<Track> = likedGate.withLock {
        val session = Api.sessionId ?: return@withLock emptyList()
        recentLikes()?.let { return@withLock it }
        val since = Likes.snapshot()
        val tracks = linkedMapOf<String, Track>()
        var page = 0
        while (true) {
            if (session != Api.sessionId || Prefs.offline) throw kotlinx.coroutines.CancellationException("Library changed")
            val batch = Api.likedTracks(page, fresh = true)
            if (session != Api.sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
            check(batch.snapshotReady) { "Likes are still syncing" }
            val previousSize = tracks.size
            batch.collection.forEach { tracks.getOrPut(it.urn) { it } }
            if (!batch.has_more) break
            check(tracks.size > previousSize) { "Likes page did not advance" }
            page++
        }
        val result = tracks.values.toList()
        Likes.replaceAll(result, since)
        likedSnapshot = LikedSnapshot(session, result, TimeSource.Monotonic.markNow())
        val visible = Likes.visible(result)
        soundprint = visible.take(300)
        visible
    }

    suspend fun likedPage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        if (page == 0 && fresh && !offline) return refreshLikes() to false
        if (offline) {
            if (page != 0) return emptyList<Track>() to false
            val session = Api.sessionId ?: return Likes.visible(Downloads.tracks().filter { Likes.isLiked(it.urn) }) to false
            val tracks = linkedMapOf<String, Track>()
            var p = 0
            while (true) {
                val cached = cachedPage(LibraryPages.ListKind.LIKES, p++) ?: break
                cached.tracks.forEach { tracks.getOrPut(it.urn) { it } }
                if (!cached.more || cached.tracks.isEmpty()) break
            }
            if (session != Api.sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
            Likes.seed(tracks.values.toList())
            Downloads.tracks().filter { Likes.isLiked(it.urn) }.forEach { tracks.getOrPut(it.urn) { it } }
            return Likes.visible(tracks.values.toList()) to false
        }
        if (page == 0) {
            cachedPage(LibraryPages.ListKind.LIKES, 0)?.let { cached ->
                Likes.seed(cached.tracks)
                return cached.tracks to cached.more
            }
            val local = Likes.visible(Downloads.tracks().filter { Likes.isLiked(it.urn) })
            if (local.isNotEmpty()) return local to false
        }
        val since = Likes.snapshot()
        val res = if (page > 0) {
            runCatching { Api.likedTracks(page, fresh = true) }.getOrElse { Api.likedTracks(page) }
        } else {
            Api.likedTracks(page)
        }
        Likes.seed(res.collection, since)
        return res.collection to res.has_more
    }

    suspend fun genrePage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        val res = Api.likedTracks(page, fresh = fresh && !offline)
        Likes.seed(res.collection)
        return res.collection to res.has_more
    }

    suspend fun historyPage(page: Int, fresh: Boolean, offline: Boolean): Pair<List<Track>, Boolean> {
        if ((!fresh && page == 0) || offline) {
            cachedPage(LibraryPages.ListKind.HISTORY, page)?.let { cached -> return cached.tracks to cached.more }
            if (offline) return emptyList<Track>() to false
        }
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
        val own = LocalPlaylists.find(urn) ?: if (offline) LocalPlaylists.find("local:album:$urn") else null
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
        PlaylistSnapshots.record(urn, page, res.collection, res.has_more, res.snapshotReady)
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
