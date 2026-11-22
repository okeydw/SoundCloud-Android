package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AlbumActions(key: String, title: String, cover: String?, info: SavedAlbumInfo? = null, onRemoved: () -> Unit = {}, shuffled: Boolean, shuffle: () -> Unit, load: suspend () -> List<Track>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = LocalPlaylists.find("local:$key")
    var busy by remember(key) { mutableStateOf(false) }
    var failed by remember(key) { mutableStateOf(false) }
    val downloading = key in Downloads.activeLists
    val complete = saved != null && saved.tracks.isNotEmpty() && saved.tracks.all { it.urn in Downloads.downloaded }
    fun save(download: Boolean) {
        if (busy) return
        busy = true
        failed = false
        scope.launch {
            try {
                val tracks = load().filter { it.urn.isNotBlank() && !it.urn.startsWith("wanted:") }
                if (tracks.isEmpty()) { failed = true; return@launch }
                LocalPlaylists.saveAlbum(key, title, cover, tracks, info)
                if (download) Downloads.enqueue(context, tracks, listKey = key)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            AlbumActionButton(if (saved != null) R.drawable.ic_check else R.drawable.ic_plus,
                stringResource(if (saved != null) R.string.remove_album else R.string.save_album), saved != null, !busy) {
                if (saved != null) { LocalPlaylists.delete(saved.id); onRemoved() } else save(false)
            }
            AlbumActionButton(if (downloading) R.drawable.ic_clear else if (complete) R.drawable.ic_check else R.drawable.ic_download,
                stringResource(if (downloading) R.string.cd_cancel_download else R.string.cd_download),
                downloading || complete, !busy && (downloading || !Prefs.offline && !complete)) {
                if (downloading) Downloads.cancelList(context, key) else save(true)
            }
            AlbumActionButton(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle,
                stringResource(R.string.notif_shuffle), shuffled, true, shuffle)
        }
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        if (failed) Text(stringResource(R.string.error_network), style = MaterialTheme.typography.labelSmall)
    }
}
