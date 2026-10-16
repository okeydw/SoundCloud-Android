package com.scd.android

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController

private object GlassLyricsCache {
    val map = java.util.concurrent.ConcurrentHashMap<String, LyricsResponse>()
}

@Composable
fun GlassLyricsSheet(urn: String, controller: MediaController, onClose: () -> Unit) {
    val p = LocalGlass.current
    var data by remember(urn) { mutableStateOf(GlassLyricsCache.map[urn]) }
    var loading by remember(urn) { mutableStateOf(data == null) }
    LaunchedEffect(urn) {
        if (data != null) return@LaunchedEffect
        loading = true
        val res = runCatching { Api.lyrics(urn) }.getOrNull()
        if (res != null) GlassLyricsCache.map[urn] = res
        data = res
        loading = false
    }
    val art = NowPlaying.artworkUri
    val artColor = rememberArtColor(art)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize()) {
            ArtBackdrop(art, artColor)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconOrb(R.drawable.ic_chevron_down, stringResource(R.string.cd_close), size = 48.dp, onClick = onClose)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Eyebrow(stringResource(R.string.dock_lyrics), color = p.text3)
                        Text(NowPlaying.title, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(NowPlaying.artist, style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val synced = remember(data) { data?.syncedLrc?.let { parseLrc(it) }.orEmpty() }
                val plain = data?.plainText
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        loading -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            Spinner()
                            Text(stringResource(R.string.lyrics_loading), style = GlassType.body, color = p.text2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        synced.isNotEmpty() -> GlassSyncedLyrics(synced, controller)
                        !plain.isNullOrBlank() -> Text(
                            plain,
                            style = GlassType.section.copy(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold),
                            color = p.text,
                            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                            EmptyState(R.drawable.ic_lyrics, stringResource(R.string.lyrics_none))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassSyncedLyrics(lines: List<LyricLine>, controller: MediaController) {
    val p = LocalGlass.current
    val current by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= NowPlaying.position + 300 }.coerceAtLeast(0) }
    }
    val state = rememberLazyListState()
    LaunchedEffect(current) { state.animateScrollToItem((current - 2).coerceAtLeast(0)) }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 40.dp, bottom = 240.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        itemsIndexed(lines, key = { i, l -> "$i:${l.timeMs}" }) { i, line ->
            val active = i == current
            val scale by animateFloatAsState(if (active) 1.02f else 1f, tween(420, easing = EaseApple), label = "lyricScale")
            val alpha by animateFloatAsState(
                when {
                    active -> 1f
                    i < current -> 0.28f
                    else -> 0.5f
                },
                tween(420),
                label = "lyricAlpha",
            )
            val shift by animateFloatAsState(if (active) 0f else if (i < current) -4f else 4f, tween(420, easing = EaseApple), label = "lyricShift")
            Text(
                line.text.ifBlank { "♪" },
                style = GlassType.section.copy(
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Black,
                    shadow = if (active) Shadow(p.glowStrong, blurRadius = 28f) else null,
                ),
                color = p.text,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        translationY = shift.dp.toPx()
                    }
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { controller.seekTo(line.timeMs) },
            )
        }
    }
}

@Composable
fun GlassSettingsSkin(content: @Composable () -> Unit) {
    val skin = remember {
        SettingsSkin(
            header = { title, back -> ScreenTop(title, onBack = back) },
            panel = { inner -> SettingsGlassPanel(inner) },
            row = { icon, title, subtitle, onClick -> SettingsGlassRow(icon, title, subtitle, onClick) },
            equalizer = { GlassEqualizer(onClose = null) },
            footerAlpha = false,
        )
    }
    CompositionLocalProvider(LocalSettingsSkin provides skin, content = content)
}

@Composable
private fun SettingsGlassPanel(content: @Composable () -> Unit) {
    val p = LocalGlass.current
    Column(
        Modifier
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(24.dp), p, shadow = 0.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsGlassRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).glass(RoundedCornerShape(13.dp), p, shadow = 0.dp, tint = p.glow), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = p.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = GlassType.rowTitle, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(1.dp))
            Text(subtitle, style = GlassType.small, color = p.text2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = p.text3, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun SettingsInsetsColumn(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.navigationBars)) { content() }
}
