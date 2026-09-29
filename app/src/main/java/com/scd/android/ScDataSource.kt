package com.scd.android

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

class NoPlayableSourceException(message: String = MESSAGE) : IOException(message) {
    companion object {
        const val MESSAGE = "no playable source"

        fun matches(error: Throwable?): Boolean {
            var e = error
            var depth = 0
            while (e != null && depth < 8) {
                if (e is NoPlayableSourceException || e.message?.contains(MESSAGE) == true) return true
                e = e.cause
                depth++
            }
            return false
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
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
        val urn = urnFromStreamUrl(requested)

        val cacheFile = cacheDir?.let { File(it, spoolName(urn, requested)) }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            return openFromFile(cacheFile, dataSpec, position, length)
        }

        var picked: Response? = null
        var pickedUrl: String? = null
        var attempts = 0
        var escalated = false
        var reachable = false

        try {
            for (candidate in candidates(requested, urn)) {
                if (attempts > 0 && !escalated && urn != null) {
                    escalated = true
                    StreamStatus.begin(urn)
                }
                if (isStorage(candidate) && storageCooling(candidate)) continue
                attempts++
                val started = System.currentTimeMillis()
                val r = try {
                    executeGet(candidate, dataSpec, position, length)
                } catch (e: java.net.SocketTimeoutException) {
                    logAttempt(candidate, requested, "fail ${e.javaClass.simpleName}", started)
                    if (isStorage(candidate)) noteStorageFail(candidate)
                    continue
                } catch (e: java.io.InterruptedIOException) {
                    logAttempt(candidate, requested, "cancelled", started)
                    throw e
                } catch (e: IOException) {
                    logAttempt(candidate, requested, "fail ${e.javaClass.simpleName}", started)
                    if (isStorage(candidate)) noteStorageFail(candidate)
                    continue
                } catch (e: IllegalArgumentException) {
                    logAttempt(candidate, requested, "bad url", started)
                    continue
                }
                if (r.isSuccessful) {
                    if (candidate != requested) logAttempt(candidate, requested, "ok", started)
                    picked = r
                    pickedUrl = candidate
                    break
                }
                logAttempt(candidate, requested, "HTTP ${r.code}", started)
                reachable = true
                r.close()
                if (r.code == 401 && candidate.contains("/download/")) {
                    SessionState.streamDenied()
                }
            }
        } finally {
            if (escalated && urn != null) StreamStatus.end(urn)
        }

        if (picked == null) {
            if (reachable && urn != null) markDead(urn)
            throw if (reachable) NoPlayableSourceException() else IOException("sources unreachable")
        }
        val resp: Response = picked
        if (urn != null) deadUrns.remove(urn)
        if (urn != null && pickedUrl != null) {
            if (servedHq.size > 500) servedHq.clear()
            servedHq[urn] = pickedUrl.contains("hq=true")
            if (pickedUrl != requested && !pickedUrl.contains("sndcdn.com")) {
                if (resolvedAlt.size > 500) resolvedAlt.clear()
                resolvedAlt[urn] = pickedUrl
            }
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

        val seen = mutableSetOf<String>()
        remembered?.let { seen.add(it) }

        if (isStream && (isForcedHq(urn) || isGoPlus(urn))) {
            for (url in storageCandidates(requested)) {
                if (seen.add(url)) yield(url)
            }
            starHq(requested)?.let { if (seen.add(it)) yield(it) }
        }

        if (seen.add(requested)) yield(requested)
        if (!isStream) return@sequence

        for (url in escalation(requested)) {
            if (seen.add(url)) yield(url)
        }
    }

    private fun escalation(requested: String): Sequence<String> = sequence {
        val urn = urnFromStreamUrl(requested)
        val regular = urn != null && !isGoPlus(urn) && !isForcedHq(urn)
        if (regular) {
            resolveViaDownload(requested)?.let { yield(it) }
            ScAnon.progressiveUrl(urn!!)?.let { yield(it) }
        }
        starHq(requested)?.let { yield(it) }
        for (url in storageCandidates(requested)) yield(url)
        if (!regular) resolveViaDownload(requested)?.let { yield(it) }
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
        val builder = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
        if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
            val range = buildString {
                append("bytes=").append(position).append("-")
                if (length != C.LENGTH_UNSET.toLong()) append(position + length - 1)
            }
            builder.header("Range", range)
        }
        for ((k, v) in dataSpec.httpRequestHeaders) builder.header(k, v)
        val http = if (isStorage(url)) storageClient(client) else streamClient(client)
        return http.newCall(builder.build()).execute()
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
        return storageUrlsFor(urn)
    }

    internal fun resolveViaDownload(streamUrl: String): String? {
        val urn = urnFromStreamUrl(streamUrl)?.let { if (streamUrl.contains("hq=true")) "$it|hq" else it }
        if (urn != null) {
            resolvedDownload[urn]?.let { (url, at) ->
                val ttl = if (url.isEmpty()) 2 * 60_000L else 10 * 60_000L
                if (System.currentTimeMillis() - at < ttl) {
                    if (url.isEmpty()) Logs.add("stream", "download: no progressive (checked recently)")
                    return url.ifEmpty { null }
                }
            }
        }
        var probe = fetchDownloadCandidate(streamUrl)
        if (probe.code == 401 && refreshForStream()) probe = fetchDownloadCandidate(streamUrl)
        if (probe.url == null && probe.code != 200 && Prefs.star) {
            val i = streamUrl.indexOf("/stream/")
            val onStar = if (i >= 0) Endpoints.STREAM_STAR + streamUrl.substring(i) else null
            if (onStar != null && onStar != streamUrl) {
                val alt = fetchDownloadCandidate(onStar)
                if (alt.url != null || alt.code == 200) probe = alt
            }
        }
        if (probe.code == 401) SessionState.streamDenied()
        if (urn != null && (probe.url != null || probe.code == 200)) {
            if (resolvedDownload.size > 500) resolvedDownload.clear()
            resolvedDownload[urn] = (probe.url ?: "") to System.currentTimeMillis()
        }
        return probe.url
    }

    private class DownloadProbe(val url: String?, val code: Int)

    private fun fetchDownloadCandidate(streamUrl: String): DownloadProbe {
        val url = streamUrl.replaceFirst("/stream/", "/download/")
        val host = url.substringAfter("://").substringBefore('/')
        val req = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        return runCatching {
            probeClient(client).newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    Logs.add("stream", "download HTTP ${r.code} @$host")
                    return@use DownloadProbe(null, r.code)
                }
                val parsed = Api.parseDownload(r.body?.string() ?: "")
                if (parsed == null) {
                    Logs.add("stream", "download: unreadable answer @$host")
                    return@use DownloadProbe(null, -2)
                }
                Logs.add(
                    "stream",
                    "candidates: " + parsed.candidates.joinToString(", ") {
                        "${it.kind}/${it.quality ?: "?"}"
                    },
                )
                val progressive = parsed.candidates
                    .filter { it.kind == "progressive" && !it.url.isNullOrEmpty() }
                if (progressive.isEmpty() && parsed.candidates.isNotEmpty() &&
                    parsed.candidates.all { it.kind.contains("encrypted") }
                ) {
                    Logs.add("stream", "only DRM-protected stream, cannot play or download")
                }
                DownloadProbe((progressive.firstOrNull { it.quality == "hq" } ?: progressive.firstOrNull())?.url, 200)
            }
        }.getOrElse {
            Logs.add("stream", "download failed @$host: ${it.javaClass.simpleName}")
            DownloadProbe(null, -1)
        }
    }

    private fun spoolToFile(resp: Response, cacheFile: File) {
        val tmp = File(cacheFile.path + ".part")
        try {
            resp.use { r ->
                val src = r.body ?: throw IOException("empty body")
                tmp.parentFile?.mkdirs()
                tmp.outputStream().use { out -> src.byteStream().copyTo(out) }
            }
            if (!Downloads.looksLikeAudio(tmp)) throw IOException("spooled body is not audio (${tmp.length()} B)")
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmp.renameTo(cacheFile)) throw IOException("cache rename failed")
        } catch (e: Exception) {
            tmp.delete()
            throw e
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

    @androidx.annotation.OptIn(UnstableApi::class)
    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        override fun createDataSource(): DataSource = ScDataSource(client)
    }

    companion object {
        @Volatile
        var cacheDir: File? = null

        private val resolvedAlt = ConcurrentHashMap<String, String>()
        private val resolvedDownload = ConcurrentHashMap<String, Pair<String, Long>>()

        private val forceHq = ConcurrentHashMap<String, Boolean>()
        private val goPlusUrns = ConcurrentHashMap<String, Boolean>()
        private val servedHq = ConcurrentHashMap<String, Boolean>()
        private val deadUrns = ConcurrentHashMap<String, Long>()

        private fun markDead(urn: String) {
            if (deadUrns.size > 500) deadUrns.clear()
            deadUrns[urn] = System.currentTimeMillis()
        }

        fun failedRecently(urn: String?): Boolean {
            val at = urn?.let { deadUrns[it] } ?: return false
            return System.currentTimeMillis() - at < 60_000L
        }

        fun wasHq(urn: String?): Boolean = urn != null && servedHq[urn] == true

        fun storageUrlsFor(urn: String): List<String> {
            if (!urn.startsWith("soundcloud:tracks:")) return emptyList()
            val file = urn.replace(':', '_') + ".m4a"
            val bases = if (Prefs.star) {
                listOf(Endpoints.STORAGE_STAR, Endpoints.STORAGE_MAIN)
            } else {
                listOf(Endpoints.STORAGE_MAIN)
            }
            return bases.map { "$it/$file" }
        }

        fun dropSpooled(urn: String) {
            val dir = cacheDir
            if (dir != null) {
                for (variant in listOf("sq", "hq")) {
                    runCatching { File(dir, spoolNameFor(urn, variant)).delete() }
                }
            }
            resolvedAlt.remove(urn)
        }

        fun invalidateNow(urn: String) {
            resolvedAlt.remove(urn)
            dropSpooled(urn)
            runCatching { MediaCache.remove(urn) }
        }

        fun invalidate(urn: String) {
            resolvedAlt.remove(urn)
            App.scope.launch {
                dropSpooled(urn)
                runCatching { MediaCache.remove(urn) }
            }
        }

        fun markGoPlus(urn: String) {
            if (goPlusUrns.size > 1000) goPlusUrns.clear()
            goPlusUrns[urn] = true
        }

        fun isGoPlus(urn: String?): Boolean = urn != null && goPlusUrns[urn] == true

        fun forceHq(urn: String, value: Boolean) {
            if (forceHq.size > 500) forceHq.clear()
            forceHq[urn] = value
            resolvedAlt.remove(urn)
        }

        fun isForcedHq(urn: String?): Boolean = urn != null && forceHq[urn] == true

        @Volatile
        private var probe: OkHttpClient? = null

        @Volatile
        private var stream: OkHttpClient? = null

        @Volatile
        private var storage: OkHttpClient? = null

        private val storageDown = ConcurrentHashMap<String, Long>()

        private fun hostOf(url: String) = url.substringAfter("://").substringBefore('/')

        fun isStorage(url: String): Boolean = hostOf(url).startsWith("storage")

        fun storageCooling(url: String): Boolean {
            val at = storageDown[hostOf(url)] ?: return false
            return System.currentTimeMillis() - at < 60_000L
        }

        fun noteStorageFail(url: String) {
            storageDown[hostOf(url)] = System.currentTimeMillis()
        }

        fun storageClient(base: OkHttpClient): OkHttpClient {
            storage?.let { return it }
            return synchronized(this) {
                storage ?: base.newBuilder()
                    .connectTimeout(2, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                    .also { storage = it }
            }
        }

        private val refreshLock = Any()

        @Volatile
        private var streamRefreshAt = 0L

        fun refreshForStream(): Boolean {
            if (Api.sessionId == null) return false
            synchronized(refreshLock) {
                val now = System.currentTimeMillis()
                if (now - streamRefreshAt < 2 * 60_000L) return false
                if (now - SessionState.lastRefreshAt < 60_000L) return false
                streamRefreshAt = now
                SessionState.lastRefreshAt = now
                val code = runCatching { runBlocking { Api.refreshSession() } }.getOrDefault(-1)
                return if (code in 200..299) {
                    Logs.add("auth", "stream 401 → session refreshed, retrying")
                    resolvedAlt.clear()
                    resolvedDownload.clear()
                    deadUrns.clear()
                    true
                } else {
                    Logs.add("auth", "stream 401, refresh gave $code")
                    false
                }
            }
        }

        fun forgetResolved() {
            resolvedAlt.clear()
            resolvedDownload.clear()
            deadUrns.clear()
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
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                    .also { stream = it }
            }
        }

        private fun budgetBytes(): Long {
            val limit = Prefs.cacheLimit
            if (limit == CacheLimits.UNLIMITED) return Long.MAX_VALUE
            return (limit / 4).coerceAtLeast(256L * 1024 * 1024)
        }

        private fun sha1(value: String): String =
            MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
                .joinToString("") { "%02x".format(it) }

        private fun spoolNameFor(urn: String, variant: String): String = sha1("$urn|$variant") + ".m4a"

        private fun spoolName(urn: String?, url: String): String {
            if (urn != null) return spoolNameFor(urn, if (url.contains("hq=true")) "hq" else "sq")
            return sha1(url.replace(Regex("[?&]session_id=[^&]*"), "")) + ".m4a"
        }

        private fun evictCache() {
            val dir = cacheDir ?: return
            val hourAgo = System.currentTimeMillis() - 60L * 60L * 1000L
            dir.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".part") && it.lastModified() < hourAgo }
                ?.forEach { it.delete() }
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
