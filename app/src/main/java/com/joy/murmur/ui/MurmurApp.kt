package com.joy.murmur.ui

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.joy.murmur.domain.settings.AppearanceMode
import com.joy.murmur.domain.settings.SettingsRepository
import com.joy.murmur.MurmurIntentRequest
import com.joy.murmur.domain.music.MusicLibraryRepository
import com.joy.murmur.domain.music.MusicPermissionRequiredException
import com.joy.murmur.domain.music.MusicTrack
import com.joy.murmur.domain.music.MusicLibrarySnapshot
import com.joy.murmur.domain.music.MusicPlaylist
import com.joy.murmur.domain.music.MusicPlaylistRepository
import com.joy.murmur.domain.music.MusicPlaylistTrack
import com.joy.murmur.data.music.MusicCatalogRefreshScheduler
import com.joy.murmur.domain.audiobooks.Audiobook
import com.joy.murmur.domain.audiobooks.ChapterSource
import com.joy.murmur.domain.audiobooks.AudiobookLibraryRepository
import com.joy.murmur.domain.documents.DocumentLibraryRepository
import com.joy.murmur.domain.documents.ReaderDocument
import com.joy.murmur.domain.documents.PdfPage
import com.joy.murmur.domain.documents.DocumentSearchMatch
import com.joy.murmur.data.documents.TocEntry
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.data.speech.SpeechVoiceOption
import com.joy.murmur.data.speech.VoiceNetworkRequirement
import com.joy.murmur.playback.PlaybackContentType
import com.joy.murmur.playback.PlaybackController
import com.joy.murmur.playback.PlaybackSnapshot
import com.joy.murmur.playback.playbackPositionAtFraction
import com.joy.murmur.playback.playbackPositionFraction
import com.joy.murmur.playback.ReadAloudStatus
import com.joy.murmur.playback.parseReadAloudMediaId
import com.joy.murmur.playback.parseReadAloudStartMediaId
import com.joy.murmur.playback.SleepTimer
import com.joy.murmur.playback.SelectedAudio
import com.joy.murmur.playback.readAloudMediaId
import com.joy.murmur.playback.markdownForSpeech
import com.joy.murmur.playback.ReadAloudUtils.buildSpokenIndexMap
import com.joy.murmur.playback.ReadAloudUtils.mapTapOffsetToSegment
import com.joy.murmur.playback.ReadAloudUtils.buildInverseSpokenIndexMap
import com.joy.murmur.playback.toStableMediaId
import com.joy.murmur.playback.BackgroundPlaybackHealth
import com.joy.murmur.playback.OemDiagnosticState
import com.joy.murmur.playback.OemCompatibility
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.ui.theme.MurmurTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.serialization.json.Json

private fun createTtsSettingsIntent(): Intent {
    val intent = Intent("com.android.settings.TTS_SETTINGS")
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    return intent
}

private fun buildHighlightedText(
    text: String,
    sentenceIdx: Int,
    segmentMap: ReadAloudSegmentMapEntity,
    inverseSpokenIndexMap: IntArray?,
    highlightColor: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    val sentenceOffsets = Json.decodeFromString<List<Long>>(segmentMap.sentenceStartOffsetsJson)
    if (sentenceIdx >= sentenceOffsets.size - 1) return AnnotatedString(text) // -1 because last entry is end offset
    val spokenStart = sentenceOffsets[sentenceIdx].toInt()
    val spokenEnd = sentenceOffsets[sentenceIdx + 1].toInt()
    
    // Map spoken offsets to rendered (original) text offsets using inverse map
    val (renderedStart, renderedEnd) = if (inverseSpokenIndexMap != null && spokenStart < inverseSpokenIndexMap.size) {
        val start = inverseSpokenIndexMap[spokenStart].coerceIn(0, text.length)
        val end = if (spokenEnd < inverseSpokenIndexMap.size) {
            inverseSpokenIndexMap[spokenEnd].coerceIn(start, text.length)
        } else {
            text.length
        }
        start to end
    } else {
        // Fallback: use spoken offsets directly (for non-markdown or if inverse map unavailable)
        spokenStart.coerceIn(0, text.length) to spokenEnd.coerceIn(0, text.length)
    }
    
    val builder = AnnotatedString.Builder(text)
    if (renderedStart < renderedEnd) {
        builder.addStyle(
            SpanStyle(background = highlightColor),
            renderedStart,
            renderedEnd,
        )
    }
    return builder.toAnnotatedString()
}

/** Maps a parsed `murmur://` link onto a graph route. Unknown hosts are ignored rather than guessed. */
private fun deepLinkRoute(request: MurmurIntentRequest.DeepLink): String? = when (request.host) {
    "continue" -> Destination.Continue.route
    "music" -> Destination.Music.route
    "audiobooks" -> Destination.Audiobooks.route
    "documents" -> Destination.Documents.route
    "player" -> Destination.NowPlaying.route
    "reader" -> request.pathSegments
        .getOrNull(1)
        ?.takeIf { it.isNotBlank() && it.length <= 256 }
        ?.let { "reader/${Uri.encode(it)}" }
    else -> null
}

private enum class SharedContentKind { DOCUMENT, AUDIO, UNSUPPORTED }

private val sharedDocumentMimeTypes = setOf(
    "text/plain", "text/markdown", "text/x-markdown", "text/html", "application/epub+zip", "application/pdf",
)
private val sharedDocumentExtensions = setOf("txt", "md", "markdown", "html", "htm", "epub", "pdf")
private val sharedAudioExtensions = setOf("mp3", "m4a", "m4b", "aac", "flac", "ogg", "oga", "opus", "wav", "mp4", "amr")

private data class SharedContentImport(
    val documentIds: List<String>,
    val documentsPartial: Boolean,
    val audio: List<SelectedAudio>,
    val skippedForAccess: Int,
    val unsupported: Int,
    val documentFailure: Boolean,
)

private fun classifySharedUri(resolver: ContentResolver, uri: Uri): SharedContentKind {
    val mime = runCatching { resolver.getType(uri) }.getOrNull()?.lowercase().orEmpty()
    val extension = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
    return when {
        mime in sharedDocumentMimeTypes || extension in sharedDocumentExtensions -> SharedContentKind.DOCUMENT
        mime.startsWith("audio/") || extension in sharedAudioExtensions -> SharedContentKind.AUDIO
        else -> SharedContentKind.UNSUPPORTED
    }
}

/**
 * Turns shared URIs into real library entries or a play queue. Runs off the main thread because it
 * touches [ContentResolver] (type lookups, display names, persistable grants) for every URI.
 */
private suspend fun importSharedContent(
    context: Context,
    uris: List<Uri>,
    documentLibraryRepository: DocumentLibraryRepository?,
): SharedContentImport = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    var skippedForAccess = 0
    val retained = uris.filter { uri ->
        val granted = retainReadAccess(resolver, uri)
        if (!granted) skippedForAccess++
        granted
    }
    var unsupported = 0
    val documents = mutableListOf<Uri>()
    val audio = mutableListOf<Uri>()
    retained.forEach { uri ->
        when (classifySharedUri(resolver, uri)) {
            SharedContentKind.DOCUMENT -> documents += uri
            SharedContentKind.AUDIO -> audio += uri
            SharedContentKind.UNSUPPORTED -> unsupported++
        }
    }

    val audioItems = audio.map { uri ->
        SelectedAudio(
            id = uri.toStableMediaId(),
            uri = uri,
            title = queryDisplayName(resolver, uri),
            contentType = PlaybackContentType.MUSIC,
        )
    }

    if (documents.isEmpty()) {
        return@withContext SharedContentImport(
            documentIds = emptyList(),
            documentsPartial = false,
            audio = audioItems,
            skippedForAccess = skippedForAccess,
            unsupported = unsupported,
            documentFailure = false,
        )
    }
    val repository = documentLibraryRepository
        ?: return@withContext SharedContentImport(
            documentIds = emptyList(),
            documentsPartial = false,
            audio = audioItems,
            skippedForAccess = skippedForAccess,
            unsupported = unsupported,
            documentFailure = true,
        )
    val added = runCatching { repository.addDocuments(documents) }.getOrNull()
    if (added == null || added <= 0) {
        return@withContext SharedContentImport(
            documentIds = emptyList(),
            documentsPartial = false,
            audio = audioItems,
            skippedForAccess = skippedForAccess,
            unsupported = unsupported,
            documentFailure = true,
        )
    }
    SharedContentImport(
        documentIds = documents.map { it.toStableMediaId() },
        // addDocuments silently skips formats it cannot represent, so only a full match is a safe
        // licence to open the reader directly on a single shared file.
        documentsPartial = added < documents.size,
        audio = audioItems,
        skippedForAccess = skippedForAccess,
        unsupported = unsupported,
        documentFailure = false,
    )
}

private enum class Destination(val route: String, val title: String, val description: String) {
    Continue("continue", "Continue", "Pick up with local listening and reading"),
    Music("music", "Music", "Browse music on this device"),
    Audiobooks("audiobooks", "Audiobooks", "Keep your place in every book"),
    Documents("documents", "Read & Listen", "Read documents and listen aloud in one place"),
    ReadAloud("read-aloud", "Read Aloud", "Listen to a document"),
    Settings("settings", "Settings", "Appearance and app preferences"),
    VoiceSettings("settings/voice", "Voice", "Read Aloud voice settings"),
    OemFix("oem-fix", "Fix Background Playback", "Resolve background playback restrictions"),
    NowPlaying("now-playing", "Now Playing", "Playback controls and queue");

    companion object {
        fun fromRoute(route: String): Destination? = entries.firstOrNull { it.route == route }
    }
}

private enum class SettingsTab(val label: String) {
    APPEARANCE("Appearance"),
    VOICE("Read Aloud Voice"),
    STORAGE("Storage"),
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MurmurApp(
    settingsRepository: SettingsRepository,
    playbackController: PlaybackController? = null,
    musicLibraryRepository: MusicLibraryRepository? = null,
    musicPlaylistRepository: MusicPlaylistRepository? = null,
    audiobookLibraryRepository: AudiobookLibraryRepository? = null,
    documentLibraryRepository: DocumentLibraryRepository? = null,
    request: MurmurIntentRequest? = null,
    onRequestHandled: () -> Unit = {},
) {
    LaunchedEffect(playbackController) {
        while (playbackController != null) {
            delay(1_000L)
            playbackController.refreshPosition()
        }
    }
    val appearanceMode by settingsRepository.appearanceMode.collectAsState(initial = AppearanceMode.SYSTEM)
    val playback by (playbackController?.snapshot ?: androidx.compose.runtime.remember {
        kotlinx.coroutines.flow.MutableStateFlow(PlaybackSnapshot())
    }).collectAsState()
    MurmurTheme(appearanceMode = appearanceMode) {
        val navController = rememberNavController()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route ?: Destination.Continue.route
        val title = Destination.fromRoute(currentRoute)?.title ?: "Murmur"
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }

        // Deep links, app shortcuts, "Open with" and Sharesheet hand-offs are handled here, where the
        // controller is actually attached to the NavHost. The request is consumed first so a
        // recomposition (or a failed import) can never replay it.
        LaunchedEffect(request) {
            val activeRequest = request ?: return@LaunchedEffect
            onRequestHandled()
            when (activeRequest) {
                is MurmurIntentRequest.DeepLink -> {
                    val route = deepLinkRoute(activeRequest) ?: return@LaunchedEffect
                    navController.navigate(route) { launchSingleTop = true }
                }
                is MurmurIntentRequest.OpenContent -> {
                    val imported = importSharedContent(context, activeRequest.uris, documentLibraryRepository)
                    val notes = mutableListOf<String>()
                    if (imported.skippedForAccess > 0) {
                        notes += "${imported.skippedForAccess} file(s) couldn't be opened because Murmur couldn't keep access to them."
                    }
                    if (imported.unsupported > 0) {
                        notes += "${imported.unsupported} file(s) weren't a supported audio or document format."
                    }
                    if (imported.audio.isNotEmpty()) {
                        val controller = playbackController
                        if (controller == null) {
                            notes += "Playback isn't available right now."
                        } else {
                            controller.playSelected(imported.audio)
                            navController.navigate(Destination.NowPlaying.route) { launchSingleTop = true }
                        }
                        if (imported.documentIds.isNotEmpty()) {
                            notes += "${imported.documentIds.size} document(s) added to Read & Listen."
                        }
                    } else if (imported.documentIds.isNotEmpty()) {
                        if (imported.documentsPartial) {
                            notes += "Some shared documents were added to Read & Listen."
                        }
                        if (imported.documentIds.size == 1 && !imported.documentsPartial) {
                            navController.navigate("reader/${Uri.encode(imported.documentIds.single())}") { launchSingleTop = true }
                        } else {
                            navController.navigate(Destination.Documents.route) { launchSingleTop = true }
                        }
                    } else if (imported.documentFailure && notes.isEmpty()) {
                        notes += "Murmur couldn't add the shared document. Open the document library and choose the file again."
                    }
                    if (notes.isNotEmpty()) snackbarHostState.showSnackbar(notes.joinToString(" "))
                }
            }
        }

        Scaffold(
            topBar = {
                MurmurTopBar(
                    title = title,
                    showBack = currentRoute != Destination.Continue.route,
                    showSettings = currentRoute != Destination.Settings.route && currentRoute != Destination.NowPlaying.route,
                    onBack = { navController.popBackStack() },
                    onSettings = { navController.navigate(Destination.Settings.route) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (playback.current != null && currentRoute != Destination.NowPlaying.route) {
                    MiniPlayer(
                        snapshot = playback,
                        onOpen = { navController.navigate(Destination.NowPlaying.route) },
                        onToggle = { playbackController?.togglePlayPause() },
                    )
                }
            },
        ) { contentPadding ->
            MurmurNavigation(
                navController = navController,
                contentPadding = contentPadding,
                settingsRepository = settingsRepository,
                appearanceMode = appearanceMode,
                playbackController = playbackController,
                musicLibraryRepository = musicLibraryRepository,
                musicPlaylistRepository = musicPlaylistRepository,
                audiobookLibraryRepository = audiobookLibraryRepository,
                documentLibraryRepository = documentLibraryRepository,
                playback = playback,
            )
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun MurmurTopBar(title: String, showBack: Boolean, showSettings: Boolean, onBack: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { if (showBack) TextButton(onClick = onBack) { Text("Back") } },
        actions = { if (showSettings) TextButton(onClick = onSettings) { Text("Settings") } },
    )
}

@Composable
private fun MurmurNavigation(
    navController: NavHostController,
    contentPadding: PaddingValues,
    settingsRepository: SettingsRepository,
    appearanceMode: AppearanceMode,
    playbackController: PlaybackController?,
    musicLibraryRepository: MusicLibraryRepository?,
    musicPlaylistRepository: MusicPlaylistRepository?,
    audiobookLibraryRepository: AudiobookLibraryRepository?,
    documentLibraryRepository: DocumentLibraryRepository?,
    playback: PlaybackSnapshot,
) {
    NavHost(navController, startDestination = Destination.Continue.route, modifier = Modifier.padding(contentPadding)) {
        composable(Destination.Continue.route) {
            ContinueScreen { destination -> navController.navigate(destination.route) }
        }
        composable(Destination.Music.route) {
            MusicLibraryScreen(musicLibraryRepository, musicPlaylistRepository, playbackController, settingsRepository)
        }
        composable(Destination.Audiobooks.route) {
            AudiobookLibraryScreen(audiobookLibraryRepository, playbackController)
        }
        composable(Destination.Documents.route) {
            DocumentLibraryScreen(documentLibraryRepository) { document ->
                navController.navigate("reader/${Uri.encode(document.id)}")
            }
        }
        composable(Destination.ReadAloud.route) {
            DocumentLibraryScreen(documentLibraryRepository) { document ->
                navController.navigate("reader/${Uri.encode(document.id)}")
            }
        }
        composable("reader/{documentId}", arguments = listOf(navArgument("documentId") { type = NavType.StringType })) { entry ->
            ReaderScreen(entry.arguments?.getString("documentId").orEmpty(), documentLibraryRepository, playbackController, settingsRepository)
        }
        composable(Destination.Settings.route) {
            SettingsScreen(settingsRepository, playback.current != null)
        }
        composable(Destination.OemFix.route) {
            val fixLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
                navController.popBackStack()
            }
            val diagnostic = OemCompatibility(LocalContext.current).diagnosticState.collectAsState(initial = OemDiagnosticState(
                health = BackgroundPlaybackHealth.UNKNOWN,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.SDK_INT,
                oemSkin = null,
                isBatteryOptimizationEnabled = false,
                isPowerSaverActive = false,
                isAppRestricted = false,
                isForegroundServiceActive = false,
                mediaSessionState = 0,
                playerState = 0,
                notificationState = false,
                recentInterruption = false,
            ))
            OemFixScreen(
                diagnostic = diagnostic.value,
                oemCompatibility = OemCompatibility(LocalContext.current),
                onDismiss = { navController.popBackStack() },
                onOpenSettings = { intent ->
                    fixLauncher.launch(intent)
                },
            )
        }
        composable(Destination.NowPlaying.route) {
            NowPlayingScreen(
                snapshot = playback,
                controller = playbackController,
                audiobookLibraryRepository = audiobookLibraryRepository,
                onReturnToDocument = {
                    val documentId = playback.current?.id?.let { mediaId ->
                        parseReadAloudMediaId(mediaId)?.documentId
                            ?: parseReadAloudStartMediaId(mediaId)?.documentId
                    }
                    if (documentId != null) navController.navigate("reader/${Uri.encode(documentId)}")
                    else navController.navigate(Destination.Documents.route)
                },
                onOpenAudiobookLibrary = { navController.navigate(Destination.Audiobooks.route) },
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
private fun MusicLibraryScreen(
    musicLibraryRepository: MusicLibraryRepository?,
    musicPlaylistRepository: MusicPlaylistRepository?,
    playbackController: PlaybackController?,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var library by remember { mutableStateOf<MusicLibrarySnapshot?>(null) }
    var hasAudioAccess by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(MusicBrowseTab.SONGS) }
    var playlists by remember { mutableStateOf<List<MusicPlaylist>>(emptyList()) }
    var selectedPlaylistId by remember { mutableStateOf<String?>(null) }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    var playlistNameError by remember { mutableStateOf<String?>(null) }
    var pendingTrackToAdd by remember { mutableStateOf<MusicTrack?>(null) }
    var showAddTrackDialog by remember { mutableStateOf(false) }
    var confirmDeletePlaylist by remember { mutableStateOf(false) }
    var editingPlaylistId by remember { mutableStateOf<String?>(null) }
    val backgroundRefreshEnabled by settingsRepository.backgroundMusicRefresh.collectAsState(initial = false)

    LaunchedEffect(musicLibraryRepository) {
        musicLibraryRepository?.observeLibrary()?.collect { library = it }
    }
    LaunchedEffect(musicPlaylistRepository) {
        musicPlaylistRepository?.observePlaylists()?.collect { playlists = it }
    }
    LaunchedEffect(backgroundRefreshEnabled, library?.hasScanned, hasAudioAccess) {
        if (backgroundRefreshEnabled && library?.hasScanned == true && hasAudioAccess) {
            MusicCatalogRefreshScheduler.schedule(context)
        } else {
            MusicCatalogRefreshScheduler.cancel(context)
        }
    }

    val loadLibrary: suspend () -> Unit = {
        loading = true
        error = null
        try {
            musicLibraryRepository?.refresh()
            permissionDenied = false
        } catch (_: MusicPermissionRequiredException) {
            permissionDenied = true
            if (library?.hasScanned != true) library = null
        } catch (_: SecurityException) {
            permissionDenied = true
            if (library?.hasScanned != true) library = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "Murmur couldn't read the music library. Try again or choose files directly."
        } finally {
            loading = false
        }
    }
    LaunchedEffect(musicLibraryRepository, hasAudioAccess, library?.hasScanned) {
        if (hasAudioAccess && library?.hasScanned == true) {
            musicLibraryRepository?.observeMediaChanges()?.debounce(750L)?.collect { changedIds ->
                try {
                    if (changedIds == null) loadLibrary()
                    else changedIds.forEach { musicLibraryRepository.refreshChangedItem(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    loadLibrary()
                }
            }
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioAccess = granted
        if (granted) scope.launch { loadLibrary() } else permissionDenied = true
    }
    val pickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val retainedUris = uris.filter { retainReadAccess(context.contentResolver, it) }
        val selected = retainedUris.map { uri ->
            SelectedAudio(uri.toStableMediaId(), uri, queryDisplayName(context.contentResolver, uri), PlaybackContentType.MUSIC)
        }
        if (retainedUris.size < uris.size) {
            error = "Skipped ${uris.size - retainedUris.size} files because Murmur couldn't retain access. Choose them again from a provider that supports ongoing access."
        }
        if (selected.isNotEmpty()) playbackController?.playSelected(selected)
    }

    fun requestLibrary() {
        if (musicLibraryRepository == null) {
            error = "Music library discovery isn't available right now. You can still choose audio files."
        } else if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            hasAudioAccess = true
            scope.launch { loadLibrary() }
        } else {
            permissionLauncher.launch(permission)
        }
    }

    fun savePlaylist() {
        val repository = musicPlaylistRepository ?: return
        scope.launch {
            try {
                val editingId = editingPlaylistId
                if (editingId == null) {
                    val created = repository.createPlaylist(playlistName)
                    selectedPlaylistId = created.id
                    pendingTrackToAdd?.let { track ->
                        repository.addTrack(
                            created.id,
                            MusicPlaylistTrack(track.id, track.uri, track.title, track.artist, track.album, track.durationMs),
                        )
                    }
                } else {
                    repository.renamePlaylist(editingId, playlistName)
                }
                playlistName = ""
                playlistNameError = null
                editingPlaylistId = null
                showPlaylistDialog = false
                pendingTrackToAdd = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: IllegalArgumentException) {
                playlistNameError = failure.message ?: "Enter a playlist name."
            } catch (_: Exception) {
                playlistNameError = "Murmur couldn't save that playlist. Try again."
            }
        }
    }

    fun beginCreatePlaylist() {
        editingPlaylistId = null
        playlistName = ""
        playlistNameError = null
        showPlaylistDialog = true
    }

    val tracks = library?.takeIf(MusicLibrarySnapshot::hasScanned)?.tracks
    val allTracks = tracks.orEmpty()
    val matchingTracks = allTracks.filter { track ->
        query.isBlank() || listOfNotNull(track.title, track.artist, track.album, track.relativePath).any { it.contains(query, ignoreCase = true) }
    }
    val artistGroups = matchingTracks.filter { it.artist != null }.groupBy { it.artist!! }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val albumGroups = matchingTracks.filter { it.album != null }.groupBy { it.album!! }.toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val folderGroups = matchingTracks.filter { it.relativePath != null }.groupBy { it.relativePath!! }.toSortedMap(String.CASE_INSENSITIVE_ORDER)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Your music", style = MaterialTheme.typography.headlineSmall)
            Text("Music on this device, kept on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (library?.hasScanned == true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Refresh in background", style = MaterialTheme.typography.titleSmall)
                    Text("Daily, when battery allows. No network.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = backgroundRefreshEnabled,
                    enabled = hasAudioAccess,
                    onCheckedChange = { enabled -> scope.launch { settingsRepository.setBackgroundMusicRefresh(enabled) } },
                )
            }
        }
        if (tracks != null && selectedTab != MusicBrowseTab.PLAYLISTS) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search songs, artists, albums") },
            )
        }
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal, containerColor = MaterialTheme.colorScheme.background) {
            MusicBrowseTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab; selectedPlaylistId = null },
                    text = { Text(tab.label) },
                )
            }
        }

        when {
            selectedTab == MusicBrowseTab.PLAYLISTS -> {
                val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
                if (selectedPlaylist == null) {
                    PlaylistLibraryContent(playlists, onCreate = ::beginCreatePlaylist, onSelect = { selectedPlaylistId = it })
                } else {
                    PlaylistDetailContent(
                        playlist = selectedPlaylist,
                        onBack = { selectedPlaylistId = null },
                        onPlay = { playlist ->
                            playbackController?.playSelected(playlist.tracks.map { SelectedAudio(it.trackId, it.uri, it.title, PlaybackContentType.MUSIC) })
                        },
                        onRemove = { trackId -> scope.launch { musicPlaylistRepository?.removeTrack(selectedPlaylist.id, trackId) } },
                        onMoveTrack = { from, to ->
                            val reordered = selectedPlaylist.tracks.toMutableList().apply {
                                add(to, removeAt(from))
                            }
                            scope.launch {
                                musicPlaylistRepository?.reorderTracks(selectedPlaylist.id, reordered.map { it.trackId })
                            }
                        },
                        onRename = {
                            editingPlaylistId = selectedPlaylist.id
                            playlistName = selectedPlaylist.name
                            playlistNameError = null
                            showPlaylistDialog = true
                        },
                        onDelete = { confirmDeletePlaylist = true },
                        playbackController = playbackController,
                    )
                }
            }
            loading -> Text("Looking for music…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            tracks == null && error != null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                Button(onClick = ::requestLibrary) { Text("Try again") }
                TextButton(onClick = { pickerLauncher.launch(arrayOf("audio/*")) }) { Text("Choose audio files") }
            }
            tracks == null -> {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Find music on this phone", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (permissionDenied) "Audio access wasn't granted. You can allow it to browse the library, or choose individual files instead."
                            else "Scan the device's indexed audio library, or choose individual files. Murmur doesn't upload your music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = ::requestLibrary) { Text(if (permissionDenied) "Allow audio access" else "Scan device") }
                        TextButton(onClick = { pickerLauncher.launch(arrayOf("audio/*")) }) { Text("Choose audio files") }
                    }
                }
            }
            allTracks.isEmpty() -> {
                if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                EmptyMusicLibraryCard(onScan = ::requestLibrary, onChooseFiles = { pickerLauncher.launch(arrayOf("audio/*")) })
            }
            else -> when (selectedTab) {
                MusicBrowseTab.SONGS -> {
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    Text("${matchingTracks.size} songs", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (matchingTracks.isEmpty()) {
                        Text("No songs match that search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    matchingTracks.forEach { track ->
                        MusicTrackRow(track, onPlay = {
                            playbackController?.playSelected(listOf(track.toSelectedAudio()))
                        }, onQueue = {
                            playbackController?.addToQueue(listOf(track.toSelectedAudio()))
                        }, onAddToPlaylist = {
                            pendingTrackToAdd = track
                            showAddTrackDialog = true
                        })
                    }
                }
                MusicBrowseTab.ALBUMS -> {
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    if (albumGroups.isEmpty()) Text("No album information is available in this library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    albumGroups.forEach { (album, albumTracks) ->
                        MusicGroupRow(album, "${albumTracks.size} songs", onPlay = {
                            playbackController?.playSelected(albumTracks.map(MusicTrack::toSelectedAudio))
                        })
                    }
                }
                MusicBrowseTab.ARTISTS -> {
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    if (artistGroups.isEmpty()) Text("No artist information is available in this library.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    artistGroups.forEach { (artist, artistTracks) ->
                        MusicGroupRow(artist, "${artistTracks.size} songs", onPlay = {
                            playbackController?.playSelected(artistTracks.map(MusicTrack::toSelectedAudio))
                        })
                    }
                }
                MusicBrowseTab.FOLDERS -> {
                    if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    if (folderGroups.isEmpty()) Text("Folder locations are not available for these tracks.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    folderGroups.forEach { (folder, folderTracks) ->
                        MusicGroupRow(folder.trimEnd('/'), "${folderTracks.size} songs", onPlay = {
                            playbackController?.playSelected(folderTracks.map(MusicTrack::toSelectedAudio))
                        })
                    }
                }
                MusicBrowseTab.PLAYLISTS -> Unit
            }
        }

        if (tracks != null && tracks!!.isNotEmpty()) {
            TextButton(onClick = ::requestLibrary, enabled = !loading) { Text("Refresh library") }
        }
    }

    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false; editingPlaylistId = null; playlistNameError = null },
            title = { Text(if (editingPlaylistId == null) "New playlist" else "Rename playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it; playlistNameError = null },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        isError = playlistNameError != null,
                    )
                    playlistNameError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = ::savePlaylist) { Text("Save") } },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false; editingPlaylistId = null }) { Text("Cancel") } },
        )
    }

    if (showAddTrackDialog) pendingTrackToAdd?.let { track ->
        AlertDialog(
            onDismissRequest = { showAddTrackDialog = false; pendingTrackToAdd = null },
            title = { Text("Add to playlist") },
            text = {
                if (playlists.isEmpty()) {
                    Text("Create a playlist first. The selected song will be added to it.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        playlists.forEach { playlist ->
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        musicPlaylistRepository?.addTrack(
                                            playlist.id,
                                            MusicPlaylistTrack(track.id, track.uri, track.title, track.artist, track.album, track.durationMs),
                                        )
                                    }
                                    showAddTrackDialog = false
                                    pendingTrackToAdd = null
                                },
                            ) { Text(playlist.name) }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddTrackDialog = false; beginCreatePlaylist() }) { Text("New playlist") }
            },
            dismissButton = { TextButton(onClick = { showAddTrackDialog = false; pendingTrackToAdd = null }) { Text("Cancel") } },
        )
    }

    val playlistBeingDeleted = playlists.firstOrNull { it.id == selectedPlaylistId }
    if (confirmDeletePlaylist && playlistBeingDeleted != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePlaylist = false },
            title = { Text("Delete playlist?") },
            text = { Text("${playlistBeingDeleted.name} and its saved track list will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { musicPlaylistRepository?.deletePlaylist(playlistBeingDeleted.id) }
                    selectedPlaylistId = null
                    confirmDeletePlaylist = false
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeletePlaylist = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyMusicLibraryCard(onScan: () -> Unit, onChooseFiles: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("♫", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
            Text("No music found", style = MaterialTheme.typography.titleLarge)
            Text(
                "There are no indexed music files on this device yet. You can scan again or select audio files yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScan) { Text("Scan again") }
            TextButton(onClick = onChooseFiles) { Text("Choose audio files") }
        }
    }
}

@Composable
private fun MusicTrackRow(
    track: MusicTrack,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("♫", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                Text(track.artist ?: track.album ?: "Details not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            TextButton(onClick = onQueue) { Text("Queue") }
            TextButton(onClick = onAddToPlaylist) { Text("Playlist") }
        }
    }
}

@Composable
private fun PlaylistLibraryContent(
    playlists: List<MusicPlaylist>,
    onCreate: () -> Unit,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedCard(onClick = onCreate, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("+", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Text("Add new playlist", style = MaterialTheme.typography.titleMedium)
            }
        }
        Text("Your playlists", style = MaterialTheme.typography.titleLarge)
        if (playlists.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("No playlists yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Create a playlist, then add songs from your local library.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreate) { Text("Create playlist") }
                }
            }
        } else {
            playlists.forEach { playlist ->
                OutlinedCard(onClick = { onSelect(playlist.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("♫", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(playlist.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${playlist.tracks.size} ${if (playlist.tracks.size == 1) "song" else "songs"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: MusicPlaylist,
    onBack: () -> Unit,
    onPlay: (MusicPlaylist) -> Unit,
    onRemove: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    playbackController: PlaybackController?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Playlists") }
        Text(playlist.name, style = MaterialTheme.typography.headlineSmall)
        Text("${playlist.tracks.size} songs", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onPlay(playlist) }, enabled = playlist.tracks.isNotEmpty() && playbackController != null) { Text("Play") }
            TextButton(onClick = onRename) { Text("Rename") }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
        if (playlist.tracks.isEmpty()) {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "This playlist is empty. Use Playlist on a song in Songs to add it here.",
                    modifier = Modifier.padding(20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            playlist.tracks.forEachIndexed { index, track ->
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            playbackController?.playSelected(listOf(SelectedAudio(track.trackId, track.uri, track.title, PlaybackContentType.MUSIC)))
                        }.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(track.artist ?: track.album ?: "Details not available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row {
                                TextButton(onClick = { onMoveTrack(index, index - 1) }, enabled = index > 0) { Text("Move up") }
                                TextButton(onClick = { onMoveTrack(index, index + 1) }, enabled = index < playlist.tracks.lastIndex) { Text("Move down") }
                            }
                            TextButton(onClick = { onRemove(track.trackId) }) { Text("Remove") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicGroupRow(title: String, subtitle: String, onPlay: () -> Unit) {
    OutlinedCard(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Play", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private enum class MusicBrowseTab(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
}

private fun MusicTrack.toSelectedAudio() = SelectedAudio(id, uri, title, PlaybackContentType.MUSIC, artist)

private fun retainReadAccess(resolver: ContentResolver, uri: Uri): Boolean {
    if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) return true
    return runCatching {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }.getOrDefault(false)
}

@Composable
private fun DocumentLibraryScreen(repository: DocumentLibraryRepository?, onOpen: (ReaderDocument) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf<List<ReaderDocument>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    LaunchedEffect(repository) { repository?.observeDocuments()?.collect { documents = it } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            adding = true
            message = null
            try {
                val retainedUris = uris.filter { retainReadAccess(context.contentResolver, it) }
                val added = repository?.addDocuments(retainedUris) ?: 0
                message = when {
                    retainedUris.size < uris.size -> "Added $added files. ${uris.size - retainedUris.size} couldn't be added because Murmur couldn't retain access. Choose those files again from a provider that supports ongoing access."
                    added < retainedUris.size -> "Added $added supported files. Murmur supports TXT, Markdown, HTML, EPUB, and PDF text extraction on Android 15 or newer."
                    else -> null
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message = "Murmur couldn't open those files. Check that they are still available and choose them again."
            } finally {
                adding = false
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Read & Listen", style = MaterialTheme.typography.headlineSmall)
        Text("Read your documents here; listen from the open reader.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { picker.launch(arrayOf("text/plain", "text/markdown", "text/x-markdown", "text/html", "application/epub+zip", "application/pdf")) }, enabled = repository != null && !adding) {
            Text(if (adding) "Adding…" else "Add documents")
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (documents.isEmpty()) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("No documents yet", style = MaterialTheme.typography.titleLarge)
                    Text("Choose a TXT, Markdown, HTML, EPUB, or PDF file to read. PDF text extraction requires Android 15 or newer; Murmur stores access and reading place, not a copy of the file.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else documents.forEach { document ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(document.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (document.positionChars > 0L) "Continue reading" else document.mimeType.substringAfter('/').uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { onOpen(document) }) { Text("Open") }
                    TextButton(onClick = { scope.launch { repository?.removeDocument(document.id) } }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun ReaderScreen(
    documentId: String,
    repository: DocumentLibraryRepository?,
    playbackController: PlaybackController?,
    settingsRepository: SettingsRepository,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val speechGenerator = remember(context) { SystemTtsSpeechGenerator(context) }
    DisposableEffect(speechGenerator) { onDispose { speechGenerator.shutdown() } }
    val strictOffline by settingsRepository.strictOfflineSpeech.collectAsState(initial = true)
    val readerTextScale by settingsRepository.readerTextScale.collectAsState(initial = 1f)
    val selectedSpeechVoice by settingsRepository.selectedSpeechVoice.collectAsState(initial = null)
    val readAloudFailure by ReadAloudStatus.failure.collectAsState()
    var documents by remember { mutableStateOf<List<ReaderDocument>>(emptyList()) }
    var position by remember(documentId) { mutableStateOf<Long?>(null) }
    var page by remember { mutableStateOf("") }
    var pdfPage by remember(documentId) { mutableStateOf<PdfPage?>(null) }
    var searchExpanded by remember(documentId) { mutableStateOf(false) }
    var searchQuery by remember(documentId) { mutableStateOf("") }
    var searchMatches by remember(documentId) { mutableStateOf<List<DocumentSearchMatch>>(emptyList()) }
    var searchError by remember(documentId) { mutableStateOf<String?>(null) }
    var searching by remember(documentId) { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var checkingSource by remember(documentId) { mutableStateOf(true) }
    var sourceChanged by remember(documentId) { mutableStateOf(false) }
    var synthesizing by remember { mutableStateOf(false) }
    var speechError by remember { mutableStateOf<String?>(null) }
    var voiceNetworkRequirement by remember { mutableStateOf(VoiceNetworkRequirement.UNKNOWN) }
    var speechVoices by remember { mutableStateOf<List<SpeechVoiceOption>>(emptyList()) }
    var showVoicePicker by remember { mutableStateOf(false) }
    val previousPages = remember(documentId) { mutableStateListOf<Long>() }
    val document = documents.firstOrNull { it.id == documentId }
    val documentReadAloudFailure = readAloudFailure?.takeIf { it.documentId == documentId }
    var bookmarks by remember { mutableStateOf<List<com.joy.murmur.domain.documents.ReaderBookmark>>(emptyList()) }
    var epubContents by remember(documentId) { mutableStateOf<List<TocEntry>>(emptyList()) }
    var showEpubContents by remember(documentId) { mutableStateOf(false) }
    var loadingEpubContents by remember(documentId) { mutableStateOf(false) }
    var epubContentsError by remember(documentId) { mutableStateOf<String?>(null) }

    // Read Aloud highlighting state
    val playback by (playbackController?.snapshot ?: remember { kotlinx.coroutines.flow.MutableStateFlow(PlaybackSnapshot()) }).collectAsState()
    var currentSegmentMap by remember { mutableStateOf<ReadAloudSegmentMapEntity?>(null) }
    var spokenIndexMap by remember { mutableStateOf<IntArray?>(null) }
    var inverseSpokenIndexMap by remember { mutableStateOf<IntArray?>(null) }
    var currentSentenceIdx by remember { mutableStateOf<Int?>(null) }

    // Query segment map when Read Aloud is playing for this document
    LaunchedEffect(playback, documentId) {
        val current = playback.current
        if (current != null && current.contentType == PlaybackContentType.READ_ALOUD) {
            val segment = parseReadAloudMediaId(current.id)
            if (segment?.documentId == documentId) {
                scope.launch(Dispatchers.IO) {
                    val database = PlaybackDatabase.getInstance(context)
                    val dao = database.playbackDao()
                    val map = dao.getSegmentMap(documentId, segment.sourceOffset)
                    currentSegmentMap = map
                    if (map != null && page.isNotEmpty()) {
                        val spokenMap = buildSpokenIndexMap(page)
                        spokenIndexMap = spokenMap
                        inverseSpokenIndexMap = buildInverseSpokenIndexMap(spokenMap)
                    }
                }
            } else {
                currentSegmentMap = null
                spokenIndexMap = null
                inverseSpokenIndexMap = null
            }
        } else {
            currentSegmentMap = null
            spokenIndexMap = null
            inverseSpokenIndexMap = null
        }
    }

    // Update current sentence index based on playback position
    LaunchedEffect(playback, currentSegmentMap) {
        val current = playback.current
        val map = currentSegmentMap
        if (current != null && current.contentType == PlaybackContentType.READ_ALOUD && map != null) {
            val segment = parseReadAloudMediaId(current.id)
            if (segment?.documentId == documentId) {
                val segmentDuration = map.segmentDurationMs.coerceAtLeast(1)
                val sentenceCount = Json.decodeFromString<List<String>>(map.sentenceIdsJson).size
                if (sentenceCount > 0) {
                    val sentenceDuration = segmentDuration / sentenceCount.toLong()
                    val idx = (playback.positionMs / sentenceDuration).coerceIn(0L, sentenceCount - 1L).toInt()
                    currentSentenceIdx = idx
                }
            }
        } else {
            currentSentenceIdx = null
        }
    }

    fun startReadAloud(continuous: Boolean) {
        val currentDocument = document ?: return
        val pageStart = position ?: 0L
        val sourcePage = page
        val playbackAtRequest = playbackController?.snapshot?.value
        val queueIdsAtRequest = playbackAtRequest?.items?.map { it.id }
        val currentIndexAtRequest = playbackAtRequest?.currentIndex
        ReadAloudStatus.clearFailure(currentDocument.id)
        scope.launch {
            synthesizing = true
            speechError = null
            try {
                val spokenText = if (currentDocument.mimeType == "text/markdown") markdownForSpeech(page) else page
                val identity = speechGenerator.currentIdentity()
                val generated = speechGenerator.synthesize(
                    text = spokenText,
                    identity = identity,
                    strictOffline = strictOffline,
                )
                val items = generated.map { segment ->
                    SelectedAudio(
                        id = readAloudMediaId(
                            documentId = currentDocument.id,
                            pageStart = pageStart,
                            pageLength = page.length,
                            continuous = continuous,
                            sourceOffset = segment.sourceOffset.toLong(),
                            cacheKey = segment.file.nameWithoutExtension,
                        ),
                        uri = Uri.fromFile(segment.file),
                        title = currentDocument.title,
                        contentType = PlaybackContentType.READ_ALOUD,
                        subtitle = "Read Aloud",
                    )
                }
                if (items.isEmpty()) speechError = "There is no readable text on this page."
                else if (page != sourcePage) {
                    speechError = "The reading position changed while speech was being prepared. Start Read Aloud again."
                } else if (playbackController != null) {
                    val currentPlayback = playbackController.snapshot.value
                    if (currentPlayback.items.map { it.id } != queueIdsAtRequest || currentPlayback.currentIndex != currentIndexAtRequest) {
                        speechError = "Playback changed while speech was being prepared. Start Read Aloud again."
                    } else {
                        playbackController.playSelected(items)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                speechError = failure.message ?: "Murmur couldn't prepare speech for this page."
            } finally {
                synthesizing = false
            }
        }
    }

    LaunchedEffect(repository) { repository?.observeDocuments()?.collect { documents = it } }
    LaunchedEffect(repository, documentId) {
        checkingSource = true
        sourceChanged = false
        try {
            sourceChanged = repository?.validateDocumentSource(documentId) ?: false
            if (sourceChanged) position = 0L
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            sourceChanged = false
        } finally {
            checkingSource = false
        }
    }
    LaunchedEffect(document?.id, document?.positionChars) {
        val savedPosition = document?.positionChars ?: return@LaunchedEffect
        if (!sourceChanged && (position == null || position != savedPosition)) position = savedPosition
    }
    LaunchedEffect(repository, documentId) { repository?.observeBookmarks(documentId)?.collect { bookmarks = it } }
    LaunchedEffect(speechGenerator, selectedSpeechVoice, strictOffline) {
        speechError = null
        runCatching {
            speechVoices = speechGenerator.availableVoices()
            voiceNetworkRequirement = speechGenerator.voiceNetworkRequirement(selectedSpeechVoice)
            speechGenerator.selectVoice(selectedSpeechVoice, strictOffline)
            voiceNetworkRequirement = speechGenerator.voiceNetworkRequirement()
        }.onFailure { failure ->
            speechError = failure.message ?: "System speech isn't available on this device."
        }
    }
    LaunchedEffect(repository, document?.uri, position, checkingSource) {
        val current = document
        val start = position
        if (!checkingSource && repository != null && current != null && start != null) {
            loading = true
            error = null
            try {
                if (current.mimeType == "application/pdf") {
                    val pageIndex = start.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                    val rendered = repository.renderPdfPage(current, pageIndex, 1440)
                    page = repository.extractPdfPageText(current, rendered.pageIndex).orEmpty()
                    pdfPage = rendered
                    repository.savePosition(current.id, rendered.pageIndex.toLong())
                    if (start != rendered.pageIndex.toLong()) position = rendered.pageIndex.toLong()
                } else {
                    pdfPage = null
                    page = repository.readPage(current, start)
                    repository.savePosition(current.id, start)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "This document can't be opened. Its access may have been removed; add it again from Documents."
                page = ""
                pdfPage = null
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(document?.title ?: "Reader", style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (document?.mimeType == "application/pdf") "Page ${(position ?: 0L) + 1}" else "Position ${(position ?: 0L) + 1}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sourceChanged) {
            Text(
                "This document or its saved position format changed. Reading restarted at the beginning; saved bookmarks were kept and may need review.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (document?.mimeType == "application/epub+zip") {
            TextButton(
                enabled = !loadingEpubContents && repository != null,
                onClick = {
                    if (epubContents.isNotEmpty()) {
                        showEpubContents = true
                    } else {
                        scope.launch {
                            loadingEpubContents = true
                            epubContentsError = null
                            try {
                                epubContents = repository?.getEpubToc(documentId).orEmpty()
                                if (epubContents.isEmpty()) epubContentsError = "This EPUB has no readable contents list."
                                else showEpubContents = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                epubContentsError = failure.message ?: "Murmur couldn't load this book's contents."
                            } finally {
                                loadingEpubContents = false
                            }
                        }
                    }
                },
            ) { Text(if (loadingEpubContents) "Loading contents…" else "Contents") }
            epubContentsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (document?.mimeType != "application/pdf" || Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            if (document?.mimeType != "application/pdf") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Text size", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { scope.launch { settingsRepository.setReaderTextScale(readerTextScale - 0.1f) } },
                            enabled = readerTextScale > 0.8f,
                        ) { Text("A−") }
                        Text("${(readerTextScale * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
                        TextButton(
                            onClick = { scope.launch { settingsRepository.setReaderTextScale(readerTextScale + 0.1f) } },
                            enabled = readerTextScale < 1.6f,
                        ) { Text("A+") }
                    }
                }
            }
            TextButton(onClick = {
                searchExpanded = !searchExpanded
                searchError = null
                if (!searchExpanded) searchMatches = emptyList()
            }) { Text(if (searchExpanded) "Close search" else "Find in document") }
            if (searchExpanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it.take(128); searchError = null },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("Search text") },
                    )
                    TextButton(
                        enabled = !searching && searchQuery.isNotBlank() && repository != null,
                        onClick = {
                            val current = document ?: return@TextButton
                            scope.launch {
                                searching = true
                                searchError = null
                                searchMatches = emptyList()
                                try {
                                    searchMatches = repository?.searchDocument(current, searchQuery, limit = 25).orEmpty()
                                    if (searchMatches.isEmpty()) searchError = "No matches found."
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (failure: Exception) {
                                    searchError = failure.message ?: "Murmur couldn't search this document."
                                } finally {
                                    searching = false
                                }
                            }
                        },
                    ) { Text(if (searching) "Searching…" else "Search") }
                }
                searchError?.let { Text(it, color = if (it == "No matches found.") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error) }
                if (searchMatches.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().height(140.dp).verticalScroll(rememberScrollState())) {
                        searchMatches.forEach { match ->
                            TextButton(
                                onClick = {
                                    previousPages.clear()
                                    position = match.positionChars
                                    searchExpanded = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(match.snippet, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (document?.mimeType == "application/pdf") {
                if (page.isNotBlank()) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !loading && !synthesizing && playbackController != null,
                        onClick = { startReadAloud(continuous = true) },
                    ) { Text(if (synthesizing) "Preparing speech…" else "Read from here") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !loading && !synthesizing && playbackController != null,
                        onClick = { startReadAloud(continuous = false) },
                    ) { Text("This page") }
                } else {
                    Text(
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                            "PDF text reading needs Android 15 or newer."
                        } else {
                            "This page has no extractable text. Scanned PDFs aren't supported."
                        },
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = page.isNotBlank() && !loading && !synthesizing && playbackController != null,
                    onClick = { startReadAloud(continuous = true) },
                ) { Text(if (synthesizing) "Preparing speech…" else "Read from here") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = page.isNotBlank() && !loading && !synthesizing && playbackController != null,
                    onClick = { startReadAloud(continuous = false) },
                ) { Text("This page") }
            }
            if (document?.mimeType == "application/pdf") OutlinedButton(onClick = {
                val current = document ?: return@OutlinedButton
                val at = position ?: current.positionChars
                scope.launch { repository?.addBookmark(current.id, at, "Page ${at + 1}") }
            }, enabled = repository != null && !loading) { Text("Bookmark") }
        }
        if (document?.mimeType != "application/pdf") TextButton(
            onClick = {
                val current = document ?: return@TextButton
                val at = position ?: current.positionChars
                scope.launch { repository?.addBookmark(current.id, at, "Position ${at + 1}") }
            },
            enabled = document != null && repository != null && !loading,
        ) { Text("Bookmark this position") }
        if (document?.mimeType != "application/pdf" || page.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Strict Offline Speech", style = MaterialTheme.typography.titleSmall)
                Text(
                    "System voice: ${voiceNetworkRequirement.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!strictOffline) {
                    Text("This voice may use a network connection; Murmur itself does not connect.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { showVoicePicker = true }, enabled = speechVoices.isNotEmpty()) {
                    val selected = speechVoices.firstOrNull { it.name == selectedSpeechVoice }
                    Text("Voice: ${selected?.label ?: "System default"}")
                }
            }
            Switch(checked = strictOffline, onCheckedChange = { enabled -> scope.launch { settingsRepository.setStrictOfflineSpeech(enabled) } })
        }
        speechError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        documentReadAloudFailure?.let { failure ->
            Text(failure.message, color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = { startReadAloud(continuous = true) },
                enabled = page.isNotBlank() && !loading && !synthesizing && playbackController != null,
            ) { Text("Retry Read Aloud from this position") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (document?.mimeType == "application/pdf" && pdfPage != null) {
            val image = remember(pdfPage) {
                pdfPage?.let { BitmapFactory.decodeByteArray(it.pngBytes, 0, it.pngBytes.size)?.asImageBitmap() }
            }
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = "PDF page ${(pdfPage?.pageIndex ?: 0) + 1} of ${pdfPage?.pageCount ?: 0}",
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                )
            }
        } else if (page.isNotEmpty()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                if (document?.mimeType == "text/markdown") {
                    val type = MaterialTheme.typography
                    val highlightedPage = if (currentSentenceIdx != null && currentSegmentMap != null) {
                        // Build AnnotatedString with highlighted sentence for markdown
                        // For simplicity, we'll just render the markdown without sentence highlighting for now
                        // since markdown rendering is block-based
                        page
                    } else page
                    MaterialTheme(
                        typography = type.copy(
                            bodyLarge = type.bodyLarge.copy(fontSize = type.bodyLarge.fontSize * readerTextScale),
                            bodyMedium = type.bodyMedium.copy(fontSize = type.bodyMedium.fontSize * readerTextScale),
                            titleLarge = type.titleLarge.copy(fontSize = type.titleLarge.fontSize * readerTextScale),
                            titleMedium = type.titleMedium.copy(fontSize = type.titleMedium.fontSize * readerTextScale),
                            titleSmall = type.titleSmall.copy(fontSize = type.titleSmall.fontSize * readerTextScale),
                        ),
                    ) { MarkdownReaderContent(highlightedPage) }
                } else if (document?.mimeType == "text/plain") {
                    val pageStart = position ?: document.positionChars
                    var textLayout by remember(page) { mutableStateOf<TextLayoutResult?>(null) }
                    val highlightedText: AnnotatedString = if (currentSentenceIdx != null && currentSegmentMap != null) {
                        buildHighlightedText(page, currentSentenceIdx!!, currentSegmentMap!!, inverseSpokenIndexMap, MaterialTheme.colorScheme.primaryContainer)
                    } else AnnotatedString(page)
                    Text(
                        "Tap text to set a reading position, then choose Read from here.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = highlightedText,
                        modifier = Modifier
                            .pointerInput(page, pageStart, currentSentenceIdx, currentSegmentMap, playbackController, playback) {
                                detectTapGestures { tapOffset ->
                                    val offset = textLayout?.getOffsetForPosition(tapOffset) ?: return@detectTapGestures
                                    // Tap-to-Read: if Read Aloud is active, seek to tapped sentence
                                    if (currentSegmentMap != null && spokenIndexMap != null) {
                                        val segmentSourceOffset = mapTapOffsetToSegment(offset, spokenIndexMap!!, currentSegmentMap!!)
                                        if (segmentSourceOffset != null) {
                                            // Find the matching segment in the current queue
                                            val targetIndex = playback.items.indexOfFirst { item ->
                                                parseReadAloudMediaId(item.id)?.sourceOffset == segmentSourceOffset
                                            }
                                            if (targetIndex >= 0) {
                                                playbackController?.playItem(targetIndex)
                                                return@detectTapGestures
                                            }
                                        }
                                    }
                                    // Regular tap: set reading position
                                    previousPages.clear()
                                    position = pageStart + offset.coerceIn(0, page.lastIndex)
                                }
                            }
                            .semantics {
                                onClick(label = "Set reading position to the beginning of this passage") {
                                    previousPages.clear()
                                    position = pageStart
                                    true
                                }
                            },
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * readerTextScale,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * readerTextScale * 1.45f,
                        ),
                        onTextLayout = { textLayout = it },
                    )
                } else {
                    Text(
                        page,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize * readerTextScale,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * readerTextScale * 1.45f,
                        ),
                    )
                }
            }
        } else Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                when {
                    checkingSource -> "Checking document source…"
                    loading -> "Loading document…"
                    document == null -> "Choose a supported document from Documents."
                    error != null -> "The document is unavailable."
                    document.mimeType == "application/pdf" -> "Opening PDF page…"
                    else -> "This document is empty."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (bookmarks.isNotEmpty()) {
            HorizontalDivider()
            Text("Bookmarks", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                bookmarks.forEach { bookmark ->
                    TextButton(onClick = {
                        previousPages.clear()
                        position = bookmark.positionChars
                    }) { Text(bookmark.label) }
                    TextButton(onClick = { scope.launch { repository?.removeBookmark(bookmark.id) } }) { Text("Remove") }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                when {
                    previousPages.isNotEmpty() -> position = previousPages.removeAt(previousPages.lastIndex)
                    (pdfPage?.pageIndex ?: 0) > 0 -> position = (pdfPage?.pageIndex ?: 1) - 1L
                }
            }, enabled = (previousPages.isNotEmpty() || (pdfPage?.pageIndex ?: 0) > 0) && !loading) {
                Text("Previous")
            }
            TextButton(onClick = {
                val currentPosition = position ?: 0L
                val currentPdf = pdfPage
                if (currentPdf != null && currentPdf.pageIndex < currentPdf.pageCount - 1) {
                    previousPages.add(currentPosition)
                    position = currentPdf.pageIndex + 1L
                } else if (currentPdf == null && page.length == 12_000) {
                    previousPages.add(currentPosition)
                    position = currentPosition + page.length
                }
            }, enabled = (pdfPage?.let { it.pageIndex < it.pageCount - 1 } ?: (page.length == 12_000)) && !loading) { Text("Next") }
        }
    }
    if (showVoicePicker) {
        AlertDialog(
            onDismissRequest = { showVoicePicker = false },
            title = { Text("Choose a system voice") },
            text = {
                Column(Modifier.fillMaxWidth().height(320.dp).verticalScroll(rememberScrollState())) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                settingsRepository.setSelectedSpeechVoice(null)
                                showVoicePicker = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("System default", Modifier.fillMaxWidth()) }
                    speechVoices.forEach { voice ->
                        TextButton(
                            onClick = {
                                scope.launch {
                                    settingsRepository.setSelectedSpeechVoice(voice.name)
                                    showVoicePicker = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(voice.localeTag, style = MaterialTheme.typography.titleSmall)
                                Text(voice.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVoicePicker = false }) { Text("Done") } },
        )
    }
    if (showEpubContents) {
        AlertDialog(
            onDismissRequest = { showEpubContents = false },
            title = { Text("Contents") },
            text = {
                Column(Modifier.fillMaxWidth().height(360.dp).verticalScroll(rememberScrollState())) {
                    epubContents.forEach { entry ->
                        TextButton(
                            onClick = {
                                entry.positionChars?.let { target ->
                                    previousPages.clear()
                                    position = target
                                }
                                showEpubContents = false
                            },
                            enabled = entry.positionChars != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                entry.title,
                                modifier = Modifier.fillMaxWidth().padding(start = ((entry.level - 1).coerceAtLeast(0) * 16).dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEpubContents = false }) { Text("Close") } },
        )
    }
}

private val VoiceNetworkRequirement.label: String
    get() = when (this) {
        VoiceNetworkRequirement.KNOWN_OFFLINE -> "Known offline"
        VoiceNetworkRequirement.NETWORK_REQUIRED -> "Network required"
        VoiceNetworkRequirement.UNKNOWN -> "Unknown"
        VoiceNetworkRequirement.UNAVAILABLE -> "Unavailable"
        VoiceNetworkRequirement.MISSING_DATA -> "Missing data"
    }

@Composable
private fun MarkdownReaderContent(markdown: String) {
    val lines = remember(markdown) { markdown.lines() }
    val blocks = remember(lines) { parseMarkdownBlocks(lines) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    inlineMarkdown(block.text),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium
                        2 -> MaterialTheme.typography.headlineSmall
                        else -> MaterialTheme.typography.titleLarge
                    },
                )
                is MarkdownBlock.Paragraph -> Text(
                    inlineMarkdown(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.45f,
                )
                is MarkdownBlock.Quote -> Text(
                    inlineMarkdown(block.text),
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                is MarkdownBlock.ListItem -> Text(
                    inlineMarkdown("•  ${block.text}"),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
                is MarkdownBlock.Code -> Text(
                    block.text,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                MarkdownBlock.Separator -> HorizontalDivider()
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class ListItem(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data object Separator : MarkdownBlock
}

private fun parseMarkdownBlocks(lines: List<String>): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            result += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
            paragraph.clear()
        }
    }
    lines.forEach { raw ->
        val line = raw.trimEnd('\r')
        if (line.trimStart().startsWith("```")) {
            flushParagraph()
            if (inCode) {
                result += MarkdownBlock.Code(code.joinToString("\n"))
                code.clear()
            }
            inCode = !inCode
        } else if (inCode) {
            code += line
        } else {
            val trimmed = line.trim()
            val heading = Regex("^(#{1,6})\\s+(.+)$").matchEntire(trimmed)
            when {
                trimmed.isEmpty() -> flushParagraph()
                heading != null -> {
                    flushParagraph()
                    result += MarkdownBlock.Heading(heading.groupValues[1].length.coerceAtMost(3), heading.groupValues[2])
                }
                trimmed.matches(Regex("^([-*_])\\1{2,}$")) -> {
                    flushParagraph()
                    result += MarkdownBlock.Separator
                }
                trimmed.startsWith(">") -> {
                    flushParagraph()
                    result += MarkdownBlock.Quote(trimmed.removePrefix(">").trimStart())
                }
                trimmed.matches(Regex("^([-*+]\\s+|\\d+[.)]\\s+).*$")) -> {
                    flushParagraph()
                    result += MarkdownBlock.ListItem(trimmed.replaceFirst(Regex("^([-*+]\\s+|\\d+[.)]\\s+)"), ""))
                }
                else -> paragraph += trimmed
            }
        }
    }
    flushParagraph()
    if (code.isNotEmpty()) result += MarkdownBlock.Code(code.joinToString("\n"))
    return result
}

private fun inlineMarkdown(source: String): AnnotatedString {
    val linkPattern = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    val displayText = linkPattern.replace(source) { it.groupValues[1] }
    val builder = AnnotatedString.Builder()
    var index = 0
    val pattern = Regex("(\\*\\*.+?\\*\\*|(?<!\\*)\\*[^*]+\\*(?!\\*)|`[^`]+`)")
    pattern.findAll(displayText).forEach { match ->
        builder.append(displayText.substring(index, match.range.first))
        val token = match.value
        val markerLength = if (token.startsWith("**")) 2 else 1
        val content = token.substring(markerLength, token.length - markerLength)
        val style = when {
            token.startsWith("**") -> SpanStyle(fontWeight = FontWeight.Bold)
            token.startsWith("`") -> SpanStyle(fontFamily = FontFamily.Monospace)
            else -> SpanStyle(fontStyle = FontStyle.Italic)
        }
        val start = builder.length
        builder.append(content)
        builder.addStyle(style, start, builder.length)
        index = match.range.last + 1
    }
    builder.append(displayText.substring(index))
    return builder.toAnnotatedString()
}

@Composable
private fun AudiobookLibraryScreen(
    repository: AudiobookLibraryRepository?,
    playbackController: PlaybackController?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var books by remember { mutableStateOf<List<Audiobook>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        repository?.observeBooks()?.collect { books = it }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                adding = true
                error = null
                try {
                    val retainedUris = uris.filter { retainReadAccess(context.contentResolver, it) }
                    val added = repository?.addBooks(retainedUris) ?: 0
                    error = when {
                        retainedUris.size < uris.size -> "Added $added audiobooks. ${uris.size - retainedUris.size} couldn't be added because Murmur couldn't retain access. Choose those files again from a provider that supports ongoing access."
                        added < retainedUris.size -> "Added $added supported audiobooks. Some selected files weren't recognized as supported audio."
                        else -> null
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    error = "Murmur couldn't add those files. Please choose them again."
                } finally {
                    adding = false
                }
            }
        }
    }

    val matchingBooks = books.filter { book ->
        query.isBlank() || book.title.contains(query, ignoreCase = true) || book.author?.contains(query, ignoreCase = true) == true
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Murmur  •  Audiobooks", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("My books", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = { filePicker.launch(arrayOf("audio/*")) }, enabled = !adding) { Text("+ Add books") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search books or author") },
        )
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        if (adding) Text("Adding audiobooks…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            repository == null -> Text("Audiobook storage isn't available right now.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            books.isEmpty() -> OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Your library is quiet", style = MaterialTheme.typography.titleLarge)
                    Text("Add audiobook files from this device. Murmur keeps them and your listening place on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { filePicker.launch(arrayOf("audio/*")) }) { Text("Add audiobook files") }
                }
            }
            matchingBooks.isEmpty() -> Text("No books match that search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> matchingBooks.forEach { book ->
                AudiobookRow(book, playbackController, onRemove = { scope.launch { repository.removeBook(book.id) } })
            }
        }
    }
}

@Composable
private fun AudiobookRow(book: Audiobook, playbackController: PlaybackController?, onRemove: () -> Unit) {
    OutlinedCard(
        onClick = {
            val selected = SelectedAudio(book.id, book.uri, book.title, PlaybackContentType.AUDIOBOOK, book.author)
            if (book.isInProgress) playbackController?.playSelectedAt(selected, book.positionMs)
            else playbackController?.playSelected(listOf(selected))
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AudiobookArtwork(book.coverArt, book.title, Modifier.size(68.dp), RoundedCornerShape(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!book.author.isNullOrBlank()) {
                    Text(book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (book.isInProgress && book.progressFraction != null) {
                    Text("Continue · ${(book.progressFraction!! * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (book.durationMs > 0L) {
                    Text(formatPlaybackTime(book.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalMinutes = milliseconds.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}h ${minutes}m" else "${minutes} min"
}

@Composable
private fun ContinueScreen(onOpenDestination: (Destination) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("A quiet place for your local media.", style = MaterialTheme.typography.headlineMedium)
        Text("Choose local audio, or open a document to read and listen in one place.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(Destination.Music, Destination.Audiobooks, Destination.Documents).forEach { destination ->
                DestinationCard(destination) { onOpenDestination(destination) }
            }
        }
    }
}

@Composable
private fun DestinationCard(destination: Destination, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("destination-${destination.route}"),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(destination.title, style = MaterialTheme.typography.titleMedium)
                Text(destination.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Open", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EmptySectionScreen(title: String, message: String) {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MiniPlayer(snapshot: PlaybackSnapshot, onOpen: () -> Unit, onToggle: () -> Unit) {
    val current = snapshot.current ?: return
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(current.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(current.contentType.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onToggle) { Text(if (snapshot.isPlaying) "Pause" else "Play") }
    }
}

@Composable
private fun SleepTimerControl(controller: PlaybackController?) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(SleepTimer.PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
    }
    var deadlineMs by remember(preferences) {
        mutableStateOf(preferences.getLong(SleepTimer.PREFERENCE_END_TIME_MS, 0L))
    }
    var showDialog by remember { mutableStateOf(false) }
    val remainingMs = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)

    LaunchedEffect(preferences) {
        while (true) {
            deadlineMs = preferences.getLong(SleepTimer.PREFERENCE_END_TIME_MS, 0L)
            kotlinx.coroutines.delay(1_000L)
        }
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            if (remainingMs > 0L) {
                val totalSeconds = (remainingMs + 999L) / 1_000L
                val minutes = totalSeconds / 60L
                val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
                "Sleep timer · $minutes:$seconds left"
            } else {
                "Sleep timer off"
            },
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = { showDialog = true }, enabled = controller != null) { Text("Set") }
        if (remainingMs > 0L) {
            TextButton(onClick = { controller?.cancelSleepTimer(); deadlineMs = 0L }) { Text("Cancel") }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Sleep timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Pause playback after:", style = MaterialTheme.typography.bodyMedium)
                    SleepTimer.PRESET_MINUTES.forEach { minutes ->
                        TextButton(
                            onClick = {
                                controller?.startSleepTimer(minutes)
                                deadlineMs = System.currentTimeMillis() + minutes.toLong() * SleepTimer.MILLIS_PER_MINUTE
                                showDialog = false
                            },
                            enabled = controller != null,
                        ) { Text("$minutes minutes") }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        controller?.cancelSleepTimer()
                        deadlineMs = 0L
                        showDialog = false
                    },
                    enabled = remainingMs > 0L,
                ) { Text("Cancel timer") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Close") } },
        )
    }
}

@Composable
private fun NowPlayingScreen(
    snapshot: PlaybackSnapshot,
    controller: PlaybackController?,
    audiobookLibraryRepository: AudiobookLibraryRepository?,
    onReturnToDocument: () -> Unit,
    onOpenAudiobookLibrary: () -> Unit,
) {
    val current = snapshot.current
    if (current == null) {
        EmptySectionScreen("Now Playing", "Choose an audio file in Music or Audiobooks to start listening.")
        return
    }
    if (current.contentType == PlaybackContentType.AUDIOBOOK) {
        var books by remember { mutableStateOf<List<Audiobook>>(emptyList()) }
        LaunchedEffect(audiobookLibraryRepository) {
            audiobookLibraryRepository?.observeBooks()?.collect { books = it }
        }
        val artwork = books.firstOrNull { it.id == current.id }?.coverArt
        AudiobookPlayerScreen(snapshot, controller, artwork, audiobookLibraryRepository, onOpenAudiobookLibrary)
        return
    }
    if (current.contentType == PlaybackContentType.READ_ALOUD) {
        ReadAloudPlayerScreen(snapshot, controller, onReturnToDocument)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(current.title, style = MaterialTheme.typography.headlineSmall)
        Text(current.contentType.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SleepTimerControl(controller)
        Slider(
            value = snapshot.positionMs.coerceIn(0L, snapshot.durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { controller?.seekTo(it.toLong()) },
            valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { controller?.previous() }) { Text("Previous") }
            Button(onClick = { controller?.togglePlayPause() }) { Text(if (snapshot.isPlaying) "Pause" else "Play") }
            TextButton(onClick = { controller?.next() }) { Text("Next") }
        }
        Text("Up Next", style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = { controller?.clearQueue() }) { Text("Clear queue") }
        snapshot.items.forEachIndexed { index, item ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { controller?.playItem(index) }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(item.title, Modifier.weight(1f), maxLines = 1)
                    if (index == snapshot.currentIndex) Text("Playing", color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { if (index > 0) controller?.moveQueueItem(index, index - 1) }, enabled = index > 0) { Text("Move up") }
                    TextButton(onClick = { if (index < snapshot.items.lastIndex) controller?.moveQueueItem(index, index + 1) }, enabled = index < snapshot.items.lastIndex) { Text("Move down") }
                    TextButton(onClick = { controller?.removeQueueItem(index) }) { Text("Remove") }
                }
            }
        }
    }
}

@Composable
private fun ReadAloudPlayerScreen(snapshot: PlaybackSnapshot, controller: PlaybackController?, onReturnToDocument: () -> Unit) {
    val current = snapshot.current ?: return
    val failure by ReadAloudStatus.failure.collectAsState()
    val currentDocumentId = parseReadAloudMediaId(current.id)?.documentId
        ?: parseReadAloudStartMediaId(current.id)?.documentId
    val currentFailure = failure?.takeIf { it.documentId == currentDocumentId }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("READ ALOUD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text("Speech from the open document", color = MaterialTheme.colorScheme.onSurfaceVariant)
        SleepTimerControl(controller)
        currentFailure?.let {
            Text(it.message, color = MaterialTheme.colorScheme.error)
            Text("Return to the document to retry from the saved position.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = snapshot.positionMs.coerceIn(0L, snapshot.durationMs.coerceAtLeast(1L)).toFloat(),
            onValueChange = { controller?.seekTo(it.toLong()) },
            valueRange = 0f..snapshot.durationMs.coerceAtLeast(1L).toFloat(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { controller?.previous() }) { Text("Previous") }
            Button(onClick = { controller?.togglePlayPause() }) { Text(if (snapshot.isPlaying) "Pause" else "Play") }
            TextButton(onClick = { controller?.next() }) { Text("Next") }
        }
        TextButton(onClick = onReturnToDocument) { Text("Return to document") }
        TextButton(onClick = {
            // Return to Text: save last spoken position before stopping
            if (currentDocumentId != null) {
                scope.launch(Dispatchers.IO) {
                    val database = PlaybackDatabase.getInstance(context)
                    val dao = database.playbackDao()
                    val maps = dao.getAllSegmentMapsForDocument(currentDocumentId)
                    if (maps.isNotEmpty()) {
                        val lastMap = maps.maxByOrNull { it.sourceOffset } ?: maps.last()
                        val sentenceOffsets = Json.decodeFromString<List<Long>>(lastMap.sentenceStartOffsetsJson)
                        val lastSentenceOffset = sentenceOffsets.lastOrNull() ?: 0L
                        val targetChar = lastMap.sourcePageStart + lastSentenceOffset
                        val documentsStore = ReaderDocumentStore(database)
                        documentsStore.savePosition(currentDocumentId, targetChar)
                    }
                }
            }
            controller?.clearQueue()
            onReturnToDocument()
        }) { Text("Stop reading aloud") }
    }
}

@Composable
private fun AudiobookPlayerScreen(
    snapshot: PlaybackSnapshot,
    controller: PlaybackController?,
    artwork: ByteArray?,
    audiobookRepository: AudiobookLibraryRepository?,
    onOpenAudiobookLibrary: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val current = snapshot.current ?: return
    var bookmarks by remember(current.id) { mutableStateOf<List<com.joy.murmur.domain.audiobooks.AudiobookBookmark>>(emptyList()) }
    var chapters by remember(current.id) { mutableStateOf<List<com.joy.murmur.domain.audiobooks.AudiobookChapter>>(emptyList()) }
    var refreshingChapters by remember(current.id) { mutableStateOf(false) }
    var chapterError by remember(current.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(audiobookRepository, current.id) {
        audiobookRepository?.observeBookmarks(current.id)?.collect { bookmarks = it }
    }
    LaunchedEffect(audiobookRepository, current.id) {
        audiobookRepository?.observeChapters(current.id)?.collect { chapters = it }
    }
    val speedSteps = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    val speedIndex = speedSteps.indices.minByOrNull { kotlin.math.abs(speedSteps[it] - snapshot.playbackSpeed) } ?: 1
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("AUDIOBOOK PLAYER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        AudiobookArtwork(artwork, current.title, Modifier.fillMaxWidth().aspectRatio(0.92f), RoundedCornerShape(26.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(current.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            current.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        SleepTimerControl(controller)
        if (snapshot.hasPlaybackError) {
            Text(
                "Playback stopped. Check that the file is still available and that Murmur has access to it.",
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onOpenAudiobookLibrary) { Text("Return to Audiobooks") }
        }
        Column(Modifier.fillMaxWidth()) {
            Slider(
                value = playbackPositionFraction(snapshot.positionMs, snapshot.durationMs),
                onValueChange = { controller?.seekTo(playbackPositionAtFraction(it, snapshot.durationMs)) },
                valueRange = 0f..1f,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatMediaPosition(snapshot.positionMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMediaPosition(snapshot.durationMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onClick = { controller?.previous() }) { Text("Previous") }
            Button(onClick = { controller?.togglePlayPause() }) { Text(if (snapshot.isPlaying) "Pause" else "Play") }
            TextButton(onClick = { controller?.next() }) { Text("Next") }
        }
        TextButton(onClick = { controller?.setPlaybackSpeed(speedSteps[(speedIndex + 1) % speedSteps.size]) }) {
            Text("Speed  ${"%.2f".format(snapshot.playbackSpeed).trimEnd('0').trimEnd('.')}×")
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Bookmarks", style = MaterialTheme.typography.titleMedium)
            TextButton(
                enabled = audiobookRepository != null,
                onClick = {
                    scope.launch { audiobookRepository?.addBookmark(current.id, snapshot.positionMs) }
                },
            ) { Text("Add at current position") }
        }
        if (bookmarks.isEmpty()) {
            Text("No bookmarks yet. Save a place to return to it later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            bookmarks.forEach { bookmark ->
                OutlinedCard(onClick = { controller?.seekTo(bookmark.positionMs) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(bookmark.label, Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { audiobookRepository?.removeBookmark(bookmark.id) } }) { Text("Remove") }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Chapters", style = MaterialTheme.typography.titleMedium)
            TextButton(
                enabled = audiobookRepository != null && !refreshingChapters,
                onClick = {
                    chapterError = null
                    scope.launch {
                        refreshingChapters = true
                        try {
                            audiobookRepository?.refreshChapters(current.id)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            chapterError = failure.message ?: "Murmur couldn't read embedded chapters."
                        } finally {
                            refreshingChapters = false
                        }
                    }
                },
            ) { Text(if (refreshingChapters) "Checking…" else if (chapters.isEmpty()) "Find chapters" else "Refresh") }
        }
        chapterError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (chapters.isEmpty()) {
            Text("Only chapter markers embedded in the audio file can be shown.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            chapters.forEach { chapter ->
                OutlinedCard(onClick = { controller?.seekTo(chapter.startMs) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(chapter.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                when (chapter.source) {
                                    ChapterSource.CONTAINER, ChapterSource.ID3, ChapterSource.VORBIS -> "Embedded marker"
                                    ChapterSource.HEURISTIC -> "Estimated marker"
                                    ChapterSource.UNKNOWN -> "Marker source unknown"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(formatMediaPosition(chapter.startMs), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (snapshot.items.size > 1) {
            Text("Up next", modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge)
            snapshot.items.forEachIndexed { index, item ->
                if (index != snapshot.currentIndex) {
                    OutlinedCard(onClick = { controller?.playItem(index) }, modifier = Modifier.fillMaxWidth()) {
                        Text(item.title, modifier = Modifier.fillMaxWidth().padding(14.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AudiobookArtwork(
    artwork: ByteArray?,
    title: String,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(16.dp),
) {
    val image = remember(artwork) {
        artwork?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    Box(modifier.background(MaterialTheme.colorScheme.primaryContainer, shape), contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = "Cover art for $title", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                title.firstOrNull()?.uppercase() ?: "♪",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun formatMediaPosition(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds / 60L) % 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

@Composable
private fun AppearanceSettingsScreen(selectedMode: AppearanceMode, onModeSelected: suspend (AppearanceMode) -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        Text("Choose how Murmur looks on this device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppearanceMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedMode == mode, onClick = { scope.launch { onModeSelected(mode) } })
                Text(mode.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String {
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0) cursor.getString(column)?.takeIf(String::isNotBlank)?.let { return it }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Audio file"
}

@Composable
private fun SettingsScreen(settingsRepository: SettingsRepository, playbackInUse: Boolean) {
    val appearanceMode by settingsRepository.appearanceMode.collectAsState(initial = AppearanceMode.SYSTEM)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(SettingsTab.APPEARANCE) }

    Column(Modifier.fillMaxSize()) {
        SecondaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            modifier = Modifier.fillMaxWidth(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            SettingsTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) },
                )
            }
        }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (selectedTab) {
                SettingsTab.APPEARANCE -> AppearanceSettingsContent(appearanceMode) { mode ->
                    scope.launch { settingsRepository.setAppearanceMode(mode) }
                }
                SettingsTab.VOICE -> VoiceSettingsContent(settingsRepository)
                SettingsTab.STORAGE -> StorageCenterScreen(playbackInUse)
            }
        }
    }
}

@Composable
private fun AppearanceSettingsContent(
    selectedMode: AppearanceMode,
    onModeSelected: suspend (AppearanceMode) -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Appearance", style = MaterialTheme.typography.headlineSmall)
        Text("Choose how Murmur looks on this device.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppearanceMode.entries.forEach { mode ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selectedMode == mode, onClick = { scope.launch { onModeSelected(mode) } })
                Text(mode.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun VoiceSettingsContent(settingsRepository: SettingsRepository) {
    val scope = rememberCoroutineScope()
    val strictOffline by settingsRepository.strictOfflineSpeech.collectAsState(initial = true)
    val selectedVoice by settingsRepository.selectedSpeechVoice.collectAsState(initial = null)
    var availableVoices by remember { mutableStateOf<List<SpeechVoiceOption>>(emptyList()) }
    var voiceEligibility by remember { mutableStateOf<VoiceNetworkRequirement>(VoiceNetworkRequirement.UNKNOWN) }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var launchTtsSettings by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(launchTtsSettings) {
        if (launchTtsSettings) {
            val intent = createTtsSettingsIntent()
            context.startActivity(intent)
            launchTtsSettings = false
        }
    }

    fun refreshVoices() {
        scope.launch {
            checking = true
            error = null
            try {
                availableVoices = settingsRepository.getAvailableVoices()
                if (selectedVoice != null) {
                    voiceEligibility = settingsRepository.checkVoiceEligibility(selectedVoice)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                error = "Could not load voices: ${e.message}"
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { refreshVoices() }
    LaunchedEffect(strictOffline) { refreshVoices() }

    @Composable
    fun voiceStatusIcon(requirement: VoiceNetworkRequirement): String = when (requirement) {
        VoiceNetworkRequirement.KNOWN_OFFLINE -> "✓"
        VoiceNetworkRequirement.NETWORK_REQUIRED -> "⚠"
        VoiceNetworkRequirement.MISSING_DATA -> "⚠"
        VoiceNetworkRequirement.UNAVAILABLE -> "✗"
        else -> "?"
    }

    @Composable
    fun voiceStatusColor(requirement: VoiceNetworkRequirement) = when (requirement) {
        VoiceNetworkRequirement.KNOWN_OFFLINE -> MaterialTheme.colorScheme.tertiary
        VoiceNetworkRequirement.NETWORK_REQUIRED -> MaterialTheme.colorScheme.errorContainer
        VoiceNetworkRequirement.MISSING_DATA -> MaterialTheme.colorScheme.errorContainer
        VoiceNetworkRequirement.UNAVAILABLE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    @Composable
    fun voiceStatusLabel(requirement: VoiceNetworkRequirement): String = when (requirement) {
        VoiceNetworkRequirement.KNOWN_OFFLINE -> "Known Offline"
        VoiceNetworkRequirement.NETWORK_REQUIRED -> "Network Required"
        VoiceNetworkRequirement.MISSING_DATA -> "Voice Data Missing"
        VoiceNetworkRequirement.UNAVAILABLE -> "Unavailable"
        VoiceNetworkRequirement.UNKNOWN -> "Unknown"
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Read Aloud Voice", style = MaterialTheme.typography.headlineSmall)
        Text("Choose a voice for Read Aloud. Strict Offline Speech is ${if (strictOffline) "ON" else "OFF"}.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Strict Offline Speech", style = MaterialTheme.typography.titleMedium)
                Text("Only use voices that work without internet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = strictOffline,
                onCheckedChange = { enabled -> scope.launch { settingsRepository.setStrictOfflineSpeech(enabled) } },
            )
        }

        if (strictOffline) {
            Text(
                "When enabled, Murmur will not use voices that need internet. " +
                "If no compatible offline voice exists for your language, Read Aloud will not work until you install one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider()

        Text("Available Voices", style = MaterialTheme.typography.titleMedium)

        if (checking) {
            Text("Loading voices…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (availableVoices.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No TTS voices found on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Install a TTS engine (e.g., Google Speech Recognition & Synthesis) and download voice data for your language.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
Button(onClick = { launchTtsSettings = true }) { Text("Open TTS Settings") }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                availableVoices.forEach { voice ->
                    val requirement = if (voice.name == selectedVoice) voiceEligibility
                        else when {
                            voice.requiresNetwork -> VoiceNetworkRequirement.NETWORK_REQUIRED
                            voice.requiresInstall -> VoiceNetworkRequirement.MISSING_DATA
                            else -> VoiceNetworkRequirement.KNOWN_OFFLINE
                        }

                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (voice.name == selectedVoice) {
                            CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else CardDefaults.cardColors(),
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { scope.launch { settingsRepository.setSelectedSpeechVoice(voice.name) } }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(voice.name, style = MaterialTheme.typography.titleMedium)
                                Text(voice.localeTag, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(
                                    modifier = Modifier.padding(end = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        voiceStatusIcon(requirement),
                                        style = MaterialTheme.typography.titleLarge,
                                        color = voiceStatusColor(requirement),
                                    )
                                }
                                Text(
                                    voiceStatusLabel(requirement),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = voiceStatusColor(requirement),
                                )
                            }
                        }
                    }
                }
            }

            if (strictOffline && availableVoices.none { it.name == selectedVoice && voiceEligibility == VoiceNetworkRequirement.KNOWN_OFFLINE }) {
                val hasOfflineVoice = availableVoices.any { !it.requiresNetwork && !it.requiresInstall }
                if (!hasOfflineVoice) {
                    Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "No compatible offline voice is available for the selected language. " +
                            "Install offline voice data in your device's TTS settings or import a compatible local speech model.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Button(onClick = { launchTtsSettings = true }) { Text("Open TTS Settings") }
                    }
                }
            }
        }

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
    }
}

private val AppearanceMode.label: String
    get() = when (this) {
        AppearanceMode.SYSTEM -> "System default"
        AppearanceMode.LIGHT -> "Light"
        AppearanceMode.DARK -> "Dark"
    }
