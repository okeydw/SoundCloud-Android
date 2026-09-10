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

    suspend fun import(context: Context, source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use {
                it.readBytes().decodeToString()
            } ?: return@runCatching false

            val pack = json.decodeFromString(ThemePack.serializer(), text)

            Prefs.changeAccent(pack.accent)
            Prefs.changeTextColor(pack.textColor)
            Prefs.changeHeaderAlpha(pack.headerAlpha.coerceIn(0f, 1f))
            Prefs.changeFooterAlpha(pack.footerAlpha.coerceIn(0f, 1f))
            Prefs.changeBackgroundBlur(pack.backgroundBlur)
            Prefs.changeBackgroundBlurRadius(pack.backgroundBlurRadius.coerceIn(0f, 60f))
            Prefs.changeBackgroundDim(pack.backgroundDim.coerceIn(0f, 0.95f))

            val encoded = pack.image
            if (encoded.isNullOrEmpty()) {
                BackgroundImage.clear(context)
            } else {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BackgroundImage.saveBytes(context, bytes)
            }
            true
        }.getOrDefault(false)
    }
}
