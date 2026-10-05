package com.joy.murmur

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.joy.murmur.domain.music.MusicLibraryRepository
import com.joy.murmur.domain.music.MusicPlaylistRepository
import com.joy.murmur.domain.audiobooks.AudiobookLibraryRepository
import com.joy.murmur.domain.documents.DocumentLibraryRepository
import com.joy.murmur.domain.settings.SettingsRepository
import com.joy.murmur.playback.PlaybackController
import com.joy.murmur.ui.MurmurApp

class MainActivity : ComponentActivity() {
    private val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(applicationContext.settingsDataStore)
    }
    private val playbackController: PlaybackController by lazy {
        PlaybackController(applicationContext, PlaybackStore(PlaybackDatabase.getInstance(applicationContext)))
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}
