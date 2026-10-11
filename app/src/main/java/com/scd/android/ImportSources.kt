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

data class ExtList(val title: String, val tracks: List<ExtTrack>)

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

class SpotifySource(private val http: OkHttpClient) {
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
        return null
    }

    fun fetch(type: String, id: String): ExtList {
        val req = Request.Builder()
            .url("https://open.spotify.com/embed/$type/$id")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36")
            .header("Accept-Language", "en")
            .build()
        val html = http.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw ImportException("unavailable", "spotify HTTP ${res.code}")
            res.body?.string().orEmpty()
        }
        val e = entity(html) ?: throw ImportException("parse", "spotify: no data in embed page")
        val title = e.s("name") ?: e.s("title") ?: "Spotify"
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
        return ExtList(title, tracks)
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
