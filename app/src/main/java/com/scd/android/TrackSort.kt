package com.scd.android

enum class TrackSort { ORIGINAL, RECENT, RELEASED, PLAYS, LIKES, REPOSTS, TITLE, ARTIST, DURATION }

data class TrackOrder(val mode: TrackSort = TrackSort.ORIGINAL, val reversed: Boolean = false) {
    val active: Boolean get() = mode != TrackSort.ORIGINAL || reversed

    fun tracks(tracks: List<Track>, newestFirst: Boolean = false): List<Track> = arrange(
        tracks, newestFirst,
        title = { it.title },
        artist = { it.artistLinks().joinToString(", ") { artist -> artist.name } },
        metric = {
            when (mode) {
                TrackSort.RELEASED -> releaseDay(it.enrichment?.release_date) ?: releaseDay(it.release_date) ?: releaseDay(it.created_at)
                TrackSort.PLAYS -> it.playback_count
                TrackSort.LIKES -> it.favoritings_count ?: it.likes_count
                TrackSort.REPOSTS -> it.reposts_count
                TrackSort.DURATION -> it.displayDuration.takeIf { ms -> ms > 0L }
                else -> null
            }
        },
    )

    fun <T> arrange(
        items: List<T>,
        newestFirst: Boolean = false,
        title: (T) -> String,
        artist: (T) -> String = title,
        metric: (T) -> Long? = { null },
    ): List<T> {
        if (mode == TrackSort.ORIGINAL || mode == TrackSort.RECENT) {
            val flip = (mode == TrackSort.RECENT && !newestFirst) != reversed
            return if (flip) items.reversed() else items
        }
        val keys = items.map { item ->
            when (mode) {
                TrackSort.TITLE -> title(item).trim().lowercase().replace('ё', 'е').takeIf { it.isNotEmpty() }
                TrackSort.ARTIST -> artist(item).trim().lowercase().replace('ё', 'е').takeIf { it.isNotEmpty() }
                else -> null
            }
        }
        val values = if (mode == TrackSort.TITLE || mode == TrackSort.ARTIST) emptyList() else items.map(metric)
        val descending = mode !in listOf(TrackSort.TITLE, TrackSort.ARTIST, TrackSort.DURATION)
        fun <V : Comparable<V>> compare(a: V?, b: V?): Int = when {
            a == null && b == null -> 0
            a == null -> 1
            b == null -> -1
            descending != reversed -> b.compareTo(a)
            else -> a.compareTo(b)
        }
        return items.indices.sortedWith { a, b ->
            val comparison = if (values.isEmpty()) compare(keys[a], keys[b]) else compare(values[a], values[b])
            if (comparison == 0) a.compareTo(b) else comparison
        }.map(items::get)
    }
}

private val releaseDatePattern = Regex("^(\\d{4})[-/](\\d{2})[-/](\\d{2})(?:$|[T /])")

fun releaseDay(value: String?): Long? {
    val match = releaseDatePattern.find(value?.trim().orEmpty()) ?: return null
    val (year, month, day) = match.destructured
    val y = year.toInt()
    val m = month.toInt()
    val d = day.toInt()
    if (y <= 0 || m !in 1..12) return null
    val days = when (m) {
        2 -> if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return if (d in 1..days) y * 10_000L + m * 100L + d else null
}

suspend fun collectTrackPages(load: suspend (Int) -> Pair<List<Track>, Boolean>): List<Track> {
    val tracks = LinkedHashMap<String, Track>()
    repeat(500) { page ->
        val (batch, more) = load(page)
        batch.forEach { tracks[it.urn] = it }
        if (!more) return tracks.values.toList()
    }
    error("The list is too large to load at once")
}
