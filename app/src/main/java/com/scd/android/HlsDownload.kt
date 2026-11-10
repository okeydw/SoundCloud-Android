package com.scd.android

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI

object HlsDownload {
    const val PREFIX = "hls:"


    fun isHls(url: String): Boolean = url.startsWith(PREFIX)

    fun wrap(manifest: String): String = PREFIX + manifest

    fun manifest(url: String): String = url.removePrefix(PREFIX)

    private fun get(client: OkHttpClient, url: String): ByteArray {
        val req = Request.Builder().url(url).header("User-Agent", ScAnon.USER_AGENT).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("hls HTTP ${res.code}")
            return res.body?.bytes() ?: throw IOException("hls empty body")
        }
    }

    private fun resolve(base: String, ref: String): String =
        if (ref.startsWith("http://") || ref.startsWith("https://")) ref else runCatching { URI(base).resolve(ref).toString() }.getOrDefault(ref)

    private data class Playlist(val init: String?, val segments: List<String>, val durationMs: Long?)

    internal fun durationMs(text: String): Long? {
        val durations = text.lineSequence().map { it.trim() }.filter { it.startsWith("#EXTINF:") }
            .map { it.substringAfter(':').substringBefore(',').toDoubleOrNull() }.toList()
        if (durations.isEmpty() || durations.any { it == null || !it.isFinite() || it <= 0.0 }) return null
        return (durations.sumOf { it!! } * 1000).toLong()
    }

    private fun parse(text: String, base: String): Playlist {
        var init: String? = null
        val segments = mutableListOf<String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            val mapAt = line.indexOf("#EXT-X-MAP:URI=\"")
            if (mapAt >= 0) {
                val rest = line.substring(mapAt + 16)
                val end = rest.indexOf('"')
                if (end > 0) init = resolve(base, rest.substring(0, end))
                continue
            }
            if (line.startsWith("#EXT-X-KEY") && !line.contains("METHOD=NONE")) throw IOException("hls: encrypted")
            if (line.isEmpty() || line.startsWith("#")) continue
            segments += resolve(base, line)
        }
        return Playlist(init, segments, durationMs(text))
    }

    private fun ByteArray.hasEnca(): Boolean {
        for (i in 0..size - 4) {
            if (this[i] == 'e'.code.toByte() && this[i + 1] == 'n'.code.toByte() && this[i + 2] == 'c'.code.toByte() && this[i + 3] == 'a'.code.toByte()) return true
        }
        return false
    }

    private fun variant(text: String, base: String): String? {
        if (!text.contains("#EXT-X-STREAM-INF")) return null
        var best: String? = null
        var bestRate = -1L
        var rate = -1L
        var pending = false
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                rate = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                pending = true
                continue
            }
            if (pending && line.isNotEmpty() && !line.startsWith("#")) {
                if (rate > bestRate) {
                    bestRate = rate
                    best = resolve(base, line)
                }
                pending = false
            }
        }
        return best
    }

    private fun playlist(client: OkHttpClient, manifestUrl: String): Playlist {
        val text = get(client, manifestUrl).decodeToString()
        val media = variant(text, manifestUrl) ?: return parse(text, manifestUrl)
        return parse(get(client, media).decodeToString(), media)
    }

    fun writeTo(client: OkHttpClient, manifestUrl: String, target: File, expectedMs: Long = 0L) {
        val (init, segments, duration) = playlist(client, manifestUrl)
        if (segments.isEmpty()) throw IOException("hls: no segments")
        if (duration != null && expectedMs > 45_000L && duration + maxOf(4_000L, expectedMs / 25) < expectedMs) {
            throw IOException("hls: short ${duration / 1000}s of ${expectedMs / 1000}s")
        }
        target.parentFile?.mkdirs()
        try {
            target.outputStream().buffered().use { out ->
                if (init != null) {
                    val head = get(client, init)
                    if (head.hasEnca()) throw IOException("hls: encrypted")
                    out.write(head)
                }
                SegmentDownload.write(client, segments, out)
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        if (!Downloads.looksLikeAudio(target)) {
            target.delete()
            throw IOException("hls: not audio")
        }
    }
}
