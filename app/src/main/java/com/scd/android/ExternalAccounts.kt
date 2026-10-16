package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ExtAccount(val name: String, val avatar: String? = null)

object ExternalAccounts {
    const val SPOTIFY = "sp"
    const val YOUTUBE = "yt"
    const val YANDEX = "ym"
    const val SOUNDCLOUD = "sc"

    private var sp: SharedPreferences? = null
    private val json = Json { ignoreUnknownKeys = true }

    var spotify by mutableStateOf<ExtAccount?>(null)
        private set
    var youtube by mutableStateOf<ExtAccount?>(null)
        private set
    var yandex by mutableStateOf<ExtAccount?>(null)
        private set
    var soundcloud by mutableStateOf<ExtAccount?>(null)
        private set
    var spotifyRejectedClient by mutableStateOf("")
        private set

    private val http by lazy {
        Api.http.newBuilder()
            .cache(null)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val clientIdShape = Regex("^[0-9a-fA-F]{32}$")

    fun clientIdValid(id: String): Boolean = clientIdShape.matches(id.trim()) && id.trim() != spotifyRejectedClient

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("ext_accounts", Context.MODE_PRIVATE)
        sp = prefs
        spotify = load(SPOTIFY)
        youtube = load(YOUTUBE)
        yandex = load(YANDEX)
        soundcloud = load(SOUNDCLOUD)
        spotifyRejectedClient = prefs.getString("sp_rejected", "") ?: ""
    }

    private fun load(key: String): ExtAccount? {
        val name = sp?.getString("${key}_name", null)?.takeIf { it.isNotBlank() } ?: return null
        return ExtAccount(name, sp?.getString("${key}_avatar", null))
    }

    private fun store(key: String, account: ExtAccount?) {
        when (key) {
            SPOTIFY -> spotify = account
            YOUTUBE -> youtube = account
            YANDEX -> yandex = account
            SOUNDCLOUD -> soundcloud = account
        }
        sp?.edit()
            ?.putString("${key}_name", account?.name)
            ?.putString("${key}_avatar", account?.avatar)
            ?.apply()
    }

    fun forget(key: String) = store(key, null)

    fun rejectClient(id: String) {
        spotifyRejectedClient = id.trim()
        sp?.edit()?.putString("sp_rejected", spotifyRejectedClient)?.apply()
    }

    private fun get(url: String, auth: String, extra: Map<String, String> = emptyMap()): JsonObject? {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", auth)
            .header("Accept", "application/json")
            .apply { extra.forEach { (k, v) -> header(k, v) } }
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 401 || res.code == 403) return null
            if (!res.isSuccessful) throw java.io.IOException("HTTP ${res.code}")
            return json.parseToJsonElement(res.body?.string().orEmpty()) as? JsonObject
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    suspend fun refreshSpotify(): Boolean? = withContext(Dispatchers.IO) {
        if (!Prefs.spotifyLinked) return@withContext false
        val token = runCatching { SpotifyPkce.token() }.getOrNull() ?: Prefs.spotifyToken.takeIf { it.isNotEmpty() } ?: return@withContext null
        val result = runCatching { get("https://api.spotify.com/v1/me", "Bearer $token") }
        val me = result.getOrElse {
            Logs.add("import", "spotify profile: ${it.javaClass.simpleName}")
            return@withContext null
        } ?: return@withContext false
        val name = me.str("display_name") ?: me.str("id") ?: return@withContext null
        val avatar = (me["images"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonObject)?.str("url") }
        store(SPOTIFY, ExtAccount(name, avatar))
        true
    }

    suspend fun refreshYoutube(): Boolean? = withContext(Dispatchers.IO) {
        if (Prefs.ytCookies.isBlank()) return@withContext false
        val account = runCatching { YouTubeImport.client.account() }.getOrElse {
            Logs.add("youtube", "account: ${it.message?.take(80)}")
            return@withContext null
        } ?: return@withContext false
        store(YOUTUBE, ExtAccount(account.name, account.avatar))
        true
    }

    suspend fun checkSoundCloud(token: String = Prefs.scWebToken): Boolean? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        val me = runCatching { get("https://api.soundcloud.com/me", "OAuth ${token.trim()}") }.getOrElse {
            Logs.add("upload", "soundcloud profile: ${it.javaClass.simpleName}")
            return@withContext null
        } ?: return@withContext false
        val name = me.str("username") ?: me.str("permalink") ?: return@withContext null
        store(SOUNDCLOUD, ExtAccount(name, me.str("avatar_url")))
        true
    }

    suspend fun checkYandex(token: String): Boolean? = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        val headers = mapOf("User-Agent" to "Yandex-Music-API", "X-Yandex-Music-Client" to "YandexMusicAndroid/24023621")
        val status = runCatching { get(YandexSource.API + "/account/status", "OAuth ${token.trim()}", headers) }.getOrElse {
            Logs.add("import", "yandex profile: ${it.javaClass.simpleName}")
            return@withContext null
        } ?: return@withContext false
        val account = (status["result"] as? JsonObject)?.get("account") as? JsonObject ?: return@withContext false
        if (account.str("uid") == null) return@withContext false
        val name = account.str("displayName") ?: account.str("login") ?: account.str("fullName") ?: account.str("uid")!!
        store(YANDEX, ExtAccount(name))
        true
    }
}
