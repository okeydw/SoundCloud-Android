package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@Composable
private fun playlistArt(playlist: Playlist): List<Any> {
    PlaylistCovers.get(playlist.urn)?.let { return listOf(it) }
    val own = playlist.artwork_url
    if (!own.isNullOrEmpty()) return listOfNotNull(Api.artworkUrl(own, "t120x120"))
    val key = PlaylistArt.coverKey(playlist.urn)
    var models by remember(playlist.urn) { mutableStateOf(PlaylistArt.cached(playlist.urn) ?: emptyList()) }
    LaunchedEffect(playlist.urn, key) {
        PlaylistArt.cached(playlist.urn)?.let {
            models = it
            return@LaunchedEffect
        }
        models = PlaylistArt.resolve(playlist)
    }
    return models
}

@Composable
fun PlaylistArtwork(playlist: Playlist, size: Dp, corner: Dp = 6.dp) {
    val models = playlistArt(playlist)

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        when {
            models.size >= 4 -> Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Tile(models[0], Modifier.fillMaxSize().weight(1f))
                    Tile(models[1], Modifier.fillMaxSize().weight(1f))
                }
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Tile(models[2], Modifier.fillMaxSize().weight(1f))
                    Tile(models[3], Modifier.fillMaxSize().weight(1f))
                }
            }

            models.isNotEmpty() -> Tile(models.first(), Modifier.fillMaxSize())

            else -> Icon(
                painterResource(R.drawable.ic_music),
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
private fun Tile(model: Any, modifier: Modifier) {
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
