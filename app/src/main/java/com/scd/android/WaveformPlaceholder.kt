package com.scd.android

internal fun youtubeWaveform(urn: String?): List<Float>? {
    if (urn?.startsWith("youtube:tracks:") != true) return null
    return fallbackWaveform(urn)
}

internal fun fallbackWaveform(urn: String): List<Float> {
    var seed = urn.hashCode()
    return List(96) {
        seed = seed * 1664525 + 1013904223
        0.12f + (seed ushr 8).toFloat() / 16777215f * 0.76f
    }
}

fun waveBars(samples: List<Float>, count: Int): List<Float> {
    if (samples.isEmpty() || count <= 0) return emptyList()
    if (samples.size == count) return samples
    return List(count) { i ->
        val start = i * samples.size / count
        val end = ((i + 1) * samples.size / count).coerceIn(start + 1, samples.size)
        samples.subList(start, end).average().toFloat()
    }
}
