package com.scd.android

import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

class StreamPreviewTest {
    @Test fun aPreviewNeverBecomesFullAfterRepeatedAttempts() {
        repeat(5) {
            assertNotNull(AudioProbe.shortReason(AudioInfo(true, 10_000), 400_000, 154_000))
            assertNotNull(AudioProbe.shortReason(AudioInfo(true, 30_000), 800_000, 154_000))
        }
    }

    @Test fun firstSegmentIndexDoesNotOverrideTheCompleteSampleTimeline() {
        assertNull(AudioProbe.shortReason(AudioInfo(true, 154_000, declaredMs = 10_000), 4_000_000, 154_000))
        assertNull(AudioProbe.shortReason(AudioInfo(true, 153_950, declaredMs = 154_000), 4_000_000, 154_000))
        assertNull(AudioProbe.shortReason(AudioInfo(true, 10_000), 100_000, 10_000))
        assertNotNull(AudioProbe.shortReason(AudioInfo(true, 10_000, declaredMs = 154_000), 400_000, 154_000))
        assertNotNull(AudioProbe.shortReason(AudioInfo(true, null, declaredMs = 10_000), 400_000, 154_000))
    }

    @Test fun hlsDurationUsesAllSegmentsAndHandlesMissingOrInvalidDurations() {
        assertEquals(154_000L, HlsDownload.durationMs("#EXTM3U\n#EXTINF:100.25,\na\n#EXTINF:53.75,\nb"))
        assertNull(HlsDownload.durationMs("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100\na"))
        assertNull(HlsDownload.durationMs("#EXTM3U\n#EXTINF:NaN,\na"))
    }

    @Test fun shortHlsIsRejectedBeforeDownloadingSegments() {
        var calls = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            calls++
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("ok")
                .body("#EXTM3U\n#EXTINF:10.0,\nsegment.m4a\n#EXT-X-ENDLIST".toResponseBody()).build()
        }.build()
        val target = File.createTempFile("preview", ".m4a")
        try {
            val error = assertThrows(IOException::class.java) {
                HlsDownload.writeTo(http, "https://cdn.example/preview.m3u8", target, 154_000)
            }
            assertTrue(error.message.orEmpty().contains("10s of 154s"))
            assertEquals(1, calls)
            assertEquals(0L, target.length())
        } finally { target.delete() }
    }

    @Test fun downloadSelectionKeepsAlternativesAndUsesDesktopTierOrder() {
        val source = ScDataSource(OkHttpClient())
        val candidates = Api.parseDownload("""{"candidates":[
            {"kind":"hls","quality":"hq","manifest_url":"https://cdn.example/preview.m3u8"},
            {"kind":"hls","quality":"sq","manifest_url":"https://cdn.example/full.m3u8"},
            {"kind":"progressive","quality":"hq","url":"https://cdn.example/full.m4a"},
            {"kind":"hls","quality":"lq","manifest_url":"https://cdn.example/low.m3u8"}
        ]}""")!!.candidates
        assertEquals(listOf("sq", "hq", "hq"), source.orderDownloadCandidates(candidates, Quality.SQ).map { it.quality })
        assertEquals(listOf("progressive", "hls"), source.orderDownloadCandidates(candidates, Quality.HQ).map { it.kind })
        assertTrue(source.orderDownloadCandidates(candidates, Quality.UQ).isEmpty())
    }
}
