package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val liked = LikedPlaylists.isLiked(playlist.urn)

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) { LikedPlaylists.seed() }

    fun load(p: Int) {
        if (loading) return
        scope.launch {
            loading = true
            runCatching { Api.playlistTracks(playlist.urn, p) }.onSuccess {
                tracks = if (p == 0) it.collection else (tracks + it.collection).distinctBy { t -> t.urn }
                page = p
                hasMore = it.has_more
            }
            loading = false
        }
    }

    LaunchedEffect(playlist.urn) { load(0) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                playlist.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { scope.launch { LikedPlaylists.toggle(playlist) } }) {
                Icon(
                    painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    null,
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (downloading) {
                IconButton(onClick = { downloadJob?.cancel() }) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                IconButton(onClick = {
                    downloadJob = scope.launch {
                        downloading = true
                        try {
                            Downloads.downloadBatch(tracks, playlist.urn, playlist.title)
                        } finally {
                            downloading = false
                        }
                    }
                }) {
                    Icon(painterResource(R.drawable.ic_download), null, modifier = Modifier.size(20.dp))
                }
            }
        }
        TrackList(
            tracks = tracks,
            loading = loading,
            error = null,
            emptyText = "",
            canLoadMore = hasMore,
            onLoadMore = { load(page + 1) },
            onPlay = { onPlay(tracks, it) },
        )
    }
}
