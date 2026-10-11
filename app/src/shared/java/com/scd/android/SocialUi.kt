package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private fun toast(context: android.content.Context, res: Int, vararg args: Any) {
    android.widget.Toast.makeText(context, context.getString(res, *args), android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
fun BlockedArtistsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LaunchedEffect(Unit) { BlockedArtists.refresh() }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.blocked_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.blocked_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            val list = BlockedArtists.entries
            if (list.isEmpty()) {
                Text(
                    stringResource(R.string.blocked_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(list, key = { it.kind + ":" + it.id }) { e ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = Api.artworkUrl(e.avatar_url, "t120x120") ?: e.avatar_url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            Text(
                                stringResource(if (e.kind == "artist") R.string.blocked_kind_artist else R.string.blocked_kind_user),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = {
                            scope.launch {
                                if (!BlockedArtists.unblock(e.kind, e.id)) toast(context, R.string.blocked_failed)
                            }
                        }) { Text(stringResource(R.string.unblock)) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cd_close)) }
            }
        }
    }
}

@Composable
fun BlockConfirmDialog(targets: List<BlockedArtist>, onDismiss: () -> Unit, onDone: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var picked by remember(targets) { mutableStateOf(targets.firstOrNull()) }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.block_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.block_body),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            targets.forEach { t ->
                Row(
                    Modifier.fillMaxWidth().clickable { picked = t }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.RadioButton(selected = picked == t, onClick = { picked = t })
                    Column {
                        Text(t.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(if (t.kind == "artist") R.string.blocked_kind_artist else R.string.blocked_kind_user),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    enabled = picked != null,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val t = picked ?: return@Button
                        onDismiss()
                        scope.launch {
                            val ok = BlockedArtists.block(t)
                            toast(context, if (ok) R.string.blocked_done else R.string.blocked_failed, t.name)
                            if (ok) onDone()
                        }
                    },
                ) { Text(stringResource(R.string.block)) }
            }
        }
    }
}

fun userAuraColors(info: AuraInfo?): List<Color>? = Auras.orbs(info)?.map { Color(it) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AuraPickerDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val current = Auras.mine
    var hex by remember { mutableStateOf(current?.custom_hex ?: Auras.DEFAULT_HEX) }
    var saving by remember { mutableStateOf(false) }
    fun apply(id: String) {
        if (!Prefs.star) {
            toast(context, R.string.aura_star_only)
            return
        }
        saving = true
        scope.launch {
            val ok = Auras.setMine(id, hex.uppercase())
            saving = false
            if (ok) onDismiss() else toast(context, R.string.aura_failed)
        }
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(24.dp)) {
            Text(stringResource(R.string.aura_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (Prefs.star) R.string.aura_hint else R.string.aura_star_only),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Auras.presets.forEach { a ->
                    val selected = current?.aura_id == a.id
                    Column(
                        Modifier.width(64.dp).clickable(enabled = !saving) { apply(a.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(a.orbs.map { Color(it) } + Color(a.orbs.first())))
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(a.id.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val parsed = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(parsed ?: MaterialTheme.colorScheme.surfaceVariant)
                        .border(if (current?.aura_id == Auras.CUSTOM) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape),
                )
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v -> hex = ("#" + v.removePrefix("#").filter { it.isLetterOrDigit() }.take(6)) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.aura_custom)) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !saving && parsed != null && hex.length == 7,
                    onClick = { apply(Auras.CUSTOM) },
                ) { Text(stringResource(R.string.save)) }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun SocialLinks(socials: List<ArtistSocial>, chip: @Composable (label: String, onClick: () -> Unit) -> Unit) {
    val context = LocalContext.current
    socials.distinctBy { it.url }.take(8).forEach { s ->
        val label = when (s.kind.lowercase()) {
            "website", "official" -> stringResource(R.string.social_site)
            else -> s.kind.replaceFirstChar { it.uppercase() }
        }
        chip(label) {
            runCatching {
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(s.url)))
            }
        }
    }
}

fun webProfileLabel(w: WebProfile): String {
    val service = w.service.lowercase()
    val name = when (service) {
        "personal", "website", "" -> w.title.ifBlank { w.url.removePrefix("https://").removePrefix("http://").substringBefore('/') }
        else -> service.replaceFirstChar { it.uppercase() }
    }
    val handle = w.username?.takeIf { it.isNotBlank() && service != "personal" }
    return if (handle != null) "$name · $handle" else name
}
