package com.joy.murmur.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.QueueItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackController(context: Context, private val store: PlaybackStore) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot())
    val snapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync().also { future ->
            future.addListener({
                runCatching { future.get() }.onSuccess { connected ->
                    controller = connected
                    connected.addListener(object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) = publish(player)
                    })
                    publish(connected)
                }
            }, { command -> mainHandler.post(command) })
        }
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
        scope.launch { withContext(Dispatchers.Main.immediate) { controller?.removeMediaItem(index) } }
    }

    fun moveQueueItem(from: Int, to: Int) {
        scope.launch { withContext(Dispatchers.Main.immediate) { controller?.moveMediaItem(from, to) } }
    }

    fun clearQueue() {
        scope.launch {
            withContext(Dispatchers.Main.immediate) {
                controller?.stop()
                controller?.clearMediaItems()
            }
        }
    }

    private fun enqueue(items: List<SelectedAudio>, mode: QueueInsertMode, startPositionMs: Long = 0L) {
        if (items.isEmpty()) return
        scope.launch {
            val active = controller ?: runCatching { controllerFuture?.get() }.getOrNull() ?: return@launch
            controller = active
            val activeMediaId = withContext(Dispatchers.Main.immediate) { active.currentMediaItem?.mediaId }
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
            withContext(Dispatchers.Main.immediate) {
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
            }
        }
    }

    fun togglePlayPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0L)) }
    fun setPlaybackSpeed(speed: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2.5f)
        controller?.let { player ->
            scope.launch { withContext(Dispatchers.Main.immediate) { player.playbackParameters = PlaybackParameters(safeSpeed) } }
        }
    }
    fun refreshPosition() { controller?.let(::publish) }
    fun playItem(index: Int) {
        controller?.let { player ->
            player.seekToDefaultPosition(index)
            player.play()
        }
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
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

enum class QueueInsertMode { REPLACE_AND_PLAY, NEXT, END }
