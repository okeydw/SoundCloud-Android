package com.scd.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.launch


class SettingsSkin(
    val header: @Composable (title: String, onBack: () -> Unit) -> Unit,
    val panel: @Composable (content: @Composable () -> Unit) -> Unit,
    val row: @Composable (icon: Int, title: String, subtitle: String, onClick: () -> Unit) -> Unit,
    val equalizer: (@Composable () -> Unit)? = null,
    val footerAlpha: Boolean = true,
)

val LocalSettingsSkin = androidx.compose.runtime.staticCompositionLocalOf<SettingsSkin?> { null }

@Composable
private fun SettingsPanel(content: @Composable () -> Unit) {
    val skin = LocalSettingsSkin.current
    if (skin != null) skin.panel(content) else content()
}

private const val UPLOAD_VISIBLE = false

private object Sec {
    const val ROOT = -1
    const val ACCOUNT = 0
    const val VISUAL = 1
    const val STORAGE = 2
    const val LINKS = 3
    const val ABOUT = 4
    const val LOGS = 5
    const val ADVANCED = 6
    const val EQUALIZER = 7
    const val NETWORK = 8
    const val CAR = 9
    const val IMPORT = 10
    const val IMPORT_SPOTIFY = 11
    const val IMPORT_YOUTUBE = 12
    const val IMPORT_YANDEX = 13
    const val UPLOAD = 14
}


@Composable
fun SettingsScreen(
    username: String?,
    avatar: String? = null,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    visualExtras: @Composable () -> Unit = {},
    accountExtras: @Composable () -> Unit = {},
) {
    val activity = LocalActivity.current
    val ctx = LocalContext.current
    var section by remember { mutableStateOf(Sec.ROOT) }
    val scope = rememberCoroutineScope()
    val scanQr = rememberQrScanner()
    var showPushQr by remember { mutableStateOf(false) }
    if (showPushQr) LinkQrDialog(push = true, onDismiss = { showPushQr = false })
    fun goBack() {
        section = when (section) {
            Sec.ADVANCED -> Sec.VISUAL
            Sec.NETWORK -> Sec.ACCOUNT
            Sec.IMPORT_SPOTIFY, Sec.IMPORT_YOUTUBE, Sec.IMPORT_YANDEX -> Sec.IMPORT
            Sec.ROOT -> {
                onBack()
                Sec.ROOT
            }
            else -> Sec.ROOT
        }
    }
    BackHandler { goBack() }

    val skin = LocalSettingsSkin.current
    val screenTitle = stringResource(
            when (section) {
                Sec.ACCOUNT -> R.string.tab_account
                Sec.VISUAL -> R.string.tab_visual
                Sec.STORAGE -> R.string.tab_storage
                Sec.LINKS -> R.string.tab_star
                Sec.ABOUT -> R.string.tab_about
                Sec.LOGS -> R.string.tab_logs
                Sec.ADVANCED -> R.string.tab_advanced
                Sec.EQUALIZER -> R.string.equalizer
                Sec.NETWORK -> R.string.relay_section
                Sec.CAR -> R.string.car_section
                Sec.IMPORT -> R.string.import_title
                Sec.IMPORT_SPOTIFY -> R.string.import_sp_title
                Sec.IMPORT_YOUTUBE -> R.string.import_yt_section
                Sec.IMPORT_YANDEX -> R.string.import_src_yandex
                Sec.UPLOAD -> R.string.upload_title
                else -> R.string.settings
            },
        )

    Column(Modifier.fillMaxSize()) {
        if (skin != null) {
            skin.header(screenTitle) { goBack() }
        } else {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { goBack() }) {
                Icon(painterResource(R.drawable.ic_arrow_back), iconLabel(R.drawable.ic_arrow_back))
            }
            Text(
                screenTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        }

        if (section == Sec.ROOT) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
            ) {
                SettingsPanel {
                SettingsRow(
                    R.drawable.ic_user,
                    stringResource(R.string.tab_account),
                    listOfNotNull(username ?: stringResource(R.string.account_signed_in), stringResource(R.string.settings_account_sub)).joinToString(" • "),
                ) { section = Sec.ACCOUNT }
                SettingsRow(R.drawable.ic_music, stringResource(R.string.tab_visual), stringResource(R.string.settings_visual_sub)) { section = Sec.VISUAL }
                SettingsRow(R.drawable.ic_equalizer, stringResource(R.string.equalizer), stringResource(R.string.eq_sub)) { section = Sec.EQUALIZER }
                SettingsRow(R.drawable.ic_download, stringResource(R.string.tab_storage), stringResource(R.string.settings_storage_sub)) { section = Sec.STORAGE }
                SettingsRow(R.drawable.ic_import, stringResource(R.string.import_title), stringResource(R.string.import_sub)) { section = Sec.IMPORT }
                if (UPLOAD_VISIBLE && Api.sessionId != null) {
                    SettingsRow(R.drawable.ic_upload, stringResource(R.string.upload_title), stringResource(R.string.upload_sub_short)) { section = Sec.UPLOAD }
                }
                SettingsRow(R.drawable.ic_play, stringResource(R.string.car_section), stringResource(R.string.car_section_sub)) { section = Sec.CAR }
                SettingsRow(R.drawable.ic_share, stringResource(R.string.tab_star), stringResource(R.string.settings_links_sub)) { section = Sec.LINKS }
                SettingsRow(R.drawable.ic_settings, stringResource(R.string.tab_about), stringResource(R.string.settings_system_sub)) { section = Sec.ABOUT }
                SettingsRow(R.drawable.ic_history, stringResource(R.string.tab_logs), stringResource(R.string.settings_logs_sub)) { section = Sec.LOGS }
                }
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            SettingsPanel {
            when (section) {
                Sec.LINKS -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        android.net.Uri.parse("package:${ctx.packageName}"),
                                    )
                                } else {
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        android.net.Uri.parse("package:${ctx.packageName}"),
                                    )
                                }
                                runCatching { ctx.startActivity(intent) }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(painterResource(R.drawable.ic_wave), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.open_links_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.open_links_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Icon(
                            painterResource(R.drawable.ic_chevron_down),
                            null,
                            modifier = Modifier.size(18.dp).rotate(-90f),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val links = listOf(
                        Triple(R.drawable.ic_user, "Discord", "https://discord.gg/Au3ebtfYu3"),
                        Triple(R.drawable.ic_music, "Android · GitHub", "https://github.com/okeydw/SoundCloud-Android"),
                        Triple(R.drawable.ic_music, "Desktop · GitHub", "https://github.com/zxcloli666/SoundCloud-Desktop"),
                        Triple(R.drawable.ic_wave, "soundcloud-desktop.fun", "https://soundcloud-desktop.fun/"),
                    )
                    links.forEach { (icon, label, url) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    runCatching {
                                        ctx.startActivity(
                                            android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url),
                                            ),
                                        )
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(painterResource(icon), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    url.removePrefix("https://").removeSuffix("/"),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                painterResource(R.drawable.ic_share),
                                null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Sec.ACCOUNT -> {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (avatar != null) {
                                AsyncImage(
                                    model = Api.artworkUrl(avatar, "t120x120"),
                                    contentDescription = null,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Icon(painterResource(R.drawable.ic_user), null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(username ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.account_signed_in),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Star", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(
                            stringResource(if (Prefs.star) R.string.star_active else R.string.star_inactive),
                            color = if (Prefs.star) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    SettingsRow(R.drawable.ic_share, stringResource(R.string.link_share_title), stringResource(R.string.link_share_sub)) { showPushQr = true }
                    SettingsRow(R.drawable.ic_user, stringResource(R.string.link_scan_title), stringResource(R.string.link_scan_sub)) { scanQr() }
                    SettingsRow(R.drawable.ic_wave, stringResource(R.string.relay_section), stringResource(R.string.relay_section_sub)) { section = Sec.NETWORK }
                    if (Prefs.userUrn != null && !Prefs.offline) {
                        var showBlocked by remember { mutableStateOf(false) }
                        SettingsRow(
                            R.drawable.ic_block,
                            stringResource(R.string.blocked_title),
                            stringResource(R.string.blocked_count, BlockedArtists.entries.size),
                        ) { showBlocked = true }
                        if (showBlocked) BlockedArtistsDialog(onDismiss = { showBlocked = false })
                        LaunchedEffect(Unit) { DiscordInfo.load() }
                        val d = DiscordInfo.link
                        SettingsRow(
                            R.drawable.ic_user,
                            "Discord",
                            when {
                                d != null -> listOfNotNull(
                                    "@" + (d.username ?: d.global_name ?: d.discord_id),
                                    if (d.is_booster) "Booster" else null,
                                    if (d.has_star_role) "Star" else null,
                                ).joinToString(" • ")
                                DiscordInfo.loaded -> stringResource(R.string.discord_unlinked)
                                else -> stringResource(R.string.discord_loading)
                            },
                        ) { Support.apiDiscord(ctx) }
                    }
                    accountExtras()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.api_server), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(if (Prefs.star) R.string.api_server_hint else R.string.api_server_locked),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    listOf(
                        false to stringResource(R.string.api_server_main) + "  ·  api.scnative.space",
                        true to stringResource(R.string.api_server_star) + "  ·  api-star.scnative.space",
                    ).forEach { (star, label) ->
                        val selected = if (Prefs.star) Prefs.apiStar == star else !star
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (Prefs.star || !star) 1f else 0.5f)
                                .clickable(enabled = Prefs.star) { Prefs.changeApiStar(star) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                enabled = Prefs.star,
                                onClick = { Prefs.changeApiStar(star) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(stringResource(R.string.logout)) }
                }

                Sec.VISUAL -> {
                    visualExtras()
                    Text(stringResource(R.string.theme), fontWeight = FontWeight.Medium)
                    listOf(
                        "system" to stringResource(R.string.theme_system),
                        "dark" to stringResource(R.string.theme_dark),
                        "light" to stringResource(R.string.theme_light),
                    ).forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable { Prefs.setThemeMode(value) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = Prefs.theme == value, onClick = { Prefs.setThemeMode(value) })
                            Text(label)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    var langOpen by remember { mutableStateOf(false) }
                    val langLabels = languageLabels()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { langOpen = true }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.language), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text(langLabels[Prefs.language] ?: Prefs.language, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Icon(painterResource(R.drawable.ic_chevron_down), null, modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(-90f), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (langOpen) {
                        LanguageDialog(onDismiss = { langOpen = false }) { code ->
                            langOpen = false
                            if (Prefs.language != code) {
                                Prefs.changeLanguage(code)
                                activity?.recreate()
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.immersive_artwork),
                        stringResource(R.string.immersive_artwork_hint),
                        Prefs.immersiveArtwork,
                    ) { Prefs.changeImmersiveArtwork(it) }

                    Spacer(Modifier.height(8.dp))
                    var iconOpen by remember { mutableStateOf(false) }
                    SettingsRow(R.drawable.ic_music, stringResource(R.string.icon_title), stringResource(R.string.icon_sub)) { iconOpen = true }
                    if (iconOpen) LauncherIconDialog(onDismiss = { iconOpen = false })

                    Spacer(Modifier.height(16.dp))
                    SettingsRow(
                        R.drawable.ic_settings,
                        stringResource(R.string.tab_advanced),
                        stringResource(R.string.settings_advanced_sub),
                    ) { section = Sec.ADVANCED }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.crossfade),
                        stringResource(R.string.crossfade_hint),
                        Prefs.crossfade,
                    ) { Prefs.changeCrossfade(it) }
                    if (Prefs.crossfade) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.crossfade_length), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(R.string.crossfade_seconds, Prefs.crossfadeSeconds),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        androidx.compose.material3.Slider(
                            value = Prefs.crossfadeSeconds.toFloat(),
                            onValueChange = { Prefs.changeCrossfadeSeconds(it.toInt()) },
                            valueRange = 1f..30f,
                            steps = 28,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.stream_tags),
                        stringResource(R.string.stream_tags_hint),
                        Prefs.streamTags,
                    ) { Prefs.changeStreamTags(it) }

                    Spacer(Modifier.height(16.dp))
                    ColorPresets()
                }

                Sec.STORAGE -> {
                    val downloadedSet = Downloads.downloaded
                    val tracks = Downloads.tracks()
                    var usedTracks by remember { mutableStateOf(0L) }
                    LaunchedEffect(downloadedSet) {
                        usedTracks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            tracks.sumOf { Downloads.fileFor(it.urn).length() }
                        }
                    }
                    var cacheBump by remember { mutableStateOf(0) }
                    var cacheBytes by remember { mutableStateOf(0L) }
                    LaunchedEffect(cacheBump) {
                        cacheBytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            CacheTools.sizeBytes(ctx)
                        }
                    }
                    val stat = remember { runCatching { android.os.StatFs(ctx.filesDir.path) }.getOrNull() }
                    val total = stat?.totalBytes ?: 0L
                    val free = stat?.availableBytes ?: 0L
                    val usedOther = (total - free - usedTracks - cacheBytes).coerceAtLeast(0L)

                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.storage_downloaded), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Text("${tracks.size}  ·  ${fmtBytes(usedTracks)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Spacer(Modifier.height(12.dp))
                    val barTotal = (usedTracks + cacheBytes + usedOther + free).toFloat().coerceAtLeast(1f)
                    val minSlice = barTotal * 0.03f
                    val trackWeight = if (usedTracks > 0) maxOf(usedTracks.toFloat(), minSlice) else 0f
                    val cacheWeight = if (cacheBytes > 0) maxOf(cacheBytes.toFloat(), minSlice) else 0f
                    val cacheColor = MaterialTheme.colorScheme.tertiary
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))) {
                        if (trackWeight > 0f) Box(Modifier.weight(trackWeight).height(12.dp).background(MaterialTheme.colorScheme.primary))
                        if (cacheWeight > 0f) Box(Modifier.weight(cacheWeight).height(12.dp).background(cacheColor))
                        if (usedOther > 0) Box(Modifier.weight(usedOther.toFloat()).height(12.dp).background(MaterialTheme.colorScheme.onSurfaceVariant))
                        if (free > 0) Box(Modifier.weight(free.toFloat()).height(12.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                    }
                    Spacer(Modifier.height(10.dp))
                    StorageLegend(MaterialTheme.colorScheme.primary, stringResource(R.string.storage_used_tracks), fmtBytes(usedTracks))
                    StorageLegend(cacheColor, stringResource(R.string.storage_cache), fmtBytes(cacheBytes))
                    StorageLegend(MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.storage_other), fmtBytes(usedOther))
                    StorageLegend(MaterialTheme.colorScheme.surfaceVariant, stringResource(R.string.storage_free), fmtBytes(free))

                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.storage_cache_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Spacer(Modifier.height(4.dp))
                    var limitOpen by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cache_limit), Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { limitOpen = true }) {
                                Text(cacheSizeLabel(Prefs.cacheLimit))
                                Icon(painterResource(R.drawable.ic_chevron_down), null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = limitOpen, onDismissRequest = { limitOpen = false }) {
                                CacheLimits.sizes.forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(cacheSizeLabel(value)) },
                                        onClick = {
                                            limitOpen = false
                                            if (Prefs.cacheLimit != value) Prefs.changeCacheLimit(value)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    var keepOpen by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cache_keep), Modifier.weight(1f))
                        Box {
                            TextButton(onClick = { keepOpen = true }) {
                                Text(cacheDaysLabel(Prefs.cacheDays))
                                Icon(painterResource(R.drawable.ic_chevron_down), null, modifier = Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded = keepOpen, onDismissRequest = { keepOpen = false }) {
                                CacheLimits.days.forEach { value ->
                                    DropdownMenuItem(
                                        text = { Text(cacheDaysLabel(value)) },
                                        onClick = {
                                            keepOpen = false
                                            if (Prefs.cacheDays != value) {
                                                Prefs.changeCacheDays(value)
                                                App.scope.launch {
                                                    MediaCache.prune()
                                                    cacheBump++
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.offline_mode), Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Switch(checked = Prefs.offline, onCheckedChange = { Prefs.setOfflineMode(it) })
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        title = stringResource(R.string.offline_auto),
                        hint = stringResource(R.string.offline_auto_hint),
                        checked = Prefs.autoOffline,
                    ) { Prefs.changeAutoOffline(it) }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        title = stringResource(R.string.hq_streaming),
                        hint = if (Prefs.star) {
                            stringResource(R.string.hq_streaming_hint)
                        } else {
                            stringResource(R.string.hq_streaming_locked)
                        },
                        checked = Prefs.star && Prefs.hqStreaming,
                        enabled = Prefs.star,
                    ) { Prefs.changeHqStreaming(it) }

                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.download_quality), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(if (Prefs.star) R.string.download_quality_hint else R.string.download_quality_locked),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    listOf(
                        true to stringResource(R.string.download_quality_hq),
                        false to stringResource(R.string.download_quality_sq),
                    ).forEach { (hq, label) ->
                        val selected = if (Prefs.star) Prefs.downloadHq == hq else !hq
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (Prefs.star || !hq) 1f else 0.5f)
                                .clickable(enabled = Prefs.star) { Prefs.changeDownloadHq(hq) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                enabled = Prefs.star,
                                onClick = { Prefs.changeDownloadHq(hq) },
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    if (tracks.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        val upgrading = Downloads.UPGRADE_KEY in Downloads.activeLists
                        androidx.compose.material3.OutlinedButton(
                            enabled = Prefs.star || upgrading,
                            onClick = {
                                if (upgrading) {
                                    Downloads.cancelList(ctx, Downloads.UPGRADE_KEY)
                                } else {
                                    Downloads.upgradeAll(ctx) { n ->
                                        android.widget.Toast.makeText(
                                            ctx,
                                            if (n == 0) ctx.getString(R.string.upgrade_quality_none)
                                            else ctx.getString(R.string.upgrade_quality_started, n),
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (upgrading) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(stringResource(if (upgrading) R.string.upgrade_quality_stop else R.string.upgrade_quality))
                        }
                        Text(
                            stringResource(if (Prefs.star) R.string.upgrade_quality_hint else R.string.download_quality_locked),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        enabled = !Integrity.running,
                        onClick = {
                            scope.launch {
                                Integrity.scan(ctx)
                                cacheBump++
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.integrity_check))
                    }
                    Text(
                        stringResource(R.string.integrity_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (Integrity.running) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.integrity_progress, Integrity.done, Integrity.total),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(6.dp))
                        if (Integrity.total > 0) {
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { Integrity.done.toFloat() / Integrity.total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    } else {
                        Integrity.last?.let { report ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when {
                                    report.removed == 0 -> stringResource(R.string.integrity_clean, report.checked)
                                    report.redownload > 0 -> stringResource(R.string.integrity_fixed_redownload, report.removed, report.redownload)
                                    else -> stringResource(R.string.integrity_fixed, report.removed)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.play_blocked),
                        stringResource(R.string.play_blocked_hint),
                        Prefs.playBlocked,
                    ) { Prefs.changePlayBlocked(it) }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.anon_fallback),
                        stringResource(R.string.anon_fallback_hint),
                        Prefs.anonFallback,
                    ) { Prefs.changeAnonFallback(it) }

                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.auto_cache_likes),
                        stringResource(R.string.auto_cache_likes_hint),
                        Prefs.autoCacheLikes,
                    ) { on ->
                        Prefs.changeAutoCacheLikes(on)
                        if (on) LikesAutoCache.run(ctx)
                    }
                    Spacer(Modifier.height(8.dp))
                    SettingSwitch(
                        stringResource(R.string.prefetch_wifi_only),
                        stringResource(R.string.prefetch_wifi_only_hint),
                        Prefs.prefetchWifiOnly,
                    ) { Prefs.changePrefetchWifiOnly(it) }

                    Spacer(Modifier.height(12.dp))
                    SiblingShareBlock()

                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                CacheTools.clear(ctx)
                                cacheBump++
                                android.widget.Toast.makeText(
                                    ctx,
                                    ctx.getString(R.string.storage_cache_cleared),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        enabled = cacheBytes > 0,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) { Text(stringResource(R.string.storage_cache_clear)) }

                    var confirmWipe by remember { mutableStateOf(false) }
                    if (confirmWipe) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { confirmWipe = false },
                            text = { Text(stringResource(R.string.clear_downloads_q, tracks.size)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    confirmWipe = false
                                    scope.launch { runCatching { Downloads.removeAll(tracks.map { it.urn }) } }
                                }) {
                                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmWipe = false }) { Text(stringResource(R.string.cancel)) }
                            },
                        )
                    }
                    if (tracks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { confirmWipe = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(stringResource(R.string.clear_downloads)) }
                    }
                }

                Sec.ADVANCED -> {
                    var cropSource by remember { mutableStateOf<android.net.Uri?>(null) }
                    val pickImage = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    ) { uri -> if (uri != null) cropSource = uri }

                    cropSource?.let { uri ->
                        CropDialog(
                            source = uri,
                            onDismiss = { cropSource = null },
                            onResult = { ok ->
                                cropSource = null
                                if (!ok) {
                                    android.widget.Toast.makeText(
                                        ctx,
                                        ctx.getString(R.string.background_failed),
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }

                    Text(stringResource(R.string.background_image), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.background_image_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(onClick = { pickImage.launch("image/*") }) {
                            Text(
                                stringResource(
                                    if (Prefs.backgroundImage == null) R.string.background_choose
                                    else R.string.background_replace
                                )
                            )
                        }
                        if (Prefs.backgroundImage != null) {
                            androidx.compose.material3.TextButton(
                                onClick = { BackgroundImage.clear(ctx) },
                            ) { Text(stringResource(R.string.background_remove)) }
                        }
                    }

                    if (Prefs.backgroundImage != null) {
                        Spacer(Modifier.height(8.dp))
                        SettingSwitch(
                            title = stringResource(R.string.background_blur),
                            hint = if (BlurSupport.available) {
                                stringResource(R.string.background_blur_hint)
                            } else {
                                stringResource(R.string.background_blur_unsupported)
                            },
                            checked = Prefs.backgroundBlur && BlurSupport.available,
                            enabled = BlurSupport.available,
                        ) { Prefs.changeBackgroundBlur(it) }

                        if (Prefs.backgroundBlur && BlurSupport.available) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.background_blur_amount) +
                                    "  ${Prefs.backgroundBlurRadius.toInt()}",
                                fontWeight = FontWeight.Medium,
                            )
                            androidx.compose.material3.Slider(
                                value = Prefs.backgroundBlurRadius,
                                onValueChange = { Prefs.changeBackgroundBlurRadius(it) },
                                valueRange = 0f..60f,
                            )
                        }

                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.background_dim), fontWeight = FontWeight.Medium)
                        androidx.compose.material3.Slider(
                            value = Prefs.backgroundDim,
                            onValueChange = { Prefs.changeBackgroundDim(it) },
                            valueRange = 0.2f..0.95f,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        stringResource(R.string.header_alpha) +
                            "  ${(Prefs.headerAlpha * 100).toInt()}%",
                        fontWeight = FontWeight.Medium,
                    )
                    androidx.compose.material3.Slider(
                        value = Prefs.headerAlpha,
                        onValueChange = { Prefs.changeHeaderAlpha(it) },
                        valueRange = 0f..1f,
                    )
                    if (skin?.footerAlpha != false) {
                        Text(
                            stringResource(R.string.footer_alpha) +
                                "  ${(Prefs.footerAlpha * 100).toInt()}%",
                            fontWeight = FontWeight.Medium,
                        )
                        androidx.compose.material3.Slider(
                            value = Prefs.footerAlpha,
                            onValueChange = { Prefs.changeFooterAlpha(it) },
                            valueRange = 0f..1f,
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(stringResource(R.string.accent_custom), fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    RgbPicker(
                        color = Prefs.accent,
                        onChange = { Prefs.changeAccent(it) },
                        onReset = { Prefs.changeAccent(AccentPalette.DEFAULT) },
                    )

                    Spacer(Modifier.height(20.dp))
                    SettingSwitch(
                        title = stringResource(R.string.background_custom),
                        hint = stringResource(R.string.background_custom_hint),
                        checked = Prefs.bgColor1 != 0,
                    ) { on -> Prefs.changeBackgroundColors(if (on) BackgroundPalette.solids.first() else 0, 0) }
                    if (Prefs.bgColor1 != 0) {
                        Spacer(Modifier.height(4.dp))
                        RgbPicker(
                            color = Prefs.bgColor1,
                            onChange = { Prefs.changeBackgroundColors(it, Prefs.bgColor2) },
                            onReset = { Prefs.changeBackgroundColors(BackgroundPalette.solids.first(), Prefs.bgColor2) },
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    SettingSwitch(
                        title = stringResource(R.string.text_color_custom),
                        hint = stringResource(R.string.text_color_hint),
                        checked = Prefs.textColor != 0,
                    ) { on ->
                        Prefs.changeTextColor(if (on) 0xFFFFFFFF.toInt() else 0)
                    }
                    if (Prefs.textColor != 0) {
                        Spacer(Modifier.height(4.dp))
                        RgbPicker(
                            color = Prefs.textColor,
                            onChange = { Prefs.changeTextColor(it) },
                            onReset = { Prefs.changeTextColor(0) },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Text(stringResource(R.string.theme_share), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.theme_share_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    val exportTheme = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts
                            .CreateDocument("application/json"),
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                val ok = ThemeIO.export(ctx, uri)
                                android.widget.Toast.makeText(
                                    ctx,
                                    ctx.getString(if (ok) R.string.theme_exported else R.string.theme_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }
                    val importTheme = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.GetContent(),
                    ) { uri ->
                        if (uri != null) {
                            scope.launch {
                                val ok = ThemeIO.import(ctx, uri)
                                android.widget.Toast.makeText(
                                    ctx,
                                    ctx.getString(if (ok) R.string.theme_imported else R.string.theme_failed),
                                    android.widget.Toast.LENGTH_SHORT,
                                ).show()
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedButton(
                            onClick = { exportTheme.launch("scd-theme.json") },
                        ) { Text(stringResource(R.string.theme_export)) }
                        androidx.compose.material3.OutlinedButton(
                            onClick = { importTheme.launch("application/json") },
                        ) { Text(stringResource(R.string.theme_import)) }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.theme_discord),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.clickable { section = Sec.LINKS },
                    )
                }

                Sec.EQUALIZER -> skin?.equalizer?.invoke() ?: EqualizerPanel()

                Sec.NETWORK -> NetworkSection()

                Sec.CAR -> CarSection()
                Sec.IMPORT -> ImportSection {
                    val none = stringResource(R.string.import_acc_none)
                    val connected = stringResource(R.string.import_acc_connected)
                    SettingsRow(
                        R.drawable.ic_user,
                        stringResource(R.string.import_sp_title),
                        if (Prefs.spotifyLinked) ExternalAccounts.spotify?.name ?: connected else none,
                    ) { section = Sec.IMPORT_SPOTIFY }
                    SettingsRow(
                        R.drawable.ic_user,
                        stringResource(R.string.import_yt_section),
                        if (Prefs.ytCookies.isNotBlank()) ExternalAccounts.youtube?.name ?: connected else none,
                    ) { section = Sec.IMPORT_YOUTUBE }
                    SettingsRow(
                        R.drawable.ic_user,
                        stringResource(R.string.import_src_yandex),
                        if (Prefs.ymToken.isNotBlank()) ExternalAccounts.yandex?.name ?: connected else none,
                    ) { section = Sec.IMPORT_YANDEX }
                }
                Sec.IMPORT_SPOTIFY -> SpotifyAccountSection()
                Sec.IMPORT_YOUTUBE -> YouTubeAccountSection()
                Sec.IMPORT_YANDEX -> YandexAccountSection()
                Sec.UPLOAD -> UploadSection()

                Sec.LOGS -> {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${Logs.lines.size}",
                            Modifier.weight(1f),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, Logs.dump())
                            }
                            ctx.startActivity(android.content.Intent.createChooser(send, null))
                        }) {
                            Icon(painterResource(R.drawable.ic_share), stringResource(R.string.cd_share), modifier = Modifier.size(20.dp))
                        }
                        IconButton(onClick = { Logs.clear() }) {
                            Icon(painterResource(R.drawable.ic_trash), stringResource(R.string.logs_clear), modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (Logs.lines.isEmpty()) {
                        Text(
                            stringResource(R.string.logs_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        Logs.lines.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                }

                else -> {
                    val pInfo = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }.getOrNull() }
                    val vName = pInfo?.versionName ?: "—"
                    @Suppress("DEPRECATION")
                    val vCode = pInfo?.versionCode ?: 0
                    InfoRow("App", ctx.packageName)
                    InfoRow(stringResource(R.string.version, "").trim(), "$vName ($vCode)")
                    InfoRow(stringResource(R.string.about_device), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
                    InfoRow("Android", "${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT})")
                    InfoRow("ABI", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "-")
                    InfoRow("API", Api.API_BASE.removePrefix("https://"))
                    InfoRow("Stream", Endpoints.streamBase.removePrefix("https://"))
                    Spacer(Modifier.height(12.dp))
                    SettingSwitch(
                        stringResource(R.string.stream_debug),
                        stringResource(R.string.stream_debug_hint),
                        Prefs.streamDebug,
                    ) { Prefs.changeStreamDebug(it) }
                    Spacer(Modifier.height(16.dp))
                    SupportBlock()
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                val json = Support.pretty(Support.metrics(ctx))
                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(android.content.Intent.EXTRA_TEXT, json)
                                }
                                ctx.startActivity(android.content.Intent.createChooser(send, null))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.about_export)) }
                }
            }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NetworkSection() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        launch { StorageHealth.check() }
        Endpoints.measure()
    }
    SettingSwitch(
        stringResource(R.string.storage_first),
        stringResource(R.string.storage_first_hint),
        Prefs.storageFirst,
    ) { Prefs.changeStorageFirst(it) }
    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.relay_title), fontWeight = FontWeight.Medium)
    Text(
        stringResource(R.string.relay_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(8.dp))
    androidx.compose.material3.OutlinedButton(
        enabled = !Endpoints.measuring,
        onClick = {
            scope.launch { StorageHealth.check() }
            scope.launch { Endpoints.measure() }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (Endpoints.measuring) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(stringResource(R.string.relay_measure))
    }
    Spacer(Modifier.height(8.dp))
    val mode = Endpoints.relayMode
    val active = Endpoints.activeNode
    RelayRow(
        selected = mode == Endpoints.AUTO,
        title = stringResource(R.string.relay_auto),
        subtitle = stringResource(R.string.relay_auto_sub, nodeLabel(active)),
        ping = null,
        onClick = { scope.launch { Endpoints.choose(Endpoints.AUTO) } },
    )
    val statuses = Endpoints.statuses.ifEmpty { Endpoints.nodes.map { NodeStatus(it, Endpoints.hostOf(it), null) } }
    statuses.forEach { st ->
        RelayRow(
            selected = mode == st.node,
            title = nodeLabel(st.node) + if (st.node == active) "  ·  " + stringResource(R.string.relay_in_use) else "",
            subtitle = if (Endpoints.statuses.isEmpty() || !st.ok) st.host else st.host + "\n" + when {
                st.tampered -> stringResource(R.string.relay_volume_tamper)
                st.carries -> stringResource(R.string.relay_volume_ok, st.kbps ?: 0L)
                st.bytes == null -> stringResource(R.string.relay_volume_unknown)
                else -> stringResource(R.string.relay_volume_cut, ((st.bytes ?: 0L) / 1024).toInt())
            },
            ping = if (Endpoints.statuses.isEmpty()) "" else st.ms?.let { stringResource(R.string.relay_ms, it) } ?: stringResource(R.string.relay_down),
            pingOk = st.ok && !st.cut,
            onClick = { scope.launch { Endpoints.choose(st.node) } },
        )
    }
    Spacer(Modifier.height(16.dp))
    StorageStatusBlock()
}

@Composable
private fun StorageStatusBlock() {
    Text(stringResource(R.string.storage_status_title), fontWeight = FontWeight.Medium)
    Text(
        stringResource(R.string.storage_status_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(6.dp))
    val probes = StorageHealth.probes
    if (probes.isEmpty()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (StorageHealth.checking) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(R.string.storage_status_checking), style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    probes.groupBy { it.star }.toSortedMap(compareByDescending { it }).forEach { (star, list) ->
        Text(
            stringResource(if (star) R.string.storage_status_star else R.string.storage_status_main),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        list.forEach { probe ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        probe.relay?.let { stringResource(R.string.relay_node, it) } ?: stringResource(R.string.relay_direct),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(probe.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when (probe.state) {
                        StorageState.Ok -> stringResource(R.string.relay_ms, probe.ms ?: 0L)
                        StorageState.Failing -> stringResource(R.string.storage_status_failing, probe.code ?: 0)
                        StorageState.Down -> stringResource(R.string.relay_down)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (probe.state == StorageState.Ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun nodeLabel(node: String): String =
    if (node == Endpoints.DIRECT) stringResource(R.string.relay_direct) else stringResource(R.string.relay_node, node)

@Composable
private fun RelayRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    ping: String?,
    pingOk: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ping != null) {
            Text(
                ping,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (pingOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}

@Composable
private fun CarSection() {
    val ctx = LocalContext.current
    var permissionTick by remember { mutableStateOf(0) }
    val askBluetooth = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { permissionTick++ }

    fun requestPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            askBluetooth.launch(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    SettingSwitch(
        stringResource(R.string.car_autoplay),
        stringResource(R.string.car_autoplay_hint),
        Prefs.carAutoplay,
    ) { on ->
        Prefs.changeCarAutoplay(on)
        if (on && CarAutoplay.needsPermission(ctx)) requestPermission()
    }
    val needsPermission = remember(permissionTick, Prefs.carAutoplay) { CarAutoplay.needsPermission(ctx) }
    if (Prefs.carAutoplay && needsPermission) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.car_permission),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
        TextButton(onClick = { requestPermission() }) { Text(stringResource(R.string.car_permission_grant)) }
    }
    if (!Prefs.carAutoplay) return

    val paired = remember(permissionTick, needsPermission) { CarAutoplay.pairedDevices(ctx) }
    val names = paired.associate { it.address to it.name }
    val scenarios = CarScenarios.all
    var editing by remember { mutableStateOf<CarScenario?>(null) }

    Spacer(Modifier.height(12.dp))
    Text(stringResource(R.string.car_scenarios), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(
        stringResource(R.string.car_scenarios_hint),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(4.dp))
    if (scenarios.isEmpty()) {
        Text(
            stringResource(R.string.car_scenario_empty),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
    scenarios.forEachIndexed { i, sc ->
        SettingsRow(
            R.drawable.ic_play,
            scenarioTitle(sc, i),
            carDevicesLabel(sc, names) + " · " + carSourceLabel(sc.source, sc.sourceTitle),
        ) { editing = sc }
    }
    TextButton(onClick = { editing = CarScenario(id = CarScenarios.newId()) }) {
        Icon(painterResource(R.drawable.ic_plus), null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.car_scenario_add))
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.car_beta),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )

    editing?.let { sc ->
        CarScenarioDialog(
            initial = sc,
            index = scenarios.indexOfFirst { it.id == sc.id }.let { if (it < 0) scenarios.size else it },
            isNew = scenarios.none { it.id == sc.id },
            paired = paired,
            needsPermission = needsPermission,
            onGrant = { requestPermission() },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun scenarioTitle(sc: CarScenario, index: Int): String =
    sc.name.ifBlank { stringResource(R.string.car_scenario_number, index + 1) }

@Composable
private fun carDevicesLabel(sc: CarScenario, names: Map<String, String>): String =
    if (sc.anyDevice) {
        stringResource(R.string.car_devices_any)
    } else {
        sc.devices.map { (address, name) -> names[address] ?: name }.joinToString(", ")
    }

@Composable
private fun carSourceLabel(source: String, title: String): String = when {
    source == CarAutoplay.LIKED -> stringResource(R.string.liked)
    source == CarAutoplay.WAVE -> stringResource(R.string.tab_wave)
    source == CarAutoplay.DOWNLOADS -> stringResource(R.string.downloads)
    else -> title.ifEmpty { stringResource(R.string.playlist_label) }
}

@Composable
private fun CarScenarioDialog(
    initial: CarScenario,
    index: Int,
    isNew: Boolean,
    paired: List<PairedDevice>,
    needsPermission: Boolean,
    onGrant: () -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var pickSource by remember { mutableStateOf(false) }
    var pickDevices by remember { mutableStateOf(false) }
    var pickEq by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val names = paired.associate { it.address to it.name }
    val anyTaken = stringResource(R.string.car_scenario_any_taken)
    val takenFormat = stringResource(R.string.car_scenario_taken)
    val numberFormat = stringResource(R.string.car_scenario_number)
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp).verticalScroll(rememberScrollState())) {
            Text(
                scenarioTitle(draft, index),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it.take(40)) },
                singleLine = true,
                label = { Text(stringResource(R.string.car_scenario_name)) },
                placeholder = { Text(numberFormat.format(index + 1)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Column(Modifier.padding(horizontal = 16.dp)) {
                SettingsRow(R.drawable.ic_share, stringResource(R.string.car_devices), carDevicesLabel(draft, names)) { pickDevices = true }
                SettingsRow(R.drawable.ic_play, stringResource(R.string.car_source), carSourceLabel(draft.source, draft.sourceTitle)) { pickSource = true }
                SettingSwitch(
                    stringResource(R.string.car_shuffle),
                    stringResource(R.string.car_shuffle_hint),
                    draft.shuffle,
                ) { draft = draft.copy(shuffle = it) }
                Spacer(Modifier.height(8.dp))
                SettingSwitch(
                    stringResource(R.string.car_autostart),
                    stringResource(R.string.car_autostart_hint),
                    draft.autostart,
                ) { draft = draft.copy(autostart = it) }
                SettingsRow(R.drawable.ic_equalizer, stringResource(R.string.car_eq), carEqLabel(draft.eq)) { pickEq = true }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!isNew) {
                    TextButton(onClick = {
                        CarScenarios.delete(draft.id)
                        onDismiss()
                    }) { Text(stringResource(R.string.car_scenario_delete), color = MaterialTheme.colorScheme.error) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(onClick = {
                    val clash = CarScenarios.conflict(draft)
                    if (clash == null && CarScenarios.save(draft)) {
                        onDismiss()
                    } else if (clash != null) {
                        error = if (draft.anyDevice) {
                            anyTaken
                        } else {
                            val position = CarScenarios.all.indexOfFirst { it.id == clash.id } + 1
                            takenFormat.format(clash.name.ifBlank { numberFormat.format(position) })
                        }
                    }
                }) { Text(stringResource(R.string.save)) }
            }
        }
    }
    if (pickSource) {
        CarSourceDialog(
            selected = draft.source,
            onPick = { source, title -> draft = draft.copy(source = source, sourceTitle = title) },
            onDismiss = { pickSource = false },
        )
    }
    if (pickDevices) {
        CarDevicesDialog(
            scenario = draft,
            paired = paired,
            needsPermission = needsPermission,
            onGrant = onGrant,
            onSave = {
                draft = draft.copy(devices = it)
                error = null
            },
            onDismiss = { pickDevices = false },
        )
    }
    if (pickEq) CarEqDialog(selected = draft.eq, onPick = { draft = draft.copy(eq = it) }, onDismiss = { pickEq = false })
}

@Composable
private fun CarSourceDialog(selected: String, onPick: (String, String) -> Unit, onDismiss: () -> Unit) {
    val liked = stringResource(R.string.liked)
    val wave = stringResource(R.string.tab_wave)
    val downloads = stringResource(R.string.downloads)
    LaunchedEffect(Unit) { runCatching { Library.refreshPlaylists(Prefs.offline) } }
    val playlists = (LocalPlaylists.visible.map { it.asPlaylist() } + Library.likedPlaylists).distinctBy { it.urn }
    fun pick(source: String, title: String) {
        onPick(source, title)
        onDismiss()
    }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.car_source),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                item { SourceRow(selected == CarAutoplay.LIKED, liked, R.drawable.ic_heart) { pick(CarAutoplay.LIKED, liked) } }
                item { SourceRow(selected == CarAutoplay.WAVE, wave, R.drawable.ic_wave) { pick(CarAutoplay.WAVE, wave) } }
                item { SourceRow(selected == CarAutoplay.DOWNLOADS, downloads, R.drawable.ic_download) { pick(CarAutoplay.DOWNLOADS, downloads) } }
                if (playlists.isNotEmpty()) {
                    item {
                        androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(
                            stringResource(R.string.playlists),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                        )
                    }
                    items(playlists, key = { "src:" + it.urn }) { p ->
                        val source = CarAutoplay.PLAYLIST_PREFIX + p.urn
                        SourceRow(selected == source, p.title, null, playlist = p) { pick(source, p.title) }
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
private fun SourceRow(selected: Boolean, title: String, icon: Int?, playlist: Playlist? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        when {
            playlist != null -> PlaylistArtwork(playlist, 36.dp, 6.dp)
            icon != null -> Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CarDevicesDialog(
    scenario: CarScenario,
    paired: List<PairedDevice>,
    needsPermission: Boolean,
    onGrant: () -> Unit,
    onSave: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(scenario.devices) }
    val known = paired.map { it.address }.toSet()
    val rows = paired + scenario.devices.filterKeys { it !in known }.map { (address, name) -> PairedDevice(address, name) }
    val anyOwner = CarScenarios.anyOwner(scenario.id)
    val takenFormat = stringResource(R.string.car_scenario_taken)
    val numberFormat = stringResource(R.string.car_scenario_number)
    val all = CarScenarios.all
    fun ownerName(s: CarScenario): String = s.name.ifBlank { numberFormat.format(all.indexOfFirst { it.id == s.id } + 1) }
    AppDialog(onDismiss) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                stringResource(R.string.car_devices),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = anyOwner == null) { chosen = emptyMap() }
                    .padding(horizontal = 12.dp)
                    .alpha(if (anyOwner == null) 1f else 0.5f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = chosen.isEmpty(), enabled = anyOwner == null, onClick = { chosen = emptyMap() })
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.car_devices_any))
                    if (anyOwner != null) {
                        Text(
                            takenFormat.format(ownerName(anyOwner)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (needsPermission) {
                Text(
                    stringResource(R.string.car_permission),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                TextButton(onClick = onGrant, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.car_permission_grant))
                }
            } else if (rows.isEmpty()) {
                Text(
                    stringResource(R.string.car_devices_none),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(rows, key = { it.address }) { d ->
                    val on = d.address in chosen
                    val owner = all.firstOrNull { it.id != scenario.id && d.address in it.devices }
                    val free = owner == null
                    fun toggle() {
                        if (!free) return
                        chosen = if (on) chosen - d.address else chosen + (d.address to d.name)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = free) { toggle() }
                            .padding(horizontal = 12.dp)
                            .alpha(if (free) 1f else 0.5f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = on,
                            enabled = free,
                            onCheckedChange = { toggle() },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (owner != null) takenFormat.format(ownerName(owner)) else d.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                TextButton(
                    enabled = chosen.isNotEmpty() || anyOwner == null,
                    onClick = {
                        onSave(chosen)
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit) {
    LocalSettingsSkin.current?.let {
        it.row(icon, title, subtitle, onClick)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(icon),
            null,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painterResource(R.drawable.ic_chevron_down),
            null,
            modifier = Modifier.size(20.dp).rotate(-90f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    hint: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.5f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun RgbPicker(color: Int, onChange: (Int) -> Unit, onReset: () -> Unit) {
    val r = (color shr 16) and 0xFF
    val g = (color shr 8) and 0xFF
    val b = color and 0xFF

    fun pack(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    val hex = String.format("%02X%02X%02X", r, g, b)
    var typed by remember(hex) { mutableStateOf(hex) }

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(androidx.compose.ui.graphics.Color(pack(r, g, b)))
            )
            Spacer(Modifier.width(10.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { raw ->
                    val clean = raw.trimStart('#').filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
                        .take(6)
                        .uppercase()
                    typed = clean
                    if (clean.length == 6) {
                        runCatching { clean.toInt(16) }.getOrNull()?.let { rgb ->
                            onChange(pack((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF))
                        }
                    }
                },
                prefix = { Text("#") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReset) { Text(stringResource(R.string.color_reset)) }
        }
        ColorSlider(stringResource(R.string.color_red), r) { onChange(pack(it, g, b)) }
        ColorSlider(stringResource(R.string.color_green), g) { onChange(pack(r, it, b)) }
        ColorSlider(stringResource(R.string.color_blue), b) { onChange(pack(r, g, it)) }
    }
}

@Composable
private fun ColorSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        androidx.compose.material3.Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$value",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
    }
}

@Composable
private fun cacheSizeLabel(value: Long): String =
    if (value == CacheLimits.UNLIMITED) stringResource(R.string.cache_unlimited)
    else stringResource(R.string.cache_gb, (value / (1024L * 1024L * 1024L)).toInt())

@Composable
private fun cacheDaysLabel(value: Int): String =
    if (value == CacheLimits.FOREVER) stringResource(R.string.cache_forever)
    else stringResource(R.string.cache_days, value)

@Composable
private fun StorageLegend(color: androidx.compose.ui.graphics.Color, label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontWeight = FontWeight.Medium)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

private fun fmtBytes(b: Long): String {
    if (b < 1024) return "$b B"
    val kb = b / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.2f GB".format(mb / 1024.0)
}


@Composable
private fun ColorPresets() {
    Text(stringResource(R.string.accent_color), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        AccentPalette.colors.forEach { c ->
            Swatch(c, 0, selected = Prefs.accent == c) { Prefs.changeAccent(c) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.background_color), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        Swatch(
            MaterialTheme.colorScheme.background.toArgb(),
            0,
            selected = Prefs.bgColor1 == 0,
            label = stringResource(R.string.background_default),
            outlined = true,
        ) { Prefs.changeBackgroundColors(0, 0) }
        BackgroundPalette.solids.forEach { c ->
            Swatch(c, 0, selected = Prefs.bgColor1 == c && Prefs.bgColor2 == 0) { Prefs.changeBackgroundColors(c, 0) }
        }
    }
}

@Composable
private fun Swatch(first: Int, second: Int, selected: Boolean, label: String? = null, outlined: Boolean = false, onClick: () -> Unit) {
    val a = androidx.compose.ui.graphics.Color(first)
    val brush = if (second != 0) {
        androidx.compose.ui.graphics.Brush.linearGradient(listOf(a, androidx.compose.ui.graphics.Color(second)))
    } else {
        androidx.compose.ui.graphics.SolidColor(a)
    }
    val light = a.luminance() > 0.6f
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(brush)
            .then(
                if (outlined || selected) {
                    Modifier.border(
                        if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        CircleShape,
                    )
                } else Modifier,
            )
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                painterResource(R.drawable.ic_check),
                label,
                tint = if (light) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun SettingsLink(icon: Int, title: String, subtitle: String, onClick: () -> Unit) = SettingsRow(icon, title, subtitle, onClick)

@Composable
private fun SiblingShareBlock() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val other = if (ctx.packageName.endsWith(".full")) "Lite" else "Full"
    val available = remember { Sibling.available(ctx) }
    val installed = remember { Sibling.installed(ctx) }
    SettingSwitch(
        stringResource(R.string.sibling_share, other),
        stringResource(
            when {
                available -> R.string.sibling_share_hint
                installed -> R.string.sibling_share_key
                else -> R.string.sibling_share_missing
            },
            other,
        ),
        Prefs.siblingShare,
    ) { Prefs.changeSiblingShare(it) }
    if (Prefs.siblingShare && available) {
        val result = Sibling.lastImport
        TextButton(
            enabled = !Sibling.busy,
            onClick = {
                scope.launch {
                    val r = Sibling.sync(ctx, manual = true)
                    android.widget.Toast.makeText(
                        ctx,
                        if (r == null) ctx.getString(R.string.sibling_failed, other)
                        else ctx.getString(R.string.sibling_done, r.first, r.second),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        ) {
            Text(stringResource(if (Sibling.busy) R.string.sibling_busy else R.string.sibling_sync_now))
        }
        if (result != null) {
            Text(
                stringResource(R.string.sibling_done, result.first, result.second),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SupportBlock() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pick by remember { mutableStateOf<Boolean?>(null) }
    Text(stringResource(R.string.support_title), fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    SettingsRow(R.drawable.ic_settings, stringResource(R.string.support_app), stringResource(R.string.support_app_sub)) { pick = true }
    SettingsRow(R.drawable.ic_wave, stringResource(R.string.support_api), stringResource(R.string.support_api_sub)) { pick = false }
    val app = pick ?: return
    AppDialog({ pick = null }) {
        Column(Modifier.padding(24.dp)) {
            Text(
                stringResource(if (app) R.string.support_app else R.string.support_api),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (app) R.string.support_app_hint else R.string.support_api_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    pick = null
                    if (app) scope.launch { Support.appIssue(ctx) } else Support.apiIssue(ctx)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.support_issue)) }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    pick = null
                    if (app) {
                        scope.launch {
                            Support.appDiscord(ctx)
                            android.widget.Toast.makeText(ctx, ctx.getString(R.string.support_copied), android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Support.apiDiscord(ctx)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.support_discord)) }
            Row {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { pick = null }) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}
