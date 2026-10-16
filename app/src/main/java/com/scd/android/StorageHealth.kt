package com.scd.android

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
import java.io.IOException

enum class StorageState { Ok, Failing, Down }

data class StorageProbe(val base: String, val star: Boolean, val state: StorageState, val ms: Long?, val code: Int?) {
    val host: String get() = base.removePrefix("https://")
    val relay: String? get() = host.split('.').getOrNull(1)?.takeIf { host.contains(".relay.") }
}

object StorageHealth {
    private const val PROBE_FILE = "soundcloud_tracks_0.m4a"

    var probes by mutableStateOf<List<StorageProbe>>(emptyList())
        private set
    var checking by mutableStateOf(false)
        private set

    private fun targets(): List<Pair<String, Boolean>> {
        val main = (listOf(Endpoints.STORAGE_MAIN) + Endpoints.storageRelays(false)).map { it to false }
        if (!Prefs.star) return main
        return (listOf(Endpoints.STORAGE_STAR) + Endpoints.storageRelays(true)).map { it to true } + main
    }

    suspend fun check(): List<StorageProbe> {
        if (checking) return probes
        checking = true
        try {
            val file = ScDataSource.knownStorageFile ?: PROBE_FILE
            val list = withContext(Dispatchers.IO) {
                val client = ScDataSource.storageClient(Api.http)
                coroutineScope {
                    targets().map { (base, star) ->
                        async {
                            val started = System.currentTimeMillis()
                            try {
                                val req = Request.Builder()
                                    .url("$base/$file")
                                    .header("Range", "bytes=0-1")
                                    .cacheControl(CacheControl.Builder().noStore().build())
                                    .build()
                                client.newCall(req).execute().use { res ->
                                    val state = if (res.code >= 500) StorageState.Failing else StorageState.Ok
                                    StorageProbe(base, star, state, System.currentTimeMillis() - started, res.code)
                                }
                            } catch (e: IOException) {
                                StorageProbe(base, star, StorageState.Down, null, null)
                            }
                        }
                    }.awaitAll()
                }
            }
            probes = list
            Logs.add(
                "host",
                "storage " + list.joinToString(", ") { p ->
                    val name = listOfNotNull(if (p.star) "star" else "main", p.relay).joinToString(" ")
                    when (p.state) {
                        StorageState.Ok -> "$name ${p.ms}ms"
                        StorageState.Failing -> "$name HTTP ${p.code}"
                        StorageState.Down -> "$name down"
                    }
                },
            )
            return list
        } finally {
            checking = false
        }
    }
}
