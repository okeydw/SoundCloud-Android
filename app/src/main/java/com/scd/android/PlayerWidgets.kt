package com.scd.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.os.Looper
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.io.File
import java.util.concurrent.Executors

object PlayerWidgets {
    const val ACTION_TOGGLE = "com.scd.android.widget.TOGGLE"
    const val ACTION_NEXT = "com.scd.android.widget.NEXT"
    const val ACTION_PREV = "com.scd.android.widget.PREV"

    private const val PREFS = "player_widget"
    private const val ART_FILE = "widget_art.png"
    private const val ART_KEY = "cached_art_uri"

    data class State(
        val title: String = "",
        val artist: String = "",
        val playing: Boolean = false,
        val art: String? = null,
    ) {
        val hasTrack: Boolean get() = title.isNotBlank()
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "widget").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val loader by lazy { ScBitmapLoader(Api.http) }

    @Volatile private var last: State? = null
    @Volatile private var art: Bitmap? = null
    @Volatile private var artKey: String? = null
    @Volatile private var loadingArt: String? = null
    private var surfaceArt: Bitmap? = null
    private var surfaceAccent: Int? = null
    private var surfaces: WidgetArtwork.Surfaces? = null
    private var appContext: Context? = null

    private val providers = listOf(PlayerWidgetSmall::class.java, PlayerWidgetLarge::class.java)

    private fun ids(context: Context): Map<Class<*>, IntArray> {
        val mgr = AppWidgetManager.getInstance(context)
        return providers.associateWith { mgr.getAppWidgetIds(ComponentName(context, it)) }.filterValues { it.isNotEmpty() }
    }

    fun publish(context: Context, player: Player) {
        val app = context.applicationContext
        appContext = app
        val md = player.currentMediaItem?.mediaMetadata
        val playing = player.isPlaying || (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
        val state = State(
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            playing = playing,
            art = md?.artworkUri?.toString(),
        )
        if (state == last) return
        last = state
        worker.execute { save(app, state) }
        if (ids(app).isEmpty()) return
        render(app, state)
        if (state.art != artKey) loadArt(app, state)
    }

    fun refreshTheme() {
        appContext?.let { context -> main.post { last?.let { render(context, it) } } }
    }

    fun refresh(context: Context) {
        val app = context.applicationContext
        appContext = app
        worker.execute {
            val restored = restore(app)
            val cachedKey = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ART_KEY, null)
            val cached = if (art == null && cachedKey != null && cachedKey == restored.art) {
                runCatching { BitmapFactory.decodeFile(File(app.cacheDir, ART_FILE).path) }.getOrNull()
            } else null
            main.post {
                val current = last ?: restored.also { last = it }
                if (art == null && cached != null && current.art == cachedKey) {
                    art = cached
                    artKey = cachedKey
                }
                render(app, current)
                if (current.art != artKey) loadArt(app, current)
            }
        }
    }

    private fun loadArt(context: Context, state: State) {
        val uri = state.art
        if (uri == null) {
            loadingArt = null
            artKey = null
            art = null
            return
        }
        if (uri == loadingArt) return
        loadingArt = uri
        worker.execute {
            val bmp = runCatching {
                loader.loadBitmap(android.net.Uri.parse(uri)).get()?.let { WidgetArtwork.crop(it) }
            }.getOrNull()
            if (last?.art != uri) {
                if (loadingArt == uri) loadingArt = null
                return@execute
            }
            if (bmp != null) {
                val saved = runCatching {
                    File(context.cacheDir, ART_FILE).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.getOrDefault(false)
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(ART_KEY, if (saved) uri else null).apply()
            }
            main.post {
                if (loadingArt == uri) loadingArt = null
                if (last?.art != uri) return@post
                art = bmp
                artKey = if (bmp != null) uri else null
                last?.let { render(context, it) }
            }
        }
    }

    private fun render(context: Context, state: State) {
        val mgr = AppWidgetManager.getInstance(context)
        val widgets = ids(context)
        if (widgets.isEmpty()) return
        val bmp = art?.takeIf { state.hasTrack && state.art == artKey && !it.isRecycled }
        if (surfaces == null || surfaceArt !== bmp || surfaceAccent != Prefs.accent) {
            surfaces = WidgetArtwork.surfaces(bmp, Prefs.accent)
            surfaceArt = bmp
            surfaceAccent = Prefs.accent
        }
        for ((cls, widgetIds) in widgets) {
            val large = cls == PlayerWidgetLarge::class.java
            val layout = if (large) R.layout.widget_large else R.layout.widget_small
            val compact = views(context, layout, cls, state, bmp, true)
            val roomy = views(context, layout, cls, state, bmp, false)
            if (Build.VERSION.SDK_INT >= 31) {
                val sizes = linkedMapOf(
                    SizeF(if (large) 250f else 110f, 110f) to compact,
                    SizeF(if (large) 300f else 150f, if (large) 156f else 144f) to roomy,
                )
                mgr.updateAppWidget(widgetIds, RemoteViews(sizes))
            } else {
                for (id in widgetIds) {
                    val options = mgr.getAppWidgetOptions(id)
                    val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val fits = width >= (if (large) 300 else 150) && height >= (if (large) 156 else 144)
                    mgr.updateAppWidget(id, if (fits) roomy else compact)
                }
            }
        }
    }

    private fun views(context: Context, layout: Int, cls: Class<*>, state: State, bmp: Bitmap?, compact: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        val large = layout == R.layout.widget_large
        val fontScale = context.resources.configuration.fontScale
        val padding = ((if (compact) 10 else if (large) 16 else 14) * context.resources.displayMetrics.density).toInt()
        v.setViewPadding(R.id.widget_content, padding, padding, padding, padding)
        v.setTextViewText(R.id.widget_title, if (state.hasTrack) state.title else context.getString(R.string.widget_idle))
        v.setTextViewText(R.id.widget_artist, if (state.hasTrack) state.artist else context.getString(R.string.widget_open))
        v.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_SP,
            if (compact) { if (large) 14f else 13f } else if (large) 19f else 16f)
        v.setTextViewTextSize(R.id.widget_artist, TypedValue.COMPLEX_UNIT_SP, if (compact) 11f else if (large) 13f else 12f)
        v.setInt(R.id.widget_title, "setMaxLines", if (large || compact || fontScale > 1.15f) 1 else 2)
        val hideArtist = fontScale > if (compact) 1.25f else if (large) 1.75f else 1.5f
        v.setViewVisibility(R.id.widget_artist, if (hideArtist) View.GONE else View.VISIBLE)
        if (large) {
            v.setViewVisibility(R.id.widget_brand, if (compact || fontScale > 1.15f) View.GONE else View.VISIBLE)
            v.setViewPadding(R.id.widget_details, 0, if (compact || fontScale > 1.15f) 0 else (6 * context.resources.displayMetrics.density).toInt(), 0, 0)
            v.setImageViewBitmap(R.id.widget_backdrop, surfaces!!.wide)
            if (bmp != null) v.setImageViewBitmap(R.id.widget_art, bmp)
            else v.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)
        } else {
            v.setImageViewBitmap(R.id.widget_art, surfaces!!.tile)
        }
        v.setImageViewResource(R.id.widget_play_icon, if (state.playing) R.drawable.ic_pause else R.drawable.ic_play)
        v.setContentDescription(R.id.widget_play, context.getString(if (state.playing) R.string.cd_pause else R.string.cd_play))
        val open = openApp(context)
        v.setOnClickPendingIntent(R.id.widget_root, open)
        v.setOnClickPendingIntent(R.id.widget_art, open)
        v.setOnClickPendingIntent(R.id.widget_play, if (state.hasTrack) command(context, cls, ACTION_TOGGLE) else open)
        if (large) {
            v.setOnClickPendingIntent(R.id.widget_next, if (state.hasTrack) command(context, cls, ACTION_NEXT) else open)
            v.setOnClickPendingIntent(R.id.widget_prev, if (state.hasTrack) command(context, cls, ACTION_PREV) else open)
            v.setInt(R.id.widget_next, "setImageAlpha", if (state.hasTrack) 255 else 102)
            v.setInt(R.id.widget_prev, "setImageAlpha", if (state.hasTrack) 255 else 102)
        }
        return v
    }

    private fun openApp(context: Context): PendingIntent {
        val intent = (context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent())
            .putExtra("open_player", true)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, 9100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun command(context: Context, cls: Class<*>, action: String): PendingIntent {
        val intent = Intent(context, cls).setAction(action)
        val code = when (action) {
            ACTION_TOGGLE -> 1
            ACTION_NEXT -> 2
            else -> 3
        } + if (cls == PlayerWidgetLarge::class.java) 10 else 0
        return PendingIntent.getBroadcast(context, 9100 + code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun handle(context: Context, action: String, done: () -> Unit) {
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull()
            if (controller != null) {
                runCatching {
                    when (action) {
                        ACTION_TOGGLE -> if (controller.isPlaying) controller.pause() else if (controller.mediaItemCount > 0) {
                            controller.prepare()
                            controller.play()
                        }
                        ACTION_NEXT -> controller.seekToNext()
                        ACTION_PREV -> controller.seekToPrevious()
                    }
                }.onFailure { Logs.add("widget", "command failed: ${it.javaClass.simpleName}") }
                main.postDelayed({ controller.release() }, 1_500L)
            }
            done()
        }, ContextCompat.getMainExecutor(app))
    }

    private fun save(context: Context, s: State) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("title", s.title)
            .putString("artist", s.artist)
            .putString("art", s.art)
            .apply()
    }

    private fun restore(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return State(p.getString("title", "").orEmpty(), p.getString("artist", "").orEmpty(), false, p.getString("art", null))
    }
}

abstract class PlayerWidgetBase : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgets.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        PlayerWidgets.refresh(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            PlayerWidgets.ACTION_TOGGLE, PlayerWidgets.ACTION_NEXT, PlayerWidgets.ACTION_PREV -> {
                val pending = goAsync()
                PlayerWidgets.handle(context, action) { pending.finish() }
            }
            else -> super.onReceive(context, intent)
        }
    }
}

class PlayerWidgetSmall : PlayerWidgetBase()

class PlayerWidgetLarge : PlayerWidgetBase()
