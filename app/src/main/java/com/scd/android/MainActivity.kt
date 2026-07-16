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
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
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

class MainActivity : ComponentActivity() {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val controllerState = mutableStateOf<MediaController?>(null)

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
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
    val dark = isSystemInDarkTheme()
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

enum class Tab { Search, Wave, History }

@Composable
fun MainScreen(controller: MediaController?, onSessionExpired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(Tab.Search) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Track>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var searchLoading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }

    var wave by remember { mutableStateOf<List<Track>>(emptyList()) }
    var waveCursor by remember { mutableStateOf("") }
    var waveLoading by remember { mutableStateOf(false) }

    var history by remember { mutableStateOf<List<Track>>(emptyList()) }
    var historyTotal by remember { mutableStateOf(0) }
    var historyLoading by remember { mutableStateOf(false) }

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
        scope.launch {
            searchLoading = true
            error = null
            try {
                val res = Api.searchTracks(q, nextPage)
                results = if (nextPage == 0) res.collection else results + res.collection
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

    fun loadWave(more: Boolean) {
        if (waveLoading) return
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

    fun loadHistory(offset: Int) {
        if (historyLoading) return
        scope.launch {
            historyLoading = true
            error = null
            try {
                val res = Api.history(offset)
                val batch = res.collection.map { it.toTrack() }
                history = if (offset == 0) batch else history + batch
                historyTotal = res.total
            } catch (e: Exception) {
                handleError(e)
            } finally {
                historyLoading = false
            }
        }
    }

    LaunchedEffect(tab) {
        error = null
        when (tab) {
            Tab.Wave -> if (wave.isEmpty()) loadWave(false)
            Tab.History -> loadHistory(0)
            Tab.Search -> {}
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
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Search,
                        onClick = { tab = Tab.Search },
                        icon = { Icon(painterResource(R.drawable.ic_search), null) },
                        label = { Text(stringResource(R.string.tab_search)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Wave,
                        onClick = { tab = Tab.Wave },
                        icon = { Icon(painterResource(R.drawable.ic_wave), null) },
                        label = { Text(stringResource(R.string.tab_wave)) },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.History,
                        onClick = { tab = Tab.History },
                        icon = { Icon(painterResource(R.drawable.ic_history), null) },
                        label = { Text(stringResource(R.string.tab_history)) },
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            when (tab) {
                Tab.Search -> {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
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
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
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
                            }) {
                                Icon(painterResource(R.drawable.ic_clear), null)
                            }
                        },
                    )
                    TrackList(
                        tracks = results,
                        loading = searchLoading,
                        error = error,
                        emptyText = stringResource(if (searched) R.string.search_empty else R.string.search_idle),
                        canLoadMore = hasMore,
                        onLoadMore = { search(page + 1) },
                        onPlay = { play(results, it) },
                    )
                }
                Tab.Wave -> TrackList(
                    tracks = wave,
                    loading = waveLoading,
                    error = error,
                    emptyText = stringResource(R.string.wave_empty),
                    canLoadMore = wave.isNotEmpty() && waveCursor.isNotEmpty(),
                    onLoadMore = { loadWave(true) },
                    onPlay = { play(wave, it) },
                )
                Tab.History -> TrackList(
                    tracks = history,
                    loading = historyLoading,
                    error = error,
                    emptyText = stringResource(R.string.history_empty),
                    canLoadMore = history.size < historyTotal,
                    onLoadMore = { loadHistory(history.size) },
                    onPlay = { play(history, it) },
                )
            }
        }
    }

    if (showPlayer && controller != null) {
        NowPlayingScreen(controller, onClose = { showPlayer = false })
    }
}

@Composable
fun NowPlayingScreen(controller: MediaController, onClose: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableStateOf(0f) }

    DisposableEffect(controller) {
        fun sync() {
            val md = controller.currentMediaItem?.mediaMetadata
            title = md?.title?.toString() ?: ""
            artist = md?.artist?.toString() ?: ""
            artworkUri = md?.artworkUri?.toString()
            isPlaying = controller.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            if (!dragging) {
                position = controller.currentPosition.coerceAtLeast(0L)
                duration = controller.duration.coerceAtLeast(0L)
            }
            delay(400)
        }
    }

    val background = MaterialTheme.colorScheme.background
    val tintTarget = rememberArtworkColor(artworkUri)
    val tint by animateColorAsState(tintTarget, tween(800), label = "playerTint")
    val topColor = tint.copy(alpha = 0.65f).compositeOver(background)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to topColor,
                            0.75f to background,
                            1f to background,
                        )
                    )
                    .padding(horizontal = 24.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(R.drawable.ic_chevron_down), null)
                    }
                }

                Spacer(Modifier.weight(1f))

                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )

                Spacer(Modifier.height(32.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    artist,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                Slider(
                    value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                    onValueChange = {
                        dragging = true
                        dragValue = it
                    },
                    onValueChangeFinished = {
                        if (duration > 0) controller.seekTo((dragValue * duration).toLong())
                        dragging = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatDuration(if (dragging && duration > 0) (dragValue * duration).toLong() else position),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        formatDuration(duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { controller.seekToPrevious() }, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_prev), null, modifier = Modifier.size(36.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { if (controller.isPlaying) controller.pause() else controller.play() },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                null,
                                tint = MaterialTheme.colorScheme.background,
                                modifier = Modifier.size(40.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = { controller.seekToNext() }, modifier = Modifier.size(56.dp)) {
                        Icon(painterResource(R.drawable.ic_next), null, modifier = Modifier.size(36.dp))
                    }
                }

                Spacer(Modifier.weight(1.4f))
            }
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
) {
    when {
        error != null -> Text(
            stringResource(R.string.error_network) + "\n" + error,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
        loading && tracks.isEmpty() -> Row(
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }
        tracks.isEmpty() -> Text(
            emptyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }

    LazyColumn(Modifier.fillMaxSize()) {
        items(tracks) { track ->
            TrackRow(track = track, onClick = { onPlay(track) })
        }
        if (canLoadMore) {
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
fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = Api.artworkUrl(track.artwork_url, "t120x120"),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
            )
            Text(
                track.user?.username ?: "",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDuration(track.duration),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
fun PlayerBar(controller: MediaController?, onExpand: () -> Unit) {
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf("") }
    var artworkUri by remember { mutableStateOf<String?>(null) }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        fun sync() {
            val md = controller.currentMediaItem?.mediaMetadata
            title = md?.title?.toString()
            artist = md?.artist?.toString() ?: ""
            artworkUri = md?.artworkUri?.toString()
            isPlaying = controller.isPlaying
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        controller.addListener(listener)
        sync()
        onDispose { controller.removeListener(listener) }
    }

    val t = title ?: return

    val surface = MaterialTheme.colorScheme.surfaceVariant
    val tintTarget = rememberArtworkColor(artworkUri)
    val barColor by animateColorAsState(
        tintTarget.copy(alpha = 0.45f).compositeOver(surface),
        tween(800),
        label = "barTint",
    )

    Surface(color = barColor, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                if (artist.isNotEmpty()) {
                    Text(
                        artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            IconButton(onClick = { controller?.seekToPrevious() }) {
                Icon(painterResource(R.drawable.ic_prev), null)
            }
            IconButton(onClick = {
                controller?.let { if (it.isPlaying) it.pause() else it.play() }
            }) {
                Icon(painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play), null)
            }
            IconButton(onClick = { controller?.seekToNext() }) {
                Icon(painterResource(R.drawable.ic_next), null)
            }
        }
    }
}

fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(urn)
        .setUri(Api.streamUrl(urn))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(user?.username)
                .setArtworkUri(Api.artworkUrl(artwork_url)?.toUri())
                .setExtras(
                    Bundle().apply {
                        putString("artist_urn", user?.urn)
                        putString("artwork_url", artwork_url)
                        putLong("duration", duration)
                    }
                )
                .build()
        )
        .build()

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
