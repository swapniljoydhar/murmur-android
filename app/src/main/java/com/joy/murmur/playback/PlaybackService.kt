package com.joy.murmur.playback

import android.os.Process
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.QueueItemEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.data.documents.RoomDocumentLibraryRepository
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import com.joy.murmur.data.settings.settingsDataStore
import com.joy.murmur.domain.documents.ReaderDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlaybackService : MediaSessionService() {
    private data class PlayerSnapshot(
        val queue: List<QueueItemEntity>,
        val currentMediaId: String?,
        val positionMs: Long,
        val durationMs: Long,
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val persistenceMutex = Mutex()
    private lateinit var player: ExoPlayer
    private lateinit var store: PlaybackStore
    private var mediaSession: MediaSession? = null
    private var restorationComplete = false
    private var continuationJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(PlaybackDatabase.getInstance(this))
        player = ExoPlayer.Builder(this).build()
        player.setAudioAttributes(
            androidx.media3.common.AudioAttributes.DEFAULT,
            true,
        )
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val segment = mediaItem?.let { parseReadAloudMediaId(it.mediaId) } ?: return
                serviceScope.launch(Dispatchers.IO) {
                    runCatching { ReaderDocumentStore(PlaybackDatabase.getInstance(this@PlaybackService)).savePosition(segment.documentId, segment.pageStart + segment.sourceOffset) }
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != Player.STATE_ENDED) return
                val mediaItem = player.currentMediaItem ?: return
                val segment = parseReadAloudMediaId(mediaItem.mediaId) ?: return
                if (!segment.continuous || segment.pageLength < READ_ALOUD_PAGE_SIZE || player.currentMediaItemIndex != player.mediaItemCount - 1) return
                if (continuationJob?.isActive == true) return
                continuationJob = serviceScope.launch { continueReadAloud(segment, mediaItem) }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (restorationComplete) serviceScope.launch { persistPlayerState() }
            }
        })
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(LocalControllerCallback())
            .build()
        val savedPlayback = runBlocking(Dispatchers.IO) { store.queue() to store.state() }
        restoreQueue(savedPlayback.first, savedPlayback.second)
        restorationComplete = true
        serviceScope.launch {
            while (true) {
                delay(5_000L)
                persistPlayerState()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        val savedState = snapshotPlayerState()
        runBlocking(Dispatchers.IO) { persistSnapshot(savedState) }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
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

    private suspend fun continueReadAloud(segment: ReadAloudSegmentInfo, endedItem: MediaItem) {
        val nextStart = segment.pageStart + segment.pageLength
        runCatching {
            val documentsStore = ReaderDocumentStore(PlaybackDatabase.getInstance(this@PlaybackService))
            val row = documentsStore.getDocument(segment.documentId) ?: return
            val document = ReaderDocument(row.id, Uri.parse(row.uri), row.title, row.mimeType, row.addedAtMillis, row.positionChars)
            val repository = RoomDocumentLibraryRepository(this@PlaybackService, documentsStore)
            val sourcePage = repository.readPage(document, nextStart, READ_ALOUD_PAGE_SIZE)
            if (sourcePage.isBlank() || player.currentMediaItem?.mediaId != endedItem.mediaId) return
            val settings = DataStoreSettingsRepository(applicationContext.settingsDataStore)
            val selectedVoice = settings.selectedSpeechVoice.first()
            val strictOffline = settings.strictOfflineSpeech.first()
            val generator = SystemTtsSpeechGenerator(this@PlaybackService)
            try {
                generator.selectVoice(selectedVoice)
                val speechText = if (document.mimeType == "text/markdown") markdownForSpeech(sourcePage) else sourcePage
                val generated = generator.synthesize(
                    text = speechText,
                    identity = "${document.id}:$nextStart:${document.mimeType}",
                    strictOffline = strictOffline,
                )
                if (player.currentMediaItem?.mediaId != endedItem.mediaId || generated.isEmpty()) return
                val mediaItems = generated.map { file ->
                    val absoluteOffset = nextStart + file.sourceOffset
                    MediaItem.Builder()
                        .setMediaId(readAloudMediaId(document.id, nextStart, sourcePage.length, true, file.sourceOffset.toLong(), file.file.nameWithoutExtension))
                        .setUri(Uri.fromFile(file.file))
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
                if (player.currentMediaItem?.mediaId != endedItem.mediaId) return
                documentsStore.savePosition(document.id, nextStart)
                val nextIndex = player.mediaItemCount
                player.addMediaItems(mediaItems)
                player.seekTo(nextIndex, 0L)
                player.prepare()
                player.play()
            } finally {
                generator.shutdown()
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

    private inner class LocalControllerCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sameApp = controller.uid == applicationInfo.uid
            val system = controller.uid == Process.SYSTEM_UID
            return if (sameApp || system) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
        }
    }

    private companion object {
        const val READ_ALOUD_PAGE_SIZE = 12_000
    }
}
