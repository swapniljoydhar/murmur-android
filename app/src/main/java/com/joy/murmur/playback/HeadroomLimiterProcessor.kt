package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.lang.Math.pow
import kotlin.math.exp

@UnstableApi
class HeadroomLimiterProcessor : MurmurAudioProcessor {
    override val id = "headroom_limiter"
    override val name = "Headroom Limiter"
    override val defaultConfig = AudioConfig(enabled = true, parameters = mapOf(
        "headroom_db" to -1.0f,
        "lookahead_ms" to 2.0f,
        "attack_ms" to 1.0f,
        "release_ms" to 100.0f,
    ))
    
    private var config = defaultConfig

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        return HeadroomLimiterAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
    }

    private inner class HeadroomLimiterAudioProcessor(private val outer: HeadroomLimiterProcessor) : AudioProcessor {
        private var envelope = 0f
        private var sampleRate = 44100
        private var channels = 2
        private var attackCoeff = 0f
        private var releaseCoeff = 0f
        private var headroomLinear = 0.89f
        private var lookaheadBuffer: FloatArray? = null
        private var lookaheadIndex = 0

        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            sampleRate = inputAudioFormat.sampleRate
            channels = inputAudioFormat.channelCount
            val params = outer.config.parameters
            val attackMs = params.getOrDefault("attack_ms", 1.0f)
            val releaseMs = params.getOrDefault("release_ms", 100.0f)
            val lookaheadMs = params.getOrDefault("lookahead_ms", 2.0f)
            
            attackCoeff = kotlin.math.exp(-1f / (attackMs * sampleRate / 1000f))
            releaseCoeff = kotlin.math.exp(-1f / (releaseMs * sampleRate / 1000f))
            headroomLinear = Math.pow(10.0, (params.getOrDefault("headroom_db", -1.0f) / 20.0)).toFloat()
            
            val lookaheadSamples = (lookaheadMs * sampleRate / 1000f).toInt().coerceAtLeast(1)
            lookaheadBuffer = FloatArray(lookaheadSamples * channels)
            lookaheadIndex = 0
            
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            
            val floatBuffer = inputBuffer.asFloatBuffer()
            val remaining = floatBuffer.remaining()
            
            val buffer = lookaheadBuffer ?: return
            val bufSize = buffer.size
            
            for (i in 0 until remaining) {
                val sample = floatBuffer.get(i)
                val absSample = kotlin.math.abs(sample)
                
                // Update envelope
                if (absSample > envelope) {
                    envelope = absSample + attackCoeff * (envelope - absSample)
                } else {
                    envelope = absSample + releaseCoeff * (envelope - absSample)
                }
                
                // Calculate gain
                val targetGain = if (envelope > 0f) (headroomLinear / envelope).coerceAtMost(1f) else 1f
                val gain = targetGain.coerceAtMost(1f)
                
                // Apply gain with lookahead
                buffer[lookaheadIndex] = sample * gain
                lookaheadIndex = (lookaheadIndex + 1) % bufSize
                
                // Output delayed sample
                val outputIndex = (lookaheadIndex - buffer.size / 2 + bufSize) % bufSize
                floatBuffer.put(i, buffer[outputIndex])
            }
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {
            envelope = 0f
            lookaheadBuffer?.fill(0f)
            lookaheadIndex = 0
        }
    }
}