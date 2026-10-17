package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
data class PlaylistOp(val type: String, val track: String? = null, val value: String? = null)

@Serializable
data class LocalPlaylist(
    val id: String,
    val title: String,
    val sharing: String = "public",
    val tracks: List<Track> = emptyList(),
    val remoteUrn: String? = null,
    val ops: List<PlaylistOp> = emptyList(),
    val origin: String = "local",
    val createdAt: Long = 0,
    val createSentAt: Long = 0,
    val createAccepted: Boolean = false,
    val createAttempts: Int = 0,
    val sharingConfirmed: Boolean = false,
    val tracksLoaded: Boolean = false,
    val remoteCount: Int = 0,
    val artworkUrl: String? = null,
    val lastCheckAt: Long = 0,
    val deleted: Boolean = false,
    val localOnly: Boolean = false,
    val source: String? = null,
    val remoteModified: String? = null,
    val seenRemote: Boolean = false,
    val pushedAt: Long = 0,
    val pulledOnce: Boolean = false,
) {
    val fromYouTube: Boolean get() = source?.startsWith(YouTubeImport.SOURCE) == true || (localOnly && tracks.isNotEmpty() && tracks.all { YouTubeImport.isYouTube(it.urn) })
    val syncing: Boolean get() = !deleted && !localOnly && (remoteUrn == null || ops.isNotEmpty())
    val isPrivate: Boolean get() = sharing == "private"
    val isLocalOrigin: Boolean get() = origin == "local"
    val count: Int get() = if (tracksLoaded || (isLocalOrigin && !seenRemote)) tracks.size else maxOf(remoteCount, tracks.size)

    fun asPlaylist() = Playlist(
        urn = id,
        title = title,
        artwork_url = artworkUrl,
        track_count = count,
        sharing = sharing,
    )
}

object LocalPlaylists {
    private const val RECREATE_AFTER_MS = 72L * 60 * 60 * 1000
    private const val MAX_CREATE_ATTEMPTS = 3
    private const val PULL_PAGES = 100
    private const val PULLS_PER_SYNC = 8
    private const val PUSH_SETTLE_MS = 60_000L

    private lateinit var file: File
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val syncMutex = Mutex()
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val writer = Dispatchers.IO.limitedParallelism(1)
    private var syncJob: Job? = null

    var all by mutableStateOf<List<LocalPlaylist>>(emptyList())
        private set

    var syncingNow by mutableStateOf(false)
        private set

    val visible: List<LocalPlaylist> get() = all.filter { !it.deleted }

    fun init(context: Context) {
        file = File(File(context.filesDir, "playlists").apply { mkdirs() }, "own.json")
        all = runCatching {
            val atomic = android.util.AtomicFile(file)
            if (!file.exists() && !File(file.path + ".bak").exists()) return@runCatching emptyList()
            json.decodeFromString(ListSerializer(LocalPlaylist.serializer()), String(atomic.readFully(), Charsets.UTF_8))
        }.getOrElse {
            Logs.add("playlist", "local store unreadable: ${it.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun write(snapshot: List<LocalPlaylist>) {
        if (!::file.isInitialized) return
        val atomic = android.util.AtomicFile(file)
        var out: java.io.FileOutputStream? = null
        try {
            val bytes = json.encodeToString(ListSerializer(LocalPlaylist.serializer()), snapshot).toByteArray(Charsets.UTF_8)
            out = atomic.startWrite()
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            out?.let { runCatching { atomic.failWrite(it) } }
            Logs.add("playlist", "save failed: ${e.javaClass.simpleName}")
        }
    }

    private fun update(transform: (List<LocalPlaylist>) -> List<LocalPlaylist>) {
        val snapshot = synchronized(lock) {
            all = transform(all)
            all
        }
        App.scope.launch(writer) { write(snapshot) }
    }

    private fun edit(id: String, block: (LocalPlaylist) -> LocalPlaylist) =
        update { list -> list.map { if (it.id == id) block(it) else it } }

    fun find(urn: String): LocalPlaylist? =
        all.firstOrNull { !it.deleted && (it.id == urn || it.remoteUrn == urn) }

    fun owns(urn: String): Boolean = find(urn) != null

    fun isYouTube(urn: String): Boolean = find(urn)?.fromYouTube == true

    fun exportJson(): String = json.encodeToString(ListSerializer(LocalPlaylist.serializer()), visible)

    fun importJson(text: String, allowUnsynced: Boolean): List<LocalPlaylist> {
        val incoming = runCatching { json.decodeFromString(ListSerializer(LocalPlaylist.serializer()), text) }.getOrNull()
            ?: return emptyList()
        val known = all.flatMap { listOfNotNull(it.id, it.remoteUrn) }.toSet()
        val fresh = incoming.filter { p ->
            !p.deleted && p.id !in known && (p.remoteUrn == null || p.remoteUrn !in known) &&
                (p.remoteUrn != null || allowUnsynced)
        }.map { p ->
            if (p.remoteUrn != null) p.copy(ops = emptyList(), lastCheckAt = 0) else p
        }
        if (fresh.isNotEmpty()) update { it + fresh }
        return fresh
    }

    fun reset() {
        syncJob?.cancel()
        update { emptyList() }
    }

    fun create(title: String, private: Boolean, initial: List<Track> = emptyList(), localOnly: Boolean = false, source: String? = null): LocalPlaylist {
        val p = LocalPlaylist(
            id = "local:" + UUID.randomUUID().toString(),
            title = title.trim(),
            sharing = if (private) "private" else "public",
            tracks = initial.distinctBy { it.urn },
            createdAt = System.currentTimeMillis(),
            localOnly = localOnly,
            source = source,
        )
        update { listOf(p) + it }
        Logs.add("playlist", "created locally: ${p.title.take(30)} (${p.sharing})")
        scheduleSync()
        return p
    }

    fun add(urn: String, track: Track): Boolean {
        val p = find(urn) ?: return false
        if (p.tracks.any { it.urn == track.urn }) return false
        edit(p.id) {
            it.copy(
                tracks = it.tracks + track,
                ops = if (it.localOnly || !isScUrn(track.urn)) it.ops else it.ops + PlaylistOp("add", track = track.urn),
                remoteCount = if (it.tracksLoaded || it.isLocalOrigin) it.remoteCount else it.remoteCount + 1,
            )
        }
        Events.playlistAdd(track.urn)
        scheduleSync()
        return true
    }

    fun remove(urn: String, trackUrn: String) {
        val p = find(urn) ?: return
        edit(p.id) {
            it.copy(
                tracks = it.tracks.filterNot { t -> t.urn == trackUrn },
                ops = if (it.localOnly || !isScUrn(trackUrn)) it.ops else it.ops + PlaylistOp("remove", track = trackUrn),
                remoteCount = (it.remoteCount - 1).coerceAtLeast(0),
            )
        }
        scheduleSync()
    }

    fun reorder(urn: String, order: List<String>) {
        val p = find(urn) ?: return
        val byUrn = p.tracks.associateBy { it.urn }
        val sorted = order.mapNotNull { byUrn[it] } + p.tracks.filter { it.urn !in order }
        if (sorted.map { it.urn } == p.tracks.map { it.urn }) return
        edit(p.id) {
            it.copy(
                tracks = sorted,
                ops = if (it.localOnly) it.ops else it.ops.filterNot { op -> op.type == "order" } +
                    PlaylistOp("order", value = sorted.filter { t -> isScUrn(t.urn) }.joinToString(",") { t -> t.urn }),
            )
        }
        scheduleSync()
    }

    fun rename(urn: String, title: String) {
        val p = find(urn) ?: return
        val t = title.trim()
        if (t.isEmpty() || t == p.title) return
        edit(p.id) { it.copy(title = t, ops = if (it.localOnly) it.ops else it.ops + PlaylistOp("rename", value = t)) }
        scheduleSync()
    }

    fun setPrivate(urn: String, private: Boolean) {
        val p = find(urn) ?: return
        val s = if (private) "private" else "public"
        if (s == p.sharing) return
        edit(p.id) { it.copy(sharing = s, sharingConfirmed = false, ops = it.ops + PlaylistOp("sharing", value = s)) }
        scheduleSync()
    }

    fun delete(urn: String) {
        val p = find(urn) ?: return
        if (p.localOnly || (p.remoteUrn == null && !p.createAccepted)) {
            update { list -> list.filterNot { it.id == p.id } }
        } else {
            edit(p.id) { it.copy(deleted = true, ops = listOf(PlaylistOp("delete"))) }
        }
        Logs.add("playlist", "deleted: ${p.title.take(30)}")
        scheduleSync()
    }

    fun storeTracks(urn: String, remote: List<Track>, complete: Boolean, startedAt: Long = 0) {
        val p = find(urn) ?: return
        if (p.localOnly) return
        if (startedAt > 0 && p.pushedAt >= startedAt) return
        var adopted = emptyList<String>()
        edit(p.id) { cur ->
            val known = cur.tracks.associateBy { it.urn }
            var merged = remote.distinctBy { it.urn }
            val remoteUrns = merged.mapTo(HashSet()) { it.urn }
            if (cur.isLocalOrigin && !cur.pulledOnce && complete) {
                val pending = cur.ops.mapNotNullTo(HashSet()) { it.track }
                val missing = cur.tracks.filter { it.urn !in remoteUrns && it.urn !in pending && isScUrn(it.urn) }
                adopted = missing.map { it.urn }
                merged = merged + missing
            }
            for (op in cur.ops) {
                val t = op.track ?: continue
                merged = when (op.type) {
                    "add" -> if (merged.any { it.urn == t }) merged else merged + listOfNotNull(known[t])
                    "remove" -> merged.filterNot { it.urn == t }
                    else -> merged
                }
            }
            cur.ops.lastOrNull { it.type == "order" }?.value?.split(',')?.let { order ->
                val rank = order.withIndex().associate { (i, u) -> u to i }
                merged = merged.sortedBy { rank[it.urn] ?: Int.MAX_VALUE }
            }
            val have = merged.mapTo(HashSet()) { it.urn }
            merged = merged + cur.tracks.filter { !isScUrn(it.urn) && it.urn !in have }
            cur.copy(
                tracks = merged,
                tracksLoaded = complete,
                pulledOnce = cur.pulledOnce || complete,
                remoteCount = if (complete) remote.size else cur.remoteCount,
                ops = cur.ops + adopted.map { PlaylistOp("add", track = it) },
            )
        }
        if (adopted.isNotEmpty()) {
            Logs.add("playlist", "${p.title.take(30)}: ${adopted.size} local tracks missing on server, sending again")
            scheduleSync()
        }
    }

    fun pullable(p: LocalPlaylist): Boolean =
        !p.deleted && !p.localOnly && p.remoteUrn != null && (!p.isLocalOrigin || p.seenRemote)

    suspend fun pull(urn: String, fresh: Boolean = true): List<Track>? {
        val p = find(urn)?.takeIf { pullable(it) } ?: return null
        val remote = p.remoteUrn ?: return null
        val started = System.currentTimeMillis()
        val out = mutableListOf<Track>()
        var page = 0
        var complete = false
        while (page < PULL_PAGES) {
            val res = Api.playlistTracks(remote, page, fresh = fresh || page > 0)
            out += res.collection
            if (!res.has_more || res.collection.isEmpty()) {
                complete = true
                break
            }
            page++
        }
        storeTracks(urn, out.distinctBy { it.urn }, complete, started)
        return find(urn)?.tracks
    }

    fun scheduleSync(delayMs: Long = 1_500) {
        syncJob?.cancel()
        syncJob = App.scope.launch {
            delay(delayMs)
            sync()
        }
    }

    suspend fun sync() = withContext(NonCancellable) { syncLocked() }

    private suspend fun syncLocked() = syncMutex.withLock {
        if (Prefs.offline || Api.sessionId == null) return@withLock
        syncingNow = true
        try {
            val remote = fetchRemote()
            if (remote != null) reconcile(remote.first, remote.second)
            all.filter { it.remoteUrn == null && !it.createAccepted && !it.deleted && !it.localOnly && (remote != null || it.createAttempts == 0) }
                .forEach { sendCreate(it) }
            all.filter { it.remoteUrn != null && it.ops.isNotEmpty() }.forEach { replay(it) }
            all.filter { pullable(it) && it.ops.isEmpty() && !it.tracksLoaded && it.tracks.isNotEmpty() }
                .take(PULLS_PER_SYNC)
                .forEach { p ->
                    runCatching { pull(p.id) }.onFailure {
                        if (it is kotlinx.coroutines.CancellationException) throw it
                        Logs.add("playlist", "${p.title.take(24)}: tracks refresh failed (${it.javaClass.simpleName})")
                    }
                }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.add("playlist", "sync failed: ${e.javaClass.simpleName} ${e.message?.take(80) ?: ""}")
        } finally {
            syncingNow = false
        }
    }

    private suspend fun sendCreate(p: LocalPlaylist) {
        val folded = p.ops.size
        val now = System.currentTimeMillis()
        val res = runCatching { Api.createPlaylist(p.title, p.sharing, p.tracks.map { it.urn }.filter { isScUrn(it) }) }
        res.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        val created = res.getOrNull()
        val error = res.exceptionOrNull()
        val rejected = error is ApiHttpException && error.code in 400..499 && error.code != 408 && error.code != 429
        if (created == null && error != null && !rejected && !Api.neverReachedServer(error)) {
            edit(p.id) { it.copy(createAccepted = true, createSentAt = now, createAttempts = it.createAttempts + 1) }
            Logs.add("playlist", "${p.title.take(30)}: create unconfirmed (${error.message?.take(60)}), will look for it on the server before sending again")
            return
        }
        if (created != null) {
            edit(p.id) {
                it.copy(
                    remoteUrn = created.urn ?: it.remoteUrn,
                    createAccepted = true,
                    createSentAt = now,
                    createAttempts = it.createAttempts + 1,
                    ops = it.ops.drop(folded),
                )
            }
            Logs.add(
                "playlist",
                "${p.title.take(30)}: server " + when {
                    created.urn != null -> "created ${created.urn}"
                    created.queued -> "queued creation, will look for it on next launch"
                    else -> "accepted without urn"
                },
            )
        } else {
            edit(p.id) { it.copy(createSentAt = now, createAttempts = it.createAttempts + 1) }
            Logs.add("playlist", "${p.title.take(30)}: create rejected (${res.exceptionOrNull()?.message?.take(80)}), retry on next launch")
        }
    }

    private suspend fun fetchRemote(): Pair<List<Playlist>, Boolean>? {
        val out = mutableListOf<Playlist>()
        var page = 0
        var complete = false
        while (page < 40) {
            val res = runCatching { Api.myPlaylists(page, 50, fresh = true) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Logs.add("playlist", "remote list unavailable: ${it.message?.take(80)}")
                return null
            }
            out += res.collection
            if (!res.has_more || res.collection.isEmpty()) {
                complete = true
                break
            }
            page++
        }
        return out.distinctBy { it.urn } to complete
    }

    private fun reconcile(remote: List<Playlist>, complete: Boolean) {
        val prune = complete && remote.isNotEmpty()
        val byUrn = remote.associateBy { it.urn }
        val order = remote.withIndex().associate { it.value.urn to it.index }
        val now = System.currentTimeMillis()
        update { list ->
            val claimed = list.mapNotNull { it.remoteUrn }.toMutableSet()
            val kept = HashSet<String>()
            val next = mutableListOf<LocalPlaylist>()
            for (p in list) {
                var q = p
                val known = q.remoteUrn
                if (known != null && !q.deleted && !kept.add(known)) {
                    Logs.add("playlist", "${q.title.take(30)}: second local copy of $known dropped")
                    continue
                }
                if (q.remoteUrn == null && !q.deleted && !q.localOnly && (q.createAccepted || q.createAttempts > 0)) {
                    val match = remote.firstOrNull { it.title.trim().equals(q.title.trim(), ignoreCase = true) && it.urn !in claimed }
                    if (match != null) {
                        claimed += match.urn
                        q = q.copy(remoteUrn = match.urn, createAccepted = true)
                        Logs.add("playlist", "${q.title.take(30)}: found on server as ${match.urn}")
                    } else if (now - q.createSentAt > RECREATE_AFTER_MS && q.createAttempts < MAX_CREATE_ATTEMPTS) {
                        q = q.copy(createAccepted = false)
                        Logs.add("playlist", "${q.title.take(30)}: still not on server after 3 days, will send again")
                    }
                }
                val r = q.remoteUrn?.let { byUrn[it] }
                if (q.remoteUrn != null && r == null && prune) {
                    if (q.deleted) continue
                    if ((!q.isLocalOrigin || q.seenRemote) && q.ops.isEmpty()) {
                        Logs.add("playlist", "${q.title.take(30)}: gone from server, removed")
                        continue
                    }
                }
                if (r != null) {
                    val settled = now - q.pushedAt > PUSH_SETTLE_MS
                    val changed = r.last_modified != null && q.remoteModified != null && r.last_modified != q.remoteModified
                    q = q.copy(
                        lastCheckAt = now,
                        remoteCount = r.track_count,
                        artworkUrl = r.artwork_url ?: q.artworkUrl,
                        seenRemote = true,
                        remoteModified = if (settled) r.last_modified ?: q.remoteModified else q.remoteModified,
                    )
                    if (settled && r.title.isNotBlank() && q.ops.none { it.type == "rename" }) q = q.copy(title = r.title)
                    val rs = r.sharing
                    if (rs != null && q.ops.none { it.type == "sharing" }) {
                        q = when {
                            rs == q.sharing -> q.copy(sharingConfirmed = true)
                            q.sharingConfirmed || !q.isLocalOrigin -> q.copy(sharing = rs, sharingConfirmed = true)
                            else -> q.copy(ops = q.ops + PlaylistOp("sharing", value = q.sharing))
                        }
                    }
                    if (settled && q.tracksLoaded && q.ops.isEmpty() && (changed || r.track_count != q.tracks.count { isScUrn(it.urn) })) {
                        q = q.copy(tracksLoaded = false)
                    }
                }
                next += q
            }
            remote.filter { it.urn !in claimed }.forEach { r ->
                next += LocalPlaylist(
                    id = r.urn,
                    title = r.title,
                    sharing = r.sharing ?: "public",
                    remoteUrn = r.urn,
                    origin = "remote",
                    remoteCount = r.track_count,
                    remoteModified = r.last_modified,
                    seenRemote = true,
                    artworkUrl = r.artwork_url,
                    sharingConfirmed = true,
                    createdAt = now,
                    lastCheckAt = now,
                )
            }
            next.sortedBy { p -> p.remoteUrn?.let { order[it] } ?: -1 }
        }
    }

    private suspend fun replay(p: LocalPlaylist) {
        val urn = p.remoteUrn ?: return
        var conflict = false
        for (op in p.ops) {
            val res = runCatching {
                when (op.type) {
                    "add" -> op.track?.let { Api.addToPlaylist(urn, it) }
                    "remove" -> op.track?.let { Api.removeFromPlaylist(urn, it) }
                    "rename" -> op.value?.let { Api.renamePlaylist(urn, it) }
                    "sharing" -> op.value?.let { Api.setPlaylistSharing(urn, it) }
                    "order" -> op.value?.split(',')?.filter { it.isNotBlank() }?.let { Api.reorderPlaylist(urn, it) }
                    "delete" -> Api.deletePlaylist(urn)
                    else -> Unit
                }
            }
            val err = res.exceptionOrNull()
            if (err is kotlinx.coroutines.CancellationException) throw err
            val code = (err as? ApiHttpException)?.code
            val rejected = code != null && code in 400..499 && code != 401 && code != 408 && code != 429
            if (err != null && !rejected) {
                Logs.add("playlist", "${p.title.take(24)}: ${op.type} postponed (${err.javaClass.simpleName})")
                return
            }
            if (rejected) {
                Logs.add("playlist", "${p.title.take(24)}: ${op.type} rejected by server ($code), dropped")
                if (code == 409 || code == 404) conflict = true
            }
            val at = System.currentTimeMillis()
            edit(p.id) { cur -> if (cur.ops.firstOrNull() == op) cur.copy(ops = cur.ops.drop(1), pushedAt = at) else cur }
        }
        val after = all.firstOrNull { it.id == p.id }
        if (after != null && after.deleted && after.ops.isEmpty()) {
            update { list -> list.filterNot { it.id == p.id } }
            return
        }
        if (conflict && after != null && !after.deleted) {
            edit(p.id) { it.copy(tracksLoaded = false, pushedAt = 0) }
            if (after.ops.isEmpty()) {
                runCatching { pull(p.id) }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
                Logs.add("playlist", "${p.title.take(24)}: reloaded from server after conflict")
            }
        }
    }
}
