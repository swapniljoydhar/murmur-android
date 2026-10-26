package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi

@UnstableApi
class BalanceProcessor : MurmurAudioProcessor {
    override val id = "balance"
    override val name = "Balance"
    override val defaultConfig = AudioConfig(enabled = false, parameters = mapOf(
        "balance" to 0f,
    ))
    
    private var config = defaultConfig

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        return BalanceAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
    }

    private inner class BalanceAudioProcessor(private val outer: BalanceProcessor) : AudioProcessor {
        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            if (inputAudioFormat.channelCount != 2) {
                return AudioFormat.NOT_SET
            }
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            
            val balance = outer.config.parameters.getOrDefault("balance", 0f)
            if (balance == 0f) return
            
            val floatBuffer = inputBuffer.asFloatBuffer()
            val remaining = floatBuffer.remaining()
            
            val leftGain = if (balance < 0f) 1f else 1f - kotlin.math.abs(balance)
            val rightGain = if (balance > 0f) 1f else 1f - kotlin.math.abs(balance)
            
            for (i in 0 until remaining step 2) {
                floatBuffer.put(i, floatBuffer.get(i) * leftGain)
                if (i + 1 < remaining) {
                    floatBuffer.put(i + 1, floatBuffer.get(i + 1) * rightGain)
                }
            }
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {}
    }
}