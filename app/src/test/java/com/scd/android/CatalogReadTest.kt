package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CatalogReadTest {
    private fun pending(code: String, seconds: Long? = null) =
        ApiHttpException(503, """{"code":"$code"}""", seconds)

    @Test fun waitsForServerRetryAfterBeforeReadingTheSameCollectionAgain() = runBlocking {
        var time = 0L
        var calls = 0
        val waits = mutableListOf<Long>()
        val result = CatalogRead.awaitReady(
            now = { time },
            pause = { waits += it; time += it },
        ) {
            if (++calls == 1) throw ApiContract.collectionFailure(
                """{"collection":[],"has_more":false,"sync":{"status":"refreshing","lastCompletedAt":null,"retryAfterSeconds":7}}""",
            )!!
            listOf(Track(urn = "soundcloud:tracks:42"))
        }
        assertEquals(2, calls)
        assertEquals(listOf(7000L), waits)
        assertEquals("soundcloud:tracks:42", result.single().urn)
    }

    @Test fun waitsForBothProfileAndLinksAndStopsAfterAMinuteOfPendingResponses() = runBlocking {
        for (code in listOf("user_refresh_pending", "web_profiles_refresh_pending")) {
            var time = 0L
            var calls = 0
            val error = pending(code)
            try {
                CatalogRead.awaitReady(now = { time }, pause = { time += it }) {
                    calls++
                    throw error
                }
                fail("A pending server must not loop forever")
            } catch (e: ApiHttpException) {
                assertSame(error, e)
            }
            assertEquals(60_000L, time)
            assertEquals(13, calls)
        }
    }

    @Test fun neverShortensALongServerBackoff() = runBlocking {
        val error = pending("web_profiles_refresh_pending", Long.MAX_VALUE)
        try {
            CatalogRead.awaitReady(pause = { fail("Must not retry before the server allows it") }) { throw error }
            fail("Must preserve the pending error")
        } catch (e: ApiHttpException) {
            assertSame(error, e)
        }
    }

    @Test fun terminalFailuresAndUnrelatedHttpErrorsAreNotPolled() = runBlocking {
        val errors = listOf(
            pending("soundcloud_reauthorization_required"), pending("collection_forbidden"),
            pending("collection_not_found"), pending("soundcloud_search_busy"),
            ApiHttpException(404, "not found"), ApiHttpException(429, "rate limited", 5),
        )
        for (error in errors) {
            try {
                CatalogRead.awaitReady(pause = { fail("Not a pending catalog read") }) { throw error }
                fail("Must preserve the error")
            } catch (e: ApiHttpException) {
                assertSame(error, e)
            }
        }
    }

    @Test fun cancellationDuringBackoffDoesNotStartAnotherRequest() = runBlocking {
        var calls = 0
        val cancelled = CancellationException("left artist screen")
        try {
            CatalogRead.awaitReady(pause = { throw cancelled }) {
                calls++
                throw pending("user_refresh_pending")
            }
            fail("Must propagate cancellation")
        } catch (e: CancellationException) {
            assertSame(cancelled, e)
        }
        assertEquals(1, calls)
    }
}
