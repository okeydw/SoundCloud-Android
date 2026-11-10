package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

@Composable
fun rememberSleepClock(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(SleepTimer.endsAt) {
        while (SleepTimer.endsAt > 0L) {
            now = System.currentTimeMillis()
            delay(1_000L - now % 1_000L)
        }
    }
    return now
}

@Composable
fun sleepTimerStatus(): String? {
    val now = rememberSleepClock()
    return when {
        SleepTimer.endsAt > 0L -> stringResource(R.string.sleep_timer_left, SleepTimer.format(SleepTimer.remainingMs(now)))
        SleepTimer.endOfTrack -> stringResource(R.string.sleep_timer_after_track)
        else -> null
    }
}

@Composable
fun sleepTimerShort(): String? {
    val now = rememberSleepClock()
    return when {
        SleepTimer.endsAt > 0L -> SleepTimer.format(SleepTimer.remainingMs(now))
        SleepTimer.endOfTrack -> stringResource(R.string.sleep_timer_end_of_track)
        else -> null
    }
}
