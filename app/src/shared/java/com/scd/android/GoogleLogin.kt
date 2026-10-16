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
import java.util.concurrent.atomic.AtomicBoolean

object GoogleAuth {
    const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fwww.youtube.com%2F"
    const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"

    fun cookies(): String? = runCatching { CookieManager.getInstance().getCookie("https://www.youtube.com") }.getOrNull()

    fun signedIn(cookie: String?): Boolean =
        cookie?.split(';')?.map { it.trim() }?.any { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") } == true

    fun forget() {
        runCatching {
            val cm = CookieManager.getInstance()
            listOf("https://www.youtube.com", "https://youtube.com", "https://music.youtube.com", "https://accounts.google.com", "https://www.google.com").forEach { site ->
                val domain = if (site.contains("youtube")) ".youtube.com" else ".google.com"
                cm.getCookie(site)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/")
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/; Domain=$domain")
                }
            }
            cm.flush()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GoogleLoginDialog(onDismiss: () -> Unit, onCookies: (String) -> Unit) {
    val latest by rememberUpdatedState(onCookies)
    var loading by remember { mutableStateOf(true) }
    val done = remember { AtomicBoolean(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_g_login_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.import_g_login_hint),
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
                            if (android.os.Build.VERSION.SDK_INT in 29..32) {
                                @Suppress("DEPRECATION")
                                settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    val host = url?.let { android.net.Uri.parse(it).host }.orEmpty()
                                    if (!host.endsWith("youtube.com")) return
                                    val cookie = GoogleAuth.cookies()?.takeIf { GoogleAuth.signedIn(it) } ?: return
                                    if (done.compareAndSet(false, true)) {
                                        CookieManager.getInstance().flush()
                                        Logs.add("youtube", "google account linked")
                                        view.post { latest(cookie) }
                                    }
                                }
                            }
                            loadUrl(GoogleAuth.LOGIN_URL)
                        }
                    },
                )
            }
        }
    }
}
