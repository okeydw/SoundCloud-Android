package com.scd.android

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException

@Composable
fun TogetherDialog(onDismiss: () -> Unit) {
    val actionScope = rememberCoroutineScope()
    AppDialog(onDismiss) {
        Column(
            Modifier
                .padding(24.dp)
                .heightIn(max = 620.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.together_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            when (Together.phase) {
                Together.Phase.IDLE -> TogetherStart(actionScope)
                Together.Phase.CONNECTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.together_connecting))
                }
                Together.Phase.LIVE, Together.Phase.RECONNECTING -> TogetherLive()
            }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_close)) }
            }
        }
    }
}

@Composable
private fun TogetherHint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun TogetherStart(actionScope: CoroutineScope) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var rooms by remember { mutableStateOf<List<OpenRoom>?>(null) }
    var roomsFailed by remember { mutableStateOf(false) }

    fun launchAction(openRoom: Boolean = false, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        actionScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, Together.errorText(e, openRoom), android.widget.Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    fun refresh() {
        scope.launch {
            roomsFailed = false
            rooms = runCatching { Together.openRooms() }.onFailure { roomsFailed = true }.getOrNull()
        }
    }
    LaunchedEffect(Unit) { refresh() }

    if (Api.sessionId == null) {
        TogetherHint(stringResource(R.string.together_err_sign_in))
        return
    }
    TogetherHint(stringResource(R.string.together_hint))
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.together_open_room), style = MaterialTheme.typography.bodyMedium)
            TogetherHint(stringResource(R.string.together_open_room_hint))
        }
        Switch(checked = open, onCheckedChange = { open = it })
    }
    Spacer(Modifier.height(8.dp))
    Button(onClick = { launchAction { Together.host(open) } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.together_start))
    }

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.together_join_code), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = Together.cleanCode(it) },
            singleLine = true,
            placeholder = { Text("ABC234") },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = { launchAction { Together.join(code) } },
            enabled = !busy && code.length == Together.CODE_LENGTH,
        ) { Text(stringResource(R.string.together_join)) }
    }

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.together_open_rooms), Modifier.weight(1f), fontWeight = FontWeight.Medium)
        TextButton(onClick = { refresh() }) { Text(stringResource(R.string.together_refresh)) }
    }
    val list = rooms
    when {
        roomsFailed -> TogetherHint(stringResource(R.string.together_err_generic))
        list == null -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        list.isEmpty() -> TogetherHint(stringResource(R.string.together_no_rooms))
        else -> list.forEach { room ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(room.hostName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val track = room.track?.let { t -> listOfNotNull(t.artist, t.title).filter { it.isNotBlank() }.joinToString(" — ") }
                    TogetherHint(
                        listOfNotNull(track, stringResource(R.string.together_listeners, room.listeners, room.capacity))
                            .joinToString(" · "),
                    )
                }
                OutlinedButton(
                    onClick = { launchAction(openRoom = true) { Together.joinOpen(room.hostId) } },
                    enabled = !busy && !room.full,
                ) { Text(stringResource(if (room.full) R.string.together_full else R.string.together_join)) }
            }
        }
    }
}

@Composable
private fun TogetherLive() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val room = Together.room ?: return
    val host = Together.isHost

    Text(stringResource(R.string.together_code), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(room.code, fontSize = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, Together.shareText(room.code))
            }
            runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }) { Text(stringResource(R.string.together_share)) }
    }

    val status = when {
        Together.phase == Together.Phase.RECONNECTING -> stringResource(R.string.together_reconnecting)
        Together.sync == Together.Sync.WAITING -> stringResource(R.string.together_waiting, Together.waitingFor)
        Together.sync == Together.Sync.CATCHING_UP -> stringResource(R.string.together_catching_up)
        host -> stringResource(R.string.together_you_host)
        else -> stringResource(R.string.together_you_guest)
    }
    TogetherHint(status)

    if (host) {
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.together_open_room), style = MaterialTheme.typography.bodyMedium)
                TogetherHint(stringResource(R.string.together_open_room_hint))
            }
            Switch(
                checked = room.isPublic == true,
                onCheckedChange = { value -> scope.launch { runCatching { Together.setPublic(value) } } },
            )
        }
    }

    Spacer(Modifier.height(14.dp))
    Text(
        stringResource(R.string.together_members, room.members.size, Together.ROOM_CAPACITY),
        fontWeight = FontWeight.Medium,
    )
    Spacer(Modifier.height(6.dp))
    room.members.forEach { member ->
        val online = member.userId in room.online
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (online) Color(0xFF3DDC84) else MaterialTheme.colorScheme.outline, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                member.name.ifBlank { "—" },
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (member.userId == Together.selfId) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (member.userId == room.hostId) TogetherHint(stringResource(R.string.together_host_badge))
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { scope.launch { Together.leave() } }) {
            Text(stringResource(if (host) R.string.together_end else R.string.together_leave))
        }
    }
}
