package com.scd.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.CacheControl
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

object NetMonitor {

    fun hasTransport(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        val hasChannel = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return hasChannel && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isOnline(context: Context): Boolean {
        if (Prefs.offline) return false
        return hasTransport(context)
    }

    fun offlineInterceptor(context: Context) = Interceptor { chain ->
        var request = chain.request()
        if (!isOnline(context)) {
            request = request.newBuilder()
                .cacheControl(
                    CacheControl.Builder()
                        .onlyIfCached()
                        .maxStale(30, TimeUnit.DAYS)
                        .build()
                )
                .build()
        }
        chain.proceed(request)
    }
}
