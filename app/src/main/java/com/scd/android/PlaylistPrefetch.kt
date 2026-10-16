package com.scd.android

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object PlaylistPrefetch {
    private const val STALE_MS = 12L * 60 * 60 * 1000
    private const val MIN_GAP_MS = 30L * 60 * 1000
    private const val MAX_PAGES = 40

    private var appContext: Context? = null
    private var lastRun = 0L

    @Volatile
    private var job: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun run(context: Context? = appContext) {
        val ctx = context?.applicationContext ?: return
        val now = System.currentTimeMillis()
        if (Prefs.offline || Api.sessionId == null || job?.isActive == true || now - lastRun < MIN_GAP_MS) return
        if (Prefs.prefetchWifiOnly && NetMonitor.isMetered(ctx)) return
        lastRun = now
        job = App.scope.launch {
            var own = 0
            var liked = 0
            for (p in LocalPlaylists.visible.filter { LocalPlaylists.pullable(it) && it.ops.isEmpty() && !it.tracksLoaded }) {
                if (Prefs.offline) break
                if (attempt { LocalPlaylists.pull(p.id) }) own++
                delay(250)
            }
            for (pl in likedPlaylists()) {
                if (Prefs.offline) break
                if (LocalPlaylists.owns(pl.urn) || PlaylistSnapshots.isFresh(pl.urn, STALE_MS)) continue
                if (attempt { save(pl.urn) }) liked++
                delay(250)
            }
            if (own + liked > 0) Logs.add("playlist", "offline prefetch: $own own, $liked liked playlists saved")
        }
    }

    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    private suspend fun likedPlaylists(): List<Playlist> {
        val out = mutableListOf<Playlist>()
        var page = 0
        while (page < MAX_PAGES) {
            val res = try {
                Api.likedPlaylists(page, 50, fresh = page == 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
            out += res.collection
            if (!res.has_more || res.collection.isEmpty()) break
            page++
        }
        return out.distinctBy { it.urn }
    }

    private suspend fun save(urn: String) {
        var page = 0
        while (page < MAX_PAGES) {
            val res = Api.playlistTracks(urn, page, fresh = true)
            val last = !res.has_more || res.collection.isEmpty() || page == MAX_PAGES - 1
            PlaylistSnapshots.record(urn, page, res.collection, !last)
            if (last) break
            page++
        }
    }
}
