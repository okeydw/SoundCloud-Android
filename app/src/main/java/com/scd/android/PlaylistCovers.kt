package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import java.io.File

object PlaylistCovers {
    private var sp: SharedPreferences? = null
    private var dir: File? = null
    private val map = mutableStateMapOf<String, String>()

    fun init(context: Context) {
        if (sp != null) return
        val prefs = context.getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)
        sp = prefs
        dir = File(context.filesDir, "covers").apply { mkdirs() }
        prefs.all.forEach { (k, v) ->
            val path = v as? String ?: return@forEach
            if (File(path).exists()) map[k] = path
        }
    }

    fun get(urn: String): File? = map[urn]?.let { File(it) }

    fun has(urn: String): Boolean = map.containsKey(urn)

    fun set(urn: String, bitmap: Bitmap): Boolean = runCatching {
        val folder = dir ?: return@runCatching false
        folder.mkdirs()
        val name = urn.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(80)
        val target = File(folder, "${name}_${System.currentTimeMillis()}.jpg")
        val side = minOf(bitmap.width, bitmap.height)
        val scaled = if (side > 800) Bitmap.createScaledBitmap(bitmap, bitmap.width * 800 / side, bitmap.height * 800 / side, true) else bitmap
        target.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (scaled !== bitmap) scaled.recycle()
        if (target.length() <= 0L) {
            target.delete()
            return@runCatching false
        }
        map[urn]?.let { old -> if (old != target.path) File(old).delete() }
        map[urn] = target.path
        sp?.edit()?.putString(urn, target.path)?.apply()
        true
    }.getOrDefault(false)

    fun importStream(urn: String, input: java.io.InputStream): Boolean = runCatching {
        val folder = dir ?: return@runCatching false
        folder.mkdirs()
        val name = urn.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(80)
        val target = File(folder, "${name}_${System.currentTimeMillis()}.jpg")
        input.use { src -> target.outputStream().use { src.copyTo(it) } }
        if (target.length() <= 0L) {
            target.delete()
            return@runCatching false
        }
        map[urn]?.let { old -> if (old != target.path) File(old).delete() }
        map[urn] = target.path
        sp?.edit()?.putString(urn, target.path)?.apply()
        true
    }.getOrDefault(false)

    fun clear(urn: String) {
        map.remove(urn)?.let { File(it).delete() }
        sp?.edit()?.remove(urn)?.apply()
    }

    fun move(from: String, to: String) {
        val path = map.remove(from) ?: return
        map[to] = path
        sp?.edit()?.remove(from)?.putString(to, path)?.apply()
    }
}
