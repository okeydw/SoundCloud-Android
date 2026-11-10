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

class MixedSourceException : IOException("cached part belongs to another file")

@androidx.annotation.OptIn(UnstableApi::class)
class ScDataSource(private val client: OkHttpClient) : BaseDataSource(true) {
    private var currentSpec: DataSpec? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var raf: RandomAccessFile? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var pickedHeader: String? = null

    private val ladder = HashMap<String, Quality>()

    private val askable = HashSet<String>()
    private var verdict = QualityLadder.Verdict()

    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        transferInitializing(dataSpec)
        ladder.clear()
        askable.clear()
        verdict = QualityLadder.Verdict()

        val position = dataSpec.position
        val length = dataSpec.length
        val requested = dataSpec.uri.toString()
        val urn = urnFromStreamUrl(requested)

        val sizeKey = MediaCache.keyOf(requested)
        val expectedTotal = if (position > 0L) knownTotal(sizeKey) else -1L
        val cacheFile = cacheDir?.let { File(it, spoolName(urn, requested)) }
        if (cacheFile != null && cacheFile.exists() && cacheFile.length() > 0L) {
            val short = if (position == 0L) spoolTooShort(cacheFile, urn) else null
            if (short != null) {
                Logs.add("stream", "cached $short → looking further")
                cacheFile.delete()
            } else if (expectedTotal <= 0L || cacheFile.length() == expectedTotal) {
                if (position == 0L) noteTotal(sizeKey, cacheFile.length())
                return openFromFile(cacheFile, dataSpec, position, length)
            } else {
                Logs.add("stream", "spooled copy is ${cacheFile.length()} B, playing ${expectedTotal} B → skip")
            }
        }

        var picked: Response? = null
        var pickedUrl: String? = null
        var attempts = 0
        var regular = 0
        var escalated = false
        var reachable = false
        var mismatched = false
        var racer = Racer()
        var refreshed = false
        var upperStartedAt = 0L
        var lastUpperTier: Quality? = null
        var backupStarted = false

        fun upperLeft(): Long =
            (QualityLadder.BACKUP_MS - (System.currentTimeMillis() - upperStartedAt)).coerceAtLeast(0L)

        fun answered(url: String, refusedFor: Long?) {
            if (url in askable) ladder[url]?.let { verdict.answered(it, refusedFor) }
        }

        fun settle(budgetMs: Long): Pair<Flight, Response>? {
            val deadline = System.currentTimeMillis() + budgetMs
            while (racer.pending > 0) {
                val left = deadline - System.currentTimeMillis()
                if (left <= 0L) return null
                val (flight, result) = racer.next(left) ?: return null
                val storage = isStorage(flight.url)
                val res = result.getOrNull()
                if (res == null) {
                    val e = result.exceptionOrNull()
                    logAttempt(flight.url, requested, "fail ${e?.javaClass?.simpleName}", flight.started)
                    answered(flight.url, null)
                    if (storage) noteStorageFail(flight.url)
                    continue
                }
                if (res.isSuccessful) {
                    if (res.header("content-length") == "0") {
                        logAttempt(flight.url, requested, "empty body", flight.started)
                        answered(flight.url, null)
                        res.close()
                        if (!storage) reachable = true
                        continue
                    }
                    val total = totalOf(res, position)
                    if (expectedTotal > 0L && total > 0L && total != expectedTotal) {
                        logAttempt(flight.url, requested, "other file ($total B, playing $expectedTotal B)", flight.started)
                        answered(flight.url, null)
                        res.close()
                        mismatched = true
                        continue
                    }
                    return flight to res
                }
                Api.noteSoundCloudFailure(res.peekBody(16_384).string(), res.request.header("x-session-id"))
                answered(flight.url, QualityLadder.refusalHold(res.code, res.header("retry-after")))
                if (!(storage && res.code == 404)) logAttempt(flight.url, requested, "HTTP ${res.code}", flight.started)
                if (!storage) reachable = true
                res.close()
                if (storage && res.code >= 500) noteStorageServerFail(flight.url)
                if (res.code == 401 && flight.url.contains("/download/")) SessionState.streamDenied()
                if (res.code == 401 && flight.url.contains("/stream/") && !refreshed) {
                    refreshed = true
                    if (refreshForStream()) {
                        racer.launch(flight.url, streamClient(client), buildGet(flight.url, dataSpec, position, length))
                    } else {
                        SessionState.streamDenied()
                    }
                }
            }
            return null
        }

        fun take(won: Pair<Flight, Response>): Boolean {
            val (flight, r) = won
            pickedHeader = r.header("x-audio-quality")?.lowercase()
            racer.finish(flight)
            racer = Racer()
            val url = flight.url
            answered(url, null)
            val storage = isStorage(url)
            if (!storage) reachable = true
            val size = r.body?.contentLength() ?: -1L
            val unsized = size < 0
            val suspect = size in 1..SNIPPET_MAX_BYTES && urn != null && (expectedMs[urn] ?: 0L) > 45_000L
            val fragmentedMp4 = if (!unsized && !suspect && !storage && position == 0L && cacheFile != null) {
                try {
                    val head = r.peekBody(8).bytes()
                    head.size >= 8 && head.copyOfRange(4, 8).decodeToString() == "ftyp" &&
                        ClearMp4.needsRepair(r.peekBody(32_768).bytes())
                } catch (e: IOException) {
                    r.close()
                    if (e is java.io.InterruptedIOException && e !is java.net.SocketTimeoutException) throw e
                    logAttempt(url, requested, "header ${e.javaClass.simpleName}", flight.started)
                    return false
                }
            } else false
            if ((unsized || suspect || fragmentedMp4) && position == 0L && cacheFile != null) {
                val spooled = try {
                    spoolToFile(r, cacheFile)
                    true
                } catch (e: java.net.SocketTimeoutException) {
                    logAttempt(url, requested, "spool timeout", flight.started)
                    if (storage) noteStorageFail(url)
                    false
                } catch (e: java.io.InterruptedIOException) {
                    logAttempt(url, requested, "cancelled", flight.started)
                    throw e
                } catch (e: IOException) {
                    logAttempt(url, requested, "spool ${e.message ?: e.javaClass.simpleName}", flight.started)
                    if (!storage) reachable = true
                    false
                }
                if (!spooled) return false
                spoolTooShort(cacheFile, urn)?.let { why ->
                    cacheFile.delete()
                    logAttempt(url, requested, "spool $why", flight.started)
                    if (storage) noteStorageFail(url)
                    return false
                }
                if (url != requested) logAttempt(url, requested, "ok", flight.started)
                remember(urn, url, requested)
                evictCache()
                ladder[url]?.let { verdict.served(it) }
                pickedUrl = url
                return true
            }
            if (url != requested) logAttempt(url, requested, "ok", flight.started)
            picked = r
            ladder[url]?.let { verdict.served(it) }
            pickedUrl = url
            return true
        }

        try {
            for (candidate in candidates(requested, urn, if (position > 0L) urn?.let { lastServed[it] } else null, ladderOn = position == 0L)) {
                val storage = isStorage(candidate)
                if (regular > 0 && !escalated && urn != null) {
                    escalated = true
                    StreamStatus.begin(urn)
                }
                if (storage && storageCooling(candidate)) continue
                if (isBadSource(candidate)) continue
                if ((HlsDownload.isHls(candidate) || EncHls.isEnc(candidate)) && expectedTotal > 0L) continue
                val tier = ladder[candidate]
                if (tier != null) {
                    if (upperStartedAt == 0L) upperStartedAt = System.currentTimeMillis()
                    if (lastUpperTier != null && tier != lastUpperTier && racer.pending > 0) {
                        val won = settle(upperLeft())
                        if (won != null && take(won)) break
                    }
                    lastUpperTier = tier
                } else if (upperStartedAt > 0L && !backupStarted) {
                    backupStarted = true
                    if (racer.pending > 0) {
                        val won = settle(upperLeft())
                        if (won != null && take(won)) break
                        if (racer.pending > 0) {
                            Logs.add("stream", "quality sources still silent for ${QualityLadder.BACKUP_MS / 1000}s, starting sq alongside")
                        }
                    }
                }
                attempts++
                if (!storage && tier == null) regular++
                if (HlsDownload.isHls(candidate) || EncHls.isEnc(candidate)) {
                    val enc = EncHls.isEnc(candidate)
                    val label = if (enc) "enc-hls" else HlsDownload.manifest(candidate)
                    val tag = if (enc) "enc-hls" else "hls"
                    if (racer.pending > 0) {
                        val won = settle(1L)
                        if (won != null && take(won)) break
                    }
                    if (cacheFile == null) continue
                    val started = System.currentTimeMillis()
                    val tmp = File(cacheFile.path + ".part")
                    val saved = try {
                        if (enc) {
                            EncHls.writeTo(streamClient(client), candidate, tmp)
                        } else {
                            HlsDownload.writeTo(streamClient(client), HlsDownload.manifest(candidate), tmp, urn?.let { expectedMs[it] } ?: 0L)
                        }
                        if (cacheFile.exists()) cacheFile.delete()
                        tmp.renameTo(cacheFile)
                    } catch (e: java.net.SocketTimeoutException) {
                        tmp.delete()
                        logAttempt(label, requested, "$tag timeout", started)
                        false
                    } catch (e: java.io.InterruptedIOException) {
                        tmp.delete()
                        logAttempt(label, requested, "cancelled $tag", started)
                        throw e
                    } catch (e: Exception) {
                        tmp.delete()
                        logAttempt(label, requested, "$tag ${e.message ?: e.javaClass.simpleName}", started)
                        reachable = true
                        false
                    }
                    if (saved) reachable = true
                    val short = if (saved) spoolTooShort(cacheFile, urn) else null
                    if (short != null) {
                        cacheFile.delete()
                        logAttempt(label, requested, "$tag $short", started)
                        continue
                    }
                    if (saved) {
                        logAttempt(label, requested, "ok $tag", started)
                        if (urn != null) {
                            deadUrns.remove(urn)
                            if (servedHq.size > 500) servedHq.clear()
                            servedHq[urn] = sourceIsHq(candidate, urn, null)
                        }
                        evictCache()
                        pickedHeader = null
                        tier?.let { verdict.served(it) }
                        pickedUrl = candidate
                        break
                    }
                    continue
                }
                val request = try {
                    buildGet(candidate, dataSpec, position, length)
                } catch (e: IllegalArgumentException) {
                    logAttempt(candidate, requested, "bad url", System.currentTimeMillis())
                    continue
                }
                racer.launch(candidate, if (storage) storageClient(client) else streamClient(client), request)
                if (tier != null && candidate in askable) verdict.asked(tier)
                val hedge = when {
                    tier != null -> minOf(if (storage) STORAGE_HEDGE_MS else TIER_HEDGE_MS, upperLeft())
                    storage -> STORAGE_HEDGE_MS
                    else -> HEDGE_MS
                }
                val won = settle(hedge)
                if (won != null && take(won)) break
            }
            if (pickedUrl == null && racer.pending > 0) {
                val won = settle(LATE_WAIT_MS)
                if (won != null) take(won)
            }
        } catch (e: InterruptedException) {
            throw java.io.InterruptedIOException("stream open cancelled")
        } finally {
            racer.finish(null)
            if (escalated && urn != null) StreamStatus.end(urn)
            verdict.settle()
        }

        if (pickedUrl == null && mismatched && urn != null) {
            Logs.add("stream", "only other files at ${position / 1024} KB → restart track from cache-free copy")
            forgetTotal(sizeKey)
            throw MixedSourceException()
        }
        if (pickedUrl == null) {
            if (reachable && urn != null) markDead(urn)
            Logs.add("stream", if (reachable) "no playable source after $attempts tries" else "sources unreachable after $attempts tries")
            throw if (reachable) NoPlayableSourceException() else IOException("sources unreachable")
        }
        if (urn != null) pickedUrl?.let { noteServed(urn, it) }
        if (urn != null && position == 0L) {
            pickedUrl?.let { ServedQuality.record(urn, servedTierOf(it, urn, pickedHeader, ladder[it])) }
        }
        val resp: Response = picked ?: run {
            if (position == 0L) noteTotal(sizeKey, cacheFile!!.length())
            return openFromFile(cacheFile!!, dataSpec, position, length)
        }
        if (position == 0L) noteTotal(sizeKey, totalOf(resp, 0L))
        remember(urn, pickedUrl!!, requested)

        response = resp
        val body = resp.body ?: throw IOException("empty body")
        val cl = body.contentLength()

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

    private class Flight(val url: String, val call: okhttp3.Call, val started: Long)

    private class Racer {
        private val results = java.util.concurrent.LinkedBlockingQueue<Pair<Flight, Result<Response>>>()
        private val gate = Any()
        private var settled = false
        private val flights = mutableListOf<Flight>()
        var pending = 0
            private set

        fun launch(url: String, http: OkHttpClient, request: Request) {
            val flight = Flight(url, http.newCall(request), System.currentTimeMillis())
            flights += flight
            pending++
            flight.call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    synchronized(gate) { if (!settled) results.offer(flight to Result.failure(e)) }
                }

                override fun onResponse(call: okhttp3.Call, response: Response) {
                    val late = synchronized(gate) {
                        if (!settled) results.offer(flight to Result.success(response))
                        settled
                    }
                    if (late) response.close()
                }
            })
        }

        fun next(timeoutMs: Long): Pair<Flight, Result<Response>>? {
            if (pending == 0) return null
            val item = results.poll(timeoutMs, TimeUnit.MILLISECONDS) ?: return null
            pending--
            return item
        }

        fun finish(keep: Flight?) {
            synchronized(gate) { settled = true }
            for (f in flights) if (f !== keep) f.call.cancel()
            while (true) {
                val (_, result) = results.poll() ?: break
                result.getOrNull()?.close()
            }
            pending = 0
        }
    }

    private fun remember(urn: String?, pickedUrl: String, requested: String) {
        if (urn == null) return
        deadUrns.remove(urn)
        if (servedHq.size > 500) servedHq.clear()
        servedHq[urn] = sourceIsHq(pickedUrl, urn, pickedHeader)
        if (isStorage(pickedUrl)) noteStorageOk(pickedUrl)
        if (pickedUrl != requested && !pickedUrl.contains("sndcdn.com") && pickedUrl !in ladder) {
            if (resolvedAlt.size > 500) resolvedAlt.clear()
            resolvedAlt[urn] = pickedUrl
        }
    }

    private fun logAttempt(url: String, requested: String, outcome: String, startedAt: Long) {
        if (url == requested && outcome == "ok") return
        val host = url.substringAfter("://").substringBefore('/')
        Logs.add("stream", "$outcome @$host ${System.currentTimeMillis() - startedAt}ms")
    }

    private fun totalOf(res: Response, position: Long): Long {
        if (res.code == 206) {
            return res.header("content-range")?.substringAfterLast('/')?.trim()?.toLongOrNull() ?: -1L
        }
        val cl = res.body?.contentLength() ?: -1L
        return if (cl > 0L && res.code == 200) cl else -1L
    }

    private fun candidates(
        requested: String,
        urn: String?,
        pinned: String? = null,
        ladderOn: Boolean = false,
    ): Sequence<String> = sequence {
        val isStream = requested.contains("/stream/")
        val seen = mutableSetOf<String>()
        if (pinned != null && !isBadSource(pinned)) {
            seen.add(pinned)
            yield(pinned)
        }
        if (isStream && ladderOn && urn != null) {
            for (url in ladderCandidates(requested, urn)) {
                if (seen.add(url)) yield(url)
            }
        }
        val remembered = urn?.let { resolvedAlt[it] }
        if (remembered != null && seen.add(remembered)) yield(remembered)

        if (isStream && (Prefs.storageFirst || isForcedHq(urn) || isGoPlus(urn))) {
            for (url in storageCandidates(requested)) {
                if (seen.add(url)) yield(url)
            }
        }
        if (isStream && (isForcedHq(urn) || isGoPlus(urn))) {
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
            yieldAll(resolveDownload(requested).urls)
            ScAnon.sourceUrl(urn!!)?.let { yield(it) }
        }
        starHq(requested)?.let { yield(it) }
        starPlain(requested)?.let { yield(it) }
        for (url in storageCandidates(requested)) yield(url)
        if (!regular) {
            val sq = QualityLadder.tierUrl(requested, Quality.SQ)
            if (sq != requested) yield(sq)
            yieldAll(resolveDownload(sq).urls)
            if (urn != null && !isGoPlus(urn)) ScAnon.sourceUrl(urn)?.let { yield(it) }
        }
    }

    private fun ladderCandidates(requested: String, urn: String): Sequence<String> = sequence {
        val tiers = QualityLadder.upperTiers(QualityLadder.allowed(QualityLadder.wanted()))
        if (tiers.isEmpty()) return@sequence
        val stored = Quality.parse(storageQuality[urn])
        var storageTried = false
        for (tier in tiers) {
            if (QualityLadder.refuses(tier)) continue
            if (!storageTried && stored != null && stored.rank >= tier.rank) {
                storageTried = true
                for (url in storageUrlsFor(urn)) {
                    ladder[url] = tier
                    yield(url)
                }
            }
            val streams = buildList<String> {
                add(QualityLadder.tierUrl(requested, tier))
                if (Prefs.star) starStreamOf(requested)?.let { add(QualityLadder.tierUrl(it, tier)) }
            }
            for (url in streams.distinct()) {
                ladder[url] = tier
                askable += url
                yield(url)
            }
            if (tier != Quality.UQ) {
                val resolved = resolveDownload(QualityLadder.tierUrl(requested, tier))
                verdict.asked(tier)
                verdict.answered(tier, if (resolved.url == null) resolved.refusedFor else null)
                for (url in resolved.urls) {
                    ladder[url] = tier
                    yield(url)
                }
            }
        }
    }

    private fun starHq(url: String): String? {
        if (!Prefs.star || !Prefs.hqStreaming) return null
        if (urnFromStreamUrl(url)?.let { hqMissing[it] } == true) return null
        val candidate = starStreamOf(url)?.let { QualityLadder.tierUrl(it, Quality.HQ) } ?: return null
        return candidate.takeIf { it != url }
    }

    private fun starPlain(url: String): String? {
        if (!Prefs.star) return null
        return starStreamOf(url)?.let { QualityLadder.tierUrl(it, Quality.SQ) }?.takeIf { it != url }
    }

    private fun buildGet(url: String, dataSpec: DataSpec, position: Long, length: Long): Request {
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
        return builder.build()
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

    internal fun resolveViaDownload(streamUrl: String): String? = resolveDownload(streamUrl).url

    internal class Resolved(val urls: List<String>, val refusedFor: Long? = null) {
        val url: String? get() = urls.firstOrNull()
    }

    internal fun resolveDownload(streamUrl: String): Resolved {
        val tier = QualityLadder.tierOf(streamUrl)
        val urn = urnFromStreamUrl(streamUrl)?.let { if (tier.rank > Quality.SQ.rank) "$it|${tier.label}" else it }
        if (urn != null) {
            resolvedDownload[urn]?.let { (urls, at) ->
                val ttl = if (urls.isEmpty()) 2 * 60_000L else 10 * 60_000L
                if (System.currentTimeMillis() - at < ttl) {
                    if (urls.isEmpty()) Logs.add("stream", "download: no usable stream (checked recently)")
                    return Resolved(urls)
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
            resolvedDownload[urn] = probe.urls to System.currentTimeMillis()
        }
        return Resolved(probe.urls, QualityLadder.refusalHold(probe.code, probe.retryAfter))
    }

    private class DownloadProbe(val urls: List<String>, val code: Int, val retryAfter: String? = null) {
        val url: String? get() = urls.firstOrNull()
    }

    private fun fetchDownloadCandidate(streamUrl: String): DownloadProbe {
        val url = streamUrl.replaceFirst("/stream/", "/download/")
        val host = url.substringAfter("://").substringBefore('/')
        val req = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        val asked = QualityLadder.tierOf(streamUrl)
        return runCatching {
            probeClient(client).newCall(req).execute().use { r ->
                if (!r.isSuccessful) {
                    Api.noteSoundCloudFailure(r.peekBody(16_384).string(), r.request.header("x-session-id"))
                    Logs.add("stream", "download HTTP ${r.code} @$host")
                    return@use DownloadProbe(emptyList(), r.code, r.header("retry-after"))
                }
                val parsed = Api.parseDownload(r.body?.string() ?: "")
                if (parsed == null) {
                    Logs.add("stream", "download: unreadable answer @$host")
                    return@use DownloadProbe(emptyList(), -2)
                }
                Logs.add(
                    "stream",
                    "candidates: " + parsed.candidates.joinToString(", ") {
                        "${it.kind}/${it.quality ?: "?"}"
                    },
                )
                urnFromStreamUrl(streamUrl)?.let { u ->
                    if (parsed.candidates.isNotEmpty()) {
                        if (hqMissing.size > 1000) hqMissing.clear()
                        hqMissing[u] = parsed.candidates.none { Quality.of(it.quality).rank >= Quality.HQ.rank }
                    }
                }
                val usable = orderDownloadCandidates(parsed.candidates, asked)
                if (usable.isEmpty() && parsed.candidates.isNotEmpty() &&
                    parsed.candidates.all { it.kind.contains("encrypted") }
                ) {
                    Logs.add("stream", "only DRM-protected stream, cannot play or download")
                }
                val picked = usable.mapNotNull { candidate ->
                    val pickedUrl = when (candidate.kind) {
                        "progressive" -> candidate.url
                        "encrypted-hls" -> EncHls.wrap(candidate.init_base64, candidate.segments, candidate.key_base64)
                        else -> candidate.manifest_url?.let { HlsDownload.wrap(it) }
                    }
                    if (pickedUrl != null) noteCandidateQuality(pickedUrl, candidate.quality)
                    pickedUrl
                }.distinct()
                DownloadProbe(picked, 200)
            }
        }.getOrElse {
            Logs.add("stream", "download failed @$host: ${it.javaClass.simpleName}")
            DownloadProbe(emptyList(), -1)
        }
    }

    internal fun orderDownloadCandidates(candidates: List<DownloadCandidate>, asked: Quality): List<DownloadCandidate> {
        val playable = candidates
            .filter { it.quality != "lq" }
            .filter {
                (it.kind == "progressive" && !it.url.isNullOrEmpty()) ||
                    (it.kind == "hls" && !it.manifest_url.isNullOrEmpty()) ||
                    (it.kind == "encrypted-hls" && !it.key_base64.isNullOrEmpty() && it.segments.isNotEmpty() && !it.init_base64.isNullOrEmpty())
            }
        val kindScore = { c: DownloadCandidate -> when (c.kind) { "progressive" -> 0; "hls" -> 1; else -> 2 } }
        return if (asked.rank > Quality.SQ.rank) {
            playable
                .filter { Quality.of(it.quality).rank >= asked.rank }
                .sortedWith(compareBy({ -Quality.of(it.quality).rank }, kindScore))
        } else {
            playable.sortedWith(compareBy({ Quality.of(it.quality).rank }, kindScore))
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
            ClearMp4.repair(tmp)
            if (!Downloads.looksLikeAudio(tmp)) throw IOException("spooled body is not audio (${tmp.length()} B)")
            if (cacheFile.exists()) cacheFile.delete()
            if (!tmp.renameTo(cacheFile)) throw IOException("cache rename failed")
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun openFromFile(file: File, dataSpec: DataSpec, position: Long, length: Long): Long {
        if (position == 0L) {
            ClearMp4.repair(file)
            markPlayed(file)
        }
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
        private const val STORAGE_HEDGE_MS = 1_200L
        private const val HEDGE_MS = 5_000L
        private const val LATE_WAIT_MS = 35_000L

        private const val TIER_HEDGE_MS = 1_500L

        @Volatile
        var cacheDir: File? = null

        fun servedTierOf(url: String, urn: String?, header: String?, asked: Quality? = null): Quality =
            Quality.parse(header)
                ?: Quality.parse(candidateQuality[url])
                ?: (if (isStorage(url)) urn?.let { Quality.parse(storageQuality[it]) } else null)
                ?: asked
                ?: if (sourceIsHq(url, urn, header)) Quality.HQ else Quality.SQ

        fun storageTierOf(urn: String): Quality? = Quality.parse(storageQuality[urn])

        fun starStreamOf(url: String): String? {
            val i = url.indexOf("/stream/")
            if (i < 0) return null
            return Endpoints.STREAM_STAR + url.substring(i)
        }

        private fun markPlayed(file: File) {
            val dir = cacheDir ?: return
            if (file.parentFile?.absolutePath == dir.absolutePath) runCatching { file.setLastModified(System.currentTimeMillis()) }
        }

        fun spooledFiles(urn: String): List<File> {
            val dir = cacheDir ?: return emptyList()
            return listOf("sq", "hq").map { File(dir, spoolNameFor(urn, it)) }.filter { it.isFile && it.length() > 0L }
        }

        fun spooledFile(urn: String, requested: String): File? =
            cacheDir?.let { File(it, spoolName(urn, requested)) }?.takeIf { it.isFile && it.length() > 0L }

        fun adoptSpool(urn: String, file: File, tier: Quality): Boolean {
            val dir = cacheDir ?: return false
            invalidateNow(urn)
            val target = File(dir, spoolNameFor(urn, "sq"))
            val moved = runCatching {
                target.delete()
                file.renameTo(target) || run {
                    file.copyTo(target, overwrite = true)
                    file.delete()
                    true
                }
            }.getOrDefault(false)
            if (!moved || !target.isFile || target.length() == 0L) {
                target.delete()
                return false
            }
            markPlayed(target)
            ServedQuality.record(urn, tier)
            evictCache()
            return true
        }

        private val resolvedAlt = ConcurrentHashMap<String, String>()
        private val resolvedDownload = ConcurrentHashMap<String, Pair<List<String>, Long>>()

        private val forceHq = ConcurrentHashMap<String, Boolean>()
        private val goPlusUrns = ConcurrentHashMap<String, Boolean>()
        private val servedHq = ConcurrentHashMap<String, Boolean>()
        private val deadUrns = ConcurrentHashMap<String, Long>()
        private val lastServed = ConcurrentHashMap<String, String>()
        private val hqMissing = ConcurrentHashMap<String, Boolean>()
        private val storageQuality = ConcurrentHashMap<String, String>()
        private val candidateQuality = ConcurrentHashMap<String, String>()

        @Synchronized
        fun noteStorageQuality(urn: String, quality: String?, overwrite: Boolean = true) {
            val reported = Quality.parse(quality) ?: return
            loadKnownQuality()
            val before = AudioQualityPolicy.bestKnown(storageQuality[urn], knownQuality[urn])
            val best = AudioQualityPolicy.bestKnown(before?.label, reported.label) ?: return
            if (!overwrite && storageQuality[urn] == best.label) return
            if (storageQuality.size > 3000) storageQuality.clear()
            storageQuality[urn] = best.label
            if (knownQuality[urn] == best.label) return
            knownQuality[urn] = best.label
            if (before != null && best.rank > before.rank && urn != NowPlaying.urn) {
                Logs.add("stream", "storage quality ${before.label} → ${best.label}, dropping cached copy")
                invalidate(urn)
            }
            if (knownQuality.size > 20_000) knownQuality.clear()
            scheduleKnownSave()
        }

        private val knownQuality = ConcurrentHashMap<String, String>()

        @Volatile
        private var knownLoaded = false

        @Volatile
        private var knownSaveQueued = false

        private fun knownFile(): File? = cacheDir?.let { File(it, "storage_quality.txt") }

        private fun loadKnownQuality() {
            if (knownLoaded) return
            synchronized(knownQuality) {
                if (knownLoaded) return
                runCatching {
                    knownFile()?.takeIf { it.exists() }?.forEachLine { line ->
                        val i = line.indexOf(' ')
                        if (i > 0) knownQuality.putIfAbsent(line.substring(0, i), line.substring(i + 1))
                    }
                }
                knownLoaded = true
            }
        }

        private fun scheduleKnownSave() {
            if (knownSaveQueued) return
            knownSaveQueued = true
            App.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                kotlinx.coroutines.delay(5_000L)
                knownSaveQueued = false
                runCatching {
                    val file = knownFile() ?: return@runCatching
                    val tmp = File(file.path + ".tmp")
                    tmp.writeText(knownQuality.entries.joinToString("\n") { "${it.key} ${it.value}" })
                    tmp.renameTo(file)
                }
            }
        }

        private fun noteCandidateQuality(url: String, quality: String?) {
            val q = quality?.lowercase() ?: return
            if (candidateQuality.size > 500) candidateQuality.clear()
            candidateQuality[url] = q
        }

        fun sourceIsHq(url: String, urn: String?, header: String?): Boolean =
            AudioQualityPolicy.isHigh(header, if (isStorage(url)) urn?.let { storageQuality[it] } else candidateQuality[url])

        private val expectedMs = ConcurrentHashMap<String, Long>()

        fun expectDuration(urn: String, ms: Long) {
            if (ms <= 0L) return
            if (expectedMs.size > 3000) expectedMs.clear()
            expectedMs[urn] = ms
        }

        private fun probeMs(file: File): Long? = AudioProbe.durationMs(file)

        private const val SNIPPET_MAX_BYTES = 2_500_000L

        fun durationOf(file: File): Long? = probeMs(file)

        fun spoolFiles(): List<File> = cacheDir?.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".m4a") }

        fun scanSpools(skipUrn: String?, step: () -> Unit = {}): Pair<Int, Int> {
            val skip = skipUrn?.let { u -> setOf(spoolNameFor(u, "sq"), spoolNameFor(u, "hq")) }.orEmpty()
            var checked = 0
            var removed = 0
            for (f in spoolFiles()) {
                step()
                if (f.name in skip) continue
                val ms = probeMs(f) ?: continue
                checked++
                if (isSnippetLength(ms) && f.delete()) removed++
            }
            return checked to removed
        }

        fun isSnippetLength(actualMs: Long): Boolean =
            kotlin.math.abs(actualMs - 10_000L) < 1_500L || kotlin.math.abs(actualMs - 30_000L) < 1_500L

        private fun spoolTooShort(file: File, urn: String?): String? {
            if (urn == null) return null
            val expected = expectedMs[urn]?.takeIf { it > 45_000L } ?: return null
            val info = AudioProbe.inspect(file)
            return AudioProbe.shortReason(info, file.length(), expected)?.let {
                "$it (samples ${info.ms?.div(1000) ?: "?"}s, index ${info.declaredMs?.div(1000) ?: "?"}s)"
            }
        }
        private val badSources = ConcurrentHashMap<String, Long>()
        private val totals = ConcurrentHashMap<String, Long>()

        private fun noteTotal(key: String, total: Long) {
            if (total <= 0L) return
            if (totals.size > 1000) totals.clear()
            totals[key] = total
        }

        private fun forgetTotal(key: String) {
            totals.remove(key)
        }

        private fun knownTotal(key: String): Long =
            MediaCache.contentLength(key).takeIf { it > 0L } ?: totals[key] ?: -1L

        private fun noteServed(urn: String, url: String) {
            if (lastServed.size > 500) lastServed.clear()
            lastServed[urn] = url
        }

        private fun isBadSource(url: String): Boolean {
            val at = badSources[url] ?: return false
            if (System.currentTimeMillis() - at < 15 * 60_000L) return true
            badSources.remove(url)
            return false
        }

        fun blameSource(urn: String) {
            val url = lastServed.remove(urn) ?: return
            if (badSources.size > 300) badSources.clear()
            badSources[url] = System.currentTimeMillis()
            val label = audioSourceLabel(url)
            Logs.add("stream", "source gave unplayable data → skip @$label${if (QualityLadder.tierOf(url).rank > 0) " ${QualityLadder.tierOf(url).label}" else ""}")
        }

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
            val tiers = if (Prefs.star) listOf(true, false) else listOf(false)
            val direct = tiers.map { if (it) Endpoints.STORAGE_STAR else Endpoints.STORAGE_MAIN }
            val urls = direct.map { "$it/redirect/$file" } +
                direct.map { "$it/$file" } +
                tiers.flatMap { Endpoints.storageRelays(it) }.map { "$it/$file" }
            return urls.sortedBy { storageRank(it) }
        }

        private fun storageRank(url: String): Int {
            val key = storageKey(url)
            return when {
                storageCooling(url) -> 2
                key == lastGoodStorage -> 0
                else -> 1
            }
        }

        fun hasSpooled(urn: String): Boolean {
            val dir = cacheDir ?: return false
            return listOf("sq", "hq").any { runCatching { File(dir, spoolNameFor(urn, it)).length() > 0L }.getOrDefault(false) }
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
            totals.remove(urn)
            totals.remove("$urn|hq")
            dropSpooled(urn)
            runCatching { MediaCache.remove(urn) }
        }

        fun invalidate(urn: String) {
            resolvedAlt.remove(urn)
            totals.remove(urn)
            totals.remove("$urn|hq")
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

        private fun storageKey(url: String): String {
            val host = hostOf(url)
            return if (url.contains("/redirect/")) "$host/redirect" else host
        }

        private val serverDown = ConcurrentHashMap<Boolean, Long>()

        private fun starStorage(url: String) = hostOf(url).startsWith("storage-star")

        fun storageCooling(url: String): Boolean {
            val now = System.currentTimeMillis()
            if (!url.contains("/redirect/")) {
                serverDown[starStorage(url)]?.let { if (now - it < 60_000L) return true }
            }
            val at = storageDown[storageKey(url)] ?: return false
            return now - at < 60_000L
        }

        fun noteStorageServerFail(url: String) {
            noteStorageFail(url)
            if (!url.contains("/redirect/") && !hostOf(url).contains(".relay.")) serverDown[starStorage(url)] = System.currentTimeMillis()
        }

        @Volatile
        private var lastGoodStorage: String? = null

        fun noteStorageFail(url: String) {
            val key = storageKey(url)
            storageDown[key] = System.currentTimeMillis()
            if (lastGoodStorage == key) lastGoodStorage = null
        }

        @Volatile
        var knownStorageFile: String? = null
            private set

        fun noteStorageOk(url: String) {
            if (!url.contains("/redirect/")) knownStorageFile = url.substringAfter("://").substringAfter('/', "").substringBefore('?').ifEmpty { null }
            val key = storageKey(url)
            storageDown.remove(key)
            if (!url.contains("/redirect/")) serverDown.remove(starStorage(url))
            lastGoodStorage = key
        }

        fun storageClient(base: OkHttpClient): OkHttpClient {
            storage?.let { return it }
            return synchronized(this) {
                storage ?: AudioRoute.builder(base)
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
                return if (code == 200) {
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
                probe ?: AudioRoute.builder(base)
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
                stream ?: AudioRoute.builder(base)
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
