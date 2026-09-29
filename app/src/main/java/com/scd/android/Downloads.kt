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
            var skipped = 0
            val targets = tracks().filter { t ->
                val q = quality(t.urn) ?: return@filter false
                if (q.startsWith("HQ")) return@filter false
                if (recentlyNoHq(t.urn)) {
                    skipped++
                    return@filter false
                }
                true
            }
            Logs.add(
                "download",
                "улучшение качества: ${targets.size} к проверке" +
                    if (skipped > 0) ", $skipped пропущено (HQ не было на прошлой неделе)" else "",
            )
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

    private suspend fun upgradeOne(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (track.urn in inProgress) return@withContext false
        val old = fileFor(track.urn)
        if (!old.exists()) return@withContext false
        val oldKbps = fileKbps(old) ?: 0
        inProgress = inProgress + track.urn
        val base = baseName(track.urn)
        val tmp = File(dir, "$base.upgrade.part")
        try {
            val hqSources = sequence {
                ScDataSource.storageUrlsFor(track.urn).forEach { yield(it) }
                val hqStream = Api.streamUrl(track.urn, hq = true, goPlus = true)
                yield(hqStream)
                if (!track.goPlus) ScDataSource(Api.http).resolveViaDownload(hqStream)?.let { yield(it) }
            }
            for (url in hqSources) {
                tmp.delete()
                val ok = runCatching { fetchTo(url, tmp) }.getOrDefault(false)
                if (!ok || truncated(tmp, track.displayDuration, track.urn, track.goPlus)) continue
                val newKbps = fileKbps(tmp) ?: 0
                if (newKbps <= oldKbps + 16) continue
                val ext = audioExt(tmp)
                audioExts.forEach { File(dir, "$base.$it").delete() }
                if (!tmp.renameTo(File(dir, "$base.$ext"))) return@withContext false
                qualityLabels.remove(track.urn)
                Logs.add("download", "${track.title.take(24)}: ${oldKbps}k → ${newKbps}k $ext")
                return@withContext true
            }
            Logs.add("download", "${track.title.take(24)}: HQ-версии нет, оставляю ${oldKbps}k")
            markNoHq(track.urn)
            false
        } finally {
            tmp.delete()
            inProgress = inProgress - track.urn
        }
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
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )
        dir = File(context.filesDir, "tracks").apply { mkdirs() }
        indexFile = File(dir, "index.json")
        runCatching {
            if (indexFile.exists()) {
                val tracks = json.decodeFromString(
                    ListSerializer(Track.serializer()),
                    indexFile.readText(),
                )
                for (t in tracks) if (fileFor(t.urn).exists()) index[t.urn] = t
            }
        }
        downloaded = index.keys.toSet()
    }

    private val audioExts = listOf("m4a", "mp3", "ogg", "aac")

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
            at(4, "ftyp") -> "m4a"
            at(0, "OggS") -> "ogg"
            at(0, "ID3") -> "mp3"
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xE6) == 0xE2 -> "mp3"
            (head[0].toInt() and 0xFF) == 0xFF && (head[1].toInt() and 0xF6) == 0xF0 -> "aac"
            else -> "m4a"
        }
    }

    private val downloadHq: Boolean get() = Prefs.star && Prefs.downloadHq

    private val qualityLabels = androidx.compose.runtime.mutableStateMapOf<String, String>()

    fun cachedQuality(urn: String): String? = qualityLabels[urn]

    private fun fileKbps(file: File): Int? = runCatching {
        val r = android.media.MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            val declared = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            val durMs = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            (declared?.takeIf { it > 0 } ?: durMs?.takeIf { it > 0 }?.let { file.length() * 8_000L / it })
                ?.let { (it / 1000).toInt() }
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()

    suspend fun quality(urn: String): String? = withContext(Dispatchers.IO) {
        qualityLabels[urn]?.let { return@withContext it }
        if (!isDownloaded(urn)) return@withContext null
        val file = fileFor(urn)
        if (!file.exists()) return@withContext null
        val kbps = fileKbps(file)
        val tier = when {
            kbps == null -> "?"
            kbps >= 192 -> "HQ"
            else -> "SQ"
        }
        val label = listOfNotNull(tier, kbps?.let { "${it}k" }, file.extension.uppercase()).joinToString(" ")
        qualityLabels[urn] = label
        label
    }

    fun artFor(urn: String): File =
        File(dir, urn.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".jpg")

    fun artPathOrNull(urn: String): String? =
        artFor(urn).takeIf { it.exists() && it.length() > 0 }?.absolutePath

    private fun truncated(file: File, expectedMs: Long, urn: String, goPlus: Boolean): Boolean {
        val actual = runCatching {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.path)
                retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                )?.toLongOrNull() ?: 0L
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(0L)
        val snippetLike = actual > 0L &&
            (kotlin.math.abs(actual - 30_000L) < 1_500L || kotlin.math.abs(actual - 10_000L) < 1_500L)
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
        Api.http.newBuilder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun candidates(track: Track): Sequence<Pair<String, String>> = sequence {
        val gp = track.goPlus
        val hq = downloadHq
        val storageFirst = gp || hq
        if (storageFirst) ScDataSource.storageUrlsFor(track.urn).forEach { yield(it to "HQ") }
        if (hq || (gp && Prefs.star)) {
            val hqStream = Api.streamUrl(track.urn, hq = true, goPlus = true)
            yield(hqStream to "HQ")
            if (!gp) ScDataSource(Api.http).resolveViaDownload(hqStream)?.let { yield(it to "HQ?") }
        }
        val current = Endpoints.streamBase
        val primary = Api.streamUrl(track.urn)
        yield(primary to "SQ")
        for (host in Endpoints.streamHosts) {
            if (host != current) yield(primary.replaceFirst(current, host) to "SQ")
        }
        if (!gp) {
            ScDataSource(Api.http).resolveViaDownload(primary)?.let { yield(it to "SQ") }
            ScAnon.progressiveUrl(track.urn)?.let { yield(it to "SQ") }
            if (!storageFirst) ScDataSource.storageUrlsFor(track.urn).forEach { yield(it to "HQ") }
        }
    }

    fun looksLikeAudio(file: File): Boolean {
        if (file.length() < 8_192L) return false
        val head = ByteArray(12)
        val n = runCatching { file.inputStream().use { it.read(head) } }.getOrDefault(-1)
        if (n < 12) return false
        fun at(i: Int, s: String) = s.indices.all { head[i + it] == s[it].code.toByte() }
        val b0 = head[0].toInt() and 0xFF
        val b1 = head[1].toInt() and 0xFF
        return at(4, "ftyp") || at(0, "ID3") || at(0, "OggS") || at(0, "RIFF") || at(0, "fLaC") ||
            (b0 == 0xFF && (b1 and 0xE0) == 0xE0)
    }

    private val hostDown = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun fetchTo(url: String, tmp: File): Boolean {
        val host = url.substringAfter("://").substringBefore('/')
        hostDown[host]?.let { if (System.currentTimeMillis() - it < 60_000L) return false }
        val req = Api.authorize(Request.Builder().url(url), url)
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
            .build()
        return try {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful || res.header("content-length") == "0") {
                    Logs.add("download", "HTTP ${res.code} @$host")
                    if (res.code == 401 && url.contains("/stream/")) SessionState.streamDenied()
                    return@use false
                }
                val body = res.body ?: return@use false
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
                val ok = looksLikeAudio(tmp)
                if (!ok) Logs.add("download", "not audio (${tmp.length()} B) @$host")
                ok
            }
        } catch (e: java.io.IOException) {
            Logs.add("download", "fail ${e.javaClass.simpleName} @$host")
            if (e is java.net.ConnectException || e is java.net.UnknownHostException ||
                (e is java.net.SocketTimeoutException && tmp.length() == 0L)
            ) {
                hostDown[host] = System.currentTimeMillis()
            }
            false
        }
    }

    private suspend fun downloadArt(track: Track) = withContext(Dispatchers.IO) {
        val raw = Api.artworkUrl(track.artwork_url, "t500x500") ?: return@withContext
        val target = artFor(track.urn)
        if (target.exists() && target.length() > 0) return@withContext
        runCatching {
            val request = if (raw.contains("sndcdn.com")) {
                val (name, value) = Api.imageProxyTarget(raw)
                Request.Builder()
                    .url("${Api.IMAGES_BASE}/?t=${android.net.Uri.encode(value)}")
                    .header(name, value)
                    .build()
            } else {
                Request.Builder().url(raw).build()
            }
            Api.http.newCall(request).execute().use { res ->
                val body = res.body ?: return@use
                if (!res.isSuccessful) return@use
                target.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
        }
        if (target.exists() && target.length() <= 0L) target.delete()
    }

    fun isDownloaded(urn: String) = urn in downloaded

    fun tracks(): List<Track> = index.values.toList().reversed()

    suspend fun download(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(track.urn) || track.urn in inProgress) {
            if (isDownloaded(track.urn)) queued = queued - track.urn
            return@withContext true
        }
        inProgress = inProgress + track.urn
        val base = baseName(track.urn)
        val tmp = File(dir, "$base.part")
        try {
            tmp.delete()
            var picked: String? = null
            var quality = ""
            var sawTruncated = false
            val tried = mutableSetOf<String>()
            for ((url, q) in candidates(track)) {
                if (!tried.add(url)) continue
                val ok = runCatching { fetchTo(url, tmp) }.getOrDefault(false)
                if (ok && !truncated(tmp, track.displayDuration, track.urn, track.goPlus)) {
                    picked = url
                    quality = q
                    break
                }
                if (ok) sawTruncated = true
                tmp.delete()
            }
            if (picked == null) {
                Logs.add(
                    "download",
                    "${track.title.take(24)} → " + if (sawTruncated) "обрезан, не сохраняю" else "не скачался",
                )
                return@withContext false
            }
            val ext = audioExt(tmp)
            val target = File(dir, "$base.$ext")
            audioExts.forEach { File(dir, "$base.$it").delete() }
            if (!tmp.renameTo(target)) return@withContext false
            Logs.add(
                "download",
                "${track.title.take(24)} → $quality $ext ${target.length() / 1024} KB " +
                    "@${picked.substringAfter("://").substringBefore('/')}",
            )
            runCatching { downloadArt(track) }
            synchronized(index) {
                index[track.urn] = track
                saveIndex()
            }
            downloaded = downloaded + track.urn
            true
        } catch (_: Exception) {
            tmp.delete()
            false
        } finally {
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
        try {
            notifyProgress(done, total, job.playlistUrn, job.playlistTitle, single)
            for (t in pending) {
                if (job.key != null && job.key in cancelledKeys) break
                if (job.upgrade) upgradeOne(t) else download(t)
                done++
                notifyProgress(done, total, job.playlistUrn, job.playlistTitle, single)
            }
            notifyComplete(done, total, job.playlistUrn, job.playlistTitle, single)
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

    suspend fun remove(urn: String): Unit = withContext(Dispatchers.IO) {
        val base = baseName(urn)
        audioExts.forEach { File(dir, "$base.$it").delete() }
        qualityLabels.remove(urn)
        artFor(urn).delete()
        synchronized(index) {
            index.remove(urn)
            saveIndex()
        }
        downloaded = downloaded - urn
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
        done: Int,
        total: Int,
        playlistUrn: String?,
        playlistTitle: String?,
        single: Boolean,
    ) {
        if (!::appContext.isInitialized) return
        val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(appContext.getString(R.string.download_done))
            .setContentText(appContext.getString(R.string.download_done_count, done))
            .setOngoing(false)
            .setAutoCancel(true)
            .setSilent(true)
            .setContentIntent(contentIntent(playlistUrn, playlistTitle, single))
            .build()
        runCatching { NotificationManagerCompat.from(appContext).notify(NOTIF_ID, notif) }
    }

    private fun saveIndex() {
        runCatching {
            indexFile.writeText(
                json.encodeToString(ListSerializer(Track.serializer()), index.values.toList())
            )
        }
    }
}
