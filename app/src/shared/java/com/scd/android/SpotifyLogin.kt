package com.scd.android

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SpotifyAuth {
    const val LOGIN_URL = "https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F"
    const val HOME_URL = "https://open.spotify.com/"
    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"

    private val http by lazy {
        Api.http.newBuilder()
            .cache(null)
            .followRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                val req = chain.request()
                val host = req.url.host
                if (host == "spotify.com" || host.endsWith(".spotify.com")) {
                    chain.proceed(req)
                } else {
                    chain.proceed(req.newBuilder().removeHeader("Cookie").removeHeader("Authorization").build())
                }
            }
            .build()
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun isTokenRequest(url: String): Boolean {
        if (!url.startsWith("https://open.spotify.com/")) return false
        val path = url.removePrefix("https://open.spotify.com").substringBefore('?')
        return path.startsWith("/api/token") || path.startsWith("/get_access_token")
    }

    class Token(val value: String, val until: Long, val anonymous: Boolean)

    fun replay(request: WebResourceRequest): Pair<WebResourceResponse, Token?>? = runCatching {
        val url = request.url.toString()
        val builder = Request.Builder().url(url)
        request.requestHeaders.forEach { (k, v) ->
            if (!k.equals("cookie", true) && !k.equals("accept-encoding", true)) builder.header(k, v)
        }
        CookieManager.getInstance().getCookie(url)?.let { builder.header("Cookie", it) }
        http.newCall(builder.build()).execute().use { res ->
            val bytes = res.body?.bytes() ?: ByteArray(0)
            val token = runCatching {
                val o = json.parseToJsonElement(bytes.decodeToString()).jsonObject
                val value = o["accessToken"]?.jsonPrimitive?.contentOrNull
                val until = o["accessTokenExpirationTimestampMs"]?.jsonPrimitive?.longOrNull ?: (System.currentTimeMillis() + 3_000_000L)
                val anonymous = o["isAnonymous"]?.jsonPrimitive?.booleanOrNull ?: true
                value?.takeIf { it.length > 20 }?.let { Token(it, until, anonymous) }
            }.getOrNull()
            val type = res.header("content-type")?.substringBefore(';')?.trim() ?: "application/json"
            val headers = res.headers.names()
                .filter { !it.equals("content-type", true) && !it.equals("content-length", true) && !it.equals("content-encoding", true) && !it.equals("set-cookie", true) }
                .associateWith { res.header(it).orEmpty() }
            WebResourceResponse(type, "utf-8", res.code, res.message.ifBlank { "OK" }, headers, ByteArrayInputStream(bytes)) to token
        }
    }.getOrNull()

    fun signedIn(): Boolean = runCatching {
        CookieManager.getInstance().getCookie("https://open.spotify.com").orEmpty().split(';').any { it.trim().startsWith("sp_dc=") }
    }.getOrDefault(false)

    fun verify(token: String): Boolean = runCatching {
        val req = Request.Builder().url("https://api.spotify.com/v1/me").header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    fun forget() {
        Prefs.changeSpotifyToken("", 0L)
        Prefs.changeSpotifyRefresh("")
        runCatching {
            val cm = CookieManager.getInstance()
            listOf("https://open.spotify.com", "https://accounts.spotify.com", "https://www.spotify.com", "https://spotify.com").forEach { site ->
                cm.getCookie(site)?.split(';')?.map { it.substringBefore('=').trim() }?.filter { it.isNotEmpty() }?.forEach { name ->
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/")
                    cm.setCookie(site, "$name=; Max-Age=0; Path=/; Domain=.spotify.com")
                }
            }
            cm.flush()
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SpotifyLoginDialog(refresh: Boolean, onDismiss: () -> Unit, onToken: (String, Long) -> Unit) {
    val latest by rememberUpdatedState(onToken)
    var loading by remember { mutableStateOf(true) }
    val done = remember { AtomicBoolean(false) }
    val checked = remember { java.util.Collections.synchronizedSet(HashSet<String>()) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.import_sp_login_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        stringResource(R.string.import_sp_login_hint),
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
                            if (android.os.Build.VERSION.SDK_INT in 29..32) {
                                @Suppress("DEPRECATION")
                                settings.forceDark = android.webkit.WebSettings.FORCE_DARK_OFF
                            }
                            if (refresh) settings.userAgentString = SpotifyAuth.DESKTOP_UA
                            var desktop = refresh
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            val view = this
                            fun accept(token: String, until: Long) {
                                if (!done.compareAndSet(false, true)) return
                                Logs.add("import", "spotify token received")
                                view.post { latest(token, until) }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                    if (done.get()) return null
                                    val url = request.url.toString()
                                    if (request.method == "GET" && SpotifyAuth.isTokenRequest(url)) {
                                        val (response, token) = SpotifyAuth.replay(request) ?: return null
                                        if (token != null && !token.anonymous) accept(token.value, token.until)
                                        return response
                                    }
                                    val host = request.url.host.orEmpty()
                                    if (host == "api.spotify.com" || host == "api-partner.spotify.com") {
                                        val auth = request.requestHeaders.entries.firstOrNull { it.key.equals("authorization", true) }?.value
                                        val token = auth?.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")?.trim()
                                        if (token != null && token.length > 20 && checked.add(token) && SpotifyAuth.verify(token)) {
                                            accept(token, System.currentTimeMillis() + 50 * 60_000L)
                                        }
                                    }
                                    return null
                                }

                                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                                    loading = true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    loading = false
                                    if (desktop || done.get() || !SpotifyAuth.signedIn()) return
                                    desktop = true
                                    loading = true
                                    Logs.add("import", "spotify signed in, opening web player for the token")
                                    view.settings.userAgentString = SpotifyAuth.DESKTOP_UA
                                    view.loadUrl(SpotifyAuth.HOME_URL)
                                }
                            }
                            loadUrl(if (refresh) SpotifyAuth.HOME_URL else SpotifyAuth.LOGIN_URL)
                        }
                    },
                    onRelease = { it.stopLoading(); it.destroy() },
                )
            }
        }
    }
}
