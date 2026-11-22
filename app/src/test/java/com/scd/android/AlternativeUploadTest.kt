package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class AlternativeUploadTest {
    private val original = Track(urn = "soundcloud:tracks:1", title = "Song", duration = 180_000, user = TrackUser(username = "Artist"))
    private val copy = original.copy(urn = "soundcloud:tracks:2")

    @Test fun acceptsMatchingCreditsAndDurationAndExplicitReuploadCredits() {
        assertTrue(AlternativeUpload.matches(original, copy.copy(title = "Song (Official Audio)", duration = 182_000)))
        assertTrue(AlternativeUpload.matches(original, copy.copy(title = "Artist - Song", user = TrackUser(username = "Uploader"))))
        assertTrue(AlternativeUpload.matches(original.copy(title = "Artist - Song"), copy))
    }

    @Test fun rejectsOtherSongsVersionsArtistsAndUnknownDurations() {
        for (candidate in listOf(
            copy.copy(title = "Song (remix)"), copy.copy(title = "Song live"), copy.copy(title = "Song slowed"),
            copy.copy(title = "Another song"), copy.copy(user = TrackUser(username = "Other artist")),
            copy.copy(duration = 184_000), copy.copy(duration = 0), copy.copy(access = "preview"),
            copy.copy(access = "blocked"), copy.copy(policy = "BLOCK"), copy.copy(policy = "SNIP"),
            copy.copy(urn = "youtube:123"), original,
        )) assertFalse(candidate.toString(), AlternativeUpload.matches(original, candidate))
        assertNull(AlternativeUpload.query(original.copy(user = null)))
        assertFalse(AlternativeUpload.matches(original.copy(duration = 0), copy))
    }

    @Test fun usesCatalogArtistInsteadOfLabelAndPreservesFeaturedCredits() {
        val track = original.copy(title = "Song feat. Friend", user = TrackUser(username = "Record label"),
            enrichment = TrackEnrichment(primary_artist = EnrichmentArtist(name = "Artist")))
        assertTrue(AlternativeUpload.matches(track, copy.copy(title = "Artist - Song feat. Friend")))
        assertFalse(AlternativeUpload.matches(track, copy.copy(title = "Artist - Song")))
    }

    @Test fun ambiguousTitlePrefixesAndDifferentCatalogArtistsAreNotMatches() {
        assertFalse(AlternativeUpload.matches(original, copy.copy(title = "Artist Song", user = TrackUser(username = "Other"))))
        assertFalse(AlternativeUpload.matches(original.copy(title = "Artist Song"), copy))
        val a = original.copy(enrichment = TrackEnrichment(primary_artist = EnrichmentArtist(id = "one", name = "Artist")))
        val b = copy.copy(enrichment = TrackEnrichment(primary_artist = EnrichmentArtist(id = "two", name = "Artist")))
        assertFalse(AlternativeUpload.matches(a, b))
    }
}
