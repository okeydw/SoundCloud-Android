package com.scd.android

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player

internal object PlayerRecovery {
    fun waitingForAudio(playWhenReady: Boolean, state: Int): Boolean =
        playWhenReady && state == Player.STATE_BUFFERING

    fun isAudioOutputError(code: Int): Boolean = when (code) {
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_INIT_FAILED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_OFFLOAD_WRITE_FAILED -> true
        else -> false
    }
}
