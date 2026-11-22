package com.scd.android

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun TrackPresetButton(label: String, saved: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(onClick = onClick, shape = CircleShape,
        color = if (saved) colors.primary.copy(alpha = 0.16f) else colors.onSurface.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, if (saved) colors.primary else colors.outlineVariant)) {
        Text(label, modifier = androidx.compose.ui.Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            color = if (saved) colors.primary else colors.onSurface)
    }
}

@Composable
internal fun AlbumActionButton(icon: Int, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), label, tint = if (active) MaterialTheme.colorScheme.primary else LocalContentColor.current)
    }
}
