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
                        Icon(painterResource(R.drawable.ic_chevron_down), iconLabel(R.drawable.ic_chevron_down))
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

@Composable
fun LyricsSheet(urn: String, title: String, controller: MediaController, onClose: () -> Unit) {
    val lyrics = rememberLyrics(urn)
    val data = lyrics.data
    val loading = lyrics.loading


    SheetFrame(title, onClose) {
        Column(Modifier.fillMaxSize()) {
            if (data?.source == "self_gen" && data.hasText) Text(stringResource(R.string.lyrics_generated), modifier = Modifier.padding(16.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) {
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
                            stringResource(if (data?.status == "pending") R.string.lyrics_pending else R.string.lyrics_loading),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    lyrics.failed || data?.status == "pending" -> Column(
                        Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(if (lyrics.failed) R.string.lyrics_failed else R.string.lyrics_pending), textAlign = TextAlign.Center)
                        androidx.compose.material3.TextButton(onClick = lyrics::retry) { Text(stringResource(R.string.retry_action)) }
                    }
                    synced.any { it.text.isNotBlank() } -> SyncedLyrics(synced, controller)
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
