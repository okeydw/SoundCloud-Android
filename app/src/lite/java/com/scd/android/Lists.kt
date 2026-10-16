package com.scd.android

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TileGrid(
    tiles: List<Track>,
    topPadding: Dp = 0.dp,
    onLoadMore: (() -> Unit)? = null,
    onPlay: (Track) -> Unit,
) {
    if (tiles.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }
    val gridState = rememberLazyStaggeredGridState()
    val curOnLoadMore by rememberUpdatedState(onLoadMore)
    if (onLoadMore != null) {
        LaunchedEffect(gridState) {
            snapshotFlow {
                (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) to gridState.layoutInfo.totalItemsCount
            }.collect { (last, total) ->
                if (total > 0 && last >= total - 8) curOnLoadMore?.invoke()
            }
        }
    }
    LazyVerticalStaggeredGrid(
        state = gridState,
        columns = StaggeredGridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        itemsIndexed(tiles) { index, track ->
            val ratio = when (Math.floorMod(track.urn.hashCode() + index, 5)) {
                0 -> 1.6f
                1 -> 0.7f
                2 -> 1.15f
                3 -> 0.85f
                else -> 1f
            }
            val isCurrent = NowPlaying.urn == track.urn
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPlay(track) },
            ) {
                AsyncImage(
                    model = track.artModel("t250x250"),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
                if (isCurrent) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        EqualizerBars(playing = NowPlaying.isPlaying, color = Color.White)
                    }
                }
            }
        }
        if (onLoadMore != null) {
            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
                LaunchedEffect(Unit) {
                    while (true) {
                        curOnLoadMore?.invoke()
                        delay(4_000)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    emptyText: String,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onPlay: (Track) -> Unit,
    dimUndownloaded: Boolean = false,
    topPadding: Dp = 0.dp,
    rowTrailing: (@Composable (Track) -> Unit)? = null,
    onRemove: ((Track) -> Unit)? = null,
) {
    when {
        error != null -> Text(
            if (offlineByChoice) stringResource(R.string.offline_mode_on) else stringResource(R.string.error_network) + "\n" + error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = topPadding).padding(16.dp),
        )
        loading && tracks.isEmpty() -> Row(
            Modifier.fillMaxWidth().padding(top = topPadding).padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        tracks.isEmpty() -> Text(
            emptyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = topPadding).padding(16.dp),
        )
    }

    val listState = rememberLazyListState()
    InfiniteScroll(listState, canLoadMore, loading, tracks.size, onLoadMore)
    val uniqueTracks = remember(tracks) { tracks.distinctBy { it.urn } }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding),
    ) {
        items(uniqueTracks, key = { it.urn }) { track ->
            val unavailable = dimUndownloaded && !Downloads.isDownloaded(track.urn)
            TrackRow(
                track = track,
                onClick = { onPlay(track) },
                dimmed = unavailable,
                trailing = rowTrailing?.let { slot -> @Composable { slot(track) } },
                onRemove = onRemove?.let { remove -> { remove(track) } },
            )
        }
        if (canLoadMore && tracks.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
        }
    }
}

@Composable
private fun InfiniteScroll(
    listState: LazyListState,
    canLoadMore: Boolean,
    loading: Boolean,
    itemCount: Int,
    onLoadMore: () -> Unit,
) {
    val curCanLoadMore by rememberUpdatedState(canLoadMore)
    val curLoading by rememberUpdatedState(loading)
    val curCount by rememberUpdatedState(itemCount)
    val curOnLoadMore by rememberUpdatedState(onLoadMore)
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { last ->
                if (curCanLoadMore && !curLoading && curCount > 0 && last >= curCount - 4) curOnLoadMore()
            }
    }
}

@Composable
fun ArtistCardList(
    artists: List<Artist>,
    loading: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (String) -> Unit,
    topPadding: Dp = 0.dp,
) {
    if (artists.isEmpty()) {
        if (loading) {
            Row(
                Modifier.fillMaxWidth().padding(top = topPadding).padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            Text(
                stringResource(R.string.search_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = topPadding).padding(16.dp),
            )
        }
        return
    }
    val listState = rememberLazyListState()
    InfiniteScroll(listState, canLoadMore, loading, artists.size, onLoadMore)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding),
    ) {
        items(artists.distinctBy { it.urn }, key = { it.urn }) { a ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(a.urn) }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = Api.artworkUrl(a.avatar_url, "t120x120"),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.username, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.artist_label) +
                            (a.followers_count?.let { " · " + stringResource(R.string.followers, formatCount(it)) } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (canLoadMore) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
fun PlaylistCardList(
    playlists: List<Playlist>,
    loading: Boolean,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onOpen: (Playlist) -> Unit,
    topPadding: Dp = 0.dp,
) {
    if (playlists.isEmpty()) {
        if (loading) {
            Row(
                Modifier.fillMaxWidth().padding(top = topPadding).padding(32.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            Text(
                stringResource(R.string.search_empty),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = topPadding).padding(16.dp),
            )
        }
        return
    }
    val listState = rememberLazyListState()
    InfiniteScroll(listState, canLoadMore, loading, playlists.size, onLoadMore)
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding),
    ) {
        items(playlists.distinctBy { it.urn }, key = { it.urn }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p) }.padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistArtwork(p, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(p.kindLabelRes()) +
                            (p.user?.username?.let { " · $it" } ?: ""),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (canLoadMore) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
fun StarTag() {
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            "★ " + stringResource(R.string.star_badge),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun TrackArtwork(
    url: Any?,
    size: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp = 6.dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        var failed by remember(url) { mutableStateOf(false) }
        if (url == null || (url is String && url.isEmpty()) || failed) {
            Icon(
                painterResource(R.drawable.ic_music),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onState = { state ->
                    failed = state is coil.compose.AsyncImagePainter.State.Error
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    if (menu) TrackMenuDialog(track, onDismiss = { menu = false }, onPlay = onClick, onRemove = onRemove)
    val liked = Likes.isLiked(track.urn)
    val isCurrent = NowPlaying.urn == track.urn
    val accent = MaterialTheme.colorScheme.primary
    val dim = dimmed || track.starLocked || (track.unavailable && !Prefs.playBlocked)
    val rowAlpha = if (dim) 0.4f else 1f
    LaunchedEffect(track.urn) {
        if (track.goPlus && track.displayDuration in 1L..35_000L && !Prefs.offline) {
            DurationCache.resolve(track.urn)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(enabled = true, onClick = { if (!dim) onClick() }, onLongClick = { menu = true })
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .graphicsLayer { alpha = rowAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            TrackArtwork(track.artModel("t120x120"), 52.dp)
            if (dimmed && !track.unavailable) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(playing = NowPlaying.isPlaying, color = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    maxLines = 1,
                    overflow = if (isCurrent) TextOverflow.Clip else TextOverflow.Ellipsis,
                    softWrap = !isCurrent,
                    fontWeight = FontWeight.Medium,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false).then(if (isCurrent) Modifier.basicMarquee() else Modifier),
                )
                if (track.goPlus) {
                    Spacer(Modifier.width(6.dp))
                    StarTag()
                }
            }
            Text(
                track.user?.username ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(4.dp))
        if (Downloads.isDownloaded(track.urn)) {
            Icon(
                painterResource(R.drawable.ic_check),
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(4.dp))
            if (Prefs.streamTags) {
                val q = Downloads.cachedQuality(track.urn)
                LaunchedEffect(track.urn) { if (q == null) Downloads.quality(track.urn) }
                if (q != null) {
                    Text(
                        q.split(' ').take(2).joinToString(" "),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                }
            }
        }
        Text(
            formatDuration(track.displayDuration),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (trailing != null) {
            trailing()
        } else {
            IconButton(onClick = { scope.launch { Likes.toggle(track) } }, modifier = Modifier.size(40.dp)) {
                Icon(
                    painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    iconLabel(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
fun EqualizerBars(playing: Boolean, color: Color) {
    if (!playing) {
        Row(
            Modifier.size(20.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) {
                Box(Modifier.width(3.dp).fillMaxHeight(0.3f).background(color, RoundedCornerShape(1.dp)))
            }
        }
        return
    }
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 340 + i * 120, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "eqBar$i",
        )
    }
    Row(
        Modifier.size(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) h.value else 0.3f)
                    .background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}
