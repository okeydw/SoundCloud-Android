package com.scd.android

data class BrowserEntry(val id: String, val title: String, val track: Track? = null) {
    val browsable: Boolean get() = track == null
}

class BrowserCatalog(
    queue: List<Track>,
    downloads: List<Track>,
    playlists: List<LocalPlaylist>,
    labels: List<String>,
    playable: (Track) -> Boolean = { true },
) {
    val root = BrowserEntry(ROOT, labels[0])
    private val folders = listOf(
        BrowserEntry(QUEUE, labels[1]),
        BrowserEntry(DOWNLOADS, labels[2]),
        BrowserEntry(PLAYLISTS, labels[3]),
    )
    private val children = linkedMapOf<String, List<BrowserEntry>>()
    private val entries = linkedMapOf<String, BrowserEntry>()

    init {
        fun tracks(list: List<Track>) = list.asSequence()
            .filter { it.urn.isNotBlank() && !it.urn.startsWith(PREFIX) && playable(it) }
            .distinctBy { it.urn }.map { BrowserEntry(it.urn, it.title, it) }.toList()
        children[ROOT] = folders
        children[QUEUE] = tracks(queue)
        children[DOWNLOADS] = tracks(downloads)
        val visible = playlists.filter { !it.deleted }.distinctBy { it.id }
        children[PLAYLISTS] = visible.map { BrowserEntry(playlistId(it.id), it.title) }
        visible.forEach { children[playlistId(it.id)] = tracks(it.tracks) }
        entries[ROOT] = root
        children.values.forEach { list -> list.forEach { entries.putIfAbsent(it.id, it) } }
    }

    fun item(id: String): BrowserEntry? = entries[id]

    fun children(parent: String, page: Int, pageSize: Int): List<BrowserEntry>? {
        if (page < 0 || pageSize <= 0) return null
        val list = children[parent] ?: return null
        val start = page.toLong() * pageSize
        if (start >= list.size) return emptyList()
        val end = minOf(list.size.toLong(), start + pageSize).toInt()
        return list.subList(start.toInt(), end)
    }

    fun resolve(ids: List<String>): List<Track>? {
        val result = ArrayList<Track>(ids.size)
        for (id in ids) result += entries[id]?.track ?: return null
        return result
    }

    fun counts(): Map<String, Int> = children.mapValues { it.value.size }

    companion object {
        private const val PREFIX = "scd:library:"
        const val ROOT = "${PREFIX}root"
        const val QUEUE = "${PREFIX}queue"
        const val DOWNLOADS = "${PREFIX}downloads"
        const val PLAYLISTS = "${PREFIX}playlists"
        fun playlistId(id: String) = "${PREFIX}playlist:$id"
    }
}
