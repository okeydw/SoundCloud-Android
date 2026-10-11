package com.scd.android

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
fun greetingText(): String {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    return stringResource(
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..17 -> R.string.greeting_day
            in 18..22 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
}

fun shelfTitles(id: String): Pair<Int, Int> = when (id) {
    "top_artists" -> R.string.cluster_top_artists to R.string.cluster_top_artists_desc
    "fresh_drops" -> R.string.cluster_fresh_drops to R.string.cluster_fresh_drops_desc
    "same_vibe" -> R.string.cluster_same_vibe to R.string.cluster_same_vibe_desc
    "adjacent" -> R.string.cluster_adjacent to R.string.cluster_adjacent_desc
    "deep_cuts" -> R.string.cluster_deep_cuts to R.string.cluster_deep_cuts_desc
    "discover" -> R.string.cluster_discover to R.string.cluster_discover_desc
    else -> R.string.cluster_for_you to R.string.cluster_for_you_desc
}

@Composable
fun FlowScreen() {
    val nav = LocalNav.current
    val controller = LocalController.current
    val offline = Prefs.offline

    LaunchedEffect(Unit) {
        if (Library.soundprint.isEmpty()) Library.loadSoundprint()
    }
    LaunchedEffect(offline) {
        if (!offline && Recommendations.stale) Recommendations.refresh()
    }

    LazyColumn(Modifier.fillMaxWidth(), contentPadding = dockContentPadding()) {
        item(key = "mast", contentType = "mast") { Masthead() }
        if (Library.soundprint.isNotEmpty()) {
            item(key = "print", contentType = "print") {
                SoundprintGlass(
                    liked = Library.soundprint,
                    onGenre = { g -> nav.push(Screen.GenreS(g.key, g.label)) },
                    onPlay = {
                        val mix = Library.soundprint.filter(Playback::playable).shuffled()
                        if (mix.isNotEmpty()) Playback.play(controller, mix, mix.first())
                    },
                )
            }
        }
        if (!offline) {
            item(key = "deck", contentType = "deck") { EstuaryDeck(onOpenWave = { nav.push(Screen.WaveS) }) }
            if (Recommendations.shelves.isEmpty() && Recommendations.loading) {
                item(key = "shelf:skeleton", contentType = "skeleton") { SkeletonRows(4) }
            }
            Recommendations.shelves.forEach { shelf ->
                item(key = "shelf:" + shelf.id, contentType = "shelf") {
                    ShelfRow(shelf, onAll = { nav.push(Screen.ShelfS(shelf.id)) })
                }
            }
        } else {
            val saved = Downloads.tracks()
            if (saved.isNotEmpty()) {
                item(key = "offline", contentType = "shelf") {
                    ShelfRow(Shelf("offline", saved.take(30)), title = stringResource(R.string.downloads), subtitle = null, onAll = null)
                }
            } else {
                item(key = "empty") { EmptyState(R.drawable.ic_no_wifi, stringResource(R.string.no_internet)) }
            }
        }
    }
}

@Composable
private fun Masthead() {
    val p = LocalGlass.current
    val greeting = greetingText()
    val name = Prefs.username ?: "SoundCloud"
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LivePill(stringResource(R.string.tab_flow), live = NowPlaying.isPlaying)
                    Spacer(Modifier.width(10.dp))
                    Eyebrow(stringResource(R.string.river_personal), color = p.text3)
                }
                Spacer(Modifier.height(16.dp))
                val first = greeting.substringBefore(' ')
                val rest = greeting.removePrefix(first)
                val accentBrush = Brush.linearGradient(listOf(p.accentHover, Color(0xFFFF4FA3).copy(alpha = 0.95f)))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = p.text)) { append(first) }
                        withStyle(SpanStyle(brush = accentBrush)) { append("$rest,") }
                        append("\n")
                        withStyle(SpanStyle(color = p.text)) { append(name) }
                    },
                    style = GlassType.hero,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(58.dp)
                    .shadow(20.dp, CircleShape, clip = false, ambientColor = p.accent, spotColor = p.accent)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(p.accent, p.glowStrong)))
                    .border(1.dp, p.accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val avatar = Prefs.avatarUrl
                if (avatar != null) {
                    AsyncImage(
                        model = Api.artworkUrl(avatar, "t120x120"),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Icon(painterResource(R.drawable.ic_user), null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.wave_tagline), style = GlassType.body, color = p.text2)
    }
}

@Composable
fun SoundprintGlass(liked: List<Track>, onGenre: (GenreShare) -> Unit, onPlay: () -> Unit) {
    val p = LocalGlass.current
    val genres = remember(liked) { topGenres(liked, 7) }
    if (genres.isEmpty()) return
    val maxShare = genres.maxOf { it.share }.coerceAtLeast(0.01f)
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), p)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_equalizer), null, tint = p.accent, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(8.dp))
            Eyebrow(stringResource(R.string.soundprint_short), Modifier.weight(1f))
            Text(
                stringResource(R.string.play_your_sound) + " ›",
                style = GlassType.small.copy(fontWeight = FontWeight.SemiBold),
                color = p.text,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onPlay).padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(132.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            genres.forEach { g ->
                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp)).clickable { onGenre(g) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    val h = 24.dp + 76.dp * (g.share / maxShare)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(h)
                            .softShadow(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp), 10.dp, g.color.copy(alpha = 0.5f))
                            .clip(RoundedCornerShape(topStart = 7.dp, topEnd = 7.dp))
                            .background(Brush.verticalGradient(listOf(g.color, g.color.copy(alpha = 0.12f)))),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(g.label, style = GlassType.small.copy(fontSize = 11.sp), color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun EstuaryDeck(onOpenWave: () -> Unit) {
    val p = LocalGlass.current
    val controller = LocalController.current
    val scope = rememberCoroutineScope()
    val wave = WaveState.tracks
    val playingWave = NowPlaying.urn != null && WaveFeedback.isOwned(NowPlaying.urn)
    val head = wave.firstOrNull()
    val urn = if (playingWave) NowPlaying.urn else head?.urn
    val title = if (playingWave) NowPlaying.title else head?.title
    val artist = if (playingWave) NowPlaying.artist else head?.user?.username
    val art: Any? = if (playingWave) NowPlaying.artworkUri else head?.artModel("t250x250")
    val genre = (if (playingWave) NowPlaying.genre else head?.genre)?.takeIf { it.isNotBlank() }
    val samples = rememberWaveSamples(urn, if (playingWave) NowPlaying.waveformUrl else head?.waveform_url, 64)
    val duration = if (playingWave) NowPlaying.duration else head?.displayDuration ?: 0L

    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(26.dp), p, tint = p.glow)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveDot(live = NowPlaying.isPlaying && playingWave)
            Spacer(Modifier.width(8.dp))
            Eyebrow(stringResource(R.string.tab_flow), color = p.text)
            Spacer(Modifier.width(12.dp))
            EqBars(NowPlaying.isPlaying && playingWave, p.accent, Modifier.width(14.dp).height(12.dp), bars = 3)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.river_adapts), style = GlassType.small, color = p.text2, maxLines = 1)
        }
        Spacer(Modifier.height(14.dp))
        if (title == null) {
            Text(
                stringResource(if (WaveState.loading) R.string.wave_loading else R.string.wave_empty),
                style = GlassType.body,
                color = p.text2,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenWave),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(art, Modifier.size(58.dp), corner = 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = GlassType.rowTitle.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold), color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(artist ?: "", style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (genre != null) {
                            Spacer(Modifier.width(8.dp))
                            GenreDot(genreColor(genre.lowercase()), 6.dp)
                            Spacer(Modifier.width(5.dp))
                            Text(genre, style = GlassType.small, color = p.text2, maxLines = 1)
                        }
                    }
                }
                val liked = urn != null && Likes.isLiked(urn)
                IconOrb(
                    if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart,
                    stringResource(if (liked) R.string.notif_unlike else R.string.notif_like),
                    size = 44.dp,
                    active = liked,
                ) {
                    val t = if (playingWave) controller?.currentMediaItem?.toTrackOrNull() else head
                    if (t != null) scope.launch { Likes.toggle(t) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Waveform(
                samples = samples,
                progress = {
                    if (playingWave && NowPlaying.duration > 0) NowPlaying.position.toFloat() / NowPlaying.duration else 0f
                },
                height = 48.dp,
                reflection = LocalFx.current.reflection,
                onSeek = if (playingWave) { f -> controller?.let { c -> if (c.duration > 0) c.seekTo((f * c.duration).toLong()) } } else null,
            )
            Row(Modifier.fillMaxWidth()) {
                PositionText(playingWave, GlassType.mono, p.text)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(duration), style = GlassType.mono, color = p.text3)
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassDivider()
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentButton(
                stringResource(R.string.play_wave),
                R.drawable.ic_play,
                Modifier.weight(1f),
            ) {
                when {
                    playingWave -> controller?.play()
                    wave.isNotEmpty() -> Playback.play(controller, wave, wave.first(), autoContinue = true)
                    else -> WaveState.load(false)
                }
            }
            Spacer(Modifier.width(10.dp))
            GlassChip(
                stringResource(R.string.wave_fresh),
                dot = if (Prefs.waveFresh) p.accent else p.text3,
                active = Prefs.waveFresh,
                onClick = {
                    Prefs.changeWaveFresh(!Prefs.waveFresh)
                    WaveState.refresh(controller)
                    Recommendations.refresh()
                },
            )
            Spacer(Modifier.width(8.dp))
            RefreshOrb(spinning = WaveState.loading) { WaveState.refresh(controller) }
        }
    }
    Spacer(Modifier.height(22.dp))
}

@Composable
fun RefreshOrb(spinning: Boolean, onClick: () -> Unit) {
    val angle = if (spinning) {
        rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a").value
    } else 0f
    Box(Modifier.graphicsLayer { rotationZ = angle }) {
        IconOrb(R.drawable.ic_refresh, stringResource(R.string.wave_refresh), size = 44.dp, iconSize = 18.dp, onClick = onClick)
    }
}

@Composable
fun ShelfRow(
    shelf: Shelf,
    title: String? = null,
    subtitle: String? = null,
    onAll: (() -> Unit)?,
) {
    val controller = LocalController.current
    val (titleRes, descRes) = shelfTitles(shelf.id)
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        SectionHeader(
            title = title ?: stringResource(titleRes),
            subtitle = if (title != null) subtitle else stringResource(descRes),
            modifier = Modifier.padding(horizontal = 20.dp),
            action = if (onAll != null) stringResource(R.string.see_all) else null,
            onAction = onAll,
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.tracks, key = { it.urn }, contentType = { "card" }) { t ->
                TrackCard(t, onClick = { Playback.play(controller, shelf.tracks, t, autoContinue = true) })
            }
        }
    }
}
