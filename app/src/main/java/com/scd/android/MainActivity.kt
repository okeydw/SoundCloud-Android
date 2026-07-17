package com.scd.android

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Orange = Color(0xFFFF5500)

private val DarkColors = darkColorScheme(
    primary = Orange,
    background = Color(0xFF121212),
    surface = Color(0xFF121212),
)

private val LightColors = lightColorScheme(
    primary = Orange,
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFAFAFA),
)

@Composable
fun isAppDark(): Boolean = when (Prefs.theme) {
    "dark" -> true
    "light" -> false
    else -> isSystemInDarkTheme()
}

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        Prefs.init(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, Prefs.language))
    }

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_player", false)) {
            NowPlaying.openPlayerRequest = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.getBooleanExtra("open_player", false) == true) {
            NowPlaying.openPlayerRequest = true
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener(
                { controllerState.value = future.get() },
                MoreExecutors.directExecutor(),
            )
        }

        setContent {
            MaterialTheme(
                colorScheme = if (isAppDark()) DarkColors else LightColors,
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root(controllerState.value)
                }
            }
        }
    }

    override fun onDestroy() {
        controllerState.value = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }
}

@Composable
fun rememberArtworkColor(artworkUri: String?): Color {
    val context = LocalContext.current
    val dark = isAppDark()
    val fallback = MaterialTheme.colorScheme.surfaceVariant
    var color by remember { mutableStateOf(fallback) }

    LaunchedEffect(artworkUri, dark) {
        if (artworkUri == null) {
            color = fallback
            return@LaunchedEffect
        }
        val extracted = runCatching {
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .allowHardware(false)
                .size(128)
                .build()
            val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
            val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@runCatching null
            withContext(Dispatchers.Default) {
                val palette = Palette.from(bitmap).generate()
                val fb = fallback.toArgb()
                if (dark) {
                    palette.getDarkMutedColor(
                        palette.getMutedColor(palette.getDominantColor(fb))
                    )
                } else {
                    palette.getLightMutedColor(
                        palette.getMutedColor(palette.getDominantColor(fb))
                    )
                }
            }
        }.getOrNull()
        color = extracted?.let { Color(it) } ?: fallback
    }
    return color
}

@Composable
fun Root(controller: MediaController?) {
    var hasSession by remember { mutableStateOf(Api.sessionId != null) }

    if (hasSession) {
        MainScreen(controller, onSessionExpired = { hasSession = false })
    } else {
        LoginScreen(onDone = { hasSession = true })
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("SoundCloud", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.login_explain),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                status = null
                scope.launch {
                    try {
                        val login = Api.authLogin()
                        context.startActivity(Intent(Intent.ACTION_VIEW, login.url.toUri()))
                        status = context.getString(R.string.login_waiting)

                        val deadline = System.currentTimeMillis() + 5 * 60_000
                        while (System.currentTimeMillis() < deadline) {
                            delay(700)
                            val st = try {
                                Api.authLoginStatus(login.loginRequestId)
                            } catch (_: Exception) {
                                continue
                            }
                            when (st.status) {
                                "completed" -> {
                                    val sid = st.sessionId
                                    if (sid != null) {
                                        Api.storeSession(context, sid)
                                        onDone()
                                        return@launch
                                    }
                                }
                                "failed", "expired" -> {
                                    status = context.getString(R.string.login_failed) +
                                        (st.error?.let { ": $it" } ?: "")
                                    busy = false
                                    return@launch
                                }
                            }
                        }
                        status = context.getString(R.string.login_timeout)
                    } catch (e: Exception) {
                        status = context.getString(R.string.error_network) + "\n" + (e.message ?: "")
                    }
                    busy = false
                }
            },
        ) { Text(stringResource(R.string.login_button)) }

        Spacer(Modifier.height(16.dp))
        if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

enum class Tab { Search, Wave, Me }

@Composable
fun MainScreen(controller: MediaController?, onSessionExpired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Search) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    var noInternet by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            noInternet = !hasNetwork(context)
            delay(4000)
        }
    }

    LaunchedEffect(NowPlaying.openPlayerRequest) {
        if (NowPlaying.openPlayerRequest) {
            showPlayer = true
            NowPlaying.openPlayerRequest = false
        }
    }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Track>>(emptyList()) }
    var userSuggestions by remember { mutableStateOf<List<Artist>>(emptyList()) }
    var playlistResults by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var vibePreparing by remember { mutableStateOf(false) }
    var tiles by remember { mutableStateOf<List<Track>>(emptyList()) }

    var wave by remember { mutableStateOf<List<Track>>(emptyList()) }
    var waveCursor by remember { mutableStateOf("") }
    var waveLoading by remember { mutableStateOf(false) }

    var openArtist by remember { mutableStateOf<String?>(null) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(Unit) { Dislikes.seed() }

    fun handleError(e: Exception) {
        if (e is ApiHttpException && e.code == 401) {
            Api.storeSession(context, null)
            onSessionExpired()
        } else {
            error = e.message
        }
    }

    fun search(nextPage: Int) {
        val q = query.trim()
        if (q.isEmpty() || searchLoading) return
        suggestions = emptyList()
        scope.launch {
            searchLoading = true
            error = null
            try {
                val res = Api.searchTracks(q, nextPage)
                results = if (nextPage == 0) res.collection else (results + res.collection).distinctBy { it.urn }
                page = res.page
                hasMore = res.has_more
                searched = true
            } catch (e: Exception) {
                handleError(e)
            } finally {
                searchLoading = false
            }
        }
    }

    fun vibeSearch() {
        val q = query.trim()
        if (q.isEmpty() || searchLoading) return
        suggestions = emptyList()
        scope.launch {
            searchLoading = true
            error = null
            try {
                repeat(12) {
                    val res = Api.vibeSearch(q)
                    if (res.status == "preparing") {
                        vibePreparing = true
                        delay(2500)
                    } else {
                        results = res.items
                        hasMore = false
                        searched = true
                        vibePreparing = false
                        return@launch
                    }
                }
                vibePreparing = false
            } catch (e: Exception) {
                vibePreparing = false
                handleError(e)
            } finally {
                searchLoading = false
            }
        }
    }

    LaunchedEffect(query, searched) {
        val q = query.trim()
        if (q.length < 2 || searched) {
            suggestions = emptyList()
            userSuggestions = emptyList()
            playlistResults = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        runCatching { Api.searchTracks(q, 0, 6) }.onSuccess { suggestions = it.collection.distinctBy { t -> t.urn } }
        runCatching { Api.searchUsers(q, 0, 8) }.onSuccess { userSuggestions = it.collection.distinctBy { u -> u.urn }.take(4) }
        runCatching { Api.searchPlaylists(q, 0, 8) }.onSuccess { playlistResults = it.collection.distinctBy { p -> p.urn }.take(4) }
    }

    fun loadWave(more: Boolean) {
        if (waveLoading || Prefs.offline) return
        scope.launch {
            waveLoading = true
            error = null
            try {
                val (batch, cursor) = Api.waveTracks(if (more) waveCursor.ifEmpty { null } else null)
                wave = if (more) (wave + batch).distinctBy { it.urn } else batch
                waveCursor = cursor
            } catch (e: Exception) {
                handleError(e)
            } finally {
                waveLoading = false
            }
        }
    }

    fun refreshWave() {
        if (waveLoading || Prefs.offline) return
        scope.launch {
            waveLoading = true
            error = null
            try {
                val (batch, cursor) = Api.waveTracks(null)
                val fresh = batch.distinctBy { it.urn }
                wave = fresh
                waveCursor = cursor
                // Текущий трек не трогаем, а «следующее» в очереди подменяем новой волной.
                controller?.let { c ->
                    if (c.mediaItemCount > 0) {
                        val curUrn = c.currentMediaItem?.mediaId
                        val curIndex = c.currentMediaItemIndex
                        if (c.mediaItemCount > curIndex + 1) {
                            c.removeMediaItems(curIndex + 1, c.mediaItemCount)
                        }
                        c.addMediaItems(fresh.filter { it.urn != curUrn }.map { it.toMediaItem() })
                    }
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                waveLoading = false
            }
        }
    }

    LaunchedEffect(tab, Prefs.offline) {
        error = null
        if (tab == Tab.Wave && wave.isEmpty()) loadWave(false)
        if (tab == Tab.Search && tiles.isEmpty() && !Prefs.offline) {
            val picked = GENRES.shuffled().take(3)
            val all = mutableListOf<Track>()
            for (g in picked) {
                runCatching { Api.searchTracks(g, 0, 12) }.onSuccess {
                    all += it.collection.filter { t -> t.artwork_url != null }
                }
            }
            tiles = all.distinctBy { it.urn }.shuffled()
        }
    }

    fun play(list: List<Track>, track: Track) {
        controller?.let { c ->
            val index = list.indexOfFirst { it.urn == track.urn }
            c.setMediaItems(list.map { it.toMediaItem() }, index.coerceAtLeast(0), 0)
            c.prepare()
            c.play()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                PlayerBar(controller, onExpand = { showPlayer = true })
                if (!Prefs.offline) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == Tab.Search,
                            onClick = { tab = Tab.Search; openArtist = null; openPlaylist = null },
                            icon = { Icon(painterResource(R.drawable.ic_search), null) },
                            label = { Text(stringResource(R.string.tab_search)) },
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Wave,
                            onClick = { tab = Tab.Wave; openArtist = null; openPlaylist = null },
                            icon = { Icon(painterResource(R.drawable.ic_wave), null) },
                            label = { Text(stringResource(R.string.tab_wave)) },
                        )
                        NavigationBarItem(
                            selected = tab == Tab.Me,
                            onClick = { tab = Tab.Me; openArtist = null; openPlaylist = null },
                            icon = { Icon(painterResource(R.drawable.ic_user), null) },
                            label = { Text(stringResource(R.string.tab_me)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (noInternet && !Prefs.offline) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_no_wifi),
                        null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_internet),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (openArtist != null) {
                ArtistScreen(
                    urn = openArtist!!,
                    onBack = { openArtist = null },
                    onPlay = ::play,
                    onOpenPlaylist = { openPlaylist = it },
                    offline = Prefs.offline,
                )
            } else if (openPlaylist != null) {
                PlaylistScreen(
                    playlist = openPlaylist!!,
                    onBack = { openPlaylist = null },
                    onPlay = ::play,
                )
            } else if (Prefs.offline) {
                LibraryScreen(
                    play = ::play,
                    onSessionExpired = onSessionExpired,
                    onOpenArtist = { openArtist = it },
                    offline = true,
                )
            } else when (tab) {
                Tab.Search -> Box(Modifier.fillMaxSize()) {
                    val barPad = 76.dp
                    // Фоновый слой: плитки / результаты (скроллятся под плавающей строкой)
                    when {
                        searched || searchLoading || error != null -> TrackList(
                            tracks = results,
                            loading = searchLoading,
                            error = error,
                            emptyText = stringResource(R.string.search_empty),
                            canLoadMore = hasMore,
                            onLoadMore = { search(page + 1) },
                            onPlay = { play(results, it) },
                            topPadding = barPad,
                        )
                        query.isBlank() -> TileGrid(tiles, topPadding = barPad) { play(tiles, it) }
                    }

                    // Плавающая строка поиска + подсказки поверх
                    Column(Modifier.fillMaxWidth()) {
                    TextField(
                        value = query,
                        onValueChange = {
                            query = it
                            searched = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        placeholder = {
                            Text(
                                stringResource(R.string.search_hint),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { search(0) }),
                        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = {
                                query = ""
                                searched = false
                                results = emptyList()
                                hasMore = false
                                suggestions = emptyList()
                            }) {
                                Icon(painterResource(R.drawable.ic_clear), null)
                            }
                        },
                    )

                    if (vibePreparing) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.vibe_preparing),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (suggestions.isNotEmpty() && !searched && query.trim().length >= 2) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { vibeSearch() }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_wave),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.vibe_search) + ": «${query.trim()}»",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            userSuggestions.forEach { u ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openArtist = u.urn }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = Api.artworkUrl(u.avatar_url, "t120x120"),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        u.username,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.artist_label),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            playlistResults.forEach { p ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { openPlaylist = p }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = Api.artworkUrl(p.artwork_url, "t120x120"),
                                        contentDescription = null,
                                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                                        contentScale = ContentScale.Crop,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        p.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.playlist_label),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            suggestions.take(4).forEach { s ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            query = s.title
                                            search(0)
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_search),
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        s.title + (s.user?.username?.let { " — $it" } ?: ""),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }

                    }
                }
                Tab.Wave -> WaveFeed(
                    controller = controller,
                    tracks = wave,
                    loading = waveLoading,
                    error = error,
                    canLoadMore = waveCursor.isNotEmpty(),
                    onLoadMore = { loadWave(true) },
                    onRefresh = { refreshWave() },
                    onPlay = { play(wave, it) },
                )
                Tab.Me -> LibraryScreen(
                    play = ::play,
                    onSessionExpired = onSessionExpired,
                    onOpenArtist = { openArtist = it },
                )
            }
        }
    }

    if (showPlayer && controller != null) {
        NowPlayingScreen(
            controller,
            onClose = { showPlayer = false },
            onOpenArtist = { urn ->
                showPlayer = false
                openArtist = urn
            },
        )
    }
}

@Composable
fun TileGrid(tiles: List<Track>, topPadding: Dp = 0.dp, onPlay: (Track) -> Unit) {
    if (tiles.isEmpty()) {
        Row(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
    ) {
        itemsIndexed(tiles) { index, track ->
            val ratio = when ((track.urn.hashCode() + index) % 5) {
                0 -> 1.6f
                1 -> 0.7f
                2 -> 1.15f
                3 -> 0.85f
                else -> 1f
            }
            AsyncImage(
                model = Api.artworkUrl(track.artwork_url, "t250x250"),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPlay(track) },
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
fun TrackList(
    tracks: List<Track>,
    loading: Boolean,
    error: String?,
    emptyText: String,
    canLoadMore: Boolean,
    onLoadMore: () -> Unit,
    onPlay: (Track) -> Unit,
    dimUndownloaded: Boolean = false,
    topPadding: Dp = 0.dp,
) {
    when {
        error != null -> Text(
            stringResource(R.string.error_network) + "\n" + error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = topPadding).padding(16.dp),
        )
        loading && tracks.isEmpty() -> Row(
            Modifier.fillMaxWidth().padding(top = topPadding).padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        tracks.isEmpty() -> Text(
            emptyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = topPadding).padding(16.dp),
        )
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = topPadding),
    ) {
        items(tracks) { track ->
            val unavailable = dimUndownloaded && !Downloads.isDownloaded(track.urn)
            TrackRow(
                track = track,
                onClick = { onPlay(track) },
                dimmed = unavailable,
            )
        }
        if (canLoadMore && tracks.isNotEmpty()) {
            item {
                Button(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) { Text(stringResource(R.string.load_more)) }
            }
        }
    }
}

@Composable
fun TrackRow(track: Track, onClick: () -> Unit, dimmed: Boolean = false) {
    val scope = rememberCoroutineScope()
    val liked = Likes.isLiked(track.urn)
    val isCurrent = NowPlaying.urn == track.urn
    val accent = MaterialTheme.colorScheme.primary
    val rowAlpha = if (dimmed) 0.4f else 1f

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent)
            .then(if (dimmed) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .graphicsLayer { alpha = rowAlpha },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            AsyncImage(
                model = Api.artworkUrl(track.artwork_url, "t120x120"),
                contentDescription = null,
                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            )
            if (dimmed) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_download),
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            if (isCurrent) {
                Box(
                    Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    EqualizerBars(playing = NowPlaying.isPlaying, color = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                track.user?.username ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            formatDuration(track.duration),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        IconButton(onClick = { scope.launch { Likes.toggle(track) } }, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(if (liked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
                null,
                tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun EqualizerBars(playing: Boolean, color: Color) {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0 until 3).map { i ->
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 340 + i * 120, easing = LinearEasing),
                RepeatMode.Reverse,
            ),
            label = "eqBar$i",
        )
    }
    Row(
        Modifier.size(20.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        heights.forEach { h ->
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(if (playing) h.value else 0.3f)
                    .background(color, RoundedCornerShape(1.dp)),
            )
        }
    }
}

fun Track.toMediaItem(): MediaItem {
    val local = if (Downloads.isDownloaded(urn)) Downloads.fileFor(urn) else null
    return MediaItem.Builder()
        .setMediaId(urn)
        .setUri(local?.toUri() ?: Api.streamUrl(urn).toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(user?.username)
                .setArtworkUri(Api.artworkUrl(artwork_url)?.toUri())
                .setExtras(
                    Bundle().apply {
                        putString("artist_urn", user?.urn)
                        putString("artwork_url", artwork_url)
                        putString("waveform_url", waveform_url)
                        putLong("duration", duration)
                    }
                )
                .build()
        )
        .build()
}

fun MediaItem.toTrackOrNull(): Track? {
    if (mediaId.isEmpty()) return null
    val md = mediaMetadata
    val title = md.title?.toString() ?: return null
    return Track(
        urn = mediaId,
        title = title,
        duration = md.extras?.getLong("duration") ?: 0L,
        artwork_url = md.extras?.getString("artwork_url"),
        waveform_url = md.extras?.getString("waveform_url"),
        user = TrackUser(
            urn = md.extras?.getString("artist_urn"),
            username = md.artist?.toString() ?: "",
        ),
    )
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

fun hasNetwork(context: android.content.Context): Boolean = NetMonitor.hasTransport(context)
