package com.joy.murmur.playback

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.QueueItemEntity
import com.joy.murmur.data.speech.SystemTtsSpeechGenerator
import com.joy.murmur.playback.PlaybackContentType
import com.joy.murmur.playback.QueueInsertMode
import com.joy.murmur.playback.SelectedAudio
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

@OptIn(UnstableApi::class)
class PlaybackController(
    context: Context, 
    private val store: PlaybackStore,
    private val sourceInfoExtractor: SourceInfoExtractor,
    private val outputProfileManager: OutputProfileManager,
) {
    private val appContext = context.applicationContext
    private val supervisorJob = SupervisorJob()
    private val scope = CoroutineScope(supervisorJob + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var controllerJob: Job? = null
    private val controllerLock = ReentrantLock()

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync().also { future ->
            future.addListener({
                scope.launch {
                    runCatching { 
                        withTimeout(10_000L) { future.get() } 
                    }.onSuccess { connected ->
                        controllerLock.lock()
                        try {
                            controller = connected
                            controllerJob = scope.launch(Dispatchers.Main.immediate) {
                                connected.addListener(object : Player.Listener {
                                    override fun onEvents(player: Player, events: Player.Events) = publish(player)
                                })
                                publish(connected)
                            }
                        } finally {
                            controllerLock.unlock()
                        }
                    }.onFailure { e ->
                        // Connection failed; controller remains null
                    }
                }
            }, { command -> mainHandler.post(command) })
        }
    }

    suspend fun getSourceInfo(): SourceInfo? = withContext(Dispatchers.IO) {
        controllerLock.lock()
        try {
            val player = controller ?: runCatching { controllerFuture?.get() }.getOrNull() ?: return@withContext null
            val mediaItem = player.currentMediaItem ?: return@withContext null
            if (mediaItem.localConfiguration?.uri == null) return@withContext null
            
            val format = Format.Builder()
                .setId(mediaItem.mediaId)
                .setSampleMimeType(getMimeTypeFromUri(mediaItem.localConfiguration!!.uri!!))
                .build()
            
            sourceInfoExtractor.extractSourceInfo(format, mediaItem)
        } finally {
            controllerLock.unlock()
        }
    }

    private fun getMimeTypeFromUri(uri: Uri): String {
        val path = uri.path?.lowercase() ?: return "audio/mpeg"
        return when {
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".m4a") || path.endsWith(".mp4") -> "audio/mp4"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".ogg") -> "audio/ogg"
            path.endsWith(".opus") -> "audio/opus"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".mpc") -> "audio/x-musepack"
            path.endsWith(".wv") -> "audio/x-wavpack"
            path.endsWith(".ape") -> "audio/x-ape"
            path.endsWith(".alac") -> "audio/alac"
            path.endsWith(".dsf") -> "audio/x-dsf"
            path.endsWith(".dff") -> "audio/x-dff"
            else -> "audio/mpeg"
        }
    }

    fun release() {
        controllerLock.lock()
        try {
            controllerJob?.cancel()
            controllerJob = null
            controllerFuture?.let { MediaController.releaseFuture(it) }
            controllerFuture = null
            controller = null
        } finally {
            controllerLock.unlock()
        }
        supervisorJob.cancel()
    }

    fun playSelected(items: List<SelectedAudio>) {
        enqueue(items, QueueInsertMode.REPLACE_AND_PLAY, 0L)
    }

    fun playSelectedAt(item: SelectedAudio, positionMs: Long) {
        enqueue(listOf(item), QueueInsertMode.REPLACE_AND_PLAY, positionMs.coerceAtLeast(0L))
    }

    fun addNext(items: List<SelectedAudio>) {
        enqueue(items, QueueInsertMode.NEXT)
    }

    fun addToQueue(items: List<SelectedAudio>) {
        enqueue(items, QueueInsertMode.END)
    }

    fun removeQueueItem(index: Int) {
        scope.launch { 
            controllerLock.lock()
            try {
                controller?.removeMediaItem(index)
            } finally {
                controllerLock.unlock()
            }
        }
    }

    fun moveQueueItem(from: Int, to: Int) {
        scope.launch { 
            controllerLock.lock()
            try {
                controller?.moveMediaItem(from, to)
            } finally {
                controllerLock.unlock()
            }
        }
    }

    fun clearQueue() {
        scope.launch {
            controllerLock.lock()
            try {
                controller?.stop()
                controller?.clearMediaItems()
            } finally {
                controllerLock.unlock()
            }
        }
    }

    fun enqueue(items: List<SelectedAudio>, mode: QueueInsertMode, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        scope.launch {
            var active: MediaController? = null
            controllerLock.lock()
            try {
                active = controller ?: runCatching { controllerFuture?.get() }.getOrNull() ?: return@launch
            } finally {
                controllerLock.unlock()
            }
            controllerLock.lock()
            try {
                controller = active
            } finally {
                controllerLock.unlock()
            }
            val activeMediaId: String?
            controllerLock.lock()
            try {
                activeMediaId = active.currentMediaItem?.mediaId
            } finally {
                controllerLock.unlock()
            }
            val existing = store.queue()
            val persisted = when (mode) {
                QueueInsertMode.REPLACE_AND_PLAY -> emptyList()
                QueueInsertMode.NEXT, QueueInsertMode.END -> existing
            }
            val activeIndex = existing.indexOfFirst { it.mediaId == activeMediaId }
            val insertAt = when (mode) {
                QueueInsertMode.REPLACE_AND_PLAY -> 0
                QueueInsertMode.NEXT -> (activeIndex + 1).coerceAtLeast(0)
                QueueInsertMode.END -> persisted.size
            }.coerceAtMost(persisted.size)
            
            val newEntries = items.mapIndexed { index, item ->
                QueueItemEntity(
                    mediaId = item.id,
                    uri = item.uri.toString(),
                    title = item.title,
                    contentType = item.contentType.storageValue,
                    queueIndex = insertAt + index,
                    subtitle = item.subtitle,
                )
            }
            val combined = persisted.toMutableList().apply { addAll(insertAt, newEntries) }
                .mapIndexed { index, item -> item.copy(queueIndex = index) }
            store.replaceQueue(combined)
            
            controllerLock.lock()
            try {
                val mediaItems = items.map(::toMediaItem)
                when (mode) {
                    QueueInsertMode.REPLACE_AND_PLAY -> {
                        active.setMediaItems(mediaItems, 0, startPositionMs)
                        active.prepare()
                        active.play()
                    }
                    QueueInsertMode.NEXT, QueueInsertMode.END -> {
                        active.addMediaItems(insertAt, mediaItems)
                        if (active.mediaItemCount == items.size) {
                            active.prepare()
                            active.play()
                        }
                    }
                }
            } finally {
                controllerLock.unlock()
            }
        }
    }

    fun startReadAloud(documentId: String, documentTitle: String, documentUri: Uri, mimeType: String, startChar: Long, continuous: Boolean, strictOffline: Boolean) {
        val mediaItem = MediaItem.Builder()
            .setMediaId(
                readAloudStartMediaId(
                    ReadAloudStartRequest(
                        documentId = documentId,
                        startChar = startChar,
                        mimeType = mimeType,
                        strictOffline = strictOffline,
                        continuous = continuous,
                    ),
                ),
            )
            .setUri(documentUri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(documentTitle)
                    .setArtist("Read Aloud")
                    .setAlbumTitle("Read Aloud")
                    .setGenre("read_aloud")
                    .build(),
            )
            .build()
        scope.launch {
            var active: MediaController? = null
            controllerLock.lock()
            try {
                active = controller ?: runCatching { controllerFuture?.get() }.getOrNull() ?: return@launch
            } finally {
                controllerLock.unlock()
            }
            controllerLock.lock()
            try {
                controller = active
            } finally {
                controllerLock.unlock()
            }
            controllerLock.lock()
            try {
                active.setMediaItems(listOf(mediaItem), 0, 0L)
                active.prepare()
                active.play()
            } finally {
                controllerLock.unlock()
            }
        }
    }

    fun togglePlayPause() {
        controllerLock.lock()
        try {
            controller?.let { player ->
                if (player.isPlaying) player.pause() else {
                    if (player.playbackState == Player.STATE_IDLE && player.playerError != null) player.prepare()
                    player.play()
                }
            }
        } finally {
            controllerLock.unlock()
        }
    }

    fun next() { controllerLock.lock(); try { controller?.seekToNextMediaItem() } finally { controllerLock.unlock() } }
    fun previous() { controllerLock.lock(); try { controller?.seekToPreviousMediaItem() } finally { controllerLock.unlock() } }
    fun seekTo(positionMs: Long) { controllerLock.lock(); try { controller?.seekTo(positionMs.coerceAtLeast(0L)) } finally { controllerLock.unlock() } }
    
    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.5f)
        controllerLock.lock()
        try {
            controller?.let { player ->
                scope.launch(Dispatchers.Main.immediate) { player.playbackParameters = PlaybackParameters(safeSpeed) }
            }
        } finally {
            controllerLock.unlock()
        }
    }
    
    fun refreshPosition() { 
        controllerLock.lock()
        try {
            controller?.let(::publish)
        } finally {
            controllerLock.unlock()
        }
    }

    fun startSleepTimer(minutes: Int) {
        if (minutes !in 1..SleepTimer.MAX_DURATION_MINUTES) return
        appContext.startService(
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_START_SLEEP_TIMER)
                .putExtra(PlaybackService.EXTRA_SLEEP_TIMER_MINUTES, minutes),
        )
    }

    fun cancelSleepTimer() {
        appContext.startService(
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_CANCEL_SLEEP_TIMER),
        )
    }
    
    /**
     * Jumps to a queue item, optionally at an offset inside it.
     *
     * The seek and the item switch are one controller call: [playItem] followed by [seekTo] would race
     * the asynchronous media-item transition, which is how a tap-to-read seek could land on the
     * previous segment.
     */
    fun playItem(index: Int, positionMs: Long = 0L) {
        controllerLock.lock()
        try {
            controller?.let { player ->
                player.seekTo(index, positionMs.coerceAtLeast(0L))
                player.play()
            }
        } finally {
            controllerLock.unlock()
        }
    }

    private fun publish(player: Player) {
        val items = (0 until player.mediaItemCount).mapNotNull { index ->
            player.getMediaItemAt(index).toSelectedAudio()
        }
        mutableSnapshot.value = PlaybackSnapshot(
            items = items,
            currentIndex = player.currentMediaItemIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.takeIf { it >= 0L } ?: 0L,
            isPlaying = player.isPlaying,
            playbackSpeed = player.playbackParameters.speed,
            hasPlaybackError = player.playerError != null,
        )
    }

    private fun toMediaItem(item: SelectedAudio): MediaItem = MediaItem.Builder()
        .setMediaId(item.id)
        .setUri(item.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(item.title)
                .setArtist(item.contentType.displayName)
                .setAlbumTitle(item.subtitle)
                .setGenre(item.contentType.storageValue)
                .build(),
        )
        .build()

    private fun MediaItem.toSelectedAudio(): SelectedAudio? {
        val uri = localConfiguration?.uri ?: return null
        val contentType = when ((mediaMetadata.genre ?: mediaMetadata.artist)?.toString()?.lowercase()) {
            "audiobook" -> PlaybackContentType.AUDIOBOOK
            "read_aloud", "read aloud" -> PlaybackContentType.READ_ALOUD
            else -> PlaybackContentType.MUSIC
        }
        return SelectedAudio(mediaId, uri, mediaMetadata.title?.toString() ?: uri.lastPathSegment.orEmpty(), contentType, mediaMetadata.albumTitle?.toString())
    }
}
