package com.joy.murmur.playback

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.text.Regex

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

    fun markdownForSpeech(source: String): String = source
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")
        .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
        .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
        .replace(Regex("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)"), "")
        .replace(Regex("(?m)^\\s*>\\s?"), "")
        .replace(Regex("[*_~`]"), "")
}