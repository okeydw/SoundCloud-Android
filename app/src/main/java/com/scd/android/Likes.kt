package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object Likes {
    var urns by mutableStateOf(setOf<String>())
        private set

    fun seed(tracks: List<Track>) {
        urns = urns + tracks.map { it.urn }
    }

    fun isLiked(urn: String) = urn in urns

    fun clear(urn: String) {
        if (urn in urns) {
            urns = urns - urn
            App.scope.launch { runCatching { Api.unlikeTrack(urn) } }
        }
    }

    suspend fun toggle(track: Track): Boolean {
        val nowLiked = !isLiked(track.urn)
        urns = if (nowLiked) urns + track.urn else urns - track.urn
        if (nowLiked && Dislikes.isDisliked(track.urn)) Dislikes.clear(track.urn)
        return try {
            if (nowLiked) Api.likeTrack(track) else Api.unlikeTrack(track.urn)
            nowLiked
        } catch (e: Exception) {
            urns = if (nowLiked) urns - track.urn else urns + track.urn
            !nowLiked
        }
    }
}
