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
    var offlineManual by mutableStateOf(false)
        private set
    var autoOffline by mutableStateOf(true)
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

    const val QUALITY_AUTO = "auto"
    const val QUALITY_SQ = "sq"
    const val QUALITY_HQ = "hq"
    const val QUALITY_UQ = "uq"
    val streamQualities = listOf(QUALITY_AUTO, QUALITY_SQ, QUALITY_HQ, QUALITY_UQ)

    var streamQuality by mutableStateOf(QUALITY_AUTO)
        private set

    var playbackRate by mutableStateOf(1f)
        private set
    var pitchSemitones by mutableStateOf(0f)
        private set
    var pitchMode by mutableStateOf("auto")
        private set

    var themeFontPath by mutableStateOf<String?>(null)
        private set
    var themeFontName by mutableStateOf("")
        private set
    var apiStar by mutableStateOf(true)
        private set
    var anonFallback by mutableStateOf(true)
        private set
    var downloadQuality by mutableStateOf(QUALITY_AUTO)
        private set
    var autoCacheLikes by mutableStateOf(false)
        private set
    var eqEnabled by mutableStateOf(false)
        private set
    var eqGains by mutableStateOf(EqCurve.FLAT)
        private set
    var eqPresetId by mutableStateOf(EqPresets.FLAT)
        private set
    var eqPreamp by mutableStateOf<Double?>(null)
        private set
    var eqCustom by mutableStateOf("")
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
    var carScenarios by mutableStateOf("")
        private set
    var crossfadeSeconds by mutableStateOf(3)
        private set
    var launcherIcon by mutableStateOf("")
        private set
    var ymToken by mutableStateOf("")
    var scWebToken by mutableStateOf("")
    var spotifyToken by mutableStateOf("")
    var spotifyTokenUntil by mutableStateOf(0L)
    var spotifyLinked by mutableStateOf(false)
    var spotifyClientId by mutableStateOf("")
        private set
    var ytCookies by mutableStateOf("")
        private set
    var spotifyRefresh by mutableStateOf("")
        private set
    var storageFirst by mutableStateOf(true)
    var shuffleOn by mutableStateOf(false)
    var repeatMode by mutableStateOf(0)
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
        offlineManual = sp.getBoolean("offline_manual", offline)
        autoOffline = sp.getBoolean("auto_offline", true)
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
        streamQuality = sp.getString("stream_quality", null)?.takeIf { it in streamQualities }
            ?: if (star && !hqStreaming) QUALITY_SQ else QUALITY_AUTO
        hqStreaming = streamQuality != QUALITY_SQ
        playbackRate = SpeedPitch.clampRate(sp.getFloat("playback_rate", SpeedPitch.RATE_DEFAULT))
        pitchSemitones = SpeedPitch.clampSemitones(sp.getFloat("pitch_semitones", 0f))
        pitchMode = sp.getString("pitch_mode", SpeedPitch.MODE_AUTO)
            ?.takeIf { it == SpeedPitch.MODE_AUTO || it == SpeedPitch.MODE_MANUAL } ?: SpeedPitch.MODE_AUTO
        themeFontPath = sp.getString("theme_font_path", null)?.takeIf { java.io.File(it).isFile }
        themeFontName = if (themeFontPath != null) sp.getString("theme_font_name", "") ?: "" else ""
        apiStar = sp.getBoolean("api_star", true)
        anonFallback = sp.getBoolean("anon_fallback", true)
        downloadQuality = DownloadQuality.restore(sp.getString("download_quality", null), sp.getBoolean("download_hq", true))
        autoCacheLikes = sp.getBoolean("auto_cache_likes", false)
        eqEnabled = sp.getBoolean("eq_enabled", false)
        eqGains = EqCurve.bandGains((sp.getString("eq_gains", "") ?: "").split(',').mapNotNull { it.trim().toDoubleOrNull() }).toList()
        eqPresetId = sp.getString("eq_preset_id", EqPresets.FLAT)?.takeIf { it.isNotBlank() } ?: EqPresets.FLAT
        eqPreamp = sp.getString("eq_preamp", "")?.toDoubleOrNull()?.takeIf { it.isFinite() }
        eqCustom = sp.getString("eq_custom", "") ?: ""
        val legacyEq = if (!sp.contains("eq_gains") && (sp.contains("eq_bands") || sp.contains("eq_user") || sp.contains("eq_preset"))) {
            EqLegacy(sp.getInt("eq_preset", -1), sp.getString("eq_bands", "") ?: "", sp.getString("eq_user", "") ?: "")
        } else {
            null
        }
        streamDebug = sp.getBoolean("stream_debug", false)
        prefetchWifiOnly = sp.getBoolean("prefetch_wifi_only", false)
        streamTags = sp.getBoolean("stream_tags", false)
        accent = sp.getInt("accent", AccentPalette.DEFAULT)
        accent2 = 0
        bgColor1 = sp.getInt("bg_color1", 0)
        bgColor2 = 0
        cacheLimit = sp.getLong("cache_limit", CacheLimits.DEFAULT_BYTES)
        cacheDays = sp.getInt("cache_days", CacheLimits.DEFAULT_DAYS)
        carAutoplay = sp.getBoolean("car_autoplay", false)
        carSource = sp.getString("car_source", CarAutoplay.LIKED) ?: CarAutoplay.LIKED
        carSourceTitle = sp.getString("car_source_title", "") ?: ""
        carDevices = sp.getStringSet("car_devices", emptySet())?.toSet() ?: emptySet()
        carShuffle = sp.getBoolean("car_shuffle", false)
        carAutostart = sp.getBoolean("car_autostart", true)
        carEq = sp.getString("car_eq", "") ?: ""
        carScenarios = sp.getString("car_scenarios", "") ?: ""
        crossfadeSeconds = sp.getInt("crossfade_seconds", 3).coerceIn(1, 30)
        launcherIcon = sp.getString("launcher_icon", "") ?: ""
        ymToken = sp.getString("ym_token", "") ?: ""
        scWebToken = sp.getString("sc_web_token", "") ?: ""
        spotifyToken = sp.getString("sp_token", "") ?: ""
        spotifyTokenUntil = sp.getLong("sp_token_until", 0L)
        spotifyLinked = sp.getBoolean("sp_linked", false)
        spotifyClientId = sp.getString("sp_client_id", "") ?: ""
        ytCookies = sp.getString("yt_cookies", "") ?: ""
        spotifyRefresh = sp.getString("sp_refresh", "") ?: ""
        storageFirst = sp.getBoolean("storage_first", true)
        shuffleOn = sp.getBoolean("shuffle_on", false)
        repeatMode = sp.getInt("repeat_mode", 0)
        audioOutput = sp.getString("audio_output", "auto") ?: "auto"
        siblingShare = sp.getBoolean("sibling_share", true)
        effects = sp.getString("effects", "auto") ?: "auto"
        waveFresh = sp.getBoolean("wave_fresh", true)
        if (legacyEq != null) {
            EqPresets.migrate(legacyEq)
            sp.edit().remove("eq_preset").remove("eq_bands").remove("eq_user").remove("eq_hidden").apply()
        }
        EqLive.publish(eqEnabled, eqGains, eqPreamp)
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

    fun changeCarScenarios(value: String) {
        carScenarios = value
        sp.edit().putString("car_scenarios", value).apply()
    }

    fun changeCarEq(value: String) {
        carEq = value
        sp.edit().putString("car_eq", value).apply()
    }

    fun changeCrossfadeSeconds(value: Int) {
        val v = value.coerceIn(1, 30)
        if (v == crossfadeSeconds) return
        crossfadeSeconds = v
        sp.edit().putInt("crossfade_seconds", v).apply()
    }

    fun changeSpotifyToken(token: String, until: Long) {
        spotifyToken = token
        spotifyTokenUntil = until
        spotifyLinked = token.isNotEmpty()
        sp.edit().putString("sp_token", token).putLong("sp_token_until", until).putBoolean("sp_linked", spotifyLinked).apply()
    }

    fun spotifyTokenOrNull(): String? = spotifyToken.takeIf { it.isNotEmpty() && System.currentTimeMillis() < spotifyTokenUntil - 60_000L }

    fun changeYtCookies(value: String) {
        ytCookies = value.trim()
        sp.edit().putString("yt_cookies", ytCookies).apply()
    }

    fun changeSpotifyClientId(value: String) {
        spotifyClientId = value.trim()
        sp.edit().putString("sp_client_id", spotifyClientId).apply()
    }

    fun changeSpotifyRefresh(value: String) {
        spotifyRefresh = value
        sp.edit().putString("sp_refresh", value).apply()
    }

    fun setSpotifyPkce(verifier: String, state: String) {
        sp.edit().putString("sp_pkce_verifier", verifier).putString("sp_pkce_state", state).apply()
    }

    fun spotifyPkce(): Pair<String, String> =
        (sp.getString("sp_pkce_verifier", "") ?: "") to (sp.getString("sp_pkce_state", "") ?: "")

    fun changeStorageFirst(value: Boolean) {
        storageFirst = value
        sp.edit().putBoolean("storage_first", value).apply()
    }

    fun changeShuffleOn(value: Boolean) {
        if (shuffleOn == value) return
        shuffleOn = value
        sp.edit().putBoolean("shuffle_on", value).apply()
    }

    fun changeRepeatMode(value: Int) {
        if (repeatMode == value) return
        repeatMode = value
        sp.edit().putInt("repeat_mode", value).apply()
    }

    fun changeYmToken(value: String) {
        ymToken = value.trim()
        sp.edit().putString("ym_token", ymToken).apply()
    }

    fun changeScWebToken(value: String) {
        scWebToken = value.trim()
        sp.edit().putString("sc_web_token", scWebToken).apply()
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
        PlayerWidgets.refreshTheme()
    }

    fun changeAccentGradient(first: Int, second: Int) {
        accent = first
        accent2 = second
        sp.edit().putInt("accent", first).putInt("accent2", second).apply()
        PlayerWidgets.refreshTheme()
    }

    fun changeBackgroundColors(first: Int, second: Int) {
        bgColor1 = first
        bgColor2 = 0
        sp.edit().putInt("bg_color1", first).putInt("bg_color2", 0).apply()
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

    fun changePlaybackRate(value: Float) {
        val v = SpeedPitch.clampRate(value)
        if (v == playbackRate) return
        playbackRate = v
        sp.edit().putFloat("playback_rate", v).apply()
    }

    fun changePitchSemitones(value: Float) {
        val v = SpeedPitch.clampSemitones(value)
        if (v == pitchSemitones) return
        pitchSemitones = v
        sp.edit().putFloat("pitch_semitones", v).apply()
    }

    fun changePitchMode(value: String) {
        if (value != SpeedPitch.MODE_AUTO && value != SpeedPitch.MODE_MANUAL) return
        pitchMode = value
        sp.edit().putString("pitch_mode", value).apply()
    }

    fun changeThemeFont(path: String?, name: String) {
        themeFontPath = path
        themeFontName = if (path == null) "" else name
        sp.edit().putString("theme_font_path", path).putString("theme_font_name", themeFontName).apply()
    }

    fun changeStreamQuality(value: String) {
        if (value !in streamQualities) return
        streamQuality = value
        hqStreaming = value != QUALITY_SQ
        sp.edit().putString("stream_quality", value).putBoolean("hq_streaming", hqStreaming).apply()
    }

    fun changeDownloadQuality(value: String) {
        if (value !in streamQualities) return
        downloadQuality = value
        sp.edit().putString("download_quality", value).putBoolean("download_hq", value != QUALITY_SQ).apply()
    }

    fun changeAnonFallback(value: Boolean) {
        anonFallback = value
        sp.edit().putBoolean("anon_fallback", value).apply()
    }

    fun changeAutoCacheLikes(value: Boolean) {
        autoCacheLikes = value
        sp.edit().putBoolean("auto_cache_likes", value).apply()
    }

    fun changeEqEnabled(on: Boolean) {
        eqEnabled = on
        sp.edit().putBoolean("eq_enabled", on).apply()
        EqLive.publish(eqEnabled, eqGains, eqPreamp)
    }

    fun changeEqCurve(gains: List<Double>, presetId: String, preamp: Double?) {
        val g = EqCurve.bandGains(gains).toList()
        val pre = preamp?.takeIf { it.isFinite() }?.coerceIn(EqCurve.PREAMP_MIN, EqCurve.PREAMP_MAX)
        eqGains = g
        eqPresetId = presetId
        eqPreamp = pre
        sp.edit()
            .putString("eq_gains", g.joinToString(","))
            .putString("eq_preset_id", presetId)
            .putString("eq_preamp", pre?.toString() ?: "")
            .apply()
        EqLive.publish(eqEnabled, g, pre)
    }

    fun changeEqCustom(value: String) {
        eqCustom = value
        sp.edit().putString("eq_custom", value).apply()
    }

    fun changeApiStar(value: Boolean) {
        apiStar = value
        sp.edit().putBoolean("api_star", value).apply()
    }

    fun saveStar(value: Boolean) {
        if (star != value) QualityLadder.forgetRefusal()
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
        offlineManual = value
        sp.edit().putBoolean("offline", value).putBoolean("offline_manual", value).apply()
    }

    fun setOfflineAuto(value: Boolean) {
        offline = value
        offlineManual = false
        sp.edit().putBoolean("offline", value).putBoolean("offline_manual", false).apply()
    }

    fun changeAutoOffline(value: Boolean) {
        autoOffline = value
        sp.edit().putBoolean("auto_offline", value).apply()
    }
}
