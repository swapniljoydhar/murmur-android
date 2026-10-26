package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.lang.Math.pow

@UnstableApi
class ReplayGainProcessor : MurmurAudioProcessor {
    override val id = "replay_gain"
    override val name = "ReplayGain"
    override val defaultConfig = AudioConfig(enabled = true, parameters = mapOf(
        "preamp" to 0f,
        "useAlbumGain" to 1f,
    ))
    
    private var config = defaultConfig
    private var currentGainDb = 0f
    private var currentPeak = 1f

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        return ReplayGainAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
    }

    fun setTrackGain(gainDb: Float, peak: Float) {
        if (config.enabled && config.parameters["useAlbumGain"] == 0f) {
            currentGainDb = (gainDb + config.parameters.getOrDefault("preamp", 0f)).coerceIn(-20f, 20f)
            currentPeak = peak
        }
    }

    fun setAlbumGain(gainDb: Float, peak: Float) {
        if (config.enabled && config.parameters["useAlbumGain"] == 1f) {
            currentGainDb = (gainDb + config.parameters.getOrDefault("preamp", 0f)).coerceIn(-20f, 20f)
            currentPeak = peak
        }
    }

    fun clearGain() {
        currentGainDb = 0f
        currentPeak = 1f
    }

    fun getCurrentGain(): Float = currentGainDb

    private inner class ReplayGainAudioProcessor(private val outer: ReplayGainProcessor) : AudioProcessor {
        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            val gain = outer.currentGainDb
            if (gain != 0f) {
                val linearGain = Math.pow(10.0, (gain / 20.0)).toFloat()
                val floatBuffer = inputBuffer.asFloatBuffer()
                for (i in 0 until floatBuffer.remaining()) {
                    floatBuffer.put(i, floatBuffer.get(i) * linearGain)
                }
            }
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {
            outer.clearGain()
        }
    }
}