package com.scd.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.CacheControl
import okhttp3.Interceptor
import java.util.concurrent.TimeUnit

object NetMonitor {

    fun watch(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) = Health.kick()

                override fun onLost(network: android.net.Network) = Health.kick()
            })
        }
    }

    fun hasTransport(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        return cm.isActiveNetworkMetered
    }

    fun isOnline(context: Context): Boolean {
        if (Prefs.offline) return false
        return hasTransport(context)
    }

    const val PROBE_HEADER = "x-scd-probe"

    fun offlineInterceptor(context: Context) = Interceptor { chain ->
        var request = chain.request()
        if (request.header(PROBE_HEADER) != null) {
            return@Interceptor chain.proceed(request.newBuilder().removeHeader(PROBE_HEADER).build())
        }
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
