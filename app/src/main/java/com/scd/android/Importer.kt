package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

object YouTubeImport {
    const val PREFIX = "youtube:tracks:"

    fun isYouTube(urn: String?) = urn?.startsWith(PREFIX) == true

    val client by lazy {
        YouTubeClient(
            Api.http.newBuilder()
                .cache(null)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .build(),
        ) { Logs.add("youtube", it.take(160)) }
    }

    fun track(v: YtVideo) = Track(
        urn = PREFIX + v.id,
        title = v.title,
        duration = v.durationMs,
        artwork_url = v.thumbnail,
        permalink_url = "https://youtu.be/${v.id}",
        user = TrackUser(username = v.author),
    )

    fun downloadTo(track: Track, tmp: File): Boolean {
        val id = track.urn.removePrefix(PREFIX)
        val (_, audio) = client.video(id)
        var last: Throwable? = null
        for (a in audio.take(3)) {
            tmp.delete()
            val ok = runCatching { client.download(a, tmp) }.onFailure { last = it }.getOrDefault(false)
            if (ok) {
                Logs.add("youtube", "${track.title.take(24)}: ${a.client} ${a.mime.substringBefore(';')} ${a.bitrate / 1000}k")
                return true
            }
        }
        last?.let { throw it }
        return false
    }
}

enum class ImportStage { Reading, Matching, Saving, Done, Failed }

data class ImportState(
    val source: String,
    val title: String = "",
    val stage: ImportStage = ImportStage.Reading,
    val total: Int = 0,
    val done: Int = 0,
    val found: Int = 0,
    val missed: List<String> = emptyList(),
    val error: String? = null,
    val playlists: Int = 0,
)

object Importer {
    const val YOUTUBE = "youtube"
    const val SPOTIFY = "spotify"
    const val YANDEX = "yandex"
    private const val CHUNK = 500

    var state by mutableStateOf<ImportState?>(null)
        private set

    private var job: Job? = null

    val busy: Boolean get() = state?.stage.let { it == ImportStage.Reading || it == ImportStage.Matching || it == ImportStage.Saving }

    private val http by lazy {
        Api.http.newBuilder().cache(null).connectTimeout(15, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).build()
    }

    fun detect(link: String): String? = when {
        YouTubeClient.parse(link) != null -> YOUTUBE
        SpotifySource.parse(link) != null -> SPOTIFY
        YandexSource.parse(link) != null -> YANDEX
        else -> null
    }

    fun cancel() {
        job?.cancel()
        job = null
        state = state?.copy(stage = ImportStage.Failed, error = "cancelled")
    }

    fun clear() {
        if (!busy) state = null
    }

    private suspend fun set(block: (ImportState) -> ImportState) = withContext(Dispatchers.Main) {
        state = state?.let(block)
    }

    private fun run(source: String, body: suspend () -> Unit) {
        if (busy) return
        state = ImportState(source)
        job = App.scope.launch {
            try {
                body()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val code = (e as? ImportException)?.code ?: e.message?.take(120) ?: e.javaClass.simpleName
                Logs.add("import", "$source failed: ${e.message?.take(120)}")
                set { it.copy(stage = ImportStage.Failed, error = code) }
            }
        }
    }

    fun youtube(context: Context, link: String, asPlaylist: Boolean) {
        val ref = YouTubeClient.parse(link) ?: return
        val app = context.applicationContext
        run(YOUTUBE) {
            when (ref) {
                is YtRef.Video -> {
                    val (v, _) = withContext(Dispatchers.IO) { YouTubeImport.client.video(ref.id) }
                    val t = YouTubeImport.track(v)
                    set { it.copy(title = v.title, total = 1, done = 1, found = 1, stage = ImportStage.Saving) }
                    withContext(Dispatchers.Main) { Downloads.enqueue(app, listOf(t)) }
                    set { it.copy(stage = ImportStage.Done) }
                }
                is YtRef.Playlist -> {
                    val pl = withContext(Dispatchers.IO) {
                        YouTubeImport.client.playlist(ref.id) { n -> App.scope.launch(Dispatchers.Main) { state = state?.copy(done = n, total = n) } }
                    }
                    val tracks = pl.items.map { YouTubeImport.track(it) }
                    set { it.copy(title = pl.title, total = tracks.size, done = tracks.size, found = tracks.size, stage = ImportStage.Saving) }
                    withContext(Dispatchers.Main) {
                        if (asPlaylist) {
                            val p = LocalPlaylists.create(pl.title, true, tracks, localOnly = true)
                            Downloads.enqueue(app, tracks, p.id, p.title)
                        } else {
                            Downloads.enqueue(app, tracks, null, pl.title, "yt:" + ref.id)
                        }
                    }
                    set { it.copy(stage = ImportStage.Done, playlists = if (asPlaylist) 1 else 0) }
                }
            }
        }
    }

    fun spotify(context: Context, link: String, download: Boolean) {
        val ref = SpotifySource.parse(link) ?: return
        val app = context.applicationContext
        run(SPOTIFY) {
            val list = withContext(Dispatchers.IO) { SpotifySource(http).fetch(ref.first, ref.second) }
            matchAndSave(app, list, if (ref.first == "track") null else list.title, download)
        }
    }

    fun yandex(context: Context, link: String?, token: String?, download: Boolean) {
        val app = context.applicationContext
        val ref = link?.let { YandexSource.parse(it) }
        if (link != null && ref == null) return
        run(YANDEX) {
            val source = YandexSource(http, token)
            val list = withContext(Dispatchers.IO) {
                val progress: (Int, Int) -> Unit = { n, total -> App.scope.launch(Dispatchers.Main) { state = state?.copy(done = n, total = total) } }
                if (ref == null) source.likes(progress) else source.fetch(ref, progress)
            }
            matchAndSave(app, list, if (ref is YmRef.Track) null else list.title, download)
        }
    }

    private fun simplify(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()

    private fun uploadedBy(t: Track, artist: String): Boolean {
        val a = simplify(artist.substringBefore(',').substringBefore('&'))
        val u = simplify(t.user?.username.orEmpty())
        return a.isNotEmpty() && (u == a || (a.length >= 3 && u.contains(a)))
    }

    private fun closeDuration(t: Track, want: Long): Boolean {
        if (want <= 0L) return true
        val have = t.displayDuration
        if (have <= 0L) return true
        val diff = kotlin.math.abs(have - want)
        return diff <= 15_000L || diff <= want / 5
    }

    private suspend fun match(t: ExtTrack): Track? {
        val artist = t.artist.substringBefore(',').trim()
        val combined = runCatching { Api.searchTracks("$artist ${t.title}".trim(), 0, 3).collection }.getOrElse {
            delay(1_000)
            Api.searchTracks("$artist ${t.title}".trim(), 0, 3).collection
        }.filter { !it.hidden }
        combined.firstOrNull { closeDuration(it, t.durationMs) }?.let { return it }
        if (artist.isBlank() || t.title.isBlank()) return combined.firstOrNull()
        val byTitle = runCatching { Api.searchTracks(t.title, 0, 20).collection }.getOrDefault(emptyList())
        return byTitle.firstOrNull { uploadedBy(it, artist) && closeDuration(it, t.durationMs) } ?: combined.firstOrNull()
    }

    private suspend fun matchAndSave(app: Context, list: ExtList, playlistTitle: String?, download: Boolean) {
        set { it.copy(title = list.title, total = list.tracks.size, done = 0, stage = ImportStage.Matching) }
        val found = mutableListOf<Track>()
        val missed = mutableListOf<String>()
        var errorsInRow = 0
        for ((i, t) in list.tracks.withIndex()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val m = runCatching { match(t) }
                .onFailure { errorsInRow++ }
                .onSuccess { errorsInRow = 0 }
                .getOrNull()
            if (errorsInRow >= 15) throw ImportException("search", "search unavailable")
            if (m != null && found.none { it.urn == m.urn }) found += m else if (m == null) missed += "${t.artist} — ${t.title}"
            set { it.copy(done = i + 1, found = found.size, missed = missed.takeLast(200)) }
            delay(120)
        }
        if (found.isEmpty()) throw ImportException("nothing", "nothing found")
        set { it.copy(stage = ImportStage.Saving) }
        var playlists = 0
        withContext(Dispatchers.Main) {
            if (playlistTitle == null) {
                if (download) Downloads.enqueue(app, found)
            } else {
                found.chunked(CHUNK).forEachIndexed { idx, chunk ->
                    val name = if (idx == 0) playlistTitle else "$playlistTitle ${idx + 1}"
                    val p = LocalPlaylists.create(name, true, chunk)
                    playlists++
                    if (download) Downloads.enqueue(app, chunk, p.id, p.title)
                }
            }
        }
        Logs.add("import", "${list.title.take(30)}: ${found.size}/${list.tracks.size} found")
        set { it.copy(stage = ImportStage.Done, playlists = playlists) }
    }
}
