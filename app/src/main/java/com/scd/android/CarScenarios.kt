package com.scd.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class CarScenario(
    val id: String,
    val name: String = "",
    val devices: Map<String, String> = emptyMap(),
    val source: String = CarAutoplay.LIKED,
    val sourceTitle: String = "",
    val shuffle: Boolean = false,
    val autostart: Boolean = true,
    val eq: String = EqPresets.NONE,
) {
    val anyDevice: Boolean get() = devices.isEmpty()
}

object CarScenarios {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val serializer = ListSerializer(CarScenario.serializer())

    val all: List<CarScenario>
        get() = Prefs.carScenarios.takeIf { it.isNotBlank() }
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }
            ?: legacy()

    private fun legacy(): List<CarScenario> = listOf(
        CarScenario(
            id = "default",
            devices = Prefs.carDevices.associateWith { it },
            source = Prefs.carSource,
            sourceTitle = Prefs.carSourceTitle,
            shuffle = Prefs.carShuffle,
            autostart = Prefs.carAutostart,
            eq = Prefs.carEq,
        ),
    )

    private fun store(list: List<CarScenario>) = Prefs.changeCarScenarios(json.encodeToString(serializer, list))

    fun newId(): String = System.currentTimeMillis().toString(36) + kotlin.random.Random.nextInt(0, Int.MAX_VALUE).toString(36)

    fun owner(address: String, except: String?): CarScenario? = all.firstOrNull { it.id != except && address in it.devices }

    fun anyOwner(except: String?): CarScenario? = all.firstOrNull { it.id != except && it.anyDevice }

    fun conflict(s: CarScenario): CarScenario? =
        if (s.anyDevice) anyOwner(s.id) else s.devices.keys.firstNotNullOfOrNull { owner(it, s.id) }

    fun save(s: CarScenario): Boolean {
        if (conflict(s) != null) return false
        val list = all
        store(if (list.any { it.id == s.id }) list.map { if (it.id == s.id) s else it } else list + s)
        Logs.add("car", "scenario saved: ${s.name.ifBlank { s.id }.take(24)} (${if (s.anyDevice) "any device" else "${s.devices.size} devices"})")
        return true
    }

    fun delete(id: String) = store(all.filterNot { it.id == id })

    fun forDevice(address: String?): CarScenario? {
        val list = all
        return address?.let { a -> list.firstOrNull { a in it.devices } } ?: list.firstOrNull { it.anyDevice }
    }

    fun clearEq(key: String) {
        val list = all
        if (list.any { it.eq == key }) store(list.map { if (it.eq == key) it.copy(eq = EqPresets.NONE) else it })
    }
}
