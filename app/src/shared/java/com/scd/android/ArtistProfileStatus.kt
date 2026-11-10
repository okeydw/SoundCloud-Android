package com.scd.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun ArtistProfileStatus(profile: ArtistProfileState, onRetry: () -> Unit) {
    if (!profile.loading && !profile.failed) return
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (profile.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            stringResource(if (profile.loading) R.string.artist_info_loading else R.string.artist_info_failed),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!profile.loading) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry_action)) }
    }
}
