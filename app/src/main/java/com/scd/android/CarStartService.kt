package com.scd.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class CarStartService : Service() {
    private val scope = MainScope()
    private var startup: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra("address")
        val name = intent?.getStringExtra("deviceName").orEmpty()
        val title = getString(R.string.car_section)
        val channel = "soundcloud_auto"
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(channel, title, NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(this, channel).setSmallIcon(R.drawable.ic_music)
            .setContentTitle(title).setContentText(name).setOnlyAlertOnce(true).setSilent(true).build()
        try {
            startForeground(1102, notification)
        } catch (e: RuntimeException) {
            if (address != null) CarAutoplay.startFailed(address)
            Logs.add("car", "$name: foreground start refused (${e.javaClass.simpleName})")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        startup?.cancel()
        startup = scope.launch {
            try {
                if (address == null) return@launch
                val finished = withTimeoutOrNull(120_000) {
                    CarAutoplay.runScenario(this@CarStartService, address, name)
                    true
                }
                if (finished == null) {
                    CarAutoplay.startFailed(address)
                    Logs.add("car", "$name: startup timed out")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (address != null) CarAutoplay.startFailed(address)
                Logs.add("car", "$name: autoplay failed (${e.javaClass.simpleName})")
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
