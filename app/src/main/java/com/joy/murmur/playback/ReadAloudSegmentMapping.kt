package com.joy.murmur.playback

import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity
import com.joy.murmur.data.speech.GeneratedSpeechFile
import kotlinx.serialization.json.Json

/** Where a tap in the reader should send playback. */
data class ReadAloudTapTarget(
    val queueItemIndex: Int,
    val positionMs: Long,
)

/**
 * The mapping between a reader page, the speech segments synthesized for it, and the reader's taps.
 *
 * Segment maps were previously written only by [PlaybackService]'s continuation path, so the page the
 * reader synthesized itself had no map: no highlighting, and tap-to-read never ran. Both writers now
 * build their rows here, from the same offsets the generator reported.
 *
 * Tap resolution used to return the current segment's offset whatever sentence was tapped, which
 * replayed the current segment from its start. Now the tapped sentence picks the sentence, and the
 * seek position is the sentence's share of the segment's duration. The estimate is even, not
 * per-sentence: system TTS reports no sentence timing, so the last sentence of a long segment can be
 * a second or two off. Crossing into a segment that is not the current one lands at that segment's
 * start, because its map is not loaded yet.
 *
 * Everything here is pure so it can be tested without a speech engine or a database.
 */
object ReadAloudSegmentMapping {

    fun buildEntities(
        documentId: String,
        pageStart: Long,
        pageLength: Int,
        generated: List<GeneratedSpeechFile>,
    ): List<ReadAloudSegmentMapEntity> = generated.map { segment ->
        val sentenceStartOffsets = segment.sentenceStartOffsets.map { it.toLong() }
        // The final entry is the end of the segment, so the offsets list is always one longer than
        // the sentence list and highlighting can read offsets[i] until offsets[i + 1].
        val segmentEndOffset = segment.sourceOffset.toLong() + segment.spokenLength
        ReadAloudSegmentMapEntity(
            documentId = documentId,
            sourceOffset = segment.sourceOffset.toLong(),
            sentenceIdsJson = Json.encodeToString(segment.sentenceIds),
            sentenceStartOffsetsJson = Json.encodeToString(sentenceStartOffsets + segmentEndOffset),
            sourcePageStart = pageStart,
            sourcePageLength = pageLength,
            segmentDurationMs = segment.durationMs.coerceAtLeast(1L),
        )
    }

    /** Sentence start offsets for a segment, ending with the segment's end offset. */
    fun sentenceStartOffsets(segmentMap: ReadAloudSegmentMapEntity): List<Long> =
        runCatching { Json.decodeFromString<List<Long>>(segmentMap.sentenceStartOffsetsJson) }
            .getOrDefault(emptyList())

    /** Number of sentences in a segment. */
    fun sentenceCount(offsets: List<Long>): Int = (offsets.size - 1).coerceAtLeast(1)

    /** Index of the segment that covers [tapSourceOffset]: the last one that starts at or before it. */
    fun queueItemIndexFor(queueItemSourceOffsets: List<Long?>, tapSourceOffset: Long): Int? =
        queueItemSourceOffsets.indices
            .filter { index -> queueItemSourceOffsets[index]?.let { it <= tapSourceOffset } == true }
            .maxByOrNull { index -> queueItemSourceOffsets[index] ?: Long.MIN_VALUE }

    /** Index of the sentence containing [tapSourceOffset], or null when the tap is past the segment. */
    fun sentenceIndexFor(offsets: List<Long>, tapSourceOffset: Long): Int? {
        if (offsets.isEmpty()) return null
        if (tapSourceOffset < offsets.first()) return null
        var index = 0
        for (candidate in offsets.indices) {
            if (offsets[candidate] <= tapSourceOffset) index = candidate else break
        }
        // The last entry is the segment end, not a sentence.
        return index.takeIf { it < sentenceCount(offsets) }
    }

    /** Position to seek to for a sentence, assuming evenly distributed sentence durations. */
    fun positionMsForSentence(offsets: List<Long>, sentenceIndex: Int, durationMs: Long): Long {
        if (durationMs <= 0L) return 0L
        val count = sentenceCount(offsets)
        val safeIndex = sentenceIndex.coerceIn(0, count - 1)
        return (durationMs * safeIndex / count).coerceIn(0L, durationMs - 1L)
    }

    fun resolveTapTarget(
        tapSourceOffset: Long,
        queueItemSourceOffsets: List<Long?>,
        currentItemIndex: Int,
        currentSegmentMap: ReadAloudSegmentMapEntity?,
    ): ReadAloudTapTarget? {
        val target = queueItemIndexFor(queueItemSourceOffsets, tapSourceOffset) ?: return null
        val positionMs = when {
            // The map for another segment is not loaded; start that segment and let the reader
            // reload its map from the transition.
            target != currentItemIndex -> 0L
            currentSegmentMap == null -> 0L
            else -> {
                val offsets = sentenceStartOffsets(currentSegmentMap)
                val sentenceIndex = sentenceIndexFor(offsets, tapSourceOffset)
                if (sentenceIndex == null) {
                    0L
                } else {
                    positionMsForSentence(offsets, sentenceIndex, currentSegmentMap.segmentDurationMs)
                }
            }
        }
        return ReadAloudTapTarget(target, positionMs)
    }
}
