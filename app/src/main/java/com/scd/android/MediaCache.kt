package com.scd.android

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object MediaCache {

    @Volatile
    private var cache: SimpleCache? = null

    @Volatile
    private var builtWithLimit: Long = -1L

    private fun dirOf(context: Context) = File(context.applicationContext.cacheDir, "media2")

    fun dropLegacy(context: Context) {
        val legacy = File(context.applicationContext.cacheDir, "media")
        if (legacy.exists()) {
            runCatching { legacy.deleteRecursively() }
            Logs.add("cache", "dropped legacy media cache")
        }
    }

    fun get(context: Context): SimpleCache {
        cache?.let { return it }
        return synchronized(this) {
            cache ?: run {
                val limit = Prefs.cacheLimit
                val evictor = if (limit == CacheLimits.UNLIMITED) {
                    NoOpCacheEvictor()
                } else {
                    LeastRecentlyUsedCacheEvictor(limit)
                }
                val dir = dirOf(context).apply { mkdirs() }
                SimpleCache(
                    dir,
                    evictor,
                    StandaloneDatabaseProvider(context.applicationContext),
                ).also {
                    cache = it
                    builtWithLimit = limit
                }
            }
        }
    }

    fun keyOf(url: String): String {
        val urn = urnOf(url)
        if (urn != null) return urn
        val i = url.indexOf('?')
        if (i < 0) return url
        val base = url.substring(0, i)
        val params = url.substring(i + 1)
            .split('&')
            .filter { it.isNotEmpty() && !it.startsWith("session_id=") }
        return if (params.isEmpty()) base else base + "?" + params.joinToString("&")
    }

    private fun urnOf(url: String): String? {
        for (marker in listOf("/stream/", "/download/")) {
            val i = url.indexOf(marker)
            if (i >= 0) {
                val tail = url.substring(i + marker.length).substringBefore('?')
                if (tail.isNotEmpty()) {
                    return runCatching { java.net.URLDecoder.decode(tail, "UTF-8") }.getOrNull()
                }
            }
        }
        val last = url.substringAfterLast('/', "")
        if (last.endsWith(".m4a")) return last.removeSuffix(".m4a").replace('_', ':')
        return null
    }

    fun sizeBytes(): Long = cache?.cacheSpace ?: 0L

    fun prune() {
        val days = Prefs.cacheDays
        if (days == CacheLimits.FOREVER) return
        val c = cache ?: return
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        var removed = 0
        for (key in c.keys.toList()) {
            for (span in c.getCachedSpans(key)) {
                if (span.lastTouchTimestamp in 1 until cutoff) {
                    if (runCatching { c.removeSpan(span) }.isSuccess) removed++
                }
            }
        }
        if (removed > 0) Logs.add("cache", "pruned $removed spans older than ${days}d")
    }

    fun clear() {
        val c = cache ?: return
        for (key in c.keys.toList()) {
            for (span in c.getCachedSpans(key)) {
                runCatching { c.removeSpan(span) }
            }
        }
    }
}
