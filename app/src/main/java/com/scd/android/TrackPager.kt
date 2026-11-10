package com.scd.android

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Stable
class TrackPager(private val scope: CoroutineScope, private val loader: suspend (Int, Boolean) -> Pair<List<Track>, Boolean>) {
    var items by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var page by mutableIntStateOf(0)
        private set
    private val gate = Mutex()
    private var freshAt = 0L

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                gate.withLock {
                    val (batch, more) = loader(0, false)
                    items = batch.distinctBy { it.urn }
                    page = 0
                    hasMore = more
                    freshAt = System.currentTimeMillis()
                    if (!Prefs.offline) {
                        try {
                            val (fresh, freshMore) = loader(0, true)
                            if (fresh.isNotEmpty()) {
                                items = fresh.distinctBy { it.urn }
                                hasMore = freshMore
                            }
                        } catch (e: CancellationException) { throw e
                        } catch (_: Exception) {                                              }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "Load failed"
            } finally { loading = false }
        }
    }

    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000L) {
        if (Prefs.offline || loading || System.currentTimeMillis() - freshAt < maxAgeMs) return
        refresh()
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        error = null
        scope.launch {
            try { gate.withLock { appendPage() }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { error = e.message ?: "Load failed"
            } finally { loading = false }
        }
    }

    private suspend fun appendPage() {
        if (!hasMore) return
        val next = page + 1
        val (batch, more) = loader(next, false)
        items = (items + batch).distinctBy { it.urn }
        page = next
        hasMore = more
    }

    suspend fun all(): List<Track> = gate.withLock {
        loading = true
        error = null
        try {
            var fetched = 0
            while (hasMore && items.size < 10_000 && fetched++ < 500) appendPage()
            check(!hasMore) { "The list is too large to load at once" }
            items
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            error = e.message ?: "Load failed"
            throw e
        } finally { loading = false }
    }
}
