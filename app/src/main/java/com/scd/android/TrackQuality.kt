package com.scd.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class Quality(val label: String, val rank: Int) {
    SQ("sq", 0),
    HQ("hq", 1),
    UQ("uq", 2);

    val badge: String get() = label.uppercase()

    companion object {
        fun parse(value: String?): Quality? = when (value?.trim()?.lowercase()) {
            "sq" -> SQ
            "hq" -> HQ
            "uq" -> UQ
            else -> null
        }

        fun of(value: String?): Quality = parse(value) ?: SQ

        fun fromLabel(label: String?): Quality = parse(label?.trim()?.substringBefore(' ')) ?: SQ
    }
}

object QualityLadder {
    const val BACKUP_MS = 15_000L
    const val REFUSAL_TTL_MS = 600_000L

    fun wanted(): Quality = when (Prefs.streamQuality) {
        Prefs.QUALITY_SQ -> Quality.SQ
        Prefs.QUALITY_HQ -> Quality.HQ
        else -> if (Prefs.star) Quality.UQ else Quality.HQ
    }

    fun wantsUpgrade(): Boolean =
        Prefs.streamQuality == Prefs.QUALITY_HQ || Prefs.streamQuality == Prefs.QUALITY_UQ

    fun upgradeWanted(star: Boolean = Prefs.star): Quality = if (star) Quality.UQ else Quality.HQ

    fun downloadWanted(): Quality = DownloadQuality.wanted(Prefs.downloadQuality, Prefs.star)

    fun upperTiers(wanted: Quality, above: Quality = Quality.SQ): List<Quality> =
        listOf(Quality.UQ, Quality.HQ).filter { it.rank <= wanted.rank && it.rank > above.rank }

    fun tierUrl(url: String, tier: Quality): String {
        if (HlsDownload.isHls(url) || EncHls.isEnc(url)) return url
        val q = url.indexOf('?')
        val base = if (q < 0) url else url.substring(0, q)
        val kept = if (q < 0) {
            emptyList()
        } else {
            url.substring(q + 1).split('&').filter {
                it.isNotEmpty() && !it.startsWith("hq=") && !it.startsWith("quality=")
            }
        }
        val params = kept + (if (tier.rank > Quality.SQ.rank) listOf("hq=true") else emptyList()) +
            "quality=${tier.label}"
        return base + "?" + params.joinToString("&")
    }

    fun tierOf(url: String): Quality {
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return Quality.SQ
        val params = query.split('&')
        params.firstOrNull { it.startsWith("quality=") }
            ?.let { Quality.parse(it.removePrefix("quality=")) }
            ?.let { return it }
        return if (params.any { it == "hq=true" }) Quality.HQ else Quality.SQ
    }

    private class Refusal(val session: String?, val tier: Quality, val until: Long) {
        fun holds(session: String?): Boolean =
            this.session == session && System.currentTimeMillis() < until
    }

    @Volatile
    private var refusal: Refusal? = null

    private fun below(tier: Quality): Quality = if (tier == Quality.UQ) Quality.HQ else Quality.SQ

    fun allowed(wanted: Quality, session: String? = Api.sessionId): Quality {
        val r = refusal ?: return wanted
        return if (r.holds(session) && wanted.rank >= r.tier.rank) below(r.tier) else wanted
    }

    fun refuses(tier: Quality, session: String? = Api.sessionId): Boolean =
        allowed(tier, session).rank < tier.rank

    @Synchronized
    fun noteRefused(tier: Quality, holdMs: Long, session: String? = Api.sessionId) {
        if (tier == Quality.SQ || holdMs <= 0L) return
        val hold = minOf(holdMs, REFUSAL_TTL_MS)
        val current = refusal
        if (current != null && current.holds(session) && current.tier.rank <= tier.rank) return
        refusal = Refusal(session, tier, System.currentTimeMillis() + hold)
        Logs.add(
            "stream",
            "server refused ${tier.label} for this session, asking at most ${below(tier).label} for ${hold / 1000} s",
        )
    }

    fun forgetRefusal() {
        refusal = null
    }

    fun refusalHold(code: Int, retryAfter: String?): Long? = when (code) {
        403 -> REFUSAL_TTL_MS
        429 -> retryAfter?.trim()?.toLongOrNull()?.takeIf { it > 0L }?.times(1000L)
        else -> null
    }

    class Verdict {
        private val holds = HashMap<Quality, Long?>()
        private val pending = HashMap<Quality, Int>()
        private val served = HashSet<Quality>()

        @Synchronized
        fun asked(tier: Quality) {
            if (tier == Quality.SQ) return
            pending[tier] = (pending[tier] ?: 0) + 1
            if (!holds.containsKey(tier)) holds[tier] = 0L
        }

        @Synchronized
        fun answered(tier: Quality, refusedFor: Long?) {
            if (tier == Quality.SQ) return
            pending[tier] = ((pending[tier] ?: 1) - 1).coerceAtLeast(0)
            val prev = if (holds.containsKey(tier)) holds[tier] else 0L
            holds[tier] = if (prev == null || refusedFor == null) null else maxOf(prev, refusedFor)
        }

        @Synchronized
        fun served(tier: Quality) {
            served += tier
        }

        @Synchronized
        fun settle() {
            for ((tier, hold) in holds) {
                if (tier in served || (pending[tier] ?: 0) > 0) continue
                if (hold != null && hold > 0L) QualityLadder.noteRefused(tier, hold)
            }
        }
    }
}

object ServedQuality {
    private val map = ConcurrentHashMap<String, String>()

    @Volatile
    private var loaded = false

    @Volatile
    private var saveQueued = false

    private fun file(): File? = ScDataSource.cacheDir?.let { File(it, "served_quality.txt") }

    private fun load() {
        if (loaded) return
        synchronized(map) {
            if (loaded) return
            runCatching {
                file()?.takeIf { it.exists() }?.forEachLine { line ->
                    val i = line.indexOf(' ')
                    if (i > 0) map.putIfAbsent(line.substring(0, i), line.substring(i + 1))
                }
            }
            loaded = true
        }
    }

    fun of(urn: String): Quality? {
        load()
        return Quality.parse(map[urn])
    }

    fun record(urn: String, tier: Quality) {
        load()
        if (map[urn] == tier.label) return
        if (map.size > 20_000) map.clear()
        map[urn] = tier.label
        scheduleSave()
    }

    fun forget(urn: String) {
        load()
        if (map.remove(urn) != null) scheduleSave()
    }

    private fun scheduleSave() {
        if (saveQueued) return
        saveQueued = true
        App.scope.launch(Dispatchers.IO) {
            delay(5_000L)
            saveQueued = false
            runCatching {
                val target = file() ?: return@runCatching
                val tmp = File(target.path + ".tmp")
                tmp.writeText(map.entries.joinToString("\n") { "${it.key} ${it.value}" })
                tmp.renameTo(target)
            }
        }
    }
}

object DownloadQuality {
    fun restore(saved: String?, legacyHq: Boolean): String = when (saved) {
        Prefs.QUALITY_AUTO, Prefs.QUALITY_SQ, Prefs.QUALITY_HQ, Prefs.QUALITY_UQ -> saved
        else -> if (legacyHq) Prefs.QUALITY_AUTO else Prefs.QUALITY_SQ
    }

    fun wanted(setting: String, star: Boolean): Quality = when (setting) {
        Prefs.QUALITY_SQ -> Quality.SQ
        Prefs.QUALITY_HQ -> Quality.HQ
        else -> if (star) Quality.UQ else Quality.HQ
    }
}
