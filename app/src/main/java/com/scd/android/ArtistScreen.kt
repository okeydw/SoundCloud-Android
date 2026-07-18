package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
fun ArtistScreen(
    urn: String,
    onBack: () -> Unit,
    onPlay: (List<Track>, Track) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    offline: Boolean = false,
) {
    var artist by remember { mutableStateOf<Artist?>(null) }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    BackHandler(onBack = onBack)

    LaunchedEffect(urn) {
        loading = true
        if (offline) {
            val downloaded = Downloads.tracks().filter { it.user?.urn == urn }
            tracks = downloaded
            val u = downloaded.firstOrNull()?.user
            if (u != null) {
                artist = Artist(urn = urn, username = u.username, avatar_url = u.avatar_url)
            }
            playlists = emptyList()
        } else {
            runCatching { Api.user(urn) }.onSuccess { artist = it }
            runCatching { Api.userTracks(urn) }.onSuccess { tracks = it.collection }
            runCatching { Api.userPlaylists(urn) }.onSuccess { playlists = it.collection }
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                artist?.username ?: "…",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            artist?.let { a ->
                val liked = LikedArtists.isLiked(a.urn)
                IconButton(onClick = { LikedArtists.toggle(a) }) {
                    Icon(
                        painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        null,
                        tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        if (loading && tracks.isEmpty()) {
            Row(Modifier.fillMaxWidth().padding(32.dp), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = Api.artworkUrl(artist?.avatar_url, "t120x120"),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        artist?.followers_count?.let {
                            Text(
                                stringResource(R.string.followers, formatCount(it)),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        artist?.track_count?.let {
                            Text(
                                stringResource(R.string.tracks_count, it),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (playlists.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.playlists),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(playlists) { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPlaylist(p) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = Api.artworkUrl(p.artwork_url, "t120x120"),
                            contentDescription = null,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(p.kindLabelRes()) + " · " +
                                    stringResource(R.string.tracks_count, p.track_count),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.tracks),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                items(tracks) { track ->
                    TrackRow(track = track, onClick = { onPlay(tracks, track) })
                }
            }
        }
    }
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000 -> "%.1fK".format(n / 1_000.0)
    else -> n.toString()
}
