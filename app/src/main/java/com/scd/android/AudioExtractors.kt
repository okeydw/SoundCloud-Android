package com.scd.android

import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.mp4.FragmentedMp4Extractor

@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioExtractors {
    const val FRAGMENTED_FLAGS = FragmentedMp4Extractor.FLAG_MERGE_FRAGMENTED_SIDX

    fun factory() = DefaultExtractorsFactory().setFragmentedMp4ExtractorFlags(FRAGMENTED_FLAGS)
}
