package com.scd.android

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerBar(controller: MediaController?, onExpand: () -> Unit) {
    val t = NowPlaying.title.takeIf { it.isNotEmpty() } ?: return
    val artist = NowPlaying.artist
    val artworkUri = NowPlaying.artworkUri
    val isPlaying = NowPlaying.isPlaying
    val progress = if (NowPlaying.duration > 0)
        (NowPlaying.position.toFloat() / NowPlaying.duration).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "miniProgress",
    )

    val surface = MaterialTheme.colorScheme.surfaceVariant
    val tintTarget = rememberArtworkColor(artworkUri)
    val barColor by animateColorAsState(
        tintTarget.copy(alpha = 0.45f).compositeOver(surface),
        tween(800),
        label = "barTint",
    )

    var dragTotal by remember { mutableStateOf(0f) }

    Surface(color = barColor, contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onVerticalDrag = { _, dy -> dragTotal += dy },
                        onDragEnd = { if (dragTotal < -60f) onExpand() },
                    )
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    if (artist.isNotEmpty()) {
                        Text(
                            artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                IconButton(onClick = { controller?.seekToPrevious() }) {
                    Icon(painterResource(R.drawable.ic_prev), null)
                }
                IconButton(onClick = {
                    controller?.let { if (it.isPlaying) it.pause() else it.play() }
                }) {
                    Icon(painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play), null)
                }
                IconButton(onClick = { controller?.seekToNext() }) {
                    Icon(painterResource(R.drawable.ic_next), null)
                }
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun NowPlayingScreen(
    controller: MediaController,
    onClose: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val title = NowPlaying.title
    val artist = NowPlaying.artist
    val artistUrn = NowPlaying.artistUrn
    val artworkUri = NowPlaying.artworkUri
    val currentUrn = NowPlaying.urn
    val isPlaying = NowPlaying.isPlaying
    val shuffle = NowPlaying.shuffle
    val repeatMode = NowPlaying.repeatMode
    val position = NowPlaying.position
    val duration = NowPlaying.duration

    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    var waveform by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(currentUrn) {
        waveform = null
        val url = NowPlaying.waveformUrl ?: return@LaunchedEffect
        waveform = runCatching { Api.waveform(url) }.getOrNull()
    }

    val background = MaterialTheme.colorScheme.background
    val tintTarget = rememberArtworkColor(artworkUri)
    val tint by animateColorAsState(tintTarget, tween(800), label = "playerTint")
    val topColor = tint.copy(alpha = 0.65f).compositeOver(background)

    val offsetY = remember { Animatable(0f) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val immersive = Prefs.immersiveArtwork
        Surface(
            Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) },
            ) {
                if (immersive && artworkUri != null) {
                    AsyncImage(
                        model = artworkUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { scaleX = 1.25f; scaleY = 1.25f },
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Black.copy(alpha = 0.35f),
                                    0.5f to Color.Black.copy(alpha = 0.45f),
                                    1f to Color.Black.copy(alpha = 0.8f),
                                )
                            )
                    )
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (immersive) Modifier
                            else Modifier.background(
                                Brush.verticalGradient(
                                    0f to topColor,
                                    0.75f to background,
                                    1f to background,
                                )
                            )
                        )
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { _, dy ->
                                    scope.launch {
                                        offsetY.snapTo((offsetY.value + dy).coerceAtLeast(0f))
                                    }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        if (offsetY.value > 260f) onClose()
                                        else offsetY.animateTo(0f)
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { offsetY.animateTo(0f) }
                                },
                            )
                        }
                        .padding(horizontal = 24.dp),
                ) {
                    val titleColor = if (immersive) Color.White else MaterialTheme.colorScheme.onBackground
                    val subColor = if (immersive) Color.White.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                    val hasTrack = currentUrn != null

                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = titleColor)
                        }
                        Spacer(Modifier.weight(1f))
                        if (hasTrack) {
                            Box {
                                var menuOpen by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(painterResource(R.drawable.ic_plus), null, tint = titleColor)
                                }
                                controller.currentMediaItem?.toTrackOrNull()?.let { track ->
                                    AddToPlaylistMenu(
                                        expanded = menuOpen,
                                        track = track,
                                        onDismiss = { menuOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.weight(if (immersive) 6f else 1f))

                    if (!immersive && artworkUri != null) {
                        AsyncImage(
                            model = artworkUri,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(28.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                color = titleColor,
                                modifier = Modifier
                                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                    .drawWithContent {
                                        drawContent()
                                        val fade = 24.dp.toPx()
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                0f to Color.Transparent,
                                                fade / size.width to Color.Black,
                                                1f - fade / size.width to Color.Black,
                                                1f to Color.Transparent,
                                            ),
                                            blendMode = BlendMode.DstIn,
                                        )
                                    }
                                    .basicMarquee(),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                artist,
                                color = subColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (artistUrn != null) {
                                    Modifier.clickable { onOpenArtist(artistUrn) }
                                } else Modifier,
                            )
                        }
                    }

                    if (currentUrn != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val liked = Likes.isLiked(currentUrn)
                            val disliked = Dislikes.isDisliked(currentUrn)
                            IconButton(onClick = {
                                controller.currentMediaItem?.toTrackOrNull()?.let {
                                    scope.launch { Likes.toggle(it) }
                                }
                            }) {
                                Icon(
                                    painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                                    null,
                                    tint = if (liked) MaterialTheme.colorScheme.primary else subColor,
                                )
                            }
                            IconButton(onClick = {
                                controller.currentMediaItem?.toTrackOrNull()?.let {
                                    scope.launch { if (Dislikes.toggle(it)) controller.seekToNext() }
                                }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_thumb_down),
                                    null,
                                    tint = if (disliked) MaterialTheme.colorScheme.error else subColor,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            when {
                                currentUrn in Downloads.inProgress ->
                                    CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                                Downloads.isDownloaded(currentUrn) ->
                                    IconButton(onClick = { scope.launch { Downloads.remove(currentUrn) } }) {
                                        Icon(
                                            painterResource(R.drawable.ic_check),
                                            null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                else ->
                                    IconButton(onClick = {
                                        controller.currentMediaItem?.toTrackOrNull()?.let {
                                            scope.launch { Downloads.download(it) }
                                        }
                                    }) {
                                        Icon(
                                            painterResource(R.drawable.ic_download),
                                            null,
                                            tint = subColor,
                                        )
                                    }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    val wf = waveform
                    if (wf != null) {
                        WaveformSeekBar(
                            samples = wf,
                            progress = if (duration > 0) position.toFloat() / duration else 0f,
                            isPlaying = isPlaying,
                            onSeek = { frac -> if (duration > 0) controller.seekTo((frac * duration).toLong()) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        )
                    } else {
                        Slider(
                            value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                            onValueChange = {
                                dragging = true
                                dragValue = it
                            },
                            onValueChangeFinished = {
                                if (duration > 0) controller.seekTo((dragValue * duration).toLong())
                                dragging = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            formatDuration(if (dragging && duration > 0) (dragValue * duration).toLong() else position),
                            color = subColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            formatDuration(duration),
                            color = subColor,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { controller.shuffleModeEnabled = !shuffle }) {
                            Icon(
                                painterResource(R.drawable.ic_shuffle),
                                null,
                                tint = if (shuffle) MaterialTheme.colorScheme.primary else subColor,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { controller.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                            Icon(painterResource(R.drawable.ic_prev), null, modifier = Modifier.size(36.dp), tint = titleColor)
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { if (controller.isPlaying) controller.pause() else controller.play() },
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                    null,
                                    tint = MaterialTheme.colorScheme.background,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        IconButton(onClick = { controller.seekToNext() }, modifier = Modifier.size(56.dp)) {
                            Icon(painterResource(R.drawable.ic_next), null, modifier = Modifier.size(36.dp), tint = titleColor)
                        }
                        Spacer(Modifier.width(12.dp))
                        Box {
                            IconButton(onClick = {
                                controller.repeatMode = when (repeatMode) {
                                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                    else -> Player.REPEAT_MODE_OFF
                                }
                            }) {
                                Icon(
                                    painterResource(R.drawable.ic_repeat),
                                    null,
                                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else subColor,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            if (repeatMode == Player.REPEAT_MODE_ONE) {
                                Text(
                                    "1",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1.4f))
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistMenu(expanded: Boolean, track: Track, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        if (expanded) {
            runCatching { Api.myPlaylists() }.onSuccess { playlists = it.collection }
        }
    }

    fun addTo(urn: String) {
        scope.launch {
            val ok = runCatching { Api.addToPlaylist(urn, track.urn) }.isSuccess
            if (ok) PlaylistEvents.bump()
            android.widget.Toast.makeText(
                context,
                context.getString(if (ok) R.string.added else R.string.error_network),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val list = playlists
        if (list != null && list.isEmpty()) {
            androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text(stringResource(R.string.new_playlist)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    showCreate = true
                },
            )
        } else {
            androidx.compose.material3.Text(
                stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            list?.forEach { pl ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { androidx.compose.material3.Text(pl.title) },
                    onClick = {
                        onDismiss()
                        addTo(pl.urn)
                    },
                )
            }
            androidx.compose.material3.DropdownMenuItem(
                text = { androidx.compose.material3.Text(stringResource(R.string.new_playlist)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_plus), null, Modifier.size(18.dp)) },
                onClick = {
                    onDismiss()
                    showCreate = true
                },
            )
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreate = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp)) {
                    androidx.compose.material3.Text(
                        stringResource(R.string.new_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.TextButton(onClick = { showCreate = false }) {
                            androidx.compose.material3.Text(stringResource(R.string.cancel))
                        }
                        androidx.compose.material3.Button(
                            enabled = name.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    val urn = runCatching { Api.createPlaylist(name.trim()) }.getOrNull()
                                    showCreate = false
                                    if (urn != null) addTo(urn)
                                    PlaylistEvents.bump()
                                }
                            },
                        ) { androidx.compose.material3.Text(stringResource(R.string.create)) }
                    }
                }
            }
        }
    }
}

@Composable
fun WaveformSeekBar(
    samples: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val played = MaterialTheme.colorScheme.primary
    val rest = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)

    val pulse by rememberInfiniteTransition(label = "wfPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "wfPulseValue",
    )

    Canvas(
        modifier
            .pointerInput(samples) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(samples) {
                detectHorizontalDragGestures(
                    onDragStart = { dragFrac = (it.x / size.width).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        dragFrac = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFrac?.let(onSeek)
                        dragFrac = null
                    },
                    onDragCancel = { dragFrac = null },
                )
            }
    ) {
        val n = samples.size
        if (n == 0) return@Canvas
        val bw = size.width / n
        val prog = dragFrac ?: progress
        samples.forEachIndexed { i, v ->
            val frac = i.toFloat() / n
            var h = v * size.height
            if (isPlaying) {
                val d = abs(frac - prog)
                if (d < 0.035f) {
                    h *= 1f + 0.3f * pulse * (1f - d / 0.035f) * v
                }
            }
            h = h.coerceIn(2f, size.height)
            drawRoundRect(
                color = if (frac <= prog) played else rest,
                topLeft = Offset(i * bw + bw * 0.15f, (size.height - h) / 2f),
                size = Size(bw * 0.7f, h),
                cornerRadius = CornerRadius(bw * 0.35f),
            )
        }
    }
}
