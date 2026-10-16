package com.scd.android

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun fakeWave(urn: String?, bars: Int): List<Float> {
    var seed = (urn ?: "x").hashCode().toLong() and 0xffffffffL
    return List(bars) { i ->
        seed = (seed * 1103515245L + 12345L) and 0x7fffffffL
        val noise = (seed % 1000) / 1000f
        val shape = 0.55f + 0.35f * kotlin.math.sin(i / bars.toFloat() * 9.4f)
        (0.18f + 0.62f * noise * shape + 0.12f).coerceIn(0.12f, 1f)
    }
}

@Composable
fun rememberWaveSamples(urn: String?, waveformUrl: String?, bars: Int): List<Float> {
    var samples by remember(urn, bars) { mutableStateOf(WaveCache.get(urn)?.let { resample(it, bars) }) }
    LaunchedEffect(urn, waveformUrl) {
        if (samples != null || urn == null) return@LaunchedEffect
        val url = waveformUrl ?: runCatching { Api.trackByUrn(urn).waveform_url }.getOrNull() ?: return@LaunchedEffect
        val loaded = runCatching { Api.waveform(url) }.getOrNull() ?: return@LaunchedEffect
        WaveCache.put(urn, loaded)
        samples = resample(loaded, bars)
    }
    return samples ?: remember(urn, bars) { fakeWave(urn, bars) }
}

private fun resample(src: List<Float>, bars: Int): List<Float> {
    if (src.size == bars || src.isEmpty()) return src
    return List(bars) { i ->
        val a = i * src.size / bars
        val b = ((i + 1) * src.size / bars).coerceAtLeast(a + 1).coerceAtMost(src.size)
        src.subList(a, b).average().toFloat()
    }
}

private fun DrawScope.bars(samples: List<Float>, color: Color, height: Float, gap: Float, mirror: Boolean, brush: Brush? = null) {
    val n = samples.size
    if (n == 0) return
    val slot = size.width / n
    val w = (slot - gap).coerceAtLeast(1f)
    val mid = height / 2f
    samples.forEachIndexed { i, v ->
        val h = (v * height).coerceAtLeast(w)
        val top = if (mirror) height else mid - h / 2f
        val barH = if (mirror) h * 0.35f else h
        val topLeft = Offset(i * slot + gap / 2f, top)
        if (brush != null) {
            drawRoundRect(brush, topLeft, Size(w, barH), CornerRadius(w / 2f))
        } else {
            drawRoundRect(color, topLeft, Size(w, barH), CornerRadius(w / 2f))
        }
    }
}

@Composable
fun Waveform(
    samples: List<Float>,
    progress: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    reflection: Boolean = false,
    glow: Boolean = true,
    onSeek: ((Float) -> Unit)? = null,
) {
    val p = LocalGlass.current
    val muted = p.text.copy(alpha = 0.26f)
    var drag by remember { mutableFloatStateOf(-1f) }
    val seek by rememberUpdatedState(onSeek)
    val total = if (reflection) height * 1.35f else height
    val glowSamples = remember(samples) { samples.map { (it * 1.12f).coerceAtMost(1.05f) } }
    Spacer(
        modifier
            .fillMaxWidth()
            .height(total)
            .then(
                if (onSeek != null) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures { o -> seek?.invoke((o.x / size.width).coerceIn(0f, 1f)) }
                        }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> drag = (o.x / size.width).coerceIn(0f, 1f) },
                                onDragEnd = {
                                    if (drag >= 0f) seek?.invoke(drag)
                                    drag = -1f
                                },
                                onDragCancel = { drag = -1f },
                                onHorizontalDrag = { change, _ -> drag = (change.position.x / size.width).coerceIn(0f, 1f) },
                            )
                        }
                } else Modifier,
            )
            .drawBehind {
                val barsH = height.toPx()
                val gap = 2.dp.toPx()
                val frac = (if (drag >= 0f) drag else progress()).coerceIn(0f, 1f)
                val cut = size.width * frac
                bars(samples, muted, barsH, gap, false)
                if (glow) {
                    clipRect(right = cut) {
                        bars(glowSamples, p.glowStrong.copy(alpha = 0.35f), barsH, 0f, false)
                    }
                }
                clipRect(right = cut) {
                    bars(samples, p.accent, barsH, gap, false, if (p.gradient) Brush.horizontalGradient(listOf(p.accent, p.accent2), endX = size.width) else null)
                }
                if (reflection) {
                    val fade = Brush.verticalGradient(listOf(muted.copy(alpha = 0.22f), Color.Transparent), startY = barsH, endY = size.height)
                    bars(samples, muted, barsH, gap, true, fade)
                    clipRect(right = cut) {
                        bars(samples, p.accent, barsH, gap, true, Brush.verticalGradient(listOf(p.accent.copy(alpha = 0.3f), Color.Transparent), startY = barsH, endY = size.height))
                    }
                }
            },
    )
}
