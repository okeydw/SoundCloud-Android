package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object Likes {
    private var sp: SharedPreferences? = null

    var urns by mutableStateOf(setOf<String>())
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("liked_tracks", Context.MODE_PRIVATE)
        urns = sp?.getStringSet("urns", emptySet())?.toSet() ?: emptySet()
    }

    private fun persist() {
        sp?.edit()?.putStringSet("urns", urns)?.apply()
    }

    fun seed(tracks: List<Track>) {
        val next = urns + tracks.map { it.urn }
        if (next != urns) {
            urns = next
            persist()
        }
    }

    fun isLiked(urn: String) = urn in urns

    fun clear(urn: String) {
        if (urn in urns) {
            urns = urns - urn
            persist()
            App.scope.launch { runCatching { Api.unlikeTrack(urn) } }
        }
    }

    suspend fun toggle(track: Track): Boolean {
        val nowLiked = !isLiked(track.urn)
        urns = if (nowLiked) urns + track.urn else urns - track.urn
        persist()
        if (nowLiked && Dislikes.isDisliked(track.urn)) Dislikes.clear(track.urn)
        return try {
            if (nowLiked) Api.likeTrack(track) else Api.unlikeTrack(track.urn)
            nowLiked
        } catch (e: Exception) {
            urns = if (nowLiked) urns - track.urn else urns + track.urn
            persist()
            !nowLiked
        }
    }
}
