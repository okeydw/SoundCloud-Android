package com.scd.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

private fun genreEnergy(name: String): Float {
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

@Composable
fun SoundprintCard(
    liked: List<Track>,
    onGenre: (GenreShare) -> Unit,
    onPlayYourSound: () -> Unit,
) {
    val spectrum = remember(liked.size, liked.firstOrNull()?.urn) { topGenres(liked) }
    if (spectrum.isEmpty()) return
    val energy = spectrum.take(4).map { genreEnergy(it.key) }.average().toFloat()
    val lead = spectrum.first().color
    val max = spectrum.first().share.coerceAtLeast(0.0001f)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(lead.copy(alpha = 0.22f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                )
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_equalizer), null, tint = lead, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.soundprint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(
                    when {
                        energy >= 0.65f -> R.string.soundprint_hot
                        energy <= 0.35f -> R.string.soundprint_calm
                        else -> R.string.soundprint_mid
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                color = lead,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().height(120.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            spectrum.forEachIndexed { i, g ->
                val target = 0.32f + (g.share / max) * 0.68f
                val grow = remember(g.key) { Animatable(0f) }
                LaunchedEffect(g.key) { grow.animateTo(1f, tween(600, delayMillis = i * 70)) }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onGenre(g) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(target * grow.value)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(Brush.verticalGradient(listOf(g.color, g.color.copy(alpha = 0.12f)))),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        g.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "${(g.share * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = g.color,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onPlayYourSound, modifier = Modifier.align(Alignment.End)) {
            Icon(painterResource(R.drawable.ic_shuffle), null, tint = lead, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.soundprint_play), color = lead, fontWeight = FontWeight.Bold)
        }
    }
}
