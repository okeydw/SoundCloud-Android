package com.scd.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
    Font(R.font.inter_black, FontWeight.Black),
)

val Mono = FontFamily(Font(R.font.jetbrains_mono_medium, FontWeight.Medium))

val EaseApple = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

@Immutable
data class GlassPalette(
    val dark: Boolean,
    val bg: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val fillTop: Color,
    val fillMid: Color,
    val fillBottom: Color,
    val border: Color,
    val borderHi: Color,
    val sheen: Color,
    val scrim: Color,
    val accent: Color,
    val accentHover: Color,
    val glow: Color,
    val glowStrong: Color,
    val onAccent: Color,
    val accent2: Color = accent,
    val gradient: Boolean = false,
    val bgColors: List<Color>? = null,
)

fun GlassPalette.accentBrush(): Brush =
    if (gradient) Brush.horizontalGradient(listOf(accent, accent2)) else SolidColor(accent)

fun GlassPalette.accentFill(): Brush =
    if (gradient) Brush.linearGradient(listOf(accent, accent2)) else Brush.verticalGradient(listOf(accentHover, accent))

fun GlassPalette.bgBrush(): Brush {
    val c = bgColors
    return when {
        c == null -> SolidColor(bg)
        c.size > 1 -> Brush.linearGradient(c)
        else -> SolidColor(c[0])
    }
}

fun glassPalette(
    dark: Boolean,
    accent: Color,
    accent2: Color? = null,
    textColor: Color? = null,
    bgColors: List<Color>? = null,
): GlassPalette {
    val hover = lerp(accent, Color.White, 0.08f)
    val second = accent2 ?: accent
    val onAccent = if (lerp(accent, second, 0.5f).luminance() > 0.6f) Color.Black else Color.White
    val base = if (dark) {
        GlassPalette(
            dark = true,
            bg = Color(0xFF08080A),
            text = Color.White.copy(alpha = 0.92f),
            text2 = Color.White.copy(alpha = 0.55f),
            text3 = Color.White.copy(alpha = 0.25f),
            fillTop = Color.White.copy(alpha = 0.075f),
            fillMid = Color.White.copy(alpha = 0.025f),
            fillBottom = Color.White.copy(alpha = 0.045f),
            border = Color.White.copy(alpha = 0.10f),
            borderHi = Color.White.copy(alpha = 0.14f),
            sheen = Color.White.copy(alpha = 0.35f),
            scrim = Color(0xD116161A),
            accent = accent,
            accentHover = hover,
            glow = accent.copy(alpha = 0.20f),
            glowStrong = accent.copy(alpha = 0.42f),
            onAccent = onAccent,
        )
    } else {
        GlassPalette(
            dark = false,
            bg = Color(0xFFE3E5EE),
            text = Color(0xF00D0F18),
            text2 = Color(0x990D0F18),
            text3 = Color(0x520D0F18),
            fillTop = Color.White.copy(alpha = 0.62f),
            fillMid = Color.White.copy(alpha = 0.38f),
            fillBottom = Color.White.copy(alpha = 0.50f),
            border = Color(0xFF0D0F18).copy(alpha = 0.08f),
            borderHi = Color(0xFF0D0F18).copy(alpha = 0.12f),
            sheen = Color.White.copy(alpha = 0.85f),
            scrim = Color(0xF2EDEEF4),
            accent = accent,
            accentHover = hover,
            glow = accent.copy(alpha = 0.16f),
            glowStrong = accent.copy(alpha = 0.30f),
            onAccent = onAccent,
        )
    }
    var p = base.copy(accent2 = second, gradient = accent2 != null && accent2 != accent)
    if (textColor != null) {
        p = p.copy(text = textColor.copy(alpha = 0.94f), text2 = textColor.copy(alpha = 0.62f), text3 = textColor.copy(alpha = 0.32f))
    }
    if (bgColors != null && bgColors.isNotEmpty()) {
        p = p.copy(bg = bgColors.first(), bgColors = bgColors)
    }
    return p
}

enum class FxMode(val key: String) { Max("max"), Balance("balance"), Light("light") }

@Immutable
data class Fx(val mode: FxMode) {
    val blur: Boolean get() = mode != FxMode.Light && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val blurScale: Float get() = if (mode == FxMode.Max) 1f else 0.5f
    val orbs: Int get() = when (mode) {
        FxMode.Max -> 3
        FxMode.Balance -> 2
        FxMode.Light -> 2
    }
    val drift: Boolean get() = mode != FxMode.Light
    val twinkle: Boolean get() = mode == FxMode.Max
    val pulse: Boolean get() = mode == FxMode.Max
    val reflection: Boolean get() = mode == FxMode.Max
    val idle: Boolean get() = mode != FxMode.Light
}

fun defaultFxMode(context: Context): FxMode {
    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || lowRam) FxMode.Light else FxMode.Max
}

fun resolveFxMode(context: Context): FxMode =
    FxMode.entries.firstOrNull { it.key == Prefs.effects } ?: defaultFxMode(context)

val LocalGlass = staticCompositionLocalOf { glassPalette(true, Color(0xFFFF5500)) }
val LocalFx = staticCompositionLocalOf { Fx(FxMode.Light) }

@Composable
fun glassIsDark(): Boolean = when (Prefs.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

private fun Typography.inter(): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = Inter),
    displayMedium = displayMedium.copy(fontFamily = Inter),
    displaySmall = displaySmall.copy(fontFamily = Inter),
    headlineLarge = headlineLarge.copy(fontFamily = Inter, fontWeight = FontWeight.Black),
    headlineMedium = headlineMedium.copy(fontFamily = Inter, fontWeight = FontWeight.Black),
    headlineSmall = headlineSmall.copy(fontFamily = Inter, fontWeight = FontWeight.ExtraBold),
    titleLarge = titleLarge.copy(fontFamily = Inter, fontWeight = FontWeight.ExtraBold),
    titleMedium = titleMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    titleSmall = titleSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    bodyLarge = bodyLarge.copy(fontFamily = Inter),
    bodyMedium = bodyMedium.copy(fontFamily = Inter),
    bodySmall = bodySmall.copy(fontFamily = Inter),
    labelLarge = labelLarge.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelMedium = labelMedium.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
    labelSmall = labelSmall.copy(fontFamily = Inter, fontWeight = FontWeight.SemiBold),
)

object GlassType {
    val hero = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = (-0.045).em)
    val title = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 30.sp, letterSpacing = (-0.035).em)
    val section = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Black, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.03).em)
    val rowTitle = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp, letterSpacing = (-0.01).em)
    val body = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp)
    val small = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, lineHeight = 16.sp)
    val eyebrow = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 10.5.sp, letterSpacing = 0.22.em)
    val mono = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.02.em)
}

@Composable
fun GlassTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = glassIsDark()
    val accent = Color(Prefs.accent)
    val accent2: Color? = null
    val textColor = Prefs.textColor.takeIf { it != 0 }?.let { Color(it) }
    val bgKey = Prefs.bgColor1 to Prefs.bgColor2
    val palette = remember(dark, accent, accent2, textColor, bgKey) {
        glassPalette(dark, accent, accent2, textColor, customBackground(dark))
    }
    val fx = remember(Prefs.effects) { Fx(resolveFxMode(context)) }
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = accent,
        secondary = accent,
        onPrimary = palette.onAccent,
        background = palette.bg,
        primaryContainer = palette.glowStrong,
        onPrimaryContainer = palette.text,
        surface = if (dark) Color(0xFF121216) else Color(0xFFFAFAFC),
        surfaceVariant = if (dark) Color(0xFF1C1C22) else Color(0xFFE9E9EF),
        onBackground = palette.text,
        onSurface = palette.text,
        onSurfaceVariant = palette.text2,
        outline = palette.borderHi,
        outlineVariant = palette.border,
        error = Color(0xFFFF5A5F),
        onError = Color.White,
        errorContainer = Color(0xFFFF5A5F).copy(alpha = 0.18f),
        onErrorContainer = palette.text,
    )
    MaterialTheme(colorScheme = scheme, typography = remember { Typography().inter() }) {
        CompositionLocalProvider(
            LocalGlass provides palette,
            LocalFx provides fx,
            LocalDialogSkin provides GlassDialogSkin,
            androidx.compose.material3.LocalContentColor provides palette.text,
            content = content,
        )
    }
}
