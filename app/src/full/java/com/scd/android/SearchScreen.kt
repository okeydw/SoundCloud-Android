package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlin.math.sin

private val GENRE_LABELS = mapOf(
    "lofi" to "Lo-fi", "house" to "House", "phonk" to "Phonk", "ambient" to "Ambient",
    "rnb" to "R&B", "trap" to "Trap", "jazz" to "Jazz", "techno" to "Techno",
    "indie" to "Indie", "soul" to "Soul", "dnb" to "DnB", "hyperpop" to "Hyperpop",
)

fun genreLabel(key: String) = GENRE_LABELS[key] ?: key.replaceFirstChar { it.uppercase() }

@Composable
fun SearchScreenGlass() {
    val nav = LocalNav.current
    val controller = LocalController.current
    val focus = LocalFocusManager.current
    val offline = Prefs.offline
    val q = Search.query
    val showResults = Search.searched || Search.loading || Search.error != null
    val hasSuggestions = Search.suggestions.isNotEmpty() || Search.userSuggestions.isNotEmpty() || Search.playlistSuggestions.isNotEmpty()
    val breathing = LocalFx.current.mode == FxMode.Max && q.isBlank()
    val time = rememberAmbientTime(breathing)
    val saved = Downloads.downloaded
    val tiles = if (offline) remember(saved) { Downloads.tracks() } else Tiles.items

    BackHandler(enabled = q.isNotEmpty() || Search.searched) { Search.clear() }
    LaunchedEffect(offline) {
        Tiles.restore()
        if (Tiles.items.isEmpty() && !offline) Tiles.load(0)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "head", contentType = "head") {
            SearchHead(onSubmit = {
                focus.clearFocus()
                Search.search(0)
            })
        }
        when {
            showResults -> results(
                onPlay = { list, t -> Playback.play(controller, list, t) },
                onArtist = { nav.push(Screen.ArtistS(it)) },
                onPlaylist = { nav.push(Screen.PlaylistS(it)) },
            )
            q.trim().length >= 2 -> suggestions(
                onArtist = { nav.push(Screen.ArtistS(it)) },
                onPlaylist = { nav.push(Screen.PlaylistS(it)) },
                onQuery = { text ->
                    focus.clearFocus()
                    Search.type(text)
                    Search.search(0)
                },
            )
            q.isBlank() -> {
                coverWall(tiles, if (breathing) time else null, loadMore = { if (!offline) Tiles.loadMore() }) { t ->
                    Playback.play(controller, tiles, t, autoContinue = !offline)
                }
            }
        }
    }
}

@Composable
private fun SearchHead(onSubmit: () -> Unit) {
    val p = LocalGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        Eyebrow(stringResource(R.string.search_lens), Modifier.padding(horizontal = 20.dp), color = p.text3)
        Spacer(Modifier.height(8.dp))
        GradientText(
            stringResource(R.string.search_title),
            GlassType.hero,
            listOf(p.text, p.text, p.accentHover),
            Modifier.padding(horizontal = 20.dp),
            maxLines = 2,
        )
        Spacer(Modifier.height(18.dp))
        SearchField(onSubmit)
        Spacer(Modifier.height(12.dp))
        if (Search.searched) CategoryPills() else GenreTicker()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SearchField(onSubmit: () -> Unit) {
    val p = LocalGlass.current
    val shape = RoundedCornerShape(19.dp)
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(54.dp)
            .glass(shape, p, shadow = 10.dp)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_search), null, tint = p.text2, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (Search.query.isEmpty()) {
                Text(stringResource(R.string.search_placeholder), style = GlassType.body.copy(fontSize = 16.sp), color = p.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = Search.query,
                onValueChange = { Search.type(it) },
                singleLine = true,
                textStyle = GlassType.body.copy(fontSize = 16.sp, color = p.text),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (Search.query.isNotBlank()) onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (Search.query.isNotEmpty()) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).clickable { Search.clear() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_clear), stringResource(R.string.cd_clear), tint = p.text2, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun CategoryPills() {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            SearchCat.Tracks to R.string.search_tracks,
            SearchCat.Vibe to R.string.vibe_search,
            SearchCat.Artists to R.string.search_artists,
            SearchCat.Playlists to R.string.search_playlists,
            SearchCat.Albums to R.string.search_albums,
        ).forEach { (cat, label) ->
            GlassChip(stringResource(label), active = Search.cat == cat, onClick = { Search.select(cat) })
        }
    }
}

@Composable
private fun GenreTicker() {
    val p = LocalGlass.current
    val scroll = rememberScrollState()
    var cycle by remember { mutableIntStateOf(0) }
    var touching by remember { mutableStateOf(false) }
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    val paused = touching || dragged

    LaunchedEffect(paused, cycle) {
        if (paused || cycle == 0) return@LaunchedEffect
        var last = withFrameNanos { it }
        var carry = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            carry += cycle * ((now - last) / 60_000_000_000f)
            last = now
            val step = carry.toInt()
            if (step > 0) {
                carry -= step
                val next = scroll.value + step
                scroll.scrollTo(if (next >= cycle) next - cycle else next)
            }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    Brush.horizontalGradient(0f to Color.Transparent, 0.07f to Color.Black, 0.93f to Color.Black, 1f to Color.Transparent),
                    blendMode = BlendMode.DstIn,
                )
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    waitForUpOrCancellation(PointerEventPass.Initial)
                    touching = false
                }
            }
            .horizontalScroll(scroll),
    ) {
        repeat(3) { copy ->
            Row(if (copy == 0) Modifier.onSizeChanged { cycle = it.width } else Modifier) {
                GENRES.forEach { key ->
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .clickable { Search.searchGenre(key) }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GenreDot(genreColor(key))
                        Spacer(Modifier.width(8.dp))
                        Text(genreLabel(key), style = GlassType.body.copy(fontSize = 15.sp), color = p.text2, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.results(
    onPlay: (List<Track>, Track) -> Unit,
    onArtist: (String) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    val error = Search.error
    when (Search.cat) {
        SearchCat.Vibe -> {
            val list = Search.vibeTracks
            if (Search.vibeState != VibeState.Ready || list.isEmpty()) {
                item(key = "vibe:status") { VibeStatus() }
            }
            trackItems(list, onPlay = { onPlay(list, it) })
        }
        SearchCat.Tracks -> {
            val list = Search.tracks
            trackItems(list, onPlay = { onPlay(list, it) })
            if (Search.tracksMore && list.isNotEmpty()) {
                item(key = "more:tracks") {
                    LaunchedEffect(list.size) { if (!Search.loading) Search.search(Search.trackPage + 1) }
                    Spinner()
                }
            }
            if (list.isEmpty()) statusItem(error)
        }
        SearchCat.Artists -> {
            val list = Search.artists
            items(list, key = { "a:" + it.urn }) { a ->
                ArtistRowGlass(
                    name = a.username,
                    avatar = a.avatar_url,
                    subtitle = a.followers_count?.let { stringResource(R.string.followers, formatCount(it)) },
                    onClick = { onArtist(a.urn) },
                )
            }
            if (Search.artistsMore && list.isNotEmpty()) {
                item(key = "more:artists") {
                    LaunchedEffect(list.size) { if (!Search.loading) Search.loadArtists(Search.artistPage + 1) }
                    Spinner()
                }
            }
            if (list.isEmpty()) statusItem(error)
        }
        SearchCat.Playlists, SearchCat.Albums -> {
            val albums = Search.cat == SearchCat.Albums
            val list = Search.playlists.filter { it.isAlbum == albums }
            items(list, key = { "p:" + it.urn }) { pl ->
                PlaylistRowGlass(
                    playlist = pl,
                    title = pl.title,
                    subtitle = listOfNotNull(pl.user?.username, stringResource(R.string.tracks_count, pl.track_count)).joinToString(" · "),
                    onClick = { onPlaylist(pl) },
                )
            }
            if (Search.playlistsMore && Search.playlists.isNotEmpty()) {
                item(key = "more:playlists") {
                    LaunchedEffect(Search.playlists.size) { if (!Search.loading) Search.loadPlaylists(Search.playlistPage + 1) }
                    Spinner()
                }
            }
            if (list.isEmpty() && !Search.playlistsMore) statusItem(error)
        }
    }
}

private fun LazyListScope.statusItem(error: String?) {
    item(key = "status") {
        when {
            Search.loading -> Spinner()
            error != null -> EmptyState(R.drawable.ic_no_wifi, error, action = stringResource(R.string.wave_refresh), onAction = { Search.search(0) })
            else -> EmptyState(R.drawable.ic_search, stringResource(R.string.search_empty))
        }
    }
}

private fun LazyListScope.suggestions(
    onArtist: (String) -> Unit,
    onPlaylist: (Playlist) -> Unit,
    onQuery: (String) -> Unit,
) {
    item(key = "vibe:row") { VibeRow() }
    items(Search.userSuggestions.take(3), key = { "su:" + it.urn }) { a ->
        ArtistRowGlass(a.username, a.avatar_url, stringResource(R.string.artist_label)) { onArtist(a.urn) }
    }
    items(Search.playlistSuggestions.take(3), key = { "sp:" + it.urn }) { pl ->
        PlaylistRowGlass(pl, pl.title, stringResource(if (pl.isAlbum) pl.kindLabelRes() else R.string.playlist_label), onClick = { onPlaylist(pl) })
    }
    items(Search.suggestions.take(5), key = { "st:" + it.urn }) { t ->
        val p = LocalGlass.current
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onQuery(t.title) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_search), null, tint = p.text3, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(14.dp))
            Text(t.title, style = GlassType.body, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(t.user?.username ?: "", style = GlassType.small, color = p.text3, maxLines = 1)
        }
    }
}

private fun LazyListScope.coverWall(tiles: List<Track>, time: State<Float>?, loadMore: () -> Unit, onPlay: (Track) -> Unit) {
    val blocks = tiles.chunked(6)
    itemsIndexed(blocks, key = { i, b -> "wall:$i:" + b.first().urn }, contentType = { _, _ -> "wall" }) { i, block ->
        WallBlock(block, time, onPlay)
        if (i == blocks.lastIndex) LaunchedEffect(tiles.size) { loadMore() }
    }
    if (Tiles.loading) {
        item(key = "wall:loading") { Spinner() }
    } else if (tiles.isEmpty()) {
        item(key = "wall:empty") { EmptyState(R.drawable.ic_search, stringResource(R.string.search_idle)) }
    }
}

@Composable
private fun WallBlock(block: List<Track>, time: State<Float>?, onPlay: (Track) -> Unit) {
    val gap = 10.dp
    val heroLeft = (block.first().urn.hashCode() and 1) == 0
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = gap)) {
        val cell = (maxWidth - gap * 2) / 3
        val hero = cell * 2 + gap
        val top = block.take(3)
        val rest = block.drop(3)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            if (top.size == 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    val big = @Composable { WallTile(top[0], hero, true, time) { onPlay(top[0]) } }
                    val side = @Composable {
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            WallTile(top[1], cell, false, time) { onPlay(top[1]) }
                            WallTile(top[2], cell, false, time) { onPlay(top[2]) }
                        }
                    }
                    if (heroLeft) {
                        big()
                        side()
                    } else {
                        side()
                        big()
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    top.forEach { t -> WallTile(t, cell, false, time) { onPlay(t) } }
                }
            }
            if (rest.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    rest.forEach { t -> WallTile(t, cell, false, time) { onPlay(t) } }
                }
            }
        }
    }
}

@Composable
private fun WallTile(track: Track, size: Dp, hero: Boolean, time: State<Float>?, onClick: () -> Unit) {
    val p = LocalGlass.current
    val current = NowPlaying.urn == track.urn
    val corner = if (hero) 26.dp else 20.dp
    val shape = RoundedCornerShape(corner)
    val phase = remember(track.urn) { (track.urn.hashCode() and 0xff) / 255f * 6.28f }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                if (time != null) {
                    val s = 1f + 0.016f * sin(time.value * 0.8f + phase)
                    scaleX = s
                    scaleY = s
                }
            }
            .then(if (current) Modifier.shadow(22.dp, shape, clip = false, ambientColor = p.accent, spotColor = p.accent) else Modifier)
            .pressable(onClick = onClick),
    ) {
        Artwork(track.artModel(if (hero) "t500x500" else "t250x250"), Modifier.matchParentSize(), corner = corner)
        if (hero) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(track.title, style = GlassType.rowTitle.copy(fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.user?.username ?: "", style = GlassType.small, color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (current) {
            Box(Modifier.matchParentSize().border(1.75.dp, p.accent, shape))
            EqBars(NowPlaying.isPlaying, Color.White, Modifier.align(Alignment.Center).width(26.dp).height(22.dp))
        }
    }
}

@Composable
fun Spinner(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), color = LocalGlass.current.accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun VibeRow() {
    val p = LocalGlass.current
    val focus = LocalFocusManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .glass(RoundedCornerShape(18.dp), p, shadow = 0.dp, tint = p.glow)
            .pressable {
                focus.clearFocus()
                Search.searchVibe()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).glass(CircleShape, p, shadow = 0.dp, tint = p.glowStrong), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_wave), null, tint = p.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.vibe_search_row, Search.query.trim()), style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.vibe_search_hint), style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        GlassChip("AI", mono = true)
    }
}

@Composable
private fun VibeStatus() {
    when (Search.vibeState) {
        VibeState.Preparing -> Column {
            Spinner()
            EmptyState(R.drawable.ic_wave, stringResource(R.string.vibe_preparing))
        }
        VibeState.Slow -> EmptyState(R.drawable.ic_wave, stringResource(R.string.vibe_slow), action = stringResource(R.string.wave_refresh), onAction = { Search.searchVibe() })
        VibeState.Unavailable -> EmptyState(R.drawable.ic_no_wifi, stringResource(R.string.vibe_unavailable), action = stringResource(R.string.wave_refresh), onAction = { Search.searchVibe() })
        else -> if (Search.loading) Spinner() else EmptyState(R.drawable.ic_search, stringResource(R.string.vibe_empty))
    }
}
