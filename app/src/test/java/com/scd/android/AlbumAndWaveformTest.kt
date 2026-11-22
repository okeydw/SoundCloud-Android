package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class AlbumAndWaveformTest {
    @Test fun savingTheSameAlbumUpdatesItsOrderedOfflineCopy() {
        val first = Track(urn = "soundcloud:tracks:1")
        val second = Track(urn = "soundcloud:tracks:2")
        val key = "album:catalog:regression"
        LocalPlaylists.saveAlbum(key, "Old", null, listOf(first), SavedAlbumInfo("ep", primaryArtist = AlbumArtist("id", "Band"), year = 2021))
        LocalPlaylists.saveAlbum(key, "New", "cover", listOf(second, first, second))
        val saved = LocalPlaylists.find("local:$key")!!
        assertEquals(1, LocalPlaylists.visible.count { it.source == key })
        assertTrue(saved.savedAlbum)
        assertTrue(saved.asPlaylist().isAlbum)
        assertFalse(saved.syncing)
        val reopened = LocalPlaylists.cachedAlbum("regression")!!
        assertEquals("New", reopened.title)
        assertEquals("ep", saved.asPlaylist().kind)
        assertEquals("Band", saved.asPlaylist().user?.username)
        assertEquals(2021, reopened.release_year)
        assertEquals("cover", reopened.cover_url)
        assertEquals(listOf(second, first), reopened.tracks)
    }

    @Test fun ordinaryLocalPlaylistsStayInTheirOwnCategory() {
        val playlist = LocalPlaylist(id = "local:test", title = "Album", localOnly = true)
        assertFalse(playlist.savedAlbum)
        assertFalse(playlist.asPlaylist().isAlbum)
    }

    @Test fun widgetAndPlayerKeepTheAmplitudeOrderAtDifferentWidths() {
        val source = listOf(0f, 0.5f, 1f, 0.5f)
        assertEquals(listOf(0.25f, 0.75f), waveBars(source, 2))
        assertEquals(List(6) { 0.5f }, waveBars(listOf(0.5f), 6))
        assertTrue(waveBars(emptyList(), 56).isEmpty())
    }
}
