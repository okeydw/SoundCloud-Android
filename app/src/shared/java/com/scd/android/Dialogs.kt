package com.scd.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

class DialogSkin(
    val frame: @Composable (content: @Composable () -> Unit) -> Unit,
)

val LocalDialogSkin = androidx.compose.runtime.staticCompositionLocalOf<DialogSkin?> { null }

@Composable
fun AppDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val skin = LocalDialogSkin.current
    Dialog(onDismissRequest = onDismiss) {
        if (skin != null) {
            skin.frame(content)
        } else {
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) { content() }
        }
    }
}

@Composable
fun LanguageDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val labels = languageLabels()
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.language),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(LocaleHelper.languages.size) { i ->
                    val code = LocaleHelper.languages[i]
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(code) }.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = Prefs.language == code, onClick = { onPick(code) })
                        Text(labels[code] ?: code)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun languageLabels(): Map<String, String> = mapOf(
    "system" to stringResource(R.string.theme_system),
    "en" to "English", "ru" to "Русский", "be" to "Беларуская",
    "zh" to "中文", "de" to "Deutsch", "fr" to "Français",
    "es" to "Español", "tr" to "Türkçe", "ko" to "한국어",
)

@Composable
fun CreatePlaylistDialog(onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    AppDialog(onDismiss) {
        run {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.new_playlist),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                listOf(false to R.string.playlist_public, true to R.string.playlist_private).forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { private = value },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = private == value, onClick = { private = value })
                        Text(stringResource(label))
                    }
                }
                Text(
                    stringResource(R.string.playlist_local_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onCreate(name.trim(), private)
                            onDismiss()
                        },
                    ) { Text(stringResource(R.string.create)) }
                }
            }
        }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = value, onValueChange = { value = it.take(32) }, singleLine = true)
            Spacer(Modifier.height(16.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = value.isNotBlank(),
                    onClick = {
                        onConfirm(value.trim())
                        onDismiss()
                    },
                ) { Text(confirm) }
            }
        }
    }
}

@Composable
fun CarEqDialog(onDismiss: () -> Unit) {
    val system = AudioFx.info?.presets.orEmpty()
    val options = buildList {
        add(EqPresets.NONE to stringResource(R.string.car_eq_keep))
        add(EqPresets.OFF to stringResource(R.string.car_eq_off))
        EqPresets.user.forEach { add(EqPresets.userKey(it.name) to it.name) }
        EqPresets.visibleSystem(system).forEach { (i, name) -> add(EqPresets.sysKey(i) to name) }
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.car_eq),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(options.size) { i ->
                    val (key, label) = options[i]
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            Prefs.changeCarEq(key)
                            onDismiss()
                        }.padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = Prefs.carEq == key, onClick = {
                            Prefs.changeCarEq(key)
                            onDismiss()
                        })
                        Text(label)
                    }
                }
            }
            if (system.isEmpty()) {
                Text(
                    stringResource(R.string.car_eq_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun carEqLabel(): String {
    val key = Prefs.carEq
    return when (key) {
        EqPresets.NONE -> stringResource(R.string.car_eq_keep)
        EqPresets.OFF -> stringResource(R.string.car_eq_off)
        else -> EqPresets.label(key, AudioFx.info?.presets.orEmpty()) ?: key
    }
}

@Composable
fun outputKindLabel(kind: String): String = stringResource(
    when (kind) {
        AudioOutput.SPEAKER -> R.string.output_speaker
        AudioOutput.WIRED -> R.string.output_wired
        AudioOutput.BLUETOOTH -> R.string.output_bluetooth
        AudioOutput.USB -> R.string.output_usb
        AudioOutput.HDMI -> R.string.output_hdmi
        else -> R.string.output_auto
    },
)

@Composable
fun AudioOutputDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val devices = AudioOutput.outputs(context)
    val current = Prefs.audioOutput
    val selected = if (current == AudioOutput.AUTO || devices.any { it.key == current }) current else AudioOutput.AUTO
    fun pick(key: String) {
        Prefs.changeAudioOutput(key)
        onDismiss()
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.output_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clickable { pick(AudioOutput.AUTO) }.padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected == AudioOutput.AUTO, onClick = { pick(AudioOutput.AUTO) })
                Column {
                    Text(stringResource(R.string.output_auto))
                    Text(
                        stringResource(R.string.output_auto_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            devices.forEach { d ->
                Row(
                    Modifier.fillMaxWidth().clickable { pick(d.key) }.padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == d.key, onClick = { pick(d.key) })
                    Column {
                        Text(outputKindLabel(d.kind))
                        if (d.kind != AudioOutput.SPEAKER && d.name.isNotBlank()) {
                            Text(
                                d.name,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.output_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun updateBannerText(tag: String): String = when (Updater.state) {
    UpdateState.Downloading -> stringResource(R.string.update_downloading, (Updater.progress * 100).toInt())
    UpdateState.Ready -> stringResource(R.string.update_ready, tag)
    UpdateState.NeedsPermission -> stringResource(R.string.update_permission)
    UpdateState.Failed -> stringResource(R.string.update_failed)
    else -> stringResource(R.string.new_version, tag)
}

@Composable
fun UpdatePrompt(tag: String, url: String, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val asset = Updater.asset
    fun openPage() {
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.new_version, tag), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            val body = when {
                asset == null -> stringResource(R.string.update_no_apk)
                Updater.state == UpdateState.ForeignKey -> stringResource(R.string.update_foreign_key)
                Updater.state == UpdateState.Downloading -> stringResource(R.string.update_downloading, (Updater.progress * 100).toInt())
                else -> stringResource(R.string.update_body, asset.name, fmtMb(asset.size))
            }
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (Updater.state == UpdateState.Downloading) {
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { Updater.progress }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    openPage()
                    onDismiss()
                }) { Text(stringResource(R.string.update_notes)) }
                Spacer(Modifier.weight(1f))
                if (Updater.state == UpdateState.Downloading) {
                    TextButton(onClick = { Updater.cancel() }) { Text(stringResource(R.string.cancel)) }
                } else if (asset != null && Updater.state != UpdateState.ForeignKey) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = {
                        Updater.start(context)
                        if (Updater.state != UpdateState.Downloading) onDismiss()
                    }) { Text(stringResource(R.string.update_install)) }
                } else {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
    }
}

private fun fmtMb(bytes: Long): String = if (bytes <= 0) "?" else "%.1f MB".format(bytes / 1_048_576.0)
