package com.scd.android

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberAmbientTime(active: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(active, lifecycle) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = System.nanoTime()
            while (true) {
                delay(50)
                val now = System.nanoTime()
                time.floatValue += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    return time
}

private class StarSeed(val x: Float, val y: Float, val size: Float, val sparkle: Boolean, val delay: Float, val period: Float)

private val STARS = List(40) { i ->
    StarSeed(
        x = ((i * 37) % 100) / 100f,
        y = ((i * 53 + 11) % 100) / 100f,
        size = 1.2f + ((i * 7) % 5) * 0.5f,
        sparkle = i % 6 == 0,
        delay = (i * 0.27f) % 5f,
        period = 4f + (i % 5),
    )
}

private fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(path, color)
}

@Composable
fun Backdrop(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    stars: Boolean = true,
    wallpaper: Boolean = true,
    intensity: Float = 1f,
) {
    val p = LocalGlass.current
    val fx = LocalFx.current
    val time by rememberAmbientTime(fx.drift && NowPlaying.isPlaying)
    val orbColors = colors.ifEmpty { listOf(p.accent) }
    Box(modifier.fillMaxSize().background(p.bgBrush())) {
        val path = if (wallpaper) Prefs.backgroundImage else null
        if (path != null) {
            val blur = Prefs.backgroundBlur && BlurSupport.available && Prefs.backgroundBlurRadius > 0f
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blur) Modifier.blur(Prefs.backgroundBlurRadius.dp) else Modifier),
            )
            val dim = Prefs.backgroundDim.coerceIn(0f, 0.95f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to p.bg.copy(alpha = dim), 1f to p.bg.copy(alpha = (dim + 0.12f).coerceAtMost(1f))),
                ),
            )
        }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val t = time
                val w = size.width
                val h = size.height
                val alpha = (if (p.dark) 0.26f else 0.24f) * intensity
                val blend = if (p.dark) BlendMode.Screen else BlendMode.SrcOver
                val anchors = listOf(
                    Triple(Offset(w * 0.15f, h * 0.08f), w * 0.85f, 26f),
                    Triple(Offset(w * 0.95f, h * 0.42f), w * 0.75f, 32f),
                    Triple(Offset(w * 0.25f, h * 0.92f), w * 0.8f, 36f),
                )
                anchors.take(fx.orbs).forEachIndexed { i, (base, radius, period) ->
                    val phase = (t / period) * 2f * PI.toFloat() + i * 1.7f
                    val center = Offset(base.x + cos(phase) * w * 0.04f, base.y + sin(phase) * w * 0.05f)
                    val r = radius * (1f + 0.04f * sin(phase * 0.5f))
                    val c = orbColors[i % orbColors.size]
                    drawCircle(
                        brush = Brush.radialGradient(0f to c.copy(alpha = alpha), 0.65f to Color.Transparent, center = center, radius = r),
                        radius = r,
                        center = center,
                        blendMode = blend,
                    )
                }
                if (stars) {
                    val tint = if (p.dark) Color.White else p.accent
                    STARS.forEach { s ->
                        val tw = if (fx.twinkle) 0.2f + 0.7f * (0.5f + 0.5f * sin((t + s.delay) / s.period * 2f * PI.toFloat())) else 0.45f
                        val c = Offset(s.x * w, s.y * h)
                        if (s.sparkle) {
                            sparkle(c, (s.size + 3f).dp.toPx(), p.accent.copy(alpha = tw * 0.55f))
                        } else {
                            drawCircle(tint.copy(alpha = tw * 0.35f), radius = (s.size * 0.6f).dp.toPx(), center = c)
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun rememberArtColor(model: Any?): Color? {
    val context = LocalContext.current
    var color by remember(model) { mutableStateOf<Color?>(null) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        color = runCatching {
            val request = ImageRequest.Builder(context).data(model).allowHardware(false).size(96).build()
            val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                ?: return@runCatching null
            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                val swatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.dominantSwatch
                swatch?.rgb?.let { Color(it) }
            }
        }.getOrNull()
    }
    return color
}

@Composable
fun soundprintColors(): List<Color> {
    val p = LocalGlass.current
    val genres = remember(Library.soundprint) { topGenres(Library.soundprint, 2).map { it.color } }
    val now = NowPlaying.genre?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }?.let { genreColor(it) }
    return (listOf(p.accent) + listOfNotNull(now) + genres).distinct()
}
