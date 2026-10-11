package com.scd.android

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
private fun Hint(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CheckRow(checked: Boolean, label: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun importError(code: String?): String = when (code) {
    null -> ""
    "cancelled" -> stringResource(R.string.import_cancelled)
    "token" -> stringResource(R.string.import_err_token)
    "nothing" -> stringResource(R.string.import_err_nothing)
    "search" -> stringResource(R.string.import_err_search)
    "unavailable", "parse", "empty" -> stringResource(R.string.import_err_source)
    else -> code
}

@Composable
private fun ImportProgress() {
    val st = Importer.state ?: return
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(10.dp))
    Text(st.title.ifBlank { st.source }, fontWeight = FontWeight.Medium, maxLines = 2)
    val line = when (st.stage) {
        ImportStage.Reading -> stringResource(R.string.import_reading, st.done)
        ImportStage.Matching -> stringResource(R.string.import_matching, st.done, st.total, st.found)
        ImportStage.Saving -> stringResource(R.string.import_saving)
        ImportStage.Done -> stringResource(R.string.import_done, st.found, st.total)
        ImportStage.Failed -> importError(st.error)
    }
    Text(
        line,
        color = if (st.stage == ImportStage.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    if (Importer.busy) {
        Spacer(Modifier.height(6.dp))
        if (st.total > 0 && st.stage == ImportStage.Matching) {
            LinearProgressIndicator(progress = { st.done.toFloat() / st.total }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        TextButton(onClick = { Importer.cancel() }) { Text(stringResource(R.string.cancel)) }
    } else {
        if (st.stage == ImportStage.Done && st.source != Importer.YOUTUBE && st.missed.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = !open }) { Text(stringResource(R.string.import_missed, st.missed.size)) }
            if (open) {
                st.missed.take(60).forEach { Hint(it) }
            }
        }
        TextButton(onClick = { Importer.clear() }) { Text(stringResource(R.string.cd_close)) }
    }
}

@Composable
fun ImportSection() {
    val ctx = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var asPlaylist by rememberSaveable { mutableStateOf(true) }
    var download by rememberSaveable { mutableStateOf(true) }
    var token by rememberSaveable { mutableStateOf(Prefs.ymToken) }
    val kind = Importer.detect(link)
    val busy = Importer.busy

    Text(stringResource(R.string.import_link_title), fontWeight = FontWeight.Medium)
    Hint(stringResource(R.string.import_link_hint))
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = link,
        onValueChange = { link = it.trim() },
        singleLine = true,
        placeholder = { Text("youtube.com/… · open.spotify.com/… · music.yandex.ru/…") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (link.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Hint(
            when (kind) {
                Importer.YOUTUBE -> stringResource(R.string.import_kind_youtube)
                Importer.SPOTIFY -> stringResource(R.string.import_kind_spotify)
                Importer.YANDEX -> stringResource(R.string.import_kind_yandex)
                else -> stringResource(R.string.import_kind_unknown)
            },
        )
    }
    Spacer(Modifier.height(6.dp))
    if (kind == Importer.YOUTUBE) {
        listOf(true to R.string.import_as_playlist, false to R.string.import_as_downloads).forEach { (v, label) ->
            Row(Modifier.fillMaxWidth().clickable { asPlaylist = v }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = asPlaylist == v, onClick = { asPlaylist = v })
                Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Hint(stringResource(R.string.import_youtube_note))
    } else {
        CheckRow(download, stringResource(R.string.import_download_all)) { download = it }
    }
    Spacer(Modifier.height(6.dp))
    Button(
        enabled = kind != null && !busy,
        onClick = {
            when (kind) {
                Importer.YOUTUBE -> Importer.youtube(ctx, link, asPlaylist)
                Importer.SPOTIFY -> Importer.spotify(ctx, link, download)
                Importer.YANDEX -> {
                    if (token.isNotBlank()) Prefs.changeYmToken(token)
                    Importer.yandex(ctx, link, token.ifBlank { null }, download)
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.import_start)) }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.import_ym_title), fontWeight = FontWeight.Medium)
    Hint(stringResource(R.string.import_ym_steps))
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = token,
        onValueChange = { token = it.trim() },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        placeholder = { Text(stringResource(R.string.import_ym_token)) },
        modifier = Modifier.fillMaxWidth(),
    )
    var login by remember { mutableStateOf(false) }
    if (login) {
        YandexLoginDialog(onDismiss = { login = false }) { t ->
            token = t
            Prefs.changeYmToken(t)
            login = false
            android.widget.Toast.makeText(ctx, ctx.getString(R.string.import_ym_login_done), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedButton(onClick = { login = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(if (token.isBlank()) R.string.import_ym_login else R.string.import_ym_relogin))
    }
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(
            enabled = token.isNotBlank() && !busy,
            onClick = {
                Prefs.changeYmToken(token)
                Importer.yandex(ctx, null, token, download)
            },
            modifier = Modifier.weight(1f),
        ) { Text(stringResource(R.string.import_ym_likes)) }
        if (Prefs.ymToken.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {
                token = ""
                Prefs.changeYmToken("")
            }) { Text(stringResource(R.string.import_ym_forget)) }
        }
    }
    Hint(stringResource(R.string.import_ym_note))

    ImportProgress()
    Spacer(Modifier.height(4.dp))
    Hint(stringResource(R.string.import_limit_hint))
    Spacer(Modifier.padding(bottom = 8.dp))
}
