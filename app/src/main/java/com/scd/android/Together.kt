package com.scd.android

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class RoomMember(
    val userId: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val joinedAt: Double = 0.0,
    val readyUrn: String? = null,
)

@Serializable
data class RoomPlayback(
    val status: String = Together.IDLE,
    val track: JsonElement? = null,
    val trackUrn: String? = null,
    val nextTrack: JsonElement? = null,
    val positionMs: Double = 0.0,
    val at: Double = 0.0,
    val rate: Double = 1.0,
    val crossfadeSec: Double? = null,
)

@Serializable
data class RoomView(
    val code: String = "",
    val hostId: String = "",
    val version: Long = 0,
    val createdAt: Double = 0.0,
    val members: List<RoomMember> = emptyList(),
    val playback: RoomPlayback = RoomPlayback(),
    val online: List<String> = emptyList(),
    val serverNow: Double = 0.0,
    @SerialName("public") val isPublic: Boolean? = null,
)

@Serializable
data class OpenRoomTrack(
    val title: String = "",
    val artist: String? = null,
    val artworkUrl: String? = null,
)

@Serializable
data class OpenRoom(
    val hostId: String = "",
    val hostName: String = "",
    val hostAvatarUrl: String? = null,
    val status: String = Together.IDLE,
    val track: OpenRoomTrack? = null,
    val listeners: Int = 0,
    val capacity: Int = Together.ROOM_CAPACITY,
    val full: Boolean = false,
    val createdAt: Double = 0.0,
)

@Serializable
private data class OpenRoomsPage(val collection: List<OpenRoom> = emptyList())

class RoomException(val status: Int, val code: String?) : IOException("rooms $status ${code.orEmpty()}")

private object RoomsApi {
    const val POLL_TIMEOUT_MS = 35_000L
    const val QUICK_TIMEOUT_MS = 12_000L

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val quick: OkHttpClient by lazy {
        AudioRoute.builder(Api.http).cache(null)
            .connectTimeout(8, TimeUnit.SECONDS)
            .callTimeout(QUICK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build()
    }

    private val longPoll: OkHttpClient by lazy {
        AudioRoute.builder(Api.http).cache(null)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(POLL_TIMEOUT_MS + 5_000L, TimeUnit.MILLISECONDS)
            .callTimeout(POLL_TIMEOUT_MS + 8_000L, TimeUnit.MILLISECONDS)
            .build()
    }

    class Timed(val view: RoomView, val sentAt: Long, val receivedAt: Long)

    private val jsonType = "application/json".toMediaType()

    suspend fun raw(method: String, path: String, body: JsonElement? = null, long: Boolean = false): String =
        withContext(Dispatchers.IO) {
            val payload = body?.toString()?.toRequestBody(jsonType)
                ?: if (method == "GET" || method == "DELETE") null else "{}".toRequestBody(jsonType)
            val req = Request.Builder()
                .url(Endpoints.apiBase + path)
                .method(method, payload)
                .apply { Api.sessionId?.let { header("x-session-id", it) } }
                .build()
            (if (long) longPoll else quick).newCall(req).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    Api.noteAuthFailure(res.code, text, req.header("x-session-id"))
                    val code = runCatching {
                        ((json.parseToJsonElement(text) as? JsonObject)?.get("code") as? JsonPrimitive)?.content
                    }.getOrNull()
                    if (res.code !in listOf(403, 404, 409, 429, 503)) Logs.add("together", "$method $path → ${res.code}")
                    throw RoomException(res.code, code)
                }
                text
            }
        }

    suspend fun timed(method: String, path: String, body: JsonElement? = null, long: Boolean = false): Timed {
        val sentAt = System.currentTimeMillis()
        val text = raw(method, path, body, long)
        return Timed(json.decodeFromString(RoomView.serializer(), text), sentAt, System.currentTimeMillis())
    }

    private fun profile(extra: JsonObjectBuilderScope = {}): JsonObject = buildJsonObject {
        put("name", Prefs.username.orEmpty())
        put("avatarUrl", Prefs.avatarUrl)
        extra()
    }

    suspend fun create(open: Boolean) = timed("POST", "/rooms", profile { put("public", open) })
    suspend fun join(code: String) = timed("POST", "/rooms/$code/members", profile())
    suspend fun peek(code: String) = timed("GET", "/rooms/$code?follow=true")
    suspend fun poll(code: String, since: Long) = timed("GET", "/rooms/$code?since=$since&follow=true", long = true)
    suspend fun playback(code: String, update: JsonObject) = timed("PUT", "/rooms/$code/playback", update)
    suspend fun ready(code: String, urn: String) = timed("PUT", "/rooms/$code/ready", buildJsonObject { put("trackUrn", urn) })
    suspend fun visibility(code: String, open: Boolean) = timed("PUT", "/rooms/$code/visibility", buildJsonObject { put("public", open) })
    suspend fun joinOpen(hostId: String) = timed("POST", "/rooms/public/$hostId/members", profile())
    suspend fun leave(code: String) {
        raw("DELETE", "/rooms/$code/members/me")
    }

    suspend fun open(): List<OpenRoom> =
        json.decodeFromString(OpenRoomsPage.serializer(), raw("GET", "/rooms/public")).collection
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit

object Together {
    const val IDLE = "idle"
    const val LOADING = "loading"
    const val PLAYING = "playing"
    const val PAUSED = "paused"
    const val ROOM_CAPACITY = 10
    const val CODE_LENGTH = 6

    enum class Phase { IDLE, CONNECTING, LIVE, RECONNECTING }
    enum class Sync { SYNCED, CATCHING_UP, WAITING }

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var room by mutableStateOf<RoomView?>(null)
        private set
    var selfId by mutableStateOf<String?>(null)
        private set
    var sync by mutableStateOf(Sync.SYNCED)
        private set
    var waitingFor by mutableIntStateOf(0)
        private set

    val isHost: Boolean get() = room?.let { it.hostId == selfId } == true
    val isGuest: Boolean get() = room?.let { it.hostId != selfId } == true

    private const val CLOCK_WARMUP_SAMPLES = 3
    private const val CLOCK_REFRESH_MS = 60_000L
    private const val MAX_BACKOFF_MS = 15_000L
    private const val SAMPLE_LIMIT = 8

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var appContext: Context? = null
    private var player: Player? = null
    private var role: Role? = null
    private var generation = 0

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun attachPlayer(p: Player) {
        player = p
        role?.bind(p)
    }

    fun detachPlayer() {
        role?.bind(null)
        player = null
    }

    private class Sample(val offset: Double, val rtt: Long)

    private var samples = listOf<Sample>()

    private fun bestSample(): Sample? = samples.minByOrNull { it.rtt }

    fun serverTime(): Double = System.currentTimeMillis() + (bestSample()?.offset ?: 0.0)

    private fun oneWayMs(): Double = (bestSample()?.rtt ?: 0L) / 2.0

    fun positionAt(pb: RoomPlayback, serverTime: Double): Double =
        if (pb.status != PLAYING) pb.positionMs else pb.positionMs + (serverTime - pb.at) * pb.rate

    private fun accept(timed: RoomsApi.Timed, sampleClock: Boolean = true, follow: Boolean = false) {
        if (sampleClock) {
            val offset = timed.view.serverNow - (timed.sentAt + timed.receivedAt) / 2.0
            samples = (samples + Sample(offset, timed.receivedAt - timed.sentAt)).takeLast(SAMPLE_LIMIT)
        }
        val current = room ?: return
        if (phase == Phase.IDLE || timed.view.version < current.version) return
        if (current.code != timed.view.code && !follow) return
        room = timed.view
        announceMembers(timed.view, current)
        role?.onView(timed.view)
    }

    private fun onlineGuests(view: RoomView? = room): List<RoomMember> {
        if (view == null) return emptyList()
        return view.members.filter { it.userId != view.hostId && it.userId in view.online }
    }

    private fun ownId(): String? = Prefs.userUrn?.substringAfterLast(':')?.takeIf { it.isNotBlank() }

    private fun toast(text: String) {
        val ctx = appContext ?: return
        android.widget.Toast.makeText(ctx, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun str(id: Int, vararg args: Any): String = appContext?.getString(id, *args).orEmpty()

    private fun announceMembers(view: RoomView, previous: RoomView) {
        val self = selfId
        val before = previous.members.map { it.userId }.toSet()
        val after = view.members.map { it.userId }.toSet()
        view.members.filter { it.userId != self && it.userId !in before }.forEach { toast(str(R.string.together_joined, it.name)) }
        previous.members.filter { it.userId != self && it.userId !in after }.forEach { toast(str(R.string.together_left, it.name)) }
    }

    private enum class End { LEFT, ENDED, EVICTED }

    private fun endSession(reason: End) {
        generation++
        val wasGuest = isGuest
        role?.detach()
        role = null
        phase = Phase.IDLE
        room = null
        selfId = null
        sync = Sync.SYNCED
        waitingFor = 0
        if (reason == End.ENDED && wasGuest) toast(str(R.string.together_ended))
        if (reason == End.EVICTED) toast(str(R.string.together_evicted))
    }

    private suspend fun warmClock(gen: Int, code: String) {
        repeat(CLOCK_WARMUP_SAMPLES) {
            val timed = runCatching { RoomsApi.peek(code) }.getOrNull() ?: return
            if (gen != generation) return
            accept(timed, sampleClock = true, follow = true)
        }
    }

    private suspend fun pollLoop(gen: Int) {
        var failures = 0
        var lastPeek = System.currentTimeMillis()
        while (gen == generation) {
            val current = room ?: return
            try {
                val refreshClock = System.currentTimeMillis() - lastPeek > CLOCK_REFRESH_MS
                val timed = if (refreshClock) RoomsApi.peek(current.code) else RoomsApi.poll(current.code, current.version)
                if (gen != generation) return
                if (refreshClock) lastPeek = System.currentTimeMillis()
                accept(timed, sampleClock = refreshClock, follow = true)
                failures = 0
                if (phase == Phase.RECONNECTING) phase = Phase.LIVE
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (gen != generation) return
                if (e is RoomException && e.status == 404) {
                    endSession(if (e.code == "room_not_member") End.EVICTED else End.ENDED)
                    return
                }
                failures++
                phase = Phase.RECONNECTING
                delay(minOf(1000L shl failures.coerceAtMost(4), MAX_BACKOFF_MS))
            }
        }
    }

    private suspend fun enter(open: suspend () -> RoomsApi.Timed) {
        leave()
        val gen = ++generation
        phase = Phase.CONNECTING
        val timed = try {
            open()
        } catch (e: Exception) {
            if (gen == generation) phase = Phase.IDLE
            throw e
        }
        if (gen != generation) return
        samples = emptyList()
        phase = Phase.LIVE
        room = timed.view
        selfId = ownId()
        val next: Role = if (isHost) HostRole() else GuestRole()
        role = next
        next.bind(player)
        accept(timed)
        scope.launch { warmClock(gen, timed.view.code) }
        scope.launch { pollLoop(gen) }
    }

    suspend fun host(open: Boolean) = withContext(Dispatchers.Main) { enter { RoomsApi.create(open) } }

    suspend fun join(code: String) = withContext(Dispatchers.Main) { enter { RoomsApi.join(cleanCode(code)) } }

    suspend fun joinOpen(hostId: String) = withContext(Dispatchers.Main) { enter { RoomsApi.joinOpen(hostId) } }

    suspend fun openRooms(): List<OpenRoom> = RoomsApi.open()

    suspend fun setPublic(open: Boolean) = withContext(Dispatchers.Main) {
        val current = room ?: return@withContext
        val timed = RoomsApi.visibility(current.code, open)
        if (room?.code == current.code) accept(timed, sampleClock = false, follow = true)
    }

    suspend fun leave() = withContext(Dispatchers.Main) {
        val current = room ?: return@withContext
        endSession(End.LEFT)
        runCatching { RoomsApi.leave(current.code) }
        Unit
    }

    fun shareText(code: String): String = str(R.string.together_share_text, code)

    fun cleanCode(input: String): String =
        input.uppercase().filter { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" }.take(CODE_LENGTH)

    fun errorText(error: Throwable, openRoom: Boolean = false): String {
        val status = (error as? RoomException)?.status
        return str(
            when {
                status == 404 && openRoom -> R.string.together_err_closed
                status == 404 -> R.string.together_err_not_found
                status == 409 -> R.string.together_err_full
                status == 401 -> R.string.together_err_sign_in
                else -> R.string.together_err_generic
            },
        )
    }

    private fun roomTrack(track: Track): JsonObject = buildJsonObject {
        put("id", track.id.takeIf { it != 0L } ?: track.urn.substringAfterLast(':').toLongOrNull() ?: 0L)
        put("urn", track.urn)
        put("title", track.title)
        put("duration", track.duration)
        track.full_duration?.let { put("full_duration", it) }
        put("artwork_url", track.artwork_url)
        track.waveform_url?.let { put("waveform_url", it) }
        track.genre?.let { put("genre", it) }
        track.permalink_url?.let { put("permalink_url", it) }
        track.access?.let { put("access", it) }
        track.policy?.let { put("policy", it) }
        track.monetization_model?.let { put("monetization_model", it) }
        val user = track.user
        put(
            "user",
            buildJsonObject {
                put("id", user?.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L)
                put("urn", user?.urn.orEmpty())
                put("username", user?.username.orEmpty())
                put("avatar_url", user?.avatar_url.orEmpty())
            },
        )
    }

    private fun decodeTrack(element: JsonElement?): Track? =
        element?.let { runCatching { RoomsApi.json.decodeFromJsonElement(Track.serializer(), it) }.getOrNull() }

    private fun Player.nextTrack(): Track? {
        val i = nextMediaItemIndex
        if (i == C.INDEX_UNSET) return null
        return runCatching { getMediaItemAt(i).toTrackOrNull() }.getOrNull()
    }

    private interface Role {
        fun bind(p: Player?)
        fun onView(view: RoomView)
        fun detach()
    }

    private class HostRole : Role, Player.Listener {
        private var p: Player? = null
        private var pending: String? = null
        private var disposed = false
        private var publishQueued = false
        private val chain = Mutex()
        private var gateJob: Job? = null
        private val heartbeatJob = scope.launch {
            while (true) {
                delay(HEARTBEAT_MS)
                heartbeat()
            }
        }

        override fun bind(p: Player?) {
            this.p?.removeListener(this)
            this.p = p
            p?.addListener(this)
            publish()
        }

        override fun onView(view: RoomView) {}

        override fun detach() {
            disposed = true
            p?.removeListener(this)
            p = null
            gateJob?.cancel()
            heartbeatJob.cancel()
            waitingFor = 0
            sync = Sync.SYNCED
        }

        private fun update(status: String, track: Track?, positionMs: Long, rate: Float, leadMs: Long? = null) =
            buildJsonObject {
                put("status", status)
                if (track != null) {
                    put("track", roomTrack(track))
                    put("trackUrn", track.urn)
                }
                put("positionMs", positionMs)
                put("rate", rate)
                if (leadMs != null) put("leadMs", leadMs)
            }

        private fun currentUpdate(): JsonObject {
            val player = p ?: return buildJsonObject { put("status", IDLE) }
            val track = player.currentMediaItem?.toTrackOrNull() ?: return buildJsonObject { put("status", IDLE) }
            val status = when {
                !player.playWhenReady -> PAUSED
                player.playbackState == Player.STATE_READY -> PLAYING
                else -> LOADING
            }
            val lag = if (status == PLAYING) oneWayMs().toLong() else 0L
            return update(status, track, player.currentPosition + lag, player.playbackParameters.speed)
        }

        private fun send(next: () -> JsonObject?): kotlinx.coroutines.Deferred<RoomsApi.Timed?> = scope.async {
            chain.withLock {
                val code = room?.code ?: return@withLock null
                val body = next() ?: return@withLock null
                if (disposed) return@withLock null
                val full = JsonObject(
                    body + buildJsonObject {
                        put("crossfadeSec", if (Prefs.crossfade) Prefs.crossfadeSeconds else 0)
                        p?.nextTrack()?.let { put("nextTrack", roomTrack(it)) }
                    },
                )
                runCatching { RoomsApi.playback(code, full) }.getOrNull()?.also { accept(it) }
            }
        }

        private fun publish() {
            if (pending != null || disposed || publishQueued) return
            publishQueued = true
            send {
                publishQueued = false
                if (pending != null) null else currentUpdate()
            }
        }

        private fun unready(urn: String): Int = onlineGuests().count { it.readyUrn != urn }

        private suspend fun gate(urn: String) {
            val player = p ?: return
            val guests = onlineGuests().size
            var held = false
            if (guests > 0 && player.playWhenReady) {
                held = true
                player.playWhenReady = false
                sync = Sync.WAITING
                withTimeoutOrNull(BARRIER_TIMEOUT_MS) {
                    while (pending == urn) {
                        waitingFor = unready(urn)
                        if (waitingFor == 0 && player.playbackState == Player.STATE_READY) break
                        delay(100)
                    }
                }
                waitingFor = 0
                sync = Sync.SYNCED
            }
            if (disposed || pending != urn) return
            pending = null
            val track = player.currentMediaItem?.toTrackOrNull()
            if (!held || track == null || track.urn != urn) {
                publish()
                return
            }
            val timed = send { update(PLAYING, track, 0L, player.playbackParameters.speed, START_LEAD_MS) }.await()
            val wait = timed?.let { (it.view.playback.at - serverTime()).toLong() } ?: 0L
            if (wait > 0) delay(wait)
            if (disposed || p?.currentMediaItem?.mediaId != urn) return
            player.seekTo(0)
            player.playWhenReady = true
        }

        private fun heartbeat() {
            val current = room ?: return
            val player = p ?: return
            if (pending != null || player.playbackState != Player.STATE_READY) return
            val expected = currentUpdate()
            val status = (expected["status"] as? JsonPrimitive)?.content
            val urn = player.currentMediaItem?.mediaId
            val pb = current.playback
            if (pb.status != status || pb.trackUrn != urn) {
                publish()
                return
            }
            if (!player.isPlaying) return
            val drift = kotlin.math.abs(positionAt(pb, serverTime()) - player.currentPosition)
            if (drift > HOST_DRIFT_MS) publish()
        }

        override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
            val track = mediaItem?.toTrackOrNull()
            pending = track?.urn
            if (track == null) {
                publish()
                return
            }
            send { update(LOADING, track, 0L, p?.playbackParameters?.speed ?: 1f) }
            gateJob?.cancel()
            gateJob = scope.launch { gate(track.urn) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publish()

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) publish()
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publish()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) publish()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            val next = p?.nextTrack()?.urn
            val known = decodeTrack(room?.playback?.nextTrack)?.urn
            if (next != known) publish()
        }

        companion object {
            const val BARRIER_TIMEOUT_MS = 8_000L
            const val START_LEAD_MS = 700L
            const val HEARTBEAT_MS = 15_000L
            const val HOST_DRIFT_MS = 600.0
        }
    }

    private class GuestRole : Role, Player.Listener {
        private var p: Player? = null
        private var gating = false
        private var lastSignature = ""
        private var lastAlign = 0L
        private var lastNotice = 0L
        private val savedRate = Prefs.playbackRate
        private var gateJob: Job? = null
        private val driftJob = scope.launch {
            while (true) {
                delay(DRIFT_CHECK_MS)
                guardTrack()
                checkDrift(force = false)
            }
        }

        override fun bind(p: Player?) {
            this.p?.removeListener(this)
            this.p = p
            p?.addListener(this)
            lastSignature = ""
            room?.let { onView(it) }
        }

        override fun onView(view: RoomView) = follow(view)

        override fun detach() {
            p?.removeListener(this)
            p = null
            gateJob?.cancel()
            driftJob.cancel()
            Prefs.changePlaybackRate(savedRate)
            sync = Sync.SYNCED
        }

        private fun playback(): RoomPlayback? = room?.playback

        private fun hostName(): String {
            val view = room ?: return ""
            return view.members.firstOrNull { it.userId == view.hostId }?.name.orEmpty()
        }

        private fun noticeHostLeads() {
            val now = System.currentTimeMillis()
            if (now - lastNotice < NOTICE_COOLDOWN_MS) return
            lastNotice = now
            toast(str(R.string.together_host_leads, hostName()))
        }

        private fun follow(view: RoomView) {
            val pb = view.playback
            val signature = "${pb.trackUrn}|${pb.status}|${pb.positionMs}|${pb.at}|${pb.rate}"
            if (signature == lastSignature) return
            lastSignature = signature
            if (pb.status == IDLE || pb.trackUrn == null) return
            val track = decodeTrack(pb.track) ?: return
            if (kotlin.math.abs(Prefs.playbackRate - pb.rate) > 0.001) Prefs.changePlaybackRate(pb.rate.toFloat())
            val player = p ?: return
            val playing = pb.status != PAUSED
            if (player.currentMediaItem?.mediaId != pb.trackUrn) {
                sync = Sync.CATCHING_UP
                load(player, track)
                return
            }
            if (!gating && player.playWhenReady != playing) player.playWhenReady = playing
            if (!playing && player.playbackState == Player.STATE_READY) player.seekTo(pb.positionMs.toLong())
            checkDrift(force = true)
        }

        private fun load(player: Player, track: Track) {
            gating = true
            player.setMediaItem(track.toMediaItem())
            player.prepare()
            player.playWhenReady = false
            gateJob?.cancel()
            gateJob = scope.launch { gate(track.urn) }
        }

        private suspend fun gate(urn: String) {
            try {
                withTimeoutOrNull(GATE_TIMEOUT_MS) {
                    while (p?.playbackState != Player.STATE_READY) delay(100)
                }
                room?.code?.let { code ->
                    scope.launch { runCatching { RoomsApi.ready(code, urn) }.getOrNull()?.let { accept(it) } }
                }
                withTimeoutOrNull(GATE_TIMEOUT_MS) {
                    while (true) {
                        val pb = playback()
                        if (pb == null || pb.trackUrn != urn || pb.status != LOADING) break
                        delay(100)
                    }
                }
            } finally {
                gating = false
            }
            val pb = playback() ?: return
            val player = p ?: return
            if (pb.trackUrn != urn || player.currentMediaItem?.mediaId != urn) return
            if (pb.status != PLAYING) {
                player.seekTo(pb.positionMs.toLong())
                player.playWhenReady = pb.status != PAUSED
                return
            }
            val position = positionAt(pb, serverTime())
            if (position < 0) {
                delay((-position / pb.rate).toLong())
                player.seekTo(0)
            } else {
                player.seekTo(position.toLong())
            }
            player.playWhenReady = true
            sync = Sync.SYNCED
        }

        private fun guardTrack() {
            if (gating) return
            val pb = playback() ?: return
            val player = p ?: return
            if (pb.status == IDLE || pb.trackUrn == null) return
            val current = player.currentMediaItem?.mediaId ?: return
            if (current == pb.trackUrn) return
            noticeHostLeads()
            lastSignature = ""
            room?.let { follow(it) }
        }

        private fun checkDrift(force: Boolean) {
            val pb = playback() ?: return
            val player = p ?: return
            if (gating || pb.status != PLAYING || !player.playWhenReady ||
                player.playbackState != Player.STATE_READY || player.currentMediaItem?.mediaId != pb.trackUrn
            ) {
                return
            }
            val expected = positionAt(pb, serverTime())
            val duration = player.duration
            if (expected < 0 || (duration != C.TIME_UNSET && expected > duration)) return
            val off = kotlin.math.abs(player.currentPosition - expected) > DRIFT_TOLERANCE_MS
            sync = if (off) Sync.CATCHING_UP else Sync.SYNCED
            val now = System.currentTimeMillis()
            if (!off || (!force && now - lastAlign < ALIGN_COOLDOWN_MS)) return
            lastAlign = now
            player.seekTo(expected.toLong())
        }

        companion object {
            const val DRIFT_TOLERANCE_MS = 350.0
            const val ALIGN_COOLDOWN_MS = 2_500L
            const val DRIFT_CHECK_MS = 1_000L
            const val GATE_TIMEOUT_MS = 15_000L
            const val NOTICE_COOLDOWN_MS = 3_000L
        }
    }
}
