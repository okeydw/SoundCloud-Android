package com.scd.android

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import kotlin.math.roundToInt

@UnstableApi
class EqProcessor : BaseAudioProcessor() {
    private val dsp = EqDsp()
    private var failed = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val pcm = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        return if (pcm && inputAudioFormat.channelCount in 1..8) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        val format = inputAudioFormat
        failed = false
        if (format != AudioProcessor.AudioFormat.NOT_SET) {
            try { dsp.configure(format.sampleRate, format.channelCount) }
            catch (_: RuntimeException) { failed = true }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val format = inputAudioFormat
        val channels = format.channelCount
        val out = replaceOutputBuffer(remaining)
        val start = inputBuffer.position()
        if (failed || channels !in 1..8) {
            out.put(inputBuffer).flip()
            return
        }
        try {
            if (format.encoding == C.ENCODING_PCM_FLOAT) {
                repeat(remaining / (4 * channels)) {
                    dsp.beginFrame()
                    if (dsp.bypass) {
                        repeat(channels) {
                            val value = inputBuffer.getFloat()
                            out.putFloat(if (value.isFinite()) value else 0f)
                        }
                    } else {
                        for (c in 0 until channels) out.putFloat(dsp.process(c, inputBuffer.getFloat().toDouble()).toFloat())
                    }
                }
            } else {
                repeat(remaining / (2 * channels)) {
                    dsp.beginFrame()
                    if (dsp.bypass) {
                        repeat(channels) { out.putShort(inputBuffer.getShort()) }
                    } else {
                        for (c in 0 until channels) {
                            val y = dsp.process(c, inputBuffer.getShort() / 32768.0)
                            out.putShort((y * 32768.0).roundToInt().coerceIn(-32768, 32767).toShort())
                        }
                    }
                }
            }
            while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        } catch (_: RuntimeException) {
            failed = true
            dsp.reset()
            inputBuffer.position(start)
            out.clear()
            out.put(inputBuffer)
        }
        out.flip()
    }
}

@UnstableApi
class EqRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioOutputPlaybackParameters(enableAudioOutputPlaybackParams)
        .setAudioProcessors(arrayOf<AudioProcessor>(EqProcessor()))
        .build()
}
