package com.joy.murmur.playback

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.text.Regex
import kotlinx.serialization.json.Json
import com.joy.murmur.data.playback.ReadAloudSegmentMapEntity

/**
 * Utilities for Read Aloud media ID encoding/decoding and text processing.
 */
object ReadAloudUtils {

    data class ReadAloudSegmentInfo(
        val documentId: String,
        val pageStart: Long,
        val pageLength: Int,
        val continuous: Boolean,
        val sourceOffset: Long,
        val cacheKey: String = "",
    )

    fun readAloudMediaId(
        documentId: String,
        pageStart: Long,
        pageLength: Int,
        continuous: Boolean,
        sourceOffset: Long,
        cacheKey: String,
    ): String = listOf(
        "read_aloud",
        encodeForMediaId(documentId),
        pageStart.toString(),
        pageLength.toString(),
        if (continuous) "continuous" else "page",
        sourceOffset.toString(),
        encodeForMediaId(cacheKey),
    ).joinToString("|")

    fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? {
        val fields = mediaId.split('|', limit = 7)
        if (fields.size != 7 || fields[0] != "read_aloud") return null
        val pageStart = fields[2].toLongOrNull() ?: return null
        val pageLength = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val continuous = when (fields[4]) {
            "continuous" -> true
            "page" -> false
            else -> return null
        }
        val sourceOffset = fields[5].toLongOrNull() ?: return null
        val cacheKey = decodeFromMediaId(fields[6])
        return ReadAloudSegmentInfo(decodeFromMediaId(fields[1]), pageStart, pageLength, continuous, sourceOffset, cacheKey)
    }

    /** Simple encoding for media IDs - uses Android Uri when available, falls back to basic encoding */
    private fun encodeForMediaId(input: String): String = try {
        Uri.encode(input)
    } catch (_: Exception) {
        // Fallback for unit tests where Uri.encode is not mocked
        input.replace(" ", "%20")
            .replace("&", "%26")
            .replace("!", "%21")
            .replace("#", "%23")
            .replace("\"", "%22")
            .replace("'", "%27")
            .replace("(", "%28")
            .replace(")", "%29")
            .replace("*", "%2A")
            .replace("+", "%2B")
            .replace(",", "%2C")
            .replace("/", "%2F")
            .replace(":", "%3A")
            .replace(";", "%3B")
            .replace("=", "%3D")
            .replace("?", "%3F")
            .replace("@", "%40")
            .replace("[", "%5B")
            .replace("]", "%5D")
    }

    /** Simple decoding for media IDs */
    private fun decodeFromMediaId(input: String): String = try {
        Uri.decode(input)
    } catch (_: Exception) {
        // Fallback for unit tests
        input.replace("%20", " ")
            .replace("%26", "&")
            .replace("%21", "!")
            .replace("%23", "#")
            .replace("%22", "\"")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%2A", "*")
            .replace("%2B", "+")
            .replace("%2C", ",")
            .replace("%2F", "/")
            .replace("%3A", ":")
            .replace("%3B", ";")
            .replace("%3D", "=")
            .replace("%3F", "?")
            .replace("%40", "@")
            .replace("%5B", "[")
            .replace("%5D", "]")
    }

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
     * Since `markdownForSpeech` preserves string length by replacing Markdown syntax with spaces,
     * this map tells us which rendered characters correspond to spoken characters.
     * Returns -1 for syntax characters (which are replaced with spaces in spoken text).
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
     * Returns the sourceOffset if the tap lands on a spoken character, null if on syntax.
     */
    fun mapTapOffsetToSegment(
        tapOffsetInRendered: Int,
        spokenIndexMap: IntArray,
        segmentMap: ReadAloudSegmentMapEntity,
    ): Long? {
        if (tapOffsetInRendered !in 0..spokenIndexMap.lastIndex) return null
        val spokenIdx = spokenIndexMap[tapOffsetInRendered]
        if (spokenIdx < 0) return null  // tapped on syntax char
        
        val sentenceOffsets = Json.decodeFromString<List<Long>>(segmentMap.sentenceStartOffsetsJson)
        var sentenceIdx = 0
        for (idx in sentenceOffsets.indices) {
            if (sentenceOffsets[idx] <= spokenIdx) {
                sentenceIdx = idx
            } else {
                break
            }
        }
        return segmentMap.sourceOffset
    }

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
