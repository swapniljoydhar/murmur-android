package com.joy.murmur.playback

import android.content.Intent
import android.os.Process
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.PlaybackRestorePolicy
import com.joy.murmur.data.playback.QueueItemEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity
import com.joy.murmur.data.documents.RoomDocumentLibraryRepository
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.data.speech.SynthesisIdentity
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.data.settings.settingsDataStore
import com.joy.murmur.domain.documents.ReaderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private data class PlayerSnapshot(
        val queue: List<QueueItemEntity>,
        val currentMediaId: String?,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceMutex = Mutex()
    private val readAloudPositionMutex = Mutex()
    private val readAloudPositionWrites = ReadAloudPositionWriteTracker()
    private lateinit var player: ExoPlayer
    private lateinit var sleepTimer: SleepTimer
    private lateinit var store: PlaybackStore
    private var mediaSession: MediaSession? = null
    private var restorationComplete = false
    private var continuationJob: Job? = null
    @Volatile private var continuationDocumentId: String? = null
    @Volatile private var readAloudStartJob: Job? = null
    @Volatile private var readAloudStartDocumentId: String? = null

    // Single shared TTS generator instance
    private var ttsGenerator: SystemTtsSpeechGenerator? = null

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(PlaybackDatabase.getInstance(this))

        player = ExoPlayer.Builder(this)
            .build()
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.DEFAULT,
            true,
        )
        sleepTimer = SleepTimer(this) { player.pause() }
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId
                if (mediaId == null) {
                    cancelReadAloudWork()
                    return
                }
                if (isReadAloudStartMediaId(mediaId)) {
                    val request = parseReadAloudStartMediaId(mediaId)
                    val documentId = request?.documentId
                    cancelReadAloudWork()
                    readAloudStartDocumentId = documentId
                    documentId?.let(ReadAloudStatus::clearFailure)
                    if (request == null) {
                        ReadAloudStatus.reportFailure(
                            documentId ?: "",
                            "This Read Aloud request is damaged. Open the document and start again.",
                        )
                        return
                    }
                    val job = serviceScope.launch(Dispatchers.IO) {
                        try {
                            startReadAloud(mediaId)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            documentId?.let {
                                ReadAloudStatus.reportFailure(it, failure.message ?: "Murmur couldn't start Read Aloud.")
                            }
                        }
                    }
                    readAloudStartJob = job
                    job.invokeOnCompletion {
                        if (readAloudStartJob === job) {
                            readAloudStartJob = null
                            readAloudStartDocumentId = null
                        }
                    }
                    return
                }
                val segment = parseReadAloudMediaId(mediaId)
                if (segment == null) {
                    cancelReadAloudWork()
                    return
                }
                if (readAloudStartDocumentId != null && readAloudStartDocumentId != segment.documentId) {
                    readAloudStartJob?.cancel()
                    readAloudStartJob = null
                    readAloudStartDocumentId = null
                }
                if (continuationDocumentId != null && continuationDocumentId != segment.documentId) {
                    continuationJob?.cancel()
                    continuationJob = null
                    continuationDocumentId = null
                }
                persistReadAloudPositionAsync(segment)
                updateReplayGainForCurrentTrack()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) persistCurrentReadAloudPosition()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val mediaItem = player.currentMediaItem ?: return
                val segment = parseReadAloudMediaId(mediaItem.mediaId) ?: return
                if (!segment.continuous || player.currentMediaItemIndex != player.mediaItemCount - 1) return
                if (continuationJob?.isActive == true) return
                continuationDocumentId = segment.documentId
                val job = serviceScope.launch(Dispatchers.IO) { continueReadAloud(segment, mediaItem) }
                continuationJob = job
                job.invokeOnCompletion {
                    if (continuationJob === job) {
                        continuationJob = null
                        continuationDocumentId = null
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val segment = player.currentMediaItem?.mediaId?.let(::parseReadAloudMediaId)
                if (segment == null) {
                    serviceScope.launch { persistPlayerState() }
                    return
                }
                val writeToken = readAloudPositionWrites.begin(segment.documentId)
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        persistReadAloudPosition(segment, writeToken)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Keep the error visible even if the best-effort position write fails.
                    }
                    ReadAloudStatus.reportFailure(
                        segment.documentId,
                        "Read Aloud audio couldn't be played. Your position was preserved; return to the document to retry.",
                    )
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (restorationComplete) serviceScope.launch { persistPlayerState() }
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(LocalControllerCallback())
            .build()

        serviceScope.launch(Dispatchers.IO) {
            val restorePlan = PlaybackRestorePolicy.plan(store.queue(), store.state())
            withContext(Dispatchers.Main.immediate) {
                restoreQueue(restorePlan.queue, restorePlan.state)
                restorationComplete = true
            }
        }

        serviceScope.launch {
            delay(1000L)
            while (true) {
                delay(5_000L)
                if (restorationComplete) persistPlayerState()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startResult = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_SLEEP_TIMER -> {
                val minutes = intent.getIntExtra(EXTRA_SLEEP_TIMER_MINUTES, -1)
                if (minutes in 1..SleepTimer.MAX_DURATION_MINUTES) sleepTimer.start(minutes)
            }
            ACTION_CANCEL_SLEEP_TIMER -> sleepTimer.cancel()
        }
        return startResult
    }

    private fun updateReplayGainForCurrentTrack() {
        // ReplayGain metadata reading requires custom implementation
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        // Cancel all jobs first
        continuationJob?.cancel()
        continuationJob = null
        continuationDocumentId = null
        readAloudStartJob?.cancel()
        readAloudStartJob = null
        readAloudStartDocumentId = null

        // Persist final state synchronously
        val savedState = snapshotPlayerState()
        val readAloudSegment = player.currentMediaItem?.mediaId?.let(::parseReadAloudMediaId)
        val readAloudPositionToken = readAloudSegment?.let { readAloudPositionWrites.begin(it.documentId) }
        runBlocking(Dispatchers.IO) {
            readAloudSegment?.let { segment ->
                readAloudPositionToken?.let { token -> runCatching { persistReadAloudPosition(segment, token) } }
            }
            persistSnapshot(savedState)
        }

        // Release media session and player
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null

        sleepTimer.shutdown()

        // Shutdown shared TTS generator
        ttsGenerator?.shutdown()
        ttsGenerator = null

        serviceScope.cancel()
        super.onDestroy()
    }

    // Get or create the shared TTS generator instance
    @Synchronized
    private fun getTtsGenerator(): SystemTtsSpeechGenerator {
        return ttsGenerator ?: SystemTtsSpeechGenerator(this@PlaybackService).also { ttsGenerator = it }
    }

    private suspend fun isCurrentMediaItem(mediaId: String): Boolean = withContext(Dispatchers.Main.immediate) {
        player.currentMediaItem?.mediaId == mediaId
    }

    private suspend fun appendReadAloudItemsIfCurrent(expectedMediaId: String, items: List<MediaItem>): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (player.currentMediaItem?.mediaId != expectedMediaId) return@withContext false
            val nextIndex = player.mediaItemCount
            player.addMediaItems(items)
            player.seekTo(nextIndex, 0L)
            player.prepare()
            player.play()
            true
        }

    private suspend fun replaceReadAloudItemsIfCurrent(expectedMediaId: String, items: List<MediaItem>): Boolean =
        withContext(Dispatchers.Main.immediate) {
            if (player.currentMediaItem?.mediaId != expectedMediaId) return@withContext false
            player.setMediaItems(items, 0, 0L)
            player.prepare()
            player.play()
            true
        }

    private fun restoreQueue(queue: List<QueueItemEntity>, saved: com.joy.murmur.data.playback.PlaybackStateEntity?) {
        if (queue.isEmpty()) return
        val mediaItems = queue.map(::toMediaItem)
        val startIndex = queue.indexOfFirst { it.mediaId == saved?.currentMediaId }.coerceAtLeast(0)
        player.setMediaItems(mediaItems, startIndex, saved?.positionMs ?: 0L)
    }

    private suspend fun persistPlayerState() = persistenceMutex.withLock {
        val snapshot = snapshotPlayerState()
        withContext(Dispatchers.IO) { persistSnapshot(snapshot) }
    }

    private fun snapshotPlayerState(): PlayerSnapshot {
        val queue = (0 until player.mediaItemCount).mapIndexedNotNull { index, _ ->
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: return@mapIndexedNotNull null
            val contentType = when ((item.mediaMetadata.genre ?: item.mediaMetadata.artist)?.toString()?.lowercase()) {
                "audiobook" -> "audiobook"
                "read_aloud", "read aloud" -> "read_aloud"
                else -> "music"
            }
            QueueItemEntity(
                mediaId = item.mediaId,
                uri = uri.toString(),
                title = item.mediaMetadata.title?.toString() ?: uri.lastPathSegment.orEmpty(),
                contentType = contentType,
                queueIndex = index,
                subtitle = item.mediaMetadata.albumTitle?.toString(),
            )
        }
        val currentId = player.currentMediaItem?.mediaId
        val position = if (player.currentPosition >= 0L) player.currentPosition else 0L
        val duration = player.duration.takeIf { it >= 0L } ?: 0L
        return PlayerSnapshot(queue, currentId, position, duration)
    }

    private suspend fun persistSnapshot(snapshot: PlayerSnapshot) = store.persistSnapshot(
        queue = snapshot.queue,
        mediaId = snapshot.currentMediaId,
        positionMs = snapshot.positionMs,
        durationMs = snapshot.durationMs,
    )

    private fun persistCurrentReadAloudPosition() {
        val segment = player.currentMediaItem?.mediaId?.let(::parseReadAloudMediaId) ?: return
        persistReadAloudPositionAsync(segment)
    }

    private fun cancelReadAloudWork() {
        continuationJob?.cancel()
        continuationJob = null
        continuationDocumentId = null
        readAloudStartJob?.cancel()
        readAloudStartJob = null
        readAloudStartDocumentId = null
    }

    private fun persistReadAloudPositionAsync(segment: ReadAloudSegmentInfo) {
        val writeToken = readAloudPositionWrites.begin(segment.documentId)
        serviceScope.launch(Dispatchers.IO) {
            runCatching { persistReadAloudPosition(segment, writeToken) }
        }
    }

    private suspend fun persistReadAloudPosition(segment: ReadAloudSegmentInfo, writeToken: Long) {
        readAloudPositionMutex.withLock {
            try {
                if (!readAloudPositionWrites.isCurrent(segment.documentId, writeToken)) return@withLock
                val documents = ReaderDocumentStore(PlaybackDatabase.getInstance(this@PlaybackService))
                val document = documents.getDocument(segment.documentId)
                val position = if (document?.mimeType == "application/pdf") {
                    segment.pageStart
                } else {
                    segment.pageStart + segment.sourceOffset
                }
                if (readAloudPositionWrites.isCurrent(segment.documentId, writeToken)) {
                    documents.savePosition(segment.documentId, position)
                }
            } finally {
                readAloudPositionWrites.finish(segment.documentId, writeToken)
            }
        }
    }

    private suspend fun continueReadAloud(segment: ReadAloudSegmentInfo, endedItem: MediaItem) {
        runCatching {
            val documentsStore = ReaderDocumentStore(PlaybackDatabase.getInstance(this@PlaybackService))
            val row = documentsStore.getDocument(segment.documentId) ?: return
            val document = ReaderDocument(row.id, Uri.parse(row.uri), row.title, row.mimeType, row.addedAtMillis, row.positionChars)
            val isPdf = document.mimeType == "application/pdf"
            if (!isPdf && segment.pageLength < READ_ALOUD_PAGE_SIZE) return
            val nextStart = segment.pageStart + if (isPdf) 1L else segment.pageLength.toLong()
            val repository = RoomDocumentLibraryRepository(this@PlaybackService, documentsStore)
            val sourcePage = repository.readPage(document, nextStart, READ_ALOUD_PAGE_SIZE)
            if (!isCurrentMediaItem(endedItem.mediaId)) return
            if (sourcePage.isBlank()) return
            val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
            val selectedVoice = settings.selectedSpeechVoice.first() ?: ""
            val strictOffline = settings.strictOfflineSpeech.first()
            
            // Get document structure for sentence segmentation
            val docStructure = try {
                repository.getDocumentStructure(document.id)
            } catch (e: Exception) {
                null
            }

            val generator = getTtsGenerator()
            try {
                generator.selectVoice(selectedVoice, strictOffline)
                val speechText = if (document.mimeType == "text/markdown") ReadAloudUtils.markdownForSpeech(sourcePage) else sourcePage
                val identity = SynthesisIdentity(
                    engine = "android_tts",
                    engineVersion = "1",
                    voice = selectedVoice,
                    locale = document.mimeType,
                    pronunciationVersion = "1",
                    filterVersion = "1",
                    sampleRate = 0, // Will be filled by engine
                    channelCount = 0,
                    bitDepth = 0,
                )
                val generated = generator.synthesize(
                    text = speechText,
                    identity = identity,
                    strictOffline = strictOffline,
                    documentStructure = docStructure,
                )
                if (!isCurrentMediaItem(endedItem.mediaId)) return
                check(generated.isNotEmpty()) { "The speech engine produced no audio for the next section." }
                
                // Persist sentence mapping for Tap-to-Read and highlighting
                persistSegmentMap(document.id, nextStart, sourcePage.length, generated)
                
                val mediaItems = generated.map { segment ->
                    MediaItem.Builder()
                        .setMediaId(readAloudMediaId(
                            document.id,
                            nextStart,
                            sourcePage.length,
                            true,
                            segment.sourceOffset.toLong(),
                            segment.file.nameWithoutExtension,
                        ))
                        .setUri(Uri.fromFile(segment.file))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(document.title)
                                .setArtist("Read Aloud")
                                .setAlbumTitle("Read Aloud")
                                .setGenre("read_aloud")
                                .build(),
                        )
                        .build()
                }
                appendReadAloudItemsIfCurrent(endedItem.mediaId, mediaItems)
            } catch (e: Exception) {
                // Generator shutdown is handled by onDestroy
                throw e
            }
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            if (isCurrentMediaItem(endedItem.mediaId)) {
                ReadAloudStatus.reportFailure(
                    segment.documentId,
                    failure.message ?: "Murmur couldn't continue Read Aloud. Your reading position was preserved.",
                )
            }
        }
    }


    private suspend fun startReadAloud(mediaId: String) {
        val request = parseReadAloudStartMediaId(mediaId) ?: return
        val documentId = request.documentId
        val startChar = request.startChar
        val mimeType = request.mimeType
        val strictOffline = request.strictOffline
        val continuous = request.continuous
        ReadAloudStatus.clearFailure(documentId)

        runCatching {
            val documentsStore = ReaderDocumentStore(PlaybackDatabase.getInstance(this@PlaybackService))
            val row = documentsStore.getDocument(documentId) ?: return
            val document = ReaderDocument(row.id, Uri.parse(row.uri), row.title, row.mimeType, row.addedAtMillis, row.positionChars)
            val repository = RoomDocumentLibraryRepository(this@PlaybackService, documentsStore)
            val sourcePage = repository.readPage(document, startChar, READ_ALOUD_PAGE_SIZE)
            if (!isCurrentMediaItem(mediaId)) return
            check(sourcePage.isNotBlank()) { "There is no readable text at this document position." }
            val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
            val selectedVoice = settings.selectedSpeechVoice.first() ?: ""
            
            // Get document structure for sentence segmentation
            val docStructure = try {
                repository.getDocumentStructure(document.id)
            } catch (e: Exception) {
                null
            }

            val generator = getTtsGenerator()
            try {
                generator.selectVoice(selectedVoice, strictOffline)
                val speechText = if (document.mimeType == "text/markdown") ReadAloudUtils.markdownForSpeech(sourcePage) else sourcePage
                val identity = SynthesisIdentity(
                    engine = "android_tts",
                    engineVersion = "1",
                    voice = selectedVoice,
                    locale = mimeType,
                    pronunciationVersion = "1",
                    filterVersion = "1",
                    sampleRate = 0,
                    channelCount = 0,
                    bitDepth = 0,
                )
                val generated = generator.synthesize(
                    text = speechText,
                    identity = identity,
                    strictOffline = strictOffline,
                    documentStructure = docStructure,
                )
                if (!isCurrentMediaItem(mediaId)) return
                check(generated.isNotEmpty()) { "The speech engine produced no audio for this document section." }
                
                // Persist sentence mapping for Tap-to-Read and highlighting
                persistSegmentMap(document.id, startChar, sourcePage.length, generated)
                
                val mediaItems = generated.map { segment ->
                    MediaItem.Builder()
                        .setMediaId(readAloudMediaId(
                            document.id,
                            startChar,
                            sourcePage.length,
                            continuous,
                            segment.sourceOffset.toLong(),
                            segment.file.nameWithoutExtension,
                        ))
                        .setUri(Uri.fromFile(segment.file))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(document.title)
                                .setArtist("Read Aloud")
                                .setAlbumTitle("Read Aloud")
                                .setGenre("read_aloud")
                                .build(),
                        )
                        .build()
                }
                replaceReadAloudItemsIfCurrent(mediaId, mediaItems)
            } catch (e: Exception) {
                throw e
            }
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            if (isCurrentMediaItem(mediaId)) {
                ReadAloudStatus.reportFailure(
                    documentId,
                    failure.message ?: "Murmur couldn't start Read Aloud. Your reading position was preserved.",
                )
            }
        }
    }

    private fun toMediaItem(item: QueueItemEntity): MediaItem = MediaItem.Builder()
        .setMediaId(item.mediaId)
        .setUri(item.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(when (item.contentType) {
                    "audiobook" -> "Audiobook"
                    "read_aloud" -> "Read Aloud"
                    else -> "Music"
                })
                .setAlbumTitle(item.subtitle)
                .setGenre(item.contentType)
                .build(),
        )
        .build()

private suspend fun persistSegmentMap(
        documentId: String,
        pageStart: Long,
        pageLength: Int,
        generated: List<com.joy.murmur.data.speech.GeneratedSpeechFile>,
    ) = withContext(Dispatchers.IO) {
        val database = PlaybackDatabase.getInstance(this@PlaybackService)
        val dao = database.playbackDao()
        val entities = generated.map { segment ->
            // Use the accurate sentence start offsets from the generator (spoken-character offsets)
            val sentenceIds = segment.sentenceIds
            val sentenceStartOffsets = segment.sentenceStartOffsets.map { it.toLong() }
            // Add final offset (end of segment) = segment sourceOffset + spokenLength
            val segmentEndOffset = segment.sourceOffset.toLong() + segment.spokenLength
            val allOffsets = sentenceStartOffsets + segmentEndOffset

            ReadAloudSegmentMapEntity(
                documentId = documentId,
                sourceOffset = segment.sourceOffset.toLong(),
                sentenceIdsJson = Json.encodeToString(sentenceIds),
                sentenceStartOffsetsJson = Json.encodeToString(allOffsets),
                sourcePageStart = pageStart,
                sourcePageLength = pageLength,
                segmentDurationMs = segment.durationMs.coerceAtLeast(1),
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertSegmentMaps(entities)
        }
    }

    private inner class LocalControllerCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sameApp = controller.uid == applicationInfo.uid
            val system = controller.uid == Process.SYSTEM_UID
            val isBluetooth = controller.packageName?.let { pkg ->
                pkg == "com.android.bluetooth" || pkg.contains("bluetooth", ignoreCase = true)
            } == true
            val isWear = controller.packageName?.contains("wear", ignoreCase = true) == true
            val isAuto = controller.packageName?.contains("auto", ignoreCase = true) == true

            return if (sameApp || system || isBluetooth || isWear || isAuto) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }
    }

    companion object {
        const val READ_ALOUD_PAGE_SIZE = 12_000
        const val ACTION_START_SLEEP_TIMER = "com.joy.murmur.action.START_SLEEP_TIMER"
        const val ACTION_CANCEL_SLEEP_TIMER = "com.joy.murmur.action.CANCEL_SLEEP_TIMER"
        const val EXTRA_SLEEP_TIMER_MINUTES = "sleep_timer_minutes"
    }
}
