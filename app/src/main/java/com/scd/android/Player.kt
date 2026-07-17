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
import androidx.compose.runtime.DisposableEffect
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun PlayerBar(controller: MediaController?, onExpand: () -> Unit) {
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf("") }
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        fun sync() {
            val md = controller.currentMediaItem?.mediaMetadata
            title = md?.title?.toString()
            artist = md?.artist?.toString() ?: ""
            artworkUri = md?.artworkUri?.toString()
            isPlaying = controller.isPlaying
            NowPlaying.urn = controller.currentMediaItem?.mediaId
            NowPlaying.isPlaying = controller.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        if (controller == null) return@LaunchedEffect
        while (true) {
            val dur = controller.duration
            progress = if (dur > 0) (controller.currentPosition.toFloat() / dur).coerceIn(0f, 1f) else 0f
            delay(250)
        }
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 260, easing = LinearEasing),
        label = "miniProgress",
    )

    val t = title ?: return

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
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var currentUrn by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(Player.REPEAT_MODE_OFF) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    DisposableEffect(controller) {
        fun sync() {
            val md = controller.currentMediaItem?.mediaMetadata
            title = md?.title?.toString() ?: ""
            artist = md?.artist?.toString() ?: ""
            artworkUri = md?.artworkUri?.toString()
            currentUrn = controller.currentMediaItem?.mediaId ?: ""
            isPlaying = controller.isPlaying
            shuffle = controller.shuffleModeEnabled
            repeatMode = controller.repeatMode
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            if (!dragging) {
                position = controller.currentPosition.coerceAtLeast(0L)
                duration = controller.duration.coerceAtLeast(0L)
            }
            delay(400)
        }
    }

    var waveform by remember { mutableStateOf<List<Float>?>(null) }
    LaunchedEffect(currentUrn) {
        waveform = null
        val url = controller.currentMediaItem?.mediaMetadata?.extras?.getString("waveform_url")
            ?: return@LaunchedEffect
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
                    val track = controller.currentMediaItem?.toTrackOrNull()
                    val titleColor = if (immersive) Color.White else MaterialTheme.colorScheme.onBackground
                    val subColor = if (immersive) Color.White.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = titleColor)
                        }
                        Spacer(Modifier.weight(1f))
                        if (track != null) {
                            Box {
                                var menuOpen by remember { mutableStateOf(false) }
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(
                                        painterResource(R.drawable.ic_plus),
                                        null,
                                        tint = titleColor,
                                    )
                                }
                                AddToPlaylistMenu(
                                    expanded = menuOpen,
                                    track = track,
                                    onDismiss = { menuOpen = false },
                                )
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
                            val artistUrn = controller.currentMediaItem?.mediaMetadata
                                ?.extras?.getString("artist_urn")
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
                    if (track != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        val liked = Likes.isLiked(track.urn)
                        val disliked = Dislikes.isDisliked(track.urn)
                        IconButton(onClick = { scope.launch { Likes.toggle(track) } }) {
                            Icon(
                                painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                                null,
                                tint = if (liked) MaterialTheme.colorScheme.primary else subColor,
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                if (Dislikes.toggle(track)) controller.seekToNext()
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
                            track.urn in Downloads.inProgress ->
                                CircularProgressIndicator(Modifier.size(22.dp).padding(2.dp), strokeWidth = 2.dp)
                            Downloads.isDownloaded(track.urn) ->
                                IconButton(onClick = { scope.launch { Downloads.remove(track.urn) } }) {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            else ->
                                IconButton(onClick = { scope.launch { Downloads.download(track) } }) {
                                    Icon(
                                        painterResource(R.drawable.ic_download),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        onSeek = { frac ->
                            if (duration > 0) controller.seekTo((frac * duration).toLong())
                        },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        formatDuration(duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            tint = if (shuffle) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = { controller.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_prev), null, modifier = Modifier.size(36.dp))
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
                        Icon(painterResource(R.drawable.ic_next), null, modifier = Modifier.size(36.dp))
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
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
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
            if (ok) {
                PlaylistEvents.bump()
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.added),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        val list = playlists
        if (list != null && list.isEmpty()) {
            // нет плейлистов — сразу предлагаем создать
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
                                    val pl = runCatching { Api.createPlaylist(name.trim()) }.getOrNull()
                                    showCreate = false
                                    if (pl != null) {
                                        addTo(pl.urn)
                                    }
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
