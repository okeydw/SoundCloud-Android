package com.scd.android

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
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
        if (id == null && ::http.isInitialized) {
            runCatching { http.cache?.evictAll() }
        }
    }

    lateinit var http: OkHttpClient
        private set

    fun initHttp(context: Context) {
        if (::http.isInitialized) return
        val cache = Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024)
        http = OkHttpClient.Builder()
            .cache(cache)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(NetMonitor.offlineInterceptor(context))
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                val host = req.url.host
                val path = req.url.encodedPath
                val maxAge = when {
                    host == "images.scdinternal.site" || host.endsWith("sndcdn.com") -> 604800
                    host == "api.scdinternal.site" && (
                        path.contains("/playlists") ||
                            path.startsWith("/users/") ||
                            path.startsWith("/me/likes") ||
                            path.startsWith("/me/playlists")
                        ) -> 86400
                    host == "api.scdinternal.site" -> 60
                    else -> null
                }
                if (req.method == "GET" && maxAge != null) {
                    res.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=$maxAge")
                        .build()
                } else res
            }
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun searchTracks(q: String, page: Int = 0, limit: Int = 20): PagedTracks =
        getJson("$API_BASE/tracks?limit=$limit&page=$page&q=${enc(q)}", PagedTracks.serializer())

    suspend fun searchPlaylists(q: String, page: Int = 0, limit: Int = 20): PagedPlaylists =
        getJson("$API_BASE/playlists?limit=$limit&page=$page&q=${enc(q)}", PagedPlaylists.serializer())

    suspend fun searchUsers(q: String, page: Int = 0, limit: Int = 20): PagedUsers =
        getJson("$API_BASE/users?limit=$limit&page=$page&q=${enc(q)}", PagedUsers.serializer())

    suspend fun user(urn: String): Artist =
        getJson("$API_BASE/users/${enc(urn)}", Artist.serializer())

    suspend fun userTracks(urn: String, page: Int = 0, limit: Int = 30): PagedTracks =
        getJson("$API_BASE/users/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun userPlaylists(urn: String, page: Int = 0, limit: Int = 30): PagedPlaylists =
        getJson("$API_BASE/users/${enc(urn)}/playlists?limit=$limit&page=$page", PagedPlaylists.serializer())

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

    suspend fun likedTracks(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/me/likes/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    suspend fun likeTrack(track: Track): Unit = withContext(Dispatchers.IO) {
        val body = json.encodeToString(Track.serializer(), track)
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/likes/tracks/${enc(track.urn)}")
            .post(body)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun unlikeTrack(urn: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/likes/tracks/${enc(urn)}")
            .delete()
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun myPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun playlistTracks(urn: String, page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/playlists/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    suspend fun likedPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/likes/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun likePlaylist(urn: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/likes/playlists/${enc(urn)}")
            .post(ByteArray(0).toRequestBody(null))
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun unlikePlaylist(urn: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/likes/playlists/${enc(urn)}")
            .delete()
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun authStatus(): AuthStatus =
        getJson("$API_BASE/auth/status", AuthStatus.serializer())

    suspend fun dislikedIds(): List<String> =
        getJson("$API_BASE/dislikes/ids", DislikeIds.serializer()).ids

    suspend fun dislike(track: Track): Unit = withContext(Dispatchers.IO) {
        val body = json.encodeToString(Track.serializer(), track)
            .toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/dislikes/${enc(track.urn)}")
            .post(body)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun undislike(urn: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/dislikes/${enc(urn)}")
            .delete()
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun vibeSearch(q: String, limit: Int = 20): VibeSearchResponse =
        getJson("$API_BASE/search/vibe?q=${enc(q)}&limit=$limit", VibeSearchResponse.serializer())

    suspend fun createPlaylist(title: String): String? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", "public")
            })
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/playlists")
            .post(payload)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            runCatching { json.decodeFromString(Playlist.serializer(), body).urn }.getOrNull()
        }
    }

    suspend fun deletePlaylist(urn: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API_BASE/playlists/${enc(urn)}")
            .delete()
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun addToPlaylist(playlistUrn: String, trackUrn: String): Unit = withContext(Dispatchers.IO) {
        val payload = buildJsonObject { put("add", trackUrn) }
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/playlists/${enc(playlistUrn)}/tracks")
            .post(payload)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun renamePlaylist(urn: String, title: String): Unit = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject { put("title", title) })
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/playlists/${enc(urn)}")
            .put(payload)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        http.newCall(req).execute().use { it.checkOk() }
    }

    suspend fun waveform(rawUrl: String, bars: Int = 96): List<Float> = withContext(Dispatchers.IO) {
        val url = rawUrl
            .replace(Regex("\\.png(\\?.*)?$"), ".json$1")
            .replaceFirst("http://", "https://")
        val direct = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        }.getOrNull()
        val body = direct ?: runCatching {
            val (n, v) = imageProxyTarget(url)
            http.newCall(
                Request.Builder().url("$IMAGES_BASE/?t=${enc(v)}").header(n, v).build()
            ).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        }.getOrNull() ?: throw IOException("waveform fetch failed")

        val wf = json.decodeFromString(WaveformJson.serializer(), body)
        val samples = wf.samples
        if (samples.isEmpty()) throw IOException("empty waveform")
        val max = samples.max().coerceAtLeast(1)
        (0 until bars).map { i ->
            val start = i * samples.size / bars
            val end = (((i + 1) * samples.size) / bars).coerceAtLeast(start + 1).coerceAtMost(samples.size)
            val avg = samples.subList(start, end).average().toFloat()
            (avg / max).coerceIn(0.08f, 1f)
        }
    }

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

    private fun okhttp3.Response.checkOk() {
        if (!isSuccessful) throw ApiHttpException(code, body?.string()?.take(300) ?: "")
    }

    private suspend fun <T> getJson(url: String, strategy: DeserializationStrategy<T>, fresh: Boolean = false): T =
        withContext(Dispatchers.IO) {
            val req = Request.Builder().url(url).apply {
                sessionId?.let { header("x-session-id", it) }
                if (fresh) cacheControl(CacheControl.FORCE_NETWORK)
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
    val title: String = "",
    val duration: Long = 0,
    val artwork_url: String? = null,
    val waveform_url: String? = null,
    val genre: String? = null,
    val user: TrackUser? = null,
    val access: String? = null,
    @SerialName("_scd_meta") val scdMeta: ScdMeta? = null,
) {
    val isPreview: Boolean get() = access == "preview"

    val unavailable: Boolean
        get() = access == "blocked" ||
            scdMeta?.storage_state == "missing" ||
            scdMeta?.storage_state == "failed" ||
            scdMeta?.storage_state == "too_long"
}

@Serializable
data class ScdMeta(
    val storage_state: String? = null,
    val storage_quality: String? = null,
)

@Serializable
data class TrackUser(
    val urn: String? = null,
    val username: String = "",
    val avatar_url: String? = null,
)

@Serializable
data class PagedUsers(
    val collection: List<Artist> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Artist(
    val urn: String,
    val username: String = "",
    val avatar_url: String? = null,
    val followers_count: Int? = null,
    val track_count: Int? = null,
    val city: String? = null,
    val country_code: String? = null,
)

@Serializable
data class PagedPlaylists(
    val collection: List<Playlist> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
)

@Serializable
data class Playlist(
    val urn: String,
    val title: String = "",
    val artwork_url: String? = null,
    val track_count: Int = 0,
    val user: TrackUser? = null,
    val kind: String? = null,
) {
    val isAlbum: Boolean
        get() = kind == "album" || kind == "ep" || kind == "single" || kind == "compilation"

    fun kindLabelRes(): Int = when (kind) {
        "album" -> R.string.kind_album
        "ep" -> R.string.kind_ep
        "single" -> R.string.kind_single
        "compilation" -> R.string.kind_compilation
        else -> R.string.playlist_label
    }
}

@Serializable
data class AuthStatus(
    val authenticated: Boolean = false,
    val username: String? = null,
)

@Serializable
data class DislikeIds(
    val ids: List<String> = emptyList(),
)

@Serializable
data class VibeSearchResponse(
    val items: List<Track> = emptyList(),
    val status: String? = null,
)

@Serializable
data class WaveformJson(
    val width: Int = 0,
    val height: Int = 140,
    val samples: List<Int> = emptyList(),
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
