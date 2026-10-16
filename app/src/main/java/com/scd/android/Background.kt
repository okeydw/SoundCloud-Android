package com.scd.android

import android.content.Context
import android.os.Build
import java.io.File

object BlurSupport {
    val available: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

object BackgroundImage {

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "background")

    fun save(context: Context, bitmap: android.graphics.Bitmap): Boolean = runCatching {
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "bg_${System.currentTimeMillis()}.jpg")
        target.outputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, out)
        }
        if (target.length() <= 0L) {
            target.delete()
            return@runCatching false
        }
        folder.listFiles()?.forEach { if (it != target) it.delete() }
        Prefs.changeBackgroundImage(target.absolutePath)
        true
    }.getOrDefault(false)

    fun saveBytes(context: Context, bytes: ByteArray): Boolean = runCatching {
        if (bytes.isEmpty()) return@runCatching false
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "bg_${System.currentTimeMillis()}.jpg")
        target.writeBytes(bytes)
        folder.listFiles()?.forEach { if (it != target) it.delete() }
        Prefs.changeBackgroundImage(target.absolutePath)
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
        Prefs.changeBackgroundImage(null)
    }
}
