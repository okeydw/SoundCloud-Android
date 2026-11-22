package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable
fun SortButton(enabled: Boolean, description: String, onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp).semantics { stateDescription = description },
        colors = IconButtonDefaults.iconButtonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant, contentColor = androidx.compose.ui.graphics.Color.White, disabledContentColor = color.copy(alpha = 0.18f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, content = content)
    }
}
