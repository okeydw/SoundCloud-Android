package com.scd.android

import android.media.audiofx.Equalizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class EqInfo(
    val bands: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val centerFreqsHz: List<Int>,
    val presets: List<String>,
)

object AudioFx {
    var info by mutableStateOf<EqInfo?>(null)
        private set

    private var eq: Equalizer? = null
    private var session = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == session && eq != null) return
        release()
        session = audioSessionId
        val created = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        if (created == null) {
            Logs.add("eq", "equalizer unavailable on this device")
            return
        }
        eq = created
        info = runCatching {
            val range = created.bandLevelRange
            EqInfo(
                bands = created.numberOfBands.toInt(),
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFreqsHz = (0 until created.numberOfBands).map { created.getCenterFreq(it.toShort()) / 1000 },
                presets = (0 until created.numberOfPresets).map { created.getPresetName(it.toShort()) },
            )
        }.getOrNull()
        apply()
    }

    fun parseBands(csv: String): List<Int> = csv.split(',').mapNotNull { it.trim().toIntOrNull() }

    fun apply() {
        val e = eq ?: return
        runCatching {
            if (!Prefs.eqEnabled) {
                e.enabled = false
                return
            }
            val preset = Prefs.eqPreset
            if (preset >= 0 && preset < e.numberOfPresets) {
                e.usePreset(preset.toShort())
            } else {
                val levels = parseBands(Prefs.eqBands)
                val range = e.bandLevelRange
                for (b in 0 until e.numberOfBands) {
                    val level = levels.getOrNull(b) ?: 0
                    e.setBandLevel(b.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
            }
            e.enabled = true
        }.onFailure { Logs.add("eq", "apply failed: ${it.javaClass.simpleName}") }
        applied++
    }

    var applied by mutableStateOf(0)
        private set

    fun levels(@Suppress("UNUSED_PARAMETER") preset: Int): List<Int> {
        applied
        val e = eq ?: return List(info?.bands ?: 0) { 0 }
        return runCatching {
            (0 until e.numberOfBands).map { e.getBandLevel(it.toShort()).toInt() }
        }.getOrElse { List(e.numberOfBands.toInt()) { 0 } }
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
        session = 0
    }
}
