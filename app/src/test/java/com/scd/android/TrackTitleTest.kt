package com.scd.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrackTitleTest {
    @Test fun shootHasTheSameTitleInLegacyHistoryAndThePlayer() {
        val raw = "CUPREOUS, Dxnkwer - Shoot"
        val history = HistoryEntry(scTrackId = "42", title = raw, artistName = "CUPREOUS").toTrack()
        val full = history.copy(publisher_metadata = TrackPublisher("CUPREOUS, Dxnkwer"))
        assertEquals("Shoot", history.displayTitle)
        assertEquals(history.displayTitle, full.displayTitle)
        assertEquals(history.displayTitle, formattedTrackTitle(raw, "CUPREOUS"))
        assertEquals(raw, history.title)
        assertEquals("Shoot.mp3", history.copy(title = "Shoot.mp3").displayTitle)
        assertEquals("Air, the air I breathe — Live", formattedTrackTitle("Air, the air I breathe — Live", "Air"))
    }

    @Test fun formatsAllFiveReportedSoundCloudUploads() {
        fun check(id: Long, title: String, uploader: String, expected: String) {
            val track = Track(urn = "soundcloud:tracks:$id", title = title, user = TrackUser(username = uploader))
            assertEquals(expected, track.displayTitle)
            assertEquals(title, track.title)
            assertEquals(expected, formattedTrackTitle(title, uploader, track.titleCredits()))
        }
        check(1439382295, "blaze on fire - Psychosis INXWERTG", "Dimka🥴🤙", "blaze on fire")
        check(1726361691, "5.SKWLKR, Lastfragment - Last Dance Of The Valkyries", "Lastfragment", "Last Dance Of The Valkyries")
        check(1740435708, "playingtheangel, RORY — под разными одеялами", "sugardeath", "под разными одеялами")
        check(2307603770, "арахнофобия (feat. woee33)", "3umph", "арахнофобия")
        check(846921331, "Katanacss Aikko INSPACE - Салют", "aikko", "Салют")
    }

    @Test fun handlesOtherPublishersAndNumberedCreditsWithoutRemovingVersions() {
        val track = Track(urn = "other", title = "02. Guest DJ Nova – Song (Live)",
            user = TrackUser(username = "Label"), publisher_metadata = TrackPublisher("DJ Nova & Guest"))
        assertEquals("Song (Live)", track.displayTitle)
        assertEquals("Song (Live)", withoutArtistCredits("Song (feat. guest42) (Live)", emptyList()))
        assertEquals("02. Song — Live", withoutArtistCredits("02. Song — Live", listOf("Artist")))
        assertEquals("Song (feat. Guest Remix)", withoutArtistCredits("Song (feat. Guest Remix)", listOf("Label")))
    }

    @Test fun recognizesVerifiedGroupsOnOtherUploadsButPreservesIsolatedWords() {
        assertEquals("Another Song", formattedTrackTitle("INXWERTG & Psychosis — Another Song", "Uploader"))
        assertEquals("Song — Psychosis", formattedTrackTitle("Song — Psychosis", "Uploader"))
        assertEquals("Song — RORY RORY", formattedTrackTitle("Song — RORY RORY", "Uploader"))
    }

    @Test fun legacyQueueItemsExposeTheCleanTitleToTheSystemPlayer() {
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle("Performers — Song").setArtist("Performers").build()
        val item = androidx.media3.common.MediaItem.Builder().setMediaId("track").setMediaMetadata(metadata).build()
        val updated = item.withCurrentTitle()
        assertEquals("Song", updated.mediaMetadata.title)
        assertEquals("Performers", updated.mediaMetadata.artist)
        assertEquals(item.mediaId, updated.mediaId)
        assertSame(updated, updated.withCurrentTitle())
    }

    @Test fun readsEachCreditedPerformerAndKeepsTheOriginalTitle() {
        val track = Track(
            urn = "test",
            title = "B & A — Song (feat. C)",
            user = TrackUser(username = "Label"),
            enrichment = TrackEnrichment(
                primary_artist = EnrichmentArtist(name = "A"),
                participants = listOf(
                    EnrichmentParticipant(EnrichmentArtist(name = "B"), "co_primary"),
                    EnrichmentParticipant(EnrichmentArtist(name = "C"), "featured"),
                ),
            ),
        )
        assertEquals("Song", track.displayTitle)
        assertEquals("B & A — Song (feat. C)", track.title)
        assertEquals("Song", formattedTrackTitle("B x A — Song", "A & B"))
    }

    @Test fun removesCombinedAndFeaturedCreditsInAnyOrder() {
        val artists = listOf("Элджей", "Era Estrefi", "Гость")
        for (title in listOf(
            "ERA ESTREFI & Элджей – Песня (feat. Гость)",
            "Гость, Элджей x Era Estrefi — Песня",
            "Элджей feat. Era Estrefi - Песня ft. Гость",
            "Песня — Элджей / Era Estrefi",
            "Элджей — Песня [Гость]",
        )) assertEquals("Песня", withoutArtistCredits(title, artists))
        assertEquals("Песня (Live)", withoutArtistCredits("Песня (feat. Гость) (Live)", artists))
        assertEquals("Song", withoutArtistCredits("AC/DC & P!nk – Song", listOf("AC/DC", "P!nk")))
    }

    @Test fun keepsSongWordsUnknownCreditsAndVersions() {
        val artists = listOf("Air", "A", "B")
        for (title in listOf("Air", "Air Supply — Song", "Walking on Air", "Song (Air Remix)", "Song (feat. B & C)", "Song — Live", "(Air)")) {
            assertEquals(title, withoutArtistCredits(title, artists))
        }
        assertEquals("Song - Live", withoutArtistCredits("A, B - Song - Live", artists))
        assertEquals("A — Song", withoutArtistCredits("A — Song", emptyList()))
    }
}
