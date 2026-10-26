package com.joy.murmur.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import com.joy.murmur.data.speech.SpeechSegmentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Manages the internal TTS buffer/queue separately from the user-visible media queue.
 * This ensures TTS segments don't pollute the user's "Up Next" list.
 */
class TtsQueueManager(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
) {
    // Internal TTS segments that are queued but not exposed to user
    private val pendingTtsSegments = mutableListOf<PendingTtsSegment>()
    private val scopeJob = Job()
    private val internalScope = CoroutineScope(scopeJob + Dispatchers.IO)

    data class PendingTtsSegment(
        val mediaId: String,
        val file: File,
        val title: String,
        val sourceOffset: Long,
        val sentenceIds: List<String>,
    )

    /**
     * Add TTS segments to the internal buffer (not user-visible queue)
     */
    fun addTtsSegments(
        documentId: String,
        documentTitle: String,
        segments: List<SpeechSegmentResult>,
        basePageStart: Long,
        continuous: Boolean,
    ) {
        internalScope.launch {
            val newSegments = segments.mapIndexed { index, segment ->
                val mediaId = ReadAloudUtils.readAloudMediaId(
                    documentId,
                    basePageStart,
                    segments.size, // pageLength approximation
                    continuous,
                    segment.sourceOffset.toLong(),
                    segment.file.nameWithoutExtension,
                )
                PendingTtsSegment(
                    mediaId = mediaId,
                    file = segment.file,
                    title = documentTitle,
                    sourceOffset = segment.sourceOffset.toLong(),
                    sentenceIds = segment.sentenceIds,
                )
            }
            pendingTtsSegments.addAll(newSegments)
        }
    }

    /**
     * Commit buffered TTS segments to the player (make them playable)
     */
    fun commitTtsSegments(documentId: String, startChar: Long) {
        internalScope.launch {
            val segmentsToCommit = pendingTtsSegments.filter { it.mediaId.contains(documentId) }
            if (segmentsToCommit.isEmpty()) return@launch

            val mediaItems = segmentsToCommit.map { segment ->
                MediaItem.Builder()
                    .setMediaId(segment.mediaId)
                    .setUri(Uri.fromFile(segment.file))
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(segment.title)
                            .setArtist("Read Aloud")
                            .setAlbumTitle("Read Aloud")
                            .setGenre("read_aloud")
                            .build(),
                    )
                    .build()
            }

            // Add to player at the end of current queue
            val insertIndex = player.mediaItemCount
            player.addMediaItems(insertIndex, mediaItems)

            // Remove committed segments from pending
            pendingTtsSegments.removeAll(segmentsToCommit)
        }
    }

    /**
     * Clear all pending TTS segments (e.g., on seek, voice change, stop)
     */
    fun clearPendingTtsSegments(documentId: String? = null) {
        internalScope.launch {
            if (documentId != null) {
                pendingTtsSegments.removeAll { it.mediaId.contains(documentId) }
            } else {
                pendingTtsSegments.clear()
            }
        }
    }

    /**
     * Get the current pending TTS segments for a document
     */
    fun getPendingTtsCount(documentId: String): Int {
        return pendingTtsSegments.count { it.mediaId.contains(documentId) }
    }

    /**
     * Check if we have buffered TTS segments ready
     */
    fun hasPendingTts(documentId: String): Boolean {
        return pendingTtsSegments.any { it.mediaId.contains(documentId) }
    }

    fun shutdown() {
        scopeJob.cancel()
    }
}