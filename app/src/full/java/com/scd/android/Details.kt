package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun GenreDots(tracks: List<Track>) {
    val p = LocalGlass.current
    val youtubeLabel = stringResource(R.string.youtube_badge)
    val genres = remember(tracks, youtubeLabel) {
        val base = topGenres(tracks, 8)
        val fromYouTube = tracks.count { YouTubeImport.isYouTube(it.urn) }
        if (fromYouTube == 0) base
        else (base + GenreShare("youtube", youtubeLabel, fromYouTube.toFloat() / tracks.size, YouTubeNeon.core)).sortedByDescending { it.share }
    }
    if (genres.isEmpty()) return
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        genres.forEach { g ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                GenreDot(g.color, 6.dp)
                Spacer(Modifier.width(5.dp))
                Text(g.label, style = GlassType.small.copy(fontSize = 11.5.sp), color = p.text2, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CrateStack(playlist: Playlist, tracks: List<Track>) {
    val p = LocalGlass.current
    val ghosts = remember(tracks) { tracks.drop(1).take(2) }
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        ghosts.reversed().forEachIndexed { i, t ->
            val depth = ghosts.size - i
            Artwork(
                t.artModel("t250x250"),
                Modifier
                    .size(176.dp)
                    .graphicsLayer {
                        rotationZ = if (depth == 1) 6f else -7f
                        translationX = (if (depth == 1) 14.dp else (-12).dp).toPx()
                        alpha = 0.55f
                    },
                corner = 24.dp,
            )
        }
        Box(
            Modifier
                .size(176.dp)
                .shadow(28.dp, RoundedCornerShape(24.dp), clip = false, ambientColor = p.accent, spotColor = p.accent),
        ) {
            PlaylistArtwork(playlist, 176.dp, 24.dp)
        }
    }
}

@Composable
fun PlaylistDetail(playlist: Playlist, offline: Boolean) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalGlass.current
    var tracks by remember(playlist.urn) { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember(playlist.urn) { mutableIntStateOf(0) }
    var hasMore by remember(playlist.urn) { mutableStateOf(false) }
    var loading by remember(playlist.urn) { mutableStateOf(false) }
    var failed by remember(playlist.urn) { mutableStateOf(false) }
    var notSaved by remember(playlist.urn) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var reordering by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var dropping by remember { mutableStateOf<List<String>?>(null) }
    var trackMenu by remember { mutableStateOf<Track?>(null) }
    val own = LocalPlaylists.find(playlist.urn)
    val title = own?.title ?: playlist.title
    val filter = remember(playlist.urn) { LocalFilter() }

    LaunchedEffect(Unit) { if (!offline) LikedPlaylists.seed() }

    fun load(next: Int) {
        if (loading) return
        loading = true
        scope.launch {
            fun apply(res: Pair<List<Track>, Boolean>) {
                tracks = if (next == 0) res.first else (tracks + res.first).distinctBy { it.urn }
                page = next
                hasMore = res.second
            }
            val cached = runCatching { Library.playlistPage(playlist.urn, next, fresh = false, offline = offline) }
            cached.onSuccess { apply(it) }
            var ok = cached.isSuccess
            if (!offline) {
                runCatching { Library.playlistPage(playlist.urn, next, fresh = true, offline = false) }.onSuccess {
                    apply(it)
                    ok = true
                }
            }
            failed = !ok && tracks.isEmpty()
            notSaved = failed && cached.exceptionOrNull() is Library.NotSavedOfflineException
            loading = false
        }
    }

    LaunchedEffect(playlist.urn) { load(0) }
    LaunchedEffect(own?.tracks) {
        val live = own?.tracks ?: return@LaunchedEffect
        if (live.isNotEmpty() || tracks.isNotEmpty()) tracks = live
    }
    LaunchedEffect(playlist.urn, hasMore, page, loading, filter.active) {
        if (hasMore && !loading && tracks.isNotEmpty() && (tracks.size < 1000 || filter.active)) load(page + 1)
    }

    val aura = remember(tracks, p.accent) { auraColors(tracks, p.accent) }
    val playable = tracks.filter(Playback::playable)
    val whole: suspend () -> List<Track> = { Library.playlistAll(playlist.urn, offline) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") {
            ScreenTop(null, onBack = { nav.pop() }) {
                IconOrb(R.drawable.ic_more_vert, stringResource(R.string.cd_more), size = 42.dp, iconSize = 18.dp) { menu = true }
            }
        }
        item(key = "hero") {
            HeroPanel(glow = aura.first()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CrateStack(playlist.copy(title = title), tracks)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        GlassChip(stringResource(if (own != null) R.string.playlist_label else playlist.kindLabelRes()), icon = R.drawable.ic_music)
                        if (own?.localOnly == true) {
                            GlassChip(stringResource(R.string.playlist_local), icon = R.drawable.ic_download)
                        } else if (own?.isPrivate == true) {
                            GlassChip(stringResource(R.string.playlist_private), icon = R.drawable.ic_lock)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    GradientText(title, GlassType.hero.copy(textAlign = TextAlign.Center), aura.ifEmpty { listOf(p.text, p.text) }.let { listOf(p.text) + it }, maxLines = 3)
                    Spacer(Modifier.height(6.dp))
                    val owner = playlist.user?.username ?: if (own != null) Prefs.username else null
                    Text(
                        listOfNotNull(owner, stringResource(R.string.tracks_count, maxOf(tracks.size, own?.count ?: playlist.track_count))).joinToString(" · "),
                        style = GlassType.small,
                        color = p.text2,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    GenreDots(tracks)
                    if (own?.syncing == true) {
                        Spacer(Modifier.height(10.dp))
                        GlassChip(stringResource(R.string.playlist_syncing_short), icon = R.drawable.ic_refresh)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhiteButton(stringResource(R.string.play_all), R.drawable.ic_play, Modifier.weight(1f)) {
                            if (playable.isNotEmpty()) Playback.play(controller, playable, playable.first(), source = playlist.urn, more = whole)
                        }
                        Spacer(Modifier.width(10.dp))
                        val shuffled = Playback.shuffledFrom(playlist.urn)
                        IconOrb(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle, stringResource(R.string.notif_shuffle), size = 48.dp, active = shuffled) {
                            Playback.toggleShuffle(controller, playlist.urn, playable, more = whole)
                        }
                        if (tracks.isNotEmpty() || filter.open) {
                            Spacer(Modifier.width(8.dp))
                            IconOrb(
                                if (filter.open) R.drawable.ic_clear else R.drawable.ic_search,
                                stringResource(if (filter.open) R.string.cd_clear else R.string.local_search_hint),
                                size = 48.dp,
                                active = filter.open,
                            ) { filter.toggle() }
                        }
                        if (own == null && !offline) {
                            Spacer(Modifier.width(8.dp))
                            val liked = LikedPlaylists.isLiked(playlist.urn)
                            IconOrb(
                                if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                                stringResource(if (liked) R.string.notif_unlike else R.string.notif_like),
                                size = 48.dp,
                                active = liked,
                            ) { scope.launch { LikedPlaylists.toggle(playlist) } }
                        }
                        if (!offline) {
                            Spacer(Modifier.width(8.dp))
                            DownloadAllOrb(playlist.urn, tracks, complete = !hasMore) {
                                Downloads.enqueue(context, tracks, playlist.urn, title)
                                emptyList()
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val shown = if (filter.active) filterTracks(tracks, filter.query) else tracks
        if (filter.open) {
            item(key = "search", contentType = "search") { LocalSearchField(filter, if (filter.active) shown.size else null) }
        }
        trackItems(
            shown,
            onPlay = { t -> Playback.play(controller, shown, t, source = playlist.urn, more = if (filter.active) null else whole) },
            dimUndownloaded = offline,
            onRemove = if (own != null) { t -> LocalPlaylists.remove(own.id, t.urn) } else null,
            trailing = if (own != null) @Composable { t: Track ->
                Box(Modifier.size(40.dp).clip(CircleShape).pressable { trackMenu = t }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.cd_more), tint = p.text3, modifier = Modifier.size(18.dp))
                }
            } else null,
        )
        if (filter.active && shown.isEmpty() && tracks.isNotEmpty()) {
            item(key = "nothing") {
                if (hasMore) Spinner() else EmptyState(R.drawable.ic_search, stringResource(R.string.local_search_empty))
            }
        } else if (tracks.isEmpty()) {
            item(key = "empty") {
                when {
                    loading -> SkeletonRows()
                    notSaved -> EmptyState(R.drawable.ic_no_wifi, stringResource(R.string.playlist_not_offline))
                    failed -> EmptyState(R.drawable.ic_no_wifi, stringResource(networkErrorText()), action = stringResource(R.string.wave_refresh), onAction = { load(0) })
                    else -> EmptyState(R.drawable.ic_music, stringResource(R.string.playlist_empty))
                }
            }
        } else if (hasMore) {
            item(key = "more") { Spinner() }
        }
    }

    val pickCover = rememberCoverPicker(playlist.urn)
    if (menu) {
        GlassSheet({ menu = false }, title, icon = R.drawable.ic_music) {
            SheetAction(R.drawable.ic_music, stringResource(R.string.cover_change)) {
                menu = false
                pickCover()
            }
            if (PlaylistCovers.has(playlist.urn)) {
                SheetAction(R.drawable.ic_refresh, stringResource(R.string.cover_reset)) {
                    menu = false
                    PlaylistCovers.clear(playlist.urn)
                }
            }
            if (own != null && tracks.size > 1) {
                SheetAction(R.drawable.ic_drag, stringResource(R.string.reorder_title)) {
                    menu = false
                    reordering = true
                }
            }
            if (tracks.any { Downloads.isDownloaded(it.urn) }) {
                SheetAction(R.drawable.ic_trash, stringResource(R.string.playlist_remove_downloads)) {
                    menu = false
                    scope.launch { dropping = Library.playlistDownloads(playlist.urn, tracks, offline).takeIf { it.isNotEmpty() } }
                }
            }
            if (own != null) {
                SheetAction(R.drawable.ic_edit, stringResource(R.string.rename)) {
                    menu = false
                    renaming = true
                }
                SheetAction(R.drawable.ic_lock, stringResource(if (own.isPrivate) R.string.playlist_make_public else R.string.playlist_make_private)) {
                    menu = false
                    LocalPlaylists.setPrivate(own.id, !own.isPrivate)
                }
                SheetAction(R.drawable.ic_trash, stringResource(R.string.delete), tint = Color(0xFFFF5A5F)) {
                    menu = false
                    deleting = true
                }
            }
            if (playlist.urn in Downloads.activeLists) {
                SheetAction(R.drawable.ic_clear, stringResource(R.string.cd_cancel_download)) {
                    menu = false
                    Downloads.cancelList(context, playlist.urn)
                }
            }
        }
    }
    if (reordering && own != null) {
        ReorderDialog(tracks, onDismiss = { reordering = false }) { order ->
            reordering = false
            LocalPlaylists.reorder(own.id, order)
        }
    }
    if (renaming && own != null) {
        var value by remember { mutableStateOf(own.title) }
        GlassDialog(
            onDismiss = { renaming = false },
            title = stringResource(R.string.rename),
            confirm = stringResource(R.string.save),
            confirmEnabled = value.isNotBlank(),
            onConfirm = {
                LocalPlaylists.rename(own.id, value.trim())
                renaming = false
            },
        ) { GlassTextField(value, { value = it }) }
    }
    dropping?.let { urns ->
        GlassDialog(
            onDismiss = { dropping = null },
            title = stringResource(R.string.playlist_remove_downloads),
            text = stringResource(R.string.playlist_remove_downloads_q, urns.size),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                dropping = null
                if (playlist.urn in Downloads.activeLists) Downloads.cancelList(context, playlist.urn)
                App.scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    Downloads.removeAll(urns)
                    android.widget.Toast.makeText(context.applicationContext, context.getString(R.string.playlist_downloads_removed, urns.size), android.widget.Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
    if (deleting && own != null) {
        GlassDialog(
            onDismiss = { deleting = false },
            title = stringResource(R.string.delete),
            text = stringResource(R.string.delete_playlist_q, own.title),
            confirm = stringResource(R.string.delete),
            danger = true,
            onConfirm = {
                deleting = false
                LocalPlaylists.delete(own.id)
                nav.pop()
            },
        )
    }
    trackMenu?.let { t ->
        TrackActionsSheet(
            track = t,
            onDismiss = { trackMenu = null },
            onRemove = if (own != null) { { LocalPlaylists.remove(own.id, t.urn) } } else null,
            onPlay = { Playback.play(controller, tracks, t, source = playlist.urn, more = whole) },
        )
    }
}

fun shareText(context: android.content.Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, null)) }
}

@Composable
fun TrackActionsSheet(track: Track, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null, onPlay: (() -> Unit)? = null) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val controller = LocalController.current
    val scope = rememberCoroutineScope()
    val p = LocalGlass.current
    var picking by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    if (picking) {
        PlaylistPickerSheet(track, onDismiss = onDismiss)
        return
    }
    if (blocking) {
        BlockConfirmDialog(BlockedArtists.targets(track), onDismiss = onDismiss)
        return
    }
    GlassSheet(onDismiss) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artModel("t120x120"), Modifier.size(56.dp), corner = 14.dp)
            Spacer(Modifier.width(14.dp))
            androidx.compose.foundation.text.selection.SelectionContainer(Modifier.weight(1f)) {
                Column {
                    Text(track.title, style = GlassType.rowTitle.copy(fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = p.text)
                    Text(track.user?.username ?: "", style = GlassType.small, color = p.text2)
                }
            }
            IconOrb(R.drawable.ic_clear, stringResource(R.string.cd_close), size = 40.dp, iconSize = 14.dp, onClick = onDismiss)
        }
        Spacer(Modifier.height(6.dp))
        GlassDivider()
        if (onPlay != null && Playback.playable(track)) {
            SheetAction(R.drawable.ic_play, stringResource(R.string.track_menu_play)) {
                onDismiss()
                onPlay()
            }
        }
        if (controller != null && controller.currentMediaItem?.mediaId != track.urn) {
            SheetAction(R.drawable.ic_queue_next, stringResource(R.string.track_menu_play_next)) {
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
            SheetAction(R.drawable.ic_mix, stringResource(R.string.track_menu_radio)) {
                onDismiss()
                android.widget.Toast.makeText(context, context.getString(R.string.radio_starting), android.widget.Toast.LENGTH_SHORT).show()
                Playback.radioNow(controller, track) { added ->
                    if (added == 0) android.widget.Toast.makeText(context.applicationContext, context.getString(R.string.radio_failed), android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
        SheetAction(R.drawable.ic_plus, stringResource(R.string.add_to_playlist)) { picking = true }
        val liked = Likes.isLiked(track.urn)
        SheetAction(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart, stringResource(if (liked) R.string.notif_unlike else R.string.notif_like)) {
            onDismiss()
            App.scope.launch { Likes.toggle(track) }
        }
        track.catalogArtist?.let { ca ->
            if (!Prefs.offline) {
                SheetAction(R.drawable.ic_mic, stringResource(R.string.catalog_open, ca.name)) {
                    onDismiss()
                    nav.player = false
                    nav.push(Screen.CatalogS(ca.id))
                }
            }
        }
        if (Prefs.userUrn != null && !Prefs.offline && BlockedArtists.targets(track).isNotEmpty()) {
            SheetAction(R.drawable.ic_block, stringResource(R.string.block_artist)) { blocking = true }
        }
        track.user?.urn?.let { urn ->
            if (!Prefs.offline) {
                SheetAction(R.drawable.ic_user, stringResource(R.string.to_artist)) {
                    onDismiss()
                    nav.player = false
                    nav.push(Screen.ArtistS(urn))
                }
            }
        }
        if (!Downloads.isDownloaded(track.urn)) {
            if (!Prefs.offline) {
                SheetAction(R.drawable.ic_download, stringResource(R.string.cd_download)) {
                    onDismiss()
                    Downloads.enqueue(context, listOf(track))
                }
            }
        } else {
            SheetAction(R.drawable.ic_trash, stringResource(R.string.cd_remove_download)) {
                onDismiss()
                App.scope.launch { Downloads.remove(track.urn) }
            }
        }
        track.permalink_url?.let { url ->
            SheetAction(R.drawable.ic_share, stringResource(R.string.cd_share)) {
                onDismiss()
                shareText(context, url)
            }
        }
        if (onRemove != null) {
            SheetAction(R.drawable.ic_trash, stringResource(R.string.playlist_remove_track), tint = Color(0xFFFF5A5F)) {
                onDismiss()
                onRemove()
            }
        }
    }
}

private enum class ArtistTab { Tracks, Playlists }

@Composable
fun ArtistDetail(urn: String, offline: Boolean) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = LocalGlass.current
    var artist by remember(urn) { mutableStateOf<Artist?>(null) }
    var playlists by remember(urn) { mutableStateOf<List<Playlist>>(emptyList()) }
    var tab by rememberSaveable(urn) { mutableStateOf(ArtistTab.Tracks) }
    var radioBusy by remember(urn) { mutableStateOf(false) }
    var blocking by remember(urn) { mutableStateOf(false) }
    LaunchedEffect(urn) {
        if (!offline) {
            launch { Follows.load(urn) }
            launch { Auras.load(urn) }
            launch { WebProfiles.load(urn) }
        }
    }

    val pager = rememberPaged("artist:$urn:$offline") { page, fresh ->
        when {
            fresh -> emptyList<Track>() to false
            offline -> (if (page == 0) Downloads.tracks().filter { it.user?.urn == urn } else emptyList()) to false
            else -> Library.artistPage(urn, page)
        }
    }

    LaunchedEffect(urn, offline) {
        if (offline) {
            val u = Downloads.tracks().firstOrNull { it.user?.urn == urn }?.user
            if (u != null) artist = Artist(urn = urn, username = u.username, avatar_url = u.avatar_url)
        } else {
            runCatching { Api.user(urn) }.onSuccess { artist = it }
            runCatching { Api.userPlaylists(urn) }.onSuccess { playlists = it.collection.distinctBy { pl -> pl.urn } }
        }
    }
    LaunchedEffect(pager.items) {
        if (artist == null) pager.items.firstOrNull()?.user?.let { u -> artist = Artist(urn = urn, username = u.username, avatar_url = u.avatar_url) }
    }

    val tracks = pager.items
    val userAura = userAuraColors(Auras.of(urn))
    val aura = remember(tracks, p.accent, userAura) { userAura ?: auraColors(tracks, p.accent) }
    val catalog = remember(tracks) { if (offline) null else CatalogRepo.findCatalogFor(urn, tracks) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(null, onBack = { nav.pop() }) }
        item(key = "hero") {
            HeroPanel(glow = aura.first()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                        AuraRing(spinning = userAura != null || (NowPlaying.isPlaying && NowPlaying.artistUrn == urn), colors = aura, corner = 62.dp, modifier = Modifier.size(124.dp))
                        AsyncImage(
                            model = Api.artworkUrl(artist?.avatar_url, "t300x300"),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(112.dp)
                                .shadow(24.dp, CircleShape, clip = false, ambientColor = aura.first(), spotColor = aura.first())
                                .clip(CircleShape)
                                .background(p.fillTop)
                                .border(0.75.dp, p.borderHi, CircleShape),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Eyebrow(stringResource(R.string.artist_label), color = p.text3)
                    Spacer(Modifier.height(4.dp))
                    GradientText(artist?.username ?: "…", GlassType.hero.copy(textAlign = TextAlign.Center), listOf(p.text) + aura, maxLines = 2)
                    val a = artist
                    val place = listOfNotNull(a?.city?.takeIf { it.isNotBlank() }, a?.country_code?.takeIf { it.isNotBlank() }).joinToString(", ")
                    if (place.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(place, style = GlassType.small, color = p.text2)
                    }
                    Spacer(Modifier.height(10.dp))
                    StatLine(
                        listOfNotNull(
                            a?.followers_count?.let { formatCount(it) to stringResource(R.string.me_stat_followers) },
                            a?.track_count?.let { formatCount(it) to stringResource(R.string.me_stat_tracks) },
                        ),
                        center = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    GenreDots(tracks)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!offline) {
                            WhiteButton(stringResource(R.string.artist_wave), R.drawable.ic_wave, Modifier.weight(1f)) {
                                if (radioBusy) return@WhiteButton
                                radioBusy = true
                                scope.launch {
                                    val radio = runCatching { Playback.artistRadio(urn, tracks) }.getOrDefault(emptyList())
                                    radioBusy = false
                                    if (radio.isNotEmpty()) {
                                        Playback.play(controller, radio, radio.first(), autoContinue = true)
                                    } else {
                                        android.widget.Toast.makeText(context, context.getString(R.string.radio_failed), android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } else {
                            WhiteButton(stringResource(R.string.play_all), R.drawable.ic_play, Modifier.weight(1f)) {
                                val list = tracks.filter(Playback::playable)
                                if (list.isNotEmpty()) Playback.play(controller, list, list.first(), source = "artist:$urn", more = { pager.all() })
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        val shuffled = Playback.shuffledFrom("artist:$urn")
                        IconOrb(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle, stringResource(R.string.notif_shuffle), size = 48.dp, active = shuffled) {
                            Playback.toggleShuffle(controller, "artist:$urn", tracks.filter(Playback::playable), more = { pager.all() })
                        }
                        a?.let { art ->
                            Spacer(Modifier.width(8.dp))
                            val liked = LikedArtists.isLiked(art.urn)
                            IconOrb(
                                if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                                stringResource(if (liked) R.string.notif_unlike else R.string.notif_like),
                                size = 48.dp,
                                active = liked,
                            ) { LikedArtists.toggle(art) }
                        }
                    }
                    val canFollow = Follows.canFollow(urn)
                    val scId = scIdOf(urn)
                    val canBlock = !offline && Prefs.userUrn != null && scId != null && scId != scIdOf(Prefs.userUrn)
                    if (canFollow || canBlock || catalog != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canFollow) {
                                val following = Follows.isFollowing(urn) == true
                                GlassChip(
                                    stringResource(if (following) R.string.unfollow else R.string.follow),
                                    icon = if (following) R.drawable.ic_following else R.drawable.ic_follow,
                                    active = following,
                                    onClick = {
                                        if (!Follows.isBusy(urn)) scope.launch {
                                            if (!Follows.toggle(urn)) android.widget.Toast.makeText(context, context.getString(R.string.follow_failed), android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                )
                            }
                            catalog?.let { c ->
                                GlassChip(stringResource(R.string.catalog_card), icon = R.drawable.ic_mic, onClick = { nav.push(Screen.CatalogS(c.id)) })
                            }
                            if (canBlock && scId != null) {
                                val blocked = BlockedArtists.isBlocked("user", scId)
                                GlassChip(
                                    stringResource(if (blocked) R.string.unblock else R.string.block),
                                    icon = R.drawable.ic_block,
                                    iconTint = Color(0xFFFF5A5F),
                                    active = blocked,
                                    onClick = { if (blocked) scope.launch { BlockedArtists.unblock("user", scId) } else blocking = true },
                                )
                            }
                        }
                    }
                    val webs = WebProfiles.of(urn)
                    if (webs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            webs.forEach { w ->
                                GlassChip(webProfileLabel(w), icon = R.drawable.ic_share, onClick = { shareTextOrOpen(context, w.url) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (playlists.isNotEmpty()) {
            item(key = "seg") {
                SegmentDock(
                    labels = listOf(stringResource(R.string.tracks), stringResource(R.string.playlists)),
                    selected = tab.ordinal,
                    onSelect = { tab = ArtistTab.entries[it] },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        if (tab == ArtistTab.Playlists && playlists.isNotEmpty()) {
            items(playlists, key = { "p:" + it.urn }, contentType = { "playlist" }) { pl ->
                PlaylistRowGlass(
                    playlist = pl,
                    title = pl.title,
                    subtitle = stringResource(pl.kindLabelRes()) + " · " + stringResource(R.string.tracks_count, pl.track_count),
                    onClick = { nav.push(Screen.PlaylistS(pl)) },
                )
            }
        } else {
            trackItems(tracks, onPlay = { t -> Playback.play(controller, tracks, t, source = "artist:$urn", more = { pager.all() }) }, dimUndownloaded = offline)
            if (pager.hasMore && tracks.isNotEmpty()) {
                item(key = "more") {
                    LaunchedEffect(tracks.size) { pager.loadMore() }
                    Spinner()
                }
            }
            if (tracks.isEmpty()) {
                item(key = "empty") {
                    if (pager.loading) SkeletonRows() else EmptyState(R.drawable.ic_music, stringResource(R.string.artist_no_tracks))
                }
            }
        }
    }
    if (blocking) {
        val target = BlockedArtists.userTarget(urn, artist?.username ?: tracks.firstOrNull()?.user?.username.orEmpty(), artist?.avatar_url)
        if (target != null) BlockConfirmDialog(listOf(target), onDismiss = { blocking = false }) else blocking = false
    }
}

@Composable
private fun CollectionHero(
    eyebrow: String,
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    autoContinue: Boolean,
    key: String,
    more: (suspend () -> List<Track>)? = null,
) {
    val p = LocalGlass.current
    val controller = LocalController.current
    val aura = remember(tracks, p.accent) { auraColors(tracks, p.accent) }
    HeroPanel(glow = aura.first()) {
        Column {
            Eyebrow(eyebrow, color = p.text3)
            Spacer(Modifier.height(6.dp))
            GradientText(title, GlassType.hero, listOf(p.text) + aura, maxLines = 2)
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = GlassType.body, color = p.text2, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(10.dp))
            GenreDots(tracks)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val playable = tracks.filter(Playback::playable)
                WhiteButton(stringResource(R.string.play_all), R.drawable.ic_play, Modifier.weight(1f)) {
                    if (playable.isNotEmpty()) Playback.play(controller, playable, playable.first(), autoContinue = autoContinue, source = key, more = more)
                }
                Spacer(Modifier.width(10.dp))
                val shuffled = Playback.shuffledFrom(key)
                IconOrb(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle, stringResource(R.string.notif_shuffle), size = 48.dp, active = shuffled) {
                    Playback.toggleShuffle(controller, key, playable, autoContinue, more)
                }
            }
        }
    }
}

@Composable
fun ShelfDetail(id: String) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val shelf = Recommendations.shelves.firstOrNull { it.id == id }
    val tracks = shelf?.tracks.orEmpty()
    val (titleRes, descRes) = shelfTitles(id)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(null, onBack = { nav.pop() }) }
        item(key = "hero") {
            CollectionHero(stringResource(R.string.tab_flow), stringResource(titleRes), stringResource(descRes), tracks, autoContinue = true, key = "shelf:$id")
            Spacer(Modifier.height(10.dp))
        }
        if (tracks.isNotEmpty()) {
            item(key = "bar") { ListToolbar(count = tracks.size, downloaded = tracks.count { Downloads.isDownloaded(it.urn) }) }
        }
        trackItems(tracks, onPlay = { t -> Playback.play(controller, tracks, t, autoContinue = true, source = "shelf:$id") })
        if (tracks.isEmpty()) {
            item(key = "empty") {
                if (Recommendations.loading) SkeletonRows() else EmptyState(R.drawable.ic_wave, stringResource(R.string.wave_empty), action = stringResource(R.string.wave_refresh), onAction = { Recommendations.refresh() })
            }
        }
    }
}

@Composable
fun GenreDetail(key: String, label: String, offline: Boolean) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val context = LocalContext.current
    val pager = rememberPaged("genre:$key:$offline") { page, fresh -> Library.genrePage(page, fresh, offline) }
    LaunchedEffect(pager.items.size, pager.hasMore, pager.loading) {
        if (pager.hasMore && !pager.loading && pager.items.size < 1000) pager.loadMore()
    }
    val fromPrint = remember(Library.soundprint, key) { Library.soundprint.filter { genresMatch(it, key) } }
    val tracks = (pager.items.filter { genresMatch(it, key) } + fromPrint).distinctBy { it.urn }
    val whole: suspend () -> List<Track> = { (pager.all().filter { genresMatch(it, key) } + fromPrint).distinctBy { it.urn } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(null, onBack = { nav.pop() }) }
        item(key = "hero") {
            CollectionHero(stringResource(R.string.soundprint_short), label, stringResource(R.string.genre_from_likes), tracks, autoContinue = false, key = "genre:$key", more = whole)
            Spacer(Modifier.height(10.dp))
        }
        if (tracks.isNotEmpty()) {
            item(key = "bar") {
                ListToolbar(count = tracks.size, downloaded = tracks.count { Downloads.isDownloaded(it.urn) }) {
                    if (!offline) {
                        DownloadAllOrb("genre:$key", tracks, complete = !pager.hasMore) {
                            pager.all().filter { genresMatch(it, key) }.also {
                                if (it.isNotEmpty()) Downloads.enqueue(context, it, listKey = "genre:$key")
                            }
                            emptyList()
                        }
                    }
                }
            }
        }
        trackItems(tracks, onPlay = { t -> Playback.play(controller, tracks, t, source = "genre:$key", more = whole) }, dimUndownloaded = offline)
        if (tracks.isEmpty()) {
            item(key = "empty") {
                if (pager.loading || pager.hasMore) SkeletonRows() else EmptyState(R.drawable.ic_heart, stringResource(R.string.me_empty_likes))
            }
        }
    }
}

fun shareTextOrOpen(context: android.content.Context, url: String) {
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}
