package com.scd.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

object FeedCache {
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var dir: File

    fun init(context: Context) {
        dir = File(context.filesDir, "feed").apply { mkdirs() }
    }

    private fun fileFor(key: String) = File(dir, "$key.json")

    fun load(key: String): List<Track> {
        if (!::dir.isInitialized) return emptyList()
        return runCatching {
            val f = fileFor(key)
            if (!f.exists()) return emptyList()
            json.decodeFromString(ListSerializer(Track.serializer()), f.readText())
        }.getOrDefault(emptyList())
    }

    suspend fun save(key: String, tracks: List<Track>) = withContext(Dispatchers.IO) {
        if (!::dir.isInitialized || tracks.isEmpty()) return@withContext
        runCatching {
            fileFor(key).writeText(
                json.encodeToString(ListSerializer(Track.serializer()), tracks.take(60)),
            )
        }
    }
}
