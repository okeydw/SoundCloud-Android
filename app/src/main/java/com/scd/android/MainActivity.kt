package com.scd.android

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        Prefs.init(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.language))
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun handleIntentExtras(intent: Intent?) {
        intent ?: return
        if (intent.getBooleanExtra("open_player", false)) {
            NowPlaying.openPlayerRequest = true
        }
        intent.getStringExtra("open_playlist_urn")?.let { urn ->
            NavEvents.openPlaylist(urn, intent.getStringExtra("open_playlist_title") ?: "")
        }
        if (intent.action != Intent.ACTION_VIEW) return
        val data = intent.data ?: return
        if (data.scheme == "scd") {
            val parsed = QrLink.parse(data.toString())
            when {
                parsed == null -> Logs.add("qr", "external scd link ignored: malformed")
                parsed.second -> {
                    Logs.add("qr", "external pull link ignored: scan it with the in-app scanner")
                    android.widget.Toast.makeText(this, getString(R.string.qr_use_scanner), android.widget.Toast.LENGTH_LONG).show()
                }
                else -> PendingLink.set(parsed.first, pull = false)
            }
            return
        }
        val host = data.host?.lowercase() ?: return
        if (host != "soundcloud.com" && !host.endsWith(".soundcloud.com")) return
        if (isAuthPage(host, data.path ?: "")) {
            Logs.add("auth", "sign-in page bounced back to browser")
            openInBrowser(this, data.toString())
        } else {
            NavEvents.openUrl(data.toString())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntentExtras(intent)

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                {
                    runCatching { future.get() }
                        .onSuccess { controllerState.value = it }
                        .onFailure { Logs.add("player", "controller failed: ${it.javaClass.simpleName}") }
                },
                MoreExecutors.directExecutor(),
            )
        }

        setContent { AppRoot(controllerState.value) }
    }

    private var siblingAt = 0L

    override fun onStart() {
        super.onStart()
        Updater.resumeIfReady(this)
        val now = android.os.SystemClock.elapsedRealtime()
        if (siblingAt != 0L && now - siblingAt < 60_000L) return
        siblingAt = now
        val app = applicationContext
        App.scope.launch {
            kotlinx.coroutines.delay(2_500)
            runCatching { Sibling.sync(app) }
        }
    }

    override fun onDestroy() {
        controllerState.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}
