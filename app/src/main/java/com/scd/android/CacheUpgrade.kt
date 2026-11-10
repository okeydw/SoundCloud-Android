package com.scd.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class TierSource(val url: String, val tier: Quality?, val askable: Boolean)

object TierSources {
    fun at(
        urn: String,
        tier: Quality,
        goPlus: Boolean,
        withStorage: Boolean,
        verdict: QualityLadder.Verdict,
    ): Sequence<TierSource> = sequence {
        if (withStorage) {
            for (url in ScDataSource.storageUrlsFor(urn)) yield(TierSource(url, tier, false))
        }
        val main = QualityLadder.tierUrl(Api.streamUrl(urn), tier)
        yield(TierSource(main, tier, true))
        if (Prefs.star) {
            ScDataSource.starStreamOf(main)?.takeIf { it != main }?.let { yield(TierSource(it, tier, true)) }
        }
        if (tier != Quality.UQ) {
            val resolved = ScDataSource(Api.http).resolveDownload(main)
            verdict.asked(tier)
            verdict.answered(tier, if (resolved.url == null) resolved.refusedFor else null)
            resolved.urls.forEach { yield(TierSource(it, tier, false)) }
        }
    }

    fun ladder(
        urn: String,
        wanted: Quality,
        above: Quality,
        goPlus: Boolean,
        verdict: QualityLadder.Verdict,
    ): Sequence<TierSource> = sequence {
        val stored = ScDataSource.storageTierOf(urn)
        var storageDone = false
        for (tier in QualityLadder.upperTiers(QualityLadder.allowed(wanted), above)) {
            if (QualityLadder.refuses(tier)) continue
            val withStorage = !storageDone && stored != null && stored.rank >= tier.rank
            if (withStorage) storageDone = true
            yieldAll(at(urn, tier, goPlus, withStorage, verdict))
        }
    }

    class Fetched(val ok: Boolean, val header: String?)

    fun fetch(client: OkHttpClient, source: TierSource, tmp: File, verdict: QualityLadder.Verdict): Fetched {
        val url = source.url
        if (EncHls.isEnc(url)) {
            val ok = runCatching { EncHls.writeTo(client, url, tmp); true }.getOrDefault(false)
            return Fetched(ok && Downloads.looksLikeAudio(tmp), null)
        }
        if (HlsDownload.isHls(url)) {
            val ok = runCatching {
                HlsDownload.writeTo(client, HlsDownload.manifest(url), tmp)
                true
            }.getOrDefault(false)
            return Fetched(ok && Downloads.looksLikeAudio(tmp), null)
        }
        val asked = source.tier?.takeIf { source.askable }
        asked?.let { verdict.asked(it) }
        val req = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful || res.header("content-length") == "0") {
                    asked?.let { verdict.answered(it, QualityLadder.refusalHold(res.code, res.header("retry-after"))) }
                    return@use Fetched(false, null)
                }
                asked?.let { verdict.answered(it, null) }
                val body = res.body ?: return@use Fetched(false, null)
                val header = res.header("x-audio-quality")?.lowercase()
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                ClearMp4.repair(tmp)
                Fetched(Downloads.looksLikeAudio(tmp), header)
            }
        } catch (e: java.io.IOException) {
            asked?.let { verdict.answered(it, null) }
            Fetched(false, null)
        }
    }

    fun durationOk(actualMs: Long, expectedMs: Long): Boolean {
        if (expectedMs <= 45_000L || actualMs <= 0L) return true
        val tolerance = maxOf(4_000L, (expectedMs * 0.04).toLong())
        return actualMs + tolerance >= expectedMs
    }
}

object CacheUpgrade {
    private val attempts = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private val client by lazy {
        AudioRoute.builder(Api.http)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun afterPlayed(urn: String?, context: Context) {
        if (urn == null || Prefs.offline || !QualityLadder.wantsUpgrade()) return
        if (YouTubeImport.isYouTube(urn) || Downloads.isDownloaded(urn)) return
        if (Prefs.prefetchWifiOnly && NetMonitor.isMetered(context)) return
        App.scope.launch(Dispatchers.IO) { runCatching { upgrade(urn) } }
    }

    private suspend fun upgrade(urn: String) {
        if (!MediaCache.holds(urn) && ScDataSource.spooledFiles(urn).isEmpty()) return
        val current = ServedQuality.of(urn) ?: if (ScDataSource.wasHq(urn)) Quality.HQ else Quality.SQ
        val wanted = QualityLadder.allowed(QualityLadder.wanted())
        if (current.rank >= wanted.rank) return
        if (!attempts.add(urn)) return
        val dir = ScDataSource.cacheDir ?: return
        val tmp = File(dir, "upgrade-${Integer.toHexString(urn.hashCode())}.part")
        val verdict = QualityLadder.Verdict()
        var served: Quality? = null
        try {
            TrackTurns.withTurn(TrackTurns.Priority.BACKGROUND) {
                val goPlus = ScDataSource.isGoPlus(urn)
                for (source in TierSources.ladder(urn, wanted, current, goPlus, verdict)) {
                    if (ScDataSource.isStorage(source.url) && ScDataSource.storageCooling(source.url)) continue
                    tmp.delete()
                    val got = TierSources.fetch(client, source, tmp, verdict)
                    if (!got.ok) continue
                    val tier = ScDataSource.servedTierOf(source.url, urn, got.header, source.tier)
                    if (tier.rank <= current.rank) {
                        Logs.add("stream", "upgrade for $urn served ${tier.label}, already had ${current.label}")
                        continue
                    }
                    val info = AudioProbe.inspect(tmp)
                    if (!info.playable) continue
                    if (AudioProbe.shortReason(info, tmp.length(), DurationCache.get(urn)) != null) continue
                    served = tier
                    break
                }
            }
        } finally {
            verdict.settle()
        }
        val tier = served
        if (tier == null) {
            tmp.delete()
            if (QualityLadder.refuses(wanted)) attempts.remove(urn)
            Logs.add("stream", "nothing above ${current.label} for $urn")
            return
        }
        if (!awaitNotPlaying(urn)) {
            tmp.delete()
            attempts.remove(urn)
            return
        }
        if (ScDataSource.adoptSpool(urn, tmp, tier)) {
            Logs.add("stream", "upgraded cached $urn ${current.label} → ${tier.label}")
        } else {
            tmp.delete()
        }
    }

    private suspend fun awaitNotPlaying(urn: String): Boolean {
        var waited = 0
        while (NowPlaying.urn == urn) {
            if (waited >= 1_800) return false
            delay(2_000L)
            waited += 2
        }
        return true
    }
}

object CachedCopy {
    fun extract(urn: String, dest: File): Quality? {
        dest.delete()
        val spool = ScDataSource.spooledFiles(urn).maxByOrNull { it.length() }
        val copied = (spool != null && runCatching { spool.copyTo(dest, overwrite = true); true }.getOrDefault(false)) ||
            MediaCache.copyFully(urn, dest)
        if (!copied || !Downloads.looksLikeAudio(dest)) {
            dest.delete()
            return null
        }
        return ServedQuality.of(urn) ?: if (ScDataSource.wasHq(urn)) Quality.HQ else Quality.SQ
    }
}
