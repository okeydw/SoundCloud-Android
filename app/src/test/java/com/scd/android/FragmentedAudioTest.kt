package com.scd.android

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class FragmentedAudioTest {
    private fun u32(n: Int) = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())
    private fun box(type: String, bytes: ByteArray) = u32(bytes.size + 8) + type.toByteArray() + bytes
    private fun segment(start: Int, duration: Int): ByteArray {
        val data = box("free", ByteArray(8))
        return box("sidx", u32(0) + u32(1) + u32(1000) + u32(start) + u32(0) +
            u32(1) + u32(data.size) + u32(duration) + u32(0x90000000.toInt())) + data
    }

    private fun scan(bytes: ByteArray, extractor: Extractor): SeekMap {
        var map: SeekMap? = null
        extractor.init(object : ExtractorOutput {
            override fun track(id: Int, type: Int): TrackOutput = error("index-only fixture")
            override fun endTracks() = Unit
            override fun seekMap(seekMap: SeekMap) { map = seekMap }
        })
        fun input(position: Long): DefaultExtractorInput {
            var cursor = position.toInt()
            return DefaultExtractorInput({ target, offset, length ->
                val count = minOf(length, bytes.size - cursor)
                if (count == 0) C.RESULT_END_OF_INPUT else {
                    bytes.copyInto(target, offset, cursor, cursor + count)
                    cursor += count
                    count
                }
            }, position, bytes.size.toLong())
        }
        var source = input(0)
        val seek = PositionHolder()
        try {
            repeat(100) {
                when (extractor.read(source, seek)) {
                    Extractor.RESULT_END_OF_INPUT -> return requireNotNull(map)
                    Extractor.RESULT_SEEK -> source = input(seek.position)
                }
            }
            error("extractor never reached EOF")
        } finally { extractor.release() }
    }

    @Test fun combinesFragmentIndexesIntoTheFullDurationAndSeekRange() {
        val first = segment(0, 10_000)
        val full = first + segment(10_000, 111_713)
        assertEquals(10_000_000L, scan(full, FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED)).durationUs)
        val extractor = FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED, AudioExtractors.FRAGMENTED_FLAGS)
        val result = scan(full, extractor)
        assertEquals(121_713_000L, result.durationUs)
        assertTrue(result.isSeekable)
        assertTrue(result.getSeekPoints(90_000_000L).first.position >= first.size)
    }

    @Test fun aSingleTenSecondFragmentRemainsAPreview() {
        val extractor = FragmentedMp4Extractor(SubtitleParser.Factory.UNSUPPORTED, AudioExtractors.FRAGMENTED_FLAGS)
        assertEquals(10_000_000L, scan(segment(0, 10_000), extractor).durationUs)
    }
}
