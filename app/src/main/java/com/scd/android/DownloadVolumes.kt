package com.scd.android

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

internal class DownloadVolumes(private val context: Context, val internal: File) {
    private val prefs = context.getSharedPreferences("download_volumes", Context.MODE_PRIVATE)

    var selected: String
        get() = prefs.getString("selected", "").orEmpty()
        set(value) { prefs.edit().putString("selected", value).apply() }

    fun available(): Map<String, String> {
        val manager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull { files ->
            val volume = manager?.getStorageVolume(files) ?: return@mapNotNull null
            if (!volume.isRemovable || volume.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            File(files, "tracks").path to volume.getDescription(context)
        }.toMap()
    }

    fun saved(urn: String): File = prefs.getString("file:$urn", null)?.let(::File) ?: internal

    fun writable(directory: File): Boolean = directory == internal ||
        runCatching { Environment.getExternalStorageState(directory) == Environment.MEDIA_MOUNTED }.getOrDefault(false)

    fun destination(urn: String, existing: Boolean): File? {
        val folder = if (existing) saved(urn) else selected.takeIf { it.isNotEmpty() }?.let(::File) ?: internal
        return folder.takeIf { writable(it) && (it.isDirectory || it.mkdirs()) }
    }

    fun record(urn: String, directory: File) {
        prefs.edit().putString("file:$urn", directory.takeUnless { it == internal }?.path).apply()
    }

    fun commitMove(urn: String, source: File, destination: File): Boolean {
        val previous = saved(urn)
        val saved = prefs.edit().putString("file:$urn", destination.takeUnless { it == internal }?.path)
            .putString("cleanup:$urn", source.path).commit()
        if (!saved) prefs.edit().putString("file:$urn", previous.takeUnless { it == internal }?.path).remove("cleanup:$urn").commit()
        return saved
    }

    fun unfinished(): Set<String> = prefs.all.keys.filter { it.startsWith("cleanup:") }.map { it.removePrefix("cleanup:") }.toSet()

    fun cleanup(urn: String, verify: Boolean = true): Boolean {
        val old = prefs.getString("cleanup:$urn", null)?.let(::File) ?: return true
        val target = File(saved(urn), old.name)
        if (target == old || !writable(old.parentFile ?: return false) || !target.isFile) return false
        if (old.exists() && ((verify && !AudioFileTransfer.identical(old, target)) || !old.delete())) return false
        return prefs.edit().remove("cleanup:$urn").commit()
    }

    fun forget(urn: String) { prefs.edit().remove("file:$urn").apply() }
}
