package com.joy.murmur.playback

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
interface MurmurAudioProcessor {
    val id: String
    val name: String
    val defaultConfig: AudioConfig
    fun createExoPlayerProcessor(config: AudioConfig): AudioProcessor
    fun getCurrentConfig(): AudioConfig
    fun updateConfig(config: AudioConfig)
}

data class AudioConfig(
    val enabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap(),
)

data class ProcessorChainConfig(
    val processors: List<ProcessorConfig> = emptyList(),
)

data class ProcessorConfig(
    val id: String,
    val enabled: Boolean,
    val parameters: Map<String, Float> = emptyMap(),
)