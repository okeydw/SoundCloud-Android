package com.scd.android

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

val LocalMediaController = androidx.compose.runtime.staticCompositionLocalOf<androidx.media3.session.MediaController?> { null }

fun customBackground(dark: Boolean): List<Color>? {
    if (Prefs.bgColor1 == 0) return null
    val colors = listOfNotNull(Prefs.bgColor1, Prefs.bgColor2.takeIf { it != 0 }).map { Color(it) }
    return if (dark) colors else colors.map { lerp(it, Color.White, 0.55f) }
}

fun accentColors(): List<Color> = listOf(Color(Prefs.accent))

fun accentBrush(): Brush {
    val colors = accentColors()
    return if (colors.size > 1) Brush.horizontalGradient(colors) else SolidColor(colors.first())
}

@Composable
fun rememberCoverPicker(urn: String): () -> Unit {
    val context = LocalContext.current
    var source by remember(urn) { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) source = uri
    }
    source?.let { uri ->
        CropDialog(
            source = uri,
            onDismiss = { source = null },
            aspect = 1f,
            save = { PlaylistCovers.set(urn, it) },
        ) { ok ->
            source = null
            android.widget.Toast.makeText(
                context,
                context.getString(if (ok) R.string.cover_saved else R.string.background_failed),
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }
    return { runCatching { launcher.launch("image/*") } }
}
