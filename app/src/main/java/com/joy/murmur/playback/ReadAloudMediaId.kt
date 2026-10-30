package com.joy.murmur.playback

import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Canonical Media ID codec for Read Aloud segments and start markers.
 * Every string field is UTF-8 percent-encoded so pipe delimiters (`|`), percent signs (`%`),
 * spaces, and Unicode characters never shift downstream fields.
 */
data class ReadAloudSegmentInfo(
    val documentId: String,
    val pageStart: Long,
    val pageLength: Int,
    val continuous: Boolean,
    val sourceOffset: Long,
    val cacheKey: String,
    val sectionId: String? = null,
    val paragraphId: String? = null,
    val sentenceRangeStart: String? = null,
    val sentenceRangeEnd: String? = null,
    val textHash: String? = null,
    val engine: String? = "android_tts",
    val voice: String? = null,
    val locale: String? = null,
    val documentFingerprint: String? = null,
    val parseVersion: Int = 1,
)

data class ReadAloudStartInfo(
    val documentId: String,
    val startChar: Long,
    val mimeType: String,
    val strictOffline: Boolean,
    val continuous: Boolean,
)

internal fun encodeMediaIdField(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

internal fun decodeMediaIdField(encoded: String): String? = runCatching {
    URLDecoder.decode(encoded.replace("+", "%2B"), StandardCharsets.UTF_8.name())
}.getOrNull()

fun readAloudMediaId(
    documentId: String,
    pageStart: Long,
    pageLength: Int,
    continuous: Boolean,
    sourceOffset: Long,
    cacheKey: String,
    sectionId: String? = null,
    paragraphId: String? = null,
    sentenceRangeStart: String? = null,
    sentenceRangeEnd: String? = null,
    textHash: String? = null,
    engine: String? = "android_tts",
    voice: String? = null,
    locale: String? = null,
    documentFingerprint: String? = null,
    parseVersion: Int = 1,
): String = listOf(
    "read_aloud",
    encodeMediaIdField(documentId),
    pageStart.toString(),
    pageLength.toString(),
    if (continuous) "continuous" else "page",
    sourceOffset.toString(),
    encodeMediaIdField(cacheKey),
    encodeMediaIdField(sectionId.orEmpty()),
    encodeMediaIdField(paragraphId.orEmpty()),
    encodeMediaIdField(sentenceRangeStart.orEmpty()),
    encodeMediaIdField(sentenceRangeEnd.orEmpty()),
    encodeMediaIdField(textHash.orEmpty()),
    encodeMediaIdField(engine ?: "android_tts"),
    encodeMediaIdField(voice.orEmpty()),
    encodeMediaIdField(locale.orEmpty()),
    encodeMediaIdField(documentFingerprint.orEmpty()),
    parseVersion.toString(),
).joinToString("|")

fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? {
    val fields = mediaId.split('|')
    if (fields.size !in 7..17 || fields[0] != "read_aloud") return null
    val documentId = decodeMediaIdField(fields[1])?.takeIf(String::isNotBlank) ?: return null
    val pageStart = fields[2].toLongOrNull() ?: return null
    val pageLength = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val continuous = when (fields[4]) {
        "continuous" -> true
        "page" -> false
        else -> return null
    }
    val sourceOffset = fields[5].toLongOrNull() ?: return null
    if (!isValidReadAloudSourceRange(pageStart, pageLength, sourceOffset)) return null
    val cacheKey = decodeMediaIdField(fields[6]) ?: return null

    fun optionalDecoded(index: Int): String? =
        fields.getOrNull(index)?.takeIf { it.isNotEmpty() }?.let(::decodeMediaIdField)?.takeIf { it.isNotBlank() }

    val parseVersion = fields.getOrNull(16)?.let { it.toIntOrNull() ?: return null } ?: 1

    return ReadAloudSegmentInfo(
        documentId = documentId,
        pageStart = pageStart,
        pageLength = pageLength,
        continuous = continuous,
        sourceOffset = sourceOffset,
        cacheKey = cacheKey,
        sectionId = optionalDecoded(7),
        paragraphId = optionalDecoded(8),
        sentenceRangeStart = optionalDecoded(9),
        sentenceRangeEnd = optionalDecoded(10),
        textHash = optionalDecoded(11),
        engine = optionalDecoded(12) ?: "android_tts",
        voice = optionalDecoded(13),
        locale = optionalDecoded(14),
        documentFingerprint = optionalDecoded(15),
        parseVersion = parseVersion,
    )
}

fun readAloudStartMediaId(
    documentId: String,
    startChar: Long,
    mimeType: String,
    strictOffline: Boolean,
    continuous: Boolean,
): String = listOf(
    "read_aloud_start",
    encodeMediaIdField(documentId),
    startChar.coerceAtLeast(0L).toString(),
    encodeMediaIdField(mimeType),
    strictOffline.toString(),
    if (continuous) "continuous" else "page",
).joinToString("|")

fun parseReadAloudStartMediaId(mediaId: String): ReadAloudStartInfo? {
    val fields = mediaId.split('|')
    if (fields.size != 6 || fields[0] != "read_aloud_start") return null
    val documentId = decodeMediaIdField(fields[1])?.takeIf(String::isNotBlank) ?: return null
    val startChar = fields[2].toLongOrNull()?.takeIf { it >= 0L } ?: return null
    val mimeType = decodeMediaIdField(fields[3])?.takeIf(String::isNotBlank) ?: return null
    val strictOffline = when (fields[4]) {
        "true" -> true
        "false" -> false
        else -> return null
    }
    val continuous = when (fields[5]) {
        "continuous" -> true
        "page" -> false
        else -> return null
    }
    return ReadAloudStartInfo(
        documentId = documentId,
        startChar = startChar,
        mimeType = mimeType,
        strictOffline = strictOffline,
        continuous = continuous,
    )
}

internal fun isValidReadAloudSourceRange(pageStart: Long, pageLength: Int, sourceOffset: Long): Boolean =
    pageStart >= 0L &&
        pageStart < Long.MAX_VALUE &&
        pageLength >= 0 &&
        pageStart <= Long.MAX_VALUE - pageLength &&
        sourceOffset in 0L..pageLength.toLong()

fun markdownForSpeech(source: String): String = ReadAloudUtils.markdownForSpeech(source)

fun generateDocumentFingerprint(uri: Uri, lastModified: Long, size: Long): String {
    val input = "${uri}|$lastModified|$size"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(16)
}
