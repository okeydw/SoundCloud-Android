package com.scd.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun WaveFeed(
    controller: MediaController?,
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (Track) -> Unit,
) {
    when {
        error != null -> {
            Text(
                stringResource(R.string.error_network) + "\n" + error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
            )
            return
        }
        tracks.isEmpty() && loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        tracks.isEmpty() -> {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.wave_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            return
        }
    }

    val pagerState = rememberPagerState(pageCount = { tracks.size })
    val scope = rememberCoroutineScope()

    var currentUrn by remember { mutableStateOf<String?>(null) }
    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        fun sync() {
            currentUrn = controller.currentMediaItem?.mediaId
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(currentUrn) {
        val idx = tracks.indexOfFirst { it.urn == currentUrn }
        if (idx >= 0 && idx != pagerState.currentPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(idx)
        }
    }

    LaunchedEffect(pagerState.settledPage) {
        val settled = pagerState.settledPage
        // Трек НЕ переключаем при листании — только по тапу. Здесь лишь догрузка.
        if (settled >= tracks.size - 4 && canLoadMore && !loading) onLoadMore()
    }

    Box(Modifier.fillMaxSize()) {
    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 150.dp),
        pageSpacing = 18.dp,
    ) { page ->
        val track = tracks[page]
        val pageOffset =
            ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                .coerceIn(0f, 1f)
        var hDrag by remember(track.urn) { mutableStateOf(0f) }
        val liked = Likes.isLiked(track.urn)
        val disliked = Dislikes.isDisliked(track.urn)

        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 1f - 0.08f * pageOffset
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.5f * pageOffset
                }
                .pointerInput(track.urn) {
                    detectHorizontalDragGestures(
                        onDragStart = { hDrag = 0f },
                        onHorizontalDrag = { _, dx -> hDrag += dx },
                        onDragEnd = {
                            when {
                                hDrag > 120f -> scope.launch {
                                    if (!Likes.isLiked(track.urn)) Likes.toggle(track)
                                }
                                hDrag < -120f -> scope.launch {
                                    if (!Dislikes.isDisliked(track.urn)) Dislikes.toggle(track)
                                    if (page + 1 < tracks.size) {
                                        pagerState.animateScrollToPage(page + 1)
                                    }
                                }
                            }
                        },
                    )
                }
                .clickable {
                    if (track.urn == currentUrn) {
                        controller?.let { if (it.isPlaying) it.pause() else it.play() }
                    } else {
                        onPlay(track)
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AsyncImage(
                model = Api.artworkUrl(track.artwork_url, "t500x500"),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                track.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.user?.username ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (liked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.ic_heart_filled),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (disliked) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        painterResource(R.drawable.ic_thumb_down),
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }

        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp)
                .size(44.dp)
                .clickable { onRefresh() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
