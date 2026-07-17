package com.scd.android

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

object Downloads {
    private lateinit var dir: File
    private lateinit var indexFile: File
    private lateinit var appContext: Context
    private val json = Json { ignoreUnknownKeys = true }
    private val index = LinkedHashMap<String, Track>()

    private const val CHANNEL_ID = "downloads"
    private const val NOTIF_ID = 1001

    var downloaded by mutableStateOf(setOf<String>())
        private set
    var inProgress by mutableStateOf(setOf<String>())
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

    fun fileFor(urn: String): File =
        File(dir, urn.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".m4a")

    fun isDownloaded(urn: String) = urn in downloaded

    fun tracks(): List<Track> = index.values.toList().reversed()

    suspend fun download(track: Track): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(track.urn) || track.urn in inProgress) return@withContext true
        inProgress = inProgress + track.urn
        val target = fileFor(track.urn)
        val tmp = File(target.path + ".part")
        try {
            val req = Request.Builder().url(Api.streamUrl(track.urn)).build()
            Api.http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext false
                val body = res.body ?: return@withContext false
                tmp.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (!tmp.renameTo(target)) return@withContext false
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
        }
    }

    suspend fun downloadBatch(
        tracks: List<Track>,
        playlistUrn: String? = null,
        playlistTitle: String? = null,
    ) {
        val pending = tracks.filter { !isDownloaded(it.urn) }
        val total = pending.size
        if (total == 0) return
        val single = tracks.size == 1
        var done = 0
        try {
            notifyProgress(done, total, playlistUrn, playlistTitle, single)
            for (t in pending) {
                download(t)
                done++
                notifyProgress(done, total, playlistUrn, playlistTitle, single)
            }
            notifyComplete(done, total, playlistUrn, playlistTitle, single)
        } catch (e: kotlinx.coroutines.CancellationException) {
            dismissNotification()
            throw e
        }
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
            action = android.content.Intent.ACTION_MAIN
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
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
        fileFor(urn).delete()
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
