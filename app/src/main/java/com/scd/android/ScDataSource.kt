package com.scd.android

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

@UnstableApi
class ScDataSource(private val client: OkHttpClient) : BaseDataSource(true) {

    private var currentSpec: DataSpec? = null
    private var response: Response? = null
    private var input: InputStream? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        currentSpec = dataSpec
        transferInitializing(dataSpec)

        val builder = Request.Builder()
            .url(dataSpec.uri.toString())
            .cacheControl(okhttp3.CacheControl.Builder().noStore().build())
        val position = dataSpec.position
        val length = dataSpec.length
        if (position != 0L || length != C.LENGTH_UNSET.toLong()) {
            val range = buildString {
                append("bytes=").append(position).append("-")
                if (length != C.LENGTH_UNSET.toLong()) append(position + length - 1)
            }
            builder.header("Range", range)
        }
        for ((k, v) in dataSpec.httpRequestHeaders) builder.header(k, v)

        val resp = client.newCall(builder.build()).execute()
        response = resp
        if (!resp.isSuccessful) {
            val code = resp.code
            resp.close()
            throw IOException("HTTP $code")
        }
        val body = resp.body ?: throw IOException("empty body")
        if (resp.header("content-length") == "0") {
            resp.close()
            throw IOException("empty stream body")
        }
        input = body.byteStream()
        bytesRemaining = if (length != C.LENGTH_UNSET.toLong()) {
            length
        } else {
            val cl = body.contentLength()
            if (cl >= 0) cl else C.LENGTH_UNSET.toLong()
        }
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            readLength
        } else {
            minOf(readLength.toLong(), bytesRemaining).toInt()
        }
        if (toRead <= 0) return C.RESULT_END_OF_INPUT
        val read = input?.read(buffer, offset, toRead) ?: -1
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentSpec?.uri

    override fun close() {
        runCatching { input?.close() }
        input = null
        runCatching { response?.close() }
        response = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    class Factory(private val client: OkHttpClient) : DataSource.Factory {
        @UnstableApi
        override fun createDataSource(): DataSource = ScDataSource(client)
    }
}
