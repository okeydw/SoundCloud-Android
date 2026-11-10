package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LyricsFlowTest {
    @Test fun pendingIsPolledUntilTheGeneratedTextArrives() = runBlocking {
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = awaitLyrics(fetch = {
            if (++calls < 3) LyricsResponse(status = "pending")
            else LyricsResponse(status = "found", source = "self_gen", plainText = "Recognized words")
        }, pause = { waits += it })
        assertEquals(3, calls)
        assertEquals(listOf(5000L, 10000L), waits)
        assertTrue(result!!.hasText)
    }

    @Test fun pendingTimeoutRemainsPending() = runBlocking {
        var calls = 0
        val result = awaitLyrics(fetch = { calls++; LyricsResponse(status = "pending") }, pause = {}, attempts = 3)
        assertEquals(3, calls)
        assertEquals("pending", result?.status)
        assertFalse(result!!.hasText)
    }

    @Test fun networkFailureIsNotReportedAsNoLyrics() {
        assertThrows(java.io.IOException::class.java) {
            runBlocking { awaitLyrics(fetch = { throw java.io.IOException("offline") }) }
        }
    }

    @Test fun closingTheScreenCancelsPolling() {
        assertThrows(CancellationException::class.java) {
            runBlocking { awaitLyrics(fetch = { LyricsResponse(status = "pending") }, pause = { throw CancellationException() }) }
        }
    }

    @Test fun lrcSupportsRepeatedTagsOptionalFractionsOffsetsAndSortOrder() {
        val parsed = parseLrc("[offset:250]\n[ar:Artist]\n[01:02][00:03.5]Chorus\n[00:02:75]Earlier\n[00:00.1]Intro")
        assertEquals(listOf(0L, 2500L, 3250L, 61750L), parsed.map { it.timeMs })
        assertEquals(listOf("Intro", "Earlier", "Chorus", "Chorus"), parsed.map { it.text })
        assertEquals(1250L, parseLrc("[offset:-250]\n[00:01]Line").single().timeMs)
    }

    @Test fun blankSynchronizedLinesDoNotCountAsLyrics() {
        assertFalse(LyricsResponse(syncedLrc = "[00:01]\n[00:02]   ").hasText)
        assertTrue(LyricsResponse(syncedLrc = "[00:01]Words").hasText)
    }
}
