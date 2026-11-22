package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ArtistPlaylistPagesTest {
    @Test fun followsAllPagesAndDeduplicatesPlaylists() = runBlocking {
        val pages = mutableListOf<Int>()
        val result = artistPlaylists { page ->
            pages += page
            when (page) {
                0 -> PagedPlaylists(listOf(Playlist("one"), Playlist("two")), has_more = true)
                1 -> PagedPlaylists(listOf(Playlist("two"), Playlist("three")), has_more = true)
                else -> PagedPlaylists(listOf(Playlist("four")))
            }
        }
        assertEquals(listOf(0, 1, 2), pages)
        assertEquals(listOf("one", "two", "three", "four"), result.map { it.urn })
    }

    @Test fun cancellationDoesNotTurnAnIncompleteListIntoSuccess() = runBlocking {
        try {
            artistPlaylists { page ->
                if (page > 0) throw CancellationException("left profile")
                PagedPlaylists(listOf(Playlist("one")), has_more = true)
            }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
    }
}
