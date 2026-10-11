package com.scd.android

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun ArtBackdrop(model: Any?, glow: Color?) {
    val p = LocalGlass.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        if (model != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocalFx.current.blur) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }.blur(60.dp),
            )
        } else if (glow != null) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(glow.copy(alpha = 0.55f), p.bg))))
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to p.bg.copy(alpha = 0.25f),
                    0.55f to p.bg.copy(alpha = 0.55f),
                    1f to p.bg.copy(alpha = 0.92f),
                ),
            ),
        )
        Backdrop(colors = listOfNotNull(glow, p.accent), wallpaper = false, stars = true, intensity = 1.2f, modifier = Modifier.background(Color.Transparent))
    }
}

@Composable
fun WaveMode() {
    val p = LocalGlass.current
    val nav = LocalNav.current
    val controller = LocalController.current
    val scope = rememberCoroutineScope()
    val tracks = WaveState.tracks
    var showSettings by remember { mutableStateOf(false) }
    var pickerFor by remember { mutableStateOf<Track?>(null) }

    LaunchedEffect(Unit) {
        if (tracks.isEmpty()) WaveState.load(false)
    }

    val pager = rememberPagerState(pageCount = { tracks.size })
    val current = tracks.getOrNull(pager.currentPage)
    val art = current?.artModel("t500x500")
    val artColor = rememberArtColor(art)

    LaunchedEffect(NowPlaying.urn, tracks.size) {
        val idx = tracks.indexOfFirst { it.urn == NowPlaying.urn }
        if (idx >= 0 && idx != pager.currentPage && !pager.isScrollInProgress) pager.animateScrollToPage(idx)
    }
    LaunchedEffect(pager, tracks) {
        snapshotFlow { pager.settledPage }.collect { page ->
            val t = tracks.getOrNull(page) ?: return@collect
            if (page >= tracks.size - 4 && !WaveState.loading) WaveState.load(true)
            if (NowPlaying.urn != t.urn && (NowPlaying.urn == null || WaveFeedback.isOwned(NowPlaying.urn) || page > 0)) {
                Playback.play(controller, tracks, t, autoContinue = true)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        ArtBackdrop(art, artColor)

        if (tracks.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                EmptyState(
                    R.drawable.ic_wave,
                    WaveState.error ?: stringResource(if (WaveState.loading) R.string.wave_loading else R.string.wave_empty),
                    action = stringResource(R.string.wave_refresh),
                    onAction = { WaveState.refresh(controller) },
                )
            }
        } else {
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 120.dp, bottom = 120.dp),
                key = { tracks.getOrNull(it)?.urn ?: it },
            ) { page ->
                val t = tracks[page]
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                WavePage(
                    track = t,
                    next = tracks.drop(page + 1).take(2),
                    modifier = Modifier.graphicsLayer {
                        alpha = 1f - 0.6f * offset
                        val s = 1f - 0.06f * offset
                        scaleX = s
                        scaleY = s
                    },
                    glow = artColor,
                    onPick = { pickerFor = t },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconOrb(R.drawable.ic_chevron_down, stringResource(R.string.cd_close), size = 46.dp, onClick = { nav.pop() })
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                LivePill(stringResource(R.string.wave_on_air), live = NowPlaying.isPlaying)
                Spacer(Modifier.height(5.dp))
                Text(stringResource(R.string.river_adapts), style = GlassType.small, color = p.text2)
            }
            IconOrb(R.drawable.ic_equalizer, stringResource(R.string.wave_settings), size = 46.dp, onClick = { showSettings = true })
        }

        if (current != null) {
            WaveBottomPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                track = current,
                onNext = {
                    scope.launch { if (pager.currentPage < tracks.lastIndex) pager.animateScrollToPage(pager.currentPage + 1) }
                },
            )
        }
    }

    if (showSettings) {
        GlassSheet({ showSettings = false }, stringResource(R.string.wave_settings)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.wave_fresh), style = GlassType.rowTitle, color = p.text)
                    Text(
                        stringResource(if (Prefs.waveFresh) R.string.wave_fresh_on else R.string.wave_fresh_off),
                        style = GlassType.small,
                        color = p.text2,
                    )
                }
                Switch(checked = Prefs.waveFresh, onCheckedChange = {
                    Prefs.changeWaveFresh(it)
                    WaveState.refresh(controller)
                    Recommendations.refresh()
                })
            }
            SheetAction(R.drawable.ic_refresh, stringResource(R.string.wave_refresh)) {
                showSettings = false
                WaveState.refresh(controller)
            }
        }
    }
    pickerFor?.let { t -> PlaylistPickerSheet(t, onDismiss = { pickerFor = null }) }
}

@Composable
private fun WavePage(track: Track, next: List<Track>, modifier: Modifier, glow: Color?, onPick: () -> Unit) {
    val p = LocalGlass.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = LocalController.current
    val liked = Likes.isLiked(track.urn)
    val disliked = Dislikes.isDisliked(track.urn)
    val heart = remember(track.urn) { Animatable(0f) }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .widthIn(max = 318.dp)
                .fillMaxWidth(),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(34.dp, RoundedCornerShape(30.dp), clip = false, ambientColor = glow ?: p.accent, spotColor = glow ?: p.accent)
                    .pointerInput(track.urn) {
                        detectTapGestures(onDoubleTap = {
                            scope.launch { if (!Likes.isLiked(track.urn)) Likes.toggle(track) }
                            scope.launch {
                                heart.snapTo(0f)
                                heart.animateTo(1f, tween(650, easing = EaseApple))
                                heart.animateTo(0f, tween(300))
                            }
                        })
                    },
            ) {
                next.reversed().forEachIndexed { i, n ->
                    val depth = next.size - i
                    Artwork(
                        n.artModel("t250x250"),
                        Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                val s = 1f - 0.08f * depth
                                scaleX = s
                                scaleY = s
                                translationY = 18.dp.toPx() * depth
                                alpha = 0.5f
                            },
                        corner = 30.dp,
                    )
                }
                Artwork(track.artModel("t500x500"), Modifier.matchParentSize(), corner = 30.dp)
                val tag = qualityLabel(track.urn)
                Text(
                    tag,
                    style = GlassType.eyebrow.copy(fontSize = 11.sp),
                    color = Color.White,
                    modifier = Modifier
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Icon(
                    painterResource(R.drawable.ic_heart_filled),
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(96.dp)
                        .graphicsLayer {
                            alpha = heart.value
                            val s = 0.6f + 0.6f * heart.value
                            scaleX = s
                            scaleY = s
                        },
                )
            }
        }
        Spacer(Modifier.height(30.dp))
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = p.text3, modifier = Modifier.size(14.dp).graphicsLayer { rotationZ = 180f })
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.swipe_next), style = GlassType.small, color = p.text3)
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    style = GlassType.title.copy(fontSize = 29.sp, lineHeight = 32.sp),
                    color = p.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(track.user?.username ?: "", style = GlassType.body.copy(fontSize = 16.sp), color = p.text2, maxLines = 1)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    track.genre?.takeIf { it.isNotBlank() }?.let { g -> GlassChip(g, dot = genreColor(g.lowercase())) }
                    GlassChip(formatDuration(track.displayDuration), mono = true)
                }
                if (next.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .glass(RoundedCornerShape(20.dp), p, shadow = 0.dp)
                            .padding(12.dp),
                    ) {
                        Eyebrow(stringResource(R.string.next_in_flow))
                        Spacer(Modifier.height(8.dp))
                        next.forEach { n ->
                            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Artwork(n.artModel("t120x120"), Modifier.size(36.dp), corner = 10.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(n.title, style = GlassType.small.copy(fontWeight = FontWeight.SemiBold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(n.user?.username ?: "", style = GlassType.small.copy(fontSize = 11.sp), color = p.text2, maxLines = 1)
                                }
                                Text(formatDuration(n.displayDuration), style = GlassType.mono, color = p.text3)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                LabeledOrb(
                    if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    stringResource(if (liked) R.string.in_likes else R.string.notif_like),
                    active = liked,
                ) { scope.launch { Likes.toggle(track) } }
                LabeledOrb(
                    if (disliked) R.drawable.ic_thumb_down_filled else R.drawable.ic_thumb_down,
                    stringResource(R.string.not_that),
                    tint = if (disliked) Color(0xFFFF6B6B) else null,
                ) {
                    scope.launch {
                        if (Dislikes.toggle(track) && NowPlaying.urn == track.urn) controller?.seekToNextMediaItem()
                    }
                }
                LabeledOrb(R.drawable.ic_plus, stringResource(R.string.to_playlist), onClick = onPick)
                val saved = Downloads.isDownloaded(track.urn)
                LabeledOrb(
                    if (saved) R.drawable.ic_check else R.drawable.ic_download,
                    stringResource(if (saved) R.string.downloaded_short else R.string.cd_download),
                    active = saved,
                ) { if (!saved) Downloads.enqueue(context, listOf(track)) }
            }
        }
    }
}

@Composable
fun qualityLabel(urn: String?): String {
    if (urn == null) return "SQ"
    if (Downloads.isDownloaded(urn)) {
        val cached = Downloads.cachedQuality(urn)
        LaunchedEffect(urn) { if (cached == null) Downloads.quality(urn) }
        return cached?.split(' ')?.firstOrNull() ?: "SQ"
    }
    return if (ScDataSource.wasHq(urn) || (Prefs.star && Prefs.hqStreaming)) "HQ" else "SQ"
}

@Composable
private fun WaveBottomPanel(modifier: Modifier, track: Track, onNext: () -> Unit) {
    val p = LocalGlass.current
    val controller = LocalController.current
    val isCurrent = NowPlaying.urn == track.urn
    val samples = rememberWaveSamples(track.urn, track.waveform_url, 48)
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .dockGlass(RoundedCornerShape(26.dp), null)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayOrb(isCurrent && NowPlaying.isPlaying, 50.dp) {
            val c = controller ?: return@PlayOrb
            when {
                !isCurrent -> Playback.play(c, WaveState.tracks, track, autoContinue = true)
                c.isPlaying -> c.pause()
                else -> c.play()
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Waveform(
                samples = samples,
                progress = { if (isCurrent && NowPlaying.duration > 0) NowPlaying.position.toFloat() / NowPlaying.duration else 0f },
                height = 28.dp,
                glow = false,
                onSeek = if (isCurrent) { f -> controller?.let { c -> if (c.duration > 0) c.seekTo((f * c.duration).toLong()) } } else null,
            )
            Row {
                PositionText(isCurrent, GlassType.mono.copy(fontSize = 10.sp), p.text)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(track.displayDuration), style = GlassType.mono.copy(fontSize = 10.sp), color = p.text3)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(44.dp).clip(CircleShape).pressable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_next), stringResource(R.string.cd_next), tint = p.text, modifier = Modifier.size(24.dp))
        }
    }
}
