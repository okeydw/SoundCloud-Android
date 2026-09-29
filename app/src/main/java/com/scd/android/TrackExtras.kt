package com.scd.android

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController

@Composable
private fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_chevron_down), null)
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { content() }
            }
        }
    }
}

private object LyricsCache {
    val map = java.util.concurrent.ConcurrentHashMap<String, LyricsResponse>()
}

@Composable
fun LyricsSheet(urn: String, title: String, controller: MediaController, onClose: () -> Unit) {
    var data by remember(urn) { mutableStateOf(LyricsCache.map[urn]) }
    var loading by remember(urn) { mutableStateOf(data == null) }

    LaunchedEffect(urn) {
        if (data != null) return@LaunchedEffect
        loading = true
        val res = Api.lyrics(urn)
        if (res != null) LyricsCache.map[urn] = res
        data = res
        loading = false
    }

    SheetFrame(title, onClose) {
        val synced = remember(data) { data?.syncedLrc?.let { parseLrc(it) }.orEmpty() }
        val plain = data?.plainText
        when {
            loading -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.lyrics_loading),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            synced.isNotEmpty() -> SyncedLyrics(synced, controller)
            !plain.isNullOrBlank() -> Text(
                plain,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            )
            else -> Text(
                stringResource(R.string.lyrics_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }
    }
}

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, controller: MediaController) {
    val position = NowPlaying.position
    val current = lines.indexOfLast { it.timeMs <= position + 300 }.coerceAtLeast(0)
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        state.animateScrollToItem((current - 3).coerceAtLeast(0))
    }
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                line.text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (i < current) 0.35f else 0.6f)
                },
                modifier = Modifier.fillMaxWidth().clickable { controller.seekTo(line.timeMs) },
            )
        }
    }
}

@Composable
fun EqualizerPanel() {
    val info = AudioFx.info
    if (info == null) {
        Text(
            stringResource(R.string.eq_unavailable),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    val levels = if (Prefs.eqPreset >= 0) {
        AudioFx.levels(Prefs.eqPreset)
    } else {
        val parsed = AudioFx.parseBands(Prefs.eqBands)
        List(info.bands) { parsed.getOrNull(it) ?: 0 }
    }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.equalizer), Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Switch(
            checked = Prefs.eqEnabled,
            onCheckedChange = { Prefs.changeEq(it, Prefs.eqPreset, Prefs.eqBands) },
        )
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterChip(
            selected = Prefs.eqPreset < 0,
            onClick = { Prefs.changeEq(true, -1, levels.joinToString(",")) },
            label = { Text(stringResource(R.string.eq_custom)) },
        )
        info.presets.forEachIndexed { i, name ->
            FilterChip(
                selected = Prefs.eqPreset == i,
                onClick = { Prefs.changeEq(true, i, Prefs.eqBands) },
                label = { Text(name) },
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    levels.forEachIndexed { band, level ->
        val hz = info.centerFreqsHz.getOrNull(band) ?: 0
        val label = if (hz >= 1000) "${hz / 1000}k" else "$hz"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
            )
            Slider(
                value = level.toFloat(),
                onValueChange = { v ->
                    val next = levels.toMutableList().also { it[band] = v.toInt() }
                    Prefs.changeEq(true, -1, next.joinToString(","))
                },
                valueRange = info.minLevel.toFloat()..info.maxLevel.toFloat(),
                modifier = Modifier.weight(1f),
            )
            Text(
                "%+.1f".format(level / 100f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.End,
            )
        }
    }
    TextButton(onClick = { Prefs.changeEq(Prefs.eqEnabled, -1, List(info.bands) { 0 }.joinToString(",")) }) {
        Text(stringResource(R.string.color_reset))
    }
}
