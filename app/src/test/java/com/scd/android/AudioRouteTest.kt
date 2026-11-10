package com.scd.android

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import java.net.UnknownHostException

class AudioRouteTest {
    private fun reply(request: Request, code: Int = 200, location: String? = null): Response =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body("audio".toResponseBody()).apply { location?.let { header("Location", it) } }.build()

    private fun client(report: (String) -> Unit = {}, answer: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .addInterceptor(AudioRoute({ AudioRoute.plan(it, listOf("r1", "r2"), "r2") }, report))
            .addInterceptor(Interceptor { answer(it.request()) }).build()

    @Test fun ticketRedirectUsesRelayAndPreservesTicketRangeAndSession() {
        val requests = mutableListOf<Request>()
        val logs = mutableListOf<String>()
        val http = client(logs::add) { request ->
            requests += request
            if (request.url.host.startsWith("api-star.")) {
                reply(request.newBuilder().header("Host", request.url.host).build(), 307,
                    "https://stream-star.scnative.dev/stream/urn?ticket=a%2Bb%2Fc&quality=uq")
            } else reply(request)
        }
        val req = Request.Builder().url("https://api-star.scnative.dev/tracks/urn/stream?quality=uq")
            .header("x-session-id", "private-session").header("Range", "bytes=120-").build()
        http.newCall(req).execute().use { assertEquals(200, it.code) }
        assertEquals(listOf("api-star.r2.relay.scnative.dev", "stream-star.r2.relay.scnative.dev"), requests.map { it.url.host })
        val stream = requests.last()
        assertEquals("a+b/c", stream.url.queryParameter("ticket"))
        assertEquals("ticket=a%2Bb%2Fc&quality=uq", stream.url.encodedQuery)
        assertEquals("bytes=120-", stream.header("Range"))
        assertEquals("private-session", stream.header("x-session-id"))
        assertNull(stream.header("Host"))
        assertFalse(logs.any { "a%2B" in it || "private-session" in it })
    }

    @Test fun dnsFailureTriesAnotherRelayForSameService() {
        val seen = mutableListOf<String>()
        val http = client { request ->
            seen += request.url.host
            if (".r2." in request.url.host) throw UnknownHostException("fixture")
            reply(request)
        }
        http.newCall(Request.Builder().url("https://stream.scnative.dev/download/urn").build())
            .execute().use { assertEquals(200, it.code) }
        assertEquals(listOf("stream.r2.relay.scnative.dev", "stream.r1.relay.scnative.dev"), seen)
    }

    @Test fun expiredStarAccessFallsBackToTheRegularApi() {
        for (post in listOf(false, true)) {
            val seen = mutableListOf<String>()
            val http = client { request ->
                seen += request.url.host
                reply(request, if (request.url.host.startsWith("api-star.")) 403 else 200)
            }
            val request = Request.Builder().url("https://api-star.scnative.dev/recommendations/wave/feedback")
                .apply { if (post) post("{}".toRequestBody()) }.build()
            http.newCall(request).execute().use { assertEquals(200, it.code) }
            assertEquals(listOf("api-star.r2.relay.scnative.dev", "api-star.r1.relay.scnative.dev", "api.r2.relay.scnative.dev"), seen)
        }
    }

    @Test fun serverFailureTriesNextRelayButAccessDenialIsReturned() {
        for (code in listOf(503, 401, 403, 429)) {
            var calls = 0
            val http = client { request -> calls++; reply(request, if (calls == 1) code else 200) }
            http.newCall(Request.Builder().url("https://stream-star.scnative.dev/download/urn").build()).execute().use {
                assertEquals(if (code == 503) 200 else code, it.code)
            }
            assertEquals(if (code == 503) 2 else 1, calls)
        }
    }

    @Test fun externalRedirectDropsCredentialsAndKeepsByteRange() {
        val requests = mutableListOf<Request>()
        val http = client { request ->
            requests += request
            if (requests.size == 1) reply(request, 302, "https://cdn.example/audio?session_id=secret&sig=signed")
            else reply(request)
        }
        http.newCall(Request.Builder().url("https://storage-star.scnative.dev/track.m4a")
            .header("x-session-id", "secret").header("Authorization", "Bearer secret")
            .header("Cookie", "private=1").header("Range", "bytes=5-").build()).execute().close()
        val external = requests.last()
        assertEquals("cdn.example", external.url.host)
        assertNull(external.url.queryParameter("session_id"))
        assertNull(external.header("x-session-id"))
        assertNull(external.header("Authorization"))
        assertNull(external.header("Cookie"))
        assertEquals("signed", external.url.queryParameter("sig"))
        assertEquals("bytes=5-", external.header("Range"))
    }

    @Test fun foreignAndUnknownHostsAreNotRewritten() {
        for (url in listOf("https://scnative.dev.evil.example/stream/u", "https://other.scnative.dev/file",
            "https://api.scnative.dev:8443/file", "https://u:p@api.scnative.dev/file", "https://cdn.example/file")) {
            assertEquals(listOf(url.toHttpUrl()), AudioRoute.plan(url.toHttpUrl(), listOf("r1"), "r1"))
        }
    }

    @Test fun redirectLoopsAndHttpsDowngradesStop() {
        for (target in listOf("https://stream.scnative.dev/stream/u", "http://cdn.example/file")) {
            val http = client { request -> reply(request, 307, target) }
            assertThrows(java.io.IOException::class.java) {
                http.newCall(Request.Builder().url("https://stream.scnative.dev/stream/u").build()).execute().close()
            }
        }
    }

    @Test fun wrapperDisablesAutomaticRedirectsAndDoesNotDuplicateItself() {
        val wrapped = AudioRoute.builder(OkHttpClient()).build()
        val again = AudioRoute.builder(wrapped).build()
        assertFalse(again.followRedirects)
        assertFalse(again.followSslRedirects)
        assertEquals(1, again.interceptors.count { it is AudioRoute })
    }

    @Test fun aMutationRetriesOnlyWhenItCouldNotReachTheServer() {
        var calls = 0
        val http = client { request ->
            calls++
            if (calls == 1) throw UnknownHostException("fixture")
            reply(request)
        }
        http.newCall(Request.Builder().url("https://api.scnative.dev/auth/soundcloud/refresh")
            .post("{}".toRequestBody()).build()).execute().use { assertEquals(200, it.code) }
        assertEquals(2, calls)
    }

    @Test fun aMutationIsNotReplayedAfterTimeoutOrServerError() {
        for (timeout in listOf(true, false)) {
            var calls = 0
            val http = client { request ->
                calls++
                if (timeout) throw java.net.SocketTimeoutException("response lost")
                reply(request, 503)
            }
            val call = http.newCall(Request.Builder().url("https://api.scnative.dev/playlists").post("{}".toRequestBody()).build())
            if (timeout) assertThrows(java.io.IOException::class.java) { call.execute().close() }
            else call.execute().use { assertEquals(503, it.code) }
            assertEquals(1, calls)
        }
    }

    @Test fun payUsesItsOwnRelayService() {
        val hops = AudioRoute.plan("https://pay.scnative.dev/api/me/subscription".toHttpUrl(), listOf("r1", "r2"), "r2")
        assertEquals("pay.r2.relay.scnative.dev", hops.first().host)
        assertEquals("/api/me/subscription", hops.first().encodedPath)
    }
}
