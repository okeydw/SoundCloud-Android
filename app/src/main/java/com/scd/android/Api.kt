package com.scd.android

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Api {
    val API_BASE get() = if (Endpoints.starApiActive) Endpoints.API_STAR else Endpoints.apiBase
    val CONTROL_BASE get() = Endpoints.apiBase
    val STREAM_BASE get() = Endpoints.streamBase
    val IMAGES_BASE get() = Endpoints.imageBase

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
        ScDataSource.cacheDir = File(context.cacheDir, "goplus").apply { mkdirs() }
        val cache = Cache(File(context.cacheDir, "http"), 300L * 1024 * 1024)
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 96
            maxRequestsPerHost = 24
        }
        http = OkHttpClient.Builder()
            .cache(cache)
            .dispatcher(dispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(NetMonitor.offlineInterceptor(context))
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                val host = req.url.host
                val path = req.url.encodedPath
                val isApi = host in Endpoints.apiHostnames
                val maxAge = when {
                    path.startsWith("/auth") || path.startsWith("/health") ||
                        path.startsWith("/recommendations") -> null
                    host in Endpoints.imageHostnames || host.endsWith("sndcdn.com") -> 604800
                    isApi && (
                        path.contains("/playlists") ||
                            path.startsWith("/users/") ||
                            path.startsWith("/me/likes") ||
                            path.startsWith("/me/playlists")
                        ) -> 604800
                    isApi && (path.startsWith("/tracks") || path.startsWith("/history")) -> 3600
                    isApi -> 60
                    else -> null
                }
                if (req.method == "GET" && maxAge != null && res.code == 200) {
                    res.newBuilder()
                        .removeHeader("Pragma")
                        .header("Cache-Control", "public, max-age=$maxAge")
                        .build()
                } else res
            }
            .build()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val apiClient: OkHttpClient by lazy {
        http.newBuilder()
            .callTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    private val mutateClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private fun neverReachedServer(e: Throwable): Boolean =
        e is java.net.ConnectException || e is java.net.UnknownHostException ||
            e is javax.net.ssl.SSLHandshakeException || e is java.net.NoRouteToHostException

    private suspend fun mutate(method: String, path: String, body: okhttp3.RequestBody?): String =
        withContext(Dispatchers.IO) {
            val bases = if (Endpoints.starApiActive) listOf(Endpoints.API_STAR, Endpoints.apiBase) else listOf(Endpoints.apiBase)
            var last: Exception? = null
            for ((i, base) in bases.withIndex()) {
                val hasNext = i < bases.lastIndex
                val host = base.removePrefix("https://")
                val req = Request.Builder()
                    .url(base + path)
                    .method(method, body)
                    .apply { sessionId?.let { header("x-session-id", it) } }
                    .build()
                try {
                    mutateClient.newCall(req).execute().use { res ->
                        val text = res.body?.string() ?: ""
                        if (res.isSuccessful) return@withContext text
                        Logs.add("api", "$method $path → ${res.code} @$host ${text.take(120).replace('\n', ' ')}")
                        val html = res.header("content-type")?.contains("text/html", ignoreCase = true) == true
                        val infra = res.code in 502..504 && html
                        val starGate = base == Endpoints.API_STAR && res.code == 403
                        if (hasNext && (infra || starGate)) {
                            last = ApiHttpException(res.code, text.take(300))
                            return@use
                        }
                        if (res.code == 401) SessionState.markExpired()
                        throw ApiHttpException(res.code, text.take(300))
                    }
                } catch (e: ApiHttpException) {
                    throw e
                } catch (e: IOException) {
                    Logs.add("api", "$method $path fail ${e.javaClass.simpleName} @$host")
                    last = e
                    if (!hasNext || !neverReachedServer(e)) throw e
                }
            }
            throw last ?: IOException("mutation failed")
        }

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
        getJson("$API_BASE/auth/login/status?id=${enc(id)}", LoginStatus.serializer(), fresh = true)

    suspend fun trackByUrn(urn: String): Track =
        getJson("$API_BASE/tracks/${enc(urn)}", Track.serializer()).also {
            DurationCache.record(it.urn, maxOf(it.duration, it.full_duration ?: 0L))
        }

    @Volatile
    var lastResolvedTrack: Track? = null
        private set

    private fun expandShortLink(url: String): String {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        if (host != "on.soundcloud.com") return url
        return runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            ScDataSource.probeClient(http).newBuilder()
                .cache(null)
                .followRedirects(true)
                .build()
                .newCall(req).execute().use { res -> res.request.url.toString() }
        }.getOrDefault(url).also { Logs.add("link", "short → ${it.substringAfter("://").substringBefore('?')}") }
    }

    suspend fun resolve(url: String): ResolveResult? = withContext(Dispatchers.IO) {
        val clean = shareUrl(expandShortLink(url))
        val bases = listOf(Endpoints.STREAM_STAR, STREAM_BASE) + Endpoints.streamHosts
        for (base in bases.distinct()) {
            val body = runCatching {
                val req = Request.Builder()
                    .url("$base/resolve?url=${enc(clean)}")
                    .cacheControl(CacheControl.FORCE_NETWORK)
                    .build()
                ScDataSource.probeClient(http).newCall(req).execute().use { res ->
                    if (res.isSuccessful) res.body?.string() else {
                        Logs.add("link", "resolve HTTP ${res.code} @${base.removePrefix("https://")}")
                        null
                    }
                }
            }.getOrNull() ?: continue
            val parsed = runCatching { json.decodeFromString(ResolveResult.serializer(), body) }.getOrNull()
                ?.takeIf { it.urn.isNotEmpty() } ?: continue
            if (parsed.kind == "track") {
                lastResolvedTrack = runCatching { json.decodeFromString(Track.serializer(), body) }.getOrNull()
            }
            Logs.add("link", "resolved ${parsed.kind} via ${base.removePrefix("https://")}")
            return@withContext parsed
        }
        runCatching { getJson("$API_BASE/resolve?url=${enc(clean)}", ResolveResult.serializer()) }
            .onFailure { Logs.add("link", "resolve failed: ${it.message?.take(120)}") }
            .getOrNull()
            ?.takeIf { it.urn.isNotEmpty() }
    }

    fun shareUrl(url: String): String {
        val trimmed = url.trim().substringBefore('#')
        val q = trimmed.indexOf('?')
        if (q < 0) return trimmed
        val base = trimmed.substring(0, q)
        val kept = trimmed.substring(q + 1)
            .split('&')
            .filter { part ->
                part.isNotEmpty() &&
                    !part.startsWith("utm_") &&
                    !part.startsWith("si=") &&
                    !part.startsWith("ref=") &&
                    !part.startsWith("in=")
            }
        return if (kept.isEmpty()) base else base + "?" + kept.joinToString("&")
    }

    suspend fun relatedTracks(urn: String, limit: Int = 40, page: Int = 0): PagedTracks =
        getJson("$API_BASE/tracks/${enc(urn)}/related?limit=$limit&page=$page", PagedTracks.serializer())

    @Volatile
    private var waveSeed: String? = null

    private suspend fun waveBatch(
        cursor: String?,
        limit: Int,
        seedTrack: String?,
        hideListened: Boolean?,
    ): Pair<List<Track>, String> {
        val url = buildString {
            if (seedTrack != null) {
                append("$API_BASE/recommendations/wave/from-track/${enc(seedTrack.substringAfterLast(':'))}?limit=$limit")
            } else {
                append("$API_BASE/recommendations/wave?limit=$limit")
            }
            cursor?.takeIf { it.isNotEmpty() }?.let { append("&cursor=${enc(it)}") }
            hideListened?.let { append("&hide_listened=${if (it) 1 else 0}") }
        }
        val payload = getJson(url, WavePayload.serializer(), fresh = true)
        val tracks = coroutineScope {
            payload.tracks.map { rec ->
                async {
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        val id = rec.id.content.removePrefix("soundcloud:tracks:")
                        runCatching { trackByUrn("soundcloud:tracks:$id") }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val label = if (seedTrack != null) "from-track" else "user"
        Logs.add("wave", "$label ids=${payload.tracks.size} → tracks=${tracks.size}" + if (hideListened == false) " (+listened)" else "")
        return tracks to payload.cursor
    }

    private val slowClient: OkHttpClient by lazy {
        http.newBuilder()
            .callTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lyrics(urn: String): LyricsResponse? = withContext(Dispatchers.IO) {
        for (id in listOf(urn, urn.substringAfterLast(':')).distinct()) {
            val req = Request.Builder()
                .url("$API_BASE/lyrics/${enc(id)}")
                .apply { sessionId?.let { header("x-session-id", it) } }
                .build()
            val result = runCatching {
                slowClient.newCall(req).await().use { res ->
                    if (!res.isSuccessful) return@use null
                    json.decodeFromString(LyricsResponse.serializer(), res.body?.string() ?: "")
                }
            }.getOrNull()
            if (result != null) return@withContext result
        }
        null
    }

    suspend fun waveFeedback(cursor: String, negatives: Int, positives: Int): String? = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("cursor", cursor)
            put("negatives", negatives)
            put("positives", positives)
        }.toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/recommendations/wave/feedback")
            .post(payload)
            .apply { sessionId?.let { header("x-session-id", it) } }
            .build()
        apiClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            runCatching {
                json.parseToJsonElement(res.body?.string() ?: "").jsonObject["cursor"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun waveFromTrack(urn: String, limit: Int = 20): List<Track> =
        runCatching { waveBatch(null, limit, urn, null).first }.getOrDefault(emptyList())

    suspend fun waveTracks(cursor: String? = null, limit: Int = 20): Pair<List<Track>, String> {
        if (cursor == LocalRadio.CURSOR) return LocalRadio.batch(limit) to LocalRadio.CURSOR
        if (!cursor.isNullOrEmpty()) {
            val next = waveBatch(cursor, limit, waveSeed, null)
            if (next.first.isNotEmpty()) return next
            Logs.add("wave", "server wave ran out → local radio")
            return LocalRadio.batch(limit) to LocalRadio.CURSOR
        }

        waveSeed = null
        val first = runCatching { waveBatch(null, limit, null, null) }
        first.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Logs.add("wave", "failed: ${it.javaClass.simpleName}: ${it.message?.take(120)} → local radio")
            LocalRadio.resetWave()
            val local = LocalRadio.batch(limit)
            if (local.isNotEmpty()) return local to LocalRadio.CURSOR
            throw it
        }
        first.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let { return it }

        if (System.currentTimeMillis() - SessionState.lastRefreshAt > 10 * 60_000L) {
            val code = refreshSession()
            if (code in 200..299) {
                SessionState.lastRefreshAt = System.currentTimeMillis()
                Logs.add("wave", "empty → session refreshed, retrying")
                runCatching { waveBatch(null, limit, null, null) }.getOrNull()
                    ?.takeIf { it.first.isNotEmpty() }?.let { return it }
            }
        }

        val withListened = runCatching { waveBatch(null, limit, null, false) }
        withListened.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let { return it }

        val seed = Likes.urns.firstOrNull()
        if (seed != null) {
            val fromTrack = runCatching { waveBatch(null, limit, seed, false) }
            fromTrack.getOrNull()?.takeIf { it.first.isNotEmpty() }?.let {
                waveSeed = seed
                return it
            }
        }

        Logs.add("wave", "server wave empty → local radio")
        LocalRadio.resetWave()
        return LocalRadio.batch(limit) to LocalRadio.CURSOR
    }

    suspend fun history(offset: Int = 0, limit: Int = 50): HistoryPage =
        getJson("$API_BASE/history?limit=$limit&offset=$offset", HistoryPage.serializer())

    suspend fun likedTracks(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/me/likes/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    private fun jsonBody(text: String) = text.toRequestBody("application/json".toMediaType())

    private val emptyBody get() = ByteArray(0).toRequestBody(null)

    suspend fun likeTrack(track: Track) {
        mutate("POST", "/likes/tracks/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun unlikeTrack(urn: String) {
        mutate("DELETE", "/likes/tracks/${enc(urn)}", null)
    }

    suspend fun myPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun playlistTracks(urn: String, page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getJson("$API_BASE/playlists/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    suspend fun likedPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getJson("$API_BASE/me/likes/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun likePlaylist(urn: String) {
        mutate("POST", "/likes/playlists/${enc(urn)}", emptyBody)
    }

    suspend fun unlikePlaylist(urn: String) {
        mutate("DELETE", "/likes/playlists/${enc(urn)}", null)
    }

    suspend fun authStatus(): AuthStatus =
        getJson("$API_BASE/auth/session", AuthStatus.serializer(), fresh = true)

    suspend fun me(fresh: Boolean = false): MeProfile =
        getJson("$API_BASE/me", MeProfile.serializer(), fresh)

    suspend fun latestRelease(): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://api.github.com/repos/okeydw/SoundCloud-Android/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            http.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val obj = json.parseToJsonElement(res.body?.string() ?: "").jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val url = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: "https://github.com/okeydw/SoundCloud-Android/releases"
                tag to url
            }
        }.getOrNull()
    }

    suspend fun linkClaim(claimToken: String, pull: Boolean): LinkClaim = withContext(Dispatchers.IO) {
        val sid = sessionId
        if (pull && sid == null) throw ApiHttpException(401, "not signed in")
        val payload = buildJsonObject { put("claimToken", claimToken) }
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$CONTROL_BASE/auth/link/claim")
            .post(payload)
            .apply { if (pull && sid != null) header("x-session-id", sid) }
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        http.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            val claim = json.decodeFromString(LinkClaim.serializer(), body)
            val expected = if (pull) "pull" else "push"
            if (claim.mode.isNotEmpty() && claim.mode != expected) {
                throw ApiHttpException(400, "link mode ${claim.mode}, expected $expected")
            }
            claim
        }
    }

    suspend fun refreshSession(): Int = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext 401
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/auth/refresh")
                .header("x-session-id", sid)
                .post(ByteArray(0).toRequestBody(null))
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            http.newBuilder()
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
                .newCall(req).execute().use { res ->
                    if (!res.isSuccessful) {
                        Logs.add("auth", "refresh HTTP ${res.code}: ${res.body?.string()?.take(120) ?: ""}")
                    }
                    res.code
                }
        }.getOrElse {
            Logs.add("auth", "refresh failed: ${it.javaClass.simpleName}")
            -1
        }
    }

    suspend fun sessionValid(): Boolean? = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/auth/session")
                .header("x-session-id", sid)
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            apiClient.newCall(req).execute().use { res ->
                when {
                    res.code == 401 || res.code == 403 -> false
                    !res.isSuccessful -> null
                    else -> runCatching {
                        json.decodeFromString(AuthStatus.serializer(), res.body?.string() ?: "").authenticated
                    }.getOrNull()
                }
            }
        }.getOrNull()
    }

    suspend fun subscription(): Subscription =
        getJson("$API_BASE/me/subscription", Subscription.serializer())

    suspend fun healthOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/health")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            ScDataSource.probeClient(http).newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun dislikedIds(): List<String> =
        getJson("$API_BASE/dislikes/ids", DislikeIds.serializer()).ids

    suspend fun dislike(track: Track) {
        mutate("POST", "/dislikes/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun undislike(urn: String) {
        mutate("DELETE", "/dislikes/${enc(urn)}", null)
    }

    suspend fun createPlaylist(title: String): String? {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", "public")
            })
        }.toString()
        val body = mutate("POST", "/playlists", jsonBody(payload))
        val urn = runCatching { json.decodeFromString(Playlist.serializer(), body).urn }.getOrNull()
        Logs.add("api", "playlist created: ${urn ?: "no urn in reply"}")
        return urn
    }

    suspend fun deletePlaylist(urn: String) {
        mutate("DELETE", "/playlists/${enc(urn)}", null)
    }

    suspend fun addToPlaylist(playlistUrn: String, trackUrn: String) {
        val payload = buildJsonObject { put("add", trackUrn) }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
        Events.playlistAdd(trackUrn)
    }

    suspend fun sendEvent(userUrn: String, trackUrn: String, type: String, positionPct: Double?) {
        val payload = buildJsonObject {
            put("scUserId", userUrn)
            put("scTrackId", trackUrn)
            put("eventType", type)
            if (positionPct != null) put("positionPct", positionPct)
        }.toString()
        mutate("POST", "/events", jsonBody(payload))
    }

    suspend fun renamePlaylist(urn: String, title: String) {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject { put("title", title) })
        }.toString()
        mutate("PUT", "/playlists/${enc(urn)}", jsonBody(payload))
    }

    suspend fun waveform(rawUrl: String, bars: Int = 96): List<Float> = withContext(Dispatchers.IO) {
        val url = rawUrl
            .replace(Regex("\\.png(\\?.*)?$"), ".json$1")
            .replaceFirst("http://", "https://")
        val client = ScDataSource.probeClient(http)
        val proxied = runCatching {
            val (n, v) = imageProxyTarget(url)
            client.newCall(
                Request.Builder().url("$IMAGES_BASE/?t=${enc(v)}").header(n, v).build()
            ).execute().use { res ->
                if (res.isSuccessful) res.body?.string() else null
            }
        }.getOrNull()
        val body = proxied ?: runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { res ->
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

    fun streamUrl(urn: String, hq: Boolean = false, goPlus: Boolean = false): String {
        val base = if (goPlus && Prefs.star) Endpoints.STREAM_STAR else STREAM_BASE
        val qs = if (hq) "?hq=true" else ""
        return "$base/stream/${enc(urn)}$qs"
    }

    fun authorize(builder: Request.Builder, url: String): Request.Builder {
        val sid = sessionId ?: return builder
        if (!url.contains("/stream/") && !url.contains("/download/")) return builder
        builder.header("x-session-id", sid)
        if (url.contains("session_id=")) return builder
        val sep = if (url.contains('?')) '&' else '?'
        return builder.url("$url${sep}session_id=${enc(sid)}")
    }

    fun parseDownload(body: String): DownloadResponse? =
        runCatching { json.decodeFromString(DownloadResponse.serializer(), body) }.getOrNull()

    suspend fun streamProbe(urn: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val url = streamUrl(urn)
            val req = authorize(Request.Builder().url(url), url)
                .header("Range", "bytes=0-15")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            ScDataSource.probeClient(http).newCall(req).execute().use { res ->
                val ct = res.header("content-type") ?: "?"
                val cl = res.header("content-length") ?: "?"
                val bytes = runCatching { res.peekBody(16L).bytes() }.getOrNull()
                val hex = bytes?.joinToString("") { b -> "%02x".format(b) } ?: ""
                val ascii = bytes?.map { b -> if (b in 32..126) b.toInt().toChar() else '.' }?.joinToString("") ?: ""
                "HTTP ${res.code} @${res.request.url.host} ct=$ct len=$cl\n$hex\n$ascii"
            }
        }.getOrElse { "probe fail: ${it.javaClass.simpleName}: ${it.message}" }
    }

    fun artworkUrl(raw: String?, size: String = "t500x500"): String? =
        raw?.replace("-large", "-$size")

    fun imageProxyTarget(artworkUrl: String): Pair<String, String> =
        "X-Target" to Base64.encodeToString(artworkUrl.toByteArray(), Base64.NO_WRAP)

    private fun okhttp3.Response.checkOk() {
        if (!isSuccessful) throw ApiHttpException(code, body?.string()?.take(300) ?: "")
    }

    private fun pathOf(url: String): String {
        val afterScheme = url.removePrefix("https://").removePrefix("http://")
        val slash = afterScheme.indexOf('/')
        return if (slash >= 0) afterScheme.substring(slash) else "/"
    }

    private suspend fun <T> getJson(url: String, strategy: DeserializationStrategy<T>, fresh: Boolean = false): T =
        withContext(Dispatchers.IO) {
            val path = pathOf(url)
            val hosts = Endpoints.hostsFor(path)
            val firstMain = hosts.firstOrNull { it != Endpoints.API_STAR }
            var lastError: Exception? = null
            for (base in hosts) {
                val isStar = base == Endpoints.API_STAR
                val full = base + path
                try {
                    val req = Request.Builder().url(full).apply {
                        sessionId?.let { header("x-session-id", it) }
                        if (fresh) cacheControl(CacheControl.FORCE_NETWORK)
                    }.build()
                    return@withContext apiClient.newCall(req).await().use { res ->
                        val body = res.body?.string() ?: ""
                        if (!res.isSuccessful) {
                            if (isStar && (res.code == 403 || res.code >= 500)) {
                                throw IOException("star ${res.code}")
                            }
                            if (res.code == 401) SessionState.markExpired()
                            if (res.code in intArrayOf(400, 401, 403, 404)) {
                                throw ApiHttpException(res.code, body.take(300))
                            }
                            throw IOException("API host ${res.code}")
                        }
                        if (!isStar && base != firstMain) {
                            Endpoints.commitApi(Endpoints.apiHosts.indexOf(base))
                        }
                        json.decodeFromString(strategy, body)
                    }
                } catch (e: ApiHttpException) {
                    throw e
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ensureActive()
                    lastError = e
                }
            }
            throw lastError ?: IOException("all API hosts unavailable")
        }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { runCatching { cancel() } }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (cont.isActive) cont.resume(response) else response.close()
            }
        })
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
    val full_duration: Long? = null,
    val artwork_url: String? = null,
    val waveform_url: String? = null,
    val genre: String? = null,
    val permalink_url: String? = null,
    val user: TrackUser? = null,
    val access: String? = null,
    val policy: String? = null,
    val monetization_model: String? = null,
    @SerialName("_scd_meta") val scdMeta: ScdMeta? = null,
) {
    val isPreview: Boolean get() = access == "preview"

    val displayDuration: Long
        get() = maxOf(duration, full_duration ?: 0L, DurationCache.get(urn))

    val goPlus: Boolean
        get() = access == "preview" ||
            policy == "SNIP" ||
            monetization_model == "SUB_HIGH_TIER"

    val starLocked: Boolean get() = access == "preview" && !Prefs.star

    val unavailable: Boolean get() = access == "blocked"
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
data class LinkClaim(
    @SerialName("sessionId") val sessionId: String? = null,
    val mode: String = "",
)

@Serializable
data class MeProfile(
    val username: String = "",
    val avatar_url: String? = null,
    val urn: String? = null,
    val followers_count: Int? = null,
    val followings_count: Int? = null,
    val track_count: Int? = null,
)

@Serializable
data class Subscription(
    val premium: Boolean = false,
)

@Serializable
data class DownloadResponse(
    val track_urn: String = "",
    val candidates: List<DownloadCandidate> = emptyList(),
)

@Serializable
data class DownloadCandidate(
    val kind: String = "",
    val quality: String? = null,
    val preset: String? = null,
    val mime: String? = null,
    val url: String? = null,
    val manifest_url: String? = null,
    val content_type: String? = null,
    val init_base64: String? = null,
    val segments: List<String> = emptyList(),
    val key_base64: String? = null,
)

@Serializable
data class ResolveResult(
    val urn: String = "",
    val kind: String? = null,
    val title: String? = null,
)

@Serializable
data class LyricsResponse(
    val syncedLrc: String? = null,
    val plainText: String? = null,
    val source: String? = null,
    val language: String? = null,
)

data class LyricLine(val timeMs: Long, val text: String)

fun parseLrc(lrc: String): List<LyricLine> {
    val re = Regex("^\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})]\\s*(.*)")
    return lrc.lines().mapNotNull { raw ->
        val m = re.find(raw.trim()) ?: return@mapNotNull null
        val (mm, ss, frac, text) = m.destructured
        if (text.isBlank()) return@mapNotNull null
        val ms = mm.toLong() * 60_000L + ss.toLong() * 1000L + frac.padEnd(3, '0').take(3).toLong()
        LyricLine(ms, text.trim())
    }
}

@Serializable
data class DislikeIds(
    val ids: List<String> = emptyList(),
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
