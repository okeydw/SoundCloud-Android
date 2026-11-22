package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class WaveformStoreTest {
    private val saved = linkedMapOf<String, String>()
    private val downloaded = mutableSetOf<String>()

    private fun open(memoryLimit: Int = 2, diskLimit: Int = 8) = WaveformStore(
        initial = saved,
        save = { changes -> changes.forEach { (key, value) -> if (value == null) saved.remove(key) else saved[key] = value } },
        pinned = { it in downloaded },
        memoryLimit = memoryLimit,
        diskLimit = diskLimit,
    )

    @Test fun aFreshProcessGetsTheExactSavedWaveWithoutFetching() {
        val samples = listOf(0.08f, 0.3720192f, 1f, 0.234501f)
        open().put("track", samples)
        assertEquals(samples, open().get("track"))
        assertNull(open().get("different-track"))
    }

    @Test fun exceedingTheMemoryLimitDoesNotEraseEarlierWaves() {
        val cache = open()
        repeat(6) { cache.put("track-$it", listOf((it + 1) / 10f)) }
        repeat(6) { assertEquals(listOf((it + 1) / 10f), cache.get("track-$it")) }
        assertEquals(6, saved.size)
    }

    @Test fun diskEvictionPreservesDownloadedTracksAndRecentReads() {
        downloaded += "offline"
        val cache = open(diskLimit = 3)
        cache.put("offline", listOf(0.1f))
        cache.put("recent", listOf(0.2f))
        cache.put("old", listOf(0.3f))
        cache.get("recent")
        cache.put("new", listOf(0.4f))
        assertNotNull(open().get("offline"))
        assertNotNull(open().get("recent"))
        assertNotNull(open().get("new"))
        assertNull(open().get("old"))
    }

    @Test fun clearingTemporaryCacheKeepsOfflineWavesAfterRestart() {
        downloaded += "offline"
        val cache = open()
        cache.put("offline", listOf(0.2f))
        cache.put("stream", listOf(0.4f))
        cache.clear()
        assertEquals(listOf(0.2f), open().get("offline"))
        assertNull(open().get("stream"))
        cache.clear(keepPinned = false)
        assertTrue(saved.isEmpty())
    }

    @Test fun aLargeOfflineLibraryIsNotDroppedAtTheCacheLimit() {
        downloaded += listOf("a", "b", "c")
        val cache = open(diskLimit = 2)
        downloaded.forEach { cache.put(it, listOf(0.5f)) }
        downloaded.forEach { assertEquals(listOf(0.5f), open().get(it)) }
    }

    @Test fun malformedSamplesAreMissesAndCanBeReplaced() {
        listOf("", "NaN", "Infinity", "-0.1", "1.01", "0.5,broken", "0.1,".repeat(5000)).forEach { bad ->
            saved["track"] = bad
            val cache = open()
            assertNull(cache.get("track"))
            assertFalse(saved.containsKey("track"))
            cache.put("track", listOf(0.25f))
            assertEquals(listOf(0.25f), open().get("track"))
        }
    }

    @Test fun invalidUpdatesDoNotReplaceTheRealWave() {
        val cache = open()
        val samples = mutableListOf(0.1f, 0.9f)
        cache.put("track", samples)
        samples[0] = 0.7f
        cache.put("track", emptyList())
        cache.put("track", listOf(Float.NaN))
        assertEquals(listOf(0.1f, 0.9f), cache.get("track"))
        assertEquals(listOf(0.1f, 0.9f), open().get("track"))
    }

    @Test fun unchangedSamplesDoNotRewriteTheSavedWave() {
        val writes = mutableListOf<Map<String, String?>>()
        val cache = WaveformStore(initial = mapOf("track" to "0.1,0.6"), save = { writes.add(it) })
        cache.put("track", listOf(0.1f, 0.6f))
        assertTrue(writes.isEmpty())
        cache.put("track", listOf(0.2f, 0.5f))
        assertEquals(listOf(0.2f, 0.5f), cache.get("track"))
        assertEquals(1, writes.size)
    }
}
