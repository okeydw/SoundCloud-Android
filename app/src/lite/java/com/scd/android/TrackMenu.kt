package com.scd.android

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun TrackMenuDialog(track: Track, onDismiss: () -> Unit, onPlay: () -> Unit, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    val controller = LocalMediaController.current
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    if (picking) {
        PlaylistPickDialog(track, onDismiss = onDismiss)
        return
    }
    if (blocking) {
        BlockConfirmDialog(BlockedArtists.targets(track), onDismiss = onDismiss)
        return
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackArtwork(track.artModel("t120x120"), 52.dp)
                Spacer(Modifier.width(12.dp))
                SelectionContainer(Modifier.weight(1f)) {
                    Column {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            track.user?.username ?: "",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            MenuRow(R.drawable.ic_play, stringResource(R.string.track_menu_play)) {
                onDismiss()
                onPlay()
            }
            if (controller != null && controller.currentMediaItem?.mediaId != track.urn) {
                MenuRow(R.drawable.ic_queue_next, stringResource(R.string.track_menu_play_next)) {
                    onDismiss()
                    val ok = Playback.playNext(controller, track)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(if (ok) R.string.play_next_done else R.string.play_next_failed),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            if (!Prefs.offline && controller != null) {
                MenuRow(R.drawable.ic_mix, stringResource(R.string.track_menu_radio)) {
                    onDismiss()
                    android.widget.Toast.makeText(context, context.getString(R.string.radio_starting), android.widget.Toast.LENGTH_SHORT).show()
                    Playback.radioNow(controller, track) { added ->
                        if (added == 0) android.widget.Toast.makeText(context.applicationContext, context.getString(R.string.radio_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            MenuRow(R.drawable.ic_plus, stringResource(R.string.add_to_playlist)) { picking = true }
            val liked = Likes.isLiked(track.urn)
            MenuRow(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart, stringResource(if (liked) R.string.notif_unlike else R.string.notif_like)) {
                onDismiss()
                scope.launch { Likes.toggle(track) }
            }
            if (!Downloads.isDownloaded(track.urn) && !Prefs.offline) {
                MenuRow(R.drawable.ic_download, stringResource(R.string.cd_download)) {
                    onDismiss()
                    Downloads.enqueue(context, listOf(track))
                }
            }
            track.permalink_url?.let { url ->
                MenuRow(R.drawable.ic_share, stringResource(R.string.cd_share)) {
                    onDismiss()
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, url)
                    }
                    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
                }
            }
            if (!Prefs.offline) {
                track.catalogArtist?.let { ca ->
                    MenuRow(R.drawable.ic_mic, stringResource(R.string.catalog_open, ca.name)) {
                        onDismiss()
                        NavEvents.openCatalog(ca.id)
                    }
                }
                track.user?.urn?.takeIf { it.isNotBlank() }?.let { u ->
                    MenuRow(R.drawable.ic_user, stringResource(R.string.open_profile)) {
                        onDismiss()
                        NavEvents.openUser(u)
                    }
                }
            }
            if (Prefs.userUrn != null && !Prefs.offline && BlockedArtists.targets(track).isNotEmpty()) {
                MenuRow(R.drawable.ic_block, stringResource(R.string.block_artist)) { blocking = true }
            }
            if (onRemove != null) {
                MenuRow(R.drawable.ic_trash, stringResource(R.string.playlist_remove_track), tint = MaterialTheme.colorScheme.error) {
                    onDismiss()
                    onRemove()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistPickDialog(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreatePlaylistDialog(onDismiss = onDismiss) { title, private ->
            LocalPlaylists.create(title, private, listOf(track))
            android.widget.Toast.makeText(context, context.getString(R.string.added), android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.add_to_playlist),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp).basicMarquee(),
            )
            Spacer(Modifier.height(6.dp))
            MenuRow(R.drawable.ic_plus, stringResource(R.string.new_playlist), tint = MaterialTheme.colorScheme.primary) { creating = true }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                items(LocalPlaylists.visible, key = { it.id }) { pl ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val added = LocalPlaylists.add(pl.id, track)
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(if (added) R.string.added else R.string.playlist_already_has),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistArtwork(pl.asPlaylist(), 40.dp, 6.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(pl.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
