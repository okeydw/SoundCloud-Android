package com.scd.android

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MediaExtractorCompat
import java.io.File

class AudioInfo(
    val readable: Boolean,
    val ms: Long?,
    val playable: Boolean = readable,
    val problem: String? = null,
    val declaredMs: Long? = null,
)

object AudioProbe {

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private class Scan(val ok: Boolean, val ms: Long?, val problem: String?, val declaredMs: Long? = null)

    fun inspect(file: File): AudioInfo {
        if (!file.exists() || file.length() == 0L) return AudioInfo(false, null, false, "empty")
        val exo = exoScan(file)
        if (exo != null && exo.ok && exo.ms != null) return AudioInfo(true, exo.ms, true, declaredMs = exo.declaredMs)
        val ex = MediaExtractor()
        val platform = try {
            ex.setDataSource(file.path)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            if (track == null) {
                Scan(false, null, "no audio track")
            } else {
                ex.selectTrack(track)
                Scan(true, lastSampleMs(ex), null)
            }
        } catch (e: Exception) {
            Scan(false, null, e.javaClass.simpleName)
        } finally {
            runCatching { ex.release() }
        }
        val ms = exo?.ms ?: platform.ms ?: headerMs(file)
        return AudioInfo(
            readable = platform.ok || exo?.ok == true,
            ms = ms,
            playable = exo?.ok ?: platform.ok,
            problem = exo?.problem ?: platform.problem,
            declaredMs = exo?.declaredMs,
        )
    }

    fun durationMs(file: File): Long? = inspect(file).ms

    fun tooSmall(bytes: Long, expectedMs: Long): Boolean =
        bytes > 0L && expectedMs > 45_000L && bytes * 8_000L / expectedMs < 24_000L

    @Suppress("DEPRECATION")
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun exoScan(file: File): Scan? {
        val ctx = appContext ?: return null
        val ex = runCatching { MediaExtractorCompat(ctx) }.getOrNull() ?: return null
        return try {
            ex.setDataSource(Uri.fromFile(file), 0L)
            val track = (0 until ex.trackCount).firstOrNull {
                ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return Scan(false, null, "no audio track")
            val format = ex.getTrackFormat(track)
            val declared = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else null
            ex.selectTrack(track)
            var last = -1L
            while (true) {
                val t = ex.sampleTime
                if (t < 0L) break
                if (t > last) last = t
                if (!ex.advance()) break
            }
            Scan(true, (last / 1000).takeIf { it > 0L }, null, declared?.takeIf { it > 0L })
        } catch (e: Exception) {
            Scan(false, null, describe(e))
        } finally {
            runCatching { ex.release() }
        }
    }

    fun describe(error: Throwable): String {
        val root = generateSequence(error) { it.cause }.take(8).last()
        val at = root.stackTrace.take(2).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
        val msg = root.message?.take(60)?.let { ": $it" }.orEmpty()
        return "${root.javaClass.simpleName}$msg" + if (at.isNotEmpty()) " at $at" else ""
    }

    private fun lastSampleMs(ex: MediaExtractor): Long? {
        var last = -1L
        while (true) {
            val t = ex.sampleTime
            if (t < 0L) break
            if (t > last) last = t
            if (!ex.advance()) break
        }
        return (last / 1000).takeIf { it > 0L }
    }

    private fun headerMs(file: File): Long? = runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(file.path)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0L }
        } finally {
            runCatching { r.release() }
        }
    }.getOrNull()
}
