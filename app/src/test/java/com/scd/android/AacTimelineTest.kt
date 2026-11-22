package com.scd.android

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer

class AacTimelineTest {
    @Test fun startsAtZeroAndPreservesNormalPacketTiming() {
        val clock = AacTimeline(48_000)
        assertEquals(listOf(0L, 21_333L, 42_667L), listOf(90_000L, 111_333L, 132_667L).map(clock::next))
        assertEquals(0, clock.discontinuities)
    }

    @Test fun repairsFragmentResetsAndLargeGapsWithoutDroppingAudioPackets() {
        val clock = AacTimeline(48_000)
        assertEquals(listOf(0L, 21_333L, 42_666L, 63_999L, 85_332L), listOf(0L, 21_333L, 0L, 90_000_000L, 90_021_333L).map(clock::next))
        assertEquals(2, clock.discontinuities)
    }

    @Test fun keepsRealOrdinaryMp4AndDetectsTopLevelFragmentsOnly() {
        fun box(name: String, body: ByteArray = byteArrayOf()) = ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put(name.toByteArray()).put(body).array()
        val file = File.createTempFile("audio-container-", ".m4a")
        try {
            file.writeBytes(box("ftyp") + box("mdat", "moof".toByteArray()))
            assertFalse(AudioNormalize.fragmented(file))
            file.writeBytes(box("ftyp") + box("moof") + box("mdat"))
            assertTrue(AudioNormalize.fragmented(file))
            file.writeBytes(box("ftyp") + byteArrayOf(0, 0, 0, 1))
            assertFalse(AudioNormalize.fragmented(file))
        } finally { file.delete() }
    }
}
