package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TrackSortTest {
    private fun ids(tracks: List<Track>) = tracks.map { it.urn }

    @Test fun restoresOriginalOrderAndUsesTheCollectionDirectionForRecent() {
        val source = listOf(Track(urn = "old"), Track(urn = "new"))
        assertEquals(listOf("new", "old"), ids(TrackOrder(TrackSort.RECENT).tracks(source)))
        assertEquals(ids(source), ids(TrackOrder(TrackSort.RECENT).tracks(source, newestFirst = true)))
        assertEquals(ids(source), ids(TrackOrder().tracks(source)))
        assertEquals(listOf("new", "old"), ids(TrackOrder(reversed = true).tracks(source)))
        assertEquals(listOf("old", "new"), ids(source))
    }

    @Test fun titleAndArtistUseDisplayedNamesAndKeepEqualKeysStable() {
        val source = listOf(
            Track(urn = "1", title = "Бета", user = TrackUser(username = "Аноним"), enrichment = TrackEnrichment(primary_artist = EnrichmentArtist(name = "Яна"))),
            Track(urn = "2", title = " альфа ", user = TrackUser(username = "Анна")),
            Track(urn = "3", title = "АЛЬФА", user = TrackUser(username = "Борис")),
        )
        assertEquals(listOf("2", "3", "1"), ids(TrackOrder(TrackSort.TITLE).tracks(source)))
        assertEquals(listOf("2", "3", "1"), ids(TrackOrder(TrackSort.ARTIST).tracks(source)))
    }

    @Test fun durationUsesFullAudioAndUnknownValuesStayLastInEitherDirection() {
        val source = listOf(Track(urn = "preview", duration = 10_000, full_duration = 154_000), Track(urn = "unknown"), Track(urn = "short", duration = 30_000))
        assertEquals(listOf("short", "preview", "unknown"), ids(TrackOrder(TrackSort.DURATION).tracks(source)))
        assertEquals(listOf("preview", "short", "unknown"), ids(TrackOrder(TrackSort.DURATION, reversed = true).tracks(source)))
    }

    @Test fun countersUseDescendingOrderAliasesAndStableUnknownTails() {
        val source = listOf(Track(urn = "missing"), Track(urn = "zero", likes_count = 0), Track(urn = "popular", favoritings_count = 12), Track(urn = "same", likes_count = 12))
        assertEquals(listOf("popular", "same", "zero", "missing"), ids(TrackOrder(TrackSort.LIKES).tracks(source)))
        assertEquals(listOf("zero", "popular", "same", "missing"), ids(TrackOrder(TrackSort.LIKES, reversed = true).tracks(source)))
    }

    @Test fun releaseDatesDecodeAndPreferEnrichmentWithSafeFallbacks() {
        val enriched = Json.decodeFromString<Track>("""{"urn":"enriched","created_at":"2018/01/01 00:00:00 +0000","release_date":"2020-01-01","enrichment":{"release_date":"2025-02-01"}}""")
        val source = listOf(Track(urn = "unknown", release_date = "bad"), Track(urn = "older", created_at = "2024-01-01T00:00:00Z"), enriched)
        assertEquals(listOf("enriched", "older", "unknown"), ids(TrackOrder(TrackSort.RELEASED).tracks(source)))
        assertNull(releaseDay("2025-02-29"))
        assertEquals(20240229L, releaseDay("2024-02-29"))
    }

    @Test fun completeCollectionDoesNotStopAtEmptyOrDuplicatePages() = runBlocking {
        val pages = mutableListOf<Int>()
        val all = collectTrackPages { page ->
            pages += page
            when (page) {
                0, 2 -> listOf(Track(urn = "old")) to true
                1 -> emptyList<Track>() to true
                else -> listOf(Track(urn = "new")) to false
            }
        }
        assertEquals(listOf(0, 1, 2, 3), pages)
        assertEquals(listOf("new", "old"), ids(TrackOrder(TrackSort.RECENT).tracks(all)))
    }

    @Test fun failedOrCancelledCollectionNeverLooksComplete() = runBlocking {
        for (error in listOf(IllegalStateException("offline"), CancellationException("closed"))) {
            try {
                collectTrackPages { page ->
                    if (page > 0) throw error
                    listOf(Track(urn = "partial")) to true
                }
                fail("Expected a complete-list failure")
            } catch (caught: Exception) { assertSame(error, caught) }
        }
    }
}
