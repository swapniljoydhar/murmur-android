package com.joy.murmur.playback

import android.net.Uri

data class ReadAloudSegmentInfo(
    val documentId: String,
    val pageStart: Long,
    val pageLength: Int,
    val continuous: Boolean,
    val sourceOffset: Long,
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
    Uri.encode(documentId),
    pageStart.toString(),
    pageLength.toString(),
    if (continuous) "continuous" else "page",
    sourceOffset.toString(),
    cacheKey,
).joinToString("|")

fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? {
    val fields = mediaId.split('|')
    if (fields.size < 7 || fields[0] != "read_aloud") return null
    val pageStart = fields[2].toLongOrNull() ?: return null
    val pageLength = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val continuous = when (fields[4]) {
        "continuous" -> true
        "page" -> false
        else -> return null
    }
    val sourceOffset = fields[5].toLongOrNull() ?: return null
    return ReadAloudSegmentInfo(Uri.decode(fields[1]), pageStart, pageLength, continuous, sourceOffset)
}

fun markdownForSpeech(source: String): String = source
    .replace(Regex("```[\\s\\S]*?```"), " ")
    .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")
    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    .replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    .replace(Regex("(?m)^\\s*(?:[-*+]\\s+|\\d+[.)]\\s+)"), "")
    .replace(Regex("(?m)^\\s*>\\s?"), "")
    .replace(Regex("[*_~`]"), "")
