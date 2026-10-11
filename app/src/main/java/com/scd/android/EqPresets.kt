package com.scd.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class EqUserPreset(val name: String, val bands: String)

object EqPresets {
    const val NONE = ""
    const val OFF = "off"
    private const val SYS = "sys:"
    private const val USER = "user:"

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(EqUserPreset.serializer())

    val user: List<EqUserPreset>
        get() = Prefs.eqUser.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            .orEmpty()

    private fun store(list: List<EqUserPreset>) = Prefs.changeEqUser(json.encodeToString(serializer, list))

    fun save(name: String, bands: String) {
        val clean = name.trim().take(32)
        if (clean.isEmpty()) return
        store(user.filterNot { it.name.equals(clean, ignoreCase = true) } + EqUserPreset(clean, bands))
        Prefs.changeEq(true, -1, bands)
    }

    fun delete(name: String) {
        store(user.filterNot { it.name == name })
        if (Prefs.carEq == userKey(name)) Prefs.changeCarEq(NONE)
    }

    fun use(p: EqUserPreset) = Prefs.changeEq(true, -1, p.bands)

    fun isActive(p: EqUserPreset): Boolean =
        Prefs.eqEnabled && Prefs.eqPreset < 0 && AudioFx.parseBands(p.bands) == AudioFx.parseBands(Prefs.eqBands)

    fun activeUser(): EqUserPreset? = user.firstOrNull { isActive(it) }

    fun hideSystem(index: Int) {
        Prefs.changeEqHidden(Prefs.eqHidden + index)
        if (Prefs.eqPreset == index) Prefs.changeEq(Prefs.eqEnabled, -1, AudioFx.levels(index).joinToString(","))
        if (Prefs.carEq == sysKey(index)) Prefs.changeCarEq(NONE)
    }

    fun restoreSystem() = Prefs.changeEqHidden(emptySet())

    fun visibleSystem(names: List<String>): List<Pair<Int, String>> =
        names.mapIndexedNotNull { i, n -> if (i in Prefs.eqHidden) null else i to n }

    fun sysKey(index: Int) = SYS + index
    fun userKey(name: String) = USER + name

    fun label(key: String, systemNames: List<String>): String? = when {
        key.startsWith(SYS) -> key.removePrefix(SYS).toIntOrNull()?.let { systemNames.getOrNull(it) }
        key.startsWith(USER) -> key.removePrefix(USER)
        else -> null
    }

    fun applyCar() {
        val key = Prefs.carEq
        if (key == NONE) return
        if (Prefs.carEqBackup() == null) {
            Prefs.setCarEqBackup(listOf(if (Prefs.eqEnabled) "1" else "0", Prefs.eqPreset.toString(), Prefs.eqBands).joinToString("|"))
        }
        when {
            key == OFF -> Prefs.changeEq(false, Prefs.eqPreset, Prefs.eqBands)
            key.startsWith(SYS) -> key.removePrefix(SYS).toIntOrNull()?.let { Prefs.changeEq(true, it, Prefs.eqBands) }
            key.startsWith(USER) -> user.firstOrNull { it.name == key.removePrefix(USER) }?.let { use(it) }
        }
        Logs.add("car", "equalizer: $key")
    }

    fun restoreCar() {
        val backup = Prefs.carEqBackup() ?: return
        Prefs.setCarEqBackup(null)
        val parts = backup.split('|', limit = 3)
        if (parts.size < 3) return
        Prefs.changeEq(parts[0] == "1", parts[1].toIntOrNull() ?: -1, parts[2])
        Logs.add("car", "equalizer restored")
    }
}
