package com.scd.android

sealed interface LinkTarget {
    data class PlayTrack(val track: Track) : LinkTarget
    data class OpenUser(val urn: String) : LinkTarget
    data class OpenPlaylist(val playlist: Playlist) : LinkTarget
    data class Failed(val reason: String) : LinkTarget
}

object Links {
    private val scHost = Regex("^(https?://)?((www|m|on)\\.)?soundcloud\\.(com|app\\.goo\\.gl)/\\S+$", RegexOption.IGNORE_CASE)

    fun isScLink(text: String): Boolean = scHost.matches(text.trim())

    fun withScheme(text: String): String = text.trim().let { if (it.contains("://")) it else "https://$it" }

    suspend fun open(url: String): LinkTarget {
        Logs.add("link", "open ${url.substringAfter("://").substringBefore('?')}")
        val r = try {
            Api.resolve(url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.add("link", "resolve error: ${e.javaClass.simpleName}")
            null
        } ?: return failed("resolve gave nothing")

        return when (r.type) {
            "track" -> {
                val t = Api.lastResolvedTrack?.takeIf { it.urn == r.urn && it.title.isNotEmpty() }
                    ?: runCatching { Api.trackByUrn(r.urn) }.getOrNull()
                    ?: return failed("track ${r.urn} not loaded")
                if (!Playback.playable(t)) return failed("track ${r.urn} is locked (${t.access})")
                LinkTarget.PlayTrack(t)
            }
            "user" -> LinkTarget.OpenUser(r.urn)
            "playlist" -> LinkTarget.OpenPlaylist(Playlist(urn = r.urn, title = r.title ?: ""))
            else -> failed("unsupported kind ${r.kind} (${r.urn})")
        }
    }

    private fun failed(why: String): LinkTarget {
        Logs.add("link", why)
        return LinkTarget.Failed(why)
    }
}
