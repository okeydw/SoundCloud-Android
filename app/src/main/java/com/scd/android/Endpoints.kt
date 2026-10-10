package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

object Endpoints {
    private lateinit var sp: SharedPreferences

    private const val RELAY_ZONE = "relay.scnative.space"
    private const val MAX_RELAY = 16
    private val DEFAULT_RELAYS = listOf("r1", "r2")

    @Volatile
    var relayNodes: List<String> = DEFAULT_RELAYS
        private set

    private fun relayHosts(service: String) = relayNodes.map { "https://$service.$it.$RELAY_ZONE" }

    val apiHosts: List<String>
        get() = listOf("https://api.scnative.space") + relayHosts("api")

    val streamHosts: List<String>
        get() = listOf("https://stream.scnative.space") + relayHosts("stream")

    val imageHosts: List<String>
        get() = listOf("https://images.scnative.space") + relayHosts("images")

    private const val TRUSTED_ZONE = "scnative.space"

    fun trustedHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == TRUSTED_ZONE || h.endsWith(".$TRUSTED_ZONE")
    }

    fun trustedUrl(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://", ignoreCase = true)) return false
        val host = u.substring(8).substringBefore('/').substringBefore('?').substringBefore(':').substringAfterLast('@')
        return trustedHost(host)
    }

    const val API_STAR = "https://api-star.scnative.space"
    const val STREAM_STAR = "https://stream-star.scnative.space"
    const val STORAGE_STAR = "https://storage-star.scnative.space"
    const val STORAGE_MAIN = "https://storage.scnative.space"

    val starApiActive: Boolean get() = Prefs.star && Prefs.apiStar

    fun hostsFor(path: String): List<String> {
        val hosts = apiHosts
        val n = hosts.size
        val main = (0 until n).map { hosts[(apiIndex + it) % n] }
        if (path.startsWith("/auth") || !starApiActive) return main
        return listOf(API_STAR) + main
    }

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
        sp.edit().remove("relays").apply()
        relayNodes = sp.getString("relays_checked", null)
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.matches(Regex("r\\d{1,2}")) }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_RELAYS
        apiIndex = sp.getInt("api2", 0).coerceIn(0, apiHosts.lastIndex)
        streamIndex = sp.getInt("stream2", 0).coerceIn(0, streamHosts.lastIndex)
        imageIndex = sp.getInt("image2", 0).coerceIn(0, imageHosts.lastIndex)
    }

    val apiBase get() = apiHosts.let { it[apiIndex.coerceIn(0, it.lastIndex)] }
    val streamBase get() = streamHosts.let { it[streamIndex.coerceIn(0, it.lastIndex)] }
    val imageBase get() = imageHosts.let { it[imageIndex.coerceIn(0, it.lastIndex)] }

    val apiHostnames: List<String> get() = (apiHosts + API_STAR).map { it.removePrefix("https://") }
    val imageHostnames: List<String> get() = imageHosts.map { it.removePrefix("https://") }

    fun apiHostAt(i: Int) = apiHosts[i]

    fun commitApi(i: Int) { if (i >= 0) { apiIndex = i; persist("api2", i) } }
    fun commitStream(i: Int) { if (i >= 0) { streamIndex = i; persist("stream2", i) } }
    fun commitImage(i: Int) { if (i >= 0) { imageIndex = i; persist("image2", i) } }

    fun rotateStream(): Int {
        val next = (streamIndex + 1) % streamHosts.size
        commitStream(next)
        return next
    }

    private fun persist(key: String, value: Int) {
        if (::sp.isInitialized) sp.edit().putInt(key, value).apply()
    }

    private val quickClient by lazy {
        Api.http.newBuilder()
            .cache(null)
            .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private val TOPOLOGY_URLS = listOf(
        "https://health.scnative.space/topology",
        "https://health.r1.relay.scnative.space/topology",
        "https://health.r2.relay.scnative.space/topology",
    )

    private suspend fun topologyRelays(): List<String>? = withContext(Dispatchers.IO) {
        for (url in TOPOLOGY_URLS) {
            val nodes = runCatching {
                val req = Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()
                quickClient.newCall(req).execute().use { res ->
                    if (!res.isSuccessful) return@use null
                    val root = kotlinx.serialization.json.Json.parseToJsonElement(res.body?.string() ?: "")
                    val arr = (root as? kotlinx.serialization.json.JsonObject)?.get("relays") as? kotlinx.serialization.json.JsonArray
                    arr?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?.filter { it.matches(Regex("r\\d{1,2}")) }
                }
            }.getOrNull()
            if (!nodes.isNullOrEmpty()) return@withContext nodes
        }
        null
    }

    private suspend fun relayAlive(n: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://api.r$n.$RELAY_ZONE/health").cacheControl(CacheControl.FORCE_NETWORK).build()
            quickClient.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun discoverRelays() {
        val fromTopology = topologyRelays()
        val nodes = if (fromTopology != null) {
            fromTopology.distinct().sortedBy { it.removePrefix("r").toIntOrNull() ?: 0 }
        } else {
            val base = DEFAULT_RELAYS.mapNotNull { it.removePrefix("r").toIntOrNull() }.toSet()
            val probed = kotlinx.coroutines.coroutineScope {
                ((base.maxOrNull() ?: 0) + 1..MAX_RELAY).map { n ->
                    async { n to relayAlive(n) }
                }.map { it.await() }
            }
            (base + probed.filter { it.second }.map { it.first }).sorted().map { "r$it" }
        }
        if (nodes != relayNodes) {
            relayNodes = nodes
            Logs.add("host", "relays: ${nodes.joinToString(", ")}")
        }
        if (::sp.isInitialized) sp.edit().putString("relays_checked", nodes.joinToString(",")).apply()
        apiIndex = apiIndex.coerceIn(0, apiHosts.lastIndex)
        streamIndex = streamIndex.coerceIn(0, streamHosts.lastIndex)
        imageIndex = imageIndex.coerceIn(0, imageHosts.lastIndex)
    }

    private suspend fun healthy(base: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$base/health").cacheControl(CacheControl.FORCE_NETWORK).build()
            quickClient.newCall(req).execute().use { it.isSuccessful }
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
        runCatching { discoverRelays() }
        pick("api", apiHosts, apiIndex.coerceIn(0, apiHosts.lastIndex), ::commitApi)
        pick("stream", streamHosts, streamIndex.coerceIn(0, streamHosts.lastIndex), ::commitStream)
        pick("image", imageHosts, imageIndex.coerceIn(0, imageHosts.lastIndex), ::commitImage)
    }
}
