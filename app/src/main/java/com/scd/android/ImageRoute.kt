package com.scd.android

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

object ImageRoute {
    private const val COOL_MS = 60_000L
    private const val MAX_PROXIES = 3
    private val down = ConcurrentHashMap<String, Long>()

    @Volatile
    private var reported = 0

    private fun report(what: String) {
        if (reported++ < 20) Logs.add("img", what)
    }

    private fun base(url: String) = url.substringBefore("/?t=")

    fun candidates(target: String, encoded: String): List<String> {
        val now = System.currentTimeMillis()
        val hosts = (listOf(Endpoints.imageBase) + Endpoints.imageHosts).distinct()
        val (live, cooling) = hosts.partition { now - (down[it] ?: 0L) >= COOL_MS }
        return (live.take(MAX_PROXIES) + cooling.take(1)).map { "$it/?t=$encoded" } + target
    }

    private fun failover(code: Int) = code == 403 || code == 408 || code == 429 || code >= 500

    fun proceed(chain: Interceptor.Chain): Response {
        val req = chain.request()
        if (!req.url.host.endsWith("sndcdn.com")) return chain.proceed(req)
        val target = req.url.toString()
        val (name, value) = Api.imageProxyTarget(target)
        val urls = candidates(target, android.net.Uri.encode(value))
        var last: IOException? = null
        for ((i, url) in urls.withIndex()) {
            val direct = url == target
            val builder = Request.Builder().url(url)
            if (!direct) builder.header(name, value)
            val res = try {
                chain.proceed(builder.build())
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                last = e
                if (!direct) {
                    down[base(url)] = System.currentTimeMillis()
                    report("proxy ${e.javaClass.simpleName} @${base(url).substringAfter("://")}")
                }
                continue
            }
            if (res.isSuccessful || i == urls.lastIndex || !failover(res.code)) {
                if (res.isSuccessful && !direct) down.remove(base(url))
                if (res.isSuccessful && i > 0) report("served by ${if (direct) "sndcdn" else base(url).substringAfter("://")}")
                return res
            }
            if (!direct) {
                down[base(url)] = System.currentTimeMillis()
                report("proxy HTTP ${res.code} @${base(url).substringAfter("://")}")
            }
            res.close()
        }
        throw last ?: IOException("image unavailable")
    }
}
