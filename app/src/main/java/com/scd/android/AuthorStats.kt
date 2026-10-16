package com.scd.android

data class AuthorStats(
    val tracks: List<Track>,
    val plays: Long,
    val likes: Long,
    val reposts: Long,
    val comments: Long,
    val followers: Int?,
    val counted: Boolean,
)

object AuthorStatsRepo {
    private const val PAGE = 50
    private const val MAX_PAGES = 20

    @Volatile
    private var cached: Pair<AuthorStats, Long>? = null

    fun likesOf(t: Track): Long = t.likes_count ?: t.favoritings_count ?: 0L

    suspend fun load(fresh: Boolean = false): AuthorStats? {
        val urn = Prefs.userUrn ?: return null
        cached?.let { (s, at) -> if (!fresh && System.currentTimeMillis() - at < 5 * 60_000L) return s }
        val all = ArrayList<Track>()
        var page = 0
        while (page < MAX_PAGES) {
            val res = Api.userTracks(urn, page, PAGE)
            all += res.collection
            if (!res.has_more || res.collection.isEmpty()) break
            page++
        }
        val tracks = all.distinctBy { it.urn }.sortedByDescending { it.playback_count ?: 0L }
        val followers = runCatching { Api.me().followers_count }.getOrNull()
        val stats = AuthorStats(
            tracks = tracks,
            plays = tracks.sumOf { it.playback_count ?: 0L },
            likes = tracks.sumOf { likesOf(it) },
            reposts = tracks.sumOf { it.reposts_count ?: 0L },
            comments = tracks.sumOf { it.comment_count ?: 0L },
            followers = followers,
            counted = tracks.any { it.playback_count != null },
        )
        cached = stats to System.currentTimeMillis()
        return stats
    }
}

fun formatBig(n: Long): String {
    fun scaled(unit: Long, suffix: String): String {
        val tenths = n * 10 / unit
        return if (tenths % 10 == 0L) "${tenths / 10}$suffix" else "${tenths / 10}.${tenths % 10}$suffix"
    }
    return when {
        n >= 1_000_000_000L -> scaled(1_000_000_000L, "B")
        n >= 1_000_000L -> scaled(1_000_000L, "M")
        n >= 10_000L -> scaled(1_000L, "K")
        else -> n.toString()
    }
}
