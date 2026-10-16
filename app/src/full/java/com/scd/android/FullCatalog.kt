package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class CatalogPane { Tracks, Albums, Related, About }

@Composable
fun CatalogArtistDetail(id: String) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val scope = rememberCoroutineScope()
    val p = LocalGlass.current
    var artist by remember(id) { mutableStateOf<CatalogArtist?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var albums by remember(id) { mutableStateOf<List<ArtistAlbum>>(emptyList()) }
    var pane by rememberSaveable(id) { mutableStateOf(CatalogPane.Tracks) }
    var sort by rememberSaveable(id) { mutableStateOf("popular") }
    var blocking by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        runCatching { Api.catalogArtist(id) }.onSuccess { artist = it }.onFailure { failed = true }
        runCatching { Api.catalogArtistAlbums(id) }.onSuccess { albums = it }
    }
    val pager = rememberPaged("catalog:$id:$sort") { page, fresh ->
        if (fresh) emptyList<Track>() to false else CatalogRepo.tracksPage(id, sort, page)
    }
    val tracks = pager.items
    val a = artist
    val aura = remember(tracks, p.accent) { auraColors(tracks, p.accent) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(null, onBack = { nav.pop() }) }
        if (a == null) {
            item(key = "wait") {
                if (failed) EmptyState(R.drawable.ic_mic, stringResource(R.string.catalog_failed)) else SkeletonRows()
            }
            return@LazyColumn
        }
        item(key = "hero") {
            HeroPanel(glow = aura.first()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(124.dp), contentAlignment = Alignment.Center) {
                        AuraRing(spinning = NowPlaying.isPlaying && tracks.any { it.urn == NowPlaying.urn }, colors = aura, corner = 62.dp, modifier = Modifier.size(124.dp))
                        AsyncImage(
                            model = a.avatar_url,
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
                    Eyebrow(stringResource(R.string.catalog_card), color = p.text3)
                    Spacer(Modifier.height(4.dp))
                    GradientText(a.name, GlassType.hero.copy(textAlign = TextAlign.Center), listOf(p.text) + aura, maxLines = 2)
                    a.country?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = GlassType.small, color = p.text2)
                    }
                    Spacer(Modifier.height(10.dp))
                    StatLine(
                        listOfNotNull(
                            formatCount(a.track_count) to stringResource(R.string.me_stat_tracks),
                            if (a.album_count > 0) formatCount(a.album_count) to stringResource(R.string.catalog_albums) else null,
                            if (a.track_count_featured > 0) formatCount(a.track_count_featured) to stringResource(R.string.catalog_featured) else null,
                        ),
                        center = true,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WhiteButton(stringResource(R.string.play_all), R.drawable.ic_play, Modifier.weight(1f)) {
                            val list = tracks.ifEmpty { a.popular_tracks }.filter(Playback::playable)
                            if (list.isNotEmpty()) Playback.play(controller, list, list.first(), source = "catalog:$id")
                        }
                        Spacer(Modifier.width(10.dp))
                        val shuffled = Playback.shuffledFrom("catalog:$id")
                        IconOrb(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle, stringResource(R.string.notif_shuffle), size = 48.dp, active = shuffled) {
                            Playback.toggleShuffle(controller, "catalog:$id", tracks.ifEmpty { a.popular_tracks }.filter(Playback::playable))
                        }
                        if (Prefs.userUrn != null) {
                            Spacer(Modifier.width(8.dp))
                            val blocked = BlockedArtists.isBlocked("artist", a.id)
                            IconOrb(
                                R.drawable.ic_block,
                                stringResource(if (blocked) R.string.unblock else R.string.block),
                                size = 48.dp,
                                active = blocked,
                                tint = if (blocked) Color(0xFFFF5A5F) else null,
                            ) {
                                if (blocked) scope.launch { BlockedArtists.unblock("artist", a.id) } else blocking = true
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        a.mainScUsers.firstOrNull()?.let { sc ->
                            GlassChip(stringResource(R.string.catalog_sc_profile), icon = R.drawable.ic_user, onClick = { nav.push(Screen.ArtistS(CatalogRepo.scUserUrn(sc))) })
                        }
                        SocialLinks(a.socials) { label, open -> GlassChip(label, icon = R.drawable.ic_share, onClick = open) }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        val panes = buildList {
            add(CatalogPane.Tracks)
            if (albums.isNotEmpty()) add(CatalogPane.Albums)
            if (a.related_artists.isNotEmpty()) add(CatalogPane.Related)
            if (!a.bio.isNullOrBlank()) add(CatalogPane.About)
        }
        if (panes.size > 1) {
            item(key = "seg") {
                SegmentDock(
                    labels = panes.map {
                        stringResource(
                            when (it) {
                                CatalogPane.Tracks -> R.string.tracks
                                CatalogPane.Albums -> R.string.catalog_albums
                                CatalogPane.Related -> R.string.catalog_related
                                CatalogPane.About -> R.string.catalog_about
                            },
                        )
                    },
                    selected = panes.indexOf(pane).coerceAtLeast(0),
                    onSelect = { pane = panes[it] },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        when (pane) {
            CatalogPane.Tracks -> {
                item(key = "sort") {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassChip(stringResource(R.string.catalog_popular), active = sort == "popular", onClick = { sort = "popular" })
                        GlassChip(stringResource(R.string.catalog_recent), active = sort == "recent", onClick = { sort = "recent" })
                    }
                    Spacer(Modifier.height(6.dp))
                }
                trackItems(tracks, onPlay = { t -> Playback.play(controller, tracks, t, source = "catalog:$id") })
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
            CatalogPane.Albums -> items(albums, key = { "al:" + it.id }) { al ->
                AlbumRowGlass(al.title, al.cover_url, listOfNotNull(stringResource(albumKindRes(al.type)), al.release_year?.toString()).joinToString(" · ")) {
                    nav.push(Screen.AlbumS(al.id))
                }
            }
            CatalogPane.Related -> items(a.related_artists, key = { "r:" + it.id }) { r ->
                ArtistRowGlass(r.name, r.avatar_url, r.country) { nav.push(Screen.CatalogS(r.id)) }
            }
            CatalogPane.About -> item(key = "bio") {
                Text(a.bio.orEmpty(), style = GlassType.body, color = p.text2, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
        }
    }
    if (blocking && a != null) BlockConfirmDialog(listOf(BlockedArtists.artistTarget(a)), onDismiss = { blocking = false })
}

@Composable
private fun AlbumRowGlass(title: String, cover: String?, subtitle: String, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(cover, Modifier.size(56.dp), corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = GlassType.small, color = p.text2, maxLines = 1)
        }
    }
}

@Composable
fun AlbumDetail(id: String) {
    val nav = LocalNav.current
    val controller = LocalController.current
    val p = LocalGlass.current
    var album by remember(id) { mutableStateOf<CatalogAlbum?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { runCatching { Api.catalogAlbum(id) }.onSuccess { album = it }.onFailure { failed = true } }
    val al = album
    val tracks = remember(al) { al?.tracks.orEmpty().filter { !it.urn.startsWith("wanted:") } }
    val aura = remember(tracks, p.accent) { auraColors(tracks, p.accent) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(null, onBack = { nav.pop() }) }
        if (al == null) {
            item(key = "wait") { if (failed) EmptyState(R.drawable.ic_album, stringResource(R.string.catalog_failed)) else SkeletonRows() }
            return@LazyColumn
        }
        item(key = "hero") {
            HeroPanel(glow = aura.first()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(al.cover_url, Modifier.size(180.dp).shadow(28.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = aura.first(), spotColor = aura.first()), corner = 20.dp)
                    Spacer(Modifier.height(14.dp))
                    Eyebrow(listOfNotNull(stringResource(albumKindRes(al.type)), al.release_year?.toString()).joinToString(" · "), color = p.text3)
                    Spacer(Modifier.height(4.dp))
                    GradientText(al.title, GlassType.title.copy(textAlign = TextAlign.Center), listOf(p.text) + aura, maxLines = 3)
                    al.primary_artist?.let { pa ->
                        Spacer(Modifier.height(8.dp))
                        GlassChip(pa.name, icon = R.drawable.ic_mic, onClick = { nav.push(Screen.CatalogS(pa.id)) })
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val playable = tracks.filter(Playback::playable)
                        WhiteButton(stringResource(R.string.play_all), R.drawable.ic_play, Modifier.weight(1f)) {
                            if (playable.isNotEmpty()) Playback.play(controller, playable, playable.first(), source = "album:$id")
                        }
                        Spacer(Modifier.width(10.dp))
                        val shuffled = Playback.shuffledFrom("album:$id")
                        IconOrb(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle, stringResource(R.string.notif_shuffle), size = 48.dp, active = shuffled) {
                            Playback.toggleShuffle(controller, "album:$id", playable)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        trackItems(tracks, onPlay = { t -> Playback.play(controller, tracks, t, source = "album:$id") })
        if (tracks.isEmpty()) item(key = "empty") { EmptyState(R.drawable.ic_album, stringResource(R.string.catalog_album_empty)) }
        val others = al.artists.filter { it.id != al.primary_artist?.id }
        if (others.isNotEmpty()) {
            item(key = "cast") {
                SectionHeader(stringResource(R.string.catalog_cast), null, Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            }
            items(others, key = { "c:" + it.id }) { c -> ArtistRowGlass(c.name, c.avatar_url, c.role) { nav.push(Screen.CatalogS(c.id)) } }
        }
    }
}

@Composable
fun StatsDetail() {
    val nav = LocalNav.current
    val controller = LocalController.current
    val p = LocalGlass.current
    var period by rememberSaveable { mutableStateOf("month") }
    var stats by remember(period) { mutableStateOf<ListeningStats?>(null) }
    var failed by remember(period) { mutableStateOf(false) }
    LaunchedEffect(period) { runCatching { Api.listeningStats(period) }.onSuccess { stats = it }.onFailure { failed = true } }
    val canAuthor = Prefs.userUrn != null && !Prefs.offline
    var author by rememberSaveable { mutableStateOf(false) }
    var mine by remember { mutableStateOf<AuthorStats?>(null) }
    var mineFailed by remember { mutableStateOf(false) }
    LaunchedEffect(author) {
        if (author && mine == null) runCatching { AuthorStatsRepo.load() }.onSuccess { mine = it; mineFailed = it == null }.onFailure { mineFailed = true }
    }
    val s = stats
    val tops = remember(s) { s?.topTracks.orEmpty().map { it.asTrack() } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = dockContentPadding()) {
        item(key = "top") { ScreenTop(stringResource(R.string.stats_title), onBack = { nav.pop() }) }
        item(key = "periods") {
            SegmentDock(
                labels = STATS_PERIODS.map { statsPeriodLabel(it) },
                selected = STATS_PERIODS.indexOf(period),
                onSelect = { if (!author) period = STATS_PERIODS[it] },
                modifier = if (author && canAuthor) Modifier.alpha(0.4f) else Modifier,
            )
            if (canAuthor) {
                Spacer(Modifier.height(8.dp))
                SegmentDock(
                    labels = listOf(stringResource(R.string.stats_mode_listener), stringResource(R.string.stats_mode_author)),
                    selected = if (author) 1 else 0,
                    onSelect = { author = it == 1 },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        if (author && canAuthor) {
            authorItems(mine, mineFailed, onPlay = { list, t -> Playback.play(controller, list, t) })
            return@LazyColumn
        }
        if (s == null) {
            item(key = "wait") { if (failed) EmptyState(R.drawable.ic_stats, stringResource(R.string.stats_failed)) else SkeletonRows(4) }
            return@LazyColumn
        }
        item(key = "hero") {
            HeroPanel {
                Column {
                    Eyebrow(statsPeriodLabel(period), color = p.text3)
                    Spacer(Modifier.height(6.dp))
                    GradientText(listenTime(s.totals.listenedMs), GlassType.hero, listOf(p.text, p.accent), maxLines = 1)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.stats_time), style = GlassType.small, color = p.text2)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassStat(s.totals.plays.toString(), stringResource(R.string.stats_plays), statsDelta(s.totals.plays, s.previous?.plays), Modifier.weight(1f))
                        GlassStat(s.totals.tracks.toString(), stringResource(R.string.stats_tracks), statsDelta(s.totals.tracks, s.previous?.tracks), Modifier.weight(1f))
                        GlassStat(s.totals.artists.toString(), stringResource(R.string.stats_artists), statsDelta(s.totals.artists, s.previous?.artists), Modifier.weight(1f))
                    }
                    if (s.timeline.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Eyebrow(stringResource(R.string.stats_timeline), color = p.text3)
                        Spacer(Modifier.height(8.dp))
                        TimelineChart(remember(s) { s.filledTimeline() }, p.accent, p.fillTop)
                    }
                    if (s.rhythm.isNotEmpty()) {
                        Spacer(Modifier.height(18.dp))
                        Eyebrow(stringResource(R.string.stats_rhythm), color = p.text3)
                        Spacer(Modifier.height(8.dp))
                        RhythmHeatmap(s.rhythm, p.accent, p.fillTop, p.text3)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        if (tops.isNotEmpty()) {
            item(key = "tth") { SectionHeader(stringResource(R.string.stats_top_tracks), null, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            trackItems(
                tops,
                onPlay = { t -> Playback.play(controller, tops, t) },
                trailing = { Spacer(Modifier.width(4.dp)) },
                note = { t ->
                    val plays = s.topTracks.firstOrNull { it.trackUrn == t.urn }?.plays ?: 0
                    stringResource(R.string.stats_plays_n, plays.toInt())
                },
            )
        }
        if (s.topArtists.isNotEmpty()) {
            item(key = "tah") { SectionHeader(stringResource(R.string.stats_top_artists), null, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            items(s.topArtists.size, key = { "ta:$it" }) { i ->
                val ar = s.topArtists[i]
                ArtistRowGlass(
                    "${i + 1}. ${ar.artistName}",
                    ar.artworkUrl,
                    stringResource(R.string.stats_plays_n, ar.plays.toInt()) + " · " + listenTime(ar.listenedMs),
                ) { ar.artistUrn?.let { nav.push(Screen.ArtistS(it)) } }
            }
        }
        if (s.totals.plays == 0L) item(key = "none") { EmptyState(R.drawable.ic_stats, stringResource(R.string.stats_empty)) }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.authorItems(mine: AuthorStats?, failed: Boolean, onPlay: (List<Track>, Track) -> Unit) {
    if (mine == null) {
        item(key = "a:wait") { if (failed) EmptyState(R.drawable.ic_stats, stringResource(R.string.stats_failed)) else SkeletonRows(4) }
        return
    }
    if (mine.tracks.isEmpty()) {
        item(key = "a:none") { EmptyState(R.drawable.ic_stats, stringResource(R.string.stats_author_empty)) }
        return
    }
    item(key = "a:hero") {
        val p = LocalGlass.current
        HeroPanel {
            Column {
                Eyebrow(stringResource(R.string.stats_author_all_time), color = p.text3)
                Spacer(Modifier.height(6.dp))
                GradientText(if (mine.counted) formatBig(mine.plays) else mine.tracks.size.toString(), GlassType.hero, listOf(p.text, p.accent), maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(stringResource(if (mine.counted) R.string.stats_author_plays else R.string.stats_tracks), style = GlassType.small, color = p.text2)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassStat(mine.tracks.size.toString(), stringResource(R.string.stats_tracks), null, Modifier.weight(1f))
                    if (mine.counted) {
                        GlassStat(formatBig(mine.likes), stringResource(R.string.stats_author_likes), null, Modifier.weight(1f))
                        GlassStat(formatBig(mine.reposts), stringResource(R.string.stats_author_reposts), null, Modifier.weight(1f))
                    }
                }
                if (mine.counted || mine.followers != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (mine.counted) GlassStat(formatBig(mine.comments), stringResource(R.string.stats_author_comments), null, Modifier.weight(1f))
                        mine.followers?.let { GlassStat(formatBig(it.toLong()), stringResource(R.string.stats_author_followers), null, Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    val tops = mine.tracks.take(50)
    item(key = "a:th") { SectionHeader(stringResource(R.string.stats_author_top), null, Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
    if (mine.counted) {
        trackItems(
            tops,
            onPlay = { t -> onPlay(tops, t) },
            trailing = { Spacer(Modifier.width(4.dp)) },
            note = { t -> stringResource(R.string.stats_author_plays_n, formatBig(t.playback_count ?: 0L)) },
        )
    } else {
        trackItems(tops, onPlay = { t -> onPlay(tops, t) })
    }
}

@Composable
private fun GlassStat(value: String, label: String, delta: Int?, modifier: Modifier) {
    val p = LocalGlass.current
    Column(modifier.glass(RoundedCornerShape(16.dp), p, shadow = 0.dp, sheen = false).padding(12.dp)) {
        Text(value, style = GlassType.section, color = p.text, maxLines = 1)
        Text(label, style = GlassType.small, color = p.text2, maxLines = 1)
        if (delta != null) {
            Text(
                (if (delta >= 0) "+" else "") + "$delta%",
                style = GlassType.mono,
                color = if (delta >= 0) p.accent else Color(0xFFFF5A5F),
            )
        }
    }
}
