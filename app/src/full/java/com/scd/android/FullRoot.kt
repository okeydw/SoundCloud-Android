package com.scd.android

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

sealed interface Screen {
    data class PlaylistS(val playlist: Playlist) : Screen
    data class ArtistS(val urn: String) : Screen
    data class ShelfS(val id: String) : Screen
    data class GenreS(val key: String, val label: String) : Screen
    data object DislikedS : Screen
    data object SettingsS : Screen
    data object WaveS : Screen
    data class CatalogS(val id: String) : Screen
    data class AlbumS(val id: String) : Screen
    data object StatsS : Screen
}

class Nav {
    var stack by mutableStateOf<List<Screen>>(emptyList())
        private set
    var player by mutableStateOf(false)

    val top: Screen? get() = stack.lastOrNull()

    fun push(s: Screen) {
        val key = screenKey(s)
        stack = stack.filterNot { screenKey(it) == key } + s
    }

    fun pop() {
        stack = stack.dropLast(1)
    }

    fun clear() {
        stack = emptyList()
    }
}

val LocalNav = compositionLocalOf { Nav() }
val LocalController = compositionLocalOf<MediaController?> { null }
val LocalDockPadding = compositionLocalOf { 0.dp }

@Composable
fun AppRoot(controller: MediaController?) {
    GlassTheme {
        val activity = LocalActivity.current as? ComponentActivity
        val p = LocalGlass.current
        LaunchedEffect(p.dark) {
            val transparent = Color.Transparent.toArgb()
            val style = if (p.dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
            activity?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
        CompositionLocalProviderController(controller) {
            Root(controller)
        }
    }
}

@Composable
private fun CompositionLocalProviderController(controller: MediaController?, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalController provides controller, content = content)
}

@Composable
private fun Root(controller: MediaController?) {
    val context = LocalContext.current
    val signedIn = SessionGuard.signedIn
    var guest by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(SessionState.expired) { SessionGuard.handleExpired(context, controller) }
    LaunchedEffect(signedIn) { if (signedIn) guest = false }

    val linkToken = PendingLink.token
    val linkPull = PendingLink.pull
    when {
        linkToken == null -> {}
        linkPull && !signedIn -> LaunchedEffect(linkToken) {
            android.widget.Toast.makeText(context, context.getString(R.string.link_pull_need_login), android.widget.Toast.LENGTH_LONG).show()
            PendingLink.clear()
        }
        linkPull && !PendingLink.fromScanner -> LaunchedEffect(linkToken) { PendingLink.clear() }
        else -> GlassLinkDialog(linkToken, linkPull, signedIn, controller)
    }

    when {
        signedIn -> FullMain(controller, offlineGuest = false, onSignOut = { SessionGuard.signOut(context, controller) })
        guest -> FullMain(controller, offlineGuest = true, onSignOut = { guest = false })
        else -> GlassLoginScreen(onGuest = { guest = true })
    }
}

@Composable
private fun GlassLinkDialog(token: String, pull: Boolean, signedIn: Boolean, controller: MediaController?) {
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var freshName by remember(token) { mutableStateOf<String?>(null) }
    LaunchedEffect(token, pull) { freshName = LinkClaims.accountName(pull) }
    val account = freshName ?: Prefs.username ?: "SoundCloud"
    AlertDialog(
        onDismissRequest = { if (!busy) PendingLink.clear() },
        title = { Text(stringResource(if (pull) R.string.link_pull_title else R.string.link_confirm_title)) },
        text = {
            Text(
                when {
                    pull -> stringResource(R.string.link_pull_text, account)
                    signedIn -> stringResource(R.string.link_confirm_replace)
                    else -> stringResource(R.string.link_confirm_text)
                },
            )
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                App.scope.launch {
                    LinkClaims.claim(context, token, pull, controller)
                    busy = false
                }
            }) { Text(stringResource(if (pull) R.string.link_pull_yes else R.string.link_confirm_yes)) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = { PendingLink.clear() }) { Text(stringResource(R.string.cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = LocalGlass.current.scrim,
    )
}

private enum class FullTab { Flow, Search, Me }

@Composable
fun FullMain(controller: MediaController?, offlineGuest: Boolean, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = remember { Nav() }
    val haze = remember { HazeState() }
    var tab by rememberSaveable { mutableStateOf(if (offlineGuest) FullTab.Me else FullTab.Flow) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val liveController by rememberUpdatedState(controller)
    val offline = Prefs.offline || offlineGuest

    LaunchedEffect(offlineGuest) { if (!offlineGuest) Startup.run(context) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        NowPlaying.sync(controller)
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                NowPlaying.sync(controller)
            }
        }
        controller.addListener(listener)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller, lifecycleOwner) {
        val c = controller ?: return@LaunchedEffect
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { PositionTicker.run(c) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { Health.watch(context) }
    }
    LaunchedEffect(Unit) { if (!offlineGuest) Updates.check(context) }

    LaunchedEffect(NowPlaying.openPlayerRequest) {
        if (NowPlaying.openPlayerRequest) {
            nav.player = true
            NowPlaying.openPlayerRequest = false
        }
    }
    LaunchedEffect(NavEvents.route) {
        val r = NavEvents.route ?: return@LaunchedEffect
        NavEvents.consumeRoute()
        nav.player = false
        when {
            r == NavEvents.STATS -> nav.push(Screen.StatsS)
            r.startsWith(NavEvents.CATALOG) -> nav.push(Screen.CatalogS(r.removePrefix(NavEvents.CATALOG)))
            r.startsWith(NavEvents.ALBUM) -> nav.push(Screen.AlbumS(r.removePrefix(NavEvents.ALBUM)))
        }
    }
    LaunchedEffect(NavEvents.userUrn) {
        val u = NavEvents.userUrn ?: return@LaunchedEffect
        NavEvents.consumeUser()
        nav.player = false
        nav.push(Screen.ArtistS(u))
    }
    LaunchedEffect(NavEvents.playlistUrn) {
        NavEvents.playlistUrn?.let { urn ->
            nav.push(Screen.PlaylistS(Playlist(urn = urn, title = NavEvents.playlistTitle ?: "")))
            NavEvents.consume()
        }
    }
    LaunchedEffect(offline, Health.serverDown) {
        if (offline || Health.serverDown) return@LaunchedEffect
        WaveState.load(false)
        Tiles.load(0)
    }
    LaunchedEffect(NavEvents.pendingUrl) {
        val url = NavEvents.pendingUrl ?: return@LaunchedEffect
        NavEvents.consumeUrl()
        scope.launch {
            when (val target = Links.open(url)) {
                is LinkTarget.PlayTrack -> if (Playback.playSingle({ liveController }, target.track)) nav.player = true
                is LinkTarget.OpenUser -> nav.push(Screen.ArtistS(target.urn))
                is LinkTarget.OpenPlaylist -> nav.push(Screen.PlaylistS(target.playlist))
                is LinkTarget.Failed -> android.widget.Toast.makeText(context, context.getString(R.string.link_failed), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = nav.stack.isNotEmpty() && !nav.player) { nav.pop() }

    val top = nav.top
    val immersive = top == Screen.WaveS || top == Screen.SettingsS
    val dockPad = if (immersive) 0.dp else if (NowPlaying.urn != null) 160.dp else 88.dp
    androidx.compose.runtime.CompositionLocalProvider(LocalNav provides nav, LocalDockPadding provides dockPad) {
        val screens = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().hazeSource(haze)) {
                Backdrop(colors = soundprintColors(), stars = true)
                AnimatedContent(
                    targetState = top ?: tab,
                    contentKey = { screenKey(it) },
                    transitionSpec = {
                        val d = Motion.duration
                        val settings = initialState == Screen.SettingsS || targetState == Screen.SettingsS
                        when {
                            settings -> (fadeIn(tween(d + 120)) + androidx.compose.animation.scaleIn(tween(d + 120), initialScale = 0.96f)) togetherWith
                                (fadeOut(tween(d)) + androidx.compose.animation.scaleOut(tween(d + 120), targetScale = 1.02f))
                            else -> fadeIn(tween(d + 100)) togetherWith fadeOut(tween(d * 4 / 5))
                        }
                    },
                    label = "screen",
                ) { target ->
                    screens.SaveableStateProvider(screenKey(target)) {
                        when (target) {
                            FullTab.Flow -> FlowScreen()
                            FullTab.Search -> SearchScreenGlass()
                            FullTab.Me -> MeScreen(offline = offline, onSignOut = onSignOut, guest = offlineGuest)
                            is Screen.PlaylistS -> PlaylistDetail(target.playlist, offline)
                            is Screen.ArtistS -> ArtistDetail(target.urn, offline)
                            is Screen.ShelfS -> ShelfDetail(target.id)
                            is Screen.GenreS -> GenreDetail(target.key, target.label, offline)
                            Screen.DislikedS -> DislikedDetail()
                            Screen.SettingsS -> SettingsGlass(onSignOut = onSignOut)
                            Screen.WaveS -> WaveMode()
                            is Screen.CatalogS -> CatalogArtistDetail(target.id)
                            is Screen.AlbumS -> AlbumDetail(target.id)
                            Screen.StatsS -> StatsDetail()
                            else -> {}
                        }
                    }
                }
            }

            if (!immersive && Prefs.headerAlpha > 0.01f) {
                val bg = LocalGlass.current.bg
                Box(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .background(bg.copy(alpha = Prefs.headerAlpha * 0.9f)),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if ((Health.noInternet || Health.serverDown) && !offline) {
                    GlassChip(
                        stringResource(if (Health.noInternet) R.string.no_internet else R.string.server_down),
                        icon = R.drawable.ic_no_wifi,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Updates.available?.let { (tag, url) ->
                    var prompt by remember { mutableStateOf(false) }
                    if (!immersive) {
                        GlassChip(
                            updateBannerText(tag),
                            icon = R.drawable.ic_download,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onClick = {
                                when (Updater.state) {
                                    UpdateState.Ready, UpdateState.NeedsPermission -> Updater.install(context)
                                    else -> prompt = true
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (prompt) UpdatePrompt(tag, url, onDismiss = { prompt = false })
                }
                AnimatedVisibility(
                    visible = !immersive,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(500, easing = EaseApple)) { it / 2 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(300)) { it / 2 },
                ) {
                    Column {
                        LiquidPillDock(controller, haze, onOpen = { nav.player = true })
                        if (NowPlaying.urn != null) Spacer(Modifier.height(10.dp))
                        if (!offlineGuest) {
                            FloatingTabDock(
                                tabs = listOf(
                                    DockTab(R.drawable.ic_wave, stringResource(R.string.tab_wave)),
                                    DockTab(R.drawable.ic_search, stringResource(R.string.tab_search)),
                                    DockTab(R.drawable.ic_user, stringResource(R.string.tab_me)),
                                ),
                                selected = tab.ordinal,
                                onSelect = { i ->
                                    tab = FullTab.entries[i]
                                    nav.clear()
                                },
                                haze = haze,
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = nav.player && controller != null,
                enter = slideInVertically(tween(520, easing = EaseApple)) { it } + fadeIn(tween(300)),
                exit = slideOutVertically(tween(380, easing = EaseApple)) { it } + fadeOut(tween(250)),
            ) {
                controller?.let { c -> PlayerGlass(c, onClose = { nav.player = false }) }
            }
        }
    }
}

@Composable
fun ScreenTop(title: String?, onBack: (() -> Unit)?, trailing: @Composable () -> Unit = {}) {
    val p = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconOrb(R.drawable.ic_arrow_back, stringResource(R.string.cd_back), size = 42.dp, iconSize = 18.dp, onClick = onBack)
            Spacer(Modifier.width(12.dp))
        }
        if (title != null) {
            Text(title, style = GlassType.section, color = p.text, maxLines = 1, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        trailing()
    }
}

@Composable
fun dockContentPadding(top: androidx.compose.ui.unit.Dp = 0.dp): PaddingValues {
    val nav = WindowInsets.navigationBars
    val density = androidx.compose.ui.platform.LocalDensity.current
    val bottom = with(density) { nav.getBottom(this).toDp() }
    return PaddingValues(top = top, bottom = LocalDockPadding.current + bottom + 16.dp)
}

@Composable
fun SettingsGlass(onSignOut: () -> Unit) {
    val nav = LocalNav.current
    GlassSettingsSkin {
        SettingsInsetsColumn {
            SettingsScreen(
                username = Prefs.username,
                avatar = Prefs.avatarUrl,
                onBack = { nav.pop() },
                onLogout = {
                    nav.clear()
                    onSignOut()
                },
                visualExtras = { EffectsPicker() },
                accountExtras = {
                    if (!Prefs.offline && Prefs.userUrn != null) {
                        var auraOpen by remember { mutableStateOf(false) }
                        SettingsLink(R.drawable.ic_wave, stringResource(R.string.aura_title), stringResource(if (Prefs.star) R.string.aura_sub else R.string.aura_star_only)) {
                            auraOpen = true
                        }
                        if (auraOpen) AuraPickerDialog(onDismiss = { auraOpen = false })
                        SettingsLink(R.drawable.ic_stats, stringResource(R.string.stats_title), stringResource(R.string.stats_sub)) {
                            nav.push(Screen.StatsS)
                        }
                    }
                    if (!Prefs.offline) {
                        SettingsLink(R.drawable.ic_thumb_down, stringResource(R.string.disliked), stringResource(R.string.disliked_sub)) {
                            nav.push(Screen.DislikedS)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun EffectsPicker() {
    val context = LocalContext.current
    val p = LocalGlass.current
    val current = resolveFxMode(context)
    Text(stringResource(R.string.effects_title), style = GlassType.rowTitle, color = p.text)
    Text(stringResource(R.string.effects_hint), style = GlassType.small, color = p.text2)
    Spacer(Modifier.height(10.dp))
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            FxMode.Max to R.string.effects_max,
            FxMode.Balance to R.string.effects_balance,
            FxMode.Light to R.string.effects_light,
            FxMode.Off to R.string.effects_off,
        ).forEach { (mode, label) ->
            GlassChip(stringResource(label), active = current == mode, onClick = { Prefs.changeEffects(mode.key) })
        }
    }
    Spacer(Modifier.height(18.dp))
}

private fun screenKey(target: Any): String = when (target) {
    is FullTab -> "tab:" + target.name
    is Screen.PlaylistS -> "pl:" + target.playlist.urn
    is Screen.ArtistS -> "ar:" + target.urn
    is Screen.ShelfS -> "sh:" + target.id
    is Screen.GenreS -> "ge:" + target.key
    is Screen.CatalogS -> "ca:" + target.id
    is Screen.AlbumS -> "al:" + target.id
    else -> target.toString()
}
