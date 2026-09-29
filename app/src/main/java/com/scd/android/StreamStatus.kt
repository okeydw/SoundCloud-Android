package com.scd.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

object AccountData {

    fun wipe(context: android.content.Context, controller: androidx.media3.session.MediaController?) {
        runCatching {
            controller?.stop()
            controller?.clearMediaItems()
        }
        Api.storeSession(context, null)
        Prefs.saveUsername(null)
        Prefs.saveAvatar(null)
        Prefs.saveUserUrn(null)
        Prefs.saveStar(false)
        Likes.reset()
        Dislikes.reset()
        LikedPlaylists.reset()
        LikedArtists.reset()
        LocalRadio.reset()
        FeedCache.clear()
        PlaylistArt.clear()
        WaveCache.clear()
        ScDataSource.forgetResolved()
        val app = context.applicationContext
        App.scope.launch { runCatching { MediaCache.clear(app) } }
    }
}

object PendingLink {
    var token by mutableStateOf<String?>(null)
    var pull by mutableStateOf(false)

    fun set(token: String, pull: Boolean) {
        this.pull = pull
        this.token = token
    }

    fun clear() {
        token = null
        pull = false
    }
}

object SessionState {

    var expired by mutableStateOf(false)
        private set
    var fromStream by mutableStateOf(false)
        private set

    @Volatile
    private var lastStreamCheck = 0L

    @Volatile
    var lastRefreshAt = 0L

    fun markExpired() {
        if (Api.sessionId != null) expired = true
    }

    fun streamDenied() {
        if (Api.sessionId == null) return
        val now = System.currentTimeMillis()
        if (now - lastStreamCheck < 60_000L) return
        lastStreamCheck = now
        fromStream = true
        expired = true
    }

    fun consume() {
        expired = false
        fromStream = false
    }
}

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
