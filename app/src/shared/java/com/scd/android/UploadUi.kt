package com.scd.android

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@Composable
private fun Note(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun SoundCloudAccount() {
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf(false) }
    val linked = Prefs.scWebToken.isNotBlank()
    LaunchedEffect(linked) {
        if (linked && ExternalAccounts.soundcloud == null) ExternalAccounts.checkSoundCloud()
    }
    if (login) {
        SoundCloudLoginDialog(onDismiss = { login = false }) { token ->
            login = false
            Prefs.changeScWebToken(token)
            scope.launch { ExternalAccounts.checkSoundCloud(token) }
        }
    }
    Text(stringResource(R.string.upload_sc_account), fontWeight = FontWeight.Medium)
    Note(stringResource(R.string.upload_sc_hint))
    Spacer(Modifier.height(6.dp))
    if (!linked) {
        OutlinedButton(onClick = { login = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.upload_sc_login))
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ExternalAccounts.soundcloud?.name ?: stringResource(R.string.import_acc_connected),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                Prefs.changeScWebToken("")
                ExternalAccounts.forget(ExternalAccounts.SOUNDCLOUD)
                SoundCloudWebAuth.forget()
            }) { Text(stringResource(R.string.upload_sc_logout)) }
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable
fun UploadSection() {
    when (TrackUpload.phase) {
        UploadPhase.Idle -> UploadForm()
        UploadPhase.Sending, UploadPhase.Processing -> UploadRunning()
        UploadPhase.Done -> UploadDone()
        UploadPhase.Failed -> UploadFailed()
    }
}

@Composable
private fun UploadForm() {
    val ctx = LocalContext.current
    val prev = TrackUpload.last
    var audioUri by rememberSaveable { mutableStateOf(prev?.audio?.uri?.toString()) }
    var artUri by rememberSaveable { mutableStateOf(prev?.artwork?.uri?.toString()) }
    var title by rememberSaveable { mutableStateOf(prev?.title.orEmpty()) }
    var genre by rememberSaveable { mutableStateOf(prev?.genre.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(prev?.tags.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(prev?.description.orEmpty()) }
    var private by rememberSaveable { mutableStateOf(prev?.private ?: false) }
    val audio = remember(audioUri) { audioUri?.let { TrackUpload.describe(ctx, Uri.parse(it)) } }
    val art = remember(artUri) { artUri?.let { TrackUpload.describe(ctx, Uri.parse(it)) } }

    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        audioUri = uri.toString()
        if (title.isBlank()) TrackUpload.describe(ctx, uri)?.let { title = TrackUpload.titleFrom(it).take(200) }
    }
    val pickArt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) artUri = uri.toString()
    }

    Note(stringResource(R.string.upload_sub))
    Spacer(Modifier.height(12.dp))
    SoundCloudAccount()
    if (audio == null) {
        Button(onClick = { pickAudio.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(painterResource(R.drawable.ic_upload), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.upload_choose_audio))
        }
        Spacer(Modifier.height(6.dp))
        Note(stringResource(R.string.upload_formats))
        return
    }

    val tooLarge = audio.size > TrackUpload.MAX_AUDIO_BYTES
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.ic_music), null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(audio.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (audio.size > 0) Note(Formatter.formatShortFileSize(ctx, audio.size))
        }
        TextButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) { Text(stringResource(R.string.upload_change)) }
    }
    if (tooLarge) {
        Text(stringResource(R.string.upload_err_too_large), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { pickArt.launch(arrayOf("image/jpeg", "image/png")) },
                contentAlignment = Alignment.Center,
            ) {
                if (art != null) {
                    AsyncImage(model = art.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(6.dp)) {
                        Icon(painterResource(R.drawable.ic_music), null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.upload_add_artwork), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
            if (art != null) {
                TextButton(onClick = { artUri = null }) { Text(stringResource(R.string.upload_remove_artwork), style = MaterialTheme.typography.labelSmall) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(200) },
                label = { Text(stringResource(R.string.upload_field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !private, onClick = { private = false }, label = { Text(stringResource(R.string.upload_public)) })
                FilterChip(
                    selected = private,
                    onClick = { private = true },
                    label = { Text(stringResource(R.string.upload_private)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_lock), null, modifier = Modifier.size(14.dp)) },
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = genre,
        onValueChange = { genre = it.take(100) },
        label = { Text(stringResource(R.string.upload_field_genre)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = tags,
        onValueChange = { tags = it },
        label = { Text(stringResource(R.string.upload_field_tags)) },
        supportingText = { Text(stringResource(R.string.upload_tags_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = description,
        onValueChange = { description = it },
        label = { Text(stringResource(R.string.upload_field_description)) },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Note(stringResource(R.string.upload_rights))
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { TrackUpload.start(ctx, UploadRequest(audio, art, title, description, genre, tags, private)) },
        enabled = title.isNotBlank() && !tooLarge && !Prefs.offline,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(painterResource(R.drawable.ic_upload), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.upload_start))
    }
}

@Composable
private fun UploadRunning() {
    val total = TrackUpload.total
    val processing = TrackUpload.phase == UploadPhase.Processing
    val percent = if (total > 0) ((TrackUpload.sent * 100) / total).toInt().coerceIn(0, 100) else null
    Text(TrackUpload.last?.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2)
    Spacer(Modifier.height(10.dp))
    if (processing || percent == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    if (TrackUpload.direct) {
        Note(stringResource(R.string.upload_sc_direct))
        Spacer(Modifier.height(4.dp))
    }
    Text(
        when {
            processing -> stringResource(R.string.upload_handing_off)
            percent != null -> stringResource(R.string.upload_sending_percent, percent)
            else -> stringResource(R.string.upload_sending)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(4.dp))
    Note(stringResource(R.string.upload_keeps_running))
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { TrackUpload.cancel() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.upload_cancel))
    }
}

@Composable
private fun UploadDone() {
    Text(
        stringResource(R.string.upload_done, TrackUpload.last?.title.orEmpty()),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(6.dp))
    Note(stringResource(R.string.upload_processing_hint))
    Spacer(Modifier.height(12.dp))
    Button(onClick = { TrackUpload.reset() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.upload_another))
    }
}

@Composable
private fun UploadFailed() {
    val ctx = LocalContext.current
    Text(
        stringResource(TrackUpload.error ?: R.string.upload_err_failed),
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
    )
    TrackUpload.reason?.let {
        Spacer(Modifier.height(4.dp))
        Note(it)
    }
    if (Prefs.scWebToken.isBlank()) {
        Spacer(Modifier.height(8.dp))
        Note(stringResource(R.string.upload_sc_needed))
        Spacer(Modifier.height(8.dp))
        SoundCloudAccount()
    }
    Spacer(Modifier.height(12.dp))
    TrackUpload.last?.let { request ->
        Button(onClick = { TrackUpload.start(ctx, request) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.upload_try_again))
        }
        Spacer(Modifier.height(6.dp))
    }
    OutlinedButton(onClick = { TrackUpload.reset() }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.upload_edit))
    }
}
