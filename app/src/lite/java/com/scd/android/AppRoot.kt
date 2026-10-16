package com.scd.android

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Orange = Color(0xFFFF5500)

private val DarkColors = darkColorScheme(
    primary = Orange,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Orange,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFAFAFA),
)

@Composable
fun isAppDark(): Boolean = when (Prefs.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

@Composable
fun AppRoot(controller: MediaController?) {
    val base = if (isAppDark()) DarkColors else LightColors
    val accent = Color(Prefs.accent)
    var scheme = base.copy(primary = accent, secondary = accent)
    if (Prefs.textColor != 0) {
        val text = Color(Prefs.textColor)
        scheme = scheme.copy(
            onBackground = text,
            onSurface = text,
            onSurfaceVariant = text.copy(alpha = 0.7f),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        val customBackground = Prefs.backgroundImage != null || Prefs.bgColor1 != 0
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppBackground()
            Surface(
                Modifier.fillMaxSize(),
                color = if (customBackground) Color.Transparent else MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ) {
                Root(controller)
            }
        }
    }
}

@Composable
fun rememberArtworkColor(artworkUri: String?): Color {
    val context = LocalContext.current
    val dark = isAppDark()
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    var color by remember { mutableStateOf(fallback) }

    LaunchedEffect(artworkUri, dark) {
        if (artworkUri == null) {
            color = fallback
            return@LaunchedEffect
        }
        val extracted = runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .size(128)
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                val fb = fallback.toArgb()
                if (dark) {
                    palette.getDarkMutedColor(palette.getMutedColor(palette.getDominantColor(fb)))
                } else {
                    palette.getLightMutedColor(palette.getMutedColor(palette.getDominantColor(fb)))
                }
            }
        }.getOrNull()
        color = extracted?.let { Color(it) } ?: fallback
    }
    return color
}

@Composable
private fun Root(controller: MediaController?) {
    val context = LocalContext.current
    val signedIn = SessionGuard.signedIn

    LaunchedEffect(SessionState.expired) {
        SessionGuard.handleExpired(context, controller)
    }

    val linkToken = PendingLink.token
    val linkPull = PendingLink.pull
    when {
        linkToken == null -> {}
        linkPull && !signedIn -> LaunchedEffect(linkToken) {
            Logs.add("qr", "pull link ignored: not signed in")
            android.widget.Toast.makeText(context, context.getString(R.string.link_pull_need_login), android.widget.Toast.LENGTH_LONG).show()
            PendingLink.clear()
        }
        linkPull && !PendingLink.fromScanner -> LaunchedEffect(linkToken) {
            Logs.add("qr", "pull link without in-app scanner ignored")
            PendingLink.clear()
        }
        else -> LinkConfirmDialog(linkToken, linkPull, signedIn, controller)
    }

    if (signedIn) {
        MainScreen(controller, onSignOut = { SessionGuard.signOut(context, controller) })
    } else {
        LoginScreen()
    }
}

@Composable
private fun LinkConfirmDialog(token: String, pull: Boolean, signedIn: Boolean, controller: MediaController?) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var freshName by remember(token) { mutableStateOf<String?>(null) }
    LaunchedEffect(token, pull) { freshName = LinkClaims.accountName(pull) }
    val account = freshName ?: Prefs.username ?: "SoundCloud"
    AlertDialog(
        onDismissRequest = { if (!busy) PendingLink.clear() },
        title = { Text(stringResource(if (pull) R.string.link_pull_title else R.string.link_confirm_title)) },
        text = {
            Text(
                when {
                    pull -> stringResource(R.string.link_pull_text, account)
                    signedIn -> stringResource(R.string.link_confirm_replace)
                    else -> stringResource(R.string.link_confirm_text)
                },
            )
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    App.scope.launch {
                        LinkClaims.claim(context, token, pull, controller)
                        busy = false
                    }
                },
            ) {
                Text(stringResource(if (pull) R.string.link_pull_yes else R.string.link_confirm_yes))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { PendingLink.clear() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@Composable
private fun LoginScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scanQr = rememberQrScanner()
    var showPullQr by remember { mutableStateOf(false) }

    if (showPullQr) {
        LinkQrDialog(push = false, onDismiss = { showPullQr = false })
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SoundCloud", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.qr_scan_hint),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { scanQr() }) { Text(stringResource(R.string.qr_scan_button)) }
        TextButton(onClick = { showPullQr = true }) { Text(stringResource(R.string.link_show_title)) }

        Spacer(Modifier.height(28.dp))
        HorizontalDivider(Modifier.fillMaxWidth(0.6f))
        Spacer(Modifier.height(16.dp))

        TextButton(
            enabled = !BrowserLogin.busy,
            onClick = { scope.launch { BrowserLogin.run(context) } },
        ) { Text(stringResource(R.string.login_button)) }
        Text(
            stringResource(R.string.login_browser_warn),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BrowserLogin.busy) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator(Modifier.size(20.dp))
        }
        BrowserLogin.status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
