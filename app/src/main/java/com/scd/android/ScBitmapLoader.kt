package com.scd.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.ListeningExecutorService
import com.google.common.util.concurrent.MoreExecutors
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@androidx.annotation.OptIn(UnstableApi::class)
class ScBitmapLoader(private val client: OkHttpClient) : BitmapLoader {
    private val executor: ListeningExecutorService =
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor { r -> Thread(r, "artwork").apply { isDaemon = true } })

    private val masters = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_ENTRIES
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        load("data:${data.size}:${data.contentHashCode()}") {
            BitmapFactory.decodeByteArray(data, 0, data.size)?.let { it to true }
                ?: throw IOException("cannot decode artwork")
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        load(uri.toString()) { fetch(uri) }

    private fun master(key: String): Bitmap? = synchronized(masters) { masters[key]?.takeIf { !it.isRecycled } }

    private fun copyOf(bitmap: Bitmap): Bitmap? =
        if (bitmap.isRecycled) null else runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()

    private fun load(key: String, produce: () -> Pair<Bitmap, Boolean>): ListenableFuture<Bitmap> {
        master(key)?.let { m -> copyOf(m)?.let { return Futures.immediateFuture(it) } }
        val loaded = executor.submit(
            Callable {
                master(key) ?: run {
                    val (raw, cacheable) = produce()
                    val small = shrink(raw)
                    if (cacheable) synchronized(masters) { masters[key] = small }
                    small
                }
            },
        )
        return Futures.transform(
            loaded,
            { m -> copyOf(m) ?: throw IllegalStateException("artwork bitmap was recycled") },
            MoreExecutors.directExecutor(),
        )
    }

    private fun shrink(bitmap: Bitmap): Bitmap {
        val side = maxOf(bitmap.width, bitmap.height)
        if (side <= MAX_SIDE) return bitmap
        val w = (bitmap.width.toLong() * MAX_SIDE / side).toInt().coerceAtLeast(1)
        val h = (bitmap.height.toLong() * MAX_SIDE / side).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(bitmap, w, h, true) }.getOrDefault(bitmap)
    }

    private fun fetch(uri: Uri): Pair<Bitmap, Boolean> {
        val raw = uri.toString()
        if (uri.scheme == "file") {
            uri.path?.let { BitmapFactory.decodeFile(it) }?.let { return it to true }
        }
        val request = if (raw.contains("sndcdn.com")) {
            val (name, value) = Api.imageProxyTarget(raw)
            Request.Builder()
                .url("${Api.IMAGES_BASE}/?t=${Uri.encode(value)}")
                .header(name, value)
                .build()
        } else {
            Request.Builder().url(raw).build()
        }
        val loaded = runCatching {
            client.newCall(request).execute().use { res ->
                val bytes = res.body?.bytes()
                if (!res.isSuccessful || bytes == null || bytes.isEmpty()) null
                else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()
        if (loaded != null) return loaded to true
        val note = NoteArtwork.bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            ?: throw IOException("no artwork")
        return note to false
    }

    private companion object {
        const val MAX_ENTRIES = 6
        const val MAX_SIDE = 512
    }
}
