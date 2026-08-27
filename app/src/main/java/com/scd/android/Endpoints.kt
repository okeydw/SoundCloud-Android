package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

object Endpoints {
    private lateinit var sp: SharedPreferences

    val apiHosts = listOf(
        "https://api.scdinternal.site",
        "https://api.r1.relay.scnative.space",
        "https://api.r2.relay.scnative.space",
    )
    val streamHosts = listOf(
        "https://stream.scdinternal.site",
        "https://stream.r1.relay.scnative.space",
        "https://stream.r2.relay.scnative.space",
    )
    val imageHosts = listOf(
        "https://images.scdinternal.site",
        "https://images.r1.relay.scnative.space",
        "https://images.r2.relay.scnative.space",
    )

    @Volatile
    var apiIndex = 0
        private set

    @Volatile
    var streamIndex = 0
        private set

    @Volatile
    var imageIndex = 0
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("endpoints", Context.MODE_PRIVATE)
        apiIndex = sp.getInt("api", 0).coerceIn(0, apiHosts.lastIndex)
        streamIndex = sp.getInt("stream", 0).coerceIn(0, streamHosts.lastIndex)
        imageIndex = sp.getInt("image", 0).coerceIn(0, imageHosts.lastIndex)
    }

    val apiBase get() = apiHosts[apiIndex]
    val streamBase get() = streamHosts[streamIndex]
    val imageBase get() = imageHosts[imageIndex]

    val apiHostnames = apiHosts.map { it.removePrefix("https://") }
    val imageHostnames = imageHosts.map { it.removePrefix("https://") }

    fun apiHostAt(i: Int) = apiHosts[i]

    fun commitApi(i: Int) { apiIndex = i; persist("api", i) }
    fun commitStream(i: Int) { streamIndex = i; persist("stream", i) }
    fun commitImage(i: Int) { imageIndex = i; persist("image", i) }

    fun rotateStream(): Int {
        val next = (streamIndex + 1) % streamHosts.size
        commitStream(next)
        return next
    }

    private fun persist(key: String, value: Int) {
        if (::sp.isInitialized) sp.edit().putInt(key, value).apply()
    }

    private suspend fun healthy(base: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$base/health").cacheControl(CacheControl.FORCE_NETWORK).build()
            Api.http.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun pick(label: String, hosts: List<String>, current: Int, commit: (Int) -> Unit) {
        val order = (current until hosts.size) + (0 until current)
        for (i in order) {
            if (healthy(hosts[i])) {
                if (i != current) {
                    commit(i)
                    Logs.add("host", "$label → ${hosts[i].removePrefix("https://")}")
                }
                return
            }
        }
        Logs.add("host", "$label: no healthy host")
    }

    suspend fun probeAll() {
        pick("api", apiHosts, apiIndex, ::commitApi)
        pick("stream", streamHosts, streamIndex, ::commitStream)
        pick("image", imageHosts, imageIndex, ::commitImage)
    }
}
