package com.scd.android

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder

internal suspend fun localWaveform(urn: String): List<Float>? = withContext(Dispatchers.IO) {
    val file = Downloads.localFile(urn) ?: ScDataSource.spooledFiles(urn).firstOrNull() ?: return@withContext null
    val extractor = MediaExtractor()
    var codec: MediaCodec? = null
    try {
        extractor.setDataSource(file.absolutePath)
        val index = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true } ?: return@withContext null
        val format = extractor.getTrackFormat(index)
        extractor.selectTrack(index)
        val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME) ?: return@withContext null)
        codec = decoder
        decoder.configure(format, null, null, 0)
        decoder.start()
        val info = MediaCodec.BufferInfo()
        val envelope = WaveformEnvelope()
        var inputDone = false
        var floating = false
        var idle = 0
        while (idle < 200) {
            currentCoroutineContext().ensureActive()
            var fed = false
            if (!inputDone) {
                val slot = decoder.dequeueInputBuffer(0)
                if (slot >= 0) {
                    val input = decoder.getInputBuffer(slot) ?: return@withContext null
                    val size = extractor.readSampleData(input, 0)
                    inputDone = size < 0
                    decoder.queueInputBuffer(slot, 0, size.coerceAtLeast(0), if (inputDone) 0 else extractor.sampleTime,
                        if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                    if (!inputDone) extractor.advance()
                    idle = 0
                    fed = true
                }
            }
            val slot = decoder.dequeueOutputBuffer(info, if (fed) 0L else 10000L)
            if (slot == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val output = decoder.outputFormat
                floating = output.containsKey(MediaFormat.KEY_PCM_ENCODING) && output.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
            } else if (slot >= 0) {
                idle = 0
                val buffer = decoder.getOutputBuffer(slot)?.order(ByteOrder.nativeOrder())
                if (buffer != null && info.size > 0) {
                    val width = if (floating) 4 else 2
                    for (i in info.offset..info.offset + info.size - width step width * 8) {
                        val sample = if (floating) buffer.getFloat(i).toDouble() else buffer.getShort(i) / 32768.0
                        envelope.add(sample)
                    }
                }
                decoder.releaseOutputBuffer(slot, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else if (!fed) idle++
        }
        if (idle >= 200) null else envelope.finish()
    } catch (e: CancellationException) { throw e }
    catch (_: Exception) { null }
    finally { codec?.release(); extractor.release() }
}
