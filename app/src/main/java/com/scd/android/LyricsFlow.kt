package com.scd.android

import kotlinx.coroutines.delay

val LyricsResponse.hasText: Boolean
    get() = !plainText.isNullOrBlank() || syncedLrc?.let { parseLrc(it).any { line -> line.text.isNotBlank() } } == true

suspend fun awaitLyrics(
    fetch: suspend () -> LyricsResponse?,
    onPending: (LyricsResponse) -> Unit = {},
    pause: suspend (Long) -> Unit = { delay(it) },
    attempts: Int = 12,
): LyricsResponse? {
    var last: LyricsResponse? = null
    repeat(attempts) { attempt ->
        last = fetch()
        val result = last ?: return null
        if (result.hasText || result.status != "pending") return result
        onPending(result)
        if (attempt < attempts - 1) pause(minOf(5_000L * (attempt + 1), 15_000L))
    }
    return last
}
