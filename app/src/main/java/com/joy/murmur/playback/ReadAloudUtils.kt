package com.joy.murmur.playback

import java.nio.charset.StandardCharsets
import kotlin.text.Regex

/**
 * Pure text helpers for Read Aloud. Media ID encoding lives in ReadAloudMediaId.kt and is the only place
 * that may build or parse a Read Aloud media ID.
 */
object ReadAloudUtils {

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

    /** The exact text handed to the speech engine for a page, so offsets can be mapped back. */
    fun speechTextFor(pageText: String, mimeType: String?): String =
        if (mimeType == "text/markdown") markdownForSpeech(pageText) else pageText

    /**
     * Builds a mapping from rendered text index to speech text index.
     *
     * [markdownForSpeech] preserves length by replacing markup with spaces, so a surviving character
     * keeps its own index and stripped markup maps to -1. This must stay index-preserving: the
     * generator's `sourceOffset` and `sentenceStartOffsets` are plain string indices into
     * [speechText], so any compression here (an earlier version dropped every space from the
     * numbering) shifts highlighting and tap-to-read by the number of preceding spaces.
     */
    fun buildSpokenIndexMap(sourcePageText: String, speechText: String = markdownForSpeech(sourcePageText)): IntArray {
        require(speechText.length == sourcePageText.length) { "speech text must preserve source offsets" }
        return IntArray(sourcePageText.length) { index ->
            if (speechText[index] == sourcePageText[index]) index else -1
        }
    }

    /**
     * Maps a tap offset in the rendered text to the spoken (synthesized) offset.
     * Returns null when the tap landed on markup that is not spoken.
     */
    fun spokenOffsetForTap(tapOffsetInRendered: Int, spokenIndexMap: IntArray): Int? =
        spokenIndexMap.getOrNull(tapOffsetInRendered)?.takeIf { it >= 0 }

    /**
     * Builds the inverse mapping from spoken text index to rendered text index.
     * Since `buildSpokenIndexMap` gives renderedIndex -> spokenIndex (or -1 for syntax),
     * this returns spokenIndex -> renderedIndex (the first rendered index that maps to it).
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
        // Fill in any gaps (shouldn't happen with correct mapping, but defensive)
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
