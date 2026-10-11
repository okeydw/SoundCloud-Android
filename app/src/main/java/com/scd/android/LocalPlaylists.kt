package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
) {
    val syncing: Boolean get() = !deleted && !localOnly && (remoteUrn == null || ops.isNotEmpty())
    val isPrivate: Boolean get() = sharing == "private"
    val isLocalOrigin: Boolean get() = origin == "local"
    val count: Int get() = if (isLocalOrigin || tracksLoaded) tracks.size else maxOf(remoteCount, tracks.size)

    fun asPlaylist() = Playlist(
        urn = id,
        title = title,
        artwork_url = artworkUrl,
        track_count = count,
        sharing = sharing,
    )
}

object LocalPlaylists {
    private const val RECREATE_AFTER_MS = 24L * 60 * 60 * 1000

    private lateinit var file: File
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val syncMutex = Mutex()
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

    fun create(title: String, private: Boolean, initial: List<Track> = emptyList(), localOnly: Boolean = false): LocalPlaylist {
        val p = LocalPlaylist(
            id = "local:" + UUID.randomUUID().toString(),
            title = title.trim(),
            sharing = if (private) "private" else "public",
            tracks = initial.distinctBy { it.urn },
            createdAt = System.currentTimeMillis(),
            localOnly = localOnly,
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
        PlaylistOrder.set(listOf(p.id, p.remoteUrn, urn), order)
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

    fun storeTracks(urn: String, remote: List<Track>, complete: Boolean) {
        val p = find(urn) ?: return
        if (p.isLocalOrigin && p.tracks.isNotEmpty()) return
        edit(p.id) { cur ->
            val known = cur.tracks.associateBy { it.urn }
            var merged = remote.distinctBy { it.urn }
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
            merged = PlaylistOrder.apply(listOf(cur.id, cur.remoteUrn), merged)
            cur.copy(tracks = merged, tracksLoaded = complete, remoteCount = if (complete) remote.size else cur.remoteCount)
        }
    }

    fun scheduleSync(delayMs: Long = 1_500) {
        syncJob?.cancel()
        syncJob = App.scope.launch {
            delay(delayMs)
            sync()
        }
    }

    suspend fun sync() = syncMutex.withLock {
        if (Prefs.offline || Api.sessionId == null) return@withLock
        syncingNow = true
        try {
            all.filter { it.remoteUrn == null && !it.createAccepted && !it.deleted && !it.localOnly }.forEach { sendCreate(it) }
            val remote = fetchRemote()
            if (remote != null) reconcile(remote)
            all.filter { it.remoteUrn != null && it.ops.isNotEmpty() }.forEach { replay(it) }
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

    private suspend fun fetchRemote(): List<Playlist>? {
        val out = mutableListOf<Playlist>()
        var page = 0
        while (page < 20) {
            val res = runCatching { Api.myPlaylists(page, 50, fresh = true) }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                Logs.add("playlist", "remote list unavailable: ${it.message?.take(80)}")
                return null
            }
            out += res.collection
            if (!res.has_more || res.collection.isEmpty()) break
            page++
        }
        return out.distinctBy { it.urn }
    }

    private fun reconcile(remote: List<Playlist>) {
        val byUrn = remote.associateBy { it.urn }
        val order = remote.withIndex().associate { it.value.urn to it.index }
        val now = System.currentTimeMillis()
        update { list ->
            val claimed = list.mapNotNull { it.remoteUrn }.toMutableSet()
            val next = mutableListOf<LocalPlaylist>()
            for (p in list) {
                var q = p
                if (q.remoteUrn == null && q.createAccepted) {
                    val match = remote.firstOrNull { it.title.trim() == q.title && it.urn !in claimed }
                    if (match != null) {
                        claimed += match.urn
                        q = q.copy(remoteUrn = match.urn)
                        Logs.add("playlist", "${q.title.take(30)}: found on server as ${match.urn}")
                    } else if (now - q.createSentAt > RECREATE_AFTER_MS) {
                        q = q.copy(createAccepted = false)
                        Logs.add("playlist", "${q.title.take(30)}: still not on server after a day, will send again")
                    }
                }
                val r = q.remoteUrn?.let { byUrn[it] }
                if (q.remoteUrn != null && r == null) {
                    if (q.deleted) continue
                    if (!q.isLocalOrigin && q.ops.isEmpty()) continue
                }
                if (r != null) {
                    q = q.copy(lastCheckAt = now, remoteCount = r.track_count, artworkUrl = r.artwork_url ?: q.artworkUrl)
                    if (!q.isLocalOrigin && q.ops.none { it.type == "rename" }) q = q.copy(title = r.title)
                    val rs = r.sharing
                    if (rs != null && q.ops.none { it.type == "sharing" }) {
                        q = when {
                            rs == q.sharing -> q.copy(sharingConfirmed = true)
                            q.sharingConfirmed || !q.isLocalOrigin -> q.copy(sharing = rs, sharingConfirmed = true)
                            else -> q.copy(ops = q.ops + PlaylistOp("sharing", value = q.sharing))
                        }
                    }
                    if (!q.isLocalOrigin && q.tracksLoaded && q.ops.isEmpty() && r.track_count != q.tracks.size) {
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
            if (rejected) Logs.add("playlist", "${p.title.take(24)}: ${op.type} rejected by server ($code), dropped")
            edit(p.id) { cur -> if (cur.ops.firstOrNull() == op) cur.copy(ops = cur.ops.drop(1)) else cur }
        }
        val after = all.firstOrNull { it.id == p.id }
        if (after != null && after.deleted && after.ops.isEmpty()) {
            update { list -> list.filterNot { it.id == p.id } }
        }
    }
}
