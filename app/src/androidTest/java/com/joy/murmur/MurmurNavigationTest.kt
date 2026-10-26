package com.joy.murmur

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joy.murmur.domain.settings.AppearanceMode
import com.joy.murmur.domain.settings.SettingsRepository
import com.joy.murmur.data.speech.SpeechVoiceOption
import com.joy.murmur.data.speech.VoiceNetworkRequirement
import com.joy.murmur.ui.MurmurApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MurmurNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun continueHubOpensPrimaryContentSections() {
        composeRule.setContent {
            TestContent()
        }

        composeRule.onNodeWithText("A quiet place for your local media.").assertIsDisplayed()
        composeRule.onNodeWithTag("destination-music").performClick()
        composeRule.onNodeWithText("Find music on this phone").assertIsDisplayed()
        composeRule.onNodeWithText("Scan device").assertIsDisplayed()
        composeRule.onNodeWithText("Choose audio files").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag("destination-audiobooks").performClick()
        composeRule.onNodeWithText("Audiobook storage isn't available right now.").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag("destination-documents").performClick()
        composeRule.onNodeWithText("No documents yet").assertIsDisplayed()
        composeRule.onNodeWithText("Read your documents here; listen from the open reader.").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    fun TestContent() {
        MurmurApp(
            settingsRepository = TestSettingsRepository(),
            playbackController = null,
            musicLibraryRepository = null,
            musicPlaylistRepository = null,
            audiobookLibraryRepository = null,
            documentLibraryRepository = null,
        )
    }

    private class TestSettingsRepository : SettingsRepository {
        private val mode = MutableStateFlow(AppearanceMode.SYSTEM)

        override val appearanceMode: Flow<AppearanceMode> = mode

        override suspend fun setAppearanceMode(mode: AppearanceMode) {
            this.mode.update { mode }
        }

        override suspend fun setStrictOfflineSpeech(enabled: Boolean) = Unit
        override suspend fun setReaderTextScale(scale: Float) = Unit
        override suspend fun setBackgroundMusicRefresh(enabled: Boolean) = Unit
        override suspend fun setSelectedSpeechVoice(voiceName: String?) = Unit
        override suspend fun setSelectedNeuralVoice(voiceId: String?) = Unit
        override suspend fun setNeuralTtsSpeed(speed: Float) = Unit
        override suspend fun setOutputProfiles(json: String) = Unit
        override suspend fun checkVoiceEligibility(voiceName: String?) = VoiceNetworkRequirement.UNKNOWN
        override suspend fun getAvailableVoices(): List<SpeechVoiceOption> = emptyList()
    }
}
