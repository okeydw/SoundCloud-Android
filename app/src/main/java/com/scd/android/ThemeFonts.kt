package com.scd.android

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.material3.Typography
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ThemeFonts {
    const val MAX_BYTES = 8L * 1024 * 1024

    var family by mutableStateOf<FontFamily?>(null)
        private set

    private fun dir(context: Context) = File(context.applicationContext.filesDir, "fonts")

    fun init() {
        load(Prefs.themeFontPath)
    }

    private fun load(path: String?) {
        family = path?.let { p ->
            runCatching { FontFamily(Typeface.createFromFile(p)) }
                .onFailure { Logs.add("theme", "font load failed: ${it.javaClass.simpleName}") }
                .getOrNull()
        }
    }

    fun extensionOf(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = bytes[1].toInt() and 0xFF
        val b2 = bytes[2].toInt() and 0xFF
        val b3 = bytes[3].toInt() and 0xFF
        val tag = String(bytes, 0, 4, Charsets.ISO_8859_1)
        return when {
            b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00 -> "ttf"
            tag == "true" -> "ttf"
            tag == "OTTO" -> "otf"
            tag == "ttcf" -> "ttc"
            else -> null
        }
    }

    fun currentBytes(): ByteArray? = Prefs.themeFontPath?.let { runCatching { File(it).readBytes() }.getOrNull() }

    fun save(context: Context, bytes: ByteArray, name: String): Boolean = runCatching {
        if (bytes.size > MAX_BYTES) return@runCatching false
        val ext = extensionOf(bytes) ?: return@runCatching false
        val folder = dir(context).apply { mkdirs() }
        val target = File(folder, "theme_${System.currentTimeMillis()}.$ext")
        target.writeBytes(bytes)
        val ok = runCatching { Typeface.createFromFile(target) }.isSuccess
        if (!ok) {
            target.delete()
            return@runCatching false
        }
        folder.listFiles()?.forEach { if (it != target) it.delete() }
        val label = name.trim().substringBeforeLast('.').ifBlank { "Custom" }.take(48)
        Prefs.changeThemeFont(target.absolutePath, label)
        load(target.absolutePath)
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { dir(context).deleteRecursively() }
        Prefs.changeThemeFont(null, "")
        family = null
    }

    suspend fun importFile(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(source)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > MAX_BYTES) return@runCatching false
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            } ?: return@runCatching false
            save(context, bytes, displayName(context, source) ?: "Custom")
        }.getOrDefault(false)
    }

    private fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

fun Typography.withThemeFont(family: FontFamily?): Typography {
    if (family == null) return this
    return copy(
        displayLarge = displayLarge.copy(fontFamily = family),
        displayMedium = displayMedium.copy(fontFamily = family),
        displaySmall = displaySmall.copy(fontFamily = family),
        headlineLarge = headlineLarge.copy(fontFamily = family),
        headlineMedium = headlineMedium.copy(fontFamily = family),
        headlineSmall = headlineSmall.copy(fontFamily = family),
        titleLarge = titleLarge.copy(fontFamily = family),
        titleMedium = titleMedium.copy(fontFamily = family),
        titleSmall = titleSmall.copy(fontFamily = family),
        bodyLarge = bodyLarge.copy(fontFamily = family),
        bodyMedium = bodyMedium.copy(fontFamily = family),
        bodySmall = bodySmall.copy(fontFamily = family),
        labelLarge = labelLarge.copy(fontFamily = family),
        labelMedium = labelMedium.copy(fontFamily = family),
        labelSmall = labelSmall.copy(fontFamily = family),
    )
}
