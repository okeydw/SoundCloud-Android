package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    @Volatile
    private var version = 0

    fun snapshot(): Int = version

    fun replaceAll(tracks: List<Track>, since: Int) {
        if (since != version) {
            seed(tracks)
            return
        }
        val next = tracks.map { it.urn }.toSet()
        if (next != urns) {
            urns = next
            persist()
        }
    }

    fun reset() {
        urns = emptySet()
        persist()
    }

    fun isLiked(urn: String) = urn in urns

    fun clear(urn: String) {
        if (urn in urns) {
            version++
            urns = urns - urn
            persist()
            App.scope.launch { runCatching { Api.unlikeTrack(urn) } }
        }
    }

    suspend fun toggle(track: Track): Boolean = withContext(NonCancellable) {
        val nowLiked = !isLiked(track.urn)
        version++
        if (nowLiked) {
            WaveFeedback.positive(track.urn)
            Events.like(track.urn)
            LikesAutoCache.onLiked(track)
        }
        urns =if (nowLiked) urns + track.urn else urns - track.urn
        persist()
        if (nowLiked && Dislikes.isDisliked(track.urn)) Dislikes.clear(track.urn)
        try {
            if (nowLiked) Api.likeTrack(track) else Api.unlikeTrack(track.urn)
            nowLiked
        } catch (e: Exception) {
            urns = if (nowLiked) urns - track.urn else urns + track.urn
            persist()
            !nowLiked
        }
    }
}
