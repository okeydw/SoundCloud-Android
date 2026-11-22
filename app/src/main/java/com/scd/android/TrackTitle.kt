package com.scd.android

private val creditJoin = Regex("""\s*(?:[,;&+]|\s/\s|\s(?:x|×|and|и)\s|\s(?:feat(?:uring)?|ft)\.?\s)\s*""", RegexOption.IGNORE_CASE)
private val titleDivider = Regex("""\s+[-:|]\s*|\s*[-:|]\s+|\s*[—–]\s*""")
private val startFeature = Regex("""^(?:feat(?:uring)?|ft)\.?\s+""", RegexOption.IGNORE_CASE)
private val repeatedSpace = Regex(""" {2,}""")
private val featureSuffix = Regex("""\s+(?:feat(?:uring)?|ft)\.?\s+""", RegexOption.IGNORE_CASE)
private val creditBrackets = Regex("""\(([^()]*)\)|\[([^\[\]]*)\]""")
private val trackNumber = Regex("""^\d{1,3}[.)]\s*""")
private val artistHandle = Regex("""[\p{L}\p{N}][\p{L}\p{N}_.'’\-]*""")

private val verifiedLineups = listOf(
    listOf("Psychosis", "INXWERTG"),
    listOf("playingtheangel", "RORY"),
    listOf("Katanacss", "aikko", "INSPACE"),
)

internal fun Track.titleCredits(): List<String> = buildList {
    val saved = if (enrichment == null || publisher_metadata?.artist.isNullOrBlank()) Downloads.savedTrack(urn) else null
    addAll((if (enrichment == null && saved != null) saved else this@titleCredits).creditedArtists().map { it.name })
    (publisher_metadata?.artist ?: saved?.publisher_metadata?.artist)?.takeIf { it.isNotBlank() }?.let {
        add(it)
        addAll(creditJoin.split(it))
    }
}.distinct()

internal fun withoutArtistCredits(title: String, artists: List<String>): String {
    val names = artists.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedByDescending { it.length }
    if (title.isBlank()) return title

    fun matchesNames(block: String, candidates: List<String>, requireAll: Boolean = false): Boolean {
        var rest = block
        val seen = if (requireAll) mutableSetOf<String>() else null
        while (rest.isNotEmpty()) {
            val name = candidates.firstOrNull { rest.startsWith(it, ignoreCase = true) } ?: return false
            seen?.add(name)
            rest = rest.substring(name.length)
            if (rest.isEmpty()) return seen == null || seen.size == candidates.size
            val join = creditJoin.matchAt(rest, 0)
            rest = when {
                join != null -> rest.substring(join.value.length)
                rest.first().isWhitespace() -> rest.trimStart()
                else -> return false
            }
        }
        return false
    }

    fun isCredit(text: String): Boolean {
        val block = text.trim().replaceFirst(startFeature, "").trim()
        return matchesNames(block, names) || verifiedLineups.any { group ->
            group.any { block.startsWith(it, ignoreCase = true) } && matchesNames(block, group, requireAll = true)
        }
    }

    fun isArtistPrefix(text: String): Boolean {
        if (isCredit(text)) return true
        val number = trackNumber.find(text)
        val block = number?.let { text.substring(it.range.last + 1) } ?: text
        if (isCredit(block)) return true
        val performers = block.split(',').map(String::trim)
        return performers.size > 1 && performers.all { artistHandle.matches(it) || isCredit(it) } &&
            (if (number == null) isCredit(performers.first()) else performers.any(::isCredit))
    }

    var result = creditBrackets.replace(title) { match ->
        val block = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
        val guest = startFeature.find(block)?.let { block.substring(it.range.last + 1).trim() }
        if (isCredit(block) || (guest != null && artistHandle.matches(guest))) "" else match.value
    }.trim()
    val feature = featureSuffix.findAll(result).firstOrNull { isCredit(result.substring(it.range.last + 1)) }
    if (feature != null) result = result.take(feature.range.first).trimEnd()
    while (true) {
        val dividers = titleDivider.findAll(result).toList()
        val prefix = dividers.firstOrNull { isArtistPrefix(result.take(it.range.first)) }
        val suffix = dividers.lastOrNull { isCredit(result.substring(it.range.last + 1)) }
        result = when {
            prefix != null -> result.substring(prefix.range.last + 1).trim()
            suffix != null -> result.take(suffix.range.first).trim()
            else -> break
        }
    }
    return if (result.isEmpty() || result == title.trim()) title else result.replace(repeatedSpace, " ")
}

fun formattedTrackTitle(title: String, artist: String, credits: List<String> = emptyList()): String {
    if (!Prefs.hideTitleArtists || title.isEmpty()) return title
    val fallback = if (credits.isEmpty()) creditJoin.split(artist) else emptyList()
    return withoutArtistCredits(title, credits + artist + fallback)
}

val Track.displayTitle: String
    get() {
        if (!Prefs.hideTitleArtists) return title
        return formattedTrackTitle(title, user?.username.orEmpty(), titleCredits())
    }
