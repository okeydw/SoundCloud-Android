package com.scd.android

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

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
private fun RadioRow(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun importError(code: String?): String = when (code) {
    null -> ""
    "cancelled" -> stringResource(R.string.import_cancelled)
    "token" -> stringResource(R.string.import_err_token)
    "sp_token" -> stringResource(R.string.import_err_sp_token)
    "sp_not_owned" -> stringResource(R.string.import_err_sp_not_owned)
    "sp_forbidden" -> stringResource(R.string.import_err_sp_forbidden)
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
        if (st.stage == ImportStage.Done && st.partial) Hint(stringResource(R.string.import_sp_partial))
        if (st.stage == ImportStage.Done && st.missed.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            TextButton(onClick = { open = !open }) { Text(stringResource(R.string.import_missed, st.missed.size)) }
            if (open) {
                st.missed.take(60).forEach { Hint(it) }
            }
        }
        TextButton(onClick = { Importer.clear() }) { Text(stringResource(R.string.cd_close)) }
    }
}

private const val GUIDE_BASE = "https://github.com/okeydw/SoundCloud-Android/blob/main/docs/import-lang/"
private val guideLangs = setOf("en", "be", "de", "es", "fr", "ko", "tr", "zh")

private fun guideUrl(ctx: android.content.Context): String {
    val lang = ctx.resources.configuration.locales[0].language
    val file = when {
        lang == "ru" -> "import.md"
        lang in guideLangs -> "import.$lang.md"
        else -> "import.en.md"
    }
    return GUIDE_BASE + file
}

@Composable
private fun GuideLink(anchor: String? = null) {
    val ctx = LocalContext.current
    TextButton(onClick = { openInDefaultBrowser(ctx, guideUrl(ctx) + (anchor?.let { "#$it" } ?: "")) }) {
        Text(stringResource(R.string.import_guide))
    }
}

private fun openInDefaultBrowser(context: android.content.Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
        .onFailure { android.widget.Toast.makeText(context, url, android.widget.Toast.LENGTH_LONG).show() }
}

@Composable
fun ImportSection(accounts: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var link by rememberSaveable { mutableStateOf("") }
    var asPlaylist by rememberSaveable { mutableStateOf(true) }
    var ytSimilar by rememberSaveable { mutableStateOf(false) }
    var download by rememberSaveable { mutableStateOf(true) }
    val kind = Importer.detect(link)
    val busy = Importer.busy
    var spotifyLogin by remember { mutableStateOf(false) }
    var pendingSpotify by remember { mutableStateOf<String?>(null) }
    if (spotifyLogin) {
        SpotifyLoginDialog(refresh = true, onDismiss = {
            spotifyLogin = false
            pendingSpotify?.let { Importer.spotify(ctx, it, asPlaylist, download) }
            pendingSpotify = null
        }) { t, until ->
            Prefs.changeSpotifyToken(t, until)
            spotifyLogin = false
            pendingSpotify?.let { Importer.spotify(ctx, it, asPlaylist, download) }
            pendingSpotify = null
        }
    }

    Text(stringResource(R.string.import_link_title), fontWeight = FontWeight.Medium)
    Hint(stringResource(R.string.import_link_hint))
    GuideLink()
    Spacer(Modifier.height(4.dp))
    OutlinedTextField(
        value = link,
        onValueChange = { link = it.trim() },
        singleLine = true,
        placeholder = { Text("youtube.com/… · open.spotify.com/… · music.yandex.ru/…") },
        modifier = Modifier.fillMaxWidth(),
    )
    val collection = kind != null && Importer.isCollection(link)
    val ytDirect = kind == Importer.YOUTUBE && !ytSimilar
    if (link.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        if (kind != null) {
            val name = when (kind) {
                Importer.YOUTUBE -> "YouTube"
                Importer.SPOTIFY -> "Spotify"
                else -> stringResource(R.string.import_src_yandex)
            }
            Text(
                stringResource(if (collection) R.string.import_detected_playlist else R.string.import_detected_track, name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        when (kind) {
            null -> Hint(stringResource(R.string.import_kind_unknown))
            Importer.YANDEX -> Hint(stringResource(R.string.import_kind_yandex))
        }
    }
    Spacer(Modifier.height(6.dp))
    if (kind == Importer.YOUTUBE) {
        Text(stringResource(R.string.import_yt_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        RadioRow(!ytSimilar, stringResource(if (collection) R.string.import_yt_exact_many else R.string.import_yt_exact)) { ytSimilar = false }
        RadioRow(ytSimilar, stringResource(if (collection) R.string.import_yt_similar_many else R.string.import_yt_similar)) { ytSimilar = true }
        Spacer(Modifier.height(4.dp))
    }
    if (kind != null) {
        if (!ytDirect) Hint(stringResource(R.string.import_match_hint))
        if (collection) {
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.import_dest_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (ytDirect) {
                RadioRow(asPlaylist, stringResource(R.string.import_dest_local)) { asPlaylist = true }
                RadioRow(!asPlaylist, stringResource(R.string.import_dest_singles)) { asPlaylist = false }
            } else {
                RadioRow(asPlaylist, stringResource(R.string.import_dest_playlist_sc)) { asPlaylist = true }
                RadioRow(!asPlaylist, stringResource(R.string.import_as_downloads)) { asPlaylist = false }
                if (asPlaylist) CheckRow(download, stringResource(R.string.import_download_all)) { download = it }
            }
        } else {
            Hint(stringResource(if (ytDirect) R.string.import_dest_track_singles else R.string.import_dest_track_downloads))
        }
        if (ytDirect) Hint(stringResource(R.string.import_youtube_note))
    }
    Spacer(Modifier.height(6.dp))
    Button(
        enabled = kind != null && !busy,
        onClick = {
            when (kind) {
                Importer.YOUTUBE -> Importer.youtube(ctx, link, asPlaylist, ytSimilar, download)
                Importer.SPOTIFY -> {
                    if (Prefs.spotifyLinked && Prefs.spotifyTokenOrNull() == null && Prefs.spotifyRefresh.isEmpty()) {
                        pendingSpotify = link
                        spotifyLogin = true
                    } else {
                        Importer.spotify(ctx, link, asPlaylist, download)
                    }
                }
                Importer.YANDEX -> Importer.yandex(ctx, link, Prefs.ymToken.ifBlank { null }, asPlaylist, download)
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(stringResource(R.string.import_start)) }

    Spacer(Modifier.height(20.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.import_accounts), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(4.dp))
    accounts()

    ImportProgress()
    Spacer(Modifier.height(4.dp))
    Hint(stringResource(R.string.import_limit_hint))
    Spacer(Modifier.padding(bottom = 8.dp))
}

@Composable
private fun AccountCard(account: ExtAccount?) {
    val name = account?.name ?: stringResource(R.string.import_acc_connected)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1).uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            account?.avatar?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (account != null) Hint(stringResource(R.string.import_acc_connected))
        }
    }
}

private fun toast(ctx: android.content.Context, res: Int) {
    android.widget.Toast.makeText(ctx, ctx.getString(res), android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
fun SpotifyAccountSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.spotifyLinked
    var download by rememberSaveable { mutableStateOf(true) }
    var clientId by rememberSaveable { mutableStateOf(Prefs.spotifyClientId) }
    var webLogin by remember { mutableStateOf<String?>(null) }
    var pendingLikes by remember { mutableStateOf(false) }

    LaunchedEffect(linked) { if (linked) ExternalAccounts.refreshSpotify() }

    webLogin?.let { mode ->
        SpotifyLoginDialog(refresh = mode == "refresh", onDismiss = {
            webLogin = null
            pendingLikes = false
        }) { t, until ->
            Prefs.changeSpotifyToken(t, until)
            webLogin = null
            toast(ctx, R.string.import_sp_login_done)
            if (pendingLikes) Importer.spotifyLikes(ctx, download)
            pendingLikes = false
            scope.launch { ExternalAccounts.refreshSpotify() }
        }
    }

    if (linked) AccountCard(ExternalAccounts.spotify) else Hint(stringResource(R.string.import_sp_hint))
    GuideLink("spotify")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = clientId,
        onValueChange = {
            clientId = it.trim()
            Prefs.changeSpotifyClientId(clientId)
        },
        singleLine = true,
        isError = clientId.isNotBlank() && !ExternalAccounts.clientIdValid(clientId),
        label = { Text(stringResource(R.string.import_sp_client_id)) },
        modifier = Modifier.fillMaxWidth(),
    )
    val valid = ExternalAccounts.clientIdValid(clientId)
    when {
        clientId.isBlank() -> Hint(stringResource(R.string.import_sp_client_hint))
        clientId == ExternalAccounts.spotifyRejectedClient ->
            Text(stringResource(R.string.import_sp_client_rejected), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        !valid -> Text(stringResource(R.string.import_sp_client_bad), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = { openInDefaultBrowser(ctx, "https://developer.spotify.com/dashboard") }) {
        Text(stringResource(R.string.import_sp_dashboard))
    }
    Spacer(Modifier.height(6.dp))
    if (!linked) {
        Button(
            enabled = !busy,
            onClick = { if (!valid || !SpotifyPkce.start(ctx)) webLogin = "login" },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (valid) R.string.import_sp_authorize else R.string.import_sp_login)) }
    } else {
        CheckRow(download, stringResource(R.string.import_download_all)) { download = it }
        Button(
            enabled = !busy,
            onClick = {
                if (Prefs.spotifyTokenOrNull() == null && Prefs.spotifyRefresh.isEmpty()) {
                    pendingLikes = true
                    webLogin = "refresh"
                } else {
                    Importer.spotifyLikes(ctx, download)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_sp_likes)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                SpotifyAuth.forget()
                ExternalAccounts.forget(ExternalAccounts.SPOTIFY)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_acc_forget)) }
    }
    ImportProgress()
}

@Composable
fun YouTubeAccountSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.ytCookies.isNotBlank()
    var download by rememberSaveable { mutableStateOf(true) }
    var likesSimilar by rememberSaveable { mutableStateOf(true) }
    var googleLogin by remember { mutableStateOf(false) }

    LaunchedEffect(linked) { if (linked) ExternalAccounts.refreshYoutube() }

    if (googleLogin) {
        GoogleLoginDialog(onDismiss = { googleLogin = false }) { cookie ->
            Prefs.changeYtCookies(cookie)
            googleLogin = false
            toast(ctx, R.string.import_g_login_done)
            scope.launch { ExternalAccounts.refreshYoutube() }
        }
    }

    GuideLink("youtube")
    if (!linked) {
        Hint(stringResource(R.string.import_g_hint))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { googleLogin = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.import_acc_add))
        }
    } else {
        AccountCard(ExternalAccounts.youtube)
        Text(stringResource(R.string.import_yt_mode), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        RadioRow(!likesSimilar, stringResource(R.string.import_yt_exact_many)) { likesSimilar = false }
        RadioRow(likesSimilar, stringResource(R.string.import_yt_similar_many)) { likesSimilar = true }
        Spacer(Modifier.height(4.dp))
        if (likesSimilar) {
            Hint(stringResource(R.string.import_g_likes_hint))
            CheckRow(download, stringResource(R.string.import_download_all)) { download = it }
        } else {
            Hint(stringResource(R.string.import_youtube_note))
        }
        Button(
            enabled = !busy,
            onClick = { Importer.youtube(ctx, YouTubeImport.LIKES_URL, asPlaylist = true, similar = likesSimilar, download = download) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_ym_likes)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                GoogleAuth.forget()
                Prefs.changeYtCookies("")
                ExternalAccounts.forget(ExternalAccounts.YOUTUBE)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_acc_forget)) }
    }
    ImportProgress()
}

@Composable
fun YandexAccountSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val busy = Importer.busy
    val linked = Prefs.ymToken.isNotBlank()
    var download by rememberSaveable { mutableStateOf(true) }
    var token by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var rejected by remember { mutableStateOf(false) }
    var awaitingYandex by rememberSaveable { mutableStateOf(false) }
    var webLogin by remember { mutableStateOf(false) }

    LaunchedEffect(linked) {
        if (!linked) return@LaunchedEffect
        rejected = ExternalAccounts.checkYandex(Prefs.ymToken) == false
    }

    fun accept(t: String) {
        if (checking) return
        checking = true
        rejected = false
        scope.launch {
            when (ExternalAccounts.checkYandex(t)) {
                false -> rejected = true
                else -> {
                    Prefs.changeYmToken(t)
                    token = ""
                    awaitingYandex = false
                    toast(ctx, R.string.import_ym_login_done)
                }
            }
            checking = false
        }
    }

    fun takeYandex(fromClipboard: Boolean): Boolean {
        val clip = (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager)
            ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
        val t = YandexAuth.tokenFrom(clip)
        if (t == null) {
            if (fromClipboard) toast(ctx, R.string.import_ym_paste_fail)
            return false
        }
        token = t
        accept(t)
        return true
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var resumes by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resumes) {
        if (!awaitingYandex || linked) return@LaunchedEffect
        kotlinx.coroutines.delay(500)
        takeYandex(false)
    }

    if (webLogin) {
        YandexLoginDialog(onDismiss = { webLogin = false }) { t ->
            webLogin = false
            token = t
            accept(t)
        }
    }
    GuideLink("yandex-music")
    if (!linked) {
        Hint(stringResource(R.string.import_ym_steps))
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = {
                token = YandexAuth.tokenFrom(it.trim()) ?: it.trim()
                rejected = false
            },
            singleLine = true,
            isError = rejected,
            visualTransformation = PasswordVisualTransformation(),
            placeholder = { Text(stringResource(R.string.import_ym_token)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (rejected) Text(stringResource(R.string.import_err_token), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        Button(
            enabled = !busy && !checking,
            onClick = {
                if (token.isNotBlank()) {
                    accept(token)
                } else {
                    awaitingYandex = true
                    openInDefaultBrowser(ctx, YandexAuth.AUTH_URL)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    checking -> stringResource(R.string.import_acc_checking)
                    token.isNotBlank() -> stringResource(R.string.import_ym_use_token)
                    else -> stringResource(R.string.import_ym_login)
                },
            )
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            enabled = !busy && !checking,
            onClick = {
                awaitingYandex = false
                webLogin = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_ym_auto)) }
        if (awaitingYandex) {
            Hint(stringResource(R.string.import_ym_browser_hint))
            TextButton(onClick = { takeYandex(true) }) { Text(stringResource(R.string.import_ym_paste)) }
        }
    } else {
        AccountCard(ExternalAccounts.yandex)
        if (rejected) Text(stringResource(R.string.import_err_token), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        CheckRow(download, stringResource(R.string.import_download_all)) { download = it }
        Button(
            enabled = !busy,
            onClick = { Importer.yandex(ctx, null, Prefs.ymToken, true, download) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_ym_likes)) }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(
            enabled = !busy,
            onClick = {
                Prefs.changeYmToken("")
                YandexAuth.forget()
                ExternalAccounts.forget(ExternalAccounts.YANDEX)
                rejected = false
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.import_acc_forget)) }
    }
    Hint(stringResource(R.string.import_ym_note))
    ImportProgress()
}
