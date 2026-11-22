package com.scd.android

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.inspector.MediaExtractorCompat
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@androidx.annotation.OptIn(UnstableApi::class)
internal object AudioNormalize {
    @Volatile private var context: Context? = null

    fun init(context: Context) { this.context = context.applicationContext }

    fun fragmented(file: File): Boolean = runCatching {
        RandomAccessFile(file, "r").use { data ->
            if (data.length() < 8) return@use false
            data.seek(4)
            if (data.readInt() != 0x66747970) return@use false
            var offset = 0L
            while (offset <= data.length() - 8) {
                data.seek(offset)
                var size = data.readInt().toLong() and 0xffffffffL
                val type = data.readInt()
                var header = 8L
                if (size == 1L) {
                    if (offset > data.length() - 16) return@use false
                    size = data.readLong()
                    header = 16
                } else if (size == 0L) size = data.length() - offset
                if (size < header || size > data.length() - offset) return@use false
                if (type == 0x6d6f6f66) return@use true
                offset += size
            }
            false
        }
    }.getOrDefault(false)

    fun repair(file: File, expectedMs: Long = 0L): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        if (runCatching { ClearMp4.repair(file) }.isFailure) return false
        val ctx = context ?: return false
        if (!fragmented(file)) return false
        val temp = runCatching { File.createTempFile("audio-normalize-", ".m4a", file.parentFile) }.getOrNull() ?: return false
        val extractor = runCatching { MediaExtractorCompat(AudioExtractors.factory(), DefaultDataSource.Factory(ctx)) }.getOrNull()
        if (extractor == null) {
            temp.delete()
            return false
        }
        var muxer: MediaMuxer? = null
        var started = false
        try {
            extractor.setDataSource(Uri.fromFile(file), 0L)
            val source = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"
            } ?: return false
            extractor.selectTrack(source)
            val format = extractor.getTrackFormat(source)
            val timeline = AacTimeline(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
            val output = MediaMuxer(temp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = output
            val track = output.addTrack(format)
            output.start()
            started = true
            val data = ByteBuffer.allocateDirect(1024 * 1024)
            val info = MediaCodec.BufferInfo()
            var count = 0
            while (extractor.sampleTime >= 0L) {
                data.clear()
                val size = extractor.readSampleData(data, 0)
                if (size < 0) break
                if (extractor.sampleFlags and android.media.MediaExtractor.SAMPLE_FLAG_ENCRYPTED != 0) return false
                if (size > 0) {
                    info.set(0, size, timeline.next(extractor.sampleTime), MediaCodec.BUFFER_FLAG_KEY_FRAME)
                    output.writeSampleData(track, data, info)
                    count++
                }
                if (!extractor.advance()) break
            }
            output.stop()
            started = false
            output.release()
            muxer = null
            if (count == 0 || temp.length() == 0L) return false
            val result = AudioProbe.inspect(temp)
            if (!result.playable || (result.ms ?: 0L) <= 0L) return false
            if (AudioProbe.shortReason(result, temp.length(), expectedMs) != null) return false
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            Logs.add("cache", "HLS repacked: $count samples, ${timeline.discontinuities} timestamp jumps")
            return true
        } catch (e: Exception) {
            Logs.add("cache", "HLS repack failed: ${e.javaClass.simpleName}")
            return false
        } finally {
            if (started) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            temp.delete()
        }
    }
}
