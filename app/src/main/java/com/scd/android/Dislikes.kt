package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object Dislikes {
    var urns by mutableStateOf(setOf<String>())
        private set
    private var seeded = false

    suspend fun seed() {
        if (seeded) return
        runCatching { Api.dislikedIds() }.onSuccess { ids ->
            urns = ids.map {
                if (it.startsWith("soundcloud:tracks:")) it else "soundcloud:tracks:$it"
            }.toSet()
            seeded = true
        }
    }

    fun isDisliked(urn: String) = urn in urns

    fun clear(urn: String) {
        if (urn in urns) {
            urns = urns - urn
            App.scope.launch { runCatching { Api.undislike(urn) } }
        }
    }

    suspend fun toggle(track: Track): Boolean {
        val now = !isDisliked(track.urn)
        urns = if (now) urns + track.urn else urns - track.urn
        if (now && Likes.isLiked(track.urn)) Likes.clear(track.urn)
        return try {
            if (now) Api.dislike(track) else Api.undislike(track.urn)
            now
        } catch (e: Exception) {
            urns = if (now) urns - track.urn else urns + track.urn
            !now
        }
    }
}
