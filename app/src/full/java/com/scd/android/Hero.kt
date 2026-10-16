package com.scd.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun auraColors(tracks: List<Track>, accent: Color): List<Color> {
    val genres = topGenres(tracks, 3).map { it.color }
    return when {
        genres.isEmpty() -> listOf(accent, lerpColor(accent, Color.White, 0.35f))
        genres.size == 1 -> listOf(genres[0], accent)
        else -> genres
    }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
fun HeroPanel(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    content: @Composable () -> Unit,
) {
    val p = LocalGlass.current
    val tint = glow ?: p.accent
    Box(
        modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .drawBehind {
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(tint.copy(alpha = if (p.dark) 0.28f else 0.16f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, 0f),
                        radius = size.width * 0.9f,
                    ),
                    cornerRadius = CornerRadius(30.dp.toPx()),
                )
            }
            .glass(RoundedCornerShape(30.dp), p, shadow = 18.dp, tint = tint.copy(alpha = 0.10f))
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
fun AuraRing(spinning: Boolean, colors: List<Color>, corner: Dp, modifier: Modifier = Modifier) {
    val fx = LocalFx.current
    val angle = if (spinning && fx.idle) {
        rememberInfiniteTransition(label = "ring").animateFloat(0f, 360f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "ringA")
    } else null
    val sweep = remember(colors) { colors + colors.first() }
    Box(
        modifier.drawBehind {
            val stroke = 2.5.dp.toPx()
            rotate(angle?.value ?: 0f) {
                drawRoundRect(
                    Brush.sweepGradient(sweep),
                    topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(stroke),
                )
            }
        },
    )
}

@Composable
fun SegmentDock(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(22.dp)
    BoxWithConstraints(
        modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(52.dp)
            .glass(shape, p, shadow = 0.dp)
            .padding(5.dp),
    ) {
        val w = maxWidth / labels.size.coerceAtLeast(1)
        val x by animateDpAsState(w * selected, tween(500, easing = EaseApple), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .softShadow(RoundedCornerShape(17.dp), 10.dp, p.accent.copy(alpha = 0.45f))
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.verticalGradient(listOf(p.glowStrong, p.glow.copy(alpha = 0.05f))))
                .border(0.75.dp, p.accent.copy(alpha = 0.45f), RoundedCornerShape(17.dp)),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(17.dp)).pressable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = GlassType.small.copy(fontSize = 13.5.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (i == selected) p.text else p.text2,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun ListToolbar(
    count: Int,
    downloaded: Int?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val p = LocalGlass.current
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val parts = buildList {
            add(stringResource(R.string.tracks_count, count))
            if (downloaded != null && downloaded > 0) add(stringResource(R.string.me_downloaded_n, downloaded))
        }
        Text(parts.joinToString(" · "), style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun DownloadAllOrb(listKey: String, tracks: List<Track>, complete: Boolean, collect: suspend () -> List<Track>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var gathering by remember(listKey) { mutableStateOf(false) }
    val p = LocalGlass.current
    val busy = gathering || listKey in Downloads.activeLists
    val allSaved = complete && tracks.isNotEmpty() && tracks.all { Downloads.isDownloaded(it.urn) }
    when {
        busy -> Box(
            Modifier.size(44.dp).glass(CircleShape, p, shadow = 0.dp).pressable {
                Downloads.cancelList(context, listKey)
                gathering = false
            },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(18.dp), color = p.accent, strokeWidth = 2.dp)
        }
        allSaved -> IconOrb(R.drawable.ic_check, stringResource(R.string.downloaded_short), size = 44.dp, iconSize = 18.dp, active = true) {}
        else -> IconOrb(R.drawable.ic_download, stringResource(R.string.download_all), size = 44.dp, iconSize = 18.dp) {
            gathering = true
            scope.launchSafe {
                try {
                    val all = collect()
                    if (all.isNotEmpty()) Downloads.enqueue(context, all, listKey = listKey)
                } finally {
                    gathering = false
                }
            }
        }
    }
}

fun CoroutineScope.launchSafe(block: suspend () -> Unit) = launch {
    try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        Logs.add("ui", "action failed: ${e.javaClass.simpleName} ${e.message?.take(60) ?: ""}")
    }
}

@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String? = null,
    confirm: String,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    icon: Int? = null,
    onConfirm: () -> Unit,
    content: (@Composable () -> Unit)? = null,
) {
    val p = LocalGlass.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassModalFrame(Modifier.padding(horizontal = 18.dp)) {
            Column(Modifier.padding(18.dp)) {
                ModalHeader(icon ?: if (danger) R.drawable.ic_trash else R.drawable.ic_edit, title, onClose = onDismiss)
                if (text != null) {
                    Spacer(Modifier.height(14.dp))
                    Text(text, style = GlassType.body, color = p.text2)
                }
                if (content != null) {
                    Spacer(Modifier.height(14.dp))
                    content()
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    GlassChip(stringResource(R.string.cancel), onClick = onDismiss)
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .height(42.dp)
                            .clip(CircleShape)
                            .background(if (danger) SolidColor(Color(0xFFFF5A5F)) else p.accentFill())
                            .graphicsLayer { alpha = if (confirmEnabled) 1f else 0.4f }
                            .pressable(enabled = confirmEnabled, onClick = onConfirm)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(confirm, style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold), color = if (danger) Color.White else p.onAccent)
                    }
                }
            }
        }
    }
}

@Composable
fun GlassTextField(value: String, onChange: (String) -> Unit, placeholder: String? = null) {
    val p = LocalGlass.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .glass(RoundedCornerShape(16.dp), p, shadow = 0.dp, sheen = false)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(placeholder, style = GlassType.body.copy(fontSize = 15.sp), color = p.text3, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = GlassType.body.copy(fontSize = 15.sp, color = p.text),
            cursorBrush = SolidColor(p.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun StatLine(parts: List<Pair<String, String>>, modifier: Modifier = Modifier, center: Boolean = false) {
    val p = LocalGlass.current
    Text(
        androidx.compose.ui.text.buildAnnotatedString {
            parts.forEachIndexed { i, (num, label) ->
                if (i > 0) withStyle(SpanStyle(color = p.text3)) { append("\u00A0· ") }
                withStyle(SpanStyle(color = p.text, fontWeight = FontWeight.ExtraBold)) { append(num.replace(' ', '\u00A0')) }
                withStyle(SpanStyle(color = p.text2)) { append("\u00A0" + label.replace(' ', '\u00A0')) }
            }
        },
        style = GlassType.small.copy(fontSize = 13.sp),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier,
    )
}

@Composable
fun PositionText(active: Boolean, style: androidx.compose.ui.text.TextStyle, color: Color) {
    Text(formatDuration(if (active) NowPlaying.position else 0L), style = style, color = color)
}

@Composable
fun SkeletonRows(count: Int = 6) {
    val p = LocalGlass.current
    val fx = LocalFx.current
    val shift = if (fx.idle) {
        rememberInfiniteTransition(label = "skel").animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "skelX")
    } else null
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(count) { i ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(Modifier.size(46.dp), 12.dp, shift?.value)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(Modifier.fillMaxWidth(0.62f + 0.07f * (i % 3)).height(13.dp), 6.dp, shift?.value)
                    Spacer(Modifier.height(7.dp))
                    SkeletonBlock(Modifier.fillMaxWidth(0.34f + 0.05f * (i % 2)).height(10.dp), 5.dp, shift?.value)
                }
                Spacer(Modifier.width(12.dp))
                SkeletonBlock(Modifier.width(30.dp).height(10.dp), 5.dp, shift?.value)
            }
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, corner: Dp, shift: Float?) {
    val p = LocalGlass.current
    val base = p.text.copy(alpha = 0.07f)
    val hi = p.text.copy(alpha = 0.14f)
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                if (shift == null) {
                    drawRect(base)
                } else {
                    val x = size.width * shift
                    drawRect(
                        Brush.horizontalGradient(listOf(base, hi, base), startX = x - size.width * 0.6f, endX = x + size.width * 0.6f),
                    )
                }
            },
    )
}
