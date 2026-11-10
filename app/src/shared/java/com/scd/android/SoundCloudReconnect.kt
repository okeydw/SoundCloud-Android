package com.scd.android

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

@Composable
fun SoundCloudReconnect() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (!SessionState.reconnect || !SessionGuard.signedIn) return
    AlertDialog(
        onDismissRequest = { if (!BrowserLogin.busy) SessionState.dismissReconnect() },
        title = { Text(stringResource(R.string.upload_sc_login_title)) },
        text = { Text(BrowserLogin.status ?: stringResource(R.string.upload_err_sc_session)) },
        confirmButton = {
            TextButton(enabled = !BrowserLogin.busy, onClick = { scope.launch { BrowserLogin.run(context) } }) {
                Text(stringResource(R.string.login_button))
            }
        },
        dismissButton = {
            TextButton(enabled = !BrowserLogin.busy, onClick = { SessionState.dismissReconnect() }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
