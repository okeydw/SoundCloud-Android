package com.scd.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density

@Composable
fun eqPresetName(id: String): String = when (id) {
    "flat" -> stringResource(R.string.eq_p_flat)
    "bassBoost" -> stringResource(R.string.eq_p_bass_boost)
    "bassDestroyer" -> stringResource(R.string.eq_p_bass_destroyer)
    "trebleBoost" -> stringResource(R.string.eq_p_treble_boost)
    "vocal" -> stringResource(R.string.eq_p_vocal)
    "rock" -> stringResource(R.string.eq_p_rock)
    "electronic" -> stringResource(R.string.eq_p_electronic)
    "classical" -> stringResource(R.string.eq_p_classical)
    "loudness" -> stringResource(R.string.eq_p_loudness)
    "vShape" -> stringResource(R.string.eq_p_v_shape)
    "nightMode" -> stringResource(R.string.eq_p_night)
    EqPresets.CUSTOM -> stringResource(R.string.eq_custom)
    else -> EqPresets.customById(id)?.name ?: stringResource(R.string.eq_custom)
}

sealed interface EqImportOutcome {
    class Done(val result: EqImport) : EqImportOutcome
    data object Empty : EqImportOutcome
    data object Unreadable : EqImportOutcome
}

fun eqImportText(text: String): EqImportOutcome {
    val result = runCatching { EqTransfer.import(text) }.getOrNull() ?: return EqImportOutcome.Empty
    EqPresets.applyImport(result)
    return EqImportOutcome.Done(result)
}

@Composable
fun rememberEqFilePicker(onText: (String?) -> Unit): () -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?.takeIf { it.size <= 2 * 1024 * 1024 }
                ?.let(EqTransfer::decode)
        }.getOrNull()
        latest(text)
    }
    return { runCatching { launcher.launch(arrayOf("text/*", "application/octet-stream")) } }
}

@Composable
fun rememberEqFileSaver(onDone: (Boolean) -> Unit): (name: String, text: String) -> Unit {
    val context = LocalContext.current
    val latest by rememberUpdatedState(onDone)
    val pending = remember { java.util.concurrent.atomic.AtomicReference("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(pending.get().toByteArray()) } != null
        }.getOrDefault(false)
        latest(ok)
    }
    return { name, text ->
        pending.set(text)
        runCatching { launcher.launch(name) }.onFailure { latest(false) }
    }
}

fun eqCopy(context: Context, text: String): Boolean = runCatching {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
    cm.setPrimaryClip(ClipData.newPlainText("equalizer", text))
    true
}.getOrDefault(false)

fun eqExportName(format: EqFormat): String = if (format == EqFormat.APO) "soundcloud-eq.txt" else "soundcloud-graphiceq.txt"

fun eqExportText(format: EqFormat): String {
    val gains = EqCurve.bandGains(Prefs.eqGains)
    return EqTransfer.export(gains, EqCurve.resolvePreamp(gains, Prefs.eqPreamp), format)
}

@Composable
fun CappedFontScale(max: Float = 1.15f, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    if (d.fontScale <= max) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(d.density, max)) { content() }
    }
}
