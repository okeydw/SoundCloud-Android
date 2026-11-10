package com.scd.android

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException

private val lyricsMemory = linkedMapOf<String, LyricsResponse>()

class LyricsState(initial: LyricsResponse?) {
    var data by mutableStateOf(initial)
        internal set
    var loading by mutableStateOf(initial == null)
        internal set
    var failed by mutableStateOf(false)
        internal set
    internal var attempt by mutableIntStateOf(0)
    fun retry() { attempt++ }
}

@Composable
fun rememberLyrics(urn: String): LyricsState {
    val state = remember(urn) { LyricsState(lyricsMemory[urn]) }
    LaunchedEffect(urn, state.attempt) {
        if (state.data?.hasText == true) return@LaunchedEffect
        state.loading = true
        state.failed = false
        try {
            state.data = awaitLyrics(fetch = { Api.lyrics(urn) }, onPending = { state.data = it })
            state.data?.takeIf { it.hasText }?.let {
                if (lyricsMemory.size >= 100) lyricsMemory.remove(lyricsMemory.keys.first())
                lyricsMemory[urn] = it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            state.failed = true
        } finally {
            state.loading = false
        }
    }
    return state
}
