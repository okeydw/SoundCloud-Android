package com.scd.android

import androidx.media3.common.PlaybackParameters
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

object SpeedPitch {
    const val RATE_MIN = 0.5f
    const val RATE_MAX = 2.0f
    const val RATE_STEP = 0.05f
    const val RATE_DEFAULT = 1.0f

    const val SEMI_MIN = -12f
    const val SEMI_MAX = 12f
    const val SEMI_STEP = 0.5f

    const val MODE_AUTO = "auto"
    const val MODE_MANUAL = "manual"

    fun clampRate(rate: Float): Float {
        if (!rate.isFinite()) return RATE_DEFAULT
        return (rate.coerceIn(RATE_MIN, RATE_MAX) * 100f).roundToInt() / 100f
    }

    fun clampSemitones(semi: Float): Float {
        if (!semi.isFinite()) return 0f
        return (semi.coerceIn(SEMI_MIN, SEMI_MAX) * 2f).roundToInt() / 2f
    }

    fun effectiveSemitones(rate: Float, mode: String, manual: Float): Float =
        if (mode == MODE_MANUAL) {
            clampSemitones(manual)
        } else {
            clampSemitones((ln(maxOf(0.01f, rate).toDouble()) / ln(2.0) * 12.0).toFloat())
        }

    fun parameters(rate: Float = Prefs.playbackRate, mode: String = Prefs.pitchMode, manual: Float = Prefs.pitchSemitones): PlaybackParameters {
        val speed = clampRate(rate)
        val pitch = if (mode == MODE_MANUAL) 2.0.pow(clampSemitones(manual) / 12.0).toFloat() else speed
        return PlaybackParameters(speed, pitch)
    }

    val active: Boolean
        get() = kotlin.math.abs(Prefs.playbackRate - RATE_DEFAULT) > 0.001f ||
            (Prefs.pitchMode == MODE_MANUAL && Prefs.pitchSemitones != 0f)

    fun rateLabel(rate: Float): String = "%.2f×".format(java.util.Locale.ROOT, clampRate(rate))

    fun semitoneLabel(semi: Float): String {
        val v = clampSemitones(semi)
        val text = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v)
        return if (v > 0f) "+$text" else text
    }
}
