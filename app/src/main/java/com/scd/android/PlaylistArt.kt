package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.concurrent.ConcurrentHashMap

object PlaylistArt {

    private val resolved = ConcurrentHashMap<String, List<String>>()

    fun cached(urn: String): List<String>? = resolved[urn]

    fun remember(urn: String, urls: List<String>) {
        resolved[urn] = urls
    }

    fun clear() = resolved.clear()
}

private val sizeSuffix = Regex("-(large|original|crop|small|badge|tiny|mini|t\\d+x\\d+)(\\.\\w+)?$")

private fun artKey(url: String): String =
    url.substringBefore('?').substringAfterLast('/').replace(sizeSuffix, "")

private val hashGate = kotlinx.coroutines.sync.Semaphore(6)
private val hashCache = ConcurrentHashMap<String, Long>()

private suspend fun imageHash(url: String): Long? {
    val key = artKey(url)
    hashCache[key]?.let { return it }
    val small = Api.artworkUrl(url, "small") ?: return null
    val hash = hashGate.withPermitCompat {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
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
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
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

private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermitCompat(block: suspend () -> T): T {
    acquire()
    try {
        return block()
    } finally {
        release()
    }
}

private suspend fun distinctCovers(urls: List<String>, need: Int = 4): List<String> {
    val byKey = urls.distinctBy { artKey(it) }.take(12)
    val hashes = kotlinx.coroutines.coroutineScope {
        byKey.map { u -> async { imageHash(u) } }.awaitAll()
    }
    val picked = mutableListOf<String>()
    val pickedHashes = mutableListOf<Long>()
    byKey.forEachIndexed { i, u ->
        if (picked.size >= need) return@forEachIndexed
        val h = hashes[i]
        if (h != null && pickedHashes.any { java.lang.Long.bitCount(it xor h) <= 6 }) return@forEachIndexed
        picked += u
        if (h != null) pickedHashes += h
    }
    return picked
}

@Composable
private fun playlistArtUrls(playlist: Playlist): List<String> {
    val own = playlist.artwork_url
    if (!own.isNullOrEmpty()) return listOfNotNull(Api.artworkUrl(own, "t120x120"))

    var urls by remember(playlist.urn) {
        mutableStateOf(PlaylistArt.cached(playlist.urn) ?: emptyList())
    }

    LaunchedEffect(playlist.urn) {
        if (PlaylistArt.cached(playlist.urn) != null) return@LaunchedEffect
        val page = runCatching { Api.playlistTracks(playlist.urn, 0, limit = 20) }.getOrNull()
            ?: return@LaunchedEffect
        val raw = page.collection.mapNotNull { it.artwork_url?.takeIf { url -> url.isNotBlank() } }
        val found = distinctCovers(raw).mapNotNull { Api.artworkUrl(it, "t120x120") }
        PlaylistArt.remember(playlist.urn, found)
        urls = found
    }

    return urls
}

@Composable
fun PlaylistArtwork(playlist: Playlist, size: Dp, corner: Dp = 6.dp) {
    val urls = playlistArtUrls(playlist)

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            urls.size >= 4 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Tile(urls[0], Modifier.fillMaxSize().weight(1f))
                    Tile(urls[1], Modifier.fillMaxSize().weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Tile(urls[2], Modifier.fillMaxSize().weight(1f))
                    Tile(urls[3], Modifier.fillMaxSize().weight(1f))
                }
            }

            urls.isNotEmpty() -> AsyncImage(
                model = urls.first(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Icon(
                painterResource(R.drawable.ic_music),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
private fun Tile(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
