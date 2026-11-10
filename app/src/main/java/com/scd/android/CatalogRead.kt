package com.scd.android

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

internal object CatalogRead {
    suspend fun <T> awaitReady(
        now: () -> Long = { System.nanoTime() / 1_000_000 },
        pause: suspend (Long) -> Unit = { delay(it) },
        load: suspend () -> T,
    ): T {
        val started = now()
        while (true) {
            currentCoroutineContext().ensureActive()
            try {
                return load()
            } catch (e: ApiHttpException) {
                if (e.code != 503 || e.serviceCode !in setOf(
                        "user_refresh_pending", "web_profiles_refresh_pending", "collection_refresh_pending", "playlist_refresh_pending",
                    )) throw e
                val seconds = (e.retryAfterSeconds ?: 5L).coerceAtLeast(1L)
                val remaining = 60_000L - (now() - started)
                if (seconds > remaining / 1000L) throw e
                pause(seconds * 1000L)
            }
        }
    }
}
