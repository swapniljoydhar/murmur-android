package com.joy.murmur.playback

import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity
import com.joy.murmur.data.speech.GeneratedSpeechFile
import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers the two halves of Read Aloud that were broken: the map the reader now writes for its own
 * page, and the tap that used to ignore which sentence was tapped.
 */
class ReadAloudSegmentMappingTest {

    private fun segmentMap(offsets: List<Long>, durationMs: Long = 4_000L) = ReadAloudSegmentMapEntity(
        documentId = "doc",
        sourceOffset = offsets.firstOrNull() ?: 0L,
        sentenceIdsJson = Json.encodeToString(List((offsets.size - 1).coerceAtLeast(0)) { "s${it + 1}" }),
        sentenceStartOffsetsJson = Json.encodeToString(offsets),
        sourcePageStart = 0L,
        sourcePageLength = 1_000,
        segmentDurationMs = durationMs,
    )

    private fun generatedFile(sourceOffset: Int, offsets: List<Int>, durationMs: Long) = GeneratedSpeechFile(
        file = File("segment.wav"),
        segmentIndex = 0,
        sourceOffset = sourceOffset,
        sentenceIds = offsets.mapIndexed { index, _ -> "s${index + 1}" },
        sentenceStartOffsets = offsets,
        spokenLength = 100,
        durationMs = durationMs,
    )

    @Test
    fun buildEntitiesStoresGeneratorOffsetsPlusTheSegmentEnd() {
        val entities = ReadAloudSegmentMapping.buildEntities(
            documentId = "doc",
            pageStart = 500L,
            pageLength = 2_000,
            generated = listOf(generatedFile(sourceOffset = 100, offsets = listOf(100, 150), durationMs = 2_500L)),
        )
        assertEquals(1, entities.size)
        val entity = entities.single()
        assertEquals("doc", entity.documentId)
        assertEquals(100L, entity.sourceOffset)
        assertEquals(500L, entity.sourcePageStart)
        assertEquals(2_000, entity.sourcePageLength)
        assertEquals(2_500L, entity.segmentDurationMs)
        assertEquals(listOf("s1", "s2"), Json.decodeFromString<List<String>>(entity.sentenceIdsJson))
        // 100 + spokenLength(100) closes the list, so highlighting can read offsets[i + 1].
        assertEquals(listOf(100L, 150L, 200L), ReadAloudSegmentMapping.sentenceStartOffsets(entity))
    }

    @Test
    fun buildEntitiesKeepsAZeroDurationFromBeingPlayableAsZero() {
        val entity = ReadAloudSegmentMapping.buildEntities(
            "doc",
            0L,
            10,
            listOf(generatedFile(sourceOffset = 0, offsets = listOf(0), durationMs = 0L)),
        ).single()
        assertEquals(1L, entity.segmentDurationMs)
    }

    @Test
    fun sentenceIndexForFindsTheContainingSentence() {
        val offsets = listOf(0L, 10L, 20L, 30L)
        assertEquals(0, ReadAloudSegmentMapping.sentenceIndexFor(offsets, 0L))
        assertEquals(0, ReadAloudSegmentMapping.sentenceIndexFor(offsets, 9L))
        assertEquals(1, ReadAloudSegmentMapping.sentenceIndexFor(offsets, 10L))
        assertEquals(2, ReadAloudSegmentMapping.sentenceIndexFor(offsets, 25L))
        assertNull("the last entry is the segment end, not a sentence", ReadAloudSegmentMapping.sentenceIndexFor(offsets, 30L))
    }

    @Test
    fun queueItemIndexForPicksTheSegmentThatCoversTheTap() {
        val offsets = listOf(0L, 100L, 250L, null)
        assertEquals(0, ReadAloudSegmentMapping.queueItemIndexFor(offsets, 0L))
        assertEquals(0, ReadAloudSegmentMapping.queueItemIndexFor(offsets, 99L))
        assertEquals(1, ReadAloudSegmentMapping.queueItemIndexFor(offsets, 100L))
        assertEquals(2, ReadAloudSegmentMapping.queueItemIndexFor(offsets, 260L))
        assertNull("a tap before the first segment has no target", ReadAloudSegmentMapping.queueItemIndexFor(offsets, -1L))
    }

    @Test
    fun resolveTapTargetSeeksToTheTappedSentenceInsideTheCurrentSegment() {
        val map = segmentMap(listOf(500L, 600L, 700L, 800L), durationMs = 4_000L)
        val target = ReadAloudSegmentMapping.resolveTapTarget(
            tapSourceOffset = 650L,
            queueItemSourceOffsets = listOf(0L, 500L, 900L),
            currentItemIndex = 1,
            currentSegmentMap = map,
        )
        assertEquals(1, target?.queueItemIndex)
        // Second of three sentences of a 4 000 ms segment.
        assertEquals(1_333L, target?.positionMs)
    }

    @Test
    fun resolveTapTargetJumpsToAnotherSegmentStart() {
        val target = ReadAloudSegmentMapping.resolveTapTarget(
            tapSourceOffset = 950L,
            queueItemSourceOffsets = listOf(0L, 500L, 900L),
            currentItemIndex = 1,
            currentSegmentMap = segmentMap(listOf(500L, 600L, 700L, 800L)),
        )
        assertEquals(2, target?.queueItemIndex)
        assertEquals(0L, target?.positionMs)
    }

    @Test
    fun resolveTapTargetWithoutAMapStillSelectsTheSegment() {
        val target = ReadAloudSegmentMapping.resolveTapTarget(
            tapSourceOffset = 120L,
            queueItemSourceOffsets = listOf(0L, 100L),
            currentItemIndex = 1,
            currentSegmentMap = null,
        )
        assertEquals(1, target?.queueItemIndex)
        assertEquals(0L, target?.positionMs)
    }

    @Test
    fun resolveTapTargetIgnoresTapsOutsideAnySegment() {
        assertNull(
            ReadAloudSegmentMapping.resolveTapTarget(
                tapSourceOffset = 50L,
                queueItemSourceOffsets = listOf(100L, 200L),
                currentItemIndex = 0,
                currentSegmentMap = null,
            ),
        )
    }

    @Test
    fun seekPositionAlwaysStaysInsideTheSegment() {
        val offsets = listOf(0L, 10L, 20L, 30L)
        assertEquals(0L, ReadAloudSegmentMapping.positionMsForSentence(offsets, 0, 4_000L))
        assertEquals(1_333L, ReadAloudSegmentMapping.positionMsForSentence(offsets, 1, 4_000L))
        assertEquals(2_666L, ReadAloudSegmentMapping.positionMsForSentence(offsets, 2, 4_000L))
        // Out-of-range indexes are clamped rather than producing a seek past the end.
        assertEquals(2_666L, ReadAloudSegmentMapping.positionMsForSentence(offsets, 99, 4_000L))
        assertEquals(0L, ReadAloudSegmentMapping.positionMsForSentence(offsets, 0, 0L))
    }

    @Test
    fun corruptOffsetJsonFallsBackInsteadOfCrashingTheReader() {
        val corrupt = ReadAloudSegmentMapEntity(
            documentId = "doc",
            sourceOffset = 0L,
            sentenceIdsJson = "[]",
            sentenceStartOffsetsJson = "{not json",
            sourcePageStart = 0L,
            sourcePageLength = 10,
            segmentDurationMs = 1_000L,
        )
        assertEquals(emptyList<Long>(), ReadAloudSegmentMapping.sentenceStartOffsets(corrupt))
        val target = ReadAloudSegmentMapping.resolveTapTarget(
            tapSourceOffset = 5L,
            queueItemSourceOffsets = listOf(0L),
            currentItemIndex = 0,
            currentSegmentMap = corrupt,
        )
        assertEquals(0, target?.queueItemIndex)
        assertEquals(0L, target?.positionMs)
    }
}
