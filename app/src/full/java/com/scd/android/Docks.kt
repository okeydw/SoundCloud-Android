package com.scd.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.session.MediaController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun Modifier.dockGlass(shape: Shape, haze: HazeState?): Modifier {
    val p = LocalGlass.current
    val fx = LocalFx.current
    val base = softShadow(shape, 20.dp, Color.Black.copy(alpha = if (p.dark) 0.5f else 0.16f), 6.dp).clip(shape)
    return if (haze != null && fx.blur) {
        base
            .hazeEffect(haze) {
                blurEnabled = true
                blurRadius = (30f * fx.blurScale).dp
                backgroundColor = p.bg
                tints = listOf(HazeTint(p.bg.copy(alpha = if (p.dark) 0.45f else 0.55f)))
                noiseFactor = 0.04f
            }
            .glass(shape, p, shadow = 0.dp)
    } else {
        base.background(p.scrim).glass(shape, p, shadow = 0.dp)
    }
}

@Composable
fun LiquidPillDock(
    controller: MediaController?,
    haze: HazeState?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val urn = NowPlaying.urn ?: return
    val p = LocalGlass.current
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playing = NowPlaying.isPlaying
    val shape = RoundedCornerShape(24.dp)
    val preview = rememberQueuePreview(controller)
    val swipe = rememberTrackSwipeFade(preview)
    val drag = remember { Animatable(0f) }
    val spin = if (playing && fx.idle) {
        rememberInfiniteTransition(label = "dockSpin").animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    } else null

    Box(
        modifier
            .fillMaxWidth()
            .height(68.dp)
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .drawBehind {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(p.glowStrong.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.82f, size.height * 0.9f),
                        radius = size.width * 0.45f,
                    ),
                    topLeft = Offset(0f, size.height * 0.35f),
                    size = Size(size.width, size.height),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                )
            }
            .dockGlass(shape, haze)
            .pointerInput(urn, preview) {
                val threshold = with(density) { 80.dp.toPx() }
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero; swipe.begin(preview, threshold) },
                    onDragEnd = {
                        if (total.y < -threshold / 2 && abs(total.y) > abs(total.x)) {
                            swipe.cancel()
                            onOpen()
                        } else swipe.finish(controller)
                        scope.launch { drag.animateTo(0f, tween(380, easing = EaseApple)) }
                    },
                    onDragCancel = {
                        swipe.cancel()
                        scope.launch { drag.animateTo(0f, tween(380, easing = EaseApple)) }
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                        if (abs(total.x) > abs(total.y)) {
                            swipe.drag(amount.x)
                            scope.launch { drag.snapTo(swipe.distance * 0.6f) }
                        }
                    },
                )
            }
            .pressable(onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(p.glow, Color.Transparent), start = Offset(Float.POSITIVE_INFINITY, 0f), end = Offset(0f, Float.POSITIVE_INFINITY))),
        )
        Row(
            Modifier.fillMaxSize().padding(start = 11.dp, end = 11.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FadingTrack(swipe, preview.current, Modifier.weight(1f)) { md ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Artwork(md.artworkUri?.toString(), Modifier.size(46.dp), corner = 13.dp)
                        if (spin != null) Box(Modifier.size(46.dp).drawBehind {
                            rotate(spin.value) {
                                drawRoundRect(
                                    Brush.sweepGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent, p.accent.copy(alpha = 0.6f), Color.Transparent)),
                                    cornerRadius = CornerRadius(13.dp.toPx()), style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        })
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(md.title?.toString().orEmpty(), style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(md.artist?.toString().orEmpty(), style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            val liked = Likes.isLiked(urn)
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable {
                        controller?.currentMediaItem?.toTrackOrNull()?.let { t -> scope.launch { Likes.toggle(t) } }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    stringResource(if (liked) R.string.notif_unlike else R.string.notif_like),
                    tint = if (liked) p.accent else p.text2,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            PlayOrb(playing, 46.dp) { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 5.dp)
                .fillMaxWidth()
                .height(2.5.dp)
                .drawBehind {
                    val d = NowPlaying.duration
                    val frac = if (d > 0) (NowPlaying.position.toFloat() / d).coerceIn(0f, 1f) else 0f
                    drawRoundRect(p.text.copy(alpha = 0.12f), cornerRadius = CornerRadius(size.height))
                    drawRoundRect(p.accentBrush(), size = Size(size.width * frac, size.height), cornerRadius = CornerRadius(size.height))
                },
        )
    }
}

data class DockTab(val icon: Int, val label: String)

@Composable
fun FloatingTabDock(
    tabs: List<DockTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    haze: HazeState?,
    modifier: Modifier = Modifier,
) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(26.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .dockGlass(shape, haze)
            .padding(6.dp),
    ) {
        val tabWidth = maxWidth / tabs.size
        val pillX by animateDpAsState(tabWidth * selected, tween(500, easing = EaseApple), label = "pill")
        Box(
            Modifier
                .offset(x = pillX)
                .width(tabWidth)
                .fillMaxHeight()
                .softShadow(RoundedCornerShape(20.dp), 12.dp, p.accent.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(p.glowStrong, p.glow.copy(alpha = 0.05f))))
                .border(0.75.dp, p.accent.copy(alpha = 0.45f), RoundedCornerShape(20.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            tabs.forEachIndexed { i, tab ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .pressable { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        null,
                        tint = if (active) p.text else p.text2,
                        modifier = Modifier
                            .size(22.dp)
                            .drawBehind {
                                if (active) {
                                    drawCircle(
                                        Brush.radialGradient(listOf(p.accent.copy(alpha = 0.55f), Color.Transparent), radius = size.minDimension * 1.3f),
                                        radius = size.minDimension * 1.3f,
                                    )
                                }
                            }
                            .graphicsLayer { alpha = if (active) 1f else 0.85f },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        style = GlassType.small.copy(fontSize = 11.sp),
                        color = if (active) p.text else p.text2,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
