package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadQualityTest {
    @Test fun explicitDownloadChoicesResolveIndependently() {
        assertEquals(Quality.SQ, DownloadQuality.wanted("sq", star = true))
        assertEquals(Quality.HQ, DownloadQuality.wanted("hq", star = true))
        assertEquals(Quality.UQ, DownloadQuality.wanted("uq", star = true))
        assertEquals(Quality.UQ, DownloadQuality.wanted("auto", star = true))
        assertEquals(Quality.HQ, DownloadQuality.wanted("auto", star = false))
        assertEquals(Quality.HQ, DownloadQuality.wanted("uq", star = false))
    }

    @Test fun existingSqDownloadsStaySqAfterMigration() {
        assertEquals("sq", DownloadQuality.restore(null, legacyHq = false))
        assertEquals("auto", DownloadQuality.restore(null, legacyHq = true))
        assertEquals("sq", DownloadQuality.restore("invalid", legacyHq = false))
    }

    @Test fun explicitChoiceSurvivesRestartRegardlessOfLegacyFlag() {
        for (quality in listOf("auto", "sq", "hq", "uq")) {
            assertEquals(quality, DownloadQuality.restore(quality, legacyHq = true))
            assertEquals(quality, DownloadQuality.restore(quality, legacyHq = false))
        }
    }
}
