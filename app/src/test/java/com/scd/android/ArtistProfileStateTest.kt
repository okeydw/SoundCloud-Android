package com.scd.android

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class ArtistProfileStateTest {
    private val urn = "soundcloud:users:1481834099"

    @Test fun slowLinksDoNotDelayProfileAndPlaylists() = runBlocking {
        val started = mutableSetOf<String>()
        val user = CompletableDeferred<Artist>()
        val lists = CompletableDeferred<List<Playlist>>()
        val links = CompletableDeferred<List<WebProfile>>()
        val state = ArtistProfileState(urn,
            user = { started += "user"; user.await() },
            userPlaylists = { started += "playlists"; lists.await() },
            webProfiles = { started += "links"; links.await() },
            report = { _, _ -> fail("Unexpected failure") },
        )
        val job = launch { state.load() }
        yield(); yield()
        assertEquals(setOf("user", "playlists", "links"), started)
        user.complete(Artist(urn, "U-TOPIA"))
        lists.complete(listOf(Playlist("soundcloud:playlists:1")))
        yield()
        assertEquals("U-TOPIA", state.artist?.username)
        assertEquals(1, state.playlists.size)
        assertTrue(state.loading)
        links.complete(listOf(WebProfile(url = "https://example.com/artist")))
        job.join()
        assertFalse(state.loading)
        assertFalse(state.failed)
        assertEquals(1, state.links.size)
    }

    @Test fun failedSectionCanRetryWithoutDiscardingTheLoadedProfile() = runBlocking {
        var calls = 0
        var broken = true
        val errors = mutableListOf<String>()
        val state = ArtistProfileState(urn,
            user = { calls++; if (calls > 1) throw IOException("temporary"); Artist(urn, "U-TOPIA") },
            userPlaylists = { emptyList() },
            webProfiles = { if (broken) throw IOException("temporary"); listOf(WebProfile(url = "https://example.com")) },
            report = { part, _ -> errors += part },
        )
        state.load()
        assertTrue(state.failed)
        assertEquals(listOf("links"), errors)
        assertEquals("U-TOPIA", state.artist?.username)
        broken = false
        state.load()
        assertEquals("U-TOPIA", state.artist?.username)
        assertEquals(1, state.links.size)
        assertTrue(state.failed)
        assertFalse(state.loading)
    }

    @Test fun emptyLinksCanBeRefreshedAndOnlyHttpLinksAreDisplayed() = runBlocking {
        var links = emptyList<WebProfile>()
        val state = ArtistProfileState(urn,
            user = { Artist(urn) }, userPlaylists = { emptyList() }, webProfiles = { links }, report = { _, _ -> },
        )
        state.load()
        links = listOf(WebProfile(url = "https://example.com"), WebProfile(url = "https://example.com"),
            WebProfile(url = "javascript:alert(1)"), WebProfile(url = "http-invalid"))
        state.load()
        assertFalse(state.failed)
        assertEquals(listOf("https://example.com"), state.links.map { it.url })
    }

    @Test fun leavingTheScreenCancelsAllSectionsWithoutReportingFailure() = runBlocking {
        var cancelled = 0
        suspend fun <T> wait(): T = try { awaitCancellation() } finally { cancelled++ }
        val state = ArtistProfileState(urn,
            user = { wait() }, userPlaylists = { wait() }, webProfiles = { wait() },
            report = { _, _ -> fail("Cancellation is not a loading failure") },
        )
        val job = launch { state.load() }
        yield(); yield()
        job.cancelAndJoin()
        assertEquals(3, cancelled)
        assertFalse(state.loading)
        assertFalse(state.failed)
    }
}
