package com.scd.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

object SpotifyPkce {
    const val HOST = "spotify-auth"
    private const val AUTHORIZE = "https://accounts.spotify.com/authorize"
    private const val TOKEN = "https://accounts.spotify.com/api/token"
    private const val SCOPES = "user-library-read playlist-read-private playlist-read-collaborative"

    private val json = Json { ignoreUnknownKeys = true }
    private val random = SecureRandom()

    private val http by lazy {
        Api.http.newBuilder()
            .cache(null)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    fun redirectUri(context: Context): String = "${context.packageName}://$HOST"

    fun configured(): Boolean = Prefs.spotifyClientId.isNotBlank()

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun randomToken(size: Int): String = b64(ByteArray(size).also { random.nextBytes(it) })

    fun start(context: Context): Boolean {
        val clientId = Prefs.spotifyClientId.trim()
        if (clientId.isEmpty()) return false
        val verifier = randomToken(64)
        val state = randomToken(16)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        Prefs.setSpotifyPkce(verifier, state)
        val url = Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", redirectUri(context))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", state)
            .build()
        val intent = Intent(Intent.ACTION_VIEW, url)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    fun isCallback(context: Context, uri: Uri?): Boolean =
        uri != null && uri.scheme == context.packageName && uri.host == HOST

    suspend fun complete(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val (verifier, state) = Prefs.spotifyPkce()
        Prefs.setSpotifyPkce("", "")
        uri.getQueryParameter("error")?.let {
            Logs.add("import", "spotify login refused: ${it.take(60)}")
            return@withContext false
        }
        val code = uri.getQueryParameter("code")
        if (code.isNullOrEmpty() || verifier.isEmpty() || uri.getQueryParameter("state") != state) {
            Logs.add("import", "spotify login: bad callback")
            return@withContext false
        }
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", redirectUri(context))
            .add("client_id", Prefs.spotifyClientId.trim())
            .add("code_verifier", verifier)
            .build()
        exchange(form).also { if (it) ExternalAccounts.refreshSpotify() }
    }

    suspend fun refresh(): String? = withContext(Dispatchers.IO) {
        val refresh = Prefs.spotifyRefresh
        val clientId = Prefs.spotifyClientId.trim()
        if (refresh.isEmpty() || clientId.isEmpty()) return@withContext null
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", clientId)
            .build()
        if (exchange(form)) Prefs.spotifyTokenOrNull() else null
    }

    suspend fun token(): String? = Prefs.spotifyTokenOrNull() ?: refresh()

    private fun exchange(form: FormBody): Boolean = runCatching {
        val req = Request.Builder().url(TOKEN).post(form).build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                Logs.add("import", "spotify token HTTP ${res.code}: ${body.take(120)}")
                if (body.contains("invalid_client")) ExternalAccounts.rejectClient(Prefs.spotifyClientId)
                return@use false
            }
            val o = json.parseToJsonElement(body).jsonObject
            val access = o["access_token"]?.jsonPrimitive?.contentOrNull ?: return@use false
            val expires = o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
            o["refresh_token"]?.jsonPrimitive?.contentOrNull?.let { Prefs.changeSpotifyRefresh(it) }
            Prefs.changeSpotifyToken(access, System.currentTimeMillis() + expires * 1000L)
            Logs.add("import", "spotify token received (${o["scope"]?.jsonPrimitive?.contentOrNull ?: "no scope"})")
            true
        }
    }.getOrElse {
        Logs.add("import", "spotify token failed: ${it.javaClass.simpleName}")
        false
    }
}
