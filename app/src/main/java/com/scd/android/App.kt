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
            .crossfade(true)
            .build()
}
