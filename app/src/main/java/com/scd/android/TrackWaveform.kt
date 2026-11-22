package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun rememberTrackWaveform(urn: String?, url: String?): List<Float> {
    var samples by remember(urn) { mutableStateOf(WaveCache.initial(urn)) }
    val downloaded = urn != null && Downloads.isDownloaded(urn)
    LaunchedEffect(urn, url, downloaded, Prefs.offline, Health.noInternet) {
        if (!urn.isNullOrBlank()) WaveCache.display(urn, url).collect { samples = it }
    }
    return samples
}
