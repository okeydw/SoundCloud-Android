package com.scd.android

import org.junit.Assert.*
import org.junit.Test

class LikedTrackOrderTest {
    private fun tracks(vararg urns: String) = urns.map { Track(urn = it) }
    private fun ids(tracks: List<Track>) = tracks.map { it.urn }

    @Test fun preservesServerOrderUntilTheUserReorders() {
        assertEquals(listOf("b", "a"), ids(LikedTrackOrder().apply(tracks("b", "a"))))
    }

    @Test fun savedOrderSurvivesRestartAndRemoteRefresh() {
        val saved = LikedTrackOrder().updated(listOf("c", "a", "b")).encode()
        assertEquals(listOf("c", "a", "b"), ids(LikedTrackOrder.decode(saved).apply(tracks("b", "c", "a"))))
    }

    @Test fun newLikesGoAboveTheCustomOrderAndRemovedLikesStayAbsent() {
        val saved = LikedTrackOrder(listOf("c", "a", "b"))
        assertEquals(listOf("new", "c", "b"), ids(saved.apply(tracks("new", "b", "c"))))
    }

    @Test fun reorderingAnOfflineSubsetPreservesUnseenTracks() {
        val saved = LikedTrackOrder(listOf("a", "hidden", "b", "other"))
        val changed = saved.updated(listOf("b", "a"))
        assertEquals(listOf("b", "hidden", "a", "other"), changed.urns)
        assertEquals(listOf("b", "a"), ids(changed.apply(tracks("a", "b"))))
    }

    @Test fun aNewLikeCanBeDraggedBetweenPreviouslyOrderedTracks() {
        val saved = LikedTrackOrder(listOf("a", "b"))
        assertEquals(listOf("b", "new", "a"), saved.updated(listOf("b", "new", "a")).urns)
    }

    @Test fun duplicatesAndEmptyIdsDoNotCreateDuplicateRows() {
        val saved = LikedTrackOrder.decode("b\na\nb\n\n")
        assertEquals(listOf("b", "a"), ids(saved.apply(tracks("a", "b", "a"))))
        assertEquals(listOf("a", "b"), saved.updated(listOf("a", "a", "", "b")).urns)
    }

    @Test fun sortingByOtherFieldsDoesNotOverwriteTheManualOrder() {
        val original = tracks("b", "a").map { it.copy(title = it.urn) }
        val saved = LikedTrackOrder(listOf("b", "a"))
        assertEquals(listOf("a", "b"), ids(TrackOrder(TrackSort.TITLE).tracks(saved.apply(original))))
        assertEquals(listOf("b", "a"), ids(saved.apply(original)))
    }
}
