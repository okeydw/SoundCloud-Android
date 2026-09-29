package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

object DurationCache {
    private const val MAX = 4000
    private var sp: SharedPreferences? = null
    private val map = mutableStateMapOf<String, Long>()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences("durations", Context.MODE_PRIVATE)
        sp = prefs
        runCatching {
            for ((k, v) in prefs.all) {
                if (v is Long && v > 0L) map[k] = v
            }
        }
    }

    fun get(urn: String): Long = map[urn] ?: 0L

    private val asked = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val gate = kotlinx.coroutines.sync.Semaphore(3)

    suspend fun resolve(urn: String) {
        if ((map[urn] ?: 0L) > 45_000L || !asked.add(urn)) return
        try {
            gate.acquire()
        } catch (e: kotlinx.coroutines.CancellationException) {
            asked.remove(urn)
            throw e
        }
        try {
            Api.trackByUrn(urn)
        } catch (e: kotlinx.coroutines.CancellationException) {
            asked.remove(urn)
            throw e
        } catch (_: Exception) {
        } finally {
            gate.release()
        }
    }

    fun record(urn: String?, ms: Long) {
        if (urn.isNullOrEmpty() || ms <= 0L) return
        val known = map[urn] ?: 0L
        if (ms <= known + 1_000L) return
        map[urn] = ms
        val prefs = sp ?: return
        runCatching {
            val edit = prefs.edit()
            if (map.size > MAX) {
                val drop = map.keys.take(map.size - MAX + 500).filter { it != urn }
                drop.forEach {
                    map.remove(it)
                    edit.remove(it)
                }
            }
            edit.putLong(urn, ms).apply()
        }
    }
}
