package com.scd.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object PlaylistSnapshots {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dir: File
    private val lock = Any()
    private val loaded = mutableMapOf<String, List<Track>>()
    private val pending = mutableMapOf<String, Pending>()

    private class Pending {
        val pages = mutableMapOf<Int, List<Track>>()
        var last = -1
    }

    fun init(context: Context) {
        dir = File(File(context.filesDir, "playlists"), "snapshots").apply { mkdirs() }
    }

    private fun fileFor(urn: String) = File(dir, urn.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    private fun read(urn: String): List<Track> {
        synchronized(lock) { loaded[urn] }?.let { return it }
        if (!::dir.isInitialized) return emptyList()
        val list = runCatching {
            val f = fileFor(urn)
            if (!f.exists() && !File(f.path + ".bak").exists()) emptyList()
            else json.decodeFromString(ListSerializer(Track.serializer()), String(android.util.AtomicFile(f).readFully(), Charsets.UTF_8))
        }.getOrDefault(emptyList())
        synchronized(lock) { loaded[urn] = list }
        return list
    }

    fun isFresh(urn: String, maxAgeMs: Long): Boolean {
        if (!::dir.isInitialized) return false
        val f = fileFor(urn)
        return f.exists() && System.currentTimeMillis() - f.lastModified() < maxAgeMs
    }

    suspend fun get(urn: String): List<Track>? = withContext(Dispatchers.IO) {
        read(urn).takeIf { it.isNotEmpty() }
    }

    suspend fun record(urn: String, page: Int, tracks: List<Track>, hasMore: Boolean) = withContext(Dispatchers.IO) {
        if (!::dir.isInitialized) return@withContext
        val (list, complete) = synchronized(lock) {
            val p = if (page == 0) Pending().also { pending[urn] = it } else pending[urn] ?: return@withContext
            p.pages[page] = tracks
            if (!hasMore) p.last = page
            val acc = mutableListOf<Track>()
            var i = 0
            while (true) {
                acc += p.pages[i] ?: break
                i++
            }
            val done = p.last in 0 until i
            if (done) pending.remove(urn)
            acc.distinctBy { it.urn } to done
        }
        if (list.isEmpty()) return@withContext
        if (!complete && list.size < read(urn).size) return@withContext
        synchronized(lock) { loaded[urn] = list }
        val atomic = android.util.AtomicFile(fileFor(urn))
        var out: java.io.FileOutputStream? = null
        runCatching {
            val stream = atomic.startWrite()
            out = stream
            stream.write(json.encodeToString(ListSerializer(Track.serializer()), list).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        }.onFailure {
            out?.let { o -> runCatching { atomic.failWrite(o) } }
            Logs.add("playlist", "snapshot save failed: ${it.javaClass.simpleName}")
        }
    }

    fun clear() {
        synchronized(lock) {
            loaded.clear()
            pending.clear()
        }
        if (!::dir.isInitialized) return
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
