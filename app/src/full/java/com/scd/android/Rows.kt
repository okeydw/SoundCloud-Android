package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GlassTrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    note: String? = null,
) {
    val p = LocalGlass.current
    val scope = rememberCoroutineScope()
    val current = NowPlaying.urn == track.urn
    var menu by remember { mutableStateOf(false) }
    if (menu) TrackActionsSheet(track, onDismiss = { menu = false }, onRemove = onRemove, onPlay = onClick)
    val locked = !Playback.playable(track) || dimmed
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (current) {
                    Modifier
                        .background(Brush.horizontalGradient(listOf(p.glowStrong.copy(alpha = 0.35f), p.glow.copy(alpha = 0.04f))))
                        .border(0.75.dp, p.accent.copy(alpha = 0.45f), shape)
                } else Modifier,
            )
            .combinedClickable(onClick = { if (!locked) onClick() }, onLongClick = { menu = true })
            .padding(horizontal = 8.dp, vertical = 7.dp)
            .graphicsLayer { alpha = if (locked) 0.42f else 1f },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp)) {
            Artwork(track.artModel("t120x120"), Modifier.size(46.dp), corner = 12.dp)
            if (current) {
                Box(
                    Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqBars(NowPlaying.isPlaying, Color.White, Modifier.width(18.dp).height(16.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    style = GlassType.rowTitle,
                    color = p.text,
                    maxLines = 1,
                    softWrap = !current,
                    overflow = if (current) TextOverflow.Clip else TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).then(if (current) Modifier.basicMarquee() else Modifier),
                )
                if (track.goPlus) {
                    Spacer(Modifier.width(6.dp))
                    StarBadgeGlass()
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Downloads.isDownloaded(track.urn)) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = p.accent, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(track.user?.username ?: "", style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (note == null) {
            Text(formatDuration(track.displayDuration), style = GlassType.mono, color = p.text3)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text(formatDuration(track.displayDuration), style = GlassType.mono, color = p.text3)
                Text(note, style = GlassType.mono, color = p.text2, maxLines = 1)
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            val liked = Likes.isLiked(track.urn)
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable { scope.launch { Likes.toggle(track) } },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                    stringResource(if (liked) R.string.notif_unlike else R.string.notif_like),
                    tint = if (liked) p.accent else p.text3,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

fun LazyListScope.trackItems(
    tracks: List<Track>,
    onPlay: (Track) -> Unit,
    dimUndownloaded: Boolean = false,
    trailing: (@Composable (Track) -> Unit)? = null,
    onRemove: ((Track) -> Unit)? = null,
    note: (@Composable (Track) -> String)? = null,
) {
    items(tracks, key = { "t:" + it.urn }, contentType = { "track" }) { t ->
        GlassTrackRow(
            track = t,
            onClick = { onPlay(t) },
            modifier = Modifier.padding(horizontal = 12.dp),
            dimmed = dimUndownloaded && !Downloads.isDownloaded(t.urn),
            trailing = trailing?.let { slot -> @Composable { slot(t) } },
            onRemove = onRemove?.let { remove -> { remove(t) } },
            note = note?.invoke(t),
        )
    }
}

@Composable
fun TrackCard(track: Track, onClick: () -> Unit, width: Dp = 138.dp) {
    val p = LocalGlass.current
    val current = NowPlaying.urn == track.urn
    Column(Modifier.width(width).pressable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
            Artwork(
                track.artModel("t250x250"),
                Modifier
                    .fillMaxSize()
                    .then(if (current) Modifier.border(1.5.dp, p.accent, RoundedCornerShape(16.dp)) else Modifier),
                corner = 16.dp,
            )
            if (current) {
                EqBars(NowPlaying.isPlaying, Color.White, Modifier.align(Alignment.BottomStart).padding(10.dp).width(20.dp).height(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(track.title, style = GlassType.small.copy(fontWeight = FontWeight.SemiBold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.user?.username ?: "", style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun PlaylistRowGlass(
    playlist: Playlist,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    syncing: Boolean = false,
    locked: Boolean = false,
) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistArtwork(playlist, 52.dp, 14.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(painterResource(R.drawable.ic_lock), stringResource(R.string.playlist_private), tint = p.text2, modifier = Modifier.size(13.dp))
                }
            }
            if (subtitle != null) {
                Text(subtitle, style = GlassType.small, color = if (syncing) p.accent else p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (syncing) {
            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.playlist_syncing), tint = p.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun ArtistRowGlass(name: String, avatar: String?, subtitle: String?, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Api.artworkUrl(avatar, "t120x120"),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(CircleShape).background(p.fillTop),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = GlassType.small, color = p.text2, maxLines = 1)
        }
    }
}

@Stable
class TrackPager(private val scope: CoroutineScope, private val loader: suspend (Int, Boolean) -> Pair<List<Track>, Boolean>) {
    var items by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var page = 0
    private var generation = 0
    private var freshAt = 0L
    private var syncing = false

    fun refresh() {
        if (loading) return
        loading = true
        val gen = ++generation
        freshAt = System.currentTimeMillis()
        scope.launch {
            try {
                val (batch, more) = loader(0, false)
                if (gen == generation) {
                    items = batch.distinctBy { it.urn }
                    hasMore = more
                    page = 0
                    error = null
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = e.message
            } finally {
                loading = false
            }
            syncHead(gen)
        }
    }

    fun refreshIfStale(maxAgeMs: Long = 5 * 60_000L) {
        if (Prefs.offline || loading || syncing || System.currentTimeMillis() - freshAt < maxAgeMs) return
        freshAt = System.currentTimeMillis()
        val gen = generation
        scope.launch { syncHead(gen) }
    }

    private suspend fun syncHead(gen: Int) {
        if (Prefs.offline || syncing) return
        syncing = true
        try {
            val (batch, more) = runCatching { loader(0, true) }.getOrNull() ?: return
            if (gen != generation || batch.isEmpty()) return
            freshAt = System.currentTimeMillis()
            if (page == 0) {
                items = batch.distinctBy { it.urn }
                hasMore = more
            } else {
                items = (batch + items).distinctBy { it.urn }
            }
            error = null
        } finally {
            syncing = false
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        loading = true
        val gen = generation
        scope.launch {
            try {
                val next = page + 1
                val (batch, more) = loader(next, false)
                if (gen == generation) {
                    items = (items + batch).distinctBy { it.urn }
                    page = next
                    hasMore = more
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (gen == generation) hasMore = false
            } finally {
                loading = false
            }
        }
    }

    suspend fun all(): List<Track> {
        var more = hasMore
        var p = page
        var acc = items
        while (more && acc.size < 10_000) {
            p++
            val (batch, next) = runCatching { loader(p, false) }.getOrNull() ?: break
            acc = (acc + batch).distinctBy { it.urn }
            more = next
        }
        items = acc
        page = p
        hasMore = false
        return acc
    }
}

@Composable
fun rememberPaged(key: Any?, loader: suspend (Int, Boolean) -> Pair<List<Track>, Boolean>): TrackPager {
    val scope = rememberCoroutineScope()
    val state = remember(key) { TrackPager(scope, loader) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(key) { state.refresh() }
    LaunchedEffect(key, owner) {
        owner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { state.refreshIfStale() }
    }
    return state
}

@Composable
fun GlassSheet(onDismiss: () -> Unit, title: String? = null, icon: Int? = null, content: @Composable () -> Unit) {
    val p = LocalGlass.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss, indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }), contentAlignment = Alignment.BottomCenter) {
            GlassModalFrame(
                Modifier
                    .padding(10.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .heightIn(max = 640.dp)
                    .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {},
            ) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(p.text3))
                    if (title != null) {
                        Spacer(Modifier.height(10.dp))
                        ModalHeader(icon, title, onClose = onDismiss, modifier = Modifier.padding(horizontal = 18.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun SheetAction(icon: Int, label: String, tint: Color? = null, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = tint ?: p.text, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = GlassType.rowTitle, color = tint ?: p.text)
    }
}

@Composable
fun PlaylistPickerSheet(track: Track, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val p = LocalGlass.current
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CreatePlaylistDialog(onDismiss = { creating = false; onDismiss() }) { title, private ->
            LocalPlaylists.create(title, private, listOf(track))
            android.widget.Toast.makeText(context, context.getString(R.string.added), android.widget.Toast.LENGTH_SHORT).show()
        }
        return
    }
    GlassSheet(onDismiss, stringResource(R.string.add_to_playlist)) {
        SheetAction(R.drawable.ic_plus, stringResource(R.string.new_playlist), tint = p.accent) { creating = true }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(LocalPlaylists.visible, key = { it.id }) { pl ->
                PlaylistRowGlass(
                    playlist = pl.asPlaylist(),
                    title = pl.title,
                    subtitle = (if (pl.localOnly) stringResource(R.string.playlist_local) + " · " else "") + stringResource(R.string.tracks_count, pl.count),
                    syncing = pl.syncing,
                    locked = pl.isPrivate && !pl.localOnly,
                    onClick = {
                        val added = LocalPlaylists.add(pl.id, track)
                        android.widget.Toast.makeText(
                            context,
                            context.getString(if (added) R.string.added else R.string.playlist_already_has),
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    },
                )
            }
        }
    }
}
