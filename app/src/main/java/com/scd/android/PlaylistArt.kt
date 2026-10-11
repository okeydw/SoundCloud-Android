package com.scd.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object PlaylistArt {

    private val resolved = ConcurrentHashMap<String, List<Any>>()
    private val sizeSuffix = Regex("-(large|original|crop|small|badge|tiny|mini|t\\d+x\\d+)(\\.\\w+)?$")
    private val hashGate = Semaphore(6)
    private val hashCache = ConcurrentHashMap<String, Long>()

    fun cached(urn: String): List<Any>? = if (LocalPlaylists.owns(urn)) null else resolved[urn]

    fun clear() = resolved.clear()

    fun coverKey(urn: String): String =
        LocalPlaylists.find(urn)?.tracks?.take(12)?.joinToString(",") { it.urn } ?: ""

    suspend fun resolve(playlist: Playlist): List<Any> {
        val own = LocalPlaylists.find(playlist.urn)
        if (own != null && (own.isLocalOrigin || own.tracks.isNotEmpty())) return covers(own.tracks)
        resolved[playlist.urn]?.let { return it }
        val remote = own?.remoteUrn ?: playlist.urn
        val page = runCatching { Api.playlistTracks(remote, 0, limit = 20) }.getOrNull() ?: return emptyList()
        val found = covers(page.collection)
        resolved[playlist.urn] = found
        return found
    }

    private fun artKey(url: String): String =
        url.substringBefore('?').substringAfterLast('/').replace(sizeSuffix, "")

    private suspend fun covers(tracks: List<Track>, need: Int = 4): List<Any> {
        val candidates = tracks.filter { !it.artwork_url.isNullOrBlank() }.distinctBy { artKey(it.artwork_url!!) }.take(12)
        val hashes = coroutineScope { candidates.map { t -> async { imageHash(t) } }.awaitAll() }
        val picked = mutableListOf<Track>()
        val pickedHashes = mutableListOf<Long>()
        candidates.forEachIndexed { i, t ->
            if (picked.size >= need) return@forEachIndexed
            val h = hashes[i]
            if (h != null && pickedHashes.any { java.lang.Long.bitCount(it xor h) <= 6 }) return@forEachIndexed
            picked += t
            if (h != null) pickedHashes += h
        }
        return picked.mapNotNull { it.artModel("t120x120") }
    }

    private suspend fun imageHash(track: Track): Long? {
        val url = track.artwork_url ?: return null
        val key = artKey(url)
        hashCache[key]?.let { return it }
        val local = Downloads.artPathOrNull(track.urn)
        val hash = hashGate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = if (local != null) {
                        android.graphics.BitmapFactory.decodeFile(
                            local,
                            android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 },
                        )
                    } else {
                        val small = Api.artworkUrl(url, "small") ?: return@runCatching null
                        val req = if (small.contains("sndcdn.com")) {
                            val (name, value) = Api.imageProxyTarget(small)
                            okhttp3.Request.Builder()
                                .url("${Api.IMAGES_BASE}/?t=${android.net.Uri.encode(value)}")
                                .header(name, value)
                                .build()
                        } else {
                            okhttp3.Request.Builder().url(small).build()
                        }
                        val bytes = Api.http.newCall(req).execute().use { res ->
                            if (!res.isSuccessful) null else res.body?.bytes()
                        } ?: return@runCatching null
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    } ?: return@runCatching null
                    val tiny = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 8, true)
                    val gray = IntArray(64) { i ->
                        val p = tiny.getPixel(i % 8, i / 8)
                        ((p shr 16 and 0xFF) * 30 + (p shr 8 and 0xFF) * 59 + (p and 0xFF) * 11) / 100
                    }
                    if (tiny !== bmp) tiny.recycle()
                    bmp.recycle()
                    val avg = gray.average()
                    var h = 0L
                    gray.forEachIndexed { i, g -> if (g >= avg) h = h or (1L shl i) }
                    h
                }.getOrNull()
            }
        } ?: return null
        hashCache[key] = hash
        return hash
    }
}
