package com.scd.android

internal class AacTimeline(sampleRate: Int) {
    private var stepUs = 1_024_000_000L / sampleRate.coerceAtLeast(1)
    private var previousUs: Long? = null
    private var outputUs = 0L
    var discontinuities = 0
        private set

    fun next(inputUs: Long): Long {
        previousUs?.let { previous ->
            val delta = inputUs - previous
            if (delta in 1..100_000L) {
                stepUs = delta
                outputUs += delta
            } else {
                outputUs += stepUs
                discontinuities++
            }
        }
        previousUs = inputUs
        return outputUs
    }
}
