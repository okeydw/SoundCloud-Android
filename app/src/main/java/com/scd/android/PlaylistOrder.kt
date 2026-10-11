package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

object PlaylistOrder {
    private var sp: SharedPreferences? = null

    var version by mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        if (sp == null) sp = context.getSharedPreferences("playlist_order", Context.MODE_PRIVATE)
    }

    fun get(urn: String?): List<String>? {
        if (urn == null) return null
        return sp?.getString(urn, null)?.split(',')?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
    }

    fun has(urn: String?): Boolean = get(urn) != null

    fun set(keys: Collection<String?>, order: List<String>) {
        val edit = sp?.edit() ?: return
        keys.filterNotNull().forEach { edit.putString(it, order.joinToString(",")) }
        edit.apply()
        version++
    }

    fun clear(keys: Collection<String?>) {
        val edit = sp?.edit() ?: return
        keys.filterNotNull().forEach { edit.remove(it) }
        edit.apply()
        version++
    }

    fun apply(keys: Collection<String?>, tracks: List<Track>): List<Track> {
        val order = keys.firstNotNullOfOrNull { get(it) } ?: return tracks
        val rank = order.withIndex().associate { (i, u) -> u to i }
        return tracks.sortedBy { rank[it.urn] ?: Int.MAX_VALUE }
    }
}
