package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi

@UnstableApi
class CrossfeedProcessor : MurmurAudioProcessor {
    override val id = "crossfeed"
    override val name = "Crossfeed"
    override val defaultConfig = AudioConfig(enabled = false, parameters = mapOf(
        "crossfeed_level" to 0.5f,
        "high_freq_attenuation" to 0.7f,
    ))
    
    private var config = defaultConfig

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        return CrossfeedAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
    }

    private inner class CrossfeedAudioProcessor(private val outer: CrossfeedProcessor) : AudioProcessor {
        private var leftDelayBuffer = FloatArray(0)
        private var rightDelayBuffer = FloatArray(0)
        private var delayIndex = 0
        private var sampleRate = 44100
        private var delaySamples = 16

        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            if (inputAudioFormat.channelCount != 2) {
                return AudioFormat.NOT_SET
            }
            sampleRate = inputAudioFormat.sampleRate
            val newDelaySamples = (sampleRate * 0.0003f).toInt().coerceAtLeast(1).coerceAtMost(64)
            if (newDelaySamples != delaySamples) {
                delaySamples = newDelaySamples
                leftDelayBuffer = FloatArray(delaySamples)
                rightDelayBuffer = FloatArray(delaySamples)
                delayIndex = 0
            }
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            
            val floatBuffer = inputBuffer.asFloatBuffer()
            val remaining = floatBuffer.remaining()
            
            if (remaining % 2 != 0) return
            
            val params = outer.config.parameters
            val crossfeedLevel = params.getOrDefault("crossfeed_level", 0.5f)
            val highFreqAttenuation = params.getOrDefault("high_freq_attenuation", 0.7f)
            
            val delay = delaySamples
            val lb = leftDelayBuffer
            val rb = rightDelayBuffer
            var idx = delayIndex
            
            for (i in 0 until remaining step 2) {
                val left = floatBuffer.get(i)
                val right = floatBuffer.get(i + 1)
                
                val delayedLeft = lb[idx]
                val delayedRight = rb[idx]
                
                val mixedLeft = left + crossfeedLevel * (highFreqAttenuation * delayedRight)
                val mixedRight = right + crossfeedLevel * (highFreqAttenuation * delayedLeft)
                
                floatBuffer.put(i, mixedLeft)
                floatBuffer.put(i + 1, mixedRight)
                
                lb[idx] = left
                rb[idx] = right
                idx = (idx + 1) % delay
            }
            
            delayIndex = idx
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {
            leftDelayBuffer.fill(0f)
            rightDelayBuffer.fill(0f)
            delayIndex = 0
        }
    }
}