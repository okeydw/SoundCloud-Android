package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.session.MediaController

object NowPlaying {
    var urn by mutableStateOf<String?>(null)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artistUrn by mutableStateOf<String?>(null)
    var artworkUri by mutableStateOf<String?>(null)
    var waveformUrl by mutableStateOf<String?>(null)
    var genre by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var openPlayerRequest by mutableStateOf(false)
    var autoContinue by mutableStateOf(false)

    fun sync(c: MediaController) {
        val md = c.currentMediaItem?.mediaMetadata
        urn = c.currentMediaItem?.mediaId
        title = md?.title?.toString() ?: ""
        artist = md?.artist?.toString() ?: ""
        artistUrn = md?.extras?.getString("artist_urn")
        artworkUri = md?.artworkUri?.toString()
        waveformUrl = md?.extras?.getString("waveform_url")
        genre = md?.extras?.getString("genre")
        isPlaying = c.isPlaying
        shuffle = ShuffleQueue.on && !ShuffleQueue.isWave(urn)
        repeatMode = c.repeatMode
        WaveState.remember(urn, c.currentPosition)
    }
}

object PlaylistEvents {
    var version by mutableStateOf(0)
    fun bump() { version++ }
}

object NavEvents {
    var playlistUrn by mutableStateOf<String?>(null)
    var playlistTitle by mutableStateOf<String?>(null)
    var pendingUrl by mutableStateOf<String?>(null)

    fun openPlaylist(urn: String, title: String) {
        playlistTitle = title
        playlistUrn = urn
    }

    fun consume() {
        playlistUrn = null
        playlistTitle = null
    }

    var route by mutableStateOf<String?>(null)
        private set
    var userUrn by mutableStateOf<String?>(null)
        private set

    const val STATS = "stats"
    const val CATALOG = "catalog:"
    const val ALBUM = "album:"

    fun openCatalog(id: String) {
        route = CATALOG + id
    }

    fun openAlbum(id: String) {
        route = ALBUM + id
    }

    fun openStats() {
        route = STATS
    }

    fun openUser(urn: String) {
        userUrn = urn
    }

    fun openScUser(scId: String) {
        userUrn = "soundcloud:users:$scId"
    }

    fun consumeRoute() {
        route = null
    }

    fun consumeUser() {
        userUrn = null
    }

    fun openUrl(url: String) {
        pendingUrl = url
    }

    fun consumeUrl() {
        pendingUrl = null
    }
}
