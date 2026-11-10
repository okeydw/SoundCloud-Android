package com.scd.android

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CollectionReadRegressionTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val pending = """{"collection":[{"urn":"soundcloud:tracks:42","title":"Available track"}],"has_more":false,"sync":{"status":"refreshing","lastCompletedAt":null,"retryAfterSeconds":7}}"""

    @Test fun availableTracksAreShownWithoutWaitingForBackgroundRefresh() = runBlocking {
        val page = CatalogRead.awaitReady(pause = { fail("Available tracks must not wait") }) {
            ApiContract.collectionFailure(pending)?.let { throw it }
            json.decodeFromString(PagedTracks.serializer(), pending)
        }
        assertEquals("Available track", page.collection.single().title)
        assertFalse(page.snapshotReady)
        assertFalse(page.complete)
        assertTrue(ApiContract.pendingCollection(pending))
    }

    @Test fun emptyPendingCollectionWaitsAndThenReturnsAvailableTracks() = runBlocking {
        var calls = 0
        var clock = 0L
        val waits = mutableListOf<Long>()
        val page = CatalogRead.awaitReady(now = { clock }, pause = { clock += it; waits += it }) {
            val body = if (++calls == 1) pending.replace("[{\"urn\":\"soundcloud:tracks:42\",\"title\":\"Available track\"}]", "[]") else pending
            ApiContract.collectionFailure(body)?.let { throw it }
            json.decodeFromString(PagedTracks.serializer(), body)
        }
        assertEquals(2, calls)
        assertEquals(listOf(7000L), waits)
        assertEquals(1, page.collection.size)
        assertFalse(page.complete)
    }

    @Test fun existingSnapshotsAndLegacyCollectionsRemainUsable() {
        for (sync in listOf("null", """{"status":"ready"}""", """{"status":"refreshing","lastCompletedAt":"2026-11-10T00:00:00Z"}""", """{"status":"pending","baselineGeneration":2}""", """{"status":"pending","baselineGeneration":0,"observedAt":"2026-11-10T00:00:00Z"}""")) {
            val body = """{"collection":[],"has_more":false,"sync":$sync}"""
            assertNull(ApiContract.collectionFailure(body))
            assertTrue(json.decodeFromString(PagedTracks.serializer(), body).complete)
            assertTrue(json.decodeFromString(PagedPlaylists.serializer(), body).complete)
        }
        assertTrue(json.decodeFromString(PagedTracks.serializer(), """{"collection":[]}""").complete)
        assertFalse(json.decodeFromString(PagedTracks.serializer(), """{"collection":[],"has_more":true,"sync":{"status":"ready"}}""").complete)
    }

    @Test fun pendingPlaylistsCanBeShownWithoutConfirmingRemovals() {
        val body = """{"collection":[{"urn":"soundcloud:playlists:7","title":"Existing playlist"}],"has_more":false,"sync":{"status":"unhydrated","baselineGeneration":0,"observedAt":null}}"""
        assertNull(ApiContract.collectionFailure(body))
        val page = json.decodeFromString(PagedPlaylists.serializer(), body)
        assertEquals("Existing playlist", page.collection.single().title)
        assertFalse(page.complete)
    }

    @Test fun availableDataDoesNotHideTerminalFailures() {
        for ((error, code) in mapOf("auth_required" to "soundcloud_reauthorization_required", "forbidden" to "collection_forbidden", "not_found" to "collection_not_found")) {
            val body = pending.replace("\"retryAfterSeconds\":7", "\"retryAfterSeconds\":7,\"errorCode\":\"$error\"")
            assertEquals(code, ApiContract.collectionFailure(body)?.serviceCode)
        }
    }

    @Test fun partialPlaylistRefreshPreservesTracksUntilCompleteSnapshotArrives() {
        LocalPlaylists.reset()
        try {
            val urn = "soundcloud:playlists:654321"
            val imported = LocalPlaylists.importJson("""[{"id":"$urn","remoteUrn":"$urn","origin":"remote","title":"Saved playlist","tracksLoaded":true,"remoteCount":2,"tracks":[{"urn":"soundcloud:tracks:42","title":"Old title"},{"urn":"soundcloud:tracks:43","title":"Keep me"}]}]""", false)
            assertEquals(1, imported.size)
            val partial = json.decodeFromString(PagedTracks.serializer(), pending)
            LocalPlaylists.storeTracks(urn, partial.collection, partial.complete)
            val saved = LocalPlaylists.find(urn)!!
            assertEquals(listOf("soundcloud:tracks:42", "soundcloud:tracks:43"), saved.tracks.map { it.urn })
            assertEquals("Available track", saved.tracks.first().title)
            assertEquals(2, saved.remoteCount)
            assertFalse(saved.tracksLoaded)
            val ready = partial.copy(sync = CollectionSync(status = "ready"))
            LocalPlaylists.storeTracks(urn, ready.collection, ready.complete)
            assertEquals(1, LocalPlaylists.find(urn)!!.tracks.size)
            assertTrue(LocalPlaylists.find(urn)!!.tracksLoaded)
        } finally {
            LocalPlaylists.reset()
        }
    }
}
