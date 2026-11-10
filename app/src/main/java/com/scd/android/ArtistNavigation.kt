package com.scd.android

import java.util.Locale

fun soundCloudUserId(value: String?): String? {
    val id = value?.trim()?.removePrefix("soundcloud:users:") ?: return null
    return id.takeIf { it.isNotEmpty() && it.all { c -> c in '0'..'9' } && it.any { c -> c != '0' } }
}

sealed interface ArtistDestination {
    data class Catalog(val id: String) : ArtistDestination
    data class Profile(val urn: String) : ArtistDestination
}

data class TrackArtistLink(val name: String, val destination: ArtistDestination?)

fun Track.artistLinks(): List<TrackArtistLink> {
    val artists = buildList {
        enrichment?.primary_artist?.let { add(it) }
        enrichment?.participants.orEmpty()
            .filter { it.role in setOf("primary", "co_primary", "co-primary", "featured") }
            .forEach { add(it.artist) }
    }.filter { it.name.isNotBlank() }
    if (artists.isEmpty()) {
        val uploader = user ?: return emptyList()
        if (uploader.username.isBlank()) return emptyList()
        return listOf(TrackArtistLink(uploader.username, CatalogRepo.scUserUrn(uploader.urn.orEmpty())?.let(ArtistDestination::Profile)))
    }
    return artists.distinctBy { it.id.takeIf(String::isNotBlank) ?: it.name.trim().lowercase(Locale.ROOT) }.map { artist ->
        val destination = artist.id.takeIf(String::isNotBlank)?.let(ArtistDestination::Catalog)
            ?: CatalogRepo.scUserUrn(artist.sc_user_id.orEmpty())?.let(ArtistDestination::Profile)
            ?: user?.takeIf { it.username.trim().equals(artist.name.trim(), ignoreCase = true) }
                ?.urn?.let(CatalogRepo::scUserUrn)?.let(ArtistDestination::Profile)
        TrackArtistLink(artist.name, destination)
    }
}

fun ArtistDestination.open() {
    when (this) {
        is ArtistDestination.Catalog -> NavEvents.openCatalog(id)
        is ArtistDestination.Profile -> NavEvents.openUser(urn)
    }
}
