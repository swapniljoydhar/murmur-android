package com.joy.murmur.data.playback

import com.joy.murmur.playback.isReadAloudStartMediaId

internal data class PlaybackRestorePlan(
    val queue: List<QueueItemEntity>,
    val state: PlaybackStateEntity?,
)

/** Restores durable playback state without replaying transient Read Aloud generation requests. */
internal object PlaybackRestorePolicy {
    fun plan(queue: List<QueueItemEntity>, state: PlaybackStateEntity?): PlaybackRestorePlan {
        // A start request is transient: it asks the service to synthesize, so restoring it would replay
        // generation on every launch. Only finished segments belong in a restored queue.
        val restorableQueue = queue.filterNot { isReadAloudStartMediaId(it.mediaId) }
        val currentMediaId = state?.currentMediaId?.takeIf { id -> restorableQueue.any { it.mediaId == id } }
            ?: restorableQueue.firstOrNull()?.mediaId
        val currentPosition = state?.takeIf { currentMediaId == it.currentMediaId }?.positionMs ?: 0L
        return PlaybackRestorePlan(
            queue = restorableQueue,
            state = state?.copy(currentMediaId = currentMediaId, positionMs = currentPosition, isPlaying = false),
        )
    }
}
