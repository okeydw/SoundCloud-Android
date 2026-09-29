package com.scd.android

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object WaveFeedback {
    private val owned = ConcurrentHashMap.newKeySet<String>()
    private val negatives = AtomicInteger(0)
    private val positives = AtomicInteger(0)

    fun own(tracks: List<Track>) {
        if (owned.size > 2000) owned.clear()
        tracks.forEach { owned.add(it.urn) }
    }

    fun isOwned(urn: String?) = urn != null && urn in owned

    fun negative(urn: String?) {
        if (isOwned(urn)) negatives.incrementAndGet()
    }

    fun positive(urn: String?) {
        if (!isOwned(urn)) return
        positives.incrementAndGet()
        LocalRadio.addSeed(urn!!)
    }

    suspend fun flush(cursor: String): String? {
        if (cursor.isEmpty() || cursor == LocalRadio.CURSOR) {
            negatives.set(0)
            positives.set(0)
            return null
        }
        val neg = negatives.getAndSet(0)
        val pos = positives.getAndSet(0)
        if (neg == 0 && pos == 0) return null
        val updated = runCatching { Api.waveFeedback(cursor, neg, pos) }.getOrNull()
        Logs.add("wave", "feedback -$neg +$pos${if (updated != null) " → new cursor" else ""}")
        return updated
    }
}
