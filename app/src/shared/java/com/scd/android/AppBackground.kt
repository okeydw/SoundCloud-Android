package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
fun AppBackground() {
    val custom = customBackground(MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val path = Prefs.backgroundImage
    if (path == null) {
        if (custom != null) {
            Box(
                Modifier.fillMaxSize().background(
                    if (custom.size > 1) Brush.linearGradient(custom) else Brush.verticalGradient(listOf(custom[0], custom[0])),
                ),
            )
        }
        return
    }
    val blur = Prefs.backgroundBlur && BlurSupport.available
    val veil = MaterialTheme.colorScheme.background
    val dim = Prefs.backgroundDim.coerceIn(0f, 0.95f)

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = File(path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (blur && Prefs.backgroundBlurRadius > 0f) {
                        Modifier.blur(Prefs.backgroundBlurRadius.dp)
                    } else {
                        Modifier
                    }
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to veil.copy(alpha = dim),
                        1f to veil.copy(alpha = (dim + 0.12f).coerceAtMost(1f)),
                    )
                )
        )
    }
}
