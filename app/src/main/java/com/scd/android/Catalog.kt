package com.scd.android

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EnrichmentArtist(
    val id: String = "",
    val name: String = "",
    val avatar_url: String? = null,
    val sc_user_id: String? = null,
)

@Serializable
data class EnrichmentParticipant(
    val artist: EnrichmentArtist = EnrichmentArtist(),
    val role: String = "",
)

@Serializable
data class EnrichmentAlbum(
    val id: String = "",
    val title: String = "",
    val year: Int? = null,
    val cover_url: String? = null,
    val type: String = "",
)

@Serializable
data class TrackEnrichment(
    val primary_artist: EnrichmentArtist? = null,
    val participants: List<EnrichmentParticipant> = emptyList(),
    val album: EnrichmentAlbum? = null,
    val release_year: Int? = null,
)

@Serializable
data class ArtistSocial(val kind: String = "", val url: String = "", val verified: Boolean = false)

@Serializable
data class ArtistScAccount(val sc_user_id: String = "", val role: String = "", val verified: Boolean = false)

@Serializable
data class RelatedArtist(
    val id: String = "",
    val name: String = "",
    val country: String? = null,
    val avatar_url: String? = null,
)

@Serializable
data class CatalogArtist(
    val id: String = "",
    val name: String = "",
    val country: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val socials: List<ArtistSocial> = emptyList(),
    val sc_accounts: List<ArtistScAccount> = emptyList(),
    val track_count: Int = 0,
    val track_count_primary: Int = 0,
    val track_count_featured: Int = 0,
    val album_count: Int = 0,
    val popular_tracks: List<Track> = emptyList(),
    val related_artists: List<RelatedArtist> = emptyList(),
) {
    val mainScUsers: List<String> get() = sc_accounts.filter { it.role == "main" }.map { it.sc_user_id }
}

@Serializable
data class ArtistAlbum(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val release_year: Int? = null,
    val cover_url: String? = null,
    val role: String = "",
)

@Serializable
data class AlbumArtist(
    val id: String = "",
    val name: String = "",
    val role: String = "",
    val avatar_url: String? = null,
)

@Serializable
data class CatalogAlbum(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val release_year: Int? = null,
    val cover_url: String? = null,
    val primary_artist: AlbumArtist? = null,
    val artists: List<AlbumArtist> = emptyList(),
    val tracks: List<Track> = emptyList(),
)

fun albumKindRes(kind: String): Int = when (kind) {
    "album" -> R.string.kind_album
    "ep" -> R.string.kind_ep
    "single" -> R.string.kind_single
    "compilation" -> R.string.kind_compilation
    else -> R.string.kind_album
}

@Serializable
data class StatsTotals(
    val plays: Long = 0,
    @SerialName("listenedMs") val listenedMs: Long = 0,
    val tracks: Long = 0,
    val artists: Long = 0,
)

@Serializable
data class TopTrack(
    @SerialName("trackUrn") val trackUrn: String = "",
    val title: String = "",
    @SerialName("artistName") val artistName: String = "",
    @SerialName("artistUrn") val artistUrn: String? = null,
    @SerialName("artworkUrl") val artworkUrl: String? = null,
    val duration: Long = 0,
    val plays: Long = 0,
    @SerialName("listenedMs") val listenedMs: Long = 0,
) {
    fun asTrack() = Track(
        urn = trackUrn,
        title = title,
        duration = duration,
        artwork_url = artworkUrl,
        user = TrackUser(urn = artistUrn, username = artistName),
    )
}

@Serializable
data class TopArtist(
    @SerialName("artistName") val artistName: String = "",
    @SerialName("artistUrn") val artistUrn: String? = null,
    @SerialName("artworkUrl") val artworkUrl: String? = null,
    val plays: Long = 0,
    val tracks: Long = 0,
    @SerialName("listenedMs") val listenedMs: Long = 0,
)

@Serializable
data class TimelinePoint(
    val date: String = "",
    val plays: Long = 0,
    @SerialName("listenedMs") val listenedMs: Long = 0,
)

@Serializable
data class RhythmCell(val weekday: Int = 0, val hour: Int = 0, val plays: Long = 0)

@Serializable
data class ListeningStats(
    val period: String = "month",
    val unit: String = "day",
    val from: String = "",
    val to: String = "",
    val totals: StatsTotals = StatsTotals(),
    val previous: StatsTotals? = null,
    @SerialName("topTracks") val topTracks: List<TopTrack> = emptyList(),
    @SerialName("topArtists") val topArtists: List<TopArtist> = emptyList(),
    val timeline: List<TimelinePoint> = emptyList(),
    val rhythm: List<RhythmCell> = emptyList(),
)

@Serializable
data class AuraInfo(val aura_id: String? = null, val custom_hex: String? = null)

@Serializable
data class BlockedArtist(
    val kind: String = "user",
    val id: String = "",
    val name: String = "",
    val avatar_url: String? = null,
    val sc_user_ids: List<String> = emptyList(),
)

@Serializable
data class BlockedList(val collection: List<BlockedArtist> = emptyList())

@Serializable
data class BlockInput(val name: String, val avatar_url: String? = null, val sc_user_ids: List<String> = emptyList())

object CatalogRepo {
    const val PAGE = 30

    suspend fun tracksPage(id: String, sort: String, page: Int, role: String = "any"): Pair<List<Track>, Boolean> {
        val res = Api.catalogArtistTracks(id, role, sort, page, PAGE)
        val real = res.collection.filter { !it.urn.startsWith("wanted:") }
        return real to (real.size >= PAGE)
    }

    fun scUserUrn(id: String) = "soundcloud:users:$id"

    fun findCatalogFor(userUrn: String, tracks: List<Track>): EnrichmentArtist? {
        val id = scIdOf(userUrn) ?: return null
        return tracks.asSequence().mapNotNull { it.catalogArtist }.firstOrNull { scIdOf(it.sc_user_id) == id }
    }

    fun formatListen(ms: Long): Pair<Long, Long> {
        val minutes = ms / 60_000
        return (minutes / 60) to (minutes % 60)
    }
}

@Serializable
data class DiscordLink(
    val discord_id: String = "",
    val username: String? = null,
    val global_name: String? = null,
    val avatar_url: String? = null,
    val is_booster: Boolean = false,
    val has_star_role: Boolean = false,
)

@Serializable
data class PaySubscription(
    val premium: Boolean = false,
    val premium_until: Long = 0,
    val discord: DiscordLink? = null,
)

@Serializable
data class WebProfile(
    val service: String = "",
    val title: String = "",
    val url: String = "",
    val username: String? = null,
)
