package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class WaveformEnvelopeTest {
    @Test fun audioWithNoDeclaredDurationKeepsItsPeaksInPlaybackOrder() {
        val wave = WaveformEnvelope(4)
        repeat(4096) { wave.add(if (it in 2048 until 3072) -0.8 else 0.0) }
        assertEquals(listOf(0f, 0f, 1f, 0f), wave.finish())
    }

    @Test fun shorterLastBucketUsesItsActualSampleCount() {
        val wave = WaveformEnvelope(4)
        repeat(37) { wave.add(0.4) }
        assertEquals(List(4) { 1f }, wave.finish())
    }

    @Test fun distinguishesMissingAudioFromSilence() {
        val wave = WaveformEnvelope()
        assertNull(wave.finish())
        wave.add(Double.NaN)
        assertNull(wave.finish())
        wave.add(0.0)
        assertEquals(listOf(0f), wave.finish())
    }
}
