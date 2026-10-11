package com.scd.android

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Downloads.NOTIF_ID,
                    Downloads.startingNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(Downloads.NOTIF_ID, Downloads.startingNotification(this))
            }
        }
        if (started.isFailure) {
            Logs.add("download", "foreground refused: ${started.exceptionOrNull()?.javaClass?.simpleName}")
            Downloads.cancelAll(this)
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            Downloads.drain()
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int) {
        stopOnTimeout()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stopOnTimeout()
    }

    private fun stopOnTimeout() {
        Logs.add("download", "system time limit for background downloads reached, stopping")
        scope.cancel()
        Downloads.cancelAll(this)
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
