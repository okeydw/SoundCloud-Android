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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
            .background(androidx.compose.ui.graphics.Color(0xFFFF5500))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "★ " + stringResource(R.string.star_badge),
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun LibraryScreen(
    play: (List<Track>, Track) -> Unit,
    onSessionExpired: () -> Unit,
    onOpenArtist: (String) -> Unit = {},
    offline: Boolean = false,
) {
    val context = LocalContext.current
    var view by remember { mutableStateOf<LibView>(LibView.Root) }
    var username by remember { mutableStateOf(Prefs.username) }
    var avatar by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var likedPls by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Api.authStatus() }.onSuccess {
            username = it.username
            Prefs.saveUsername(it.username)
        }
        if (!offline) {
            runCatching { Api.subscription() }.onSuccess { Prefs.saveStar(it.premium) }
            runCatching { Api.me() }.onSuccess {
                if (it.username.isNotEmpty()) {
                    username = it.username
                    Prefs.saveUsername(it.username)
                }
                avatar = it.avatar_url
            }
        }
    }

    LaunchedEffect(view, PlaylistEvents.version) {
        if (view != LibView.Root) return@LaunchedEffect
        runCatching { Api.myPlaylists(fresh = false) }.onSuccess { playlists = it.collection }
        runCatching { Api.likedPlaylists(fresh = false) }.onSuccess { likedPls = it.collection }
        if (!offline) {
            runCatching { Api.myPlaylists(fresh = true) }.onSuccess { playlists = it.collection }
            runCatching { Api.likedPlaylists(fresh = true) }.onSuccess {
                likedPls = it.collection
                LikedPlaylists.seed(force = true)
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            username = username,
            avatar = avatar,
            onBack = { showSettings = false },
            onLogout = {
                showSettings = false
                Prefs.saveUsername(null)
                Api.storeSession(context, null)
                onSessionExpired()
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
                            username ?: "SoundCloud",
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
                    Icon(painterResource(R.drawable.ic_plus), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(painterResource(R.drawable.ic_settings), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    LibRow(R.drawable.ic_heart, stringResource(R.string.liked)) { view = LibView.Liked }
                }
                item {
                    LibRow(R.drawable.ic_download, stringResource(R.string.downloads)) { view = LibView.Downloaded }
                }
                items(playlists) { p ->
                    LibRow(
                        R.drawable.ic_music,
                        p.title,
                        subtitle = stringResource(R.string.tracks_count, p.track_count),
                        artworkUrl = p.artwork_url,
                    ) {
                        view = LibView.PlaylistView(p.urn, p.title)
                    }
                }
                items(likedPls) { p ->
                    LibRow(
                        R.drawable.ic_music,
                        p.title,
                        subtitle = p.user?.username ?: stringResource(R.string.tracks_count, p.track_count),
                        artworkUrl = p.artwork_url,
                    ) {
                        view = LibView.PlaylistView(p.urn, p.title, owned = false)
                    }
                }
                items(LikedArtists.artists) { a ->
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
            }
        }

        LibView.Liked -> LibTracks(
            title = stringResource(R.string.liked),
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            downloadAll = !offline,
            loader = { page ->
                val res = Api.likedTracks(page, fresh = page == 0 && !offline)
                Likes.seed(res.collection)
                res.collection to res.has_more
            },
        )

        LibView.Downloaded -> LibTracks(
            title = stringResource(R.string.downloads),
            onBack = { view = LibView.Root },
            play = play,
            loader = { page -> if (page == 0) Downloads.tracks() to false else emptyList<Track>() to false },
        )

        LibView.HistoryView -> LibTracks(
            title = stringResource(R.string.history),
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            loader = { page ->
                val res = Api.history(offset = page * 50, limit = 50)
                val batch = res.collection.map { it.toTrack() }.distinctBy { it.urn }
                batch to (page * 50 + res.collection.size < res.total)
            },
        )

        is LibView.PlaylistView -> LibTracks(
            title = v.title,
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            loader = { page ->
                val res = Api.playlistTracks(v.urn, page, fresh = page == 0 && !offline)
                res.collection to res.has_more
            },
            downloadAll = !offline,
            onRename = if (offline || !v.owned) null else { newTitle ->
                Api.renamePlaylist(v.urn, newTitle)
                playlists = playlists.map { if (it.urn == v.urn) it.copy(title = newTitle) else it }
                view = LibView.PlaylistView(v.urn, newTitle)
            },
            onDelete = if (offline || !v.owned) null else {
                {
                    runCatching { Api.deletePlaylist(v.urn) }
                    playlists = playlists.filterNot { it.urn == v.urn }
                    view = LibView.Root
                }
            },
            likedPlaylistUrn = if (v.owned) null else v.urn,
        )

        is LibView.ArtistTracks -> LibTracks(
            title = v.title,
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            downloadAll = !offline,
            loader = { page ->
                val res = Api.userTracks(v.urn, page)
                res.collection to res.has_more
            },
            likedArtist = Artist(urn = v.urn, username = v.title, avatar_url = v.avatar),
        )
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showCreate = false }) {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp)) {
                    Text(
                        stringResource(R.string.new_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                    Spacer(Modifier.height(16.dp))
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { showCreate = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            enabled = name.isNotBlank(),
                            onClick = {
                                val title = name.trim()
                                val beforeUrns = playlists.map { it.urn }.toSet()
                                showCreate = false
                                scope.launch {
                                    val result = runCatching { Api.createPlaylist(title) }
                                    val err = result.exceptionOrNull()
                                    val gatewayTimeout = (err as? ApiHttpException)?.code in setOf(502, 503, 504)
                                    if (result.isSuccess || gatewayTimeout) {
                                        var appeared = false
                                        var tries = 0
                                        while (tries < 15) {
                                            kotlinx.coroutines.delay(1000)
                                            val fresh = runCatching { Api.myPlaylists(fresh = true) }.getOrNull()
                                            if (fresh != null) {
                                                playlists = fresh.collection
                                                if (fresh.collection.any { it.urn !in beforeUrns && it.title == title } ||
                                                    fresh.collection.size > beforeUrns.size
                                                ) {
                                                    appeared = true
                                                    break
                                                }
                                            }
                                            tries++
                                        }
                                        android.widget.Toast.makeText(
                                            context,
                                            if (appeared || result.isSuccess) context.getString(R.string.playlist_created)
                                            else context.getString(R.string.playlist_creating_slow),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        android.widget.Toast.makeText(
                                            context,
                                            err?.message ?: context.getString(R.string.error_network),
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.create)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibRow(
    icon: Int,
    title: String,
    subtitle: String? = null,
    artworkUrl: String? = null,
    circle: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun LibTracks(
    title: String,
    onBack: () -> Unit,
    play: (List<Track>, Track) -> Unit,
    loader: suspend (Int) -> Pair<List<Track>, Boolean>,
    downloadAll: Boolean = false,
    dimUndownloaded: Boolean = false,
    onRename: (suspend (String) -> Unit)? = null,
    onDelete: (suspend () -> Unit)? = null,
    likedPlaylistUrn: String? = null,
    likedArtist: Artist? = null,
) {
    var items by remember { mutableStateOf<List<Track>>(emptyList()) }
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
                val (batch, more) = loader(p)
                items = if (p == 0) batch else (items + batch).distinctBy { it.urn }
                page = p
                hasMore = more
            } catch (e: Exception) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            likedPlaylistUrn?.let { urn ->
                val plLiked = LikedPlaylists.isLiked(urn)
                IconButton(onClick = { scope.launch { LikedPlaylists.toggle(Playlist(urn = urn, title = title)) } }) {
                    Icon(
                        painterResource(if (plLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                        null,
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
                        null,
                        tint = if (arLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            if (onRename != null || onDelete != null) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(painterResource(R.drawable.ic_more_vert), null, modifier = Modifier.size(20.dp))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            val allDownloaded = items.isNotEmpty() && !hasMore &&
                items.all { Downloads.isDownloaded(it.urn) }
            val downloading = paginating || items.any { it.urn in Downloads.inProgress }
            if (downloadAll) {
                if (downloading) {
                    IconButton(onClick = {
                        downloadJob?.cancel()
                        Downloads.cancelAll(context)
                    }) {
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
                                    val (batch, hasNext) = runCatching { loader(p) }.getOrNull() ?: break
                                    all += batch
                                    more = hasNext
                                }
                                val full = all.distinctBy { it.urn }
                                items = full
                                page = p
                                hasMore = false
                                Downloads.enqueue(context, full)
                            } finally {
                                paginating = false
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_download), null, modifier = Modifier.size(20.dp))
                    }
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
                                        runCatching { onRename(newTitle.trim()) }
                                        showRename = false
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

        TrackList(
            tracks = items,
            loading = loading,
            error = error,
            emptyText = stringResource(R.string.search_empty),
            canLoadMore = hasMore,
            onLoadMore = { load(page + 1) },
            onPlay = { play(items, it) },
            dimUndownloaded = dimUndownloaded,
        )
    }
}

@Composable
fun SettingsScreen(
    username: String?,
    avatar: String? = null,
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val activity = LocalActivity.current
    val ctx = LocalContext.current
    var section by remember { mutableStateOf(-1) }
    val scope = rememberCoroutineScope()
    BackHandler { if (section >= 0) section = -1 else onBack() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { if (section >= 0) section = -1 else onBack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                stringResource(
                    when (section) {
                        0 -> R.string.tab_account
                        1 -> R.string.tab_visual
                        2 -> R.string.tab_storage
                        3 -> R.string.tab_star
                        4 -> R.string.tab_about
                        5 -> R.string.tab_logs
                        else -> R.string.settings
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        if (section == -1) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                SettingsRow(R.drawable.ic_user, stringResource(R.string.tab_account), username ?: stringResource(R.string.account_signed_in)) { section = 0 }
                SettingsRow(R.drawable.ic_music, stringResource(R.string.tab_visual), stringResource(R.string.settings_visual_sub)) { section = 1 }
                SettingsRow(R.drawable.ic_download, stringResource(R.string.tab_storage), stringResource(R.string.settings_storage_sub)) { section = 2 }
                SettingsRow(R.drawable.ic_share, stringResource(R.string.tab_star), stringResource(R.string.settings_links_sub)) { section = 3 }
                SettingsRow(R.drawable.ic_settings, stringResource(R.string.tab_about), stringResource(R.string.settings_system_sub)) { section = 4 }
                SettingsRow(R.drawable.ic_history, stringResource(R.string.tab_logs), stringResource(R.string.settings_logs_sub)) { section = 5 }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            when (section) {
                3 -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        android.net.Uri.parse("package:${ctx.packageName}"),
                                    )
                                } else {
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${ctx.packageName}"),
                                    )
                                }
                                runCatching { ctx.startActivity(intent) }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_wave), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.open_links_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.open_links_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_chevron_down),
                            null,
                            modifier = Modifier.size(18.dp).rotate(-90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val links = listOf(
                        Triple(R.drawable.ic_user, "Discord", "https://discord.gg/Au3ebtfYu3"),
                        Triple(R.drawable.ic_music, "Android · GitHub", "https://github.com/okeydw/SoundCloud-Android"),
                        Triple(R.drawable.ic_music, "Desktop · GitHub", "https://github.com/zxcloli666/SoundCloud-Desktop"),
                        Triple(R.drawable.ic_wave, "soundcloud-desktop.fun", "https://soundcloud-desktop.fun/"),
                    )
                    links.forEach { (icon, label, url) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url),
                                            ),
                                        )
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(icon), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    url.removePrefix("https://").removeSuffix("/"),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                painterResource(R.drawable.ic_share),
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                0 -> {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
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
                        Column {
                            Text(username ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.account_signed_in),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Star", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(if (Prefs.star) R.string.star_active else R.string.star_inactive),
                            color = if (Prefs.star) androidx.compose.ui.graphics.Color(0xFFFF5500) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(stringResource(R.string.logout)) }
                }

                1 -> {
                    Text(stringResource(R.string.theme), fontWeight = FontWeight.Medium)
                    listOf(
                        "system" to stringResource(R.string.theme_system),
                        "dark" to stringResource(R.string.theme_dark),
                        "light" to stringResource(R.string.theme_light),
                    ).forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Prefs.setThemeMode(value) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = Prefs.theme == value, onClick = { Prefs.setThemeMode(value) })
                            Text(label)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    val langLabels = mapOf(
                        "system" to stringResource(R.string.theme_system),
                        "en" to "English", "ru" to "Русский", "be" to "Беларуская",
                        "zh" to "中文", "de" to "Deutsch", "fr" to "Français",
                        "es" to "Español", "tr" to "Türkçe", "ko" to "한국어",
                    )
                    var langOpen by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.language), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Box {
                            TextButton(onClick = { langOpen = true }) {
                                Text(langLabels[Prefs.language] ?: Prefs.language)
                                Icon(painterResource(R.drawable.ic_chevron_down), null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = langOpen, onDismissRequest = { langOpen = false }) {
                                LocaleHelper.languages.forEach { code ->
                                    DropdownMenuItem(
                                        text = { Text(langLabels[code] ?: code) },
                                        onClick = {
                                            langOpen = false
                                            if (Prefs.language != code) {
                                                Prefs.changeLanguage(code)
                                                activity?.recreate()
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.immersive_artwork),
                        stringResource(R.string.immersive_artwork_hint),
                        Prefs.immersiveArtwork,
                    ) { Prefs.changeImmersiveArtwork(it) }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.crossfade),
                        stringResource(R.string.crossfade_hint),
                        Prefs.crossfade,
                    ) { Prefs.changeCrossfade(it) }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.stream_tags),
                        stringResource(R.string.stream_tags_hint),
                        Prefs.streamTags,
                    ) { Prefs.changeStreamTags(it) }

                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.accent_color), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                        AccentPalette.colors.forEach { c ->
                            val selected = Prefs.accent == c
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(androidx.compose.ui.graphics.Color(c))
                                    .clickable { Prefs.changeAccent(c) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        null,
                                        tint = androidx.compose.ui.graphics.Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                2 -> {
                    val downloadedSet = Downloads.downloaded
                    val tracks = Downloads.tracks()
                    val usedTracks = remember(downloadedSet) { tracks.sumOf { Downloads.fileFor(it.urn).length() } }
                    val stat = remember { runCatching { android.os.StatFs(ctx.filesDir.path) }.getOrNull() }
                    val total = stat?.totalBytes ?: 0L
                    val free = stat?.availableBytes ?: 0L
                    val usedOther = (total - free - usedTracks).coerceAtLeast(0L)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.storage_downloaded), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${tracks.size}  ·  ${fmtBytes(usedTracks)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))
                    val barTotal = (usedTracks + usedOther + free).toFloat().coerceAtLeast(1f)
                    val trackWeight = if (usedTracks > 0) maxOf(usedTracks.toFloat(), barTotal * 0.03f) else 0f
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
                        if (trackWeight > 0f) Box(Modifier.weight(trackWeight).height(12.dp).background(MaterialTheme.colorScheme.primary))
                        if (usedOther > 0) Box(Modifier.weight(usedOther.toFloat()).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        if (free > 0) Box(Modifier.weight(free.toFloat()).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    Spacer(Modifier.height(10.dp))
                    StorageLegend(MaterialTheme.colorScheme.primary, stringResource(R.string.storage_used_tracks), fmtBytes(usedTracks))
                    StorageLegend(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.storage_other), fmtBytes(usedOther))
                    StorageLegend(MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.storage_free), fmtBytes(free))

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.offline_mode), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Switch(checked = Prefs.offline, onCheckedChange = { Prefs.setOfflineMode(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.play_blocked),
                        stringResource(R.string.play_blocked_hint),
                        Prefs.playBlocked,
                    ) { Prefs.changePlayBlocked(it) }

                    if (tracks.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { scope.launch { tracks.forEach { runCatching { Downloads.remove(it.urn) } } } },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(stringResource(R.string.clear_downloads)) }
                    }
                }

                5 -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Logs.lines.size}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, Logs.dump())
                            }
                            ctx.startActivity(android.content.Intent.createChooser(send, null))
                        }) {
                            Icon(painterResource(R.drawable.ic_share), null, modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { Logs.clear() }) {
                            Icon(painterResource(R.drawable.ic_trash), null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (Logs.lines.isEmpty()) {
                        Text(
                            stringResource(R.string.logs_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        Logs.lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }

                else -> {
                    val pInfo = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
                    val vName = pInfo?.versionName ?: "—"
                    @Suppress("DEPRECATION")
                    val vCode = pInfo?.versionCode ?: 0
                    InfoRow("App", ctx.packageName)
                    InfoRow(stringResource(R.string.version, "").trim(), "$vName ($vCode)")
                    InfoRow(stringResource(R.string.about_device), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    InfoRow("Android", "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    InfoRow("ABI", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "-")
                    InfoRow("API", Endpoints.apiBase.removePrefix("https://"))
                    InfoRow("Stream", Endpoints.streamBase.removePrefix("https://"))
                    Spacer(Modifier.height(12.dp))
                    SettingSwitch(
                        "Stream debug",
                        "Показывать ответ стрим-сервера при открытии трека",
                        Prefs.streamDebug,
                    ) { Prefs.changeStreamDebug(it) }
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = {
                            val json = """
                                {
                                  "app": "${ctx.packageName}",
                                  "versionName": "$vName",
                                  "versionCode": $vCode,
                                  "device": "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
                                  "android": "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})",
                                  "abi": "${android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: ""}",
                                  "language": "${Prefs.language}",
                                  "offline": ${Prefs.offline},
                                  "downloads": ${Downloads.downloaded.size}
                                }
                            """.trimIndent()
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(android.content.Intent.EXTRA_TEXT, json)
                            }
                            ctx.startActivity(android.content.Intent.createChooser(send, null))
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.about_export)) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            null,
            modifier = Modifier.size(20.dp).rotate(-90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StorageLegend(color: androidx.compose.ui.graphics.Color, label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

private fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}
