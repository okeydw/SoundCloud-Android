package com.scd.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.Request

class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        Api.loadSession(this)
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
                                    .url(Api.IMAGES_BASE)
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
