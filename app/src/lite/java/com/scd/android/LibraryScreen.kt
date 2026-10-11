package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch


private sealed interface LibView {
    data object Root : LibView
    data object Liked : LibView
    data object Downloaded : LibView
    data object HistoryView : LibView
    data object Disliked : LibView
    data class LikedGenre(val key: String, val label: String) : LibView
    data class PlaylistView(val urn: String, val title: String, val owned: Boolean = true) : LibView
    data class ArtistTracks(val urn: String, val title: String, val avatar: String?) : LibView
}

@Composable
private fun greeting(): String {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_day
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
    )
}

@Composable
private fun StarBadge() {
    Row(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "★ " + stringResource(R.string.star_badge),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LibraryScreen(
    play: (List<Track>, Track) -> Unit,
    onSignOut: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    offline: Boolean = false,
) {
    var view by remember { mutableStateOf<LibView>(LibView.Root) }
    var showSettings by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { Library.refreshProfile(offline) }
    LaunchedEffect(Unit) { Library.loadSoundprint() }
    LaunchedEffect(view) {
        if (view == LibView.Root) Library.refreshPlaylists(offline)
    }

    if (showSettings) {
        SettingsScreen(
            username = Prefs.username,
            avatar = Prefs.avatarUrl,
            onBack = { showSettings = false },
            onLogout = {
                showSettings = false
                onSignOut()
            },
        )
        return
    }

    BackHandler(enabled = view != LibView.Root) { view = LibView.Root }

    when (val v = view) {
        LibView.Root -> Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    val avatar = Prefs.avatarUrl
                    if (avatar != null) {
                        AsyncImage(
                            model = Api.artworkUrl(avatar, "t120x120"),
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(painterResource(R.drawable.ic_user), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Prefs.username ?: "SoundCloud",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (Prefs.star) {
                            Spacer(Modifier.width(8.dp))
                            StarBadge()
                        }
                    }
                }
                IconButton(onClick = { showCreate = true }) {
                    Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.new_playlist), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(painterResource(R.drawable.ic_settings), iconLabel(R.drawable.ic_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val printLikes = Library.soundprint
            LazyColumn(Modifier.fillMaxSize()) {
                if (printLikes.isNotEmpty()) {
                    item {
                        SoundprintCard(
                            liked = printLikes,
                            onGenre = { g -> view = LibView.LikedGenre(g.key, g.label) },
                            onPlayYourSound = {
                                val mix = printLikes.filter(Playback::playable).shuffled()
                                if (mix.isNotEmpty()) play(mix, mix.first())
                            },
                        )
                    }
                }
                item {
                    LibRow(R.drawable.ic_heart, stringResource(R.string.liked)) { view = LibView.Liked }
                }
                item {
                    LibRow(R.drawable.ic_download, stringResource(R.string.downloads)) { view = LibView.Downloaded }
                }
                items(LocalPlaylists.visible, key = { "own:" + it.id }) { p ->
                    LibRow(
                        R.drawable.ic_music,
                        p.title,
                        subtitle = playlistSubtitle(p),
                        playlist = p.asPlaylist(),
                        syncing = p.syncing,
                        locked = p.isPrivate,
                    ) {
                        view = LibView.PlaylistView(p.id, p.title)
                    }
                }
                items(Library.likedPlaylists.distinctBy { it.urn }, key = { "liked:" + it.urn }) { p ->
                    LibRow(
                        R.drawable.ic_music,
                        p.title,
                        subtitle = p.user?.username ?: stringResource(R.string.tracks_count, p.track_count),
                        playlist = p,
                    ) {
                        view = LibView.PlaylistView(p.urn, p.title, owned = false)
                    }
                }
                items(LikedArtists.artists.distinctBy { it.urn }, key = { "artist:" + it.urn }) { a ->
                    LibRow(
                        R.drawable.ic_user,
                        a.username,
                        subtitle = stringResource(R.string.artist_label),
                        artworkUrl = a.avatar_url,
                        circle = true,
                    ) {
                        view = LibView.ArtistTracks(a.urn, a.username, a.avatar_url)
                    }
                }
                item {
                    LibRow(R.drawable.ic_history, stringResource(R.string.history)) { view = LibView.HistoryView }
                }
                if (!offline && Prefs.userUrn != null) {
                    item {
                        LibRow(R.drawable.ic_stats, stringResource(R.string.stats_title), subtitle = stringResource(R.string.stats_sub)) { NavEvents.openStats() }
                    }
                }
                if (!offline) {
                    item {
                        LibRow(
                            R.drawable.ic_thumb_down,
                            stringResource(R.string.disliked),
                            subtitle = stringResource(R.string.disliked_sub),
                        ) { view = LibView.Disliked }
                    }
                }
            }
        }

        LibView.Liked -> LibTracks(
            title = stringResource(R.string.liked),
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            downloadAll = !offline,
            loader = { page, fresh -> Library.likedPage(page, fresh, offline) },
        )

        LibView.Downloaded -> LibTracks(
            title = stringResource(R.string.downloads),
            onBack = { view = LibView.Root },
            play = play,
            loader = { page, _ -> Library.downloadedPage(page) },
        )

        LibView.HistoryView -> LibTracks(
            title = stringResource(R.string.history),
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            loader = { page, fresh -> Library.historyPage(page, fresh, offline) },
            onClear = if (offline) null else { { Api.clearHistory() } },
        )

        is LibView.LikedGenre -> LibTracks(
            title = v.label,
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            downloadAll = !offline,
            loader = { page, fresh -> Library.genrePage(page, fresh, offline) },
            listKey = "genre:${v.key}",
            visible = { genresMatch(it, v.key) },
        )

        LibView.Disliked -> LibTracks(
            title = stringResource(R.string.disliked),
            onBack = { view = LibView.Root },
            play = play,
            loader = { page, fresh -> if (fresh) emptyList<Track>() to false else Dislikes.page(page) },
            emptyText = stringResource(R.string.disliked_empty),
            visible = { Dislikes.isDisliked(it.urn) },
            rowTrailing = { track ->
                val scope = rememberCoroutineScope()
                TextButton(onClick = { scope.launch { Dislikes.toggle(track) } }) {
                    Text(stringResource(R.string.undislike), style = MaterialTheme.typography.labelMedium)
                }
            },
        )

        is LibView.PlaylistView -> {
            val own = if (v.owned) LocalPlaylists.find(v.urn) else null
            LibTracks(
                title = own?.title ?: v.title,
                onBack = { view = LibView.Root },
                play = play,
                dimUndownloaded = offline,
                loader = { page, fresh -> Library.playlistPage(v.urn, page, fresh, offline) },
                downloadAll = !offline,
                onRename = if (own == null) null else { newTitle -> LocalPlaylists.rename(v.urn, newTitle) },
                onDelete = if (own == null) null else {
                    {
                        LocalPlaylists.delete(v.urn)
                        view = LibView.Root
                    }
                },
                privacy = own?.let { p -> p.isPrivate to { private: Boolean -> LocalPlaylists.setPrivate(v.urn, private) } },
                syncing = own?.syncing == true,
                liveTracks = own?.tracks,
                onRemoveTrack = if (own == null) null else { t -> LocalPlaylists.remove(v.urn, t.urn) },
                onReorder = if (own == null) { order -> PlaylistOrder.set(listOf(v.urn), order) } else { order -> LocalPlaylists.reorder(v.urn, order) },
                likedPlaylistUrn = if (v.owned) null else v.urn,
                listKey = v.urn,
                coverUrn = v.urn,
            )
        }

        is LibView.ArtistTracks -> LibTracks(
            title = v.title,
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            downloadAll = !offline,
            loader = { page, _ -> Library.artistPage(v.urn, page) },
            likedArtist = Artist(urn = v.urn, username = v.title, avatar_url = v.avatar),
            listKey = v.urn,
        )
    }

    if (showCreate) {
        CreatePlaylistDialog(onDismiss = { showCreate = false }) { title, private ->
            LocalPlaylists.create(title, private)
        }
    }
}

@Composable
private fun playlistSubtitle(p: LocalPlaylist): String {
    val count = stringResource(R.string.tracks_count, p.count)
    return if (p.syncing) count + " · " + stringResource(R.string.playlist_syncing) else count
}

@Composable
private fun LibRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    artworkUrl: String? = null,
    playlist: Playlist? = null,
    circle: Boolean = false,
    syncing: Boolean = false,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playlist != null) {
            PlaylistArtwork(playlist, 48.dp, 8.dp)
        } else {
        Box(
            Modifier
                .size(48.dp)
                .clip(if (circle) CircleShape else RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUrl != null) {
                AsyncImage(
                    model = Api.artworkUrl(artworkUrl, "t120x120"),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painterResource(icon),
                    null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painterResource(R.drawable.ic_lock),
                        stringResource(R.string.playlist_private),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = if (syncing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (syncing) {
            Icon(
                painterResource(R.drawable.ic_refresh),
                stringResource(R.string.playlist_syncing),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun LibTracks(
    title: String,
    onBack: () -> Unit,
    play: (List<Track>, Track) -> Unit,
    loader: suspend (Int, Boolean) -> Pair<List<Track>, Boolean>,
    downloadAll: Boolean = false,
    dimUndownloaded: Boolean = false,
    onRename: (suspend (String) -> Unit)? = null,
    onDelete: (suspend () -> Unit)? = null,
    likedPlaylistUrn: String? = null,
    likedArtist: Artist? = null,
    emptyText: String? = null,
    listKey: String? = null,
    visible: (Track) -> Boolean = { true },
    rowTrailing: (@Composable (Track) -> Unit)? = null,
    onClear: (suspend () -> Unit)? = null,
    privacy: Pair<Boolean, (Boolean) -> Unit>? = null,
    syncing: Boolean = false,
    liveTracks: List<Track>? = null,
    onRemoveTrack: ((Track) -> Unit)? = null,
    coverUrn: String? = null,
    onReorder: ((List<String>) -> Unit)? = null,
) {
    var reordering by remember { mutableStateOf(false) }
    val pickCover = coverUrn?.let { rememberCoverPicker(it) }
    var items by remember { mutableStateOf<List<Track>>(emptyList()) }
    var confirmClear by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var paginating by remember { mutableStateOf(false) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun load(p: Int) {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            try {
                val (batch, more) = loader(p, false)
                items = if (p == 0) batch else (items + batch).distinctBy { it.urn }
                page = p
                hasMore = more
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }

            if (p == 0 && !Prefs.offline) {
                runCatching { loader(0, true) }.onSuccess { (batch, more) ->
                    if (page == 0 && batch.isNotEmpty()) {
                        items = batch
                        hasMore = more
                        error = null
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { load(0) }

    LaunchedEffect(liveTracks) {
        val live = liveTracks ?: return@LaunchedEffect
        if (live.isNotEmpty() || items.isNotEmpty()) items = live
    }

    LaunchedEffect(hasMore, page, loading) {
        if (!hasMore || loading || items.isEmpty() || items.size >= 1000) return@LaunchedEffect
        val next = page + 1
        val (batch, more) = runCatching { loader(next, false) }.getOrNull() ?: return@LaunchedEffect
        items = (items + batch).distinctBy { it.urn }
        page = next
        hasMore = more
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), iconLabel(R.drawable.ic_arrow_back))
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onClear != null && items.isNotEmpty()) {
                IconButton(onClick = { confirmClear = true }) {
                    Icon(
                        painterResource(R.drawable.ic_trash),
                        stringResource(R.string.history_clear),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (confirmClear && onClear != null) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    text = { Text(stringResource(R.string.history_clear_q)) },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmClear = false
                            scope.launch {
                                val ok = runCatching { onClear() }.isSuccess
                                if (ok) {
                                    items = emptyList()
                                    hasMore = false
                                } else {
                                    android.widget.Toast.makeText(context, context.getString(R.string.error_network), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
                    },
                )
            }
            likedPlaylistUrn?.let { urn ->
                val plLiked = LikedPlaylists.isLiked(urn)
                IconButton(onClick = { scope.launch { LikedPlaylists.toggle(Playlist(urn = urn, title = title)) } }) {
                    Icon(
                        painterResource(if (plLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        iconLabel(if (plLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        tint = if (plLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            likedArtist?.let { a ->
                val arLiked = LikedArtists.isLiked(a.urn)
                IconButton(onClick = { LikedArtists.toggle(a) }) {
                    Icon(
                        painterResource(if (arLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        iconLabel(if (arLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        tint = if (arLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onRename != null || onDelete != null || privacy != null || pickCover != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), iconLabel(R.drawable.ic_more_vert), modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (pickCover != null && coverUrn != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.cover_change)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_music), null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    menuOpen = false
                                    pickCover()
                                },
                            )
                            if (PlaylistCovers.has(coverUrn)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cover_reset)) },
                                    leadingIcon = { Icon(painterResource(R.drawable.ic_refresh), null, modifier = Modifier.size(18.dp)) },
                                    onClick = {
                                        menuOpen = false
                                        PlaylistCovers.clear(coverUrn)
                                    },
                                )
                            }
                        }
                        if (onReorder != null && (liveTracks ?: items).size > 1) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reorder_title)) },
                                leadingIcon = { Icon(painterResource(R.drawable.ic_drag), null, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    menuOpen = false
                                    reordering = true
                                },
                            )
                        }
                        if (onRename != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename)) },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_edit), null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    showRename = true
                                },
                            )
                        }
                        if (privacy != null) {
                            val (isPrivate, setPrivate) = privacy
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(if (isPrivate) R.string.playlist_make_public else R.string.playlist_make_private))
                                },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_lock), null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    menuOpen = false
                                    setPrivate(!isPrivate)
                                },
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_trash),
                                        null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp),
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    showDelete = true
                                },
                            )
                        }
                    }
                }
            }
            val visibleItems = items.filter(visible)
            val allDownloaded = visibleItems.isNotEmpty() && !hasMore &&
                visibleItems.all { Downloads.isDownloaded(it.urn) }
            val key = listKey ?: title
            val downloading = paginating || key in Downloads.activeLists
            if (downloadAll) {
                if (downloading) {
                    val cancelLabel = stringResource(R.string.cd_cancel_download)
                    IconButton(
                        onClick = {
                            downloadJob?.cancel()
                            Downloads.cancelList(context, key)
                        },
                        modifier = Modifier.semantics { contentDescription = cancelLabel },
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                } else if (allDownloaded) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(12.dp).size(20.dp),
                    )
                } else {
                    IconButton(onClick = {
                        downloadJob = scope.launch {
                            paginating = true
                            try {
                                val all = items.toMutableList()
                                var p = page
                                var more = hasMore
                                while (more) {
                                    p++
                                    val (batch, hasNext) = runCatching { loader(p, false) }.getOrNull() ?: break
                                    all += batch
                                    more = hasNext
                                }
                                val full = all.distinctBy { it.urn }
                                items = full
                                page = p
                                hasMore = false
                                Downloads.enqueue(context, full.filter(visible), listKey = key)
                            } finally {
                                paginating = false
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_download), iconLabel(R.drawable.ic_download), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        if (reordering && onReorder != null) {
            ReorderDialog(liveTracks ?: items, onDismiss = { reordering = false }) { order ->
                reordering = false
                onReorder(order)
                if (liveTracks == null) {
                    val byUrn = items.associateBy { it.urn }
                    items = order.mapNotNull { byUrn[it] } + items.filter { it.urn !in order }
                }
            }
        }

        if (showRename && onRename != null) {
            var newTitle by remember { mutableStateOf(title) }
            Dialog(onDismissRequest = { showRename = false }) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.rename),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(value = newTitle, onValueChange = { newTitle = it }, singleLine = true)
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showRename = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                enabled = newTitle.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        val ok = runCatching { onRename(newTitle.trim()) }.isSuccess
                                        showRename = false
                                        if (!ok) {
                                            android.widget.Toast.makeText(
                                                context,
                                                context.getString(R.string.error_network),
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    }
                                },
                            ) { Text(stringResource(R.string.save)) }
                        }
                    }
                }
            }
        }

        if (showDelete && onDelete != null) {
            Dialog(onDismissRequest = { showDelete = false }) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(24.dp)) {
                        Text(
                            stringResource(R.string.delete_playlist_q, title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showDelete = false }) {
                                Text(stringResource(R.string.cancel))
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                onClick = {
                                    scope.launch {
                                        showDelete = false
                                        onDelete()
                                    }
                                },
                            ) { Text(stringResource(R.string.delete)) }
                        }
                    }
                }
            }
        }

        if (syncing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_refresh),
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.playlist_syncing_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        val shown = items.filter(visible)
        val trailing: (@Composable (Track) -> Unit)? = when {
            rowTrailing != null -> rowTrailing
            onRemoveTrack != null -> @Composable { track: Track -> PlaylistTrackMenu(track, onRemove = { onRemoveTrack(track) }) }
            else -> null
        }
        TrackList(
            tracks = shown,
            loading = loading,
            error = error,
            emptyText = emptyText ?: stringResource(R.string.search_empty),
            canLoadMore = hasMore,
            onLoadMore = { load(page + 1) },
            onPlay = { play(shown, it) },
            dimUndownloaded = dimUndownloaded,
            rowTrailing = trailing,
            onRemove = onRemoveTrack,
        )
    }
}

@Composable
private fun PlaylistTrackMenu(track: Track, onRemove: () -> Unit) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val liked = Likes.isLiked(track.urn)
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
            Icon(painterResource(R.drawable.ic_more_vert), iconLabel(R.drawable.ic_more_vert), modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (liked) R.string.notif_unlike else R.string.notif_like)) },
                leadingIcon = {
                    Icon(painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart), null, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    open = false
                    scope.launch { Likes.toggle(track) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.playlist_remove_track), color = MaterialTheme.colorScheme.error) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_trash), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}
