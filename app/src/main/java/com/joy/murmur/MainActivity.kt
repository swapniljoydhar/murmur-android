package com.joy.murmur

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.joy.murmur.data.audiobooks.RoomAudiobookLibraryRepository
import com.joy.murmur.data.documents.RoomDocumentLibraryRepository
import com.joy.murmur.data.music.MediaStoreMusicLibraryRepository
import com.joy.murmur.data.music.RoomMusicPlaylistRepository
import com.joy.murmur.data.playback.AudiobookStore
import com.joy.murmur.data.playback.MusicCatalogStore
import com.joy.murmur.data.playback.MusicPlaylistStore
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.data.settings.settingsDataStore
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.domain.audiobooks.AudiobookLibraryRepository
import com.joy.murmur.domain.documents.DocumentLibraryRepository
import com.joy.murmur.domain.music.MusicLibraryRepository
import com.joy.murmur.domain.music.MusicPlaylistRepository
import com.joy.murmur.domain.settings.SettingsRepository
import com.joy.murmur.playback.PlaybackContentType
import com.joy.murmur.playback.PlaybackController
import com.joy.murmur.playback.SelectedAudio
import com.joy.murmur.playback.SourceInfoExtractor
import com.joy.murmur.playback.toStableMediaId
import com.joy.murmur.ui.MurmurApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal const val MAX_SHARED_URIS = 100

internal sealed interface IncomingIntentAction {
    data class Navigate(val route: String) : IncomingIntentAction
    data class OpenSharedContent(
        val audioUris: List<Uri>,
        val documentUris: List<Uri>,
    ) : IncomingIntentAction
}

internal fun parseIncomingIntent(intent: Intent?, mimeResolver: (Uri) -> String?): IncomingIntentAction? {
    if (intent == null) return null
    val action = intent.action
    val data = intent.data

    if (data != null && data.scheme == "murmur") {
        val route = when (data.host) {
            "continue" -> "continue"
            "music" -> "music"
            "audiobooks" -> "audiobooks"
            "documents" -> "documents"
            "player" -> "now-playing"
            "reader" -> {
                val segments = data.pathSegments
                val documentId = when {
                    segments.size >= 2 && segments[0] == "document" -> segments[1]
                    segments.size == 1 -> segments[0]
                    else -> null
                }
                documentId?.takeIf(String::isNotBlank)?.let { "reader/${Uri.encode(it)}" }
            }
            else -> null
        }
        return route?.let(IncomingIntentAction::Navigate)
    }

    if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
        val candidateUris = buildList {
            if (data != null) add(data)
            IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let(::addAll)
        }.distinctBy(Uri::toString).take(MAX_SHARED_URIS)

        val audioUris = mutableListOf<Uri>()
        val documentUris = mutableListOf<Uri>()

        candidateUris.forEach { uri ->
            if (uri.scheme != ContentResolver.SCHEME_CONTENT && uri.scheme != ContentResolver.SCHEME_FILE) {
                return@forEach
            }
            val mime = (mimeResolver(uri) ?: intent.type ?: "").lowercase()
            val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
            when {
                mime.startsWith("audio/") || ext in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav") -> {
                    audioUris += uri
                }
                mime.startsWith("text/") ||
                    mime == "application/epub+zip" ||
                    mime == "application/pdf" ||
                    ext in setOf("txt", "md", "markdown", "html", "htm", "epub", "pdf") -> {
                    documentUris += uri
                }
            }
        }

        if (audioUris.isNotEmpty() || documentUris.isNotEmpty()) {
            return IncomingIntentAction.OpenSharedContent(audioUris = audioUris, documentUris = documentUris)
        }
    }

    return null
}

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
    private val playbackController: PlaybackController by lazy {
        PlaybackController(applicationContext, PlaybackStore(PlaybackDatabase.getInstance(applicationContext)), sourceInfoExtractor)
    }
    private val musicLibraryRepository: MusicLibraryRepository by lazy {
        MediaStoreMusicLibraryRepository(
            applicationContext,
            MusicCatalogStore(PlaybackDatabase.getInstance(applicationContext)),
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

    private val mutablePendingRoute = MutableStateFlow<String?>(null)
    internal val pendingNavigationRoute: StateFlow<String?> = mutablePendingRoute.asStateFlow()
    private val sharedImportMutex = Mutex()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            handleIntent(intent)
        }
        setContent {
            MurmurApp(
                settingsRepository = settingsRepository,
                playbackController = playbackController,
                musicLibraryRepository = musicLibraryRepository,
                musicPlaylistRepository = musicPlaylistRepository,
                audiobookLibraryRepository = audiobookLibraryRepository,
                documentLibraryRepository = documentLibraryRepository,
                pendingRouteFlow = pendingNavigationRoute,
                onRouteConsumed = ::consumePendingRoute,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    internal fun consumePendingRoute() {
        mutablePendingRoute.value = null
    }

    private fun handleIntent(intent: Intent?) {
        val parsed = parseIncomingIntent(intent) { uri ->
            runCatching { contentResolver.getType(uri) }.getOrNull()
        } ?: return

        when (parsed) {
            is IncomingIntentAction.Navigate -> {
                mutablePendingRoute.value = parsed.route
            }
            is IncomingIntentAction.OpenSharedContent -> {
                lifecycleScope.launch {
                    sharedImportMutex.withLock {
                        withContext(Dispatchers.IO) {
                            parsed.documentUris.forEach { uri -> tryPersistReadPermission(uri) }
                            parsed.audioUris.forEach { uri -> tryPersistReadPermission(uri) }

                            if (parsed.documentUris.isNotEmpty()) {
                                runCatching { documentLibraryRepository.addDocuments(parsed.documentUris) }
                            }
                            if (parsed.audioUris.isNotEmpty()) {
                                runCatching { musicLibraryRepository.importAudioUris(parsed.audioUris, "Shared audio") }
                                val selected = parsed.audioUris.map { uri ->
                                    SelectedAudio(
                                        id = uri.toStableMediaId(),
                                        uri = uri,
                                        title = resolveDisplayName(uri),
                                        contentType = PlaybackContentType.MUSIC,
                                    )
                                }
                                playbackController.playSelected(selected)
                            }
                        }
                        mutablePendingRoute.value = if (parsed.documentUris.isNotEmpty()) {
                            "documents"
                        } else {
                            "now-playing"
                        }
                    }
                }
            }
        }
    }

    private fun tryPersistReadPermission(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        cursor.getString(idx)?.substringBeforeLast('.')?.takeIf(String::isNotBlank)?.let { return it }
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.takeIf(String::isNotBlank)
            ?: "Shared audio"
    }

    override fun onDestroy() {
        playbackController.release()
        ttsGenerator.shutdown()
        super.onDestroy()
    }
}