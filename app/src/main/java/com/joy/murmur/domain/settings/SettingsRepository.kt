package com.joy.murmur.domain.settings

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

    suspend fun setAppearanceMode(mode: AppearanceMode)

    suspend fun setStrictOfflineSpeech(enabled: Boolean) = Unit

    suspend fun setReaderTextScale(scale: Float) = Unit

    suspend fun setBackgroundMusicRefresh(enabled: Boolean) = Unit

    suspend fun setSelectedSpeechVoice(voiceName: String?) = Unit
}
