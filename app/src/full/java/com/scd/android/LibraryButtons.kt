package com.scd.android

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun TrackPresetButton(label: String, saved: Boolean, onClick: () -> Unit) {
    val p = LocalGlass.current
    val glow = if (saved) Modifier.softShadow(CircleShape, 14.dp, p.accent.copy(alpha = 0.55f)) else Modifier
    Text(label, style = GlassType.rowTitle, color = if (saved) p.accent else p.text,
        modifier = glow.glass(CircleShape, p, shadow = 0.dp, tint = if (saved) p.glowStrong else Color.Unspecified,
            borderColor = if (saved) p.accent.copy(alpha = 0.55f) else Color.Unspecified)
            .pressable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp))
}

@Composable
internal fun AlbumActionButton(icon: Int, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconOrb(icon, label, size = 48.dp, iconSize = 22.dp, active = active, enabled = enabled, onClick = onClick)
}
