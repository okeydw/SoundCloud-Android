package com.scd.android

import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

internal object LibraryPages {
    enum class ListKind { LIKES, HISTORY }

    @Serializable
    data class Page(val tracks: List<Track>, val more: Boolean)

    var store: android.content.SharedPreferences? = null
    private val lock = Any()

    fun clear() = synchronized(lock) { store?.edit()?.clear()?.apply(); Unit }

    private val json = Json { ignoreUnknownKeys = true }

    private fun key(kind: ListKind, page: Int) = "library_${kind.name}_$page"

    suspend fun save(kind: ListKind, page: Int, tracks: List<Track>, more: Boolean, session: String?) = withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (session == null || session != Api.sessionId) return@withContext
        val payload = json.encodeToString(Page.serializer(), Page(tracks, more))
        synchronized(lock) {
            if (session == Api.sessionId) store?.edit()?.putString(key(kind, page), payload)?.apply()
        }
        Unit
    }

    suspend fun read(kind: ListKind, page: Int): Page? = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val session = Api.sessionId ?: return@withContext null
        val text = store?.getString(key(kind, page), null) ?: return@withContext null
        runCatching { json.decodeFromString(Page.serializer(), text) }.getOrNull()?.takeIf { session == Api.sessionId }
    }
}
