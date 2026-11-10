package com.scd.android

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityLadderTest {
    @Test fun manualUpgradeAsksForTheBestAccessibleTier() {
        assertEquals(Quality.UQ, QualityLadder.upgradeWanted(star = true))
        assertEquals(Quality.HQ, QualityLadder.upgradeWanted(star = false))
        assertEquals(listOf(Quality.UQ, Quality.HQ), QualityLadder.upperTiers(QualityLadder.upgradeWanted(true)))
    }

    @After
    fun reset() = QualityLadder.forgetRefusal()

    @Test
    fun theLadderStartsAtTheAskedTierAndStopsAboveTheFloor() {
        assertEquals(listOf(Quality.UQ, Quality.HQ), QualityLadder.upperTiers(Quality.UQ, Quality.SQ))
        assertEquals(listOf(Quality.HQ), QualityLadder.upperTiers(Quality.HQ, Quality.SQ))
        assertTrue(QualityLadder.upperTiers(Quality.SQ, Quality.SQ).isEmpty())
        assertEquals(listOf(Quality.UQ), QualityLadder.upperTiers(Quality.UQ, Quality.HQ))
        assertTrue(QualityLadder.upperTiers(Quality.HQ, Quality.HQ).isEmpty())
    }

    @Test
    fun aTierUrlNamesItsTierAndKeepsOtherParams() {
        val base = "https://api.example/stream/soundcloud%3Atracks%3A1"
        assertEquals("$base?hq=true&quality=hq", QualityLadder.tierUrl(base, Quality.HQ))
        assertEquals("$base?quality=sq", QualityLadder.tierUrl("$base?hq=true", Quality.SQ))
        assertEquals("$base?a=1&hq=true&quality=uq", QualityLadder.tierUrl("$base?a=1&quality=sq", Quality.UQ))
        assertEquals(Quality.UQ, QualityLadder.tierOf("$base?hq=true&quality=uq"))
        assertEquals(Quality.HQ, QualityLadder.tierOf("$base?hq=true"))
        assertEquals(Quality.SQ, QualityLadder.tierOf(base))
    }

    @Test
    fun aRefusedTierDropsTheAskOneStepBelowIt() {
        QualityLadder.noteRefused(Quality.HQ, QualityLadder.REFUSAL_TTL_MS, "s1")
        assertEquals(Quality.SQ, QualityLadder.allowed(Quality.HQ, "s1"))
        assertEquals(Quality.SQ, QualityLadder.allowed(Quality.UQ, "s1"))
        assertEquals(Quality.HQ, QualityLadder.allowed(Quality.HQ, "s2"))
        assertEquals(Quality.HQ, QualityLadder.allowed(Quality.HQ, null))
    }

    @Test
    fun theLowestRefusalWinsAndSqIsNeverRefused() {
        QualityLadder.noteRefused(Quality.SQ, QualityLadder.REFUSAL_TTL_MS, "s1")
        assertEquals(Quality.HQ, QualityLadder.allowed(Quality.HQ, "s1"))
        QualityLadder.noteRefused(Quality.UQ, QualityLadder.REFUSAL_TTL_MS, "s1")
        assertEquals(Quality.HQ, QualityLadder.allowed(Quality.UQ, "s1"))
        QualityLadder.noteRefused(Quality.HQ, QualityLadder.REFUSAL_TTL_MS, "s1")
        assertEquals(Quality.SQ, QualityLadder.allowed(Quality.UQ, "s1"))
        QualityLadder.forgetRefusal()
        assertEquals(Quality.UQ, QualityLadder.allowed(Quality.UQ, "s1"))
    }

    @Test
    fun onlyForbiddenAndTimedBusyAnswersAreRefusals() {
        assertEquals(QualityLadder.REFUSAL_TTL_MS, QualityLadder.refusalHold(403, null))
        assertEquals(30_000L, QualityLadder.refusalHold(429, "30"))
        assertEquals(null, QualityLadder.refusalHold(429, null))
        assertEquals(null, QualityLadder.refusalHold(404, null))
    }

    @Test
    fun aTierIsRefusedOnlyWhenEverySourceRefusedIt() {
        val mixed = QualityLadder.Verdict()
        mixed.asked(Quality.HQ)
        mixed.asked(Quality.HQ)
        mixed.answered(Quality.HQ, QualityLadder.REFUSAL_TTL_MS)
        mixed.answered(Quality.HQ, null)
        mixed.settle()
        assertFalse(QualityLadder.refuses(Quality.HQ, null))

        val both = QualityLadder.Verdict()
        both.asked(Quality.HQ)
        both.asked(Quality.HQ)
        both.answered(Quality.HQ, 60_000L)
        both.answered(Quality.HQ, QualityLadder.REFUSAL_TTL_MS)
        both.settle()
        assertTrue(QualityLadder.refuses(Quality.HQ, null))
    }

    @Test
    fun aStillPendingOrServedTierIsNotRefused() {
        val pending = QualityLadder.Verdict()
        pending.asked(Quality.UQ)
        pending.asked(Quality.UQ)
        pending.answered(Quality.UQ, QualityLadder.REFUSAL_TTL_MS)
        pending.settle()
        assertFalse(QualityLadder.refuses(Quality.UQ, null))

        val served = QualityLadder.Verdict()
        served.asked(Quality.UQ)
        served.answered(Quality.UQ, QualityLadder.REFUSAL_TTL_MS)
        served.served(Quality.UQ)
        served.settle()
        assertFalse(QualityLadder.refuses(Quality.UQ, null))
    }

    @Test
    fun onlyAShortFileIsBadAgainstTheApiLength() {
        assertTrue(TierSources.durationOk(200_000L, 200_000L))
        assertTrue(TierSources.durationOk(400_000L, 30_000L))
        assertTrue(TierSources.durationOk(193_000L, 200_000L))
        assertFalse(TierSources.durationOk(30_000L, 200_000L))
    }
}
