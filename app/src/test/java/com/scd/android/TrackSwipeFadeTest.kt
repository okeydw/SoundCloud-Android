package com.scd.android

import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class TrackSwipeFadeTest {
    private fun metadata(title: String) = MediaMetadata.Builder().setTitle(title).build()

    @Test fun expandedPlayerUsesTheWholePageForItsSlide() {
        val next = metadata("Next")
        val state = TrackSwipeFade(CoroutineScope(Dispatchers.Unconfined))
        state.begin(QueuePreview(next = next, current = metadata("Current")), 90f, 400f, 400f)
        state.drag(-100f)
        assertEquals(0.25f, state.fraction, 0.0001f)
        assertSame(next, state.target)
        state.drag(-800f)
        assertEquals(-400f, state.distance, 0.0001f)
        assertEquals(1f, state.fraction, 0.0001f)
        state.reset()
    }

    @Test fun theWholePreviewFollowsDirectionAndResetsTogether() {
        val previous = metadata("Previous")
        val next = metadata("Next")
        val current = metadata("Current")
        val state = TrackSwipeFade(CoroutineScope(Dispatchers.Unconfined))
        state.begin(QueuePreview(previous, next, current), 80f)
        state.drag(-40f)
        assertSame(next, state.target)
        assertEquals(0.5f, state.fraction, 0.0001f)
        assertSame(current, state.window?.current)
        state.drag(120f)
        assertSame(previous, state.target)
        assertEquals(1f, state.fraction, 0.0001f)
        state.reset()
        assertNull(state.window)
        assertEquals(0f, state.fraction, 0.0001f)
    }

    @Test fun swipingPastTheQueueEndDoesNotFadeIntoAnEmptyTrack() {
        val state = TrackSwipeFade(CoroutineScope(Dispatchers.Unconfined))
        state.begin(QueuePreview(previous = metadata("Previous")), 80f)
        state.drag(-200f)
        assertEquals(0f, state.distance, 0.0001f)
        assertEquals(0f, state.fraction, 0.0001f)
        state.drag(200f)
        assertEquals(1f, state.fraction, 0.0001f)
        state.reset()
    }
}
