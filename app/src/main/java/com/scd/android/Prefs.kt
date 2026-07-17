package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

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

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        theme = sp.getString("theme", "system") ?: "system"
        offline = sp.getBoolean("offline", false)
        language = sp.getString("language", "system") ?: "system"
        immersiveArtwork = sp.getBoolean("immersive_artwork", false)
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
