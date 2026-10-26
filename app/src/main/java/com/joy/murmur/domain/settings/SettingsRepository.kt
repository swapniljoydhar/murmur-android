package com.joy.murmur.domain.settings

import com.joy.murmur.data.speech.SpeechVoiceOption
import com.joy.murmur.data.speech.VoiceNetworkRequirement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class AppearanceMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

interface SettingsRepository {
    val appearanceMode: Flow<AppearanceMode>

    val strictOfflineSpeech: Flow<Boolean>
        get() = flowOf(true)

    val readerTextScale: Flow<Float>
        get() = flowOf(1f)

    val backgroundMusicRefresh: Flow<Boolean>
        get() = flowOf(false)

    val selectedSpeechVoice: Flow<String?>
        get() = flowOf(null)

    val selectedNeuralVoice: Flow<String?>
        get() = flowOf(null)

    val neuralTtsSpeed: Flow<Float>
        get() = flowOf(1f)

    val outputProfiles: Flow<String>
        get() = flowOf("[]")

    suspend fun setAppearanceMode(mode: AppearanceMode)

    suspend fun setStrictOfflineSpeech(enabled: Boolean)

    suspend fun setReaderTextScale(scale: Float)

    suspend fun setBackgroundMusicRefresh(enabled: Boolean)

    suspend fun setSelectedSpeechVoice(voiceName: String?)

    suspend fun setSelectedNeuralVoice(voiceId: String?)

    suspend fun setNeuralTtsSpeed(speed: Float)

    suspend fun setOutputProfiles(json: String)

    // Voice eligibility for Strict Offline Speech
    suspend fun checkVoiceEligibility(voiceName: String?): VoiceNetworkRequirement

    suspend fun getAvailableVoices(): List<SpeechVoiceOption>
}