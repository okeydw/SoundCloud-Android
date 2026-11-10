package com.scd.android

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object TrackExport {
    class Outcome(val exported: Int, val skipped: Int, val failed: Int)

    var running by mutableStateOf(false)
        private set

    private const val FOLDER = "SoundCloud"

    suspend fun exportAll(context: Context): Outcome? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        if (running) return Outcome(0, 0, 0)
        running = true
        return try {
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    exportQ(context.applicationContext.contentResolver)
                } else {
                    Outcome(0, 0, 0)
                }
            }
        } finally {
            running = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exportQ(resolver: ContentResolver): Outcome {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relative = Environment.DIRECTORY_MUSIC + "/" + FOLDER
        var exported = 0
        var skipped = 0
        var failed = 0
        for (track in Downloads.tracks()) {
            val file = Downloads.localFile(track.urn) ?: continue
            val artist = track.user?.username.orEmpty()
            val name = plainName(if (artist.isBlank()) track.title else "$artist - ${track.title}", track.urn) +
                "." + file.extension.ifEmpty { "m4a" }
            if (exists(resolver, collection, relative, name)) {
                skipped++
                continue
            }
            if (copyOne(resolver, collection, relative, name, file, track, artist)) exported++ else failed++
        }
        Logs.add("download", "export to Music/$FOLDER: $exported new, $skipped already there, $failed failed")
        return Outcome(exported, skipped, failed)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun copyOne(
        resolver: ContentResolver,
        collection: Uri,
        relative: String,
        name: String,
        file: File,
        track: Track,
        artist: String,
    ): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeOf(file.extension))
            put(MediaStore.Audio.Media.RELATIVE_PATH, relative)
            put(MediaStore.Audio.Media.TITLE, track.title)
            if (artist.isNotBlank()) put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return false
        return try {
            val out = resolver.openOutputStream(uri) ?: throw java.io.IOException("no output stream")
            out.use { sink -> file.inputStream().use { it.copyTo(sink) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            Logs.add("download", "export ${track.title.take(24)}: ${e.javaClass.simpleName}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun exists(resolver: ContentResolver, collection: Uri, relative: String, name: String): Boolean =
        runCatching {
            resolver.query(
                collection,
                arrayOf(MediaStore.Audio.Media._ID),
                "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.RELATIVE_PATH} = ?",
                arrayOf(name, "$relative/"),
                null,
            )?.use { it.moveToFirst() } ?: false
        }.getOrDefault(false)

    private fun plainName(raw: String, urn: String): String {
        val cleaned = raw.map { c -> if (c.isISOControl() || c in "\\/:*?\"<>|") '_' else c }
            .joinToString("")
            .trim()
            .trim('.')
            .take(120)
        return cleaned.ifBlank { urn.substringAfterLast(':') }
    }

    private fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        "webm" -> "audio/webm"
        else -> "audio/mp4"
    }
}
