package com.scd.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.BitmapLoader
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
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        executor.submit(
            Callable {
                BitmapFactory.decodeByteArray(data, 0, data.size)
                    ?: throw IOException("cannot decode artwork")
            },
        )

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        executor.submit(
            Callable {
                val raw = uri.toString()
                if (uri.scheme == "file") {
                    val path = uri.path
                    val local = path?.let { BitmapFactory.decodeFile(it) }
                    if (local != null) return@Callable local
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

                loaded ?: fallback() ?: throw IOException("no artwork")
            },
        )

    private fun fallback(): Bitmap? = NoteArtwork.bytes?.let {
        BitmapFactory.decodeByteArray(it, 0, it.size)
    }
}
