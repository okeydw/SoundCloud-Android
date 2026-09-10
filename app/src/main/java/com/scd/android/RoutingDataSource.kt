package com.scd.android

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

@UnstableApi
class RoutingDataSource(
    private val localFactory: DataSource.Factory,
    private val networkFactory: DataSource.Factory,
) : DataSource {

    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners.add(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val scheme = dataSpec.uri.scheme?.lowercase()
        val remote = scheme == "http" || scheme == "https"
        val source = if (remote) networkFactory.createDataSource() else localFactory.createDataSource()
        for (l in listeners) source.addTransferListener(l)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        delegate?.read(buffer, offset, length) ?: -1

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        delegate?.responseHeaders ?: emptyMap()

    override fun close() {
        try {
            delegate?.close()
        } finally {
            delegate = null
        }
    }

    class Factory(
        private val localFactory: DataSource.Factory,
        private val networkFactory: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = RoutingDataSource(localFactory, networkFactory)
    }
}
