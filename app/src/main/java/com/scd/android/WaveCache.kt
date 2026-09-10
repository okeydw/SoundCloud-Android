package com.scd.android

import java.util.concurrent.ConcurrentHashMap

object WaveCache {
    private val map = ConcurrentHashMap<String, List<Float>>()

    fun get(urn: String?): List<Float>? = urn?.let { map[it] }

    fun put(urn: String, samples: List<Float>) {
        if (samples.isEmpty()) return
        if (map.size > 300) map.clear()
        map[urn] = samples
    }

    fun clear() = map.clear()
}

object Images {
    @Volatile
    var disk: coil.disk.DiskCache? = null

    fun clear() {
        runCatching { disk?.clear() }
    }
}
