package com.joy.murmur.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.PI
import java.lang.Math.pow

@UnstableApi
class EqualizerProcessor : MurmurAudioProcessor {
    override val id = "equalizer"
    override val name = "Equalizer"
    override val defaultConfig = AudioConfig(enabled = false, parameters = mapOf(
        "band_31" to 0f, "band_62" to 0f, "band_125" to 0f, "band_250" to 0f,
        "band_500" to 0f, "band_1k" to 0f, "band_2k" to 0f, "band_4k" to 0f,
        "band_8k" to 0f, "band_16k" to 0f,
    ))
    
    private var config = defaultConfig
    private var bands = FloatArray(10)
    private var sampleRate = 44100
    private var filterCoefficients: Array<FloatArray>? = null

    override fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor {
        this.config = config
        updateBandsFromConfig()
        return EqualizerAudioProcessor(this)
    }

    override fun getCurrentConfig(): AudioConfig = config

    override fun updateConfig(config: AudioConfig) {
        this.config = config
        updateBandsFromConfig()
    }

    fun setPreset(preset: EqualizerPreset) {
        config = AudioConfig(
            enabled = true,
            parameters = preset.gains.withIndex().associate { it.index.toString() to it.value }
        )
        updateBandsFromConfig()
    }

    private fun updateBandsFromConfig() {
        bands[0] = config.parameters.getOrDefault("band_31", 0f)
        bands[1] = config.parameters.getOrDefault("band_62", 0f)
        bands[2] = config.parameters.getOrDefault("band_125", 0f)
        bands[3] = config.parameters.getOrDefault("band_250", 0f)
        bands[4] = config.parameters.getOrDefault("band_500", 0f)
        bands[5] = config.parameters.getOrDefault("band_1k", 0f)
        bands[6] = config.parameters.getOrDefault("band_2k", 0f)
        bands[7] = config.parameters.getOrDefault("band_4k", 0f)
        bands[8] = config.parameters.getOrDefault("band_8k", 0f)
        bands[9] = config.parameters.getOrDefault("band_16k", 0f)
        rebuildFilters()
    }

    private fun rebuildFilters() {
        filterCoefficients = Array(10) { i ->
            val freq = equalizerFrequencies[i]
            val gainDb = bands[i]
            if (gainDb == 0f) {
                floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
            } else {
                val A = Math.pow(10.0, (gainDb / 40.0)).toFloat()
                val w0 = (2f * Math.PI.toFloat() * freq / sampleRate)
                val alpha = kotlin.math.sin(w0) / 2f * A
                val b0 = 1f + alpha * A
                val b1 = -2f * kotlin.math.cos(w0)
                val b2 = 1f - alpha * A
                val a0 = 1f + alpha / A
                val a1 = -2f * kotlin.math.cos(w0)
                val a2 = 1f - alpha / A
                floatArrayOf(b0/a0, b1/a0, b2/a0, 1f, a1/a0, a2/a0)
            }
        }
    }

    fun setSampleRate(sampleRate: Int) {
        this.sampleRate = sampleRate
        rebuildFilters()
    }

    companion object {
        private val equalizerFrequencies = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    }

    private inner class EqualizerAudioProcessor(private val outer: EqualizerProcessor) : AudioProcessor {
        private val buffers = Array(10) { FloatArray(2) }

        override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
            outer.setSampleRate(inputAudioFormat.sampleRate)
            return inputAudioFormat
        }

        override fun isActive(): Boolean = outer.config.enabled

        override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
            if (!outer.config.enabled) return
            
            val coeffs = outer.filterCoefficients
            if (coeffs == null) return
            
            val floatBuffer = inputBuffer.asFloatBuffer()
            val remaining = floatBuffer.remaining()
            val output = FloatArray(remaining)
            
            for (ch in 0 until 1) {
                for (i in 0 until remaining) {
                    val input = floatBuffer.get(i)
                    var acc = 0f
                    for (b in 0 until 10) {
                        val c = coeffs[b]
                        val buf = buffers[b]
                        acc += c[0] * input + c[1] * buf[0] + c[2] * buf[1] - c[4] * buf[0] - c[5] * buf[1]
                        buf[1] = buf[0]
                        buf[0] = input
                    }
                    output[i] = acc
                }
                floatBuffer.rewind()
                floatBuffer.put(output)
                floatBuffer.rewind()
            }
        }

        override fun queueEndOfStream() {}

        override fun getOutput(): java.nio.ByteBuffer = java.nio.ByteBuffer.allocate(0)

        override fun isEnded(): Boolean = false

        override fun reset() {
            buffers.forEach { it.fill(0f) }
        }
    }
}

data class EqualizerPreset(
    val name: String,
    val gains: FloatArray,
) {
    companion object {
        val FLAT = EqualizerPreset("Flat", FloatArray(10))
        val BASS_BOOST = EqualizerPreset("Bass Boost", floatArrayOf(6f, 4f, 2f, 1f, 0f, 0f, 0f, -1f, -2f, -3f))
        val TREBLE_BOOST = EqualizerPreset("Treble Boost", floatArrayOf(-3f, -2f, -1f, 0f, 0f, 0f, 1f, 2f, 4f, 6f))
        val VOCAL = EqualizerPreset("Vocal", floatArrayOf(-2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f, -2f))
        val CLASSICAL = EqualizerPreset("Classical", floatArrayOf(2f, 2f, 1f, 0f, -1f, -1f, 0f, 1f, 2f, 2f))
        val ROCK = EqualizerPreset("Rock", floatArrayOf(4f, 3f, 1f, -1f, -2f, -1f, 1f, 3f, 4f, 3f))
        val JAZZ = EqualizerPreset("Jazz", floatArrayOf(3f, 2f, 1f, 0f, -1f, 0f, 1f, 2f, 3f, 2f))
        val POP = EqualizerPreset("Pop", floatArrayOf(2f, 1f, 0f, 1f, 2f, 3f, 2f, 1f, 0f, -1f))
        val SPOKEN_WORD = EqualizerPreset("Spoken Word", floatArrayOf(-4f, -2f, 0f, 3f, 4f, 4f, 3f, 0f, -2f, -4f))
    }
}