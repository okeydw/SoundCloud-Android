package com.scd.android

import android.content.Context
import android.content.ContextWrapper
import java.util.Locale

object LocaleHelper {
    val languages = listOf("system", "en", "ru", "be", "zh", "de", "fr", "es", "tr", "ko")

    fun wrap(base: Context, lang: String): Context {
        if (lang == "system") return base
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = base.resources.configuration
        config.setLocale(locale)
        return ContextWrapper(base.createConfigurationContext(config))
    }
}
