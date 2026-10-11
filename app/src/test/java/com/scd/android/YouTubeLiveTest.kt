package com.scd.android

import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class YouTubeLiveTest {

    private val live = System.getProperty("yt.live") == "true"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun report(line: String) = println("YT: $line")

    @Test
    fun parsesLinks() {
        assertEquals(YtRef.Video("dQw4w9WgXcQ"), YouTubeClient.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=10"))
        assertEquals(YtRef.Video("dQw4w9WgXcQ"), YouTubeClient.parse("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(YtRef.Video("dQw4w9WgXcQ"), YouTubeClient.parse("https://music.youtube.com/watch?v=dQw4w9WgXcQ&feature=share"))
        assertEquals(YtRef.Video("dQw4w9WgXcQ"), YouTubeClient.parse("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(YtRef.Playlist("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"), YouTubeClient.parse("https://www.youtube.com/playlist?list=PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI"))
        assertEquals(YtRef.Playlist("OLAK5uy_kAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), YouTubeClient.parse("https://music.youtube.com/playlist?list=OLAK5uy_kAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
        assertEquals(YtRef.Video("dQw4w9WgXcQ"), YouTubeClient.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=RDdQw4w9WgXcQ"))
        assertEquals(null, YouTubeClient.parse("https://soundcloud.com/x/y"))
        assertEquals("Artist", YouTubeClient.cleanAuthor("Artist - Topic"))
    }

    @Test
    fun liveVideoAndDownload() {
        assumeTrue(live)
        val yt = YouTubeClient(http) { report("log $it") }
        for (id in listOf("dQw4w9WgXcQ", "jNQXAC9IVRw", "kJQP7kiw5Fk")) {
            report("video $id start")
            val res = runCatching { yt.video(id) }
            res.onFailure { report("video $id FAIL ${it.message}") }
            val (v, audio) = res.getOrNull() ?: continue
            val best = audio.first()
            report("video $id OK '${v.title}' by '${v.author}' ${v.durationMs / 1000}s via ${best.client} ${best.mime.substringBefore(';')} ${best.bitrate / 1000}k ${best.contentLength / 1024}KB formats=${audio.size}")
            val tmp = File.createTempFile("ytdl", ".part")
            val small = best.copy(contentLength = minOf(best.contentLength, 3L * 1024 * 1024))
            val started = System.currentTimeMillis()
            val ok = runCatching { yt.download(small, tmp) { done, total -> if (done == total || done % (1024 * 1024) < 70_000) report("progress $id $done/$total") } }
            report("download $id took ${(System.currentTimeMillis() - started) / 1000}s")
            report("download $id ${if (ok.getOrDefault(false)) "OK ${tmp.length() / 1024}KB" else "FAIL ${ok.exceptionOrNull()?.message}"}")
            tmp.delete()
        }
    }

    @Test
    fun livePlaylist() {
        assumeTrue(live)
        val yt = YouTubeClient(http) { report("log $it") }
        for (id in listOf("PLFgquLnL59alCl_2TQvOiD5Vgm1hCaGSI", "PL4fGSI1pDJn6puJdseH2Rt9sMvt9E2M4i")) {
            val res = runCatching { yt.playlist(id, limit = 300) }
            res.onSuccess { p -> report("playlist $id OK '${p.title}' items=${p.items.size} first='${p.items.firstOrNull()?.title}' by '${p.items.firstOrNull()?.author}' ${p.items.firstOrNull()?.durationMs}") }
            res.onFailure { report("playlist $id FAIL ${it.message}") }
        }
    }

    @Test
    fun liveSpotify() {
        assumeTrue(live)
        val sp = SpotifySource(http)
        for (link in listOf(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M",
            "https://open.spotify.com/album/4aawyAB9vmqN3uQ7FjRGTy",
            "https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT",
        )) {
            val ref = SpotifySource.parse(link)
            if (ref == null) {
                report("spotify parse FAIL $link")
                continue
            }
            runCatching { sp.fetch(ref.first, ref.second) }
                .onSuccess { l -> report("spotify ${ref.first} OK '${l.title}' items=${l.tracks.size} first='${l.tracks.firstOrNull()?.artist} - ${l.tracks.firstOrNull()?.title}' ${l.tracks.firstOrNull()?.durationMs}") }
                .onFailure { report("spotify ${ref.first} FAIL ${it.message}") }
        }
    }

    @Test
    fun liveYandexPublic() {
        assumeTrue(live)
        val ym = YandexSource(http, null)
        for (link in listOf("https://music.yandex.ru/album/4766", "https://music.yandex.ru/users/music-blog/playlists/2140")) {
            val ref = YandexSource.parse(link) ?: continue
            runCatching { ym.fetch(ref) }
                .onSuccess { l -> report("yandex OK '${l.title}' items=${l.tracks.size} first='${l.tracks.firstOrNull()?.artist} - ${l.tracks.firstOrNull()?.title}'") }
                .onFailure { report("yandex FAIL $link ${it.message}") }
        }
    }
}
