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
import androidx.compose.ui.layout.ContentScale
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
    data class PlaylistView(val urn: String, val title: String) : LibView
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
    var username by remember { mutableStateOf<String?>(null) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var showSettings by remember { mutableStateOf(false) }
    var showCreate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { Api.authStatus() }.onSuccess { username = it.username }
    }

    LaunchedEffect(view, PlaylistEvents.version) {
        if (view == LibView.Root) {
            runCatching { Api.myPlaylists(fresh = !offline) }
                .recoverCatching { if (!offline) Api.myPlaylists(fresh = false) else throw it }
                .onSuccess { playlists = it.collection }
        }
    }

    if (showSettings) {
        SettingsScreen(
            username = username,
            onBack = { showSettings = false },
            onLogout = {
                showSettings = false
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
                    Modifier
                        .size(36.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_user),
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    username ?: "SoundCloud",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
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
                val res = Api.likedTracks(page)
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
                val batch = res.collection.map { it.toTrack() }
                batch to (page * 50 + res.collection.size < res.total)
            },
        )

        is LibView.PlaylistView -> LibTracks(
            title = v.title,
            onBack = { view = LibView.Root },
            play = play,
            dimUndownloaded = offline,
            loader = { page ->
                val res = Api.playlistTracks(v.urn, page)
                res.collection to res.has_more
            },
            downloadAll = !offline,
            onRename = if (offline) null else { newTitle ->
                Api.renamePlaylist(v.urn, newTitle)
                playlists = playlists.map { if (it.urn == v.urn) it.copy(title = newTitle) else it }
                view = LibView.PlaylistView(v.urn, newTitle)
            },
            onDelete = if (offline) null else {
                {
                    runCatching { Api.deletePlaylist(v.urn) }
                    playlists = playlists.filterNot { it.urn == v.urn }
                    view = LibView.Root
                }
            },
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
                                scope.launch {
                                    val result = runCatching { Api.createPlaylist(title) }
                                    showCreate = false
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
                .clip(RoundedCornerShape(8.dp))
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
) {
    var items by remember { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            if (downloadAll) {
                if (downloading) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                } else {
                    IconButton(onClick = {
                        scope.launch {
                            downloading = true
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
                            Downloads.downloadBatch(full)
                            downloading = false
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
    onBack: () -> Unit,
    onLogout: () -> Unit,
) {
    val activity = LocalContext.current as? android.app.Activity
    val ctx = LocalContext.current
    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), null)
            }
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            if (username != null) {
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_user),
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(username, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
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
                "en" to "English",
                "ru" to "Русский",
                "be" to "Беларуская",
                "zh" to "中文",
                "de" to "Deutsch",
                "fr" to "Français",
                "es" to "Español",
                "tr" to "Türkçe",
                "ko" to "한국어",
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.immersive_artwork), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.immersive_artwork_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = Prefs.immersiveArtwork, onCheckedChange = { Prefs.changeImmersiveArtwork(it) })
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.offline_mode), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                Switch(checked = Prefs.offline, onCheckedChange = { Prefs.setOfflineMode(it) })
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(stringResource(R.string.logout))
            }

            Spacer(Modifier.height(16.dp))
            val version = remember {
                runCatching {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
                }.getOrNull() ?: "—"
            }
            Text(
                stringResource(R.string.version, version),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
