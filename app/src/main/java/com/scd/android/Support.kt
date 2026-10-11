package com.scd.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object Support {
    const val API_ISSUES = "https://github.com/zxcloli666/SoundCloud-Backend/issues/new"
    const val API_DISCORD = "https://discord.gg/xQcGBP8fGG"
    const val APP_ISSUES = "https://github.com/okeydw/SoundCloud-Android/issues/new"
    const val APP_DISCORD = "https://discord.gg/Au3ebtfYu3"

    @Volatile
    private var profileUrl: String? = null

    private suspend fun profile(): String? {
        profileUrl?.let { return it }
        if (Prefs.userUrn == null || Prefs.offline) return null
        val me = withTimeoutOrNull(4_000) { runCatching { Api.me() }.getOrNull() }
        return me?.permalink_url?.also { profileUrl = it }
    }

    fun reset() {
        profileUrl = null
    }

    suspend fun metrics(context: Context): JsonObject {
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val code = info?.versionCode ?: 0
        val link = profile()
        return buildJsonObject {
            put("app", context.packageName)
            put("edition", if (context.packageName.endsWith(".full")) "full" else "lite")
            put("versionName", info?.versionName ?: "")
            put("versionCode", code)
            put("uid", scIdOf(Prefs.userUrn) ?: "")
            put("username", Prefs.username ?: "")
            put("profile", link ?: "")
            put("star", Prefs.star)
            put("device", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            put("android", "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
            put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            put("language", Prefs.language)
            put("offline", Prefs.offline)
            put("api", Api.API_BASE.removePrefix("https://"))
            put("stream", Endpoints.streamBase.removePrefix("https://"))
            put("node", Endpoints.activeNode)
            put("downloads", Downloads.downloaded.size)
        }
    }

    fun pretty(json: JsonObject): String =
        json.entries.joinToString(",\n", "{\n", "\n}") { (k, v) -> "  \"$k\": $v" }

    private fun open(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        runCatching { cm.setPrimaryClip(ClipData.newPlainText("metrics", text)) }
    }

    suspend fun appIssue(context: Context) {
        val m = pretty(metrics(context))
        val body = "**Что случилось**\n\n\n**Как повторить**\n1. \n\n**Метрика**\n```json\n$m\n```\n"
        val title = "[" + (if (context.packageName.endsWith(".full")) "Full" else "Lite") + "] "
        open(context, "$APP_ISSUES?title=${Uri.encode(title)}&body=${Uri.encode(body)}")
    }

    suspend fun appDiscord(context: Context) {
        copy(context, "```json\n" + pretty(metrics(context)) + "\n```")
        open(context, APP_DISCORD)
    }

    fun apiIssue(context: Context) {
        val body = "**Что случилось**\n\n\n**Эндпоинт / хост**\n${Api.API_BASE.removePrefix("https://")} · ${Endpoints.activeNode}\n\n**Клиент**\nSoundCloud Android ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""}\n"
        open(context, "$API_ISSUES?title=${Uri.encode("[Android] ")}&body=${Uri.encode(body)}")
    }

    fun apiDiscord(context: Context) = open(context, API_DISCORD)
}
