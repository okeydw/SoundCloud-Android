package com.scd.android

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistActions(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
fun artistAccountLabel(account: ArtistScAccount, accounts: List<ArtistScAccount>): String {
    fun kind(role: String) = when (role) {
        "main" -> R.string.catalog_account_main
        "demo" -> R.string.catalog_account_demo
        else -> R.string.catalog_sc_profile
    }
    val label = stringResource(kind(account.role))
    val siblings = accounts.filter { kind(it.role) == kind(account.role) }
    return if (siblings.size > 1) "$label ${siblings.indexOf(account) + 1}" else label
}

@Composable
fun TrackArtistNames(
    track: Track,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = !Prefs.offline,
    fallbackName: String = "",
    onOpen: (ArtistDestination) -> Unit = { it.open() },
) {
    val artists = remember(track) { track.artistLinks() }
    val text = buildAnnotatedString {
        if (artists.isEmpty()) append(fallbackName)
        artists.forEachIndexed { index, artist ->
            if (index > 0) append(", ")
            val target = artist.destination
            if (enabled && target != null) {
                withLink(LinkAnnotation.Clickable("artist:$index") { onOpen(target) }) { append(artist.name) }
            } else {
                append(artist.name)
            }
        }
    }
    Text(text, modifier = modifier, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
