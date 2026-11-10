package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistNavigationTest {
    private val uploader = TrackUser("soundcloud:users:123", "Label")

    @Test
    fun artistCreditOpensCatalogInsteadOfUploadingLabel() {
        val track = Track(
            urn = "soundcloud:tracks:1", user = uploader,
            enrichment = TrackEnrichment(primary_artist = EnrichmentArtist("artist-a", "Singer")),
        )
        assertEquals(listOf(TrackArtistLink("Singer", ArtistDestination.Catalog("artist-a"))), track.artistLinks())
    }

    @Test
    fun trackWithoutEnrichmentKeepsUploaderProfile() {
        val track = Track(urn = "soundcloud:tracks:1", user = uploader)
        assertEquals(listOf(TrackArtistLink("Label", ArtistDestination.Profile("soundcloud:users:123"))), track.artistLinks())
        assertNull(track.copy(user = uploader.copy(urn = "soundcloud:tracks:123")).artistLinks().single().destination)
    }

    @Test
    fun eachPerformerGetsOwnLinkAndUnknownNameStaysPlainText() {
        val primary = EnrichmentArtist("artist-a", "Singer")
        val track = Track(
            urn = "soundcloud:tracks:1", user = uploader,
            enrichment = TrackEnrichment(primary_artist = primary, participants = listOf(
                EnrichmentParticipant(primary, "primary"),
                EnrichmentParticipant(EnrichmentArtist("artist-b", "Guest"), "featured"),
                EnrichmentParticipant(EnrichmentArtist(name = "Unmatched"), "featured"),
                EnrichmentParticipant(EnrichmentArtist("producer", "Producer"), "producer"),
            )),
        )
        assertEquals(listOf("Singer", "Guest", "Unmatched"), track.artistLinks().map { it.name })
        assertEquals(ArtistDestination.Catalog("artist-b"), track.artistLinks()[1].destination)
        assertNull(track.artistLinks()[2].destination)
    }

    @Test
    fun incompleteArtistUsesExplicitAccountOrMatchingUploaderOnly() {
        fun links(artist: EnrichmentArtist) = Track(
            urn = "soundcloud:tracks:1", user = uploader,
            enrichment = TrackEnrichment(primary_artist = artist),
        ).artistLinks().single()
        assertEquals(ArtistDestination.Profile("soundcloud:users:456"), links(EnrichmentArtist(name = "Singer", sc_user_id = "456")).destination)
        assertEquals(ArtistDestination.Profile("soundcloud:users:123"), links(EnrichmentArtist(name = "label")).destination)
        assertNull(links(EnrichmentArtist(name = "Singer")).destination)
    }

    @Test
    fun accountsIncludeDemosAndOtherProfilesWithoutDuplicatesOrInvalidIds() {
        val artist = CatalogArtist(sc_accounts = listOf(
            ArtistScAccount("soundcloud:users:2", "demo"),
            ArtistScAccount("3", "other"),
            ArtistScAccount("1", "main"),
            ArtistScAccount("2", "demo", verified = true),
            ArtistScAccount("", "main"),
            ArtistScAccount("soundcloud:tracks:4", "main"),
        ))
        assertEquals(listOf("1", "2", "3"), artist.linkedScAccounts.map { it.sc_user_id })
        assertEquals(true, artist.linkedScAccounts[1].verified)
        assertEquals("soundcloud:users:2", CatalogRepo.scUserUrn(artist.linkedScAccounts[1].sc_user_id))
    }

    @Test
    fun profileCanFindCatalogFromFeaturedArtistWithoutMatchingUploader() {
        val primary = EnrichmentArtist("artist-a", "Singer", sc_user_id = "456")
        val guest = EnrichmentArtist("artist-b", "Guest", sc_user_id = "123")
        val track = Track(urn = "soundcloud:tracks:1", user = uploader, enrichment = TrackEnrichment(
            primary_artist = primary, participants = listOf(EnrichmentParticipant(guest, "featured")),
        ))
        assertEquals(guest, CatalogRepo.findCatalogFor("soundcloud:users:123", listOf(track)))
        assertNull(CatalogRepo.findCatalogFor("soundcloud:users:789", listOf(track)))
    }

    @Test
    fun canonicalProfileUrnsAreNotPrefixedTwiceAndInvalidIdsAreRejected() {
        assertEquals("soundcloud:users:123", CatalogRepo.scUserUrn(" soundcloud:users:123 "))
        assertEquals("soundcloud:users:123", CatalogRepo.scUserUrn("123"))
        listOf("", "0", "soundcloud:tracks:123", "https://soundcloud.com/test", "١٢٣", "-1").forEach {
            assertNull(CatalogRepo.scUserUrn(it))
        }
    }
}
