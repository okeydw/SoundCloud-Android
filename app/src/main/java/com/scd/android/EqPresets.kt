package com.scd.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.pow

@Serializable
data class EqCustomPreset(
    val id: String,
    val name: String,
    val gains: List<Double>,
    val preamp: Double? = null,
)

class EqBuiltin(val id: String, val gains: List<Double>)

class EqLegacy(val preset: Int, val bands: String, val user: String)

object EqPresets {
    const val NONE = ""
    const val OFF = "off"
    const val FLAT = "flat"
    const val CUSTOM = "custom"
    const val LIMIT = 20
    const val NAME_MAX = 24

    const val STEP = 0.5

    private const val PRESET = "preset:"
    private const val MINE = "custom:"
    private const val LEGACY_SYS = "sys:"
    private const val LEGACY_USER = "user:"

    val builtin: List<EqBuiltin> = listOf(
        EqBuiltin("flat", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        EqBuiltin("bassBoost", listOf(6.0, 5.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)),
        EqBuiltin("bassDestroyer", listOf(12.0, 12.0, 10.0, 7.0, 3.0, 0.0, -2.0, -4.0, -4.0, -5.0)),
        EqBuiltin("trebleBoost", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 5.0, 6.0)),
        EqBuiltin("vocal", listOf(-2.0, -1.0, 0.0, 2.0, 4.0, 4.0, 3.0, 1.0, 0.0, -1.0)),
        EqBuiltin("rock", listOf(4.0, 3.0, 1.0, 0.0, -1.0, 0.0, 2.0, 3.0, 4.0, 4.0)),
        EqBuiltin("electronic", listOf(5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 1.0, 3.0, 4.0, 5.0)),
        EqBuiltin("classical", listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -2.0, -3.0, -3.0, -4.0)),
        EqBuiltin("loudness", listOf(5.0, 4.0, 1.0, 0.0, -1.0, 0.0, -1.0, 0.0, 3.0, 4.0)),
        EqBuiltin("vShape", listOf(5.0, 3.0, 1.0, -1.0, -3.0, -3.0, -1.0, 1.0, 3.0, 5.0)),
        EqBuiltin("nightMode", listOf(-3.0, -2.0, 0.0, 2.0, 3.0, 3.0, 2.0, 0.0, -2.0, -4.0)),
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(EqCustomPreset.serializer())

    val custom: List<EqCustomPreset> get() = parse(Prefs.eqCustom)

    private fun parse(raw: String): List<EqCustomPreset> =
        raw.takeIf { it.isNotBlank() }?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }.orEmpty()

    private fun store(list: List<EqCustomPreset>) = Prefs.changeEqCustom(json.encodeToString(serializer, list))

    fun builtinById(id: String): EqBuiltin? = builtin.firstOrNull { it.id == id }

    fun customById(id: String): EqCustomPreset? = custom.firstOrNull { it.id == id }

    val limitReached: Boolean get() = custom.size >= LIMIT

    fun setEnabled(on: Boolean) = Prefs.changeEqEnabled(on)

    fun select(id: String) {
        builtinById(id)?.let {
            Prefs.changeEqCurve(it.gains, id, null)
            return
        }
        customById(id)?.let { Prefs.changeEqCurve(it.gains, id, it.preamp) }
    }

    fun setBand(band: Int, gain: Double) {
        if (band !in 0 until EqCurve.BANDS) return
        val snapped = (EqCurve.roundAway(gain / STEP) * STEP).coerceIn(-EqCurve.MAX_GAIN, EqCurve.MAX_GAIN)
        val next = Prefs.eqGains.toMutableList().also { it[band] = snapped }
        if (next == Prefs.eqGains) return
        Prefs.changeEqCurve(next, CUSTOM, Prefs.eqPreamp)
    }

    fun setPreamp(value: Double?) {
        val v = value?.let { (EqCurve.roundAway(it / STEP) * STEP).coerceIn(EqCurve.PREAMP_MIN, EqCurve.PREAMP_MAX) }
        if (v == Prefs.eqPreamp) return
        Prefs.changeEqCurve(Prefs.eqGains, Prefs.eqPresetId, v)
    }

    fun reset() = Prefs.changeEqCurve(EqCurve.FLAT, FLAT, null)

    fun applyImport(result: EqImport) = Prefs.changeEqCurve(result.gains, CUSTOM, result.preamp)

    fun shownPreamp(): Double = EqCurve.resolvePreamp(EqCurve.bandGains(Prefs.eqGains), Prefs.eqPreamp)

    fun saveCustom(rawName: String): Boolean {
        val name = rawName.trim().take(NAME_MAX)
        if (name.isEmpty()) return false
        val list = custom
        val gains = Prefs.eqGains
        val preamp = Prefs.eqPreamp
        val existing = list.firstOrNull { it.name.equals(name, ignoreCase = true) }
        if (existing != null) {
            store(list.map { if (it.id == existing.id) it.copy(name = name, gains = gains, preamp = preamp) else it })
            Prefs.changeEqCurve(gains, existing.id, preamp)
            return true
        }
        if (list.size >= LIMIT) return false
        val id = newId()
        store(list + EqCustomPreset(id, name, gains, preamp))
        Prefs.changeEqCurve(gains, id, preamp)
        return true
    }

    fun deleteCustom(id: String) {
        store(custom.filterNot { it.id == id })
        if (Prefs.eqPresetId == id) Prefs.changeEqCurve(Prefs.eqGains, CUSTOM, Prefs.eqPreamp)
        val key = customKey(id)
        if (Prefs.carEq == key) Prefs.changeCarEq(NONE)
        CarScenarios.clearEq(key)
    }

    private fun newId(): String = "user-" + kotlin.random.Random.nextLong(1L shl 40, Long.MAX_VALUE).toString(36)

    fun presetKey(id: String) = PRESET + id
    fun customKey(id: String) = MINE + id

    fun builtinOfKey(key: String): EqBuiltin? = if (key.startsWith(PRESET)) builtinById(key.removePrefix(PRESET)) else null
    fun customOfKey(key: String): EqCustomPreset? = if (key.startsWith(MINE)) customById(key.removePrefix(MINE)) else null

    fun applyCar(key: String) {
        if (key == NONE) return
        if (Prefs.carEqBackup() == null) {
            Prefs.setCarEqBackup(
                listOf(
                    if (Prefs.eqEnabled) "1" else "0",
                    Prefs.eqPresetId,
                    Prefs.eqGains.joinToString(","),
                    Prefs.eqPreamp?.toString() ?: "",
                ).joinToString("|"),
            )
        }
        when {
            key == OFF -> Prefs.changeEqEnabled(false)
            builtinOfKey(key) != null || customOfKey(key) != null -> {
                select(key.substringAfter(':'))
                Prefs.changeEqEnabled(true)
            }
        }
        Logs.add("car", "equalizer: $key")
    }

    fun restoreCar() {
        val backup = Prefs.carEqBackup() ?: return
        Prefs.setCarEqBackup(null)
        val parts = backup.split('|')
        if (parts.size < 4) return
        val gains = parts[2].split(',').mapNotNull { it.trim().toDoubleOrNull() }
        Prefs.changeEqCurve(gains, parts[1].ifBlank { CUSTOM }, parts[3].toDoubleOrNull())
        Prefs.changeEqEnabled(parts[0] == "1")
        Logs.add("car", "equalizer restored")
    }

    @Serializable
    private data class LegacyUserPreset(val name: String = "", val bands: String = "")

    private fun legacyFreqs(count: Int): List<Double> = when {
        count == 5 -> listOf(60.0, 230.0, 910.0, 3600.0, 14000.0)
        count <= 1 -> listOf(1000.0)
        else -> List(count) { i -> 60.0 * (14000.0 / 60.0).pow(i.toDouble() / (count - 1)) }
    }

    fun legacyToGains(csv: String): List<Double> {
        val levels = csv.split(',').mapNotNull { it.trim().toIntOrNull() }
        if (levels.isEmpty() || levels.all { it == 0 }) return EqCurve.FLAT
        val points = legacyFreqs(levels.size).zip(levels.map { it / 100.0 })
        val target = DoubleArray(EqCurve.grid.size) { EqCurve.interpolate(points, EqCurve.grid[it]) }
        return EqCurve.fit(target).gains.toList()
    }

    fun migrate(old: EqLegacy) {
        val users = old.user.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ListSerializer(LegacyUserPreset.serializer()), it) }.getOrNull() }
            .orEmpty()
            .filter { it.name.isNotBlank() }
        val byName = LinkedHashMap<String, EqCustomPreset>()
        for (u in users.take(LIMIT)) {
            val name = u.name.trim().take(NAME_MAX)
            byName[u.name] = EqCustomPreset(newId(), name, legacyToGains(u.bands), null)
        }
        if (byName.isNotEmpty()) store(custom + byName.values)
        if (old.preset < 0 && old.bands.isNotBlank()) {
            val gains = legacyToGains(old.bands)
            val same = users.firstOrNull { it.bands == old.bands }?.let { byName[it.name] }
            Prefs.changeEqCurve(gains, same?.id ?: CUSTOM, null)
        } else {
            Prefs.changeEqCurve(EqCurve.FLAT, FLAT, null)
        }
        fun mapKey(key: String): String = when {
            key.startsWith(LEGACY_SYS) -> NONE
            key.startsWith(LEGACY_USER) -> byName[key.removePrefix(LEGACY_USER)]?.let { customKey(it.id) } ?: NONE
            else -> key
        }
        val car = mapKey(Prefs.carEq)
        if (car != Prefs.carEq) Prefs.changeCarEq(car)
        CarScenarios.remapEq(::mapKey)
        Prefs.setCarEqBackup(null)
        Logs.add("eq", "moved to 10-band equalizer (${byName.size} presets)")
    }
}
