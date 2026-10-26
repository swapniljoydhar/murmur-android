package com.joy.murmur.data.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackRestorePolicyTest {
    @Test
    fun transientReadAloudStartIsRemovedAndSavedPositionIsKeptPaused() {
        val segment = queueItem("read_aloud|doc-a|0|12000|continuous|200|cache")
        val request = queueItem("read_aloud_start|doc-a|12000|text/plain|true|continuous")
        val state = PlaybackStateEntity(currentMediaId = segment.mediaId, positionMs = 8_500L, isPlaying = true)

        val plan = PlaybackRestorePolicy.plan(listOf(segment, request), state)

        assertEquals(listOf(segment), plan.queue)
        assertEquals(segment.mediaId, plan.state?.currentMediaId)
        assertEquals(8_500L, plan.state?.positionMs)
        assertFalse(plan.state?.isPlaying ?: true)
    }

    @Test
    fun transientCurrentItemFallsBackToRestorableQueuePausedAtStart() {
        val music = queueItem("music-track")
        val request = queueItem("read_aloud_start|doc-a|12000|text/plain|true|continuous")
        val state = PlaybackStateEntity(currentMediaId = request.mediaId, positionMs = 4_000L, isPlaying = true)

        val plan = PlaybackRestorePolicy.plan(listOf(music, request), state)

        assertEquals(listOf(music), plan.queue)
        assertEquals(music.mediaId, plan.state?.currentMediaId)
        assertEquals(0L, plan.state?.positionMs)
        assertFalse(plan.state?.isPlaying ?: true)
    }

    @Test
    fun queueContainingOnlyTransientReadAloudRequestRestoresAsEmpty() {
        val request = queueItem("read_aloud_start|doc-a|12000|text/plain|true|continuous")

        val plan = PlaybackRestorePolicy.plan(
            listOf(request),
            PlaybackStateEntity(currentMediaId = request.mediaId, positionMs = 4_000L, isPlaying = true),
        )

        assertEquals(emptyList<QueueItemEntity>(), plan.queue)
        assertNull(plan.state?.currentMediaId)
        assertEquals(0L, plan.state?.positionMs)
        assertFalse(plan.state?.isPlaying ?: true)
    }

    private fun queueItem(mediaId: String) = QueueItemEntity(
        mediaId = mediaId,
        uri = "file:///cache/$mediaId.wav",
        title = mediaId,
        contentType = "read_aloud",
        queueIndex = 0,
    )
}
