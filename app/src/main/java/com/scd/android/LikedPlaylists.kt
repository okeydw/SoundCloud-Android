package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object LikedPlaylists {
    var urns by mutableStateOf(setOf<String>())
        private set
    private var seeded = false

    suspend fun seed(force: Boolean = false) {
        if (seeded && !force) return
        runCatching { Api.likedPlaylists(fresh = true) }.onSuccess { res ->
            urns = res.collection.map { it.urn }.toSet()
            seeded = true
        }
    }

    fun isLiked(urn: String) = urn in urns

    suspend fun toggle(playlist: Playlist): Boolean {
        val now = !isLiked(playlist.urn)
        urns = if (now) urns + playlist.urn else urns - playlist.urn
        return try {
            if (now) Api.likePlaylist(playlist.urn) else Api.unlikePlaylist(playlist.urn)
            PlaylistEvents.bump()
            now
        } catch (_: Exception) {
            urns = if (now) urns - playlist.urn else urns + playlist.urn
            !now
        }
    }
}
