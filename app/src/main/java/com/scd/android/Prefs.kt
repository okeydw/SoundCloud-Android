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
    var username by mutableStateOf<String?>(null)
        private set
    var crossfade by mutableStateOf(false)
        private set
    var playBlocked by mutableStateOf(false)
        private set
    var star by mutableStateOf(false)
        private set
    var streamDebug by mutableStateOf(false)
        private set
    var streamTags by mutableStateOf(false)
        private set
    var accent by mutableStateOf(AccentPalette.DEFAULT)
        private set

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "system") ?: "system"
        offline = sp.getBoolean("offline", false)
        language = sp.getString("language", "system") ?: "system"
        immersiveArtwork = sp.getBoolean("immersive_artwork", false)
        username = sp.getString("username", null)
        crossfade = sp.getBoolean("crossfade", false)
        playBlocked = sp.getBoolean("play_blocked", false)
        star = sp.getBoolean("star", false)
        streamDebug = sp.getBoolean("stream_debug", false)
        streamTags = sp.getBoolean("stream_tags", false)
        accent = sp.getInt("accent", AccentPalette.DEFAULT)
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
