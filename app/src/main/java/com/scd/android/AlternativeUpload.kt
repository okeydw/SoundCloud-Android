package com.scd.android

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

internal object AlternativeUpload {
    private val presentation = Regex("\\s*[\\[(](official (audio|video)|lyric(s| video)?)[\\])]", RegexOption.IGNORE_CASE)

    private fun normalized(value: String): String = presentation.replace(value, " ")
        .lowercase().map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")
        .split(' ').filter { it.isNotBlank() }.joinToString(" ")

    private fun artist(track: Track): String = normalized(
        track.enrichment?.primary_artist?.name?.takeIf { it.isNotBlank() } ?: track.user?.username.orEmpty(),
    )

    private fun creditedTitle(track: Track, artist: String): String? {
        val parts = track.title.split(Regex("\\s+[-–—]\\s+|\\s*:\\s*"), limit = 2)
        return if (parts.size == 2 && normalized(parts[0]) == artist) normalized(parts[1]) else null
    }

    fun query(track: Track): String? {
        val artist = artist(track)
        val title = normalized(track.title)
        if (artist.isBlank() || title.isBlank() || maxOf(track.duration, track.full_duration ?: 0L) < 30_000L) return null
        return if (creditedTitle(track, artist) != null) track.title else "$artist ${track.title}"
    }

    fun matches(original: Track, candidate: Track): Boolean {
        if (original.urn == candidate.urn || !isScUrn(candidate.urn)) return false
        if (candidate.access in setOf("blocked", "preview") || candidate.policy in setOf("BLOCK", "SNIP")) return false
        val duration = maxOf(original.duration, original.full_duration ?: 0L)
        val otherDuration = maxOf(candidate.duration, candidate.full_duration ?: 0L)
        if (duration < 30_000L || otherDuration < 30_000L || abs(duration - otherDuration) > minOf(3_000L, duration / 50)) return false
        val artist = artist(original)
        if (artist.isBlank()) return false
        val originalArtistId = original.enrichment?.primary_artist?.id?.takeIf { it.isNotBlank() }
        val candidateArtistId = candidate.enrichment?.primary_artist?.id?.takeIf { it.isNotBlank() }
        if (originalArtistId != null && candidateArtistId != null && originalArtistId != candidateArtistId) return false
        val title = creditedTitle(original, artist) ?: normalized(original.title)
        if (title.isBlank()) return false
        val otherTitle = normalized(candidate.title)
        return (otherTitle == title && artist(candidate) == artist) || creditedTitle(candidate, artist) == title
    }

    suspend fun find(original: Track): Track? {
        val query = query(original) ?: return null
        return withTimeoutOrNull(10_000L) {
            try {
                Api.searchTracks(query, limit = 30).collection
                    .filter { matches(original, it) && !it.hidden }
                    .sortedWith(compareBy<Track> {
                        abs(maxOf(original.duration, original.full_duration ?: 0L) - maxOf(it.duration, it.full_duration ?: 0L))
                    }.thenByDescending { it.playback_count ?: 0L })
                    .firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.add("player", "alternative search: ${e::class.simpleName}")
                null
            }
        }
    }
}
