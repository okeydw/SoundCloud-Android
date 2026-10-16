package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request

data class NodeStatus(
    val node: String,
    val host: String,
    val ms: Long?,
    val bytes: Long? = null,
    val kbps: Long? = null,
    val tampered: Boolean = false,
) {
    val ok: Boolean get() = ms != null
    val carries: Boolean get() = !tampered && bytes != null && bytes >= Endpoints.PROBE_BYTES
    val cut: Boolean get() = ok && (tampered || (bytes != null && bytes < Endpoints.PROBE_BYTES))
    val rank: Int get() = when {
        !ok -> 3
        carries -> 0
        bytes == null && !tampered -> 1
        else -> 2
    }
}

object Endpoints {
    const val AUTO = "auto"
    const val DIRECT = "direct"
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

    fun storageRelays(star: Boolean): List<String> = relayHosts(if (star) "storage-star" else "storage")

    private const val TRUSTED_ZONE = "scnative.space"

    fun trustedHost(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        return h == TRUSTED_ZONE || h.endsWith(".$TRUSTED_ZONE")
    }

    fun trustedUrl(url: String): Boolean {
        val u = url.trim()
        if (!u.startsWith("https://", ignoreCase = true)) return false
        val authority = u.substring(8).takeWhile { it != '/' && it != '?' && it != '#' && it != '\\' }
        if (authority.isEmpty() || authority.any { it == '@' || it == '%' || it.isWhitespace() }) return false
        return trustedHost(authority.substringBefore(':'))
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
        relayMode = sp.getString("relay_mode", AUTO) ?: AUTO
    }

    var relayMode by mutableStateOf(AUTO)
        private set
    var statuses by mutableStateOf<List<NodeStatus>>(emptyList())
        private set
    var measuring by mutableStateOf(false)
        private set

    val nodes: List<String> get() = listOf(DIRECT) + relayNodes

    val activeNode: String get() = nodes.getOrElse(apiIndex) { DIRECT }

    fun hostOf(node: String): String =
        if (node == DIRECT) "api.scnative.space" else "api.$node.$RELAY_ZONE"

    private fun applyNode(node: String) {
        val i = nodes.indexOf(node)
        if (i < 0) return
        val changed = i != apiIndex || i != streamIndex || i != imageIndex
        commitApi(i)
        commitStream(i)
        commitImage(i)
        if (changed) Logs.add("host", "node → ${hostOf(node)}")
    }

    private fun bestNode(list: List<NodeStatus>): String? =
        list.filter { it.ok }.minWithOrNull(compareBy<NodeStatus>({ it.rank }, { it.ms ?: Long.MAX_VALUE }))?.node

    const val PROBE_BYTES = 64L * 1024

    private val volumeClient by lazy {
        Api.http.newBuilder()
            .cache(null)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private class Volume(val bytes: Long?, val kbps: Long?, val tampered: Boolean)

    private suspend fun volume(node: String): Volume = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://${hostOf(node)}/probe?bytes=$PROBE_BYTES")
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        var read = 0L
        var digest: String? = null
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val started = System.nanoTime()
        var firstByteAt = 0L
        var answered = false
        val served = runCatching {
            volumeClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@use false
                answered = true
                digest = res.header("x-probe-sha256")?.lowercase()
                val input = res.body?.byteStream() ?: return@use false
                val buf = ByteArray(8 * 1024)
                while (read < PROBE_BYTES) {
                    val n = runCatching { input.read(buf) }.getOrDefault(-1)
                    if (n < 0) break
                    if (firstByteAt == 0L) firstByteAt = System.nanoTime()
                    md.update(buf, 0, n)
                    read += n
                }
                true
            }
        }.getOrElse { read > 0 }
        if (!served) return@withContext Volume(if (answered || read > 0) read else null, null, false)
        val elapsedMs = ((System.nanoTime() - (if (firstByteAt > 0) firstByteAt else started)) / 1_000_000).coerceAtLeast(1)
        val tampered = read >= PROBE_BYTES && digest != null &&
            md.digest().joinToString("") { "%02x".format(it) } != digest
        Volume(read, read * 8 / elapsedMs, tampered)
    }

    suspend fun choose(node: String) {
        relayMode = node
        if (::sp.isInitialized) sp.edit().putString("relay_mode", node).apply()
        if (node == AUTO) {
            val list = statuses.ifEmpty { measure() }
            bestNode(list)?.let { applyNode(it) }
        } else {
            applyNode(node)
        }
    }

    private suspend fun ping(node: String): Long? = withContext(Dispatchers.IO) {
        var best: Long? = null
        repeat(3) {
            val ms = runCatching {
                val req = Request.Builder().url("https://${hostOf(node)}/health").cacheControl(CacheControl.FORCE_NETWORK).build()
                val started = System.nanoTime()
                quickClient.newCall(req).execute().use { res ->
                    if (res.isSuccessful) (System.nanoTime() - started) / 1_000_000 else null
                }
            }.getOrNull()
            if (ms != null) best = minOf(best ?: ms, ms)
        }
        best
    }

    suspend fun measure(): List<NodeStatus> {
        measuring = true
        return try {
            val list = coroutineScope {
                nodes.map { n ->
                    async {
                        val ms = ping(n)
                        val v = if (ms != null) volume(n) else null
                        NodeStatus(n, hostOf(n), ms, v?.bytes, v?.kbps, v?.tampered == true)
                    }
                }.awaitAll()
            }
            statuses = list
            Logs.add(
                "host",
                list.joinToString(", ") { st ->
                    val vol = when {
                        !st.ok -> ""
                        st.tampered -> " tamper"
                        st.bytes == null -> " 64K?"
                        st.carries -> " 64K ok ${st.kbps ?: 0}kbit"
                        else -> " cut@${(st.bytes ?: 0L) / 1024}K"
                    }
                    "${st.node} ${st.ms?.let { ms -> "${ms}ms" } ?: "down"}$vol"
                },
            )
            list
        } finally {
            measuring = false
        }
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
        val list = measure()
        val mode = relayMode
        val manual = mode != AUTO && list.any { it.node == mode }
        val target = when {
            manual && list.first { it.node == mode }.ok -> mode
            manual -> bestNode(list)?.also { Logs.add("host", "${hostOf(mode)} unavailable, using ${hostOf(it)} for now") }
            else -> bestNode(list)
        }
        if (target != null) {
            applyNode(target)
        } else {
            pick("api", apiHosts, apiIndex.coerceIn(0, apiHosts.lastIndex), ::commitApi)
            pick("stream", streamHosts, streamIndex.coerceIn(0, streamHosts.lastIndex), ::commitStream)
            pick("image", imageHosts, imageIndex.coerceIn(0, imageHosts.lastIndex), ::commitImage)
        }
    }
}
