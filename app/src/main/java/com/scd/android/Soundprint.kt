package com.scd.android

import androidx.compose.ui.graphics.Color

data class GenreShare(val key: String, val label: String, val share: Float, val color: Color)

private val fixedGenreColors = mapOf(
    "lofi" to 0xFF8B9DC3, "house" to 0xFFFF7A59, "phonk" to 0xFFC026D3, "ambient" to 0xFF5EEAD4,
    "rnb" to 0xFFF0ABFC, "r&b" to 0xFFF0ABFC, "trap" to 0xFFFB7185, "jazz" to 0xFFFBBF24,
    "techno" to 0xFF60A5FA, "indie" to 0xFFA3E635, "soul" to 0xFFFCA5A5, "dnb" to 0xFF34D399,
    "hyperpop" to 0xFFE879F9,
)

fun genreColor(name: String): Color {
    fixedGenreColors[name.lowercase()]?.let { return Color(it) }
    var hash = 0
    for (c in name) hash = hash * 31 + c.code
    val hue = (Math.floorMod(hash, 360)).toFloat()
    return Color.hsl(hue, 0.7f, 0.62f)
}

private val hotGenres = listOf(
    "phonk", "trap", "festival", "house", "techno", "dnb", "drum", "hardstyle", "hyperpop",
    "rave", "edm", "dubstep", "bass", "hardcore",
)
private val coldGenres = listOf(
    "ambient", "lofi", "lo-fi", "chill", "sad", "piano", "acoustic", "soul", "jazz", "classical",
    "sleep", "study", "downtempo", "r&b", "rnb", "slow",
)

fun genreEnergy(name: String): Float {
    val n = name.lowercase()
    return when {
        hotGenres.any { n.contains(it) } -> 0.85f
        coldGenres.any { n.contains(it) } -> 0.2f
        else -> 0.5f
    }
}

fun topGenres(tracks: List<Track>, n: Int = 7): List<GenreShare> {
    val counts = LinkedHashMap<String, Int>()
    val labels = HashMap<String, MutableMap<String, Int>>()
    var tagged = 0
    for (t in tracks) {
        val raw = t.genre?.trim().orEmpty()
        if (raw.isEmpty()) continue
        val key = raw.lowercase()
        counts[key] = (counts[key] ?: 0) + 1
        val byLabel = labels.getOrPut(key) { HashMap() }
        byLabel[raw] = (byLabel[raw] ?: 0) + 1
        tagged++
    }
    if (tagged == 0) return emptyList()
    return counts.entries.sortedByDescending { it.value }.take(n).map { (key, c) ->
        val label = labels[key]?.maxByOrNull { it.value }?.key ?: key
        GenreShare(key, label, c.toFloat() / tagged, genreColor(key))
    }
}

fun genresMatch(track: Track, key: String): Boolean = track.genre?.trim()?.lowercase() == key
