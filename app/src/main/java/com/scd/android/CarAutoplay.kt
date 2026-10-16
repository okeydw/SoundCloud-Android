package com.scd.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PairedDevice(val address: String, val name: String)

object CarAutoplay {
    const val LIKED = "liked"
    const val WAVE = "wave"
    const val DOWNLOADS = "downloads"
    const val PLAYLIST_PREFIX = "playlist:"

    @Volatile
    private var lastStart = 0L

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun pairedDevices(context: Context): List<PairedDevice> {
        if (needsPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.orEmpty()
                .map { PairedDevice(it.address, it.name ?: it.address) }
                .sortedBy { it.name.lowercase() }
        }.getOrDefault(emptyList())
    }

    private fun a2dpActive(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
    }

    @SuppressLint("MissingPermission")
    fun onConnected(context: Context, device: BluetoothDevice?, pending: BroadcastReceiver.PendingResult) {
        val app = context.applicationContext
        Prefs.init(app)
        if (!Prefs.carAutoplay) {
            pending.finish()
            return
        }
        val address = runCatching { device?.address }.getOrNull()
        val name = runCatching { device?.name }.getOrNull() ?: address ?: "?"
        val scenario = CarScenarios.forDevice(address)
        if (scenario == null) {
            Logs.add("car", "$name: no scenario for this device")
            pending.finish()
            return
        }
        val now = System.currentTimeMillis()
        val autoplay = now - lastStart >= 60_000L
        if (autoplay) lastStart = now
        App.scope.launch {
            try {
                val routed = withTimeoutOrNull(12_000) {
                    while (!a2dpActive(app)) delay(500)
                    true
                } ?: false
                if (!routed) {
                    Logs.add("car", "$name connected, but audio is not routed to it")
                    return@launch
                }
                withContext(Dispatchers.Main) { EqPresets.applyCar(scenario.eq) }
                if (autoplay) start(app, name, scenario) else resume(app, name, scenario)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logs.add("car", "autoplay failed: ${e.javaClass.simpleName} ${e.message?.take(80) ?: ""}")
            } finally {
                pending.finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun onDisconnected(context: Context, device: BluetoothDevice?) {
        Prefs.init(context.applicationContext)
        if (Prefs.carEqBackup() == null) return
        val address = runCatching { device?.address }.getOrNull()
        if (CarScenarios.forDevice(address) == null) return
        App.scope.launch(Dispatchers.Main) { EqPresets.restoreCar() }
    }

    private suspend fun resume(context: Context, deviceName: String, scenario: CarScenario): Boolean = withContext(Dispatchers.Main) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = connect(context, token)
        try {
            if (controller.isPlaying) {
                Logs.add("car", "$deviceName: already playing")
                return@withContext true
            }
            val finished = controller.playbackState == androidx.media3.common.Player.STATE_ENDED && !controller.hasNextMediaItem()
            if (controller.mediaItemCount == 0 || controller.currentMediaItem == null || finished) return@withContext false
            if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) controller.prepare()
            val title = controller.currentMediaItem?.mediaMetadata?.title?.toString()?.take(30) ?: "?"
            if (!scenario.autostart) {
                Logs.add("car", "$deviceName: kept the current queue ($title), waiting for play")
                return@withContext true
            }
            controller.play()
            Logs.add("car", "$deviceName: continuing $title")
            var resumes = 0
            repeat(10) {
                delay(500)
                if (!controller.playWhenReady && resumes < 2) {
                    resumes++
                    controller.play()
                }
            }
            true
        } finally {
            controller.release()
        }
    }

    private suspend fun start(context: Context, deviceName: String, scenario: CarScenario) {
        if (resume(context, deviceName, scenario)) return
        val online = hasNetwork(context) && !Prefs.offline
        var tracks = tracksFor(scenario.source, online)
        if (!online) tracks = tracks.filter { Downloads.isDownloaded(it.urn) }
        if (tracks.isEmpty() && scenario.source != DOWNLOADS) {
            Logs.add("car", "source is empty, falling back to downloads")
            tracks = Downloads.tracks()
        }
        tracks = tracks.filter { Playback.playable(it) && !it.hidden }
        if (scenario.shuffle) tracks = tracks.shuffled()
        if (tracks.isEmpty()) {
            Logs.add("car", "$deviceName: nothing to play")
            return
        }
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = connect(context, token)
            try {
                if (controller.isPlaying) {
                    Logs.add("car", "$deviceName: already playing")
                    return@withContext
                }
                NowPlaying.autoContinue = scenario.source == WAVE
                LocalRadio.stopTrackRadio()
                controller.setMediaItems(tracks.map { it.toMediaItem() }, 0, 0)
                controller.prepare()
                if (!scenario.autostart) {
                    Logs.add("car", "$deviceName: ${sourceLabelForLog(scenario)} (${tracks.size}) is ready, waiting for play")
                    return@withContext
                }
                controller.play()
                Logs.add("car", "$deviceName: playing ${sourceLabelForLog(scenario)} (${tracks.size})")
                var resumes = 0
                repeat(20) {
                    delay(500)
                    if (!controller.playWhenReady && resumes < 2) {
                        resumes++
                        Logs.add("car", "$deviceName: paused right after start (${stateName(controller)}), resuming")
                        controller.play()
                    }
                }
                Logs.add("car", "$deviceName: ${stateName(controller)}")
            } finally {
                controller.release()
            }
        }
    }

    private suspend fun connect(context: Context, token: SessionToken): MediaController =
        suspendCancellableCoroutine { cont ->
            val future = MediaController.Builder(context, token).buildAsync()
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { cont.resumeWith(Result.success(it)) }
                        .onFailure { cont.resumeWith(Result.failure(it)) }
                },
                ContextCompat.getMainExecutor(context),
            )
            cont.invokeOnCancellation { MediaController.releaseFuture(future) }
        }

    private fun stateName(c: MediaController): String {
        val state = when (c.playbackState) {
            androidx.media3.common.Player.STATE_IDLE -> "idle"
            androidx.media3.common.Player.STATE_BUFFERING -> "buffering"
            androidx.media3.common.Player.STATE_READY -> "ready"
            else -> "ended"
        }
        val suppressed = if (c.playbackSuppressionReason != androidx.media3.common.Player.PLAYBACK_SUPPRESSION_REASON_NONE) " suppressed=${c.playbackSuppressionReason}" else ""
        val error = c.playerError?.let { " error=${it.errorCodeName}" } ?: ""
        return "playing=${c.isPlaying} play=${c.playWhenReady} state=$state$suppressed$error"
    }

    private fun sourceLabelForLog(s: CarScenario): String = s.name.ifBlank { s.sourceTitle.ifEmpty { s.source } }

    suspend fun tracksFor(source: String, online: Boolean): List<Track> = when {
        source == LIKED -> {
            val remote = if (online) runCatching { Api.likedTracks(0, 100) }.getOrNull()?.collection else null
            remote ?: Downloads.tracks().filter { Likes.isLiked(it.urn) }
        }
        source == WAVE -> {
            val remote = if (online) runCatching { Api.waveTracks(null).first }.getOrNull() else null
            (remote ?: emptyList()).ifEmpty { WaveState.tracks }
        }
        source == DOWNLOADS -> Downloads.tracks()
        source.startsWith(PLAYLIST_PREFIX) -> {
            val urn = source.removePrefix(PLAYLIST_PREFIX)
            runCatching { Library.playlistPage(urn, 0, fresh = online, offline = !online).first }.getOrNull()
                ?: LocalPlaylists.find(urn)?.tracks
                ?: emptyList()
        }
        else -> emptyList()
    }
}

class CarConnectReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != BluetoothDevice.ACTION_ACL_CONNECTED && action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        if (action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            CarAutoplay.onDisconnected(context, device)
        } else {
            CarAutoplay.onConnected(context, device, goAsync())
        }
    }
}
