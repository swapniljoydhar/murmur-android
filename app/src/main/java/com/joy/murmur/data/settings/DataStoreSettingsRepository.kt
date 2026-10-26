package com.joy.murmur.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joy.murmur.data.speech.SpeechVoiceOption
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.data.speech.VoiceNetworkRequirement
import com.joy.murmur.domain.settings.AppearanceMode
import com.joy.murmur.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStoreDelegate by preferencesDataStore(name = "settings")

val Context.settingsDataStore: DataStore<Preferences>
    get() = settingsDataStoreDelegate

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val ttsGenerator: SystemTtsSpeechGenerator? = null,
) : SettingsRepository {
    override val appearanceMode: Flow<AppearanceMode> = dataStore.data.map { preferences ->
        val storedValue = preferences[APPEARANCE_MODE_KEY]
        AppearanceMode.entries.firstOrNull { it.storageValue == storedValue } ?: AppearanceMode.SYSTEM
    }

    override val strictOfflineSpeech: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[STRICT_OFFLINE_SPEECH_KEY] ?: true
    }

    override val readerTextScale: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[READER_TEXT_SCALE_KEY] ?: 1f).coerceIn(0.8f, 1.6f)
    }

    override val backgroundMusicRefresh: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[BACKGROUND_MUSIC_REFRESH_KEY] ?: false
    }

    override val selectedSpeechVoice: Flow<String?> = dataStore.data.map { preferences -> preferences[SELECTED_SPEECH_VOICE_KEY] }

    override val selectedNeuralVoice: Flow<String?> = dataStore.data.map { preferences -> preferences[SELECTED_NEURAL_VOICE_KEY] }

    override val neuralTtsSpeed: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[NEURAL_TTS_SPEED_KEY] ?: 1f).coerceIn(0.5f, 2.0f)
    }

    override val outputProfiles: Flow<String> = dataStore.data.map { preferences -> preferences[OUTPUT_PROFILES_KEY] ?: "[]" }

    override suspend fun setAppearanceMode(mode: AppearanceMode) {
        dataStore.edit { preferences ->
            preferences[APPEARANCE_MODE_KEY] = mode.storageValue
        }
    }

    override suspend fun setStrictOfflineSpeech(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[STRICT_OFFLINE_SPEECH_KEY] = enabled }
    }

    override suspend fun setReaderTextScale(scale: Float) {
        dataStore.edit { preferences -> preferences[READER_TEXT_SCALE_KEY] = scale.coerceIn(0.8f, 1.6f) }
    }

    override suspend fun setBackgroundMusicRefresh(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[BACKGROUND_MUSIC_REFRESH_KEY] = enabled }
    }

    override suspend fun setSelectedSpeechVoice(voiceName: String?) {
        dataStore.edit { preferences ->
            if (voiceName.isNullOrBlank()) preferences.remove(SELECTED_SPEECH_VOICE_KEY)
            else preferences[SELECTED_SPEECH_VOICE_KEY] = voiceName
        }
    }

    override suspend fun setSelectedNeuralVoice(voiceId: String?) {
        dataStore.edit { preferences ->
            if (voiceId.isNullOrBlank()) preferences.remove(SELECTED_NEURAL_VOICE_KEY)
            else preferences[SELECTED_NEURAL_VOICE_KEY] = voiceId
        }
    }

    override suspend fun setNeuralTtsSpeed(speed: Float) {
        dataStore.edit { preferences -> preferences[NEURAL_TTS_SPEED_KEY] = speed.coerceIn(0.5f, 2.0f) }
    }

    override suspend fun setOutputProfiles(json: String) {
        dataStore.edit { preferences -> preferences[OUTPUT_PROFILES_KEY] = json }
    }

    override suspend fun checkVoiceEligibility(voiceName: String?): VoiceNetworkRequirement {
        return ttsGenerator?.voiceNetworkRequirement() ?: VoiceNetworkRequirement.UNKNOWN
    }

    override suspend fun getAvailableVoices(): List<SpeechVoiceOption> {
        return ttsGenerator?.availableVoices() ?: emptyList()
    }

    private companion object {
        val APPEARANCE_MODE_KEY = stringPreferencesKey("appearance_mode")
        val STRICT_OFFLINE_SPEECH_KEY = booleanPreferencesKey("strict_offline_speech")
        val READER_TEXT_SCALE_KEY = floatPreferencesKey("reader_text_scale")
        val BACKGROUND_MUSIC_REFRESH_KEY = booleanPreferencesKey("background_music_refresh")
        val SELECTED_SPEECH_VOICE_KEY = stringPreferencesKey("selected_speech_voice")
        val SELECTED_NEURAL_VOICE_KEY = stringPreferencesKey("selected_neural_voice")
        val NEURAL_TTS_SPEED_KEY = floatPreferencesKey("neural_tts_speed")
        val OUTPUT_PROFILES_KEY = stringPreferencesKey("output_profiles")
    }
}