package com.scd.android

data class LikedTrackOrder(val urns: List<String> = emptyList()) {
    fun apply(tracks: List<Track>): List<Track> {
        val byUrn = tracks.distinctBy { it.urn }.associateBy { it.urn }
        val known = urns.toHashSet()
        return byUrn.values.filter { it.urn !in known } + urns.mapNotNull(byUrn::get)
    }

    fun updated(visibleOrder: List<String>): LikedTrackOrder {
        val selected = visibleOrder.filter { it.isNotBlank() && '\n' !in it && '\r' !in it }.distinct()
        val selectedSet = selected.toHashSet()
        val previous = urns.toHashSet()
        val existing = selected.iterator()
        val slots = selected.filter { it !in previous } + urns
        return LikedTrackOrder(slots.map { if (it in selectedSet) existing.next() else it })
    }

    fun encode(): String = urns.joinToString("\n")

    companion object {
        fun decode(value: String?): LikedTrackOrder = LikedTrackOrder(value.orEmpty().lineSequence().filter { it.isNotBlank() }.distinct().toList())
    }
}
