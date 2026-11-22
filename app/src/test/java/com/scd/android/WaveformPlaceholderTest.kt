package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class WaveformPlaceholderTest {
    @Test fun youtubeShapeStaysTheSameAcrossRepeatedReads() {
        val urn = "youtube:tracks:abc123"
        val first = youtubeWaveform(urn)!!
        repeat(10) { assertEquals(first, youtubeWaveform(urn)) }
        assertEquals(96, first.size)
        assertTrue(first.all { it.isFinite() && it in 0f..1f })
        assertTrue(first.distinct().size > 1)
    }

    @Test fun aMissingSoundCloudWaveUsesTheExistingShapeWithoutCachingIt() {
        val urn = "soundcloud:tracks:123"
        assertEquals(fallbackWaveform(urn), WaveCache.initial(urn))
        assertEquals(WaveCache.initial(urn), WaveCache.initial(urn))
        assertNull(WaveCache.get(urn))
        assertTrue(WaveCache.initial(null).isEmpty())
    }

    @Test fun differentYouTubeTracksHaveTheirOwnStableShape() {
        assertNotEquals(youtubeWaveform("youtube:tracks:one"), youtubeWaveform("youtube:tracks:two"))
    }
}
