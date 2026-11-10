package com.scd.android

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

object Downloads {
    private lateinit var dir: File
    private lateinit var indexFile: File
    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true }
    private val index = LinkedHashMap<String, Track>()

    private const val CHANNEL_ID = "downloads"
    const val NOTIF_ID = 2001

    private class DownloadJob(
        val tracks: List<Track>,
        val playlistUrn: String?,
        val playlistTitle: String?,
        val key: String?,
        val upgrade: Boolean = false,
    )

    const val UPGRADE_KEY = "upgrade-quality"

    private const val NO_HQ_TTL_MS = 7L * 24 * 60 * 60 * 1000

    private fun noHqPrefs() = appContext.getSharedPreferences("downloads_nohq", Context.MODE_PRIVATE)

    private fun markNoHq(urn: String) {
        runCatching { noHqPrefs().edit().putLong(urn, System.currentTimeMillis()).apply() }
    }

    private fun recentlyNoHq(urn: String): Boolean {
        val at = runCatching { noHqPrefs().getLong(urn, 0L) }.getOrDefault(0L)
        return at > 0L && System.currentTimeMillis() - at < NO_HQ_TTL_MS
    }

    fun upgradeAll(context: Context, onResult: (Int) -> Unit) {
        val ctx = context.applicationContext
        App.scope.launch {
            val target = QualityLadder.allowed(QualityLadder.upgradeWanted())
            val targets = tracks().filter { t ->
                val q = quality(t.urn) ?: return@filter false
                Quality.fromLabel(q).rank < target.rank
            }
            Logs.add("download", "quality upgrade: ${targets.size} to check, up to ${target.badge}")
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onResult(targets.size)
                if (targets.isEmpty()) return@withContext
                cancelledKeys.remove(UPGRADE_KEY)
                activeLists = activeLists + UPGRADE_KEY
                jobQueue.add(DownloadJob(targets, null, ctx.getString(R.string.upgrade_quality), UPGRADE_KEY, upgrade = true))
                ContextCompat.startForegroundService(ctx, Intent(ctx, DownloadService::class.java))
            }
        }
    }

    private fun reporter(verdict: QualityLadder.Verdict, source: TierSource): (Int, String?) -> Unit {
        val tier = source.tier?.takeIf { source.askable } ?: return { _, _ -> }
        verdict.asked(tier)
        return { code, retryAfter ->
            verdict.answered(tier, if (code in 200..299) null else QualityLadder.refusalHold(code, retryAfter))
        }
    }

    private suspend fun upgradeOne(
        track: Track,
        priority: TrackTurns.Priority = TrackTurns.Priority.BACKGROUND,
        wanted: Quality = QualityLadder.downloadWanted(),
    ): Boolean = withContext(Dispatchers.IO) {
        if (track.urn in inProgress || YouTubeImport.isYouTube(track.urn)) return@withContext false
        val old = fileFor(track.urn)
        if (!old.exists()) return@withContext false
        ScDataSource.noteStorageQuality(track.urn, track.scdMeta?.storage_quality, overwrite = false)
        val current = Quality.fromLabel(storedQuality(track.urn))
        val target = QualityLadder.allowed(wanted)
        if (current.rank >= target.rank) return@withContext false
        inProgress = inProgress + track.urn
        val base = baseName(track.urn)
        val tmp = File(dir, "$base.upgrade.part")
        val fallback = File(dir, "$base.upgrade.fallback.part")
        try {
            val oldMs = AudioProbe.durationMs(old)
            var reached = false
            val selection = UpgradeSelection(current, fallback)
            val verdict = QualityLadder.Verdict()
            try {
                TrackTurns.withTurn(priority) {
                    for (source in TierSources.ladder(track.urn, target, current, track.goPlus, verdict)) {
                        if (selection.enoughFor(source.tier ?: Quality.SQ)) break
                        val url = source.url
                        if (ScDataSource.isStorage(url) && ScDataSource.storageCooling(url)) continue
                        tmp.delete()
                        var header: String? = null
                        val report = reporter(verdict, source)
                        val ok = runCatching { fetchTo(url, tmp, { header = it }, report, track.displayDuration) }.getOrDefault(false)
                        if (!ok) continue
                        reached = true
                        if (truncated(tmp, track.displayDuration, track.urn, track.goPlus)) continue
                        val newMs = AudioProbe.durationMs(tmp)
                        if (oldMs != null && (newMs == null || newMs < oldMs - 5_000L)) {
                            Logs.add("download", "${track.title.take(24)}: better copy is ${newMs?.div(1000) ?: "?"}s of ${oldMs / 1000}s, keeping ${current.badge}")
                            continue
                        }
                        val tier = ScDataSource.servedTierOf(url, track.urn, header, source.tier)
                        if (tier.rank <= current.rank) continue
                        if (!AudioProbe.inspect(tmp).playable) continue
                        selection.consider(tmp, tier, url)
                        if (tier.rank >= (source.tier ?: Quality.SQ).rank) break
                    }
                }
            } finally {
                verdict.settle()
            }
            val tier = selection.tier
            if (tier == null) {
                if (reached) {
                    Logs.add("download", "${track.title.take(24)}: sources gave ${current.badge} only, keeping it")
                    markNoHq(track.urn)
                } else {
                    Logs.add("download", "${track.title.take(24)}: ${target.badge} sources unavailable")
                }
                return@withContext false
            }
            fallback.copyTo(tmp, overwrite = true)
            if (!awaitNotPlaying(track.urn)) return@withContext false
            val ext = audioExt(tmp)
            val targetFile = File(dir, "$base.$ext")
            java.nio.file.Files.move(tmp.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            audioExts.map { File(dir, "$base.$it") }.filter { it != targetFile }.forEach { it.delete() }
            rememberQuality(track.urn, tier.badge)
            qualityLabels.remove(track.urn)
            Logs.add("download", "${track.title.take(24)}: ${current.badge} → ${tier.badge} $ext @${audioSourceLabel(selection.url)}")
            true
        } catch (e: java.io.IOException) {
            Logs.add("download", "${track.title.take(24)}: quality upgrade failed (${e.javaClass.simpleName})")
            false
        } finally {
            tmp.delete()
            fallback.delete()
            inProgress = inProgress - track.urn
        }
    }

    private suspend fun awaitNotPlaying(urn: String): Boolean {
        if (NowPlaying.urn != urn) return true
        inProgress = inProgress - urn
        var waited = 0
        try {
            while (NowPlaying.urn == urn) {
                if (waited >= 900) return false
                delay(2_000)
                waited += 2
            }
        } finally {
            inProgress = inProgress + urn
        }
        return true
    }

    private val finishedListeners = java.util.concurrent.ConcurrentHashMap<String, (File) -> Unit>()

    suspend fun redownload(context: Context, tracks: List<Track>, onReady: ((String, File) -> Unit)? = null) {
        val list = tracks.filter { !YouTubeImport.isYouTube(it.urn) }.distinctBy { it.urn }
        if (list.isEmpty()) return
        withContext(Dispatchers.IO) {
            for (t in list) ScDataSource.invalidateNow(t.urn)
            removeAll(list.map { it.urn })
            for (t in list) DownloadFailures.clear(t.urn)
        }
        if (Prefs.offline) return
        if (onReady != null) for (t in list) finishedListeners[t.urn] = { f -> onReady(t.urn, f) }
        Logs.add("download", "re-downloading ${list.size} broken ${if (list.size == 1) "track" else "tracks"}")
        enqueue(context.applicationContext, list)
    }

    private val upgradeClaims = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    private fun qualityPrefs() = appContext.getSharedPreferences("downloads_quality", Context.MODE_PRIVATE)

    private fun storedQuality(urn: String): String? = runCatching { qualityPrefs().getString(urn, null) }.getOrNull()

    private fun rememberQuality(urn: String, tier: String) {
        runCatching { qualityPrefs().edit().putString(urn, tier).apply() }
    }

    fun upgradeInBackground(urn: String?) {
        if (urn == null || Prefs.offline || !isDownloaded(urn)) return
        if (!downloadHq) return
        if (YouTubeImport.isYouTube(urn) || recentlyNoHq(urn)) return
        val target = QualityLadder.allowed(QualityLadder.downloadWanted())
        if (Quality.fromLabel(storedQuality(urn)).rank >= target.rank) return
        val track = synchronized(index) { index[urn] } ?: return
        if (!upgradeClaims.add(urn)) return
        App.scope.launch(Dispatchers.IO) { runCatching { upgradeOne(track) } }
    }

    private val jobQueue = ConcurrentLinkedQueue<DownloadJob>()
    private val drainMutex = Mutex()

    var downloaded by mutableStateOf(setOf<String>())
        private set
    var inProgress by mutableStateOf(setOf<String>())
        private set
    var queued by mutableStateOf(setOf<String>())
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        AudioProbe.init(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.downloads), NotificationManager.IMPORTANCE_LOW)
        )
        dir = File(context.filesDir, "tracks").apply { mkdirs() }
        indexFile = File(dir, "index.json")
        runCatching {
            val atomic = android.util.AtomicFile(indexFile)
            if (indexFile.exists() || File(indexFile.path + ".bak").exists()) {
                val tracks = json.decodeFromString(
                    ListSerializer(Track.serializer()),
                    String(atomic.readFully(), Charsets.UTF_8),
                )
                synchronized(index) {
                    for (t in tracks) index[t.urn] = t
                }
            }
        }
        downloaded = synchronized(index) { index.keys.toSet() }
        App.scope.launch {
            val snapshot = synchronized(index) { index.keys.toList() }
            val missing = snapshot.filter { !fileFor(it).exists() }
            if (missing.isEmpty()) return@launch
            synchronized(index) {
                missing.forEach { index.remove(it) }
                saveIndex()
            }
            downloaded = downloaded - missing.toSet()
            Logs.add("download", "index: dropped ${missing.size} missing files")
        }
    }

    private val audioExts = listOf("m4a", "mp3", "ogg", "aac", "webm")

    private fun baseName(urn: String) = urn.replace(Regex("[^A-Za-z0-9._-]"), "_")

    fun fileFor(urn: String): File {
        val base = baseName(urn)
        return audioExts.map { File(dir, "$base.$it") }.firstOrNull { it.exists() }
            ?: File(dir, "$base.m4a")
    }

    private fun audioExt(file: File): String {
        val head = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (n < 12) return "m4a"
        fun at(i: Int, s: String) = s.indices.all { head[i + it] == s[it].code.toByte() }
        return when {
            (head[0].toInt() and 0xFF) == 0x1A && (head[1].toInt() and 0xFF) == 0x45 && (head[2].toInt() and 0xFF) == 0xDF -> "webm"
            at(4, "ftyp") -> "m4a"
            at(0, "OggS") -> "ogg"
            at(0, "ID3") -> "mp3"
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE6) == 0xE2 -> "mp3"
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xF6) == 0xF0 -> "aac"
            else -> "m4a"
        }
    }

    private val downloadHq: Boolean get() = QualityLadder.downloadWanted().rank > Quality.SQ.rank

    private val qualityLabels = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun cachedQuality(urn: String): String? = qualityLabels[urn]

    suspend fun quality(urn: String): String? = withContext(Dispatchers.IO) {
        qualityLabels[urn]?.let { return@withContext it }
        if (!isDownloaded(urn)) return@withContext null
        val file = fileFor(urn)
        if (!file.exists()) return@withContext null
        val tier = storedQuality(urn) ?: "SQ"
        val label = "$tier ${file.extension.uppercase()}"
        qualityLabels[urn] = label
        label
    }

    fun artFor(urn: String): File =
        File(dir, urn.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

    fun artPathOrNull(urn: String): String? =
        artFor(urn).takeIf { it.exists() && it.length() > 0 }?.absolutePath

    private fun truncated(file: File, expectedMs: Long, urn: String, goPlus: Boolean): Boolean {
        val info = AudioProbe.inspect(file)
        if (AudioProbe.shortReason(info, file.length(), expectedMs) != null) return true
        val actual = info.ms ?: 0L
        val snippetLike = actual > 0L && ScDataSource.isSnippetLength(actual)
        if (goPlus && snippetLike) return true
        if (actual <= 0L || expectedMs <= 45_000L) {
            if (actual > 0L) DurationCache.record(urn, actual)
            return false
        }
        val cut = actual < expectedMs - 5_000L
        if (!cut) DurationCache.record(urn, actual)
        return cut
    }

    private val client by lazy {
        AudioRoute.builder(Api.http)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun candidates(track: Track, verdict: QualityLadder.Verdict): Sequence<TierSource> = sequence {
        val gp = track.goPlus
        val target = QualityLadder.downloadWanted()
        yieldAll(TierSources.ladder(track.urn, target, Quality.SQ, gp, verdict))
        if (gp) {
            if (Prefs.star && QualityLadder.upperTiers(QualityLadder.allowed(target)).isEmpty()) {
                yield(TierSource(Api.streamUrl(track.urn, hq = true, goPlus = true), null, false))
            }
            ScDataSource.storageUrlsFor(track.urn).forEach { yield(TierSource(it, null, false)) }
        }
        val current = Endpoints.streamBase
        val primary = Api.streamUrl(track.urn)
        yield(TierSource(primary, null, false))
        for (host in Endpoints.streamHosts) {
            if (host != current) yield(TierSource(primary.replaceFirst(current, host), null, false))
        }
        ScDataSource(Api.http).resolveDownload(primary).urls.forEach { yield(TierSource(it, null, false)) }
        if (!gp) {
            ScAnon.sourceUrl(track.urn)?.let { yield(TierSource(it, null, false)) }
            ScDataSource.storageUrlsFor(track.urn).forEach { yield(TierSource(it, null, false)) }
        }
    }

    private class Fetched(val url: String, val tier: Quality)

    private fun fetchBest(track: Track, tmp: File, verdict: QualityLadder.Verdict, onTruncated: () -> Unit): Fetched? {
        val tried = mutableSetOf<String>()
        val selection = UpgradeSelection(null, File(tmp.path + ".best"))
        fun selected(): Fetched? {
            val tier = selection.tier ?: return null
            selection.file.copyTo(tmp, overwrite = true)
            return Fetched(selection.url, tier)
        }
        try {
            for (source in candidates(track, verdict)) {
                val asked = source.tier ?: Quality.SQ
                if (selection.enoughFor(asked)) return selected()
                val url = source.url
                if (!tried.add(url)) continue
                val storage = ScDataSource.isStorage(url)
                if (storage && ScDataSource.storageCooling(url)) continue
                var header: String? = null
                val report = reporter(verdict, source)
                val ok = runCatching { fetchTo(url, tmp, { header = it }, report, track.displayDuration) }.getOrDefault(false)
                if (ok && !truncated(tmp, track.displayDuration, track.urn, track.goPlus) && AudioProbe.inspect(tmp).playable) {
                    selection.consider(tmp, ScDataSource.servedTierOf(url, track.urn, header, source.tier), url)
                    if (selection.enoughFor(asked)) return selected()
                } else if (ok) onTruncated()
                tmp.delete()
            }
            return selected()
        } finally {
            selection.file.delete()
        }
    }

    private fun usable(file: File, track: Track): Boolean =
        file.exists() && looksLikeAudio(file) &&
            !truncated(file, track.displayDuration, track.urn, track.goPlus) &&
            AudioProbe.inspect(file).playable

    fun looksLikeAudio(file: File): Boolean {
        if (file.length() < 8_192L) return false
        val head = ByteArray(189)
        val n = runCatching { file.inputStream().use { input -> input.readNBytesCompat(head) } }.getOrDefault(-1)
        if (n < 12) return false
        fun at(i: Int, s: String) = s.indices.all { head[i + it] == s[it].code.toByte() }
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return at(4, "ftyp") || at(4, "styp") || at(4, "moof") || at(0, "ID3") || at(0, "OggS") || at(0, "RIFF") || at(0, "fLaC") ||
            (b0 == 0x1A && b1 == 0x45 && (head[2].toInt() and 0xFF) == 0xDF) ||
            (b0 == 0xFF && (b1 and 0xE0) == 0xE0) ||
            (b0 == 0x47 && n >= 189 && (head[188].toInt() and 0xFF) == 0x47)
    }

    private fun java.io.InputStream.readNBytesCompat(buf: ByteArray): Int {
        var total = 0
        while (total < buf.size) {
            val r = read(buf, total, buf.size - total)
            if (r < 0) break
            total += r
        }
        return total
    }

    private val hostDown = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun fetchTo(
        url: String,
        tmp: File,
        onHeader: (String?) -> Unit = {},
        onStatus: (Int, String?) -> Unit = { _, _ -> },
        expectedMs: Long = 0L,
    ): Boolean {
        if (EncHls.isEnc(url)) {
            return runCatching { EncHls.writeTo(client, url, tmp); true }.getOrElse {
                Logs.add("download", "enc-hls ${it.message ?: it.javaClass.simpleName}")
                false
            }
        }
        if (HlsDownload.isHls(url)) {
            val manifest = HlsDownload.manifest(url)
            return runCatching { HlsDownload.writeTo(client, manifest, tmp, expectedMs); true }.getOrElse {
                Logs.add("download", "hls ${it.message ?: it.javaClass.simpleName} @${manifest.substringAfter("://").substringBefore('/')}")
                false
            }
        }
        val host = url.substringAfter("://").substringBefore('/')
        hostDown[host]?.let { if (System.currentTimeMillis() - it < 60_000L) return false }
        val req = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                onStatus(if (res.header("content-length") == "0") -1 else res.code, res.header("retry-after"))
                if (!res.isSuccessful || res.header("content-length") == "0") {
                    Api.noteSoundCloudFailure(res.peekBody(16_384).string(), res.request.header("x-session-id"))
                    Logs.add("download", "HTTP ${res.code} @$host")
                    if (res.code == 401 && url.contains("/stream/")) SessionState.streamDenied()
                    return@use false
                }
                val body = res.body ?: return@use false
                onHeader(res.header("x-audio-quality")?.lowercase())
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                ClearMp4.repair(tmp)
                val ok = looksLikeAudio(tmp)
                if (!ok) Logs.add("download", "not audio (${tmp.length()} B) @$host")
                ok
            }
        } catch (e: java.io.IOException) {
            onStatus(-1, null)
            Logs.add("download", "fail ${e.javaClass.simpleName} @$host")
            if (e is java.net.ConnectException || e is java.net.UnknownHostException ||
                (e is java.net.SocketTimeoutException && tmp.length() == 0L)
            ) {
                hostDown[host] = System.currentTimeMillis()
            }
            false
        }
    }

    private suspend fun downloadArt(track: Track): Boolean = withContext(Dispatchers.IO) {
        val target = artFor(track.urn)
        if (target.exists() && target.length() > 0) return@withContext true
        val raw = Api.artworkUrl(track.artwork_url, "t500x500")
            ?: Api.artworkUrl(track.user?.avatar_url, "t500x500")
            ?: return@withContext false
        val requests = buildList {
            if (raw.contains("sndcdn.com")) {
                val (name, value) = Api.imageProxyTarget(raw)
                Endpoints.imageHosts.forEach { host ->
                    add(Request.Builder().url("$host/?t=${android.net.Uri.encode(value)}").header(name, value).build())
                }
            }
            add(Request.Builder().url(raw).build())
        }
        val tmp = File(target.path + ".part")
        for (req in requests) {
            val ok = runCatching {
                client.newCall(req).execute().use { res ->
                    val body = res.body
                    if (!res.isSuccessful || body == null) return@use false
                    tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(tmp.path, bounds)
                    bounds.outWidth > 0
                }
            }.getOrDefault(false)
            if (ok && tmp.renameTo(target)) return@withContext true
            tmp.delete()
        }
        false
    }

    suspend fun backfillArt() {
        if (Prefs.offline) return
        val missing = tracks().filter { artPathOrNull(it.urn) == null && (it.artwork_url != null || it.user?.avatar_url != null) }
        if (missing.isEmpty()) return
        var got = 0
        for (t in missing) {
            if (Prefs.offline) break
            if (runCatching { downloadArt(t) }.getOrDefault(false)) got++
        }
        Logs.add("download", "offline artwork: $got of ${missing.size}")
    }

    fun isDownloaded(urn: String) = urn in downloaded

    val ready: Boolean get() = ::dir.isInitialized

    fun localFile(urn: String): File? = if (ready && isDownloaded(urn)) fileFor(urn).takeIf { it.exists() } else null

    suspend fun importFrom(track: Track, audio: java.io.InputStream, art: java.io.InputStream?): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(track.urn) || track.urn in inProgress) {
            audio.close()
            art?.close()
            return@withContext isDownloaded(track.urn)
        }
        inProgress = inProgress + track.urn
        val base = baseName(track.urn)
        val tmp = File(dir, "$base.import.part")
        try {
            tmp.delete()
            audio.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            if (!looksLikeAudio(tmp)) {
                tmp.delete()
                return@withContext false
            }
            val target = File(dir, "$base.${audioExt(tmp)}")
            audioExts.forEach { File(dir, "$base.$it").delete() }
            if (!tmp.renameTo(target)) return@withContext false
            art?.use { input -> runCatching { artFor(track.urn).outputStream().use { input.copyTo(it) } } }
            synchronized(index) {
                index[track.urn] = track
                saveIndex()
            }
            downloaded = downloaded + track.urn
            finishedListeners.remove(track.urn)?.let { cb -> runCatching { cb(target) } }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        } finally {
            art?.let { runCatching { it.close() } }
            inProgress = inProgress - track.urn
        }
    }

    fun tracks(): List<Track> = synchronized(index) { index.values.toList() }.reversed()

    suspend fun download(
        track: Track,
        priority: TrackTurns.Priority = TrackTurns.Priority.BACKGROUND,
    ): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(track.urn) || track.urn in inProgress) {
            if (isDownloaded(track.urn)) queued = queued - track.urn
            return@withContext true
        }
        inProgress = inProgress + track.urn
        val base = baseName(track.urn)
        val tmp = File(dir, "$base.part")
        val fromCache = File(dir, "$base.cache.part")
        try {
            tmp.delete()
            fromCache.delete()
            var picked: String? = null
            var quality = ""
            var sawTruncated = false
            ScDataSource.noteStorageQuality(track.urn, track.scdMeta?.storage_quality, overwrite = false)
            if (YouTubeImport.isYouTube(track.urn)) {
                val ok = runCatching { YouTubeImport.downloadTo(track, tmp) }
                    .onFailure { Logs.add("download", "${track.title.take(24)} → YouTube: ${it.message?.take(80)}") }
                    .getOrDefault(false)
                if (ok && looksLikeAudio(tmp)) {
                    picked = "youtube"
                    quality = "YT"
                } else {
                    tmp.delete()
                }
            }
            val viaNetwork = picked == null && !YouTubeImport.isYouTube(track.urn)
            val wantedTier = QualityLadder.allowed(QualityLadder.downloadWanted())
            var cachedTier: Quality? = null
            if (viaNetwork) {
                cachedTier = CachedCopy.extract(track.urn, fromCache)?.takeIf { usable(fromCache, track) }
                if (cachedTier == null) {
                    fromCache.delete()
                } else if (cachedTier.rank >= wantedTier.rank && fromCache.renameTo(tmp)) {
                    picked = "cache://${cachedTier.label}"
                    quality = cachedTier.badge
                    Logs.add("download", "${track.title.take(24)} → pinned from cache (${cachedTier.badge})")
                }
            }
            if (viaNetwork && picked == null) {
                val verdict = QualityLadder.Verdict()
                val upper = QualityLadder.upperTiers(wantedTier).isNotEmpty()
                val found = try {
                    TrackTurns.withTurn(if (upper) priority else TrackTurns.Priority.PLAYBACK) {
                        fetchBest(track, tmp, verdict) { sawTruncated = true }
                    }
                } finally {
                    verdict.settle()
                }
                if (cachedTier != null && (found == null || cachedTier.rank > found.tier.rank)) {
                    java.nio.file.Files.move(fromCache.toPath(), tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    picked = "cache://${cachedTier.label}"
                    quality = cachedTier.badge
                    Logs.add("download", "${track.title.take(24)} → network gave nothing better, pinned cached ${cachedTier.badge}")
                } else if (found != null) {
                    picked = found.url
                    quality = found.tier.badge
                }
            }
            if (picked == null) {
                DownloadFailures.record(track.urn)
                Logs.add(
                    "download",
                    "${track.title.take(24)} → " + (if (sawTruncated) "truncated, not saved" else "download failed") +
                        " (attempt ${DownloadFailures.failures(track.urn)})",
                )
                return@withContext false
            }
            val ext = audioExt(tmp)
            val target = File(dir, "$base.$ext")
            audioExts.forEach { File(dir, "$base.$it").delete() }
            if (!tmp.renameTo(target)) return@withContext false
            rememberQuality(track.urn, quality)
            Logs.add(
                "download",
                "${track.title.take(24)} → $quality $ext ${target.length() / 1024} KB " +
                    "@${audioSourceLabel(picked)}",
            )
            DownloadFailures.clear(track.urn)
            runCatching { downloadArt(track) }
            synchronized(index) {
                index[track.urn] = track
                saveIndex()
            }
            downloaded = downloaded + track.urn
            finishedListeners.remove(track.urn)?.let { cb -> runCatching { cb(target) } }
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        } finally {
            fromCache.delete()
            inProgress = inProgress - track.urn
            queued = queued - track.urn
        }
    }

    fun enqueue(
        context: Context,
        tracks: List<Track>,
        playlistUrn: String? = null,
        playlistTitle: String? = null,
        listKey: String? = playlistUrn,
    ) {
        val pending = tracks.filter { !isDownloaded(it.urn) && it.urn !in queued }
        if (pending.isEmpty()) return
        queued = queued + pending.map { it.urn }
        if (listKey != null) {
            cancelledKeys.remove(listKey)
            activeLists = activeLists + listKey
        }
        jobQueue.add(DownloadJob(pending, playlistUrn, playlistTitle, listKey))
        ContextCompat.startForegroundService(
            context.applicationContext,
            Intent(context.applicationContext, DownloadService::class.java),
        )
    }

    suspend fun drain() = drainMutex.withLock {
        while (true) {
            val job = jobQueue.poll() ?: break
            runJob(job)
        }
    }

    private suspend fun runJob(job: DownloadJob) {
        if (!job.upgrade) queued = queued - job.tracks.filter { isDownloaded(it.urn) }.map { it.urn }.toSet()
        val pending = if (job.upgrade) job.tracks else job.tracks.filter { !isDownloaded(it.urn) }
        val total = pending.size
        if (total == 0) {
            finishList(job)
            return
        }
        val single = total == 1
        var done = 0
        var saved = 0
        var failed = 0
        try {
            notifyProgress(done, total, job.playlistUrn, job.playlistTitle, single)
            for (t in pending) {
                if (job.key != null && job.key in cancelledKeys) break
                val ok = when {
                    job.upgrade -> upgradeOne(t, TrackTurns.Priority.BULK, QualityLadder.upgradeWanted())
                    job.key != null && !single -> download(t, TrackTurns.Priority.BULK)
                    else -> download(t, TrackTurns.Priority.BACKGROUND)
                }
                done++
                if (ok) saved++ else failed++
                notifyProgress(done, total, job.playlistUrn, job.playlistTitle, single)
            }
            notifyComplete(saved, failed, job.upgrade, job.playlistUrn, job.playlistTitle, single)
        } catch (e: kotlinx.coroutines.CancellationException) {
            jobQueue.clear()
            queued = emptySet()
            activeLists = emptySet()
            dismissNotification()
            throw e
        } finally {
            finishList(job)
        }
    }

    var activeLists by mutableStateOf(setOf<String>())
        private set

    private val cancelledKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private fun finishList(job: DownloadJob) {
        val key = job.key ?: return
        if (jobQueue.none { it.key == key }) activeLists = activeLists - key
        cancelledKeys.remove(key)
        queued = queued - job.tracks.map { it.urn }.toSet()
    }

    fun cancelList(context: Context, key: String) {
        val dropped = jobQueue.filter { it.key == key }
        jobQueue.removeAll(dropped.toSet())
        cancelledKeys.add(key)
        activeLists = activeLists - key
        queued = queued - dropped.flatMap { j -> j.tracks.map { it.urn } }.toSet()
        if (jobQueue.isEmpty() && inProgress.isEmpty()) cancelAll(context)
    }

    fun startingNotification(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(context.getString(R.string.downloading))
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setSilent(true)
            .build()

    fun cancelAll(context: Context) {
        jobQueue.clear()
        queued = emptySet()
        activeLists = emptySet()
        runCatching { context.applicationContext.stopService(Intent(context.applicationContext, DownloadService::class.java)) }
        dismissNotification()
    }

    fun dismissNotification() {
        if (::appContext.isInitialized) {
            runCatching { NotificationManagerCompat.from(appContext).cancel(NOTIF_ID) }
        }
    }

    private fun contentIntent(
        playlistUrn: String?,
        playlistTitle: String?,
        single: Boolean,
    ): android.app.PendingIntent {
        val intent = android.content.Intent(appContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            when {
                playlistUrn != null -> {
                    putExtra("open_playlist_urn", playlistUrn)
                    putExtra("open_playlist_title", playlistTitle ?: "")
                }
                single -> putExtra("open_player", true)
            }
        }
        return android.app.PendingIntent.getActivity(
            appContext, 1,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
    }

    suspend fun remove(urn: String): Unit = removeAll(listOf(urn), demote = true)

    suspend fun removeAll(urns: Collection<String>, demote: Boolean = false): Unit = withContext(Dispatchers.IO) {
        if (urns.isEmpty()) return@withContext
        for (urn in urns) {
            if (demote && !YouTubeImport.isYouTube(urn)) {
                val file = fileFor(urn)
                if (file.exists() && looksLikeAudio(file)) {
                    val tier = Quality.fromLabel(storedQuality(urn))
                    if (ScDataSource.adoptSpool(urn, file, tier)) Logs.add("download", "$urn back to the stream cache (${tier.badge})")
                }
            }
            val base = baseName(urn)
            audioExts.forEach { File(dir, "$base.$it").delete() }
            artFor(urn).delete()
        }
        withContext(Dispatchers.Main) { urns.forEach { qualityLabels.remove(it) } }
        runCatching { qualityPrefs().edit().apply { urns.forEach { remove(it) } }.apply() }
        synchronized(index) {
            urns.forEach { index.remove(it) }
            saveIndex()
        }
        downloaded = downloaded - urns.toSet()
    }

    @SuppressLint("MissingPermission")
    private fun notifyProgress(
        done: Int,
        total: Int,
        playlistUrn: String?,
        playlistTitle: String?,
        single: Boolean,
    ) {
        if (!::appContext.isInitialized) return
        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(playlistTitle ?: appContext.getString(R.string.downloading))
            .setContentText("$done / $total")
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(contentIntent(playlistUrn, playlistTitle, single))
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIF_ID, notif) }
    }

    @SuppressLint("MissingPermission")
    private fun notifyComplete(
        saved: Int,
        failed: Int,
        upgrade: Boolean,
        playlistUrn: String?,
        playlistTitle: String?,
        single: Boolean,
    ) {
        if (!::appContext.isInitialized) return
        val ctx = appContext
        val title = when {
            upgrade -> ctx.getString(R.string.upgrade_quality)
            saved == 0 && failed > 0 -> ctx.getString(R.string.download_failed)
            else -> ctx.getString(R.string.download_done)
        }
        val text = when {
            upgrade -> ctx.getString(R.string.upgrade_done_count, saved)
            failed == 0 -> ctx.getString(R.string.download_done_count, saved)
            saved == 0 -> ctx.getString(R.string.download_failed_count, failed)
            else -> ctx.getString(R.string.download_partial_count, saved, failed)
        }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(if (saved == 0 && failed > 0 && !upgrade) R.drawable.ic_clear else R.drawable.ic_check)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(contentIntent(playlistUrn, playlistTitle, single))
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif) }
    }

    private fun saveIndex() {
        val atomic = android.util.AtomicFile(indexFile)
        var out: java.io.FileOutputStream? = null
        try {
            val bytes = json.encodeToString(ListSerializer(Track.serializer()), index.values.toList())
                .toByteArray(Charsets.UTF_8)
            out = atomic.startWrite()
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            out?.let { runCatching { atomic.failWrite(it) } }
            Logs.add("download", "index save failed: ${e.javaClass.simpleName}")
        }
    }
}
