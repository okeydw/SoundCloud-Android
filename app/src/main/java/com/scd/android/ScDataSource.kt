package com.scd.android

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@UnstableApi
class ScDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private var currentSpec: DataSpec? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var raf: RandomAccessFile? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        transferInitializing(dataSpec)

        val position = dataSpec.position
        val length = dataSpec.length
        val requested = dataSpec.uri.toString()

        val cacheFile = cacheDir?.let { File(it, cacheKey(requested)) }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            return openFromFile(cacheFile, dataSpec, position, length)
        }

        val urn = urnFromStreamUrl(requested)
        var picked: Response? = null
        var pickedUrl: String? = null

        var escalated = false
        for (candidate in candidates(requested, urn)) {
            if (candidate != requested && !escalated && urn != null) {
                escalated = true
                StreamStatus.begin(urn)
            }
            val started = System.currentTimeMillis()
            val r = try {
                executeGet(candidate, dataSpec, position, length)
            } catch (e: IOException) {
                logAttempt(candidate, requested, "fail ${e.javaClass.simpleName}", started)
                continue
            }
            if (r.isSuccessful) {
                if (candidate != requested) logAttempt(candidate, requested, "ok", started)
                picked = r
                pickedUrl = candidate
                break
            }
            logAttempt(candidate, requested, "HTTP ${r.code}", started)
            r.close()
        }

        if (escalated && urn != null) StreamStatus.end(urn)

        val resp = picked ?: throw IOException("no playable source")
        if (urn != null && pickedUrl != null && pickedUrl != requested) {
            resolvedAlt[urn] = pickedUrl
        }

        response = resp
        val body = resp.body ?: throw IOException("empty body")
        if (resp.header("content-length") == "0") {
            resp.close()
            throw IOException("empty stream body")
        }
        val cl = body.contentLength()

        if (cl < 0 && position == 0L && cacheFile != null) {
            spoolToFile(resp, cacheFile)
            response = null
            evictCache()
            return openFromFile(cacheFile, dataSpec, position, length)
        }

        val stream = body.byteStream()
        val rangeHonored = resp.code == 206
        if (position > 0L && !rangeHonored) {
            skipFully(stream, position)
        }
        input = stream
        bytesRemaining = when {
            length != C.LENGTH_UNSET.toLong() -> length
            cl < 0 -> C.LENGTH_UNSET.toLong()
            rangeHonored -> cl
            else -> (cl - position).coerceAtLeast(0L)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun logAttempt(url: String, requested: String, outcome: String, startedAt: Long) {
        if (url == requested && outcome == "ok") return
        val host = url.substringAfter("://").substringBefore('/')
        Logs.add("stream", "$outcome @$host ${System.currentTimeMillis() - startedAt}ms")
    }

    private fun candidates(requested: String, urn: String?): Sequence<String> = sequence {
        val isStream = requested.contains("/stream/")
        val remembered = urn?.let { resolvedAlt[it] }
        if (remembered != null) yield(remembered)

        // Обычный путь уже отдавал по этому треку огрызок — сразу за полной версией
        if (isStream && isForcedHq(urn)) {
            starHq(requested)?.let { if (it != remembered) yield(it) }
        }

        if (requested != remembered) yield(requested)
        if (!isStream) return@sequence

        val seen = mutableSetOf(requested)
        remembered?.let { seen.add(it) }

        for (url in escalation(requested)) {
            if (seen.add(url)) yield(url)
        }
    }

    private fun escalation(requested: String): Sequence<String> = sequence {
        starHq(requested)?.let { yield(it) }
        for (url in storageCandidates(requested)) yield(url)
        resolveViaDownload(requested)?.let { yield(it) }
    }

    private fun starHq(url: String): String? {
        if (!Prefs.star || !Prefs.hqStreaming) return null
        val i = url.indexOf("/stream/")
        if (i < 0) return null
        val tail = url.substring(i)
        val withHq = when {
            tail.contains("hq=true") -> tail
            tail.contains('?') -> tail.replaceFirst("?", "?hq=true&")
            else -> "$tail?hq=true"
        }
        val candidate = Endpoints.STREAM_STAR + withHq
        return candidate.takeIf { it != url }
    }

    private fun executeGet(url: String, dataSpec: DataSpec, position: Long, length: Long): Response {
        val builder = Request.Builder()
            .url(url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
        if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
            val range = buildString {
                append("bytes=").append(position).append("-")
                if (length != C.LENGTH_UNSET.toLong()) append(position + length - 1)
            }
            builder.header("Range", range)
        }
        for ((k, v) in dataSpec.httpRequestHeaders) builder.header(k, v)
        return streamClient(client).newCall(builder.build()).execute()
    }

    private fun urnFromStreamUrl(url: String): String? {
        val i = url.indexOf("/stream/")
        if (i < 0) return null
        val tail = url.substring(i + "/stream/".length).substringBefore('?')
        if (tail.isEmpty()) return null
        return runCatching { URLDecoder.decode(tail, "UTF-8") }.getOrNull()
    }

    private fun storageCandidates(streamUrl: String): List<String> {
        val urn = urnFromStreamUrl(streamUrl) ?: return emptyList()
        if (!urn.startsWith("soundcloud:tracks:")) return emptyList()
        val file = urn.replace(':', '_') + ".m4a"
        val bases = if (Prefs.star) {
            listOf(Endpoints.STORAGE_STAR, Endpoints.STORAGE_MAIN)
        } else {
            listOf(Endpoints.STORAGE_MAIN)
        }
        return bases.map { "$it/$file" }
    }

    private fun resolveViaDownload(streamUrl: String): String? {
        val urn = urnFromStreamUrl(streamUrl)
        if (urn != null) resolvedDownload[urn]?.let { return it.ifEmpty { null } }
        val picked = fetchDownloadCandidate(streamUrl)
        if (urn != null) resolvedDownload[urn] = picked ?: ""
        return picked
    }

    private fun fetchDownloadCandidate(streamUrl: String): String? {
        val url = streamUrl.replaceFirst("/stream/", "/download/")
        val req = Request.Builder()
            .url(url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        return runCatching {
            probeClient(client).newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    Logs.add("stream", "download HTTP ${r.code}")
                    return@use null
                }
                val parsed = Api.parseDownload(r.body?.string() ?: "") ?: return@use null
                Logs.add(
                    "stream",
                    "candidates: " + parsed.candidates.joinToString(", ") {
                        "${it.kind}/${it.quality ?: "?"}"
                    },
                )
                val progressive = parsed.candidates
                    .filter { it.kind == "progressive" && !it.url.isNullOrEmpty() }
                (progressive.firstOrNull { it.quality == "hq" } ?: progressive.firstOrNull())?.url
            }
        }.getOrElse {
            Logs.add("stream", "download failed: ${it.javaClass.simpleName}")
            null
        }
    }

    private fun spoolToFile(resp: Response, cacheFile: File) {
        resp.use { r ->
            val src = r.body ?: throw IOException("empty body")
            val tmp = File(cacheFile.path + ".part")
            tmp.parentFile?.mkdirs()
            tmp.outputStream().use { out -> src.byteStream().copyTo(out) }
            if (tmp.length() <= 0L) {
                tmp.delete()
                throw IOException("empty spooled body")
            }
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmp.renameTo(cacheFile)) {
                tmp.delete()
                throw IOException("cache rename failed")
            }
        }
    }

    private fun openFromFile(file: File, dataSpec: DataSpec, position: Long, length: Long): Long {
        val f = RandomAccessFile(file, "r")
        if (position > 0L) f.seek(position)
        raf = f
        bytesRemaining = if (length != C.LENGTH_UNSET.toLong()) {
            length
        } else {
            (file.length() - position).coerceAtLeast(0L)
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    private fun skipFully(stream: InputStream, count: Long) {
        var left = count
        val scratch = ByteArray(16 * 1024)
        while (left > 0) {
            val skipped = stream.skip(left)
            if (skipped > 0) {
                left -= skipped
                continue
            }
            val toRead = minOf(left, scratch.size.toLong()).toInt()
            val read = stream.read(scratch, 0, toRead)
            if (read == -1) throw IOException("range skip hit EOF")
            left -= read
        }
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            minOf(readLength.toLong(), bytesRemaining).toInt()
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT
        val read = raf?.read(buffer, offset, toRead)
            ?: input?.read(buffer, offset, toRead)
            ?: -1
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentSpec?.uri

    override fun close() {
        runCatching { input?.close() }
        input = null
        runCatching { raf?.close() }
        raf = null
        runCatching { response?.close() }
        response = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        @UnstableApi
        override fun createDataSource(): DataSource = ScDataSource(client)
    }

    companion object {
        @Volatile
        var cacheDir: File? = null

        private val resolvedAlt = ConcurrentHashMap<String, String>()
        private val resolvedDownload = ConcurrentHashMap<String, String>()

        /** Треки, для которых обычный путь отдал огрызок — идём сразу за hq. */
        private val forceHq = ConcurrentHashMap<String, Boolean>()

        fun forceHq(urn: String, value: Boolean) {
            forceHq[urn] = value
            resolvedAlt.remove(urn)
        }

        fun isForcedHq(urn: String?): Boolean = urn != null && forceHq[urn] == true

        @Volatile
        private var probe: OkHttpClient? = null

        @Volatile
        private var stream: OkHttpClient? = null

        fun forgetResolved() {
            resolvedAlt.clear()
            resolvedDownload.clear()
        }

        fun probeClient(base: OkHttpClient): OkHttpClient {
            probe?.let { return it }
            return synchronized(this) {
                probe ?: base.newBuilder()
                    .callTimeout(8, TimeUnit.SECONDS)
                    .connectTimeout(4, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()
                    .also { probe = it }
            }
        }

        private fun streamClient(base: OkHttpClient): OkHttpClient {
            stream?.let { return it }
            return synchronized(this) {
                stream ?: base.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(12, TimeUnit.SECONDS)
                    .build()
                    .also { stream = it }
            }
        }

        private fun budgetBytes(): Long {
            val limit = Prefs.cacheLimit
            if (limit == CacheLimits.UNLIMITED) return Long.MAX_VALUE
            return (limit / 4).coerceAtLeast(256L * 1024 * 1024)
        }

        private fun cacheKey(url: String): String {
            val stable = url.replace(Regex("[?&]session_id=[^&]*"), "")
            val digest = MessageDigest.getInstance("SHA-1").digest(stable.toByteArray())
            return digest.joinToString("") { "%02x".format(it) } + ".m4a"
        }

        private fun evictCache() {
            val dir = cacheDir ?: return
            var files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".m4a") } ?: return

            val days = Prefs.cacheDays
            if (days != CacheLimits.FOREVER) {
                val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
                val (stale, fresh) = files.partition { it.lastModified() in 1 until cutoff }
                stale.forEach { it.delete() }
                files = fresh
            }

            val budget = budgetBytes()
            if (budget == Long.MAX_VALUE) return
            var total = files.sumOf { it.length() }
            if (total <= budget) return
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= budget) break
                total -= f.length()
                f.delete()
            }
        }
    }
}
