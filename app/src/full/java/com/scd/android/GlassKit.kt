package com.scd.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

fun Modifier.softShadow(shape: Shape, radius: Dp, color: Color, offsetY: Dp = 0.dp): Modifier =
    if (radius <= 0.dp || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) this else drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = androidx.compose.ui.graphics.Path().apply { addOutline(outline) }
        clipPath(path, androidx.compose.ui.graphics.ClipOp.Difference) {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.color = android.graphics.Color.BLACK
                    setShadowLayer(radius.toPx(), 0f, offsetY.toPx(), color.toArgb())
                }
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        }
    }

fun Modifier.glass(
    shape: Shape,
    p: GlassPalette,
    shadow: Dp = 14.dp,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    sheen: Boolean = true,
): Modifier {
    var m = this
    if (shadow > 0.dp) m = m.softShadow(shape, shadow, Color.Black.copy(alpha = if (p.dark) 0.45f else 0.14f), shadow / 3)
    m = m.clip(shape).background(Brush.verticalGradient(listOf(p.fillTop, p.fillMid, p.fillBottom)))
    if (tint.isSpecified) {
        m = m.background(Brush.linearGradient(listOf(tint, Color.Transparent), start = Offset.Zero, end = Offset.Infinite))
    }
    if (sheen) {
        m = m.drawWithContent {
            drawContent()
            val w = size.width
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, p.sheen, Color.Transparent), startX = w * 0.08f, endX = w * 0.92f),
                start = Offset(w * 0.08f, 0.5f),
                end = Offset(w * 0.92f, 0.5f),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
    return m.border(0.75.dp, if (borderColor.isSpecified) borderColor else p.border, shape)
}

@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(220, easing = EaseApple), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = LocalGlass.current.text2) {
    Text(text.uppercase(), style = GlassType.eyebrow, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

@Composable
fun LiveDot(live: Boolean, color: Color = LocalGlass.current.accent, size: Dp = 7.dp) {
    val fx = LocalFx.current
    val alpha = if (live && fx.idle) {
        val t = rememberInfiniteTransition(label = "live")
        t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "liveAlpha").value
    } else 1f
    Box(
        Modifier
            .size(size)
            .graphicsLayer { this.alpha = alpha }
            .drawBehind {
                drawCircle(color.copy(alpha = 0.35f), radius = this.size.minDimension)
                drawCircle(color)
            },
    )
}

@Composable
fun LivePill(label: String, live: Boolean, modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    Row(
        modifier
            .glass(CircleShape, p, shadow = 0.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(live)
        Spacer(Modifier.width(8.dp))
        Text(label.uppercase(), style = GlassType.eyebrow, color = p.text)
    }
}

@Composable
fun GenreDot(color: Color, size: Dp = 7.dp) {
    Box(
        Modifier
            .size(size)
            .drawBehind {
                drawCircle(color.copy(alpha = 0.3f), radius = this.size.minDimension * 0.95f)
                drawCircle(color)
            },
    )
}

@Composable
fun GlassChip(
    text: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    dot: Color? = null,
    icon: Int? = null,
    iconTint: Color = LocalGlass.current.accent,
    active: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val p = LocalGlass.current
    Row(
        modifier
            .glass(CircleShape, p, shadow = 0.dp, tint = if (active) p.glow else Color.Unspecified, borderColor = if (active) p.glowStrong else Color.Unspecified, sheen = false)
            .then(if (onClick != null) Modifier.pressable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            GenreDot(dot)
            Spacer(Modifier.width(7.dp))
        }
        if (icon != null) {
            Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = if (mono) GlassType.mono else GlassType.small,
            color = p.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun IconOrb(
    icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    tint: Color? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val p = LocalGlass.current
    Box(
        modifier
            .size(size)
            .then(if (active) Modifier.softShadow(CircleShape, 14.dp, p.accent.copy(alpha = 0.55f)) else Modifier)
            .glass(CircleShape, p, shadow = 0.dp, tint = if (active) p.glowStrong else Color.Unspecified, borderColor = if (active) p.accent.copy(alpha = 0.55f) else Color.Unspecified)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription,
            tint = tint ?: if (active) p.accent else p.text,
            modifier = Modifier.size(iconSize).graphicsLayer { alpha = if (enabled) 1f else 0.4f },
        )
    }
}

@Composable
fun LabeledOrb(icon: Int, label: String, active: Boolean = false, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalGlass.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconOrb(icon, label, size = 50.dp, iconSize = 22.dp, active = active, tint = tint, onClick = onClick)
        Spacer(Modifier.height(5.dp))
        Text(label, style = GlassType.small.copy(fontSize = 11.sp), color = p.text2, maxLines = 1)
    }
}

@Composable
fun PlayOrb(playing: Boolean, size: Dp, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalGlass.current
    val fx = LocalFx.current
    val ring = if (playing && fx.pulse) {
        val t = rememberInfiniteTransition(label = "pulse")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "pulseT").value
    } else -1f
    Box(
        modifier
            .size(size)
            .drawBehind {
                val r = this.size.minDimension / 2f
                drawCircle(p.glowStrong, radius = r * 1.12f)
                if (ring >= 0f) {
                    drawCircle(
                        p.accent.copy(alpha = 0.45f * (1f - ring)),
                        radius = r * (1.05f + 0.35f * ring),
                        style = Stroke(width = 1.5.dp.toPx()),
                    )
                }
            }
            .shadow(14.dp, CircleShape, clip = false, ambientColor = p.accent, spotColor = p.accent)
            .clip(CircleShape)
            .background(if (p.gradient) p.accentFill() else Brush.radialGradient(listOf(p.accentHover, p.accent)))
            .border(0.75.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            androidx.compose.ui.res.stringResource(if (playing) R.string.cd_pause else R.string.cd_play),
            tint = p.onAccent,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}

@Composable
fun WhiteButton(text: String, icon: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalGlass.current
    val style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold)
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val short = androidx.compose.ui.res.stringResource(R.string.cd_play)
    val textWidth = remember(text, style, measurer) { measurer.measure(text, style, maxLines = 1).size.width }
    val shortWidth = remember(short, style, measurer) { measurer.measure(short, style, maxLines = 1).size.width }
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .height(48.dp)
            .shadow(18.dp, CircleShape, clip = false, ambientColor = p.accent, spotColor = p.accent)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color.White, Color(0xFFE5E7EB))))
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val chrome = with(density) { (44.dp + if (icon != null) 28.dp else 0.dp).toPx() }
        val unbounded = constraints.maxWidth == androidx.compose.ui.unit.Constraints.Infinity
        val label = when {
            icon == null || unbounded || constraints.maxWidth >= chrome + textWidth -> text
            short != text && constraints.maxWidth >= chrome + shortWidth -> short
            else -> null
        }
        if (label != null) {
            Row(
                Modifier.padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    Icon(painterResource(icon), null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                }
                Text(label, style = style, color = Color.Black, maxLines = 1)
            }
        } else {
            if (icon != null) Icon(painterResource(icon), text, tint = Color.Black, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun AccentButton(text: String, icon: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        modifier
            .height(46.dp)
            .shadow(16.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = p.accent, spotColor = p.accent)
            .clip(RoundedCornerShape(16.dp))
            .background(p.accentFill())
            .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(painterResource(icon), null, tint = p.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = GlassType.rowTitle.copy(fontWeight = FontWeight.Bold), color = p.onAccent, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun EqBars(playing: Boolean, color: Color, modifier: Modifier = Modifier, bars: Int = 4) {
    val levels = if (playing) {
        val t = rememberInfiniteTransition(label = "eq")
        (0 until bars).map { i ->
            t.animateFloat(
                0.25f,
                1f,
                infiniteRepeatable(tween(320 + i * 110, easing = LinearEasing), RepeatMode.Reverse),
                label = "eq$i",
            )
        }
    } else null
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        repeat(bars) { i ->
            val level = levels?.get(i)
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .drawBehind {
                        val h = size.height * (level?.value ?: (0.3f + 0.15f * (i % 3)))
                        drawRoundRect(color, topLeft = Offset(0f, size.height - h), size = androidx.compose.ui.geometry.Size(size.width, h), cornerRadius = CornerRadius(size.width / 2))
                    },
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalGlass.current
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.section, color = p.text)
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = GlassType.small, color = p.text2, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (action != null && onAction != null) {
            Text(
                "$action ›",
                style = GlassType.small.copy(fontWeight = FontWeight.SemiBold),
                color = p.text2,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
fun EmptyState(icon: Int, text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    val p = LocalGlass.current
    Column(modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(64.dp).glass(RoundedCornerShape(18.dp), p, shadow = 0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(icon), null, tint = p.text3, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(text, style = GlassType.body, color = p.text2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(horizontal = 32.dp))
        if (action != null && onAction != null) {
            Spacer(Modifier.height(14.dp))
            GlassChip(action, icon = R.drawable.ic_refresh, onClick = onAction)
        }
    }
}

@Composable
fun GradientText(text: String, style: TextStyle, colors: List<Color>, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, style = style.copy(brush = Brush.linearGradient(colors)), modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Artwork(model: Any?, modifier: Modifier, corner: Dp = 12.dp) {
    val p = LocalGlass.current
    var failed by remember(model) { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(p.fillTop, p.glow))),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null || failed) {
            Icon(painterResource(R.drawable.ic_music), null, tint = p.text3, modifier = Modifier.fillMaxSize(0.4f))
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
                onError = { failed = true },
            )
        }
    }
}

@Composable
fun GlassDivider(modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    Box(modifier.fillMaxWidth().height(0.75.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, p.border, Color.Transparent))))
}

@Composable
fun StarBadgeGlass(modifier: Modifier = Modifier) {
    val p = LocalGlass.current
    Text(
        "★ " + androidx.compose.ui.res.stringResource(R.string.star_badge).uppercase(),
        style = GlassType.eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.12.sp),
        color = p.onAccent,
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(p.accentHover, p.accent)))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
