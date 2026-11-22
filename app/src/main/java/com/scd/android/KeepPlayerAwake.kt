package com.scd.android

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
fun KeepPlayerAwake() {
    if (Prefs.keepPlayerAwake) {
        val view = LocalView.current
        DisposableEffect(view) {
            val previous = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previous }
        }
    }
}
