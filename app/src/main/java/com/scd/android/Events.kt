package com.scd.android

import kotlinx.coroutines.launch

object Events {
    fun record(type: String, trackUrn: String?, positionPct: Double? = null) {
        if (trackUrn.isNullOrEmpty() || Prefs.offline) return
        val user = Prefs.userUrn ?: return
        App.scope.launch {
            runCatching { Api.sendEvent(user, trackUrn, type, positionPct) }
        }
    }

    fun like(urn: String) = record("like", urn)
    fun dislike(urn: String) = record("dislike", urn)
    fun playlistAdd(urn: String) = record("playlist_add", urn)
    fun fullPlay(urn: String) = record("full_play", urn, 1.0)
    fun skip(urn: String, pct: Double) = record("skip", urn, pct.coerceIn(0.0, 1.0))
}
