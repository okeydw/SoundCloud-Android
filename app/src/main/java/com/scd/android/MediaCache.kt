package com.scd.android

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.launch
import java.io.File
import java.util.TreeSet

@OptIn(UnstableApi::class)
private class DynamicLruEvictor(private val maxBytes: () -> Long) : CacheEvictor {
    private val spans = TreeSet<CacheSpan> { a, b ->
        when {
            a.lastTouchTimestamp < b.lastTouchTimestamp -> -1
            a.lastTouchTimestamp > b.lastTouchTimestamp -> 1
            else -> a.compareTo(b)
        }
    }
    private var size = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) evict(cache, length)
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        size += span.length
        evict(cache, 0L)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (spans.remove(span)) size -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    fun evict(cache: Cache, required: Long) {
        val max = maxBytes()
        if (max <= 0L) return
        while (size + required > max && spans.isNotEmpty()) {
            val oldest = spans.first()
            runCatching { cache.removeSpan(oldest) }
            if (spans.remove(oldest)) size -= oldest.length
        }
    }
}

@OptIn(UnstableApi::class)
object MediaCache {

    @Volatile
    private var cache: SimpleCache? = null
    private var evictor: DynamicLruEvictor? = null

    private fun dirOf(context: Context) = File(context.applicationContext.cacheDir, "media2")

    fun mediaBudget(): Long {
        val limit = Prefs.cacheLimit
        if (limit == CacheLimits.UNLIMITED) return 0L
        return (limit * 0.6).toLong().coerceAtLeast(256L * 1024 * 1024)
    }

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
                val ev = DynamicLruEvictor(::mediaBudget)
                evictor = ev
                val dir = dirOf(context).apply { mkdirs() }
                SimpleCache(
                    dir,
                    ev,
                    StandaloneDatabaseProvider(context.applicationContext),
                ).also { cache = it }
            }
        }
    }

    fun applyLimit() {
        val c = cache ?: return
        val ev = evictor ?: return
        App.scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { synchronized(c) { ev.evict(c, 0L) } }
        }
    }

    fun keyOf(url: String): String {
        val urn = urnOf(url)
        if (urn != null) return if (url.contains("hq=true")) "$urn|hq" else urn
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

    fun remove(urn: String) {
        val c = cache ?: return
        for (key in listOf(urn, "$urn|hq")) {
            runCatching { c.removeResource(key) }
        }
    }

    fun clear(context: Context) {
        val c = synchronized(this) {
            cache ?: run {
                runCatching { dirOf(context).deleteRecursively() }
                null
            }
        } ?: return
        for (key in c.keys.toList()) {
            for (span in c.getCachedSpans(key)) {
                runCatching { c.removeSpan(span) }
            }
        }
    }
}
