package com.joy.murmur

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joy.murmur.domain.settings.AppearanceMode
import com.joy.murmur.domain.settings.SettingsRepository
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
    fun continueHubOpensEveryContentSection() {
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
        composeRule.onNodeWithText("Select audiobook files from this device. Your place is saved as you listen.").assertIsDisplayed()
        composeRule.onNodeWithText("Play selected files").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag("destination-documents").performClick()
        composeRule.onNodeWithText("Your local documents will appear here when document support is added.").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()

        composeRule.onNodeWithTag("destination-read-aloud").performClick()
        composeRule.onNodeWithText("Open a document to start reading aloud when document and speech support are added.").assertIsDisplayed()
    }

    @androidx.compose.runtime.Composable
    fun TestContent() {
        val navController = rememberNavController()
        MurmurApp(
            settingsRepository = TestSettingsRepository(),
            navController = navController,
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
    }
}
