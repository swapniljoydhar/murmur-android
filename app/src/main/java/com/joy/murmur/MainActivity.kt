package com.joy.murmur

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.data.settings.settingsDataStore
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.music.MediaStoreMusicLibraryRepository
import com.joy.murmur.data.music.RoomMusicPlaylistRepository
import com.joy.murmur.data.audiobooks.RoomAudiobookLibraryRepository
import com.joy.murmur.data.playback.MusicPlaylistStore
import com.joy.murmur.data.playback.AudiobookStore
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.data.documents.RoomDocumentLibraryRepository
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.domain.music.MusicLibraryRepository
import com.joy.murmur.domain.music.MusicPlaylistRepository
import com.joy.murmur.domain.audiobooks.AudiobookLibraryRepository
import com.joy.murmur.domain.documents.DocumentLibraryRepository
import com.joy.murmur.domain.settings.SettingsRepository
import com.joy.murmur.playback.PlaybackController
import com.joy.murmur.playback.SourceInfoExtractor
import com.joy.murmur.playback.OutputProfileManager
import com.joy.murmur.ui.MurmurApp

class MainActivity : ComponentActivity() {
    private val ttsGenerator: SystemTtsSpeechGenerator by lazy {
        SystemTtsSpeechGenerator(applicationContext)
    }
    private val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext.settingsDataStore, ttsGenerator)
    }
    private val sourceInfoExtractor: SourceInfoExtractor by lazy {
        SourceInfoExtractor(applicationContext)
    }
    private val outputProfileManager: OutputProfileManager by lazy {
        OutputProfileManager(applicationContext, DataStoreSettingsRepository(applicationContext.settingsDataStore))
    }
    private val playbackController: PlaybackController by lazy {
        PlaybackController(applicationContext, PlaybackStore(PlaybackDatabase.getInstance(applicationContext)), sourceInfoExtractor, outputProfileManager)
    }
    private val musicLibraryRepository: MusicLibraryRepository by lazy {
        MediaStoreMusicLibraryRepository(
            applicationContext,
            com.joy.murmur.data.playback.MusicCatalogStore(PlaybackDatabase.getInstance(applicationContext)),
        )
    }
    private val musicPlaylistRepository: MusicPlaylistRepository by lazy {
        RoomMusicPlaylistRepository(MusicPlaylistStore(PlaybackDatabase.getInstance(applicationContext)))
    }
    private val audiobookLibraryRepository: AudiobookLibraryRepository by lazy {
        RoomAudiobookLibraryRepository(applicationContext, AudiobookStore(PlaybackDatabase.getInstance(applicationContext)))
    }
    private val documentLibraryRepository: DocumentLibraryRepository by lazy {
        RoomDocumentLibraryRepository(applicationContext, ReaderDocumentStore(PlaybackDatabase.getInstance(applicationContext)))
    }

    /**
     * The intent request waiting to be handled by the composed UI. [MurmurApp] consumes it exactly once
     * through `onRequestHandled`, using the real `NavHostController` that is attached to the `NavHost`.
     */
    private var pendingRequest by mutableStateOf<MurmurIntentRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only read the launch intent for a fresh launch: a configuration change re-delivers the same
        // intent and must not replay a deep link or re-import shared files.
        if (savedInstanceState == null) {
            pendingRequest = MurmurIntents.parse(intent)
        }
        setContent {
            MurmurApp(
                settingsRepository = settingsRepository,
                playbackController = playbackController,
                musicLibraryRepository = musicLibraryRepository,
                musicPlaylistRepository = musicPlaylistRepository,
                audiobookLibraryRepository = audiobookLibraryRepository,
                documentLibraryRepository = documentLibraryRepository,
                request = pendingRequest,
                onRequestHandled = { pendingRequest = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A plain launcher tap (ACTION_MAIN, no data) parses to null and must not discard a request
        // that is still waiting to be handled.
        MurmurIntents.parse(intent)?.let { pendingRequest = it }
    }

    override fun onDestroy() {
        playbackController.release()
        ttsGenerator.shutdown()
        super.onDestroy()
    }
}
