package com.scd.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { androidx.compose.material3.Icon(painterResource(R.drawable.ic_timer), null) },
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                val status = sleepTimerStatus()
                Text(
                    status ?: stringResource(R.string.sleep_timer_hint),
                    color = if (status != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SleepTimer.MINUTES.forEach { m ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                SleepTimer.start(m)
                                onDismiss()
                            },
                            label = { Text(stringResource(R.string.sleep_timer_minutes, m), maxLines = 1) },
                        )
                    }
                    FilterChip(
                        selected = SleepTimer.endOfTrack,
                        onClick = {
                            SleepTimer.startEndOfTrack()
                            onDismiss()
                        },
                        label = { Text(stringResource(R.string.sleep_timer_end_of_track), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
        confirmButton = {
            if (SleepTimer.endsAt > 0L) {
                TextButton(onClick = { SleepTimer.extend() }) { Text(stringResource(R.string.sleep_timer_extend, SleepTimer.EXTEND_MINUTES)) }
            }
        },
        dismissButton = {
            if (SleepTimer.active) {
                TextButton(onClick = {
                    SleepTimer.cancel()
                    onDismiss()
                }) { Text(stringResource(R.string.sleep_timer_off)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
