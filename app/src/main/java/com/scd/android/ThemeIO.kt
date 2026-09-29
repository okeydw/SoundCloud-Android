package com.scd.android

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class ThemePack(
    val version: Int = 1,
    val name: String = "",
    val accent: Int = AccentPalette.DEFAULT,
    val textColor: Int = 0,
    val headerAlpha: Float = 1f,
    val footerAlpha: Float = 1f,
    val backgroundBlur: Boolean = true,
    val backgroundBlurRadius: Float = 28f,
    val backgroundDim: Float = 0.6f,
    val image: String? = null,
)

object ThemeIO {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun current(context: Context): ThemePack {
        val encoded = Prefs.backgroundImage
            ?.let { path -> runCatching { File(path).readBytes() }.getOrNull() }
            ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

        return ThemePack(
            name = "SCD theme",
            accent = Prefs.accent,
            textColor = Prefs.textColor,
            headerAlpha = Prefs.headerAlpha,
            footerAlpha = Prefs.footerAlpha,
            backgroundBlur = Prefs.backgroundBlur,
            backgroundBlurRadius = Prefs.backgroundBlurRadius,
            backgroundDim = Prefs.backgroundDim,
            image = encoded,
        )
    }

    suspend fun export(context: Context, target: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.encodeToString(ThemePack.serializer(), current(context))
            context.contentResolver.openOutputStream(target)?.use { out ->
                out.write(payload.toByteArray())
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
    }

    private const val MAX_FILE_BYTES = 24L * 1024 * 1024

    private fun readLimited(context: Context, source: Uri): ByteArray? {
        context.contentResolver.openInputStream(source)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) return null
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        }
        return null
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0
    }

    private fun opaque(color: Int): Int = color or 0xFF000000.toInt()

    suspend fun import(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val raw = readLimited(context, source) ?: return@runCatching false
            val pack = json.decodeFromString(ThemePack.serializer(), raw.decodeToString())

            val image = pack.image?.takeIf { it.isNotEmpty() }?.let { Base64.decode(it, Base64.DEFAULT) }
            if (image != null && !isImage(image)) return@runCatching false

            Prefs.changeAccent(opaque(pack.accent))
            Prefs.changeTextColor(if (pack.textColor == 0) 0 else opaque(pack.textColor))
            Prefs.changeHeaderAlpha(pack.headerAlpha.coerceIn(0f, 1f))
            Prefs.changeFooterAlpha(pack.footerAlpha.coerceIn(0f, 1f))
            Prefs.changeBackgroundBlur(pack.backgroundBlur)
            Prefs.changeBackgroundBlurRadius(pack.backgroundBlurRadius.coerceIn(0f, 60f))
            Prefs.changeBackgroundDim(pack.backgroundDim.coerceIn(0f, 0.95f))

            if (image == null) {
                BackgroundImage.clear(context)
            } else {
                BackgroundImage.saveBytes(context, image)
            }
            true
        }.getOrDefault(false)
    }
}
