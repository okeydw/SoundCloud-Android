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

object BackgroundPalette {
    val solids = listOf(
        0xFF14101F.toInt(),
        0xFF0B1A2A.toInt(),
        0xFF0F1F17.toInt(),
        0xFF221012.toInt(),
    )
    val gradients = listOf(
        0xFF1A0F33.toInt() to 0xFF0B2233.toInt(),
        0xFF2A0F1F.toInt() to 0xFF120B26.toInt(),
        0xFF0B2A26.toInt() to 0xFF0A1022.toInt(),
        0xFF2B1A0A.toInt() to 0xFF200B1E.toInt(),
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
    var userUrn by mutableStateOf<String?>(null)
        private set
    var crossfade by mutableStateOf(false)
        private set
    var playBlocked by mutableStateOf(false)
        private set
    var star by mutableStateOf(false)
        private set
    var hqStreaming by mutableStateOf(true)
        private set
    var apiStar by mutableStateOf(true)
        private set
    var anonFallback by mutableStateOf(true)
        private set
    var downloadHq by mutableStateOf(true)
        private set
    var autoCacheLikes by mutableStateOf(false)
        private set
    var eqEnabled by mutableStateOf(false)
        private set
    var eqPreset by mutableStateOf(-1)
        private set
    var eqBands by mutableStateOf("")
        private set
    var streamDebug by mutableStateOf(false)
        private set
    var prefetchWifiOnly by mutableStateOf(false)
        private set
    var streamTags by mutableStateOf(false)
        private set
    var accent by mutableStateOf(AccentPalette.DEFAULT)
        private set
    var accent2 by mutableStateOf(0)
        private set
    var bgColor1 by mutableStateOf(0)
        private set
    var bgColor2 by mutableStateOf(0)
        private set
    var cacheLimit by mutableStateOf(CacheLimits.DEFAULT_BYTES)
        private set
    var cacheDays by mutableStateOf(CacheLimits.DEFAULT_DAYS)
        private set
    var carAutoplay by mutableStateOf(false)
        private set
    var carSource by mutableStateOf(CarAutoplay.LIKED)
        private set
    var carSourceTitle by mutableStateOf("")
        private set
    var carDevices by mutableStateOf<Set<String>>(emptySet())
        private set
    var carShuffle by mutableStateOf(false)
        private set
    var carAutostart by mutableStateOf(true)
        private set
    var carEq by mutableStateOf("")
        private set
    var eqUser by mutableStateOf("")
        private set
    var eqHidden by mutableStateOf<Set<Int>>(emptySet())
        private set
    var crossfadeSeconds by mutableStateOf(3)
        private set
    var launcherIcon by mutableStateOf("")
        private set
    var ymToken by mutableStateOf("")
        private set
    var audioOutput by mutableStateOf("auto")
        private set
    var siblingShare by mutableStateOf(true)
        private set
    var effects by mutableStateOf("auto")
        private set
    var waveFresh by mutableStateOf(true)
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
        userUrn = sp.getString("user_urn", null)
        crossfade = sp.getBoolean("crossfade", false)
        playBlocked = sp.getBoolean("play_blocked", false)
        star = sp.getBoolean("star", false)
        hqStreaming = sp.getBoolean("hq_streaming", true)
        apiStar = sp.getBoolean("api_star", true)
        anonFallback = sp.getBoolean("anon_fallback", true)
        downloadHq = sp.getBoolean("download_hq", true)
        autoCacheLikes = sp.getBoolean("auto_cache_likes", false)
        eqEnabled = sp.getBoolean("eq_enabled", false)
        eqPreset = sp.getInt("eq_preset", -1)
        eqBands = sp.getString("eq_bands", "") ?: ""
        streamDebug = sp.getBoolean("stream_debug", false)
        prefetchWifiOnly = sp.getBoolean("prefetch_wifi_only", false)
        streamTags = sp.getBoolean("stream_tags", false)
        accent = sp.getInt("accent", AccentPalette.DEFAULT)
        accent2 = 0
        bgColor1 = sp.getInt("bg_color1", 0)
        bgColor2 = sp.getInt("bg_color2", 0)
        cacheLimit = sp.getLong("cache_limit", CacheLimits.DEFAULT_BYTES)
        cacheDays = sp.getInt("cache_days", CacheLimits.DEFAULT_DAYS)
        carAutoplay = sp.getBoolean("car_autoplay", false)
        carSource = sp.getString("car_source", CarAutoplay.LIKED) ?: CarAutoplay.LIKED
        carSourceTitle = sp.getString("car_source_title", "") ?: ""
        carDevices = sp.getStringSet("car_devices", emptySet())?.toSet() ?: emptySet()
        carShuffle = sp.getBoolean("car_shuffle", false)
        carAutostart = sp.getBoolean("car_autostart", true)
        carEq = sp.getString("car_eq", "") ?: ""
        eqUser = sp.getString("eq_user", "") ?: ""
        eqHidden = (sp.getString("eq_hidden", "") ?: "").split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
        crossfadeSeconds = sp.getInt("crossfade_seconds", 3).coerceIn(1, 30)
        launcherIcon = sp.getString("launcher_icon", "") ?: ""
        ymToken = sp.getString("ym_token", "") ?: ""
        audioOutput = sp.getString("audio_output", "auto") ?: "auto"
        siblingShare = sp.getBoolean("sibling_share", true)
        effects = sp.getString("effects", "auto") ?: "auto"
        waveFresh = sp.getBoolean("wave_fresh", true)
    }

    fun changeEffects(value: String) {
        effects = value
        sp.edit().putString("effects", value).apply()
    }

    fun changeWaveFresh(value: Boolean) {
        waveFresh = value
        sp.edit().putBoolean("wave_fresh", value).apply()
    }

    fun changeCarAutoplay(value: Boolean) {
        carAutoplay = value
        sp.edit().putBoolean("car_autoplay", value).apply()
    }

    fun changeCarSource(source: String, title: String) {
        carSource = source
        carSourceTitle = title
        sp.edit().putString("car_source", source).putString("car_source_title", title).apply()
    }

    fun changeCarDevices(value: Set<String>) {
        carDevices = value
        sp.edit().putStringSet("car_devices", value).apply()
    }

    fun changeCarEq(value: String) {
        carEq = value
        sp.edit().putString("car_eq", value).apply()
    }

    fun changeEqUser(value: String) {
        eqUser = value
        sp.edit().putString("eq_user", value).apply()
    }

    fun changeEqHidden(value: Set<Int>) {
        eqHidden = value
        sp.edit().putString("eq_hidden", value.sorted().joinToString(",")).apply()
    }

    fun changeCrossfadeSeconds(value: Int) {
        val v = value.coerceIn(1, 30)
        if (v == crossfadeSeconds) return
        crossfadeSeconds = v
        sp.edit().putInt("crossfade_seconds", v).apply()
    }

    fun changeYmToken(value: String) {
        ymToken = value.trim()
        sp.edit().putString("ym_token", ymToken).apply()
    }

    fun changeLauncherIcon(value: String) {
        launcherIcon = value
        sp.edit().putString("launcher_icon", value).apply()
    }

    fun changeAudioOutput(value: String) {
        audioOutput = value
        sp.edit().putString("audio_output", value).apply()
    }

    fun changeSiblingShare(value: Boolean) {
        siblingShare = value
        sp.edit().putBoolean("sibling_share", value).apply()
    }

    fun carEqBackup(): String? = sp.getString("car_eq_backup", null)

    fun setCarEqBackup(value: String?) {
        sp.edit().apply { if (value == null) remove("car_eq_backup") else putString("car_eq_backup", value) }.apply()
    }

    fun changeCarShuffle(value: Boolean) {
        carShuffle = value
        sp.edit().putBoolean("car_shuffle", value).apply()
    }

    fun changeCarAutostart(value: Boolean) {
        carAutostart = value
        sp.edit().putBoolean("car_autostart", value).apply()
    }

    fun changeCacheLimit(value: Long) {
        cacheLimit = value
        sp.edit().putLong("cache_limit", value).apply()
        MediaCache.applyLimit()
    }

    fun changeCacheDays(value: Int) {
        cacheDays = value
        sp.edit().putInt("cache_days", value).apply()
    }

    fun changeAccent(value: Int) {
        accent = value
        accent2 = 0
        sp.edit().putInt("accent", value).putInt("accent2", 0).apply()
    }

    fun changeAccentGradient(first: Int, second: Int) {
        accent = first
        accent2 = second
        sp.edit().putInt("accent", first).putInt("accent2", second).apply()
    }

    fun changeAccentFirst(value: Int) {
        accent = value
        sp.edit().putInt("accent", value).apply()
    }

    fun changeAccentSecond(value: Int) {
        accent2 = value
        sp.edit().putInt("accent2", value).apply()
    }

    fun changeBackgroundColors(first: Int, second: Int) {
        bgColor1 = first
        bgColor2 = second
        sp.edit().putInt("bg_color1", first).putInt("bg_color2", second).apply()
    }

    fun changeStreamDebug(value: Boolean) {
        streamDebug = value
        sp.edit().putBoolean("stream_debug", value).apply()
    }

    fun changePrefetchWifiOnly(value: Boolean) {
        prefetchWifiOnly = value
        sp.edit().putBoolean("prefetch_wifi_only", value).apply()
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

    fun changeDownloadHq(value: Boolean) {
        downloadHq = value
        sp.edit().putBoolean("download_hq", value).apply()
    }

    fun changeAnonFallback(value: Boolean) {
        anonFallback = value
        sp.edit().putBoolean("anon_fallback", value).apply()
    }

    fun changeAutoCacheLikes(value: Boolean) {
        autoCacheLikes = value
        sp.edit().putBoolean("auto_cache_likes", value).apply()
    }

    fun changeEq(enabled: Boolean, preset: Int, bands: String) {
        eqEnabled = enabled
        eqPreset = preset
        eqBands = bands
        sp.edit()
            .putBoolean("eq_enabled", enabled)
            .putInt("eq_preset", preset)
            .putString("eq_bands", bands)
            .apply()
    }

    fun changeApiStar(value: Boolean) {
        apiStar = value
        sp.edit().putBoolean("api_star", value).apply()
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

    fun saveUserUrn(value: String?) {
        userUrn = value
        sp.edit().putString("user_urn", value).apply()
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
