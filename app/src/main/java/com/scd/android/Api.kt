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
    val CONTROL_BASE get() = Endpoints.serviceHosts("api").first()
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
                if (Endpoints.trustedHost(req.url.host)) return@addNetworkInterceptor chain.proceed(req)
                val hasSession = req.header("x-session-id") != null || req.url.queryParameter("session_id") != null
                if (!hasSession) return@addNetworkInterceptor chain.proceed(req)
                val cleanUrl = req.url.newBuilder().removeAllQueryParameters("session_id").build()
                chain.proceed(req.newBuilder().url(cleanUrl).removeHeader("x-session-id").build())
            }
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val res = chain.proceed(req)
                ApiContract.cacheResponse(
                    res,
                    isApi = AudioRoute.service(req.url) in setOf("api", "api-star"),
                    isImage = req.url.host in Endpoints.imageHostnames || req.url.host.endsWith(".sndcdn.com"),
                )
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

    private val controlClient: OkHttpClient by lazy {
        AudioRoute.builder(http).cache(null).callTimeout(45, TimeUnit.SECONDS).build()
    }

    internal fun noteSoundCloudFailure(body: String, requestedSession: String?) {
        if (requestedSession != null && requestedSession == sessionId && ApiContract.reconnectRequired(body)) {
            SessionState.requireReconnect()
        }
    }

    internal fun noteAuthFailure(status: Int, body: String, requestedSession: String?) {
        if (requestedSession == null || requestedSession != sessionId) return
        if (ApiContract.reconnectRequired(body)) SessionState.requireReconnect()
        else if (status == 401) SessionState.markExpired()
    }

    private val mutateClient: OkHttpClient by lazy {
        http.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }

    private val uploadClient: OkHttpClient by lazy {
        http.newBuilder()
            .cache(null)
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.MINUTES)
            .readTimeout(5, TimeUnit.MINUTES)
            .callTimeout(60, TimeUnit.MINUTES)
            .build()
    }

    suspend fun uploadTrack(body: okhttp3.RequestBody): String = withContext(Dispatchers.IO) {
        val sid = sessionId
        val bases = Endpoints.hostsFor("/tracks/upload")
        var last: IOException? = null
        for ((i, base) in bases.withIndex()) {
            ensureActive()
            if (sid != sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
            val host = base.removePrefix("https://")
            val req = Request.Builder()
                .url("$base/tracks/upload")
                .post(body)
                .apply { sid?.let { header("x-session-id", it) } }
                .build()
            try {
                uploadClient.newCall(req).await().use { res ->
                    val text = res.body?.string().orEmpty()
                    if (!res.isSuccessful) {
                        if (Endpoints.isStarApi(base) && res.code == 403 && i < bases.lastIndex) {
                            last = ApiHttpException(res.code, text.take(600))
                            return@use
                        }
                        noteAuthFailure(res.code, text, sid)
                        Logs.add("upload", "HTTP ${res.code} @$host ${text.take(120).replace('\n', ' ')}")
                        throw ApiHttpException(res.code, text.take(600))
                    }
                    return@withContext text
                }
            } catch (e: ApiHttpException) {
                throw e
            } catch (e: IOException) {
                Logs.add("upload", "fail ${e.javaClass.simpleName} @$host")
                last = e
                if (i == bases.lastIndex || !neverReachedServer(e)) throw e
            }
        }
        throw last ?: IOException("upload failed")
    }

    fun neverReachedServer(e: Throwable): Boolean =
        e is java.net.ConnectException || e is java.net.UnknownHostException ||
            e is javax.net.ssl.SSLHandshakeException || e is java.net.NoRouteToHostException

    private suspend fun mutate(method: String, path: String, body: okhttp3.RequestBody?): String =
        withContext(Dispatchers.IO) {
            val sid = sessionId
            val bases = Endpoints.hostsFor(path)
            var last: Exception? = null
            for ((i, base) in bases.withIndex()) {
                ensureActive()
                if (sid != sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
                val hasNext = i < bases.lastIndex
                val host = base.removePrefix("https://")
                val req = Request.Builder()
                    .url(base + path)
                    .method(method, body)
                    .apply { sid?.let { header("x-session-id", it) } }
                    .build()
                try {
                    mutateClient.newCall(req).execute().use { res ->
                        val text = res.body?.string() ?: ""
                        if (res.isSuccessful) return@withContext text
                        Logs.add("api", "$method $path → ${res.code} @$host ${text.take(120).replace('\n', ' ')}")
                        val starGate = Endpoints.isStarApi(base) && res.code == 403
                        if (hasNext && starGate) {
                            last = ApiHttpException(res.code, text.take(300))
                            return@use
                        }
                        noteAuthFailure(res.code, text, req.header("x-session-id"))
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

    suspend fun vibeSearch(q: String, limit: Int = 48): VibeSearchResponse =
        getJson("$API_BASE/search/vibe?q=${enc(q)}&limit=$limit", VibeSearchResponse.serializer())

    suspend fun searchTracks(q: String, page: Int = 0, limit: Int = 20): PagedTracks =
        getJson("$API_BASE/tracks?limit=$limit&page=$page&q=${enc(q)}", PagedTracks.serializer())

    suspend fun searchPlaylists(q: String, page: Int = 0, limit: Int = 20): PagedPlaylists =
        getJson("$API_BASE/playlists?limit=$limit&page=$page&q=${enc(q)}", PagedPlaylists.serializer())

    suspend fun searchUsers(q: String, page: Int = 0, limit: Int = 20): PagedUsers =
        getJson("$API_BASE/users?limit=$limit&page=$page&q=${enc(q)}", PagedUsers.serializer())

    suspend fun user(urn: String): Artist =
        getReadyJson("$API_BASE/users/${enc(urn)}", Artist.serializer())

    suspend fun userTracks(urn: String, page: Int = 0, limit: Int = 30): PagedTracks =
        getReadyJson("$API_BASE/users/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun userPlaylists(urn: String, page: Int = 0, limit: Int = 30): PagedPlaylists =
        getReadyJson("$API_BASE/users/${enc(urn)}/playlists?limit=$limit&page=$page", PagedPlaylists.serializer())

    suspend fun authLogin(): LoginResponse =
        getJson("$API_BASE/auth/login", LoginResponse.serializer())

    suspend fun authLoginStatus(id: String): LoginStatus =
        getJson("$API_BASE/auth/login/status?id=${enc(id)}", LoginStatus.serializer(), fresh = true)

    suspend fun playlistInfo(urn: String): Playlist =
        getJson("$API_BASE/playlists/${enc(urn)}", Playlist.serializer())

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

    private val profileTabs = setOf(
        "tracks", "albums", "sets", "reposts", "likes", "popular-tracks", "followers", "following",
        "comments", "spotlight", "playlists", "toptracks",
    )

    fun normalizeLink(url: String): String {
        val scheme = url.substringBefore("://", "https")
        val rest = url.substringAfter("://")
        val host = rest.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
        val query = rest.substringAfter('?', "").let { if (it.isEmpty()) "" else "?$it" }
        val segs = rest.substringAfter('/', "").substringBefore('?').split('/').filter { it.isNotEmpty() }
        val path = if (segs.size == 2 && segs[1].lowercase() in profileTabs) listOf(segs[0]) else segs
        return "$scheme://$host/" + path.joinToString("/") + query
    }

    private fun looksLikeTrack(url: String): Boolean {
        val segs = url.substringAfter("://").substringAfter('/', "").substringBefore('?')
            .split('/').filter { it.isNotEmpty() }
        return segs.size == 2 && segs[1].lowercase() !in profileTabs
    }

    private fun resolveOnStream(clean: String): ResolveResult? {
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
            if (parsed.type == "track") {
                lastResolvedTrack = runCatching { json.decodeFromString(Track.serializer(), body) }.getOrNull()
            }
            Logs.add("link", "resolved ${parsed.type} via ${base.removePrefix("https://")}")
            return parsed
        }
        return null
    }

    suspend fun resolve(url: String): ResolveResult? = withContext(Dispatchers.IO) {
        val clean = normalizeLink(shareUrl(expandShortLink(url)))
        val track = looksLikeTrack(clean)
        if (track) resolveOnStream(clean)?.let { return@withContext it }
        val viaApi = runCatching { getJson("$API_BASE/resolve?url=${enc(clean)}", ResolveResult.serializer()) }
            .onFailure { Logs.add("link", "resolve failed: ${it.message?.take(120)}") }
            .getOrNull()
            ?.takeIf { it.urn.isNotEmpty() }
        if (viaApi != null) {
            Logs.add("link", "resolved ${viaApi.type} via api")
            return@withContext viaApi
        }
        if (!track) resolveOnStream(clean) else null
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

    suspend fun tracksByIds(ids: List<String>): List<Track>? {
        if (ids.isEmpty()) return emptyList()
        val out = LinkedHashMap<String, Track>()
        for (chunk in ids.distinct().chunked(50)) {
            val body = runCatching {
                getJson(API_BASE + ApiContract.batchPath(chunk), kotlinx.serialization.json.JsonElement.serializer(), fresh = true)
            }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                null
            } ?: return null
            val list = when (body) {
                is kotlinx.serialization.json.JsonArray -> body
                is kotlinx.serialization.json.JsonObject -> body["collection"] as? kotlinx.serialization.json.JsonArray
                else -> null
            } ?: return null
            list.forEach { el ->
                runCatching { json.decodeFromJsonElement(Track.serializer(), el) }.getOrNull()?.let {
                    DurationCache.record(it.urn, maxOf(it.duration, it.full_duration ?: 0L))
                    out[it.urn.substringAfterLast(':')] = it
                }
            }
        }
        val result = ids.mapNotNull { out[ApiContract.trackId(it)] }
        if (result.isEmpty()) return null
        if (result.size < ids.size) Logs.add("wave", "batch: ${result.size}/${ids.size} tracks")
        return result
    }

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
        val tracks = hydrateWave(payload)
        val label = if (seedTrack != null) "from-track" else "user"
        Logs.add("wave", "$label ids=${payload.tracks.size} → tracks=${tracks.size}" + if (hideListened == false) " (+listened)" else "")
        return tracks to payload.cursor
    }

    private suspend fun hydrateWave(payload: WavePayload): List<Track> {
        val ids = payload.tracks.map { ApiContract.trackId(it.id.content) }
        val inline = payload.tracks.mapNotNull { it.track }.associateBy { ApiContract.trackId(it.urn) }
        val missing = ids.filterNot { it in inline }
        val fetched = tracksByIds(missing) ?: coroutineScope {
            missing.map { id ->
                async {
                    kotlinx.coroutines.withTimeoutOrNull(20_000) {
                        runCatching { trackByUrn("soundcloud:tracks:$id") }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val byId = inline + fetched.associateBy { ApiContract.trackId(it.urn) }
        return ids.mapNotNull { byId[it] }.onEach { DurationCache.record(it.urn, maxOf(it.duration, it.full_duration ?: 0)) }
    }

    private val slowClient: OkHttpClient by lazy {
        AudioRoute.builder(http)
            .callTimeout(180, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    suspend fun lyrics(urn: String): LyricsResponse? = withContext(Dispatchers.IO) {
        val sid = sessionId
        val req = Request.Builder().url("$API_BASE/lyrics/${enc(urn)}")
            .apply { sid?.let { header("x-session-id", it) } }
            .cacheControl(CacheControl.FORCE_NETWORK).build()
        slowClient.newCall(req).await().use { res ->
            val body = res.body?.string().orEmpty()
            if (res.code == 404) return@use null
            if (!res.isSuccessful) {
                noteAuthFailure(res.code, body, sid)
                throw ApiHttpException(res.code, body.take(2000))
            }
            json.decodeFromString(LyricsResponse.serializer(), body)
        }
    }

    suspend fun recommendations(hideListened: Boolean): kotlinx.serialization.json.JsonElement =
        getJson(
            "$API_BASE/recommendations?hide_listened=${if (hideListened) 1 else 0}",
            kotlinx.serialization.json.JsonElement.serializer(),
            fresh = true,
        )

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
        controlClient.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return@use null
            runCatching {
                json.parseToJsonElement(res.body?.string() ?: "").jsonObject["cursor"]
                    ?.jsonPrimitive?.contentOrNull
            }.getOrNull()?.takeIf { it.isNotEmpty() }
        }
    }

    suspend fun waveFromArtist(artistUrn: String, limit: Int = 30): List<Track> = runCatching {
        val id = enc(artistUrn.substringAfterLast(':'))
        val payload = getJson("$API_BASE/recommendations/wave/from-artist/$id?limit=$limit", WavePayload.serializer(), fresh = true)
        hydrateWave(payload)
    }.getOrDefault(emptyList())

    suspend fun waveFromTrack(urn: String, limit: Int = 20, budgetMs: Long = 15_000): List<Track> {
        val res = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(budgetMs) { waveBatch(null, limit, urn, null).first }
        }
        res.exceptionOrNull()?.let {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Logs.add("wave", "from-track failed: ${it.javaClass.simpleName} ${it.message?.take(80) ?: ""}")
        }
        val list = res.getOrNull()
        if (res.isSuccess && list == null) Logs.add("wave", "from-track: no answer in ${budgetMs / 1000}s")
        return list ?: emptyList()
    }

    suspend fun waveTracks(cursor: String? = null, limit: Int = 20): Pair<List<Track>, String> {
        if (cursor == LocalRadio.CURSOR) return LocalRadio.batch(limit) to LocalRadio.CURSOR
        if (!cursor.isNullOrEmpty()) {
            val next = waveBatch(cursor, limit, waveSeed, null)
            if (next.first.isNotEmpty()) return next
            Logs.add("wave", "server wave ran out → local radio")
            return LocalRadio.batch(limit) to LocalRadio.CURSOR
        }

        waveSeed = null
        val fresh = if (Prefs.waveFresh) null else false
        val first = runCatching { waveBatch(null, limit, null, fresh) }
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
            if (code == 200) {
                SessionState.lastRefreshAt = System.currentTimeMillis()
                Logs.add("wave", "empty → session refreshed, retrying")
                runCatching { waveBatch(null, limit, null, fresh) }.getOrNull()
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

    suspend fun history(offset: Int = 0, limit: Int = 50, fresh: Boolean = false): HistoryPage =
        getJson("$API_BASE/history?limit=$limit&offset=$offset", HistoryPage.serializer(), fresh)

    suspend fun clearHistory() {
        mutate("DELETE", "/history", null)
    }

    suspend fun logout(sid: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/auth/logout")
                .header("x-session-id", sid)
                .post(ByteArray(0).toRequestBody(null))
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            AudioRoute.builder(http).callTimeout(15, TimeUnit.SECONDS).build()
                .newCall(req).execute().use { res ->
                    Logs.add("auth", "logout → ${res.code}")
                }
        }.onFailure { Logs.add("auth", "logout failed: ${it.javaClass.simpleName}") }
    }

    suspend fun likedTracks(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getReadyJson("$API_BASE/me/likes/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    private fun jsonBody(text: String) = text.toRequestBody("application/json".toMediaType())

    private val emptyBody get() = ByteArray(0).toRequestBody(null)

    suspend fun likeTrack(track: Track) {
        mutate("POST", "/likes/tracks/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun unlikeTrack(urn: String) {
        mutate("DELETE", "/likes/tracks/${enc(urn)}", null)
    }

    suspend fun myPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getReadyJson("$API_BASE/me/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

    suspend fun playlistTracks(urn: String, page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedTracks =
        getReadyJson("$API_BASE/playlists/${enc(urn)}/tracks?limit=$limit&page=$page", PagedTracks.serializer(), fresh)

    suspend fun likedPlaylists(page: Int = 0, limit: Int = 50, fresh: Boolean = false): PagedPlaylists =
        getReadyJson("$API_BASE/me/likes/playlists?limit=$limit&page=$page", PagedPlaylists.serializer(), fresh)

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

    suspend fun latestRelease(): ReleaseInfo? = withContext(Dispatchers.IO) {
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
                val assets = (obj["assets"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull { el ->
                    val a = el as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val name = a["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val link = a["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val size = a["size"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
                    ApkAsset(name, link, size)
                }.filter { it.name.endsWith(".apk", ignoreCase = true) }
                ReleaseInfo(tag, url, assets)
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
        controlClient.newCall(req).execute().use { res ->
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

    suspend fun linkCreate(push: Boolean): LinkCreate = withContext(Dispatchers.IO) {
        val sid = sessionId
        if (push && sid == null) throw ApiHttpException(401, "not signed in")
        val payload = buildJsonObject { put("mode", if (push) "push" else "pull") }
            .toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$CONTROL_BASE/auth/link/create")
            .post(payload)
            .apply { if (push && sid != null) header("x-session-id", sid) }
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        controlClient.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(LinkCreate.serializer(), body)
        }
    }

    suspend fun linkStatus(id: String): LinkStatus = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$CONTROL_BASE/auth/link/status?id=${enc(id)}")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        controlClient.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(LinkStatus.serializer(), body)
        }
    }

    suspend fun refreshSession(): Int = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext 401
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/auth/soundcloud/refresh")
                .header("x-session-id", sid)
                .post(ByteArray(0).toRequestBody(null))
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            AudioRoute.builder(http)
                .callTimeout(45, TimeUnit.SECONDS)
                .build()
                .newCall(req).execute().use { res ->
                    val body = res.body?.string().orEmpty()
                    noteSoundCloudFailure(body, sid)
                    if (!res.isSuccessful) Logs.add("auth", "refresh HTTP ${res.code}")
                    res.code
                }
        }.getOrElse {
            Logs.add("auth", "refresh failed: ${it.javaClass.simpleName}")
            -1
        }
    }

    suspend fun sessionValid(): Boolean? = withContext(Dispatchers.IO) {
        val sid = sessionId ?: return@withContext false
        var uncertain = false
        var answered = false
        for (base in Endpoints.hostsFor("/auth/session")) {
            val valid = runCatching {
                val req = Request.Builder().url("$base/auth/session")
                    .header("x-session-id", sid).cacheControl(CacheControl.FORCE_NETWORK).build()
                apiClient.newCall(req).await().use { res ->
                    if (!res.isSuccessful) null else
                        ApiContract.sessionAuthenticated(res.body?.string().orEmpty())
                }
            }.getOrElse {
                if (it is kotlinx.coroutines.CancellationException) throw it
                null
            }
            if (sid != sessionId) return@withContext null
            if (valid == true) return@withContext true
            if (valid == false) answered = true else uncertain = true
        }
        if (answered && !uncertain) false else null
    }

    suspend fun subscription(): Subscription =
        getJson("$API_BASE/me/subscription", Subscription.serializer())

    suspend fun healthOk(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("$CONTROL_BASE/health")
                .header(NetMonitor.PROBE_HEADER, "1")
                .cacheControl(CacheControl.FORCE_NETWORK)
                .build()
            ScDataSource.probeClient(http).newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun catalogArtist(id: String): CatalogArtist =
        getJson("$API_BASE/artists/${enc(id)}", CatalogArtist.serializer())

    suspend fun catalogArtistTracks(id: String, role: String, sort: String, page: Int = 0, limit: Int = 30): PagedTracks =
        getJson("$API_BASE/artists/${enc(id)}/tracks?role=$role&sort=$sort&limit=$limit&page=$page", PagedTracks.serializer())

    suspend fun catalogArtistAlbums(id: String): List<ArtistAlbum> =
        getJson("$API_BASE/artists/${enc(id)}/albums", kotlinx.serialization.builtins.ListSerializer(ArtistAlbum.serializer()))

    suspend fun catalogAlbum(id: String): CatalogAlbum =
        getJson("$API_BASE/albums/${enc(id)}", CatalogAlbum.serializer())

    suspend fun listeningStats(period: String, fresh: Boolean = false): ListeningStats {
        val offset = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        return getJson("$API_BASE/history/stats?period=$period&utcOffset=$offset", ListeningStats.serializer(), fresh)
    }

    suspend fun userAura(urn: String): AuraInfo =
        getJson("$API_BASE/users/${enc(urn)}/aura", AuraInfo.serializer(), fresh = true)

    suspend fun setAura(id: String, hex: String?) {
        val body = buildJsonObject {
            put("aura_id", id)
            if (hex != null) put("custom_hex", hex)
        }
        mutate("PUT", "/me/aura", jsonBody(body.toString()))
    }

    suspend fun blockedArtists(): List<BlockedArtist> =
        getJson("$API_BASE/blocked-artists", BlockedList.serializer(), fresh = true).collection

    suspend fun blockArtist(entry: BlockedArtist) {
        val input = BlockInput(entry.name, entry.avatar_url, entry.sc_user_ids)
        mutate("PUT", "/blocked-artists/${entry.kind}/${enc(entry.id)}", jsonBody(json.encodeToString(BlockInput.serializer(), input)))
    }

    suspend fun unblockArtist(kind: String, id: String) {
        mutate("DELETE", "/blocked-artists/$kind/${enc(id)}", null)
    }

    suspend fun paySubscription(): PaySubscription = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://pay.scnative.dev/api/me/subscription")
            .apply { sessionId?.let { header("x-session-id", it) } }
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        controlClient.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: ""
            if (!res.isSuccessful) throw ApiHttpException(res.code, body.take(200))
            json.decodeFromString(PaySubscription.serializer(), body)
        }
    }

    suspend fun webProfiles(urn: String): List<WebProfile> {
        val root = getReadyJson("$API_BASE/users/${enc(urn)}/web-profiles", kotlinx.serialization.json.JsonElement.serializer())
        return decodeWebProfiles(root)
    }

    internal fun decodeWebProfiles(root: kotlinx.serialization.json.JsonElement): List<WebProfile> {
        val arr = when (root) {
            is kotlinx.serialization.json.JsonArray -> root
            is kotlinx.serialization.json.JsonObject -> root["collection"] as? kotlinx.serialization.json.JsonArray
            else -> null
        } ?: throw IOException("Invalid artist links response")
        val profiles = arr.mapNotNull { runCatching { json.decodeFromJsonElement(WebProfile.serializer(), it) }.getOrNull() }
        if (arr.isNotEmpty() && profiles.isEmpty()) throw IOException("Invalid artist links response")
        return profiles
    }

    suspend fun isFollowing(me: String, urn: String): Boolean =
        getJson("$API_BASE/users/${enc(me)}/followings/${enc(urn)}", kotlinx.serialization.serializer<Boolean>(), fresh = true)

    suspend fun follow(urn: String) {
        mutate("PUT", "/me/followings/${enc(urn)}", emptyBody)
    }

    suspend fun unfollow(urn: String) {
        mutate("DELETE", "/me/followings/${enc(urn)}", null)
    }

    suspend fun dislikedIds(): List<String> =
        getJson("$API_BASE/dislikes/ids", DislikeIds.serializer()).ids

    suspend fun dislike(track: Track) {
        mutate("POST", "/dislikes/${enc(track.urn)}", jsonBody(json.encodeToString(Track.serializer(), track)))
    }

    suspend fun undislike(urn: String) {
        mutate("DELETE", "/dislikes/${enc(urn)}", null)
    }

    suspend fun createPlaylist(title: String, sharing: String, trackUrns: List<String>): CreatedPlaylist {
        val payload = buildJsonObject {
            put("playlist", buildJsonObject {
                put("title", title)
                put("sharing", sharing)
                if (trackUrns.isNotEmpty()) {
                    put("tracks", kotlinx.serialization.json.buildJsonArray {
                        trackUrns.forEach { urn -> add(buildJsonObject { put("urn", urn) }) }
                    })
                }
            })
        }.toString()
        val body = mutate("POST", "/playlists", jsonBody(payload))
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
        val urn = obj?.get("urn")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
        val status = obj?.get("status")?.jsonPrimitive?.contentOrNull
        Logs.add("api", "playlist created: ${urn ?: status ?: "no urn in reply"}")
        return CreatedPlaylist(urn, status == "queued")
    }

    suspend fun setPlaylistSharing(urn: String, sharing: String) {
        val payload = buildJsonObject { put("sharing", sharing) }.toString()
        mutate("PUT", "/playlists/${enc(urn)}/sharing", jsonBody(payload))
    }

    suspend fun removeFromPlaylist(playlistUrn: String, trackUrn: String) {
        val payload = buildJsonObject { put("remove", trackUrn) }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
    }

    suspend fun deletePlaylist(urn: String) {
        mutate("DELETE", "/playlists/${enc(urn)}", null)
    }

    suspend fun reorderPlaylist(playlistUrn: String, order: List<String>) {
        val payload = buildJsonObject {
            put("order", kotlinx.serialization.json.JsonArray(order.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
    }

    suspend fun addToPlaylist(playlistUrn: String, trackUrn: String) {
        val payload = buildJsonObject { put("add", trackUrn) }.toString()
        mutate("POST", "/playlists/${enc(playlistUrn)}/tracks", jsonBody(payload))
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
        mutate("POST", "/history", body)
        Unit
    }

    fun streamUrl(urn: String, hq: Boolean = false, goPlus: Boolean = false): String {
        val base = if (goPlus && Prefs.star) Endpoints.STREAM_STAR else STREAM_BASE
        val qs = if (hq) "?hq=true" else ""
        return "$base/stream/${enc(urn)}$qs"
    }

    fun authorize(builder: Request.Builder, url: String): Request.Builder {
        val sid = sessionId ?: return builder
        if (!Endpoints.trustedUrl(url)) return builder
        val path = "/" + url.substringAfter("://").substringAfter('/', "")
        if (path.startsWith("/stream/") && !url.contains("ticket=")) {
            builder.header("x-session-id", sid)
            return builder.url(ticketedStream(url, path))
        }
        if (!path.startsWith("/download/")) return builder
        builder.header("x-session-id", sid)
        if (url.contains("session_id=")) return builder
        val sep = if (url.contains('?')) '&' else '?'
        return builder.url("$url${sep}session_id=${enc(sid)}")
    }

    private fun ticketedStream(url: String, path: String): String {
        val host = url.substringAfter("://").substringBefore('/')
        val apiHost = if (host.startsWith("stream")) "api" + host.removePrefix("stream") else host
        val urn = path.removePrefix("/stream/").substringBefore('?')
        val query = path.substringAfter('?', "").split('&').filter { it.isNotEmpty() && !it.startsWith("session_id=") }
        return "https://$apiHost/tracks/$urn/stream" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
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

    private fun pathOf(url: String): String {
        val afterScheme = url.removePrefix("https://").removePrefix("http://")
        val slash = afterScheme.indexOf('/')
        return if (slash >= 0) afterScheme.substring(slash) else "/"
    }

    private suspend fun <T> getReadyJson(url: String, strategy: DeserializationStrategy<T>, fresh: Boolean = false): T {
        if (Prefs.offline) return getJson(url, strategy, fresh)
        val sid = sessionId
        return CatalogRead.awaitReady {
            if (sid != sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
            getJson(url, strategy, fresh)
        }
    }

    private suspend fun <T> getJson(url: String, strategy: DeserializationStrategy<T>, fresh: Boolean = false): T =
        withContext(Dispatchers.IO) {
            val path = pathOf(url)
            val hosts = Endpoints.hostsFor(path)
            val firstMain = hosts.firstOrNull { !Endpoints.isStarApi(it) }
            var lastError: Exception? = null
            var rejectedSession: ApiHttpException? = null
            val sid = sessionId
            for (base in hosts) {
                val isStar = Endpoints.isStarApi(base)
                val full = base + path
                try {
                    val req = Request.Builder().url(full).apply {
                        sid?.let { header("x-session-id", it) }
                        if (fresh || !Prefs.offline) cacheControl(CacheControl.FORCE_NETWORK)
                    }.build()
                    return@withContext apiClient.newCall(req).await().use { res ->
                        val body = res.body?.string() ?: ""
                        if (sid != sessionId) throw kotlinx.coroutines.CancellationException("Session changed")
                        if (!res.isSuccessful) {
                            if (isStar && res.code == 403) {
                                throw IOException("star ${res.code}")
                            }
                            val error = ApiHttpException(res.code, body.take(2000), res.header("Retry-After")?.toLongOrNull())
                            if (res.code == 401) {
                                rejectedSession = rejectedSession ?: error
                                throw IOException("session rejected by one API route")
                            }
                            noteAuthFailure(res.code, body, sid)
                            if (res.code in 400..499 || !ApiContract.retryHost(res.code, body)) throw error
                            throw ApiHostException(res.code)
                        }
                        noteSoundCloudFailure(body, sid)
                        ApiContract.collectionFailure(body)?.let { throw it }
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
            rejectedSession?.let {
                noteAuthFailure(it.code, it.body, sid)
                throw it
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

class ApiHttpException(val code: Int, val body: String, val retryAfterSeconds: Long? = null) : IOException("API $code: $body") {
    val serviceCode: String? get() = ApiContract.errorCode(body)
}

class ApiHostException(val code: Int) : IOException("API host $code")

@Serializable
data class PagedTracks(
    val collection: List<Track> = emptyList(),
    val page: Int = 0,
    val page_size: Int = 0,
    val has_more: Boolean = false,
    val sync: CollectionSync? = null,
) {
    val snapshotReady: Boolean get() = sync?.snapshotReady != false
    val complete: Boolean get() = !has_more && snapshotReady
}

data class ApkAsset(val name: String, val url: String, val size: Long)

data class ReleaseInfo(val tag: String, val url: String, val assets: List<ApkAsset>)

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
    val enrichment: TrackEnrichment? = null,
    val playback_count: Long? = null,
    val likes_count: Long? = null,
    val favoritings_count: Long? = null,
    val reposts_count: Long? = null,
    val comment_count: Long? = null,
) {
    val catalogArtist: EnrichmentArtist? get() = enrichment?.primary_artist?.takeIf { it.id.isNotBlank() }

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
    val sync: CollectionSync? = null,
) {
    val snapshotReady: Boolean get() = sync?.snapshotReady != false
    val complete: Boolean get() = !has_more && snapshotReady
}

@Serializable
data class Playlist(
    val urn: String,
    val title: String = "",
    val artwork_url: String? = null,
    val track_count: Int = 0,
    val user: TrackUser? = null,
    val kind: String? = null,
    val sharing: String? = null,
    val last_modified: String? = null,
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

data class CreatedPlaylist(val urn: String?, val queued: Boolean)

@Serializable
data class AuthStatus(
    val authenticated: Boolean = false,
    val username: String? = null,
)

@Serializable
data class LinkCreate(
    val linkRequestId: String = "",
    val claimToken: String = "",
    val expiresAt: String? = null,
)

@Serializable
data class LinkStatus(
    val status: String = "",
    val mode: String = "",
    val sessionId: String? = null,
    val error: String? = null,
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
    val permalink_url: String? = null,
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
) {
    val type: String
        get() = when {
            urn.startsWith("soundcloud:tracks:") -> "track"
            urn.startsWith("soundcloud:playlists:") || urn.startsWith("soundcloud:system-playlists:") -> "playlist"
            urn.startsWith("soundcloud:users:") -> "user"
            kind == "system-playlist" -> "playlist"
            else -> kind.orEmpty()
        }
}

@Serializable
data class LyricsResponse(
    val syncedLrc: String? = null,
    val plainText: String? = null,
    val source: String? = null,
    val language: String? = null,
    val status: String? = null,
)

data class LyricLine(val timeMs: Long, val text: String)

@Serializable
data class VibeSearchResponse(
    val items: List<Track> = emptyList(),
    val status: String? = null,
)

fun parseLrc(lrc: String): List<LyricLine> {
    val tag = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    val offset = Regex("(?im)^\\s*\\[offset:\\s*([+-]?\\d+)\\s*]").find(lrc)
        ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    return lrc.lineSequence().flatMap { raw ->
        var rest = raw.trim()
        val times = mutableListOf<Long>()
        while (true) {
            val match = tag.find(rest) ?: break
            val (minutes, seconds, fraction) = match.destructured
            times += (minutes.toLong() * 60_000 + seconds.toLong() * 1000 +
                fraction.padEnd(3, '0').toLong() - offset).coerceAtLeast(0)
            rest = rest.substring(match.value.length).trimStart()
        }
        times.map { LyricLine(it, rest.trim()) }
    }.sortedBy { it.timeMs }.toList()
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
    val track: Track? = null,
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
    val trackUrn: String? = null,
) {
    fun toTrack(): Track = Track(
        urn = trackUrn ?: if (scTrackId.all { it.isDigit() } && scTrackId.isNotEmpty()) "soundcloud:tracks:$scTrackId" else scTrackId,
        title = title,
        duration = duration,
        artwork_url = artworkUrl,
        user = TrackUser(urn = artistUrn, username = artistName),
    )
}
