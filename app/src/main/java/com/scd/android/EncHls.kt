package com.scd.android

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException

object EncHls {
    const val PREFIX = "enchls:"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Payload(val init: String, val segments: List<String>, val key: String)

    fun isEnc(url: String): Boolean = url.startsWith(PREFIX)

    fun wrap(initBase64: String?, segments: List<String>, keyBase64: String?): String? {
        if (initBase64.isNullOrEmpty() || keyBase64.isNullOrEmpty() || segments.isEmpty()) return null
        val payload = Payload(initBase64, segments, keyBase64)
        val raw = json.encodeToString(Payload.serializer(), payload).encodeToByteArray()
        return PREFIX + Base64.encodeToString(raw, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun payload(url: String): Payload {
        val raw = Base64.decode(url.removePrefix(PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
        return json.decodeFromString(Payload.serializer(), raw.decodeToString())
    }

    fun writeTo(client: OkHttpClient, url: String, target: File) {
        val p = payload(url)
        val key = Base64.decode(p.key, Base64.DEFAULT)
        if (key.size != 16) throw IOException("enchls: key length ${key.size}")
        val init = Base64.decode(p.init, Base64.DEFAULT)
        if (init.isEmpty()) throw IOException("enchls: empty init")
        target.parentFile?.mkdirs()
        try {
            target.outputStream().buffered().use { out ->
                out.write(init)
                SegmentDownload.write(client, p.segments, out, transform = { Cenc.decryptSegment(it, key) })
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
        if (!Downloads.looksLikeAudio(target)) {
            target.delete()
            throw IOException("enchls: not audio")
        }
    }
}

internal fun audioSourceLabel(url: String): String = when {
    EncHls.isEnc(url) -> "enc-hls"
    HlsDownload.isHls(url) -> "hls"
    else -> url.substringAfter("://").substringBefore('/').substringBefore('?')
}
