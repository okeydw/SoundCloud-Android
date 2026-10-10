package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PureLogicTest {

    @Test
    fun parseLrcReadsTimestampsAndSkipsEmptyLines() {
        val lines = parseLrc(
            """
            [ar:Someone]
            [00:01.50] first
            [00:02.00]
            [01:02.345] second
            """.trimIndent(),
        )
        assertEquals(2, lines.size)
        assertEquals(1_500L, lines[0].timeMs)
        assertEquals("first", lines[0].text)
        assertEquals(62_345L, lines[1].timeMs)
    }

    @Test
    fun newerVersionComparesNumerically() {
        assertTrue(isNewerVersion("v0.4.10", "0.4.9"))
        assertTrue(isNewerVersion("1.0.0", "0.9.9"))
        assertFalse(isNewerVersion("v0.4.3", "0.4.3"))
        assertFalse(isNewerVersion("0.4.2", "0.4.3"))
        assertTrue(isNewerVersion("0.5", "0.4.9"))
    }

    @Test
    fun shareUrlDropsTrackingParams() {
        assertEquals(
            "https://soundcloud.com/a/b",
            Api.shareUrl("https://soundcloud.com/a/b?si=123&utm_source=x#t=1"),
        )
        assertEquals(
            "https://soundcloud.com/a/b?secret=1",
            Api.shareUrl("https://soundcloud.com/a/b?secret=1&ref=clipboard"),
        )
    }

    @Test
    fun cacheKeyIgnoresSessionAndKeepsHq() {
        val plain = MediaCache.keyOf("https://stream.scnative.space/stream/soundcloud%3Atracks%3A42?session_id=abc")
        val hq = MediaCache.keyOf("https://stream-star.scnative.space/stream/soundcloud%3Atracks%3A42?hq=true&session_id=abc")
        assertEquals("soundcloud:tracks:42", plain)
        assertEquals("soundcloud:tracks:42|hq", hq)
        assertEquals(
            "https://example.com/a?x=1",
            MediaCache.keyOf("https://example.com/a?session_id=zzz&x=1"),
        )
    }

    @Test
    fun authPagesAreDetected() {
        assertTrue(isAuthPage("evil.example", "/"))
        assertFalse(isAuthPage("soundcloud.com", "/someone/track"))
    }

    @Test
    fun sessionOnlyGoesToTrustedHosts() {
        assertTrue(Endpoints.trustedUrl("https://api.scnative.space/me"))
        assertTrue(Endpoints.trustedUrl("https://stream.r2.relay.scnative.space/stream/x"))
        assertFalse(Endpoints.trustedUrl("https://api.scdinternal.site/me"))
        assertFalse(Endpoints.trustedUrl("https://scnative.space.evil.com/stream/x"))
        assertFalse(Endpoints.trustedUrl("https://user@evil.com/stream/x"))
        assertFalse(Endpoints.trustedUrl("http://api.scnative.space/me"))
    }
}
