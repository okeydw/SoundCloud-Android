package com.scd.android

import androidx.compose.ui.res.stringResource
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun PlaylistScreen(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
) {
    var tracks by remember(playlist.urn) { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember(playlist.urn) { mutableStateOf(0) }
    var hasMore by remember(playlist.urn) { mutableStateOf(false) }
    var loading by remember(playlist.urn) { mutableStateOf(false) }
    var notSaved by remember(playlist.urn) { mutableStateOf(false) }
    var dropping by remember(playlist.urn) { mutableStateOf<List<String>?>(null) }
    val whole: suspend () -> List<Track> = { Library.playlistAll(playlist.urn, Prefs.offline) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val liked = LikedPlaylists.isLiked(playlist.urn)
    val controller = LocalMediaController.current

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { LikedPlaylists.seed() }

    val own = LocalPlaylists.find(playlist.urn)

    fun load(p: Int) {
        if (loading) return
        scope.launch {
            loading = true
            fun apply(res: Pair<List<Track>, Boolean>) {
                tracks = if (p == 0) res.first else (tracks + res.first).distinctBy { t -> t.urn }
                page = p
                hasMore = res.second
            }
            runCatching { Library.playlistPage(playlist.urn, p, fresh = false, offline = Prefs.offline) }
                .onSuccess { apply(it) }
                .onFailure { if (p == 0) notSaved = it is Library.NotSavedOfflineException }
            if (!Prefs.offline) {
                runCatching { Library.playlistPage(playlist.urn, p, fresh = true, offline = false) }.onSuccess { apply(it) }
            }
            loading = false
        }
    }

    LaunchedEffect(playlist.urn) { load(0) }

    LaunchedEffect(own?.tracks) {
        val live = own?.tracks ?: return@LaunchedEffect
        if (live.isNotEmpty()) tracks = live
    }

    LaunchedEffect(playlist.urn, hasMore, page, loading) {
        if (!hasMore || loading || tracks.isEmpty() || tracks.size >= 1000) return@LaunchedEffect
        val next = page + 1
        val res = runCatching { Library.playlistPage(playlist.urn, next, fresh = false, offline = Prefs.offline) }.getOrNull()
            ?: return@LaunchedEffect
        tracks = (tracks + res.first).distinctBy { it.urn }
        page = next
        hasMore = res.second
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), iconLabel(R.drawable.ic_arrow_back))
            }
            Text(
                own?.title ?: playlist.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val pickCover = rememberCoverPicker(playlist.urn)
            var reordering by remember { mutableStateOf(false) }
            if (reordering && own != null) {
                ReorderDialog(tracks, onDismiss = { reordering = false }) { order ->
                    reordering = false
                    LocalPlaylists.reorder(own.id, order)
                }
            }
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(painterResource(R.drawable.ic_more_vert), iconLabel(R.drawable.ic_more_vert), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cover_change)) },
                        onClick = {
                            menuOpen = false
                            pickCover()
                        },
                    )
                    if (PlaylistCovers.has(playlist.urn)) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cover_reset)) },
                            onClick = {
                                menuOpen = false
                                PlaylistCovers.clear(playlist.urn)
                            },
                        )
                    }
                    if (own != null && tracks.size > 1) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.reorder_title)) },
                            onClick = {
                                menuOpen = false
                                reordering = true
                            },
                        )
                    }
                    if (tracks.any { Downloads.isDownloaded(it.urn) }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.playlist_remove_downloads)) },
                            onClick = {
                                menuOpen = false
                                scope.launch {
                                    dropping = Library.playlistDownloads(playlist.urn, tracks, Prefs.offline).takeIf { it.isNotEmpty() }
                                }
                            },
                        )
                    }
                }
            }
            if (tracks.isNotEmpty()) {
                val shuffled = Playback.shuffledFrom(playlist.urn)
                IconButton(onClick = { Playback.toggleShuffle(controller, playlist.urn, tracks, more = whole) }) {
                    Icon(
                        painterResource(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle),
                        stringResource(R.string.notif_shuffle),
                        tint = if (shuffled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (own == null) {
                IconButton(onClick = { scope.launch { LikedPlaylists.toggle(playlist) } }) {
                    Icon(
                        painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        iconLabel(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val allDownloaded = tracks.isNotEmpty() && !hasMore &&
                tracks.all { Downloads.isDownloaded(it.urn) }
            val downloading = playlist.urn in Downloads.activeLists
            when {
                downloading -> IconButton(onClick = { Downloads.cancelList(context, playlist.urn) }) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                allDownloaded -> Icon(
                    painterResource(R.drawable.ic_check),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(12.dp).size(20.dp),
                )
                else -> IconButton(onClick = {
                    Downloads.enqueue(context, tracks, playlist.urn, playlist.title)
                }) {
                    Icon(painterResource(R.drawable.ic_download), iconLabel(R.drawable.ic_download), modifier = Modifier.size(20.dp))
                }
            }
        }
        dropping?.let { urns ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { dropping = null },
                title = { Text(stringResource(R.string.playlist_remove_downloads)) },
                text = { Text(stringResource(R.string.playlist_remove_downloads_q, urns.size)) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        dropping = null
                        if (playlist.urn in Downloads.activeLists) Downloads.cancelList(context, playlist.urn)
                        App.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            Downloads.removeAll(urns)
                            android.widget.Toast.makeText(context.applicationContext, context.getString(R.string.playlist_downloads_removed, urns.size), android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { dropping = null }) { Text(stringResource(R.string.cancel)) }
                },
            )
        }
        TrackList(
            tracks = tracks,
            loading = loading,
            error = null,
            emptyText = if (notSaved) stringResource(R.string.playlist_not_offline) else "",
            canLoadMore = hasMore,
            onLoadMore = { load(page + 1) },
            onPlay = { Playback.play(controller, tracks, it, source = playlist.urn, more = whole) },
            onRemove = if (own != null) { t -> LocalPlaylists.remove(own.id, t.urn) } else null,
        )
    }
}
