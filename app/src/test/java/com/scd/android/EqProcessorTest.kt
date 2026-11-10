package com.scd.android

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EqProcessorTest {
    @After fun resetEq() { EqLive.publish(false, emptyList(), 0.0) }

    @Test fun pcmFlowsAcrossFormatChangesAndEqToggles() {
        val processor = EqProcessor()
        for (rate in listOf(44100, 48000, 96000)) {
            for (enabled in listOf(false, true, false)) {
                EqLive.publish(enabled, List(10) { 12.0 }, null)
                processor.configure(AudioProcessor.AudioFormat(rate, 2, C.ENCODING_PCM_16BIT))
                processor.flush()
                val input = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder())
                repeat(2048) { input.putShort(if (it % 2 == 0) 12000 else -12000) }
                input.flip()
                processor.queueInput(input)
                assertFalse(input.hasRemaining())
                assertEquals(4096, processor.output.remaining())
                processor.queueEndOfStream()
                assertTrue(processor.isEnded)
            }
        }
        processor.reset()
    }

    @Test fun invalidFloatSamplesDoNotPoisonTheRestOfTheTrack() {
        for (enabled in listOf(false, true)) {
            EqLive.publish(enabled, List(10) { 6.0 }, -6.0)
            val p = EqProcessor()
            p.configure(AudioProcessor.AudioFormat(48000, 1, C.ENCODING_PCM_FLOAT)); p.flush()
            val input = ByteBuffer.allocateDirect(24).order(ByteOrder.nativeOrder())
            listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0.25f, 0.5f, 0.25f).forEach(input::putFloat)
            input.flip(); p.queueInput(input)
            val out = p.output.order(ByteOrder.nativeOrder())
            val samples = List(6) { out.float }
            assertTrue(samples.all { it.isFinite() })
            assertTrue(samples.drop(3).any { it != 0f })
            p.reset()
        }
    }
}
