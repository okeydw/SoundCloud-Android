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
    var isPlaying by mutableStateOf(false)
    var shuffle by mutableStateOf(false)
    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var openPlayerRequest by mutableStateOf(false)

    fun sync(c: MediaController) {
        val md = c.currentMediaItem?.mediaMetadata
        urn = c.currentMediaItem?.mediaId
        title = md?.title?.toString() ?: ""
        artist = md?.artist?.toString() ?: ""
        artistUrn = md?.extras?.getString("artist_urn")
        artworkUri = md?.artworkUri?.toString()
        waveformUrl = md?.extras?.getString("waveform_url")
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeatMode = c.repeatMode
    }
}

object PlaylistEvents {
    var version by mutableStateOf(0)
    fun bump() { version++ }
}

object NavEvents {
    var playlistUrn by mutableStateOf<String?>(null)
    var playlistTitle by mutableStateOf<String?>(null)

    fun openPlaylist(urn: String, title: String) {
        playlistTitle = title
        playlistUrn = urn
    }

    fun consume() {
        playlistUrn = null
        playlistTitle = null
    }
}
