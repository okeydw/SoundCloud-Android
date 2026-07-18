package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object LikedArtists {
    private lateinit var sp: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "artists"

    var artists by mutableStateOf<List<Artist>>(emptyList())
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("liked_artists", Context.MODE_PRIVATE)
        val raw = sp.getString(KEY, null)
        artists = if (raw == null) emptyList()
        else runCatching { json.decodeFromString(ListSerializer(Artist.serializer()), raw) }
            .getOrDefault(emptyList())
    }

    fun isLiked(urn: String) = artists.any { it.urn == urn }

    fun toggle(artist: Artist) {
        artists = if (isLiked(artist.urn)) {
            artists.filterNot { it.urn == artist.urn }
        } else {
            artists + Artist(urn = artist.urn, username = artist.username, avatar_url = artist.avatar_url)
        }
        runCatching {
            sp.edit().putString(KEY, json.encodeToString(ListSerializer(Artist.serializer()), artists)).apply()
        }
        PlaylistEvents.bump()
    }
}
