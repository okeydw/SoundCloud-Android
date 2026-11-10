package com.scd.android

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class UpgradeSelectionTest {
    @Test fun freshDownloadsKeepSqUntilAHigherTierIsActuallyFound() {
        val dir = Files.createTempDirectory("download-quality").toFile()
        try {
            val source = dir.resolve("source")
            val pick = UpgradeSelection(null, dir.resolve("best"))
            source.writeText("SQ fallback")
            pick.consider(source, Quality.SQ, "sq")
            assertFalse(pick.enoughFor(Quality.UQ))
            assertFalse(pick.enoughFor(Quality.HQ))
            assertTrue(pick.enoughFor(Quality.SQ))
            source.writeText("UQ audio")
            pick.consider(source, Quality.UQ, "uq")
            assertEquals(Quality.UQ, pick.tier)
            assertEquals("UQ audio", pick.file.readText())
        } finally { dir.listFiles().orEmpty().forEach { it.delete() }; dir.delete() }
    }

    @Test fun aLowerResponseDoesNotHideUqOnTheNextMirror() {
        val dir = Files.createTempDirectory("quality-upgrade").toFile()
        try {
            val source = dir.resolve("source")
            val pick = UpgradeSelection(Quality.SQ, dir.resolve("best"))
            source.writeText("HQ audio")
            pick.consider(source, Quality.HQ, "first")
            assertFalse(pick.enoughFor(Quality.UQ))
            assertTrue(pick.enoughFor(Quality.HQ))
            source.writeText("UQ audio")
            pick.consider(source, Quality.UQ, "second")
            assertTrue(pick.enoughFor(Quality.UQ))
            assertEquals("UQ audio", pick.file.readText())
            assertEquals("second", pick.url)
        } finally { dir.listFiles().orEmpty().forEach { it.delete() }; dir.delete() }
    }

    @Test fun hqFallbackSurvivesFailedOrLowerLaterCandidates() {
        val dir = Files.createTempDirectory("quality-fallback").toFile()
        try {
            val source = dir.resolve("source")
            val pick = UpgradeSelection(Quality.SQ, dir.resolve("best"))
            source.writeText("HQ audio")
            pick.consider(source, Quality.HQ, "hq")
            source.writeText("SQ audio")
            pick.consider(source, Quality.SQ, "sq")
            source.delete()
            assertEquals(Quality.HQ, pick.tier)
            assertEquals("HQ audio", pick.file.readText())
            val alreadyHq = UpgradeSelection(Quality.HQ, dir.resolve("no-downgrade"))
            alreadyHq.consider(pick.file, Quality.HQ, "same")
            alreadyHq.consider(pick.file, Quality.SQ, "lower")
            assertNull(alreadyHq.tier)
            assertFalse(alreadyHq.file.exists())
        } finally { dir.listFiles().orEmpty().forEach { it.delete() }; dir.delete() }
    }
}
