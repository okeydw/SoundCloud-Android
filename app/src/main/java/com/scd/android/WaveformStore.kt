package com.scd.android

internal class WaveformStore(
    initial: Map<String, String> = emptyMap(),
    private val save: (Map<String, String?>) -> Unit = {},
    private val pinned: (String) -> Boolean = { false },
    private val memoryLimit: Int = 300,
    private val diskLimit: Int = 4000,
) {
    private val records = LinkedHashMap(initial)
    private val memory = LinkedHashMap<String, List<Float>>()

    fun get(urn: String): List<Float>? {
        memory.remove(urn)?.let { samples ->
            records.remove(urn)?.let { records[urn] = it }
            memory[urn] = samples
            return samples
        }
        val encoded = records.remove(urn) ?: return null
        val samples = decode(encoded)
        if (samples == null) {
            save(mapOf(urn to null))
            return null
        }
        records[urn] = encoded
        remember(urn, samples)
        return samples
    }

    fun put(urn: String, samples: List<Float>) {
        if (urn.isBlank() || !valid(samples)) return
        if (get(urn) == samples) return
        val stable = samples.toList()
        val encoded = stable.joinToString(",")
        records.remove(urn)
        records[urn] = encoded
        remember(urn, stable)
        val changes = mutableMapOf<String, String?>(urn to encoded)
        if (records.size > diskLimit) {
            val victims = records.keys.filter { it != urn && !pinned(it) }.iterator()
            while (records.size > diskLimit && victims.hasNext()) {
                val victim = victims.next()
                records.remove(victim)
                memory.remove(victim)
                changes[victim] = null
            }
        }
        save(changes)
    }

    fun clear(keepPinned: Boolean = true) {
        memory.clear()
        val victims = records.keys.filter { !keepPinned || !pinned(it) }
        victims.forEach { records.remove(it) }
        if (victims.isNotEmpty()) save(victims.associateWith { null })
    }

    private fun remember(urn: String, samples: List<Float>) {
        memory.remove(urn)
        memory[urn] = samples
        while (memory.size > memoryLimit) memory.remove(memory.keys.first())
    }

    private fun decode(encoded: String): List<Float>? {
        if (encoded.length > 65536) return null
        val parts = encoded.split(',')
        if (parts.size !in 1..4096) return null
        val samples = parts.map { it.toFloatOrNull() ?: return null }
        return samples.takeIf(::valid)
    }

    private fun valid(samples: List<Float>): Boolean =
        samples.size in 1..4096 && samples.all { it.isFinite() && it in 0f..1f }
}
