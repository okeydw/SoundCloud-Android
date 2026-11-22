package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class StatsTimelineTest {
    @Test fun monthlyHistoryIncludesWholeBackendRange() {
        val data = ListeningStats(unit = "month", from = "2024-01-01", to = "2026-11-11",
            timeline = listOf(TimelinePoint("2024-01", 2, 100), TimelinePoint("2026-11", 3, 200)))
        val timeline = data.filledTimeline()
        assertEquals(35, timeline.size)
        assertEquals(300L, timeline.sumOf { it.listenedMs })
        assertEquals(0L, timeline[1].plays)
    }

    @Test fun pointsInSameMonthAreMergedWithoutLosingListeningTime() {
        val data = ListeningStats(unit = "month", timeline = listOf(
            TimelinePoint("2026-02-01", 2, 100), TimelinePoint("2026-02-15", 3, 200)))
        assertEquals(1, data.filledTimeline().size)
        assertEquals(5L, data.filledTimeline().single().plays)
        assertEquals(300L, data.filledTimeline().single().listenedMs)
    }
}
