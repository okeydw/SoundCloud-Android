package com.scd.android

import kotlin.math.sqrt

internal class WaveformEnvelope(private val bars: Int = 144) {
    private val energy = DoubleArray(bars * 2)
    private var bucketWidth = 1L
    private var count = 0L

    fun add(sample: Double) {
        if (!sample.isFinite()) return
        if (count == energy.size * bucketWidth) {
            for (i in 0 until bars) energy[i] = energy[i * 2] + energy[i * 2 + 1]
            energy.fill(0.0, bars)
            bucketWidth *= 2
        }
        energy[(count / bucketWidth).toInt()] += sample * sample
        count++
    }

    fun finish(): List<Float>? {
        if (count == 0L) return null
        val bins = minOf(count, bars.toLong()).toInt()
        val peaks = List(bins) { i ->
            var position = count * i / bins
            val end = count * (i + 1) / bins
            val length = end - position
            var sum = 0.0
            while (position < end) {
                val bucket = (position / bucketWidth).toInt()
                val bucketEnd = minOf((bucket + 1) * bucketWidth, count)
                val next = minOf(bucketEnd, end)
                sum += energy[bucket] * (next - position) / (bucketEnd - bucket * bucketWidth)
                position = next
            }
            sqrt(sum / length).toFloat()
        }
        val top = peaks.max().coerceAtLeast(0.001f)
        return peaks.map { (it / top).coerceIn(0f, 1f) }
    }
}
