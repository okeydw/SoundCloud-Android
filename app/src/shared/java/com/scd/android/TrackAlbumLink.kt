package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException

internal data class TrackAlbumLink(val id: String, val title: String, val playlist: Playlist? = null)

@Composable
internal fun rememberTrackAlbum(track: Track, resolve: Boolean = true): TrackAlbumLink? {
    val saved = remember(track.urn, LocalPlaylists.all) {
        LocalPlaylists.visible.firstOrNull { it.savedAlbum && it.tracks.any { entry -> entry.urn == track.urn } }
    }
    val known = track.enrichment?.album?.takeIf { it.id.isNotBlank() }
    var fetched by remember(track.urn) { mutableStateOf<EnrichmentAlbum?>(null) }
    LaunchedEffect(track.urn, known, saved, Prefs.offline, resolve) {
        if (resolve && known == null && saved == null && fetched == null && !Prefs.offline && isScUrn(track.urn)) {
            try { fetched = Api.trackByUrn(track.urn).enrichment?.album }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) {}
        }
    }
    val catalog = known ?: fetched
    if (catalog != null && catalog.id.isNotBlank() && (!Prefs.offline || LocalPlaylists.cachedAlbum(catalog.id) != null))
        return TrackAlbumLink(catalog.id, catalog.title)
    return saved?.let { TrackAlbumLink(it.id, it.title, it.asPlaylist()) }
}
