package com.scd.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean

object SoundCloudWebAuth {
    const val LOGIN_URL = "https://soundcloud.com/signin"
    private val sites = listOf("https://soundcloud.com", "https://secure.soundcloud.com", "https://api-auth.soundcloud.com")

    fun token(): String? = runCatching {
        sites.firstNotNullOfOrNull { site ->
            CookieManager.getInstance().getCookie(site)
                ?.split(';')
                ?.map { it.trim() }
                ?.firstOrNull { it.startsWith("oauth_token=") }
                ?.substringAfter('=')
                ?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    fun forget() {
        runCatching {
            val cm = CookieManager.getInstance()
            sites.forEach { site ->
                cm.getCookie(site)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/")
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/; Domain=.soundcloud.com")
                }
            }
            cm.flush()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SoundCloudLoginDialog(onDismiss: () -> Unit, onToken: (String) -> Unit) {
    val latest by rememberUpdatedState(onToken)
    var loading by remember { mutableStateOf(true) }
    val done = remember { AtomicBoolean(false) }

    fun take() {
        val token = SoundCloudWebAuth.token() ?: return
        if (done.compareAndSet(false, true)) {
            CookieManager.getInstance().flush()
            Logs.add("upload", "soundcloud web sign-in done")
            latest(token)
        }
    }

    LaunchedEffect(Unit) {
        while (!done.get()) {
            delay(1_000)
            take()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.upload_sc_login_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.upload_sc_login_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            settings.userAgentString = GoogleAuth.MOBILE_UA
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    take()
                                }
                            }
                            loadUrl(SoundCloudWebAuth.LOGIN_URL)
                        }
                    },
                )
            }
        }
    }
}
