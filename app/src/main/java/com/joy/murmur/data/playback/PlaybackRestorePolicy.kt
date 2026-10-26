package com.joy.murmur.data.playback

internal data class PlaybackRestorePlan(
    val queue: List<QueueItemEntity>,
    val state: PlaybackStateEntity?,
)

/** Restores durable playback state without replaying transient Read Aloud generation requests. */
internal object PlaybackRestorePolicy {
    fun plan(queue: List<QueueItemEntity>, state: PlaybackStateEntity?): PlaybackRestorePlan {
        val restorableQueue = queue.filterNot { it.mediaId.startsWith("read_aloud_start|") }
        val currentMediaId = state?.currentMediaId?.takeIf { id -> restorableQueue.any { it.mediaId == id } }
            ?: restorableQueue.firstOrNull()?.mediaId
        val currentPosition = state?.takeIf { currentMediaId == it.currentMediaId }?.positionMs ?: 0L
        return PlaybackRestorePlan(
            queue = restorableQueue,
            state = state?.copy(currentMediaId = currentMediaId, positionMs = currentPosition, isPlaying = false),
        )
    }
}
