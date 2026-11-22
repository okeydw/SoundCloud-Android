package com.scd.android

import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

object CatalogRead {
    private class Observer(val owner: Job?, val pending: () -> Unit) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Observer>
    }

    suspend fun <T> observe(pending: () -> Unit = {}, load: suspend () -> T): T =
        withContext(Observer(currentCoroutineContext()[Job], pending)) { load() }

    internal suspend fun <T> awaitReady(
        now: () -> Long = { System.nanoTime() / 1_000_000 },
        pause: suspend (Long) -> Unit = { delay(it) },
        load: suspend () -> T,
    ): T {
        val started = now()
        val observer = currentCoroutineContext()[Observer]
        var pendingReads = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            observer?.owner?.ensureActive()
            try {
                val result = load()
                observer?.owner?.ensureActive()
                return result
            } catch (e: ApiHttpException) {
                if (e.code != 503 || e.serviceCode !in setOf(
                        "user_refresh_pending", "web_profiles_refresh_pending", "collection_refresh_pending", "playlist_refresh_pending",
                    )) throw e
                observer?.pending?.invoke()
                pendingReads++
                val defaultWait = if (observer == null) 5L else minOf(30L, 5L * minOf(pendingReads, 6L))
                val seconds = (e.retryAfterSeconds ?: defaultWait).coerceAtLeast(1L)
                val remaining = 60_000L - (now() - started)
                if (observer == null && seconds > remaining / 1000L) throw e
                val wait = seconds.coerceAtMost(Long.MAX_VALUE / 1000L) * 1000L
                val owner = observer?.owner
                if (owner == null) pause(wait) else withContext(owner) { pause(wait) }
            }
        }
    }
}
