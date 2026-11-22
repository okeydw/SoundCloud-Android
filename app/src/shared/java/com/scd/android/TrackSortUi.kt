package com.scd.android

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class TrackSortState(initial: TrackOrder = TrackOrder()) {
    var order by mutableStateOf(initial)
    var busy by mutableStateOf(false)
    var failed by mutableStateOf(false)
    private var metadata by mutableStateOf<Map<String, Track>>(emptyMap())
    private val checked = HashSet<String>()

    fun tracks(tracks: List<Track>, newestFirst: Boolean = false): List<Track> = order.tracks(tracks.map { old ->
        val fresh = metadata[old.urn] ?: return@map old
        old.copy(
            playback_count = fresh.playback_count ?: old.playback_count,
            likes_count = fresh.likes_count ?: old.likes_count,
            favoritings_count = fresh.favoritings_count ?: old.favoritings_count,
            reposts_count = fresh.reposts_count ?: old.reposts_count,
            release_date = fresh.release_date ?: old.release_date,
            created_at = fresh.created_at ?: old.created_at,
            enrichment = old.enrichment?.let { it.copy(release_date = fresh.enrichment?.release_date ?: it.release_date) } ?: fresh.enrichment,
        )
    }, newestFirst)

    suspend fun loadMetadata(tracks: List<Track>, mode: TrackSort) {
        if (Prefs.offline) return
        val missing = tracks.filter { track ->
            isScUrn(track.urn) && track.urn !in checked && when (mode) {
                TrackSort.RELEASED -> releaseDay(track.enrichment?.release_date) == null && releaseDay(track.release_date) == null && releaseDay(track.created_at) == null
                TrackSort.PLAYS -> track.playback_count == null
                TrackSort.LIKES -> track.favoritings_count == null && track.likes_count == null
                TrackSort.REPOSTS -> track.reposts_count == null
                else -> false
            }
        }
        if (missing.isEmpty()) return
        val fresh = Api.tracksByIds(missing.map { it.urn })
        currentCoroutineContext().ensureActive()
        check(fresh != null) { "Track metadata is unavailable" }
        metadata = metadata + fresh.associateBy { it.urn }
        checked += missing.map { it.urn }
    }
}

@Composable
fun rememberTrackSort(key: String): TrackSortState = rememberSaveable(key, saver = listSaver(
    save = { listOf(it.order.mode.ordinal, if (it.order.reversed) 1 else 0) },
    restore = { TrackSortState(TrackOrder(TrackSort.entries[it[0]], it[1] != 0)) },
)) { TrackSortState() }

@Composable
fun TrackSortBar(
    state: TrackSortState,
    playlist: Boolean = false,
    loading: Boolean = false,
    options: List<TrackSort> = TrackSort.entries,
    actionScope: kotlinx.coroutines.CoroutineScope? = null,
    onReorder: (suspend () -> Unit)? = null,
    prepare: suspend (TrackSort) -> Unit = {},
) {
    val localScope = rememberCoroutineScope()
    val scope = actionScope ?: localScope
    var expanded by remember { mutableStateOf(false) }
    fun choose(order: TrackOrder) {
        expanded = false
        if (state.busy || loading) return
        state.failed = false
        if (!order.active) {
            state.order = order
            return
        }
        scope.launch {
            state.busy = true
            try {
                prepare(order.mode)
                state.order = order
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                state.failed = true
            } finally {
                state.busy = false
            }
        }
    }
    Column(horizontalAlignment = Alignment.End) {
        Box {
            SortButton(
                description = stringResource(sortLabel(state.order.mode, playlist)) +
                    if (state.order.reversed) ", " + stringResource(R.string.track_sort_reverse) else "",
                onClick = { expanded = true },
                enabled = !state.busy && !loading,
            ) {
                if (state.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(painterResource(R.drawable.ic_sort), stringResource(R.string.track_sort_label), Modifier.size(18.dp))

            }
            DropdownMenu(expanded, onDismissRequest = { expanded = false }, shape = RoundedCornerShape(22.dp), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Text(stringResource(R.string.track_sort_label), Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onReorder != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.reorder_title)) },
                        leadingIcon = { Icon(painterResource(R.drawable.ic_drag), null, Modifier.size(18.dp)) },
                        enabled = !loading && !state.busy,
                        onClick = {
                            expanded = false
                            scope.launch {
                                state.busy = true
                                state.failed = false
                                try {
                                    onReorder()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    state.failed = true
                                } finally {
                                    state.busy = false
                                }
                            }
                        },
                    )
                }
                options.forEach { mode ->
                    if (mode == TrackSort.RELEASED || mode == TrackSort.TITLE) HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    DropdownMenuItem(
                        modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(14.dp)).then(if (state.order.mode == mode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) else Modifier),
                        text = { Text(stringResource(sortLabel(mode, playlist)), style = MaterialTheme.typography.bodyMedium) },
                        onClick = { choose(TrackOrder(mode)) },
                        enabled = !loading && !state.busy,
                        leadingIcon = { Icon(painterResource(sortIcon(mode)), null, Modifier.size(18.dp)) },
                        trailingIcon = { if (state.order.mode == mode) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) },
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.track_sort_reverse)) },
                    onClick = { choose(state.order.copy(reversed = !state.order.reversed)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_sort), null, Modifier.size(18.dp)) },
                    trailingIcon = { if (state.order.reversed) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(16.dp)) },
                    enabled = !loading && !state.busy,
                )
            }
        }
        if (state.failed) Text(stringResource(R.string.track_sort_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

private fun sortLabel(mode: TrackSort, playlist: Boolean): Int = when (mode) {
    TrackSort.ORIGINAL -> if (playlist) R.string.track_sort_playlist else R.string.track_sort_original
    TrackSort.RECENT -> R.string.track_sort_recent
    TrackSort.RELEASED -> R.string.track_sort_released
    TrackSort.PLAYS -> R.string.track_sort_plays
    TrackSort.LIKES -> R.string.track_sort_likes
    TrackSort.REPOSTS -> R.string.track_sort_reposts
    TrackSort.TITLE -> R.string.track_sort_title
    TrackSort.ARTIST -> R.string.track_sort_artist
    TrackSort.DURATION -> R.string.track_sort_duration
}

private fun sortIcon(mode: TrackSort): Int = when (mode) {
    TrackSort.ORIGINAL -> R.drawable.ic_music
    TrackSort.RECENT, TrackSort.RELEASED -> R.drawable.ic_history
    TrackSort.PLAYS -> R.drawable.ic_play
    TrackSort.LIKES -> R.drawable.ic_heart
    TrackSort.REPOSTS -> R.drawable.ic_repeat
    TrackSort.TITLE -> R.drawable.ic_sort
    TrackSort.ARTIST -> R.drawable.ic_user
    TrackSort.DURATION -> R.drawable.ic_timer
}
