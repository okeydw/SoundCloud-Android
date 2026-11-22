package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class PlaybackAccessTest {
    @Test fun previewMetadataDoesNotPreventTryingFullAudio() {
        val track = Track(urn = "soundcloud:tracks:123", access = "preview", duration = 10_000, full_duration = 154_000)
        assertTrue(track.goPlus)
        assertTrue(Playback.playable(track))
        assertFalse(track.unavailable)
    }

    @Test fun blockedMetadataDoesNotPreventTryingAvailableSources() {
        val track = Track(urn = "soundcloud:tracks:123", access = "blocked")
        assertTrue(track.unavailable)
        assertTrue(Playback.playable(track))
        assertTrue(Prefs.playBlocked)
    }
}
