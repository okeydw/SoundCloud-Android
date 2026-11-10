package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class ArtistProfileState(
    private val urn: String,
    private val user: suspend (String) -> Artist = { Api.user(it) },
    private val userPlaylists: suspend (String) -> List<Playlist> = { Api.userPlaylists(it).collection },
    private val webProfiles: suspend (String) -> List<WebProfile> = { Api.webProfiles(it) },
    private val report: (String, Exception) -> Unit = { part, error ->
        Logs.add("artist", "$part $urn: ${if (error is ApiHttpException) "HTTP ${error.code} ${error.serviceCode.orEmpty()}" else error.javaClass.simpleName}")
    },
) {
    var artist by mutableStateOf<Artist?>(null)
        private set
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var links by mutableStateOf<List<WebProfile>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    suspend fun load() {
        if (loading) return
        loading = true
        failed = false
        try {
            coroutineScope {
                launch { part("profile") { artist = user(urn) } }
                launch { part("playlists") { playlists = userPlaylists(urn).distinctBy { it.urn } } }
                launch {
                    part("links") {
                        links = webProfiles(urn).filter {
                            it.url.startsWith("https://", true) || it.url.startsWith("http://", true)
                        }.distinctBy { it.url }
                    }
                }
            }
        } finally {
            loading = false
        }
    }

    private suspend fun part(name: String, load: suspend () -> Unit) {
        try {
            load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
            report(name, e)
        }
    }
}
