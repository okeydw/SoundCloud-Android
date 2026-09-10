package com.scd.android

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request

class App : Application(), ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        Prefs.init(base)
        super.attachBaseContext(LocaleHelper.wrap(base, Prefs.language))
    }

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Endpoints.init(this)
        Api.initHttp(this)
        Api.loadSession(this)
        Downloads.init(this)
        FeedCache.init(this)
        LikedArtists.init(this)
        Likes.init(this)
        scope.launch { runCatching { Endpoints.probeAll() } }
        installCrashLog()
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
                    .addInterceptor { chain ->
                        val req = chain.request()
                        if (req.url.host.endsWith("sndcdn.com")) {
                            val (name, value) = Api.imageProxyTarget(req.url.toString())
                            chain.proceed(
                                Request.Builder()
                                    .url("${Api.IMAGES_BASE}/?t=${android.net.Uri.encode(value)}")
                                    .header(name, value)
                                    .build()
                            )
                        } else {
                            chain.proceed(req)
                        }
                    }
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
