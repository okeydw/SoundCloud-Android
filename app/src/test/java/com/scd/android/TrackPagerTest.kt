package com.scd.android

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TrackPagerTest {
    private fun tracks(vararg ids: Int) = ids.map { Track(urn = "soundcloud:tracks:$it") }

    @Test fun displaysCachedRowsWhileRefreshIsPendingAndAcceptsAnEmptyLibrary() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val pager = TrackPager(this) { _, fresh ->
            if (fresh) {
                entered.complete(Unit)
                release.await()
                emptyList<Track>() to false
            } else tracks(1, 2) to false
        }
        pager.acceptEmptyRefresh = true
        pager.refresh()
        withTimeout(2000) { entered.await() }
        assertEquals(tracks(1, 2), pager.items)
        release.complete(Unit)
        yield()
        assertTrue(pager.items.isEmpty())
    }

    @Test fun pageFailureCanRetryWithoutTruncatingOrSkipping() = runBlocking {
        var fail = true
        val pages = mutableListOf<Int>()
        val pager = TrackPager(this) { page, fresh ->
            if (fresh) emptyList<Track>() to false else {
                pages += page
                if (page == 1 && fail) throw IllegalStateException("temporary")
                (if (page == 0) tracks(1, 2) else tracks(3)) to (page == 0)
            }
        }
        pager.refresh(); yield()
        pager.loadMore(); yield()
        assertTrue(pager.hasMore)
        assertNotNull(pager.error)
        assertEquals(0, pager.page)
        fail = false
        pager.loadMore(); yield()
        assertEquals(listOf(0, 1, 1), pages)
        assertEquals(3, pager.items.size)
        assertNull(pager.error)
        assertFalse(pager.hasMore)
    }

    @Test fun bulkLoadFailureRetainsTheLastSuccessfulPage() = runBlocking {
        var fail = true
        val pages = mutableListOf<Int>()
        val pager = TrackPager(this) { page, fresh ->
            if (fresh) emptyList<Track>() to false else {
                pages += page
                if (page == 2 && fail) throw IllegalStateException("temporary")
                tracks(page) to (page < 2)
            }
        }
        pager.refresh(); yield()
        try { pager.all(); fail("Must expose an incomplete load") } catch (_: IllegalStateException) { }
        assertEquals(1, pager.page)
        assertTrue(pager.hasMore)
        fail = false
        assertEquals(3, pager.all().size)
        assertEquals(listOf(0, 1, 2, 2), pages)
    }

    @Test fun duplicateOnlyPageStillAdvancesTheCursor() = runBlocking {
        val pager = TrackPager(this) { page, fresh ->
            if (fresh) emptyList<Track>() to false else (if (page < 2) tracks(1) else tracks(2)) to (page < 2)
        }
        pager.refresh(); yield()
        pager.loadMore(); yield()
        assertEquals(1, pager.items.size)
        assertEquals(1, pager.page)
        assertTrue(pager.hasMore)
        assertEquals(2, pager.all().size)
    }
}
