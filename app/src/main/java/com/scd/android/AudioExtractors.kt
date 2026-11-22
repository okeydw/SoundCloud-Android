package com.scd.android

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.ts.AdtsExtractor

@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioExtractors {
    const val FRAGMENTED_FLAGS = FragmentedMp4Extractor.FLAG_MERGE_FRAGMENTED_SIDX
    const val ADTS_FLAGS = AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING

    fun factory() = DefaultExtractorsFactory()
        .setAdtsExtractorFlags(ADTS_FLAGS)
        .setMp3ExtractorFlags(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING)
        .setFragmentedMp4ExtractorFlags(FRAGMENTED_FLAGS)
}
