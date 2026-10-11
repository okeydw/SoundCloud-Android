package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.File

@Serializable
data class Shelf(val id: String, val tracks: List<Track>)

object Recommendations {
    val ORDER = listOf("for_you", "top_artists", "fresh_drops", "same_vibe", "adjacent", "deep_cuts", "discover")
    const val FALLBACK = "tiles"

    private val json = Json { ignoreUnknownKeys = true }
    private var file: File? = null

    var shelves by mutableStateOf<List<Shelf>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    @Volatile
    private var loadedAt = 0L

    val stale: Boolean get() = shelves.isEmpty() || System.currentTimeMillis() - loadedAt > 10 * 60_000L

    fun init(context: Context) {
        file = File(File(context.filesDir, "feed").apply { mkdirs() }, "shelves.json")
        shelves = runCatching {
            json.decodeFromString(ListSerializer(Shelf.serializer()), file!!.readText())
        }.getOrDefault(emptyList())
    }

    fun reset() {
        shelves = emptyList()
        runCatching { file?.delete() }
    }

    fun refresh() {
        if (loading || Prefs.offline) return
        loading = true
        App.scope.launch {
            try {
                val loaded = runCatching { load(Prefs.waveFresh) }.getOrElse {
                    if (it is kotlinx.coroutines.CancellationException) throw it
                    Logs.add("recs", "unavailable: ${it.javaClass.simpleName} ${it.message?.take(80) ?: ""}")
                    null
                }
                val next = loaded?.takeIf { it.isNotEmpty() } ?: fallback()
                if (next.isNotEmpty()) {
                    loadedAt = System.currentTimeMillis()
                    withContext(Dispatchers.Main) { shelves = next }
                    runCatching { file?.writeText(json.encodeToString(ListSerializer(Shelf.serializer()), next)) }
                }
            } finally {
                loading = false
            }
        }
    }

    private fun fallback(): List<Shelf> {
        val tiles = Tiles.items.filter(Playback::playable)
        return if (tiles.isEmpty()) emptyList() else listOf(Shelf(FALLBACK, tiles.take(20)))
    }

    private fun idsOf(obj: JsonObject): List<String> {
        val urns = (obj["track_urns"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val raw = urns ?: (obj["track_ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return raw.map { it.substringAfterLast(':') }.filter { it.isNotEmpty() }
    }

    private suspend fun load(hideListened: Boolean): List<Shelf> {
        val root = Api.recommendations(hideListened)
        val clusters = ((root as? JsonObject)?.get("clusters") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
            .mapNotNull { obj ->
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (id !in ORDER) return@mapNotNull null
                val inline = (obj["tracks"] as? JsonArray)?.mapNotNull { el ->
                    runCatching { json.decodeFromJsonElement(Track.serializer(), el) }.getOrNull()
                }
                Triple(id, idsOf(obj), inline)
            }
        if (clusters.isEmpty()) return emptyList()
        val needed = clusters.filter { it.third == null }.flatMap { it.second }.distinct()
        val byId = hydrate(needed)
        val shelves = clusters.mapNotNull { (id, ids, inline) ->
            val tracks = inline ?: ids.mapNotNull { byId[it] }
            val usable = tracks.filter { Playback.playable(it) && !it.hidden }.distinctBy { it.urn }
            if (usable.isEmpty()) null else Shelf(id, usable)
        }
        Logs.add("recs", shelves.joinToString(", ") { "${it.id} ${it.tracks.size}" })
        return shelves.sortedBy { ORDER.indexOf(it.id) }
    }

    private suspend fun hydrate(ids: List<String>): Map<String, Track> {
        if (ids.isEmpty()) return emptyMap()
        val batch = Api.tracksByIds(ids)
        if (batch != null) return batch.associateBy { it.urn.substringAfterLast(':') }
        val gate = Semaphore(4)
        return coroutineScope {
            ids.map { id ->
                async {
                    gate.withPermit { runCatching { Api.trackByUrn("soundcloud:tracks:$id") }.getOrNull() }
                }
            }.awaitAll().filterNotNull().associateBy { it.urn.substringAfterLast(':') }
        }
    }
}
