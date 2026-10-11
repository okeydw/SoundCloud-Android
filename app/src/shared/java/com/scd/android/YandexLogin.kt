package com.scd.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
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

object YandexAuth {
    private const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"
    const val AUTH_URL = "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID"

    fun tokenFrom(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("access_token=")) return null
        val part = url.substringAfter('#', "").ifEmpty { url.substringAfter('?', "") }
        return part.split('&')
            .firstOrNull { it.startsWith("access_token=") }
            ?.substringAfter('=')
            ?.let { Uri.decode(it) }
            ?.takeIf { it.length >= 20 }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YandexLoginDialog(onDismiss: () -> Unit, onToken: (String) -> Unit) {
    val latest by rememberUpdatedState(onToken)
    var loading by remember { mutableStateOf(true) }
    var done by remember { mutableStateOf(false) }
    fun check(url: String?): Boolean {
        if (done) return true
        val token = YandexAuth.tokenFrom(url) ?: return false
        done = true
        Logs.add("import", "yandex token received")
        latest(token)
        return true
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_ym_login_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.import_ym_login_hint),
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
                            CookieManager.getInstance().setAcceptCookie(true)
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                    check(request.url?.toString())

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                    if (check(url)) view.stopLoading()
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    check(url)
                                }
                            }
                            loadUrl(YandexAuth.AUTH_URL)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}
