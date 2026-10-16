package com.scd.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.util.Base64

data class ExtTrack(val title: String, val artist: String, val durationMs: Long = 0)

data class ExtList(val title: String, val tracks: List<ExtTrack>, val partial: Boolean = false)

class ImportException(val code: String, message: String) : IOException(message)

private val importJson = Json { ignoreUnknownKeys = true }

private fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur = this
    for (p in path) {
        cur = when (p) {
            is String -> (cur as? JsonObject)?.get(p)
            is Int -> (cur as? JsonArray)?.getOrNull(p)
            else -> null
        } ?: return null
    }
    return cur
}

private fun JsonElement?.s(vararg path: Any): String? = (at(*path) as? JsonPrimitive)?.contentOrNull

private fun JsonElement?.arr(vararg path: Any): JsonArray = (at(*path) as? JsonArray) ?: JsonArray(emptyList())

class SpotifySource(private val http: OkHttpClient, private val token: String? = null) {
    companion object {
        private val PATH = Regex("^/(?:intl-[a-z]{2}(?:-[a-z]{2})?/)?(?:embed/)?(playlist|album|track)/([A-Za-z0-9]{10,40})")

        fun parse(input: String): Pair<String, String>? {
            val raw = input.trim()
            Regex("^spotify:(playlist|album|track):([A-Za-z0-9]+)$").find(raw)?.let { return it.groupValues[1] to it.groupValues[2] }
            val uri = runCatching { URI(if (raw.contains("://")) raw else "https://$raw") }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (host != "open.spotify.com" && host != "play.spotify.com") return null
            val m = PATH.find(uri.path.orEmpty()) ?: return null
            return m.groupValues[1] to m.groupValues[2]
        }
    }

    private fun scriptContent(html: String, marker: String): String? {
        val idx = html.indexOf(marker)
        if (idx < 0) return null
        val start = html.indexOf('>', idx) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        return html.substring(start, end).trim()
    }

    private fun entity(html: String): JsonElement? {
        scriptContent(html, "id=\"__NEXT_DATA__\"")?.let { text ->
            runCatching { importJson.parseToJsonElement(text) }.getOrNull()
                ?.at("props", "pageProps", "state", "data", "entity")?.let { return it }
        }
        for (marker in listOf("id=\"resource\"", "id=\"initial-state\"")) {
            val text = scriptContent(html, marker) ?: continue
            val decoded = runCatching { String(Base64.getDecoder().decode(text)) }.getOrNull() ?: continue
            val root = runCatching { importJson.parseToJsonElement(decoded) }.getOrNull() ?: continue
            return root.at("data", "entity") ?: root
        }
        return scanEntity(html)
    }

    private fun findEntity(e: JsonElement?, depth: Int = 0): JsonElement? {
        if (e == null || depth > 14) return null
        return when (e) {
            is JsonObject -> if (e["trackList"] is JsonArray) e else e.values.firstNotNullOfOrNull { findEntity(it, depth + 1) }
            is JsonArray -> e.firstNotNullOfOrNull { findEntity(it, depth + 1) }
            else -> null
        }
    }

    private val scriptRx = Regex("<script[^>]*>([\\s\\S]*?)</script>")
    private val flightRx = Regex("self\\.__next_f\\.push\\(\\[1,(\"(?:[^\"\\\\]|\\\\.)*\")\\]\\)")

    private fun unescapeHtml(s: String) = s.replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    private fun pageTitle(html: String): String? =
        (Regex("<meta[^>]+property=\"og:title\"[^>]+content=\"([^\"]*)\"").find(html)?.groupValues?.get(1)
            ?: Regex("<title>([^<]*)</title>").find(html)?.groupValues?.get(1)?.removePrefix("Spotify Embed: ")?.substringBefore(" | Spotify"))
            ?.let(::unescapeHtml)?.takeIf { it.isNotBlank() }

    private fun scanEntity(html: String): JsonElement? {
        for (m in scriptRx.findAll(html)) {
            val text = m.groupValues[1].trim()
            if (text.isEmpty() || !(text.startsWith("{") || text.startsWith("[") || !text.contains(' '))) continue
            val parsed = runCatching { importJson.parseToJsonElement(text) }.getOrNull()
                ?: runCatching { importJson.parseToJsonElement(String(Base64.getDecoder().decode(text))) }.getOrNull()
            findEntity(parsed)?.let { return it }
        }
        val flight = flightRx.findAll(html)
            .mapNotNull { m -> runCatching { (importJson.parseToJsonElement(m.groupValues[1]) as JsonPrimitive).content }.getOrNull() }
            .joinToString("")
        for (text in listOf(flight, html, unescapeHtml(html))) {
            val list = arrayAfter(text, "\"trackList\":") ?: continue
            val name = pageTitle(html)
            return JsonObject(buildMap<String, JsonElement> {
                put("trackList", list)
                if (name != null) put("name", JsonPrimitive(name))
            })
        }
        return null
    }

    private fun arrayAfter(text: String, key: String): JsonArray? {
        var from = 0
        while (true) {
            val i = text.indexOf(key, from)
            if (i < 0) return null
            from = i + key.length
            val start = text.indexOf('[', from)
            if (start < 0 || text.substring(from, start).isNotBlank()) continue
            var depth = 0
            var inStr = false
            var esc = false
            for (j in start until text.length) {
                val c = text[j]
                if (inStr) {
                    if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                    continue
                }
                when (c) {
                    '"' -> inStr = true
                    '[', '{' -> depth++
                    ']', '}' -> if (--depth == 0) {
                        val arr = runCatching { importJson.parseToJsonElement(text.substring(start, j + 1)) as? JsonArray }.getOrNull()
                        if (!arr.isNullOrEmpty()) return arr
                        break
                    }
                }
            }
        }
    }

    private fun api(path: String): JsonElement {
        val url = if (path.startsWith("https://")) path else "https://api.spotify.com/v1$path"
        repeat(3) { attempt ->
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${token.orEmpty()}")
                .header("Accept", "application/json")
                .build()
            http.newCall(req).execute().use { res ->
                if (res.code == 429 && attempt < 2) {
                    val wait = res.header("Retry-After")?.toLongOrNull()?.coerceIn(1, 10) ?: 2
                    Thread.sleep(wait * 1000)
                    return@repeat
                }
                if (res.code == 401) throw ImportException("sp_token", "spotify api HTTP 401")
                if (res.code == 403) throw ImportException("sp_forbidden", "spotify api HTTP 403 ${path.substringBefore('?').takeLast(40)}: ${res.body?.string().orEmpty().take(100)}")
                if (!res.isSuccessful) throw ImportException("unavailable", "spotify api HTTP ${res.code}")
                return importJson.parseToJsonElement(res.body?.string().orEmpty())
            }
        }
        throw ImportException("unavailable", "spotify api rate limited")
    }

    private fun apiTrack(t: JsonElement?): ExtTrack? {
        if (t == null || t.s("type") == "episode") return null
        val name = t.s("name")?.trim().orEmpty()
        if (name.isEmpty()) return null
        val artist = t.arr("artists").mapNotNull { it.s("name") }.joinToString(", ")
        return ExtTrack(name, artist, t.s("duration_ms")?.toLongOrNull() ?: 0L)
    }

    private fun pages(first: String, pick: (JsonElement) -> JsonElement?, onProgress: (Int, Int) -> Unit, start: JsonElement? = null): List<ExtTrack> {
        val out = mutableListOf<ExtTrack>()
        var page: JsonElement? = start ?: api(first)
        var guard = 0
        while (page != null && guard++ < 200) {
            page.arr("items").mapNotNullTo(out) { apiTrack(pick(it)) }
            onProgress(out.size, page.s("total")?.toIntOrNull() ?: out.size)
            val next = page.s("next")?.takeIf { it.startsWith("https://api.spotify.com/") } ?: break
            page = api(next)
        }
        return out
    }

    fun fetchWithLogin(type: String, id: String, onProgress: (Int, Int) -> Unit): ExtList = when (type) {
        "playlist" -> {
            val meta = runCatching { api("/playlists/$id") }
            (meta.exceptionOrNull() as? ImportException)?.takeIf { it.code == "sp_token" }?.let { throw it }
            val obj = meta.getOrNull()
            val title = obj?.s("name") ?: "Spotify"
            val pick: (JsonElement) -> JsonElement? = { it.at("item") ?: it.at("track") }
            val embedded = listOf("items", "tracks").firstNotNullOfOrNull { key -> obj?.at(key)?.takeIf { it.at("items") != null } }
            var tracks = embedded?.let { start -> runCatching { pages("", pick, onProgress, start = start) }.getOrNull() }.orEmpty()
            if (tracks.isEmpty()) {
                val viaItems = runCatching { pages("/playlists/$id/items?limit=50", pick, onProgress) }
                (viaItems.exceptionOrNull() as? ImportException)?.takeIf { it.code == "sp_token" }?.let { throw it }
                tracks = viaItems.getOrNull().orEmpty()
                if (tracks.isEmpty()) {
                    val me = runCatching { api("/me").s("id") }.getOrNull() ?: "?"
                    Logs.add("import", "spotify items for $id: ${viaItems.exceptionOrNull()?.message?.take(160) ?: "none"}, owner ${obj?.at("owner")?.s("id") ?: "?"}, me $me, meta ${meta.exceptionOrNull()?.message?.take(60) ?: "ok"}")
                }
            }
            if (tracks.isEmpty()) throw ImportException("sp_not_owned", "spotify: items are only given for your own playlists")
            ExtList(title, tracks)
        }
        "album" -> {
            val album = api("/albums/$id")
            val title = album.s("name") ?: "Spotify"
            val tracks = pages("", { it }, onProgress, start = album.at("tracks"))
            if (tracks.isEmpty()) throw ImportException("empty", "spotify: no tracks")
            ExtList(title, tracks)
        }
        else -> {
            val t = apiTrack(api("/tracks/$id")) ?: throw ImportException("empty", "spotify: no track")
            ExtList(t.title, listOf(t))
        }
    }

    fun likes(onProgress: (Int, Int) -> Unit): ExtList {
        if (token.isNullOrBlank()) throw ImportException("sp_token", "spotify login required")
        val tracks = pages("/me/tracks?limit=50", { it.at("track") }, onProgress)
        if (tracks.isEmpty()) throw ImportException("empty", "spotify: no liked tracks")
        return ExtList("Spotify", tracks)
    }

    fun fetch(type: String, id: String, onProgress: (Int, Int) -> Unit = { _, _ -> }): ExtList {
        var apiError: Throwable? = null
        if (!token.isNullOrBlank()) {
            val viaApi = runCatching { fetchWithLogin(type, id, onProgress) }
            viaApi.getOrNull()?.let { return it }
            apiError = viaApi.exceptionOrNull()
            Logs.add("import", "spotify api failed: ${apiError?.message?.take(200)} → embed")
        }
        return runCatching { fetchEmbed(type, id) }.getOrElse { e ->
            Logs.add("import", "spotify embed failed: ${e.message?.take(80)}")
            val code = (apiError as? ImportException)?.code
            if (type == "playlist" && code == "sp_not_owned") throw ImportException("sp_not_owned", "spotify: not your playlist and embed failed")
            if (code == "sp_forbidden") throw apiError ?: e
            val needsLogin = token.isNullOrBlank() || code == "sp_token"
            if (type == "playlist" && needsLogin) throw ImportException("sp_token", "spotify playlist needs login")
            throw e
        }
    }

    private fun fetchEmbed(type: String, id: String): ExtList {
        val req = Request.Builder()
            .url("https://open.spotify.com/embed/$type/$id")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
            .header("Accept-Language", "en")
            .build()
        var landed = ""
        val html = http.newCall(req).execute().use { res ->
            landed = res.request.url.let { it.host + it.encodedPath }
            if (!res.isSuccessful) throw ImportException("unavailable", "spotify HTTP ${res.code} @$landed")
            res.body?.string().orEmpty()
        }
        val e = entity(html) ?: run {
            val ids = Regex("<script[^>]*id=\"([^\"]+)\"").findAll(html).map { it.groupValues[1] }.take(6).joinToString(",")
            val head = Regex("<title>([^<]*)</title>").find(html)?.groupValues?.get(1)?.take(40)
            Logs.add("import", "spotify embed page @$landed: ${html.length} b, title '$head', scripts [$ids], flight ${html.contains("__next_f")}, trackList ${html.contains("trackList")}")
            throw ImportException("parse", "spotify: no data in embed page")
        }
        val title = e.s("name") ?: e.s("title") ?: pageTitle(html) ?: "Spotify"
        val list = e.arr("trackList")
        val tracks = if (list.isNotEmpty()) {
            list.mapNotNull { t ->
                val name = t.s("title") ?: t.s("name") ?: return@mapNotNull null
                val artist = t.arr("artists").mapNotNull { it.s("name") }.joinToString(", ").ifBlank { t.s("subtitle").orEmpty() }
                ExtTrack(name, artist.replace(' ', ' '), t.s("duration")?.toLongOrNull() ?: 0L)
            }
        } else {
            val artist = e.arr("artists").mapNotNull { it.s("name") }.joinToString(", ").ifBlank { e.s("subtitle").orEmpty() }
            listOf(ExtTrack(title, artist, e.s("duration")?.toLongOrNull() ?: 0L))
        }
        if (tracks.isEmpty()) throw ImportException("empty", "spotify: no tracks")
        val partial = type == "playlist" && tracks.size >= 100
        if (partial) Logs.add("import", "spotify embed is capped at ${tracks.size} tracks")
        return ExtList(title, tracks, partial)
    }
}

sealed interface YmRef {
    data class UserPlaylist(val owner: String, val kind: String) : YmRef
    data class Playlist(val uuid: String) : YmRef
    data class Album(val id: String) : YmRef
    data class Track(val id: String) : YmRef
}

class YandexSource(private val http: OkHttpClient, private val token: String?) {
    companion object {
        const val API = "https://api.music.yandex.net"

        fun parse(input: String): YmRef? {
            val uri = runCatching { URI(input.trim().let { if (it.contains("://")) it else "https://$it" }) }.getOrNull() ?: return null
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("music.yandex.")) return null
            val p = uri.path.orEmpty().trim('/').split('/')
            return when {
                p.size >= 4 && p[0] == "users" && p[2] == "playlists" -> YmRef.UserPlaylist(p[1], p[3])
                p.size >= 2 && p[0] == "playlists" -> YmRef.Playlist(p[1])
                p.size >= 4 && p[0] == "album" && p[2] == "track" -> YmRef.Track(p[3])
                p.size >= 2 && p[0] == "album" -> YmRef.Album(p[1])
                p.size >= 2 && p[0] == "track" -> YmRef.Track(p[1])
                else -> null
            }
        }
    }

    private fun get(path: String): JsonElement {
        val req = Request.Builder()
            .url(API + path)
            .header("User-Agent", "Yandex-Music-API")
            .header("X-Yandex-Music-Client", "YandexMusicAndroid/24023621")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "OAuth ${token.trim()}") }
            .build()
        http.newCall(req).execute().use { res ->
            if (res.code == 401 || res.code == 403) throw ImportException("token", "yandex HTTP ${res.code}")
            if (!res.isSuccessful) throw ImportException("unavailable", "yandex HTTP ${res.code}")
            return importJson.parseToJsonElement(res.body?.string().orEmpty())
        }
    }

    private fun toTrack(t: JsonElement?): ExtTrack? {
        val title = t.s("title")?.trim().orEmpty()
        val version = t.s("version")?.trim().orEmpty()
        val artist = t.arr("artists").mapNotNull { it.s("name") }.firstOrNull().orEmpty()
        if (title.isEmpty() && artist.isEmpty()) return null
        val full = if (version.isNotEmpty() && !title.contains(version, ignoreCase = true)) "$title ($version)" else title
        return ExtTrack(full, artist, t.s("durationMs")?.toLongOrNull() ?: 0L)
    }

    private fun idOf(e: JsonElement?): String? = e.s("id") ?: (e as? JsonPrimitive)?.contentOrNull

    private fun resolveIds(ids: List<String>, onProgress: (Int, Int) -> Unit): List<ExtTrack> {
        val out = mutableListOf<ExtTrack>()
        ids.chunked(50).forEach { chunk ->
            val res = get("/tracks?trackIds=" + chunk.joinToString(","))
            res.arr("result").mapNotNullTo(out) { toTrack(it) }
            onProgress(out.size, ids.size)
        }
        return out
    }

    private fun tracksOf(list: JsonArray, onProgress: (Int, Int) -> Unit): List<ExtTrack> {
        val direct = list.mapNotNull { toTrack(it.at("track")) }
        if (direct.size >= list.size * 9 / 10 && direct.isNotEmpty()) return direct
        val ids = list.mapNotNull { e -> e.s("id") ?: e.s("track", "id") }.map { it.substringBefore(':') }
        return resolveIds(ids, onProgress)
    }

    fun likes(onProgress: (Int, Int) -> Unit = { _, _ -> }): ExtList {
        if (token.isNullOrBlank()) throw ImportException("token", "token required")
        val uid = get("/account/status").s("result", "account", "uid") ?: throw ImportException("token", "no uid")
        val liked = get("/users/$uid/likes/tracks").arr("result", "library", "tracks")
        val ids = liked.mapNotNull { idOf(it) }.map { it.substringBefore(':') }
        return ExtList("Yandex Music", resolveIds(ids, onProgress))
    }

    fun fetch(ref: YmRef, onProgress: (Int, Int) -> Unit = { _, _ -> }): ExtList = when (ref) {
        is YmRef.UserPlaylist -> {
            val r = get("/users/${ref.owner}/playlists/${ref.kind}").at("result")
            ExtList(r.s("title") ?: "Yandex Music", tracksOf(r.arr("tracks"), onProgress))
        }
        is YmRef.Playlist -> {
            val r = get("/playlist/${ref.uuid}").at("result")
            ExtList(r.s("title") ?: "Yandex Music", tracksOf(r.arr("tracks"), onProgress))
        }
        is YmRef.Album -> {
            val r = get("/albums/${ref.id}/with-tracks").at("result")
            val tracks = r.arr("volumes").flatMap { v -> (v as? JsonArray).orEmpty().mapNotNull { toTrack(it) } }
            ExtList(r.s("title") ?: "Yandex Music", tracks)
        }
        is YmRef.Track -> {
            val r = get("/tracks/${ref.id}").arr("result")
            val t = r.mapNotNull { toTrack(it) }
            ExtList(t.firstOrNull()?.let { "${it.artist} — ${it.title}" } ?: "Yandex Music", t)
        }
    }
}
