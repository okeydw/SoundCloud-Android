package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

enum class Tab { Search, Wave, Me }

val PlaylistRefSaver = Saver<Playlist?, ArrayList<String>>(
    save = { p -> p?.let { arrayListOf(it.urn, it.title) } },
    restore = { saved -> if (saved.size >= 2) Playlist(urn = saved[0], title = saved[1]) else null },
)

@Composable
fun MainScreen(controller: MediaController?, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.Search) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var openArtist by rememberSaveable { mutableStateOf<String?>(null) }
    var openPlaylist by rememberSaveable(stateSaver = PlaylistRefSaver) { mutableStateOf<Playlist?>(null) }
    var routes by remember { mutableStateOf<List<String>>(emptyList()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveController by rememberUpdatedState(controller)

    LaunchedEffect(Unit) { Startup.run(context) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        NowPlaying.sync(controller)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                NowPlaying.sync(controller)
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller, lifecycleOwner) {
        val c = controller ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { PositionTicker.run(c) }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { Health.watch(context) }
    }

    LaunchedEffect(Unit) { Updates.check(context) }

    LaunchedEffect(NowPlaying.openPlayerRequest) {
        if (NowPlaying.openPlayerRequest) {
            showPlayer = true
            NowPlaying.openPlayerRequest = false
        }
    }

    LaunchedEffect(NavEvents.route) {
        val r = NavEvents.route ?: return@LaunchedEffect
        NavEvents.consumeRoute()
        showPlayer = false
        openArtist = null
        openPlaylist = null
        routes = routes.filterNot { it == r } + r
    }

    LaunchedEffect(NavEvents.userUrn) {
        val u = NavEvents.userUrn ?: return@LaunchedEffect
        NavEvents.consumeUser()
        showPlayer = false
        openPlaylist = null
        openArtist = u
    }

    LaunchedEffect(NavEvents.playlistUrn) {
        NavEvents.playlistUrn?.let { urn ->
            openArtist = null
            openPlaylist = Playlist(urn = urn, title = NavEvents.playlistTitle ?: "")
            NavEvents.consume()
        }
    }

    LaunchedEffect(Prefs.offline, Health.serverDown) {
        if (Prefs.offline || Health.serverDown) return@LaunchedEffect
        WaveState.load(false)
        Tiles.load(0)
    }

    LaunchedEffect(tab) {
        WaveState.error = null
    }

    fun play(list: List<Track>, track: Track) = Playback.play(controller, list, track)

    LaunchedEffect(NavEvents.pendingUrl) {
        val url = NavEvents.pendingUrl ?: return@LaunchedEffect
        NavEvents.consumeUrl()
        scope.launch {
            when (val target = Links.open(url)) {
                is LinkTarget.PlayTrack -> {
                    openArtist = null
                    openPlaylist = null
                    if (Playback.playSingle({ liveController }, target.track)) {
                        showPlayer = true
                        Logs.add("link", "playing ${target.track.title.take(30)}")
                    } else {
                        Logs.add("link", "player not ready")
                        android.widget.Toast.makeText(context, context.getString(R.string.link_failed), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                is LinkTarget.OpenUser -> {
                    openPlaylist = null
                    openArtist = target.urn
                }
                is LinkTarget.OpenPlaylist -> {
                    openArtist = null
                    openPlaylist = target.playlist
                }
                is LinkTarget.Failed -> android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.link_failed),
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalMediaController provides controller) {
    Scaffold(
        containerColor = if (Prefs.backgroundImage != null || Prefs.bgColor1 != 0) Color.Transparent else MaterialTheme.colorScheme.background,
        topBar = {
            if (Prefs.headerAlpha < 1f || Prefs.backgroundImage != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = Prefs.headerAlpha)),
                )
            }
        },
        bottomBar = {
            Column {
                androidx.compose.animation.AnimatedVisibility(
                    visible = NowPlaying.title.isNotEmpty(),
                    enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(320)) { it } +
                        androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)),
                    exit = androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(260)) { it } +
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)),
                ) {
                    PlayerBar(controller, onExpand = { showPlayer = true })
                }
                if (!Prefs.offline) {
                    NavigationBar(containerColor = NavigationBarDefaults.containerColor.copy(alpha = Prefs.footerAlpha)) {
                        listOf(
                            Triple(Tab.Search, R.drawable.ic_search, R.string.tab_search),
                            Triple(Tab.Wave, R.drawable.ic_wave, R.string.tab_wave),
                            Triple(Tab.Me, R.drawable.ic_user, R.string.tab_me),
                        ).forEach { (t, icon, label) ->
                            NavigationBarItem(
                                selected = tab == t,
                                onClick = {
                                    tab = t
                                    openArtist = null
                                    openPlaylist = null
                                    routes = emptyList()
                                },
                                icon = { Icon(painterResource(icon), null) },
                                label = { Text(stringResource(label)) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if ((Health.noInternet || Health.serverDown) && !Prefs.offline) {
                Banner(
                    icon = R.drawable.ic_no_wifi,
                    text = stringResource(if (Health.noInternet) R.string.no_internet else R.string.server_down),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Updates.available?.let { (tag, url) ->
                var prompt by remember { mutableStateOf(false) }
                Banner(
                    icon = R.drawable.ic_download,
                    text = updateBannerText(tag),
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    chevron = true,
                    onClick = {
                        when (Updater.state) {
                            UpdateState.Ready, UpdateState.NeedsPermission -> Updater.install(context)
                            else -> prompt = true
                        }
                    },
                )
                if (prompt) UpdatePrompt(tag, url, onDismiss = { prompt = false })
            }
            val playlist = openPlaylist
            val artist = openArtist
            val route = routes.lastOrNull()
            val popRoute = { routes = routes.dropLast(1) }
            when {
                playlist != null -> PlaylistScreen(
                    playlist = playlist,
                    onBack = { openPlaylist = null },
                    onPlay = ::play,
                )
                artist != null -> ArtistScreen(
                    urn = artist,
                    onBack = { openArtist = null },
                    onPlay = ::play,
                    onOpenPlaylist = { openPlaylist = it },
                    offline = Prefs.offline,
                )
                route == NavEvents.STATS -> StatsScreen(onBack = popRoute, onPlay = ::play)
                route != null && route.startsWith(NavEvents.CATALOG) -> key(route) {
                    CatalogArtistScreen(route.removePrefix(NavEvents.CATALOG), onBack = popRoute, onPlay = ::play)
                }
                route != null && route.startsWith(NavEvents.ALBUM) -> key(route) {
                    AlbumScreen(route.removePrefix(NavEvents.ALBUM), onBack = popRoute, onPlay = ::play)
                }
                Prefs.offline -> LibraryScreen(
                    play = ::play,
                    onSignOut = onSignOut,
                    onOpenArtist = { openArtist = it },
                    offline = true,
                )
                tab == Tab.Search -> SearchTab(
                    onPlay = ::play,
                    onPlayTile = { list, t -> Playback.play(controller, list, t, autoContinue = true) },
                    onOpenArtist = { openArtist = it },
                    onOpenPlaylist = { openPlaylist = it },
                )
                tab == Tab.Wave -> WaveFeed(
                    controller = controller,
                    tracks = WaveState.tracks,
                    loading = WaveState.loading,
                    error = WaveState.error,
                    canLoadMore = !Prefs.offline && WaveState.tracks.isNotEmpty(),
                    onLoadMore = { WaveState.load(true) },
                    onRefresh = { WaveState.refresh(controller) },
                    onPlay = { Playback.play(controller, WaveState.tracks, it, autoContinue = true) },
                )
                else -> LibraryScreen(
                    play = ::play,
                    onSignOut = onSignOut,
                    onOpenArtist = { openArtist = it },
                )
            }
        }
    }

    if (showPlayer && controller != null) {
        NowPlayingScreen(
            controller,
            onClose = { showPlayer = false },
            onOpenArtist = { urn ->
                showPlayer = false
                openArtist = urn
            },
        )
    }
    }
}

@Composable
private fun Banner(
    icon: Int,
    text: String,
    container: Color,
    content: Color,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = content, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (chevron) {
            Icon(painterResource(R.drawable.ic_chevron_down), null, tint = content, modifier = Modifier.size(18.dp).rotate(-90f))
        }
    }
}

@Composable
private fun SearchTab(
    onPlay: (List<Track>, Track) -> Unit,
    onPlayTile: (List<Track>, Track) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        BackHandler(enabled = Search.query.isNotEmpty() || Search.searched) { Search.clear() }
        val barPad = 76.dp
        val resultsPad = 132.dp
        when {
            Search.searched || Search.loading || Search.error != null -> when (Search.cat) {
                SearchCat.Vibe -> Column(Modifier.fillMaxSize()) {
                    val state = Search.vibeState
                    if (state != VibeState.Ready || Search.vibeTracks.isEmpty()) {
                        Text(
                            stringResource(
                                when (state) {
                                    VibeState.Preparing -> R.string.vibe_preparing
                                    VibeState.Slow -> R.string.vibe_slow
                                    VibeState.Unavailable -> R.string.vibe_unavailable
                                    else -> R.string.vibe_empty
                                },
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = resultsPad).padding(16.dp),
                        )
                    }
                    if (Search.vibeTracks.isNotEmpty()) {
                        TrackList(
                            tracks = Search.vibeTracks,
                            loading = false,
                            error = null,
                            emptyText = "",
                            canLoadMore = false,
                            onLoadMore = {},
                            onPlay = { onPlayTile(Search.vibeTracks, it) },
                            topPadding = resultsPad,
                        )
                    }
                }
                SearchCat.Tracks -> TrackList(
                    tracks = Search.tracks,
                    loading = Search.loading,
                    error = Search.error,
                    emptyText = stringResource(R.string.search_empty),
                    canLoadMore = Search.tracksMore,
                    onLoadMore = { Search.search(Search.trackPage + 1) },
                    onPlay = { onPlay(Search.tracks, it) },
                    topPadding = resultsPad,
                )
                SearchCat.Artists -> ArtistCardList(
                    artists = Search.artists,
                    loading = Search.loading,
                    canLoadMore = Search.artistsMore,
                    onLoadMore = { Search.loadArtists(Search.artistPage + 1) },
                    onOpen = onOpenArtist,
                    topPadding = resultsPad,
                )
                SearchCat.Playlists, SearchCat.Albums -> PlaylistCardList(
                    playlists = Search.playlists.filter { it.isAlbum == (Search.cat == SearchCat.Albums) },
                    loading = Search.loading,
                    canLoadMore = Search.playlistsMore,
                    onLoadMore = { Search.loadPlaylists(Search.playlistPage + 1) },
                    onOpen = onOpenPlaylist,
                    topPadding = resultsPad,
                )
            }
            Search.query.isBlank() -> TileGrid(
                Tiles.items,
                topPadding = barPad,
                onLoadMore = { Tiles.loadMore() },
            ) { onPlayTile(Tiles.items, it) }
        }

        Column(Modifier.fillMaxWidth()) {
            TextField(
                value = Search.query,
                onValueChange = { Search.type(it) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { Search.search(0) }),
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                trailingIcon = {
                    if (Search.query.isNotEmpty()) IconButton(onClick = { Search.clear() }) {
                        Icon(painterResource(R.drawable.ic_clear), iconLabel(R.drawable.ic_clear))
                    }
                },
            )

            if (Search.searched) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        SearchCat.Tracks to R.string.search_tracks,
                        SearchCat.Vibe to R.string.vibe_search,
                        SearchCat.Artists to R.string.search_artists,
                        SearchCat.Playlists to R.string.search_playlists,
                        SearchCat.Albums to R.string.search_albums,
                    ).forEach { (cat, labelRes) ->
                        FilterChip(
                            selected = Search.cat == cat,
                            onClick = { Search.select(cat) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
            }

            val hasSuggestions = Search.suggestions.isNotEmpty() || Search.userSuggestions.isNotEmpty() ||
                Search.playlistSuggestions.isNotEmpty()
            if (!Search.searched && Search.query.trim().length >= 2) {
                Column(Modifier.fillMaxWidth()) {
                    SuggestionRow(
                        onClick = { Search.searchVibe() },
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_wave),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        },
                        title = stringResource(R.string.vibe_search_row, Search.query.trim()),
                        subtitle = stringResource(R.string.vibe_search_hint),
                    )
                    Search.userSuggestions.forEach { u ->
                        SuggestionRow(
                            onClick = { onOpenArtist(u.urn) },
                            leading = {
                                AsyncImage(
                                    model = Api.artworkUrl(u.avatar_url, "t120x120"),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop,
                                )
                            },
                            title = u.username,
                            subtitle = stringResource(R.string.artist_label),
                        )
                    }
                    Search.playlistSuggestions.forEach { p ->
                        SuggestionRow(
                            onClick = { onOpenPlaylist(p) },
                            leading = { PlaylistArtwork(p, 28.dp, 4.dp) },
                            title = p.title,
                            subtitle = stringResource(p.kindLabelRes()),
                        )
                    }
                    Search.suggestions.take(4).forEach { s ->
                        SuggestionRow(
                            onClick = {
                                Search.type(s.title)
                                Search.search(0)
                            },
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_search),
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            title = s.title + (s.user?.username?.let { " — $it" } ?: ""),
                            subtitle = null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    title: String,
    subtitle: String?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (subtitle != null) FontWeight.Medium else null,
            )
            if (subtitle != null) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
