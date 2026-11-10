package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class AudioQualityPolicyTest {
    @Test fun storageCanUpgradeFromHqToUqAndStaleMetadataCannotUndoIt() {
        assertEquals(Quality.UQ, AudioQualityPolicy.bestKnown("hq", "hq", "uq"))
        assertEquals(Quality.UQ, AudioQualityPolicy.bestKnown("uq", "hq", "sq"))
        assertEquals(Quality.HQ, AudioQualityPolicy.bestKnown("sq", "hq"))
        assertNull(AudioQualityPolicy.bestKnown(null, "unknown"))
    }

    @Test fun anActualResponseOverridesTheRequestedTier() {
        assertEquals(Quality.SQ, AudioQualityPolicy.served("sq", "uq", Quality.UQ))
        assertEquals(Quality.HQ, AudioQualityPolicy.served("hq", requested = Quality.UQ))
        assertEquals(Quality.UQ, AudioQualityPolicy.served("uq", requested = Quality.HQ))
        assertEquals(Quality.UQ, AudioQualityPolicy.served(null, requested = Quality.UQ))
    }

    @Test fun legacyHighQualityChecksIncludeUq() {
        assertTrue(AudioQualityPolicy.isHigh("uq", null))
        assertTrue(AudioQualityPolicy.isHigh(null, "uq"))
        assertFalse(AudioQualityPolicy.isHigh("sq", "uq"))
        assertFalse(AudioQualityPolicy.isHigh(null, null))
    }
}
