package com.scd.android

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.Response
import okio.GzipSource
import okio.buffer

internal object ApiContract {
    private val json = Json { ignoreUnknownKeys = true }

    fun objectOf(body: String): JsonObject? = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull()
    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
    fun errorCode(body: String): String? = objectOf(body)?.text("code")
    fun sessionAuthenticated(body: String): Boolean? =
        (objectOf(body)?.get("authenticated") as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
    fun reconnectRequired(body: String): Boolean {
        val root = objectOf(body) ?: return false
        return root.text("code") in setOf("soundcloud_reauthorization_required", "soundcloud_not_connected") ||
            root.text("state") in setOf("reauthorization_required", "not_connected") ||
            (root["sync"] as? JsonObject)?.text("errorCode") == "auth_required"
    }

    fun retryHost(status: Int, body: String): Boolean {
        val code = errorCode(body)
        return status >= 500 && code?.endsWith("_refresh_pending") != true && code?.startsWith("soundcloud_") != true
    }

    fun pendingCollection(body: String): Boolean {
        val root = objectOf(body) ?: return false
        if (root["collection"] !is JsonArray) return false
        val sync = root["sync"] as? JsonObject ?: return false
        return !json.decodeFromJsonElement(CollectionSync.serializer(), sync).snapshotReady
    }

    fun trackId(raw: String): String = raw.removePrefix("soundcloud:tracks:")
    fun collectionFailure(body: String): ApiHttpException? {
        if (!pendingCollection(body)) return null
        val sync = objectOf(body)?.get("sync") as? JsonObject
        val error = sync?.text("errorCode")
        val code = when (error) {
            "auth_required" -> "soundcloud_reauthorization_required"
            "not_found", "forbidden" -> "collection_$error"
            else -> "collection_refresh_pending"
        }
        val collection = objectOf(body)?.get("collection") as? JsonArray
        if (code == "collection_refresh_pending" && !collection.isNullOrEmpty()) return null
        val retryAfter = (sync?.get("retryAfterSeconds") as? JsonPrimitive)?.longOrNull
        return ApiHttpException(503, """{"code":"$code","message":"The collection is not ready"}""", retryAfter)
    }

    fun batchPath(ids: List<String>): String = "/tracks?ids=${ids.joinToString(",") { java.net.URLEncoder.encode(trackId(it), "UTF-8") }}&limit=${ids.size}&page=0"

    private fun cacheBody(response: Response): String = runCatching {
        val limit = 2L * 1024 * 1024
        response.peekBody(limit).use { body ->
            if (!response.header("Content-Encoding").equals("gzip", ignoreCase = true)) body.string()
            else GzipSource(body.source()).buffer().use { source ->
                source.request(limit)
                source.buffer.readUtf8(minOf(limit, source.buffer.size))
            }
        }
    }.getOrDefault("")

    fun cacheResponse(response: Response, isApi: Boolean, isImage: Boolean): Response {
        val request = response.request
        if (request.method != "GET" || response.code != 200) return response
        val path = request.url.encodedPath
        if (isApi) {
            val privateControl = path.startsWith("/auth") || path.startsWith("/health") || path.startsWith("/recommendations") ||
                path.startsWith("/lyrics") || path.startsWith("/rooms") || path.startsWith("/me/subscription") || path.endsWith("/stream")
            val pending = !privateControl && pendingCollection(cacheBody(response))
            return response.newBuilder().removeHeader("Pragma")
                .header("Cache-Control", if (privateControl || pending) "private, no-store" else "private, max-age=0")
                .header("Vary", (response.headers.values("Vary").flatMap { it.split(',') }.map { it.trim() } + "x-session-id").distinct().joinToString(", "))
                .build()
        }
        if (!isImage || response.cacheControl.noStore) return response
        return response.newBuilder().header("Cache-Control", "public, max-age=604800").build()
    }
}
