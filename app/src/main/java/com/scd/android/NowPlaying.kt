package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object NowPlaying {
    var urn by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var openPlayerRequest by mutableStateOf(false)
}

object PlaylistEvents {
    var version by mutableStateOf(0)
    fun bump() { version++ }
}
