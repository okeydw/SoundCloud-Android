package com.scd.android

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Api {
    const val API_BASE = "https://api.scdinternal.site"
    const val STREAM_BASE = "https://stream.scdinternal.site"
    const val IMAGES_BASE = "https://images.scdinternal.site"

    private const val PREFS = "scd"
    private const val KEY_SESSION = "session_id"

    @Volatile
    var sessionId: String? = null
        private set

    fun loadSession(context: Context) {
        sessionId = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SESSION, null)
    }

    fun storeSession(context: Context, id: String?) {
        sessionId = id
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SESSION, id).apply()
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchTracks(q: String, page: Int = 0, limit: Int = 20): PagedTracks =
        getJson("$API_BASE/tracks?limit=$limit&page=$page&q=${enc(q)}", PagedTracks.serializer())

    suspend fun authLogin(): LoginResponse =
        getJson("$API_BASE/auth/login", LoginResponse.serializer())

    suspend fun authLoginStatus(id: String): LoginStatus =
        getJson("$API_BASE/auth/login/status?id=${enc(id)}", LoginStatus.serializer())

    suspend fun trackByUrn(urn: String): Track =
        getJson("$API_BASE/tracks/${enc(urn)}", Track.serializer())

    suspend fun waveTracks(cursor: String? = null, limit: Int = 20): Pair<List<Track>, String> {
        val url = buildString {
            append("$API_BASE/recommendations/wave?limit=$limit")
            cursor?.takeIf { it.isNotEmpty() }?.let { append("&cursor=${enc(it)}") }
        }
        val payload = getJson(url, WavePayload.serializer())
        val tracks = coroutineScope {
            payload.tracks.map { rec ->
                async {
                    runCatching { trackByUrn("soundcloud:tracks:${rec.id.content}") }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
        return tracks to payload.cursor
    }

    suspend fun history(offset: Int = 0, limit: Int = 50): HistoryPage =
        getJson("$API_BASE/history?limit=$limit&offset=$offset", HistoryPage.serializer())

    suspend fun postHistory(
        urn: String,
        title: String,
        artistName: String,
        artistUrn: String?,
        artworkUrl: String?,
        duration: Long,
    ): Unit = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("scTrackId", urn)
            put("title", title)
            put("artistName", artistName)
            put("artistUrn", artistUrn)
            put("artworkUrl", artworkUrl)
            put("duration", duration)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$API_BASE/history").post(body).apply {
            sessionId?.let { header("x-session-id", it) }
        }.build()
        http.newCall(req).execute().use { }
    }

    fun streamUrl(urn: String, hq: Boolean = false): String {
        val params = buildList {
            if (hq) add("hq=true")
            sessionId?.let { add("session_id=${enc(it)}") }
        }
        val qs = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "$STREAM_BASE/stream/${enc(urn)}$qs"
    }

    fun artworkUrl(raw: String?, size: String = "t500x500"): String? =
        raw?.replace("-large", "-$size")

    fun imageProxyTarget(artworkUrl: String): Pair<String, String> =
        "X-Target" to Base64.encodeToString(artworkUrl.toByteArray(), Base64.NO_WRAP)

    private suspend fun <T> getJson(url: String, strategy: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                sessionId?.let { header("x-session-id", it) }
            }.build()
            http.newCall(req).execute().use { res ->
                val body = res.body?.string() ?: ""
                if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(300))
                json.decodeFromString(strategy, body)
            }
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}

class ApiHttpException(val code: Int, body: String) : IOException("API $code: $body")

@Serializable
data class PagedTracks(
    val collection: List<Track> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Track(
    val id: Long = 0,
    val urn: String,
    val title: String,
    val duration: Long = 0,
    val artwork_url: String? = null,
    val genre: String? = null,
    val user: TrackUser? = null,
)

@Serializable
data class TrackUser(
    val urn: String? = null,
    val username: String = "",
    val avatar_url: String? = null,
)

@Serializable
data class LoginResponse(
    val url: String,
    val loginRequestId: String,
)

@Serializable
data class LoginStatus(
    val status: String,
    val step: String? = null,
    val sessionId: String? = null,
    val username: String? = null,
    val error: String? = null,
)

@Serializable
data class WavePayload(
    val tracks: List<WaveRec> = emptyList(),
    val cursor: String = "",
)

@Serializable
data class WaveRec(
    val id: JsonPrimitive,
    val score: Double? = null,
)

@Serializable
data class HistoryPage(
    val collection: List<HistoryEntry> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class HistoryEntry(
    val id: String = "",
    val scTrackId: String,
    val title: String = "",
    val artistName: String = "",
    val artistUrn: String? = null,
    val artworkUrl: String? = null,
    val duration: Long = 0,
    val playedAt: String = "",
) {
    fun toTrack(): Track = Track(
        urn = scTrackId,
        title = title,
        duration = duration,
        artwork_url = artworkUrl,
        user = TrackUser(urn = artistUrn, username = artistName),
    )
}
