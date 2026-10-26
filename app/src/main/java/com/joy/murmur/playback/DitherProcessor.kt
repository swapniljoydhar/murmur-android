package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlin.random.Random

@UnstableApi
class DitherProcessor : MurmurAudioProcessor {
    override val id = "dither"
    override val name = "Dither (TPDF)"
    override val defaultConfig = AudioConfig(enabled = false, parameters = mapOf(
        "bit_depth" to 16f,
    ))
    
    private var config = defaultConfig

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        return DitherAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
    }

    private inner class DitherAudioProcessor(private val outer: DitherProcessor) : AudioProcessor {
        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            
            val bitDepth = outer.config.parameters.getOrDefault("bit_depth", 16f).toInt()
            if (bitDepth >= 24) return
            
            val floatBuffer = inputBuffer.asFloatBuffer()
            val remaining = floatBuffer.remaining()
            val maxValue = (1 shl (bitDepth - 1)) - 1
            val scale = maxValue.toFloat()
            val ditherAmplitude = 1f / scale
            
            for (i in 0 until remaining) {
                val sample = floatBuffer.get(i)
                // TPDF dither: sum of two independent uniform random variables
                val dither = (Random.nextFloat() - 0.5f + Random.nextFloat() - 0.5f) * ditherAmplitude
                val quantized = kotlin.math.round((sample + dither) * scale) / scale
                floatBuffer.put(i, quantized.coerceIn(-1f, 1f))
            }
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {}
    }
}