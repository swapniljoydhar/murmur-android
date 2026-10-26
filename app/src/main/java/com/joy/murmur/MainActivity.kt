package com.joy.murmur

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavHostController
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
    private val navController: NavHostController by lazy { NavHostController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        lifecycle.addObserver(DeepLinkHandler())
        setContent {
            MurmurApp(
                settingsRepository = settingsRepository,
                playbackController = playbackController,
                musicLibraryRepository = musicLibraryRepository,
                musicPlaylistRepository = musicPlaylistRepository,
                audiobookLibraryRepository = audiobookLibraryRepository,
                documentLibraryRepository = documentLibraryRepository,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data
        
        // Handle deep links
        if (data != null && data.scheme == "murmur") {
            val host = data.host
            val path = data.path
            when (host) {
                "continue" -> navController?.navigate("continue")
                "music" -> navController?.navigate("music")
                "audiobooks" -> navController?.navigate("audiobooks")
                "documents" -> navController?.navigate("documents")
                "player" -> navController?.navigate("now-playing")
                "reader" -> {
                    val documentId = data.getPathSegments().getOrNull(1)
                    documentId?.let { navController?.navigate("reader/$it") }
                }
            }
            return
        }

        // Handle Open With / Share Into
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND) {
            val uris = mutableListOf<Uri>()
            if (data != null) {
                uris.add(data)
            }
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { uris.add(it) }
            
            if (uris.isNotEmpty()) {
                val supported = uris.filter { uri ->
                    val mime = contentResolver.getType(uri) ?: ""
                    mime.startsWith("audio/") || mime.startsWith("text/") || 
                    mime == "application/epub+zip" || mime == "application/pdf"
                }
                if (supported.isNotEmpty()) {
                    // Navigate to documents screen, the library will handle the import
                    navController?.navigate("documents")
                }
            }
        }
    }

    override fun onDestroy() {
        playbackController.release()
        ttsGenerator.shutdown()
        super.onDestroy()
    }

    private inner class DeepLinkHandler : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            handleIntent(intent)
        }
    }
}