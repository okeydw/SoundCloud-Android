package com.scd.android

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecoveryTest {
    @Test
    fun retryTimeoutOnlyWhileWaitingToPlay() {
        for (state in listOf(Player.STATE_BUFFERING, Player.STATE_IDLE, Player.STATE_READY, Player.STATE_ENDED)) {
            assertFalse(PlayerRecovery.waitingForAudio(false, state))
        }
        assertTrue(PlayerRecovery.waitingForAudio(true, Player.STATE_BUFFERING))
        for (state in listOf(Player.STATE_IDLE, Player.STATE_READY, Player.STATE_ENDED)) {
            assertFalse(PlayerRecovery.waitingForAudio(true, state))
        }
    }

    @Test
    fun audioOutputErrorsAreSeparateFromFileAndNetworkErrors() {
        assertTrue(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED))
        assertTrue(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED))
        assertTrue(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED))
        assertTrue(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED))
        assertFalse(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS))
        assertFalse(PlayerRecovery.isAudioOutputError(PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
    }
}
