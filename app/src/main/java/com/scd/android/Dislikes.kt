package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object Dislikes {
    var urns by mutableStateOf(setOf<String>())
        private set
    private var seeded = false

    private var sp: SharedPreferences? = null
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val known = java.util.concurrent.ConcurrentHashMap<String, Track>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("disliked_tracks", Context.MODE_PRIVATE)
        sp = prefs
        runCatching {
            prefs.getString("tracks", null)?.let {
                known.putAll(json.decodeFromString(MapSerializer(String.serializer(), Track.serializer()), it))
            }
        }
    }

    private fun persistKnown() {
        val prefs = sp ?: return
        runCatching {
            val snapshot = known.filterKeys { it in urns }
            prefs.edit()
                .putString("tracks", json.encodeToString(MapSerializer(String.serializer(), Track.serializer()), snapshot))
                .apply()
        }
    }

    private fun keep(track: Track) {
        if (track.urn.isEmpty()) return
        if (track.title.isEmpty()) return
        known[track.urn] = track
        persistKnown()
    }

    suspend fun seed() {
        if (seeded) return
        runCatching { Api.dislikedIds() }.onSuccess { ids ->
            urns = ids.map {
                if (it.startsWith("soundcloud:tracks:")) it else "soundcloud:tracks:$it"
            }.toSet()
            seeded = true
        }
    }

    fun reset() {
        urns = emptySet()
        seeded = false
        known.clear()
        runCatching { sp?.edit()?.clear()?.apply() }
    }

    fun isDisliked(urn: String) = urn in urns

    fun clear(urn: String) {
        if (urn in urns) {
            urns = urns - urn
            App.scope.launch { runCatching { Api.undislike(urn) } }
        }
    }

    suspend fun toggle(track: Track): Boolean = withContext(NonCancellable) {
        val now = !isDisliked(track.urn)
        urns = if (now) urns + track.urn else urns - track.urn
        if (now) {
            keep(track)
            WaveFeedback.negative(track.urn)
            Events.dislike(track.urn)
        }
        if (now && Likes.isLiked(track.urn)) Likes.clear(track.urn)
        try {
            if (now) Api.dislike(track) else Api.undislike(track.urn)
            now
        } catch (e: Exception) {
            urns = if (now) urns - track.urn else urns + track.urn
            !now
        }
    }

    suspend fun page(page: Int, size: Int = 30): Pair<List<Track>, Boolean> {
        if (page == 0) {
            seeded = false
            seed()
        }
        val all = urns.toList().sortedByDescending { known.containsKey(it) }
        val slice = all.drop(page * size).take(size)
        val gate = Semaphore(4)
        val tracks = coroutineScope {
            slice.map { urn ->
                async {
                    known[urn] ?: gate.withPermit {
                        kotlinx.coroutines.withTimeoutOrNull(8_000) {
                            runCatching { Api.trackByUrn(urn) }.getOrNull()
                        }
                    }?.also { keep(it) } ?: Track(urn = urn, title = urn.substringAfterLast(':'))
                }
            }.awaitAll()
        }
        return tracks to ((page + 1) * size < all.size)
    }
}
