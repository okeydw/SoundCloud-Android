package com.scd.android

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothProfile
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class PairedDevice(val address: String, val name: String)

object CarAutoplay {
    const val LIKED = "liked"
    const val WAVE = "wave"
    const val DOWNLOADS = "downloads"
    const val PLAYLIST_PREFIX = "playlist:"

    private var connectedAddress: String? = null

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

    private fun audioReady(context: Context, address: String, name: String): Boolean {
        val manager = context.getSystemService(AudioManager::class.java) ?: return false
        val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { AudioOutput.isBluetooth(it) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return outputs.isNotEmpty()
        return outputs.any { it.address.equals(address, ignoreCase = true) || it.productName.toString() == name } ||
            outputs.singleOrNull()?.let { it.address.isBlank() || it.address == "02:00:00:00:00:00" } == true
    }

    @SuppressLint("MissingPermission")
    fun onConnected(context: Context, device: BluetoothDevice) {
        if (!Prefs.carAutoplay || needsPermission(context)) return
        val address = device.address ?: return
        if (address == connectedAddress || CarScenarios.forDevice(address) == null) return
        val name = runCatching { device.name }.getOrNull() ?: address
        val intent = Intent(context, CarStartService::class.java)
            .putExtra("address", address).putExtra("deviceName", name)
        try {
            ContextCompat.startForegroundService(context, intent)
            connectedAddress = address
        } catch (e: RuntimeException) {
            Logs.add("car", "$name: background start refused (${e.javaClass.simpleName})")
        }
    }

    fun onDisconnected(context: Context, address: String) {
        if (connectedAddress != null && address != connectedAddress) return
        if (connectedAddress == null && (Prefs.carEqBackup() == null || CarScenarios.forDevice(address) == null)) return
        connectedAddress = null
        context.stopService(Intent(context, CarStartService::class.java))
        EqPresets.restoreCar()
    }

    internal fun startFailed(address: String) {
        if (connectedAddress == address) connectedAddress = null
    }

    internal suspend fun runScenario(context: Context, address: String, name: String) {
        fun connected() = Prefs.carAutoplay && connectedAddress == address
        if (!connected()) return
        val routed = withTimeoutOrNull(20_000) {
            while (connected() && !audioReady(context, address, name)) delay(250)
            connected()
        } == true
        if (!routed) {
            Logs.add("car", "$name: Bluetooth audio output did not become ready")
            startFailed(address)
            return
        }
        val scenario = CarScenarios.forDevice(address) ?: return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controller = connect(context, token)
        try {
            fun ready() = connected() && audioReady(context, address, name)
            if (!ready()) return
            EqPresets.applyCar(scenario.eq)
            if (controller.isPlaying || controller.playWhenReady && controller.playbackState != androidx.media3.common.Player.STATE_ENDED) return
            val finished = controller.playbackState == androidx.media3.common.Player.STATE_ENDED && !controller.hasNextMediaItem()
            if (controller.currentMediaItem == null || finished) {
                val tracks = withContext(Dispatchers.IO) {
                    val online = hasNetwork(context) && !Prefs.offline
                    tracksFor(scenario.source, online)
                        .filter { online || Downloads.isDownloaded(it.urn) }
                        .ifEmpty { Downloads.tracks() }
                        .filter { Playback.playable(it) && !it.hidden }
                }
                if (!ready() || controller.isPlaying) return
                if (tracks.isEmpty()) {
                    Logs.add("car", "$name: nothing to play")
                    return
                }
                NowPlaying.autoContinue = scenario.source == WAVE
                LocalRadio.stopTrackRadio()
                val queue = if (scenario.shuffle) tracks.shuffled() else tracks
                controller.setMediaItems(queue.map { it.toMediaItem() }, 0, 0)
            }
            if (controller.playbackState == androidx.media3.common.Player.STATE_IDLE) controller.prepare()
            if (!scenario.autostart) {
                Logs.add("car", "$name: queue ready, waiting for play")
                return
            }
            controller.play()
            Logs.add("car", "$name: starting playback")
            withTimeoutOrNull(30_000) {
                while (connected() && controller.playWhenReady && !controller.isPlaying && controller.playerError == null) delay(250)
            }
            Logs.add("car", "$name: ${stateName(controller)}")
        } finally {
            controller.release()
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
        if (action != BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED &&
            !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && action == BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)) return
        if (CarAutoplay.needsPermission(context)) return
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        device ?: return
        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
            BluetoothProfile.STATE_CONNECTED -> CarAutoplay.onConnected(context, device)
            BluetoothProfile.STATE_DISCONNECTED -> CarAutoplay.onDisconnected(context, device.address)
        }
    }
}
