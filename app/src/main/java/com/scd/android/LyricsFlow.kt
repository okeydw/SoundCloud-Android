package com.scd.android

import kotlinx.coroutines.delay

val LyricsResponse.hasText: Boolean
    get() = !plainText.isNullOrBlank() || syncedLrc?.let { parseLrc(it).any { line -> line.text.isNotBlank() } } == true

suspend fun awaitLyrics(
    fetch: suspend () -> LyricsResponse?,
    onPending: (LyricsResponse) -> Unit = {},
    pause: suspend (Long) -> Unit = { delay(it) },
    attempts: Int = Int.MAX_VALUE,
): LyricsResponse? {
    var last: LyricsResponse? = null
    repeat(attempts) { attempt ->
        last = fetch()
        val result = last ?: return null
        if (result.hasText || result.status != "pending") return result
        onPending(result)
        if (attempt < attempts - 1) pause(minOf(5_000L * (attempt.toLong() + 1), 30_000L))
    }
    return last
}
