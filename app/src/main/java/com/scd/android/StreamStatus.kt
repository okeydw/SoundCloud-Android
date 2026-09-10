package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object StreamStatus {

    var protectedUrn by mutableStateOf<String?>(null)
        private set

    fun begin(urn: String) {
        protectedUrn = urn
    }

    fun end(urn: String) {
        if (protectedUrn == urn) protectedUrn = null
    }

    fun isProtected(urn: String?): Boolean = urn != null && protectedUrn == urn
}
