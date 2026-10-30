package com.joy.murmur.playback

import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity
import kotlinx.serialization.json.Json

/**
 * Utilities for Read Aloud text processing and canonical media ID delegation.
 */
object ReadAloudUtils {

    fun readAloudMediaId(
        documentId: String,
        pageStart: Long,
        pageLength: Int,
        continuous: Boolean,
        sourceOffset: Long,
        cacheKey: String,
    ): String = com.joy.murmur.playback.readAloudMediaId(
        documentId = documentId,
        pageStart = pageStart,
        pageLength = pageLength,
        continuous = continuous,
        sourceOffset = sourceOffset,
        cacheKey = cacheKey,
    )

    fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? =
        com.joy.murmur.playback.parseReadAloudMediaId(mediaId)

    /**
     * Removes Markdown syntax without changing character indexes. Speech chunk offsets
     * are persisted against the original document, so every replacement must preserve
     * the source string's length.
     */
    fun markdownForSpeech(source: String): String {
        var output = source
            .replace(Regex("```[\\s\\S]*?```")) { " ".repeat(it.value.length) }
            .replace(Regex("!\\[([^]]*)]\\([^)]+\\)")) { match ->
                replaceMarkupWithLabel(match.value, labelStart = 2, labelLength = match.groups[1]?.value?.length ?: 0)
            }
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)")) { match ->
                replaceMarkupWithLabel(match.value, labelStart = 1, labelLength = match.groups[1]?.value?.length ?: 0)
            }
            .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*")) { " ".repeat(it.value.length) }
            .replace(Regex("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)")) { " ".repeat(it.value.length) }
            .replace(Regex("(?m)^\\s*>\\s?")) { " ".repeat(it.value.length) }
        output = output.map { char -> if (char in "*_~`") ' ' else char }.joinToString("")
        check(output.length == source.length) { "Markdown speech cleanup must preserve source offsets" }
        return output
    }

    private fun replaceMarkupWithLabel(markup: String, labelStart: Int, labelLength: Int): String {
        val output = CharArray(markup.length) { ' ' }
        val safeStart = labelStart.coerceIn(0, markup.length)
        val safeEnd = (safeStart + labelLength).coerceIn(safeStart, markup.length)
        markup.substring(safeStart, safeEnd).toCharArray().copyInto(output, safeStart)
        return output.concatToString()
    }

    /**
     * Builds a mapping from rendered text index to spoken text index.
     */
    fun buildSpokenIndexMap(sourcePageText: String): IntArray {
        val spokenText = markdownForSpeech(sourcePageText)
        val map = IntArray(sourcePageText.length) { -1 }
        var spokenIdx = 0
        for (i in sourcePageText.indices) {
            if (spokenText[i] != ' ') {
                map[i] = spokenIdx
                spokenIdx++
            }
        }
        return map
    }

    /**
     * Maps a tap offset in the rendered text to the segment's sourceOffset.
     */
    fun mapTapOffsetToSegment(
        tapOffsetInRendered: Int,
        spokenIndexMap: IntArray,
        segmentMap: ReadAloudSegmentMapEntity,
    ): Long? {
        if (tapOffsetInRendered !in 0..spokenIndexMap.lastIndex) return null
        val spokenIdx = spokenIndexMap[tapOffsetInRendered]
        if (spokenIdx < 0) return null

        val sentenceOffsets = Json.decodeFromString<List<Long>>(segmentMap.sentenceStartOffsetsJson)
        for (idx in sentenceOffsets.indices) {
            if (sentenceOffsets[idx] > spokenIdx) {
                break
            }
        }
        return segmentMap.sourceOffset
    }

    /**
     * Builds the inverse mapping from spoken text index to rendered text index.
     */
    fun buildInverseSpokenIndexMap(spokenIndexMap: IntArray): IntArray {
        val maxSpokenIdx = spokenIndexMap.maxOrNull() ?: -1
        if (maxSpokenIdx < 0) return IntArray(0)
        val inverseMap = IntArray(maxSpokenIdx + 1) { -1 }
        for (renderedIdx in spokenIndexMap.indices) {
            val spokenIdx = spokenIndexMap[renderedIdx]
            if (spokenIdx >= 0 && inverseMap[spokenIdx] == -1) {
                inverseMap[spokenIdx] = renderedIdx
            }
        }
        var lastKnown = 0
        for (i in inverseMap.indices) {
            if (inverseMap[i] >= 0) {
                lastKnown = inverseMap[i]
            } else {
                inverseMap[i] = lastKnown
            }
        }
        return inverseMap
    }
}
