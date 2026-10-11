package com.scd.android

import android.content.Context
import android.content.SharedPreferences

object DownloadFailures {
    private const val DAY = 24L * 60 * 60 * 1000
    private val backoff = longArrayOf(DAY, 3 * DAY, 7 * DAY, 30 * DAY)

    private var sp: SharedPreferences? = null

    fun init(context: Context) {
        sp = context.getSharedPreferences("download_failures", Context.MODE_PRIVATE)
    }

    private fun read(urn: String): Pair<Int, Long>? {
        val raw = sp?.getString(urn, null) ?: return null
        val count = raw.substringBefore(':').toIntOrNull() ?: return null
        val at = raw.substringAfter(':').toLongOrNull() ?: return null
        return count to at
    }

    fun record(urn: String) {
        val count = (read(urn)?.first ?: 0) + 1
        sp?.edit()?.putString(urn, "$count:${System.currentTimeMillis()}")?.apply()
    }

    fun clear(urn: String) {
        if (sp?.contains(urn) == true) sp?.edit()?.remove(urn)?.apply()
    }

    fun failures(urn: String): Int = read(urn)?.first ?: 0

    fun backingOff(urn: String): Boolean {
        val (count, at) = read(urn) ?: return false
        val wait = backoff[(count - 1).coerceIn(0, backoff.lastIndex)]
        return System.currentTimeMillis() - at < wait
    }
}
