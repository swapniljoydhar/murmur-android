package com.joy.murmur.playback

import android.net.Uri
import com.joy.murmur.domain.documents.DocumentStructure
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Media ID format for Read Aloud segments.
 * Includes full structural identity for sentence-level synchronization and cache keys.
 *
 * Format: read_aloud|documentId|pageStart|pageLength|continuous|sourceOffset|cacheKey|sectionId|paragraphId|sentenceRangeStart|sentenceRangeEnd|textHash|engine|voice|locale
 */
data class ReadAloudSegmentInfo(
    val documentId: String,
    val pageStart: Long,
    val pageLength: Int,
    val continuous: Boolean,
    val sourceOffset: Long,
    val cacheKey: String,
    // Structural identity fields for sentence synchronization
    val sectionId: String? = null,
    val paragraphId: String? = null,
    val sentenceRangeStart: String? = null,
    val sentenceRangeEnd: String? = null,
    val textHash: String? = null,
    // Synthesis identity fields
    val engine: String? = null,
    val voice: String? = null,
    val locale: String? = null,
    // Document fingerprint for cache invalidation
    val documentFingerprint: String? = null,
    val parseVersion: Int = 1,
)

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
    engine: String? = null,
    voice: String? = null,
    locale: String? = null,
    documentFingerprint: String? = null,
    parseVersion: Int = 1,
): String = listOf(
    "read_aloud",
    Uri.encode(documentId),
    pageStart.toString(),
    pageLength.toString(),
    if (continuous) "continuous" else "page",
    sourceOffset.toString(),
    cacheKey,
    sectionId.orEmpty(),
    paragraphId.orEmpty(),
    sentenceRangeStart.orEmpty(),
    sentenceRangeEnd.orEmpty(),
    textHash.orEmpty(),
    engine.orEmpty(),
    voice.orEmpty(),
    locale.orEmpty(),
    documentFingerprint.orEmpty(),
    parseVersion.toString(),
).joinToString("|")

fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? {
    val fields = mediaId.split('|', limit = 18)
    val isNeural = fields.firstOrNull() == "neural_read_aloud_start"
    if (fields.size < 7 || (fields[0] != "read_aloud" && !isNeural)) return null
    val pageStart = fields[2].toLongOrNull() ?: return null
    val pageLength = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
    val continuous = when (fields[4]) {
        "continuous" -> true
        "page" -> false
        else -> return null
    }
    val sourceOffset = fields[5].toLongOrNull() ?: return null
    val cacheKey = fields[6]
    val sectionId = fields.getOrNull(7)?.takeIf { it.isNotBlank() }
    val paragraphId = fields.getOrNull(8)?.takeIf { it.isNotBlank() }
    val sentenceRangeStart = fields.getOrNull(9)?.takeIf { it.isNotBlank() }
    val sentenceRangeEnd = fields.getOrNull(10)?.takeIf { it.isNotBlank() }
    val textHash = fields.getOrNull(11)?.takeIf { it.isNotBlank() }
    val engine = fields.getOrNull(12)?.takeIf { it.isNotBlank() }
    val voice = fields.getOrNull(13)?.takeIf { it.isNotBlank() }
    val locale = fields.getOrNull(14)?.takeIf { it.isNotBlank() }
    val documentFingerprint = fields.getOrNull(15)?.takeIf { it.isNotBlank() }
    val parseVersion = fields.getOrNull(16)?.toIntOrNull() ?: 1

    return ReadAloudSegmentInfo(
        documentId = Uri.decode(fields[1]),
        pageStart = pageStart,
        pageLength = pageLength,
        continuous = continuous,
        sourceOffset = sourceOffset,
        cacheKey = cacheKey,
        sectionId = sectionId,
        paragraphId = paragraphId,
        sentenceRangeStart = sentenceRangeStart,
        sentenceRangeEnd = sentenceRangeEnd,
        textHash = textHash,
        engine = engine?.takeIf { isNeural } ?: "android_tts",
        voice = voice,
        locale = locale,
        documentFingerprint = documentFingerprint,
        parseVersion = parseVersion,
    )
}

/**
 * Creates a cache identity string from synthesis parameters.
 * This should match the cache key used in SystemTtsSpeechGenerator.
 */
fun createCacheIdentity(
    documentId: String,
    documentFingerprint: String,
    parseVersion: Int,
    sectionId: String?,
    paragraphId: String?,
    sentenceRangeStart: String?,
    sentenceRangeEnd: String?,
    textHash: String?,
    engine: String,
    engineVersion: String,
    voice: String,
    locale: String,
    pronunciationVersion: String,
    filterVersion: String,
    sampleRate: Int,
    channelCount: Int,
    bitDepth: Int,
): String {
    return listOf(
        documentId,
        documentFingerprint,
        parseVersion.toString(),
        sectionId.orEmpty(),
        paragraphId.orEmpty(),
        sentenceRangeStart.orEmpty(),
        sentenceRangeEnd.orEmpty(),
        textHash.orEmpty(),
        engine,
        engineVersion,
        voice,
        locale,
        pronunciationVersion,
        filterVersion,
        sampleRate.toString(),
        channelCount.toString(),
        bitDepth.toString(),
    ).joinToString("|")
}

/**
 * Markdown to speech text conversion with offset mapping.
 * Returns the cleaned text and a mapping from speech text offsets to source markdown offsets.
 */
fun markdownForSpeech(source: String): String = ReadAloudUtils.markdownForSpeech(source)

/**
 * Generates a document fingerprint from the source URI and metadata.
 * Used for cache invalidation when document changes.
 */
fun generateDocumentFingerprint(uri: Uri, lastModified: Long, size: Long): String {
    val input = "${uri.toString()}|$lastModified|$size"
    return MessageDigest.getInstance("SHA-256")
        .digest(input.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }.take(16)
}
