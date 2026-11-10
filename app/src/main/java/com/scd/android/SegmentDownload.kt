package com.scd.android

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal object SegmentDownload {
    const val WINDOW = 6
    private val pool = Executors.newFixedThreadPool(WINDOW * 2) { r ->
        Thread(r, "hls-segment").apply { isDaemon = true }
    }

    private class Pending(val call: Call, val future: Future<ByteArray>)

    fun write(client: OkHttpClient, urls: List<String>, out: OutputStream, transform: (ByteArray) -> ByteArray = { it }) {
        val pending = ArrayDeque<Pending>()
        var next = 0
        fun fill() {
            while (next < urls.size && pending.size < WINDOW) {
                val call = client.newCall(Request.Builder().url(urls[next++]).header("User-Agent", ScAnon.USER_AGENT).build())
                val future = pool.submit(Callable {
                    call.execute().use { response ->
                        if (!response.isSuccessful) throw IOException("hls HTTP ${response.code}")
                        transform(response.body?.bytes() ?: throw IOException("hls empty body"))
                    }
                })
                pending.addLast(Pending(call, future))
            }
        }
        try {
            fill()
            while (pending.isNotEmpty()) {
                if (Thread.currentThread().isInterrupted) throw InterruptedIOException("hls cancelled")
                val bytes = pending.first().future.get(45, TimeUnit.SECONDS)
                pending.removeFirst()
                fill()
                out.write(bytes)
            }
        } catch (e: ExecutionException) {
            throw (e.cause as? IOException) ?: IOException("hls segment failed", e.cause)
        } catch (e: TimeoutException) {
            throw java.net.SocketTimeoutException("hls segment timeout")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("hls cancelled")
        } finally {
            pending.forEach { it.call.cancel(); it.future.cancel(true) }
        }
    }
}
