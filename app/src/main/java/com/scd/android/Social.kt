package com.scd.android

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

fun scIdOf(urn: String?): String? {
    val raw = urn?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if (raw.all { it.isDigit() }) return raw
    return raw.substringAfterLast(':').takeIf { it.isNotEmpty() && it.all { c -> c.isDigit() } }
}

val Track.hidden: Boolean get() = Dislikes.isDisliked(urn) || BlockedArtists.blocks(this)

object BlockedArtists {
    private lateinit var sp: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true }
    private const val KEY = "entries"

    var entries by mutableStateOf<List<BlockedArtist>>(emptyList())
        private set

    private var indexed: List<BlockedArtist>? = null
    private var users: Set<String> = emptySet()
    private var artists: Set<String> = emptySet()

    fun init(context: Context) {
        sp = context.getSharedPreferences("blocked_artists", Context.MODE_PRIVATE)
        entries = sp.getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(BlockedArtist.serializer()), it) }.getOrNull() }
            .orEmpty()
    }

    private fun save(list: List<BlockedArtist>) {
        entries = list
        if (::sp.isInitialized) runCatching { sp.edit().putString(KEY, json.encodeToString(ListSerializer(BlockedArtist.serializer()), list)).apply() }
    }

    fun reset() = save(emptyList())

    suspend fun refresh() {
        if (Prefs.userUrn == null || Prefs.offline) return
        runCatching { Api.blockedArtists() }.onSuccess { save(it) }
    }

    private fun index() {
        val current = entries
        if (current === indexed) return
        val u = HashSet<String>()
        val a = HashSet<String>()
        for (e in current) {
            if (e.kind == "artist") a.add(e.id) else u.add(e.id)
            u.addAll(e.sc_user_ids)
        }
        users = u
        artists = a
        indexed = current
    }

    fun blocks(t: Track): Boolean {
        if (entries.isEmpty()) return false
        index()
        scIdOf(t.user?.urn)?.let { if (it in users) return true }
        val e = t.enrichment ?: return false
        e.primary_artist?.let { p ->
            if (p.id in artists) return true
            if (scIdOf(p.sc_user_id)?.let { it in users } == true) return true
        }
        return e.participants.any { it.artist.id in artists }
    }

    fun isBlocked(kind: String, id: String): Boolean = entries.any { it.kind == kind && it.id == id }

    fun userTarget(urn: String, name: String, avatar: String?): BlockedArtist? {
        val id = scIdOf(urn) ?: return null
        return BlockedArtist("user", id, name, avatar, listOf(id))
    }

    fun artistTarget(a: CatalogArtist) = BlockedArtist("artist", a.id, a.name, a.avatar_url, a.mainScUsers)

    fun targets(t: Track): List<BlockedArtist> {
        val out = mutableListOf<BlockedArtist>()
        t.catalogArtist?.let { p ->
            out += BlockedArtist("artist", p.id, p.name, p.avatar_url, listOfNotNull(scIdOf(p.sc_user_id)))
        }
        val u = t.user
        if (u?.urn != null) {
            userTarget(u.urn, u.username, u.avatar_url)?.let { up ->
                if (out.none { up.id in it.sc_user_ids }) out += up
            }
        }
        return out
    }

    suspend fun block(entry: BlockedArtist): Boolean = withContext(NonCancellable) {
        val before = entries
        save(before.filterNot { it.kind == entry.kind && it.id == entry.id } + entry)
        val ok = runCatching { Api.blockArtist(entry) }.isSuccess
        if (!ok) save(before)
        if (ok) Logs.add("block", "${entry.kind} ${entry.name.take(30)}")
        ok
    }

    suspend fun unblock(kind: String, id: String): Boolean = withContext(NonCancellable) {
        val before = entries
        save(before.filterNot { it.kind == kind && it.id == id })
        val ok = runCatching { Api.unblockArtist(kind, id) }.isSuccess
        if (!ok) save(before)
        ok
    }
}

object Follows {
    private val state = mutableStateMapOf<String, Boolean>()
    private val busy = mutableStateMapOf<String, Boolean>()

    fun isFollowing(urn: String): Boolean? = state[urn]

    fun isBusy(urn: String): Boolean = busy[urn] == true

    fun canFollow(urn: String): Boolean {
        val me = Prefs.userUrn ?: return false
        return !Prefs.offline && scIdOf(me) != scIdOf(urn)
    }

    suspend fun load(urn: String) {
        val me = Prefs.userUrn ?: return
        if (!canFollow(urn)) return
        runCatching { Api.isFollowing(me, urn) }.onSuccess { state[urn] = it }
    }

    suspend fun toggle(urn: String): Boolean = withContext(NonCancellable) {
        val next = state[urn] != true
        state[urn] = next
        busy[urn] = true
        val ok = runCatching { if (next) Api.follow(urn) else Api.unfollow(urn) }.isSuccess
        if (!ok) state[urn] = !next
        busy.remove(urn)
        ok
    }
}

data class AuraPreset(val id: String, val orbs: List<Int>, val accent: Int)

object Auras {
    const val CUSTOM = "custom"
    const val DEFAULT_HEX = "#A855F7"

    val presets = listOf(
        AuraPreset("aurora", listOf(0xFF7C3AED, 0xFF06B6D4, 0xFFEC4899).map { it.toInt() }, 0xFFA855F7.toInt()),
        AuraPreset("magma", listOf(0xFFFF5500, 0xFFFF0080, 0xFFFF8A00).map { it.toInt() }, 0xFFFF5500.toInt()),
        AuraPreset("cyber", listOf(0xFF06B6D4, 0xFF3B82F6, 0xFF10B981).map { it.toInt() }, 0xFF06B6D4.toInt()),
        AuraPreset("void", listOf(0xFF3F3F46, 0xFF52525B, 0xFF71717A).map { it.toInt() }, 0xFFD4D4DC.toInt()),
        AuraPreset("sunset", listOf(0xFFF97316, 0xFFFB7185, 0xFFA855F7).map { it.toInt() }, 0xFFFB7185.toInt()),
        AuraPreset("forest", listOf(0xFF10B981, 0xFF84CC16, 0xFF065F46).map { it.toInt() }, 0xFF10B981.toInt()),
        AuraPreset("ocean", listOf(0xFF0EA5E9, 0xFF06B6D4, 0xFF1E3A8A).map { it.toInt() }, 0xFF0EA5E9.toInt()),
    )

    private lateinit var sp: SharedPreferences
    private val cache = mutableStateMapOf<String, AuraInfo>()

    var mine by mutableStateOf<AuraInfo?>(null)
        private set

    fun init(context: Context) {
        sp = context.getSharedPreferences("aura", Context.MODE_PRIVATE)
        val id = sp.getString("id", null)
        if (id != null) mine = AuraInfo(id, sp.getString("hex", null))
    }

    private fun storeMine(info: AuraInfo?) {
        mine = info?.takeIf { it.aura_id != null }
        if (!::sp.isInitialized) return
        sp.edit().apply {
            if (mine == null) clear() else putString("id", mine?.aura_id).putString("hex", mine?.custom_hex)
        }.apply()
    }

    fun reset() = storeMine(null)

    fun of(urn: String?): AuraInfo? {
        if (urn == null) return null
        if (scIdOf(urn) != null && scIdOf(urn) == scIdOf(Prefs.userUrn)) return mine
        return cache[urn]
    }

    suspend fun load(urn: String) {
        if (Prefs.offline) return
        val info = runCatching { Api.userAura(urn) }.getOrNull() ?: return
        if (scIdOf(urn) == scIdOf(Prefs.userUrn)) storeMine(info) else cache[urn] = info
    }

    suspend fun loadMine() {
        Prefs.userUrn?.let { load(it) }
    }

    suspend fun setMine(id: String, hex: String?): Boolean = withContext(Dispatchers.IO) {
        val ok = runCatching { Api.setAura(id, if (id == CUSTOM) hex else null) }.isSuccess
        if (ok) withContext(Dispatchers.Main) { storeMine(AuraInfo(id, if (id == CUSTOM) hex else null)) }
        ok
    }

    private fun parseHex(hex: String?): Int? {
        val h = hex?.removePrefix("#") ?: return null
        if (h.length != 6) return null
        return h.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
    }

    private fun mix(c: Int, target: Int, amt: Float): Int {
        fun ch(v: Int, s: Int) = ((v shr s) and 0xFF)
        fun m(a: Int, b: Int) = (a + (b - a) * amt).toInt().coerceIn(0, 255)
        val r = m(ch(c, 16), ch(target, 16))
        val g = m(ch(c, 8), ch(target, 8))
        val b = m(ch(c, 0), ch(target, 0))
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun orbs(info: AuraInfo?): List<Int>? {
        val id = info?.aura_id ?: return null
        if (id == CUSTOM) {
            val c = parseHex(info.custom_hex) ?: return null
            return listOf(c, mix(c, 0xFFFFFFFF.toInt(), 0.25f), mix(c, 0xFF000000.toInt(), 0.35f))
        }
        return presets.firstOrNull { it.id == id }?.orbs
    }

    fun accent(info: AuraInfo?): Int? {
        val id = info?.aura_id ?: return null
        if (id == CUSTOM) return parseHex(info.custom_hex)
        return presets.firstOrNull { it.id == id }?.accent
    }
}

object DiscordInfo {
    var link by mutableStateOf<DiscordLink?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun reset() {
        link = null
        loaded = false
        failed = false
    }

    suspend fun load() {
        if (Prefs.userUrn == null || Prefs.offline) return
        runCatching { Api.paySubscription() }
            .onSuccess {
                link = it.discord
                loaded = true
                failed = false
            }
            .onFailure { failed = true }
    }
}

object WebProfiles {
    private val cache = mutableStateMapOf<String, List<WebProfile>>()

    fun of(urn: String): List<WebProfile> = cache[urn].orEmpty()

    suspend fun load(urn: String) {
        if (Prefs.offline || cache.containsKey(urn)) return
        runCatching { Api.webProfiles(urn) }.onSuccess { cache[urn] = it.filter { p -> p.url.startsWith("http") } }
    }
}

fun isScUrn(urn: String?): Boolean = urn != null && urn.startsWith("soundcloud:")

val Track.external: Boolean get() = !isScUrn(urn)
