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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.cd_back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
private fun CenterSpinner() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

@Composable
private fun PersonRow(name: String, subtitle: String?, avatar: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Api.artworkUrl(avatar, "t120x120"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

private enum class CatalogTab { Tracks, Albums, Related, About }

@Composable
fun CatalogArtistScreen(id: String, onBack: () -> Unit, onPlay: (List<Track>, Track) -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    val controller = LocalMediaController.current
    var artist by remember(id) { mutableStateOf<CatalogArtist?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    var albums by remember(id) { mutableStateOf<List<ArtistAlbum>>(emptyList()) }
    var tab by remember(id) { mutableStateOf(CatalogTab.Tracks) }
    var sort by remember(id) { mutableStateOf("popular") }
    var tracks by remember(id, sort) { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember(id, sort) { mutableIntStateOf(0) }
    var hasMore by remember(id, sort) { mutableStateOf(false) }
    var loading by remember(id, sort) { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }

    fun load(p: Int) {
        if (loading) return
        loading = true
        scope.launch {
            runCatching { CatalogRepo.tracksPage(id, sort, p) }.onSuccess { (list, more) ->
                tracks = if (p == 0) list else (tracks + list).distinctBy { it.urn }
                page = p
                hasMore = more
            }
            loading = false
        }
    }

    LaunchedEffect(id) {
        runCatching { Api.catalogArtist(id) }.onSuccess { artist = it }.onFailure { failed = true }
        runCatching { Api.catalogArtistAlbums(id) }.onSuccess { albums = it }
    }
    LaunchedEffect(id, sort) { load(0) }

    val a = artist
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(a?.name ?: "…", onBack) {
            if (a != null && Prefs.userUrn != null) {
                val blocked = BlockedArtists.isBlocked("artist", a.id)
                IconButton(onClick = {
                    if (blocked) scope.launch { BlockedArtists.unblock("artist", a.id) } else blocking = true
                }) {
                    Icon(
                        painterResource(R.drawable.ic_block),
                        stringResource(if (blocked) R.string.unblock else R.string.block),
                        tint = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (a == null) {
            if (failed) {
                Text(stringResource(R.string.catalog_failed), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
            } else {
                CenterSpinner()
            }
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = a.avatar_url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                        val meta = listOfNotNull(
                            a.country?.takeIf { it.isNotBlank() },
                            stringResource(R.string.tracks_count, a.track_count),
                            if (a.album_count > 0) stringResource(R.string.catalog_albums_count, a.album_count) else null,
                        ).joinToString(" · ")
                        Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            val list = (tracks.ifEmpty { a.popular_tracks }).filter(Playback::playable)
                            if (list.isNotEmpty()) Playback.play(controller, list, list.first(), source = "catalog:$id")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(painterResource(R.drawable.ic_play), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.play_all))
                    }
                    Spacer(Modifier.width(8.dp))
                    val shuffled = Playback.shuffledFrom("catalog:$id")
                    OutlinedButton(onClick = {
                        Playback.toggleShuffle(controller, "catalog:$id", tracks.ifEmpty { a.popular_tracks })
                    }) {
                        Icon(
                            painterResource(if (shuffled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle),
                            stringResource(R.string.notif_shuffle),
                            tint = if (shuffled) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                val main = a.mainScUsers.firstOrNull()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (main != null) {
                        AssistChip(
                            onClick = { NavEvents.openScUser(main) },
                            label = { Text(stringResource(R.string.catalog_sc_profile)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_user), null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                    SocialLinks(a.socials) { label, open -> AssistChip(onClick = open, label = { Text(label) }) }
                }
                Spacer(Modifier.height(6.dp))
                val tabs = buildList {
                    add(CatalogTab.Tracks)
                    if (albums.isNotEmpty()) add(CatalogTab.Albums)
                    if (a.related_artists.isNotEmpty()) add(CatalogTab.Related)
                    if (!a.bio.isNullOrBlank()) add(CatalogTab.About)
                }
                if (tabs.size > 1) {
                    TabRow(selectedTabIndex = tabs.indexOf(tab).coerceAtLeast(0)) {
                        tabs.forEach { t ->
                            Tab(selected = tab == t, onClick = { tab = t }, text = {
                                Text(
                                    stringResource(
                                        when (t) {
                                            CatalogTab.Tracks -> R.string.tracks
                                            CatalogTab.Albums -> R.string.catalog_albums
                                            CatalogTab.Related -> R.string.catalog_related
                                            CatalogTab.About -> R.string.catalog_about
                                        },
                                    ),
                                    maxLines = 1,
                                )
                            })
                        }
                    }
                }
            }
            when (tab) {
                CatalogTab.Tracks -> {
                    item {
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("popular" to R.string.catalog_popular, "recent" to R.string.catalog_recent).forEach { (key, label) ->
                                FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(stringResource(label)) })
                            }
                        }
                    }
                    items(tracks, key = { "t:" + it.urn }) { t -> TrackRow(track = t, onClick = { Playback.play(controller, tracks, t, source = "catalog:$id") }) }
                    if (loading && tracks.isEmpty()) item { CenterSpinner() }
                    if (hasMore && tracks.isNotEmpty()) {
                        item {
                            LaunchedEffect(tracks.size) { load(page + 1) }
                            CenterSpinner()
                        }
                    }
                    if (!loading && tracks.isEmpty()) {
                        item { Text(stringResource(R.string.artist_no_tracks), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                    }
                }
                CatalogTab.Albums -> albumItems(albums)
                CatalogTab.Related -> items(a.related_artists, key = { "r:" + it.id }) { r ->
                    PersonRow(r.name, r.country, r.avatar_url) { NavEvents.openCatalog(r.id) }
                }
                CatalogTab.About -> item {
                    Text(a.bio.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (blocking && a != null) {
        BlockConfirmDialog(listOf(BlockedArtists.artistTarget(a)), onDismiss = { blocking = false })
    }
}

private fun LazyListScope.albumItems(albums: List<ArtistAlbum>) {
    items(albums, key = { "a:" + it.id }) { al ->
        Row(
            Modifier.fillMaxWidth().clickable { NavEvents.openAlbum(al.id) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArtwork(al.cover_url, 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(al.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    listOfNotNull(stringResource(albumKindRes(al.type)), al.release_year?.toString()).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
fun AlbumScreen(id: String, onBack: () -> Unit, onPlay: (List<Track>, Track) -> Unit) {
    BackHandler(onBack = onBack)
    var album by remember(id) { mutableStateOf<CatalogAlbum?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    LaunchedEffect(id) { runCatching { Api.catalogAlbum(id) }.onSuccess { album = it }.onFailure { failed = true } }
    val al = album
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(al?.title ?: "…", onBack)
        if (al == null) {
            if (failed) Text(stringResource(R.string.catalog_failed), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) else CenterSpinner()
            return@Column
        }
        val tracks = al.tracks.filter { !it.urn.startsWith("wanted:") }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TrackArtwork(al.cover_url, 120.dp, 10.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(al.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 3)
                        Text(
                            listOfNotNull(stringResource(albumKindRes(al.type)), al.release_year?.toString(), stringResource(R.string.tracks_count, tracks.size)).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        al.primary_artist?.let { pa ->
                            TextButton(onClick = { NavEvents.openCatalog(pa.id) }) { Text(pa.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
                Button(
                    enabled = tracks.any(Playback::playable),
                    onClick = {
                        val list = tracks.filter(Playback::playable)
                        if (list.isNotEmpty()) onPlay(list, list.first())
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.play_all)) }
                Spacer(Modifier.height(8.dp))
            }
            items(tracks, key = { "t:" + it.urn }) { t -> TrackRow(track = t, onClick = { onPlay(tracks, t) }) }
            if (tracks.isEmpty()) {
                item { Text(stringResource(R.string.catalog_album_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            val others = al.artists.filter { it.id != al.primary_artist?.id }
            if (others.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.catalog_cast)) }
                items(others, key = { "c:" + it.id }) { c -> PersonRow(c.name, c.role, c.avatar_url) { NavEvents.openCatalog(c.id) } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AuthorStatsList(mine: AuthorStats?, failed: Boolean, onPlay: (List<Track>, Track) -> Unit) {
    if (mine == null) {
        if (failed) Text(stringResource(R.string.stats_failed), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) else CenterSpinner()
        return
    }
    if (mine.tracks.isEmpty()) {
        Text(stringResource(R.string.stats_author_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
        return
    }
    val tops = mine.tracks.take(50)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.stats_author_all_time), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine.counted) StatTile(formatBig(mine.plays), stringResource(R.string.stats_author_plays), null, Modifier.weight(1f))
                StatTile(mine.tracks.size.toString(), stringResource(R.string.stats_tracks), null, Modifier.weight(1f))
            }
            if (mine.counted) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(formatBig(mine.likes), stringResource(R.string.stats_author_likes), null, Modifier.weight(1f))
                    StatTile(formatBig(mine.reposts), stringResource(R.string.stats_author_reposts), null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(formatBig(mine.comments), stringResource(R.string.stats_author_comments), null, Modifier.weight(1f))
                    mine.followers?.let { StatTile(formatBig(it.toLong()), stringResource(R.string.stats_author_followers), null, Modifier.weight(1f)) }
                }
            }
            SectionTitle(stringResource(R.string.stats_author_top))
        }
        items(tops.size, key = { "at:" + tops[it].urn }) { i ->
            val t = tops[i]
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(tops, t) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                TrackArtwork(Api.artworkUrl(t.artwork_url, "t120x120"), 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(formatDuration(t.displayDuration), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                if (mine.counted) {
                    Text(stringResource(R.string.stats_author_plays_n, formatBig(t.playback_count ?: 0L)), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun StatsScreen(onBack: () -> Unit, onPlay: (List<Track>, Track) -> Unit) {
    BackHandler(onBack = onBack)
    var period by remember { mutableStateOf("month") }
    var stats by remember(period) { mutableStateOf<ListeningStats?>(null) }
    var failed by remember(period) { mutableStateOf(false) }
    LaunchedEffect(period) {
        runCatching { Api.listeningStats(period) }.onSuccess { stats = it }.onFailure { failed = true }
    }
    val canAuthor = Prefs.userUrn != null && !Prefs.offline
    var author by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<AuthorStats?>(null) }
    var mineFailed by remember { mutableStateOf(false) }
    LaunchedEffect(author) {
        if (author && mine == null) runCatching { AuthorStatsRepo.load() }.onSuccess { mine = it; mineFailed = it == null }.onFailure { mineFailed = true }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(R.string.stats_title), onBack)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STATS_PERIODS.forEach { p -> FilterChip(selected = period == p && !author, enabled = !author, onClick = { period = p }, label = { Text(statsPeriodLabel(p)) }) }
        }
        if (canAuthor) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !author, onClick = { author = false }, label = { Text(stringResource(R.string.stats_mode_listener)) })
                FilterChip(selected = author, onClick = { author = true }, label = { Text(stringResource(R.string.stats_mode_author)) })
            }
        }
        if (author && canAuthor) {
            AuthorStatsList(mine, mineFailed, onPlay)
            return@Column
        }
        val s = stats
        if (s == null) {
            if (failed) Text(stringResource(R.string.stats_failed), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp)) else CenterSpinner()
            return@Column
        }
        val tops = s.topTracks.map { it.asTrack() }
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(listenTime(s.totals.listenedMs), stringResource(R.string.stats_time), statsDelta(s.totals.listenedMs, s.previous?.listenedMs), Modifier.weight(1f))
                    StatTile(s.totals.plays.toString(), stringResource(R.string.stats_plays), statsDelta(s.totals.plays, s.previous?.plays), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(s.totals.tracks.toString(), stringResource(R.string.stats_tracks), statsDelta(s.totals.tracks, s.previous?.tracks), Modifier.weight(1f))
                    StatTile(s.totals.artists.toString(), stringResource(R.string.stats_artists), statsDelta(s.totals.artists, s.previous?.artists), Modifier.weight(1f))
                }
                if (s.timeline.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.stats_timeline))
                    TimelineChart(remember(s) { s.filledTimeline() }, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.surfaceVariant)
                }
                if (s.rhythm.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.stats_rhythm))
                    RhythmHeatmap(
                        s.rhythm,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (tops.isNotEmpty()) SectionTitle(stringResource(R.string.stats_top_tracks))
            }
            items(s.topTracks.size, key = { "tt:" + s.topTracks[it].trackUrn }) { i ->
                val t = s.topTracks[i]
                Row(
                    Modifier.fillMaxWidth().clickable { onPlay(tops, tops[i]) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    TrackArtwork(Api.artworkUrl(t.artworkUrl, "t120x120"), 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(t.artistName, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    Text(stringResource(R.string.stats_plays_n, t.plays.toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (s.topArtists.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.stats_top_artists)) }
                items(s.topArtists.size, key = { "ta:$it" }) { i ->
                    val ar = s.topArtists[i]
                    PersonRow(
                        "${i + 1}. ${ar.artistName}",
                        stringResource(R.string.stats_plays_n, ar.plays.toInt()) + " · " + listenTime(ar.listenedMs),
                        ar.artworkUrl,
                    ) { ar.artistUrn?.let { NavEvents.openUser(it) } }
                }
            }
            if (s.totals.plays == 0L) {
                item { Text(stringResource(R.string.stats_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, delta: Int?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(14.dp),
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
            if (delta != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (delta >= 0) "+" else "") + "$delta%",
                    color = if (delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
