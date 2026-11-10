package com.scd.android

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal class AudioRoute(
    private val routes: (HttpUrl) -> List<HttpUrl> = {
        plan(it, Endpoints.relayNodes, Endpoints.activeNode)
    },
    private val report: (String) -> Unit = { Logs.add("route", it) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return fetch(chain, request)
        repeat(11) { redirects ->
            val response = fetch(chain, request)
            val location = response.header("Location")
            if (response.code !in REDIRECTS || location == null) return response
            val target = response.request.url.resolve(location) ?: return response
            if (target.scheme != "https" && response.request.url.scheme == "https") {
                response.close()
                throw IOException("audio redirect downgraded HTTPS")
            }
            response.close()
            if (redirects == 10) throw IOException("too many audio redirects")
            request = redirected(request, target)
        }
        throw IOException("too many audio redirects")
    }

    private fun fetch(chain: Interceptor.Chain, request: Request): Response {
        val hops = routes(request.url).ifEmpty { listOf(request.url) }
        val readOnly = request.method == "GET" || request.method == "HEAD"
        var failure: IOException? = null
        for ((index, url) in hops.withIndex()) {
            if (chain.call().isCanceled()) throw IOException("Canceled")
            val response = try {
                chain.proceed(request.newBuilder().url(url).removeHeader("Host").build())
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                if (!readOnly && !Api.neverReachedServer(e)) throw e
                report("${e.javaClass.simpleName} @${url.host}")
                failure = e
                continue
            }
            val starGate = service(url) == "api-star" && response.code == 403
            if (index < hops.lastIndex && (starGate ||
                (readOnly && response.code in 500..599 && ApiContract.retryHost(response.code, response.peekBody(16_384).string())))) {
                report("HTTP ${response.code} @${url.host}")
                response.close()
                continue
            }
            if (url.host != request.url.host) report("${request.url.host} → ${url.host}")
            return response
        }
        throw failure ?: IOException("no audio route")
    }

    companion object {
        private val REDIRECTS = setOf(300, 301, 302, 303, 307, 308)
        private val SERVICES = setOf("api", "api-star", "stream", "stream-star", "storage", "storage-star", "pay")

        fun builder(base: OkHttpClient): OkHttpClient.Builder = base.newBuilder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .apply { if (base.interceptors.none { it is AudioRoute }) addInterceptor(AudioRoute()) }

        internal fun service(url: HttpUrl): String? {
            if (url.scheme != "https" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) return null
            val name = listOf("scnative.dev", "scnative.space")
                .firstNotNullOfOrNull { zone -> url.host.removeSuffix(".$zone").takeIf { it != url.host } }
                ?: return null
            val parts = name.split('.')
            if (parts.size != 1 && !(parts.size == 3 && parts[1].matches(Regex("r\\d{1,2}")) && parts[2] == "relay")) return null
            return parts.first().takeIf { it in SERVICES }
        }

        internal fun plan(url: HttpUrl, relays: List<String>, active: String): List<HttpUrl> {
            val service = service(url) ?: return listOf(url)
            val nodes = (listOf(active) + relays).distinct().filter { it.matches(Regex("r\\d{1,2}")) }
            val services = if (service == "api-star") listOf(service, "api") else listOf(service)
            return services.flatMap { name -> nodes.map { url.newBuilder().host("$name.$it.relay.scnative.dev").build() } }
                .ifEmpty { listOf(url) }
        }

        internal fun redirected(request: Request, target: HttpUrl): Request {
            val own = service(request.url) != null && service(target) != null
            val url = if (own) target else target.newBuilder().removeAllQueryParameters("session_id").build()
            return request.newBuilder().url(url).apply {
                if (!own) removeHeader("x-session-id")
                if (request.url.host != target.host || request.url.port != target.port || request.url.scheme != target.scheme) {
                    removeHeader("Authorization")
                    removeHeader("Cookie")
                }
            }.build()
        }
    }
}
