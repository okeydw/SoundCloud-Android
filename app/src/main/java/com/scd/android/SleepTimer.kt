package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object SleepTimer {
    const val FADE_MS = 30_000L
    const val TRACK_FADE_MS = 10_000L
    const val EXTEND_MINUTES = 5
    val MINUTES = listOf(5, 10, 15, 30, 45, 60, 90, 120)

    var endsAt by mutableLongStateOf(0L)
        private set

    var endOfTrack by mutableStateOf(false)
        private set

    val active: Boolean get() = endsAt > 0L || endOfTrack

    fun start(minutes: Int, now: Long = System.currentTimeMillis()) {
        endOfTrack = false
        endsAt = now + minutes.coerceIn(1, 24 * 60) * 60_000L
    }

    fun startEndOfTrack() {
        endsAt = 0L
        endOfTrack = true
    }

    fun extend(now: Long = System.currentTimeMillis()) {
        endOfTrack = false
        endsAt = maxOf(endsAt, now) + EXTEND_MINUTES * 60_000L
    }

    fun cancel() {
        endsAt = 0L
        endOfTrack = false
    }

    fun remainingMs(now: Long = System.currentTimeMillis()): Long = if (endsAt > 0L) (endsAt - now).coerceAtLeast(0L) else 0L

    fun due(now: Long = System.currentTimeMillis()): Boolean = endsAt in 1..now

    fun gain(now: Long = System.currentTimeMillis(), trackLeftMs: Long = -1L): Float = when {
        endsAt > 0L -> ((endsAt - now).toFloat() / FADE_MS).coerceIn(0f, 1f)
        endOfTrack && trackLeftMs >= 0L -> (trackLeftMs.toFloat() / TRACK_FADE_MS).coerceIn(0f, 1f)
        else -> 1f
    }

    fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        fun two(v: Long) = v.toString().padStart(2, '0')
        return if (h > 0) "$h:${two(m)}:${two(s)}" else "$m:${two(s)}"
    }
}
