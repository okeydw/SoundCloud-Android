package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCatalogTest {
    private val one = Track(urn = "soundcloud:tracks:1", title = "First")
    private val two = Track(urn = "soundcloud:tracks:2", title = "Second")
    private val three = Track(urn = "soundcloud:tracks:3", title = "Third")
    private val labels = listOf("SoundCloud", "Queue", "Downloads", "Playlists")

    private fun catalog() = BrowserCatalog(
        queue = listOf(one, two, one),
        downloads = listOf(two, three),
        playlists = listOf(LocalPlaylist(id = "local:1", title = "Saved", tracks = listOf(three))),
        labels = labels,
    )

    @Test
    fun rootAndFoldersExposeRealContent() {
        val catalog = catalog()
        assertTrue(catalog.root.browsable)
        assertEquals(listOf(BrowserCatalog.QUEUE, BrowserCatalog.DOWNLOADS, BrowserCatalog.PLAYLISTS), catalog.children(BrowserCatalog.ROOT, 0, 10)?.map { it.id })
        assertEquals(listOf(one.urn, two.urn), catalog.children(BrowserCatalog.QUEUE, 0, 10)?.map { it.id })
        assertEquals(listOf(two.urn, three.urn), catalog.children(BrowserCatalog.DOWNLOADS, 0, 10)?.map { it.id })
        assertEquals(listOf(three.urn), catalog.children(BrowserCatalog.playlistId("local:1"), 0, 10)?.map { it.id })
        assertFalse(catalog.item(one.urn)!!.browsable)
    }

    @Test
    fun paginationHandlesLegacyUnlimitedRequestsAndIntegerOverflow() {
        val catalog = catalog()
        assertEquals(listOf(two.urn), catalog.children(BrowserCatalog.QUEUE, 1, 1)?.map { it.id })
        assertEquals(2, catalog.children(BrowserCatalog.QUEUE, 0, Int.MAX_VALUE)?.size)
        assertEquals(emptyList<BrowserEntry>(), catalog.children(BrowserCatalog.QUEUE, Int.MAX_VALUE, Int.MAX_VALUE))
        assertNull(catalog.children(BrowserCatalog.QUEUE, -1, 10))
        assertNull(catalog.children(BrowserCatalog.QUEUE, 0, 0))
    }

    @Test
    fun unknownIdsAndTracksCannotBeBrowsedAsFolders() {
        val catalog = catalog()
        assertNull(catalog.item("missing"))
        assertNull(catalog.children("missing", 0, 10))
        assertNull(catalog.children(one.urn, 0, 10))
        assertNull(catalog.resolve(listOf(BrowserCatalog.ROOT)))
    }

    @Test
    fun playbackResolvesOnlyKnownTracksAndRejectsMixedUnknownRequests() {
        val catalog = catalog()
        assertEquals(listOf(three, one), catalog.resolve(listOf(three.urn, one.urn)))
        assertNull(catalog.resolve(listOf(one.urn, "https://example.com/injected.mp3")))
        assertNull(catalog.resolve(listOf("file:///data/private")))
        assertNull(catalog.resolve(listOf("soundcloud:tracks:999")))
    }

    @Test
    fun unavailableTracksAndDeletedPlaylistsAreNotExposedOrPlayable() {
        val catalog = BrowserCatalog(
            queue = listOf(one, two), downloads = listOf(two, three),
            playlists = listOf(
                LocalPlaylist("deleted", "Deleted", tracks = listOf(three), deleted = true),
                LocalPlaylist("kept", "Kept", tracks = listOf(two)),
            ),
            labels = labels, playable = { it.urn != two.urn },
        )
        assertEquals(listOf(one), catalog.resolve(listOf(one.urn)))
        assertNull(catalog.resolve(listOf(two.urn)))
        assertNull(catalog.item(BrowserCatalog.playlistId("deleted")))
        assertEquals(emptyList<BrowserEntry>(), catalog.children(BrowserCatalog.playlistId("kept"), 0, 10))
    }

    @Test
    fun catalogReflectsPlaylistRemovalAndDownloadChanges() {
        val old = catalog().counts()
        val current = BrowserCatalog(emptyList(), listOf(one), emptyList(), labels).counts()
        assertEquals(1, current[BrowserCatalog.DOWNLOADS])
        assertEquals(0, current[BrowserCatalog.QUEUE])
        assertEquals(0, current[BrowserCatalog.PLAYLISTS])
        assertTrue(BrowserCatalog.playlistId("local:1") in old.keys - current.keys)
    }
}
