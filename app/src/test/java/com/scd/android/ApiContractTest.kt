package com.scd.android

import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import okio.Buffer
import okio.GzipSink
import okio.buffer

class ApiContractTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun onlyExplicitBooleanCanInvalidateASession() {
        assertEquals(true, ApiContract.sessionAuthenticated("""{"authenticated":true}"""))
        assertEquals(false, ApiContract.sessionAuthenticated("""{"authenticated":false}"""))
        for (body in listOf("{}", "null", "<html>error</html>", """{"authenticated":"false"}""", """{"authenticated":null}""")) {
            assertNull(ApiContract.sessionAuthenticated(body))
        }
    }

    @Test fun collectionAuthFailureRequestsReconnectionWithoutDiscardingTheAppSession() {
        assertTrue(ApiContract.reconnectRequired("""{"collection":[],"sync":{"status":"auth_required","errorCode":"auth_required"}}"""))
        assertTrue(ApiContract.reconnectRequired("""{"collection":[],"sync":{"status":"refreshing","errorCode":"auth_required"}}"""))
        assertFalse(ApiContract.reconnectRequired("""{"collection":[],"sync":{"status":"refreshing","errorCode":"unavailable"}}"""))
    }

    @Test fun compressedPendingCollectionCannotReplaceTheOfflineSnapshot() {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.writeUtf8("""{"collection":[],"sync":{"status":"refreshing","lastCompletedAt":null}}""") }
        val bytes = buffer.readByteArray()
        response("/me/likes/tracks").newBuilder().header("Content-Encoding", "gzip")
            .body(bytes.toResponseBody()).build().use {
                assertTrue(ApiContract.cacheResponse(it, true, false).cacheControl.noStore)
                assertArrayEquals(bytes, it.body!!.bytes())
            }
    }

    @Test fun batchRequestsAllFiftyTracksAndAcceptsUrns() {
        val ids = (1..50).map { "soundcloud:tracks:$it" }
        val url = ("https://api.example" + ApiContract.batchPath(ids)).toHttpUrl()
        assertEquals("50", url.queryParameter("limit"))
        assertEquals("0", url.queryParameter("page"))
        assertEquals((1..50).joinToString(","), url.queryParameter("ids"))
        assertEquals("42", ApiContract.trackId("soundcloud:tracks:42"))
        assertEquals("42", ApiContract.trackId("42"))
    }

    @Test fun coldCollectionIsNotAnAuthoritativeEmptyLibrary() {
        assertTrue(ApiContract.pendingCollection("""{"collection":[],"has_more":false,"sync":{"status":"refreshing","lastCompletedAt":null,"retryAfterSeconds":5}}"""))
        assertTrue(ApiContract.pendingCollection("""{"collection":[{"urn":"soundcloud:tracks:1"}],"sync":{"status":"refreshing","lastCompletedAt":null}}"""))
        assertFalse(ApiContract.pendingCollection("""{"collection":[],"sync":{"status":"ready","lastCompletedAt":"2026-11-08T10:00:00Z"}}"""))
        assertFalse(ApiContract.pendingCollection("""{"collection":[],"sync":{"status":"refreshing","lastCompletedAt":"2026-11-07T10:00:00Z"}}"""))
        assertFalse(ApiContract.pendingCollection("""{"collection":[],"has_more":false}"""))
    }

    @Test fun playlistWithoutBaselineDoesNotEraseItsLocalSnapshot() {
        assertTrue(ApiContract.pendingCollection("""{"collection":[],"sync":{"status":"unhydrated","baselineGeneration":0,"observedAt":null}}"""))
        assertTrue(ApiContract.pendingCollection("""{"collection":[],"sync":{"status":"retry_wait","baselineGeneration":0,"observedAt":null}}"""))
        assertFalse(ApiContract.pendingCollection("""{"collection":[],"sync":{"status":"pending","baselineGeneration":2,"observedAt":"2026-11-08T10:00:00Z"}}"""))
    }

    @Test fun businessErrorsAreNotRetriedAcrossEveryHost() {
        assertFalse(ApiContract.retryHost(409, """{"code":"soundcloud_reauthorization_required"}"""))
        assertFalse(ApiContract.retryHost(429, """{"code":"rate_limited"}"""))
        assertFalse(ApiContract.retryHost(503, """{"code":"playlist_refresh_pending"}"""))
        assertFalse(ApiContract.retryHost(503, """{"code":"soundcloud_search_busy"}"""))
        assertTrue(ApiContract.retryHost(502, "<html>gateway down</html>"))
    }

    @Test fun collectionFailuresPreserveBackoffAndDoNotPollTerminalStates() {
        for ((error, code) in listOf("auth_required" to "soundcloud_reauthorization_required",
            "not_found" to "collection_not_found", "forbidden" to "collection_forbidden")) {
            val failure = ApiContract.collectionFailure("""{"collection":[],"sync":{"status":"refreshing","lastCompletedAt":null,"errorCode":"$error","retryAfterSeconds":120}}""")!!
            assertEquals(code, failure.serviceCode)
            assertEquals(120L, failure.retryAfterSeconds)
        }
        assertNull(ApiContract.collectionFailure("""{"collection":[],"sync":{"status":"ready"}}"""))
        assertNull(ApiContract.collectionFailure("""{"collection":[{"urn":"soundcloud:tracks:1"}],"sync":{"status":"refreshing","lastCompletedAt":"2026-11-10T00:00:00Z"}}"""))
    }

    @Test fun malformedLinkDoesNotHideTheValidLinksBesideIt() {
        val body = """[{"url":"https://example.com","title":null,"service":"personal"},null,{"url":{}}]"""
        assertEquals(listOf("https://example.com"), Api.decodeWebProfiles(json.parseToJsonElement(body)).map { it.url })
        assertEquals(1, Api.decodeWebProfiles(json.parseToJsonElement("""{"collection":$body}""")).size)
        assertTrue(Api.decodeWebProfiles(json.parseToJsonElement("[]")).isEmpty())
        for (invalid in listOf("{}", "null", "[null]")) {
            try {
                Api.decodeWebProfiles(json.parseToJsonElement(invalid))
                fail("Malformed links are not an empty profile")
            } catch (_: java.io.IOException) { }
        }
    }

    @Test fun soundcloudReconnectIsSeparateFromAnInvalidAppSession() {
        assertTrue(ApiContract.reconnectRequired("""{"code":"soundcloud_reauthorization_required"}"""))
        assertTrue(ApiContract.reconnectRequired("""{"state":"not_connected","canUseSoundcloud":false}"""))
        assertFalse(ApiContract.reconnectRequired("""{"state":"refreshing","canUseSoundcloud":false}"""))
        assertFalse(ApiContract.reconnectRequired("""{"message":"Session not found"}"""))
    }

    private fun response(path: String, body: String = "{}") = Response.Builder()
        .request(Request.Builder().url("https://api.r2.relay.scnative.dev$path").header("x-session-id", "test-session").build())
        .protocol(Protocol.HTTP_1_1).code(200).message("OK")
        .header("Cache-Control", "private, no-store").header("Vary", "Accept-Encoding")
        .body(body.toResponseBody()).build()

    @Test fun controlAndPendingResponsesNeverEnterTheOfflineCache() {
        for (path in listOf("/auth/login/status?id=1", "/me/subscription", "/rooms/ABCDEF", "/tracks/urn/stream")) {
            response(path).use { assertTrue(ApiContract.cacheResponse(it, true, false).cacheControl.noStore) }
        }
        response("/me/likes/tracks", """{"collection":[],"sync":{"status":"refreshing","lastCompletedAt":null}}""").use {
            assertTrue(ApiContract.cacheResponse(it, true, false).cacheControl.noStore)
        }
    }

    @Test fun offlineLibraryCacheIsPrivateAndRequiresFreshDataWhenOnline() {
        response("/me/likes/tracks", """{"collection":[],"sync":{"status":"ready"}}""").use {
            val cached = ApiContract.cacheResponse(it, true, false)
            assertTrue(cached.cacheControl.isPrivate)
            assertFalse(cached.cacheControl.isPublic)
            assertFalse(cached.cacheControl.noStore)
            assertEquals(0, cached.cacheControl.maxAgeSeconds)
            assertTrue(cached.header("Vary").orEmpty().contains("x-session-id"))
            assertTrue(cached.header("Vary").orEmpty().contains("Accept-Encoding"))
        }
    }

    @Test fun currentWaveCarriesAnInlineTrackAndLegacyWaveStillParses() {
        val wave = json.decodeFromString(WavePayload.serializer(), """{"tracks":[{"id":"42","urn":"soundcloud:tracks:42","score":0.7,"track":{"id":42,"urn":"soundcloud:tracks:42","title":"Full track","duration":154000}},{"id":43,"score":0.5}],"cursor":"next"}""")
        assertEquals("Full track", wave.tracks[0].track?.title)
        assertNull(wave.tracks[1].track)
        assertEquals("43", wave.tracks[1].id.content)
        assertEquals("next", wave.cursor)
    }

    @Test fun historyNormalizesNumericIdsAndUsesTheCanonicalUrn() {
        assertEquals("soundcloud:tracks:42", HistoryEntry(scTrackId = "42").toTrack().urn)
        assertEquals("soundcloud:tracks:43", HistoryEntry(scTrackId = "42", trackUrn = "soundcloud:tracks:43").toTrack().urn)
        assertEquals("soundcloud:tracks:44", HistoryEntry(scTrackId = "soundcloud:tracks:44").toTrack().urn)
    }

    @Test fun currentLibraryAndCatalogResponseShapesParse() {
        val page = json.decodeFromString(PagedTracks.serializer(), """{"collection":[{"id":42,"urn":"soundcloud:tracks:42","title":null}],"page":1,"pageSize":50,"page_size":50,"hasMore":true,"has_more":true,"sync":{"status":"ready"}}""")
        assertEquals(50, page.page_size)
        assertTrue(page.has_more)
        assertEquals("", page.collection.single().title)
        val album = json.decodeFromString(CatalogAlbum.serializer(), """{"id":"album-id","title":"Album","type":"ep","release_year":2026,"tracks":[],"artists":[]}""")
        assertEquals("ep", album.type)
    }
}
