package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AccentPalette {
    val DEFAULT = 0xFFFF5500.toInt()
    val colors = listOf(
        0xFFFF5500.toInt(),
        0xFF0048FF.toInt(),
        0xFF7C4DFF.toInt(),
        0xFF1DB954.toInt(),
        0xFFFF2D55.toInt(),
        0xFFFFB300.toInt(),
    )
}

object CacheLimits {
    const val UNLIMITED = 0L
    val DEFAULT_BYTES = 5L * 1024 * 1024 * 1024
    val sizes = listOf(
        1L * 1024 * 1024 * 1024,
        2L * 1024 * 1024 * 1024,
        5L * 1024 * 1024 * 1024,
        10L * 1024 * 1024 * 1024,
        20L * 1024 * 1024 * 1024,
        UNLIMITED,
    )

    const val FOREVER = 0
    const val DEFAULT_DAYS = 7
    val days = listOf(1, 3, 7, 30, 90, FOREVER)
}

object Prefs {
    private lateinit var sp: SharedPreferences

    var theme by mutableStateOf("system")
        private set
    var offline by mutableStateOf(false)
        private set
    var language by mutableStateOf("system")
        private set
    var immersiveArtwork by mutableStateOf(false)
        private set
    var backgroundImage by mutableStateOf<String?>(null)
        private set
    var backgroundBlur by mutableStateOf(true)
        private set
    var backgroundDim by mutableStateOf(0.6f)
        private set
    var backgroundBlurRadius by mutableStateOf(28f)
        private set
    var textColor by mutableStateOf(0)
        private set
    var headerAlpha by mutableStateOf(1f)
        private set
    var footerAlpha by mutableStateOf(1f)
        private set
    var username by mutableStateOf<String?>(null)
        private set
    var avatarUrl by mutableStateOf<String?>(null)
        private set
    var crossfade by mutableStateOf(false)
        private set
    var playBlocked by mutableStateOf(false)
        private set
    var star by mutableStateOf(false)
        private set
    var hqStreaming by mutableStateOf(true)
        private set
    var streamDebug by mutableStateOf(false)
        private set
    var streamTags by mutableStateOf(false)
        private set
    var accent by mutableStateOf(AccentPalette.DEFAULT)
        private set
    var cacheLimit by mutableStateOf(CacheLimits.DEFAULT_BYTES)
        private set
    var cacheDays by mutableStateOf(CacheLimits.DEFAULT_DAYS)
        private set

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "system") ?: "system"
        offline = sp.getBoolean("offline", false)
        language = sp.getString("language", "system") ?: "system"
        immersiveArtwork = sp.getBoolean("immersive_artwork", false)
        backgroundImage = sp.getString("background_image", null)?.takeIf { java.io.File(it).exists() }
        backgroundBlur = sp.getBoolean("background_blur", true)
        backgroundDim = sp.getFloat("background_dim", 0.6f)
        backgroundBlurRadius = sp.getFloat("background_blur_radius", 28f)
        textColor = sp.getInt("text_color", 0)
        headerAlpha = sp.getFloat("header_alpha", 1f)
        footerAlpha = sp.getFloat("footer_alpha", 1f)
        username = sp.getString("username", null)
        avatarUrl = sp.getString("avatar_url", null)
        crossfade = sp.getBoolean("crossfade", false)
        playBlocked = sp.getBoolean("play_blocked", false)
        star = sp.getBoolean("star", false)
        hqStreaming = sp.getBoolean("hq_streaming", true)
        streamDebug = sp.getBoolean("stream_debug", false)
        streamTags = sp.getBoolean("stream_tags", false)
        accent = sp.getInt("accent", AccentPalette.DEFAULT)
        cacheLimit = sp.getLong("cache_limit", CacheLimits.DEFAULT_BYTES)
        cacheDays = sp.getInt("cache_days", CacheLimits.DEFAULT_DAYS)
    }

    fun changeCacheLimit(value: Long) {
        cacheLimit = value
        sp.edit().putLong("cache_limit", value).apply()
    }

    fun changeCacheDays(value: Int) {
        cacheDays = value
        sp.edit().putInt("cache_days", value).apply()
    }

    fun changeAccent(value: Int) {
        accent = value
        sp.edit().putInt("accent", value).apply()
    }

    fun changeStreamDebug(value: Boolean) {
        streamDebug = value
        sp.edit().putBoolean("stream_debug", value).apply()
    }

    fun changeStreamTags(value: Boolean) {
        streamTags = value
        sp.edit().putBoolean("stream_tags", value).apply()
    }

    fun changeHqStreaming(value: Boolean) {
        if (!star) return
        hqStreaming = value
        sp.edit().putBoolean("hq_streaming", value).apply()
    }

    fun saveStar(value: Boolean) {
        star = value
        sp.edit().putBoolean("star", value).apply()
    }

    fun changePlayBlocked(value: Boolean) {
        playBlocked = value
        sp.edit().putBoolean("play_blocked", value).apply()
    }

    fun changeCrossfade(value: Boolean) {
        crossfade = value
        sp.edit().putBoolean("crossfade", value).apply()
    }

    fun saveUsername(value: String?) {
        username = value
        sp.edit().putString("username", value).apply()
    }

    fun saveAvatar(value: String?) {
        avatarUrl = value
        sp.edit().putString("avatar_url", value).apply()
    }

    fun changeBackgroundImage(path: String?) {
        backgroundImage = path
        sp.edit().putString("background_image", path).apply()
    }

    fun changeBackgroundBlur(value: Boolean) {
        if (value && !BlurSupport.available) return
        backgroundBlur = value
        sp.edit().putBoolean("background_blur", value).apply()
    }

    fun changeBackgroundDim(value: Float) {
        backgroundDim = value
        sp.edit().putFloat("background_dim", value).apply()
    }

    fun changeBackgroundBlurRadius(value: Float) {
        backgroundBlurRadius = value
        sp.edit().putFloat("background_blur_radius", value).apply()
    }

    fun changeHeaderAlpha(value: Float) {
        headerAlpha = value
        sp.edit().putFloat("header_alpha", value).apply()
    }

    fun changeFooterAlpha(value: Float) {
        footerAlpha = value
        sp.edit().putFloat("footer_alpha", value).apply()
    }

    fun changeTextColor(value: Int) {
        textColor = value
        sp.edit().putInt("text_color", value).apply()
    }

    fun changeImmersiveArtwork(value: Boolean) {
        immersiveArtwork = value
        sp.edit().putBoolean("immersive_artwork", value).apply()
    }

    fun changeLanguage(value: String) {
        language = value
        sp.edit().putString("language", value).apply()
    }

    fun setThemeMode(value: String) {
        theme = value
        sp.edit().putString("theme", value).apply()
    }

    fun setOfflineMode(value: Boolean) {
        offline = value
        sp.edit().putBoolean("offline", value).apply()
    }
}
