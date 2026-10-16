package com.scd.android

import androidx.media3.common.Player

object ShuffleQueue {
    private var original: List<Track> = emptyList()
    var onChanged: () -> Unit = {}

    val on: Boolean get() = Prefs.shuffleOn

    fun isWave(urn: String?): Boolean = WaveFeedback.isOwned(urn)

    fun active(player: Player?): Boolean = on && !isWave(player?.currentMediaItem?.mediaId)

    fun arrange(queue: List<Track>, index: Int): Pair<List<Track>, Int> {
        val start = queue.getOrNull(index)
        if (!on || start == null || queue.size <= 1 || isWave(start.urn)) {
            original = emptyList()
            return queue to index
        }
        original = queue
        val rest = queue.filterIndexed { i, t -> i != index && t.urn != start.urn }.shuffled()
        return (listOf(start) + rest) to 0
    }

    fun widen(full: List<Track>) {
        if (original.isNotEmpty()) original = full.distinctBy { it.urn }
    }

    fun enable() {
        Prefs.changeShuffleOn(true)
        NowPlaying.shuffle = true
        onChanged()
    }

    fun toggle(player: Player) = set(player, !on)

    fun set(player: Player, value: Boolean) {
        if (player.shuffleModeEnabled) player.shuffleModeEnabled = false
        if (value == on) return
        if (value && isWave(player.currentMediaItem?.mediaId)) return
        runCatching { if (value) shuffleOn(player) else shuffleOff(player) }
            .onFailure { Logs.add("queue", "shuffle failed: ${it.javaClass.simpleName}") }
        Prefs.changeShuffleOn(value)
        NowPlaying.shuffle = value
        onChanged()
    }

    private fun queueTracks(p: Player): List<Track> =
        (0 until p.mediaItemCount).mapNotNull { runCatching { p.getMediaItemAt(it).toTrackOrNull() }.getOrNull() }

    private fun keepOnly(p: Player) {
        val cur = p.currentMediaItemIndex
        if (cur > 0) p.removeMediaItems(0, cur)
        if (p.mediaItemCount > 1) p.removeMediaItems(1, p.mediaItemCount)
    }

    private fun shuffleOn(p: Player) {
        val all = queueTracks(p)
        val currentUrn = p.currentMediaItem?.mediaId ?: return
        original = all.distinctBy { it.urn }
        if (all.size <= 1) return
        val rest = original.filter { it.urn != currentUrn }.shuffled()
        keepOnly(p)
        if (rest.isNotEmpty()) p.addMediaItems(rest.map { it.toMediaItem() })
        Logs.add("queue", "shuffle on: ${rest.size + 1} tracks")
    }

    private fun shuffleOff(p: Player) {
        val currentUrn = p.currentMediaItem?.mediaId
        val inQueue = queueTracks(p)
        if (currentUrn == null || inQueue.size <= 1) {
            original = emptyList()
            return
        }
        val present = inQueue.map { it.urn }.toSet()
        val base = original.filter { it.urn in present }
        val known = base.map { it.urn }.toSet()
        val ordered = (base + inQueue.filter { it.urn !in known }).distinctBy { it.urn }
        original = emptyList()
        val pos = ordered.indexOfFirst { it.urn == currentUrn }
        if (pos < 0) return
        keepOnly(p)
        if (pos > 0) p.addMediaItems(0, ordered.subList(0, pos).map { it.toMediaItem() })
        if (pos + 1 < ordered.size) p.addMediaItems(ordered.subList(pos + 1, ordered.size).map { it.toMediaItem() })
        Logs.add("queue", "shuffle off: order restored")
    }
}
