package com.scd.android

object QrLink {
    fun parse(text: String): Pair<String, Boolean>? {
        val uri = runCatching { android.net.Uri.parse(text.trim()) }.getOrNull() ?: return null
        if (!uri.isHierarchical || uri.scheme?.lowercase() != "scd") return null
        val token = runCatching { uri.getQueryParameter("token") ?: uri.getQueryParameter("claim") }.getOrNull()
        if (token.isNullOrBlank() || token.length > 512) return null
        val pull = runCatching { uri.getQueryParameter("mode") }.getOrNull()?.lowercase() == "pull"
        return token to pull
    }

    fun deepLink(token: String, push: Boolean): String =
        "scd://link?token=${android.net.Uri.encode(token)}&mode=${if (push) "push" else "pull"}"
}
