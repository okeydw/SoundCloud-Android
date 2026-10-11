package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ReorderDialog(tracks: List<Track>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var list by remember { mutableStateOf(tracks.distinctBy { it.urn }) }
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        list = list.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.reorder_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                stringResource(R.string.reorder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), state = listState) {
                items(list, key = { it.urn }) { t ->
                    ReorderableItem(reorder, key = t.urn) { dragging ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    if (dragging) MaterialTheme.colorScheme.surfaceVariant else androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .alpha(if (dragging) 0.92f else 1f)
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = t.artModel("t120x120"),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                Text(
                                    t.user?.username ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                painterResource(R.drawable.ic_drag),
                                stringResource(R.string.reorder_handle),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.draggableHandle(
                                    onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                ).padding(8.dp).size(22.dp),
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = {
                    onSave(list.map { it.urn })
                    onDismiss()
                }) { Text(stringResource(R.string.save)) }
            }
        }
    }
}
