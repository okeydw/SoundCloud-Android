package com.scd.android

import android.app.Application
import android.content.Context
import android.content.res.Resources
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application(), ImageLoaderFactory {

    private var localized: Resources? = null

    override fun attachBaseContext(base: Context) {
        Prefs.init(base)
        super.attachBaseContext(base)
        localized = LocaleHelper.wrap(base, Prefs.language).takeIf { it !== base }?.resources
    }

    override fun getResources(): Resources = localized ?: super.getResources()

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Endpoints.init(this)
        Api.initHttp(this)
        NetMonitor.watch(this)
        Api.loadSession(this)
        SessionGuard.init()
        DurationCache.init(this)
        Downloads.init(this)
        FeedCache.init(this)
        LikedArtists.init(this)
        Likes.init(this)
        Dislikes.init(this)
        BlockedArtists.init(this)
        Auras.init(this)
        LocalPlaylists.init(this)
        PlaylistSnapshots.init(this)
        ExternalAccounts.init(this)
        PlaylistCovers.init(this)
        Recommendations.init(this)
        DownloadFailures.init(this)
        LikesAutoCache.init(this)
        PlaylistPrefetch.init(this)
        ScAnon.init(this)
        scope.launch { kotlinx.coroutines.delay(3_000); ScAnon.warmUp() }
        scope.launch { runCatching { Endpoints.probeAll() } }
        installCrashLog()
        scope.launch { runCatching { NoteArtwork.init(this@App) } }
        scope.launch { runCatching { MediaCache.dropLegacy(this@App) } }
    }

    private fun installCrashLog() {
        val sp = getSharedPreferences("crash", MODE_PRIVATE)
        sp.getString("last", null)?.let {
            Logs.add("crash", it)
            sp.edit().remove("last").apply()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val text = buildString {
                    append(error.javaClass.name).append(": ").append(error.message)
                    error.stackTrace.take(6).forEach { append("\n  at ").append(it) }
                    error.cause?.let { c ->
                        append("\ncaused by ").append(c.javaClass.name).append(": ").append(c.message)
                        c.stackTrace.take(4).forEach { append("\n  at ").append(it) }
                    }
                }
                sp.edit().putString("last", text).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                Api.http.newBuilder()
                    .cache(null)
                    .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                    .addInterceptor { chain -> ImageRoute.proceed(chain) }
                    .build()
            }
            .diskCache {
                val limit = Prefs.cacheLimit
                val budget = if (limit == CacheLimits.UNLIMITED) {
                    2L * 1024 * 1024 * 1024
                } else {
                    (limit / 10).coerceIn(128L * 1024 * 1024, 2L * 1024 * 1024 * 1024)
                }
                coil.disk.DiskCache.Builder()
                    .directory(java.io.File(cacheDir, "images"))
                    .maxSizeBytes(budget)
                    .build()
                    .also { Images.disk = it }
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
