package com.scd.android

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object QrLink {
    fun parse(text: String): Pair<String, Boolean>? {
        val uri = runCatching { android.net.Uri.parse(text.trim()) }.getOrNull() ?: return null
        if (!uri.isHierarchical || uri.scheme?.lowercase() != "scd") return null
        val token = runCatching { uri.getQueryParameter("token") ?: uri.getQueryParameter("claim") }.getOrNull()
        if (token.isNullOrBlank() || token.length > 512) return null
        val pull = runCatching { uri.getQueryParameter("mode") }.getOrNull()?.lowercase() == "pull"
        return token to pull
    }

    fun deepLink(token: String, push: Boolean): String =
        "scd://link?token=${android.net.Uri.encode(token)}&mode=${if (push) "push" else "pull"}"
}

@Composable
fun rememberQrScanner(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val text = result.contents ?: return@rememberLauncherForActivityResult
        val parsed = QrLink.parse(text)
        if (parsed == null) {
            Logs.add("qr", "scanned code is not a login link")
            android.widget.Toast.makeText(context, context.getString(R.string.qr_not_link), android.widget.Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        Logs.add("qr", "scanned ${if (parsed.second) "pull" else "push"} link")
        PendingLink.set(parsed.first, pull = parsed.second, fromScanner = true)
    }
    return {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(context.getString(R.string.qr_scan_prompt))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
        runCatching { launcher.launch(options) }.onFailure {
            Logs.add("qr", "scanner failed: ${it.javaClass.simpleName}")
            openCamera(context)
        }
    }
}

@Composable
fun LinkQrDialog(push: Boolean, onDismiss: () -> Unit, onSignedIn: (String) -> Unit = {}) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    LaunchedEffect(attempt) {
        bitmap = null
        status = null
        val link = runCatching { Api.linkCreate(push) }.getOrElse {
            Logs.add("qr", "link create failed: ${it.message?.take(80)}")
            status = context.getString(R.string.login_failed)
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.Default) {
            runCatching {
                BarcodeEncoder().encodeBitmap(QrLink.deepLink(link.claimToken, push), BarcodeFormat.QR_CODE, 720, 720)
            }.getOrNull()
        }
        val deadline = System.currentTimeMillis() + 5 * 60_000L
        while (System.currentTimeMillis() < deadline) {
            delay(2_000)
            val st = runCatching { Api.linkStatus(link.linkRequestId) }.getOrNull() ?: continue
            when (st.status) {
                "claimed" -> {
                    Logs.add("qr", "link claimed (${st.mode})")
                    if (!push) {
                        val sid = st.sessionId
                        if (sid != null) {
                            Api.storeSession(context, sid)
                            onSignedIn(sid)
                        }
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.link_pull_done), android.widget.Toast.LENGTH_LONG).show()
                    }
                    onDismiss()
                    return@LaunchedEffect
                }
                "failed", "expired" -> {
                    status = context.getString(R.string.link_expired)
                    bitmap = null
                    return@LaunchedEffect
                }
            }
        }
        status = context.getString(R.string.link_expired)
        bitmap = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (push) R.string.link_share_title else R.string.link_show_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(if (push) R.string.link_share_hint else R.string.qr_login_hint),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val bmp = bitmap
                    when {
                        bmp != null -> Image(bmp.asImageBitmap(), stringResource(R.string.cd_qr_code))
                        status == null -> CircularProgressIndicator()
                        else -> Text(status ?: "", color = Color.Black, textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            if (status != null) {
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.link_regenerate)) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
