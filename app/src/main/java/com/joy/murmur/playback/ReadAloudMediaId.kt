package com.joy.murmur.playback

import android.net.Uri
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The single source of truth for Read Aloud media IDs.
 *
 * Two IDs exist:
 *
 *  - a *segment* ID, attached to one generated audio file:
 *    `read_aloud|<version>|<documentId>|<pageStart>|<pageLength>|<mode>|<sourceOffset>|<cacheKey>`
 *  - a *start request* ID, attached to the transient item that asks the service to begin reading:
 *    `read_aloud_start|<version>|<documentId>|<startChar>|<mimeType>|<strictOffline>|<mode>`
 *
 * Every free-form field is percent-encoded by [MediaIdCodec] before it is joined with `|`, so a separator
 * can never appear inside a value and the field count is therefore a reliable structural check. Encoding is
 * implemented in plain Kotlin rather than `Uri.encode` so the same code path runs in JVM unit tests.
 */
data class ReadAloudSegmentInfo(
    val documentId: String,
    val pageStart: Long,
    val pageLength: Int,
    val continuous: Boolean,
    val sourceOffset: Long,
    val cacheKey: String,
)

/** A request for the playback service to synthesize and start reading a document. */
data class ReadAloudStartRequest(
    val documentId: String,
    val startChar: Long,
    val mimeType: String,
    val strictOffline: Boolean,
    val continuous: Boolean,
)

const val READ_ALOUD_PREFIX = "read_aloud"
const val READ_ALOUD_START_PREFIX = "read_aloud_start"

/** Current ID layout. Bumped whenever fields are added, removed or re-encoded. */
internal const val READ_ALOUD_ID_VERSION = "2"

private const val SEPARATOR = '|'
private const val V2_SEGMENT_FIELDS = 8
private const val V1_SEGMENT_FIELDS = 7
private const val V2_START_FIELDS = 7
private const val V1_START_FIELDS = 6
private const val MODE_CONTINUOUS = "continuous"
private const val MODE_PAGE = "page"

fun readAloudMediaId(
    documentId: String,
    pageStart: Long,
    pageLength: Int,
    continuous: Boolean,
    sourceOffset: Long,
    cacheKey: String,
): String = buildList {
    add(READ_ALOUD_PREFIX)
    add(READ_ALOUD_ID_VERSION)
    add(MediaIdCodec.encode(documentId))
    add(pageStart.toString())
    add(pageLength.toString())
    add(if (continuous) MODE_CONTINUOUS else MODE_PAGE)
    add(sourceOffset.toString())
    add(MediaIdCodec.encode(cacheKey))
}.joinToString(SEPARATOR.toString())

fun parseReadAloudMediaId(mediaId: String): ReadAloudSegmentInfo? {
    if (!mediaId.startsWith("$READ_ALOUD_PREFIX$SEPARATOR")) return null
    val fields = mediaId.split(SEPARATOR)
    return when (fields.size) {
        V2_SEGMENT_FIELDS -> {
            if (fields[1] != READ_ALOUD_ID_VERSION) return null
            segmentInfo(
                documentId = fields[2],
                pageStart = fields[3],
                pageLength = fields[4],
                mode = fields[5],
                sourceOffset = fields[6],
                cacheKey = fields[7],
            )
        }
        // IDs written before the format was versioned: identical values, no version field.
        V1_SEGMENT_FIELDS -> segmentInfo(
            documentId = fields[1],
            pageStart = fields[2],
            pageLength = fields[3],
            mode = fields[4],
            sourceOffset = fields[5],
            cacheKey = fields[6],
        )
        else -> null
    }
}

private fun segmentInfo(
    documentId: String,
    pageStart: String,
    pageLength: String,
    mode: String,
    sourceOffset: String,
    cacheKey: String,
): ReadAloudSegmentInfo? {
    val start = pageStart.toLongOrNull() ?: return null
    val length = pageLength.toIntOrNull() ?: return null
    val offset = sourceOffset.toLongOrNull() ?: return null
    val continuous = when (mode) {
        MODE_CONTINUOUS -> true
        MODE_PAGE -> false
        else -> return null
    }
    if (!isValidReadAloudSourceRange(start, length, offset)) return null
    val decodedDocument = MediaIdCodec.decode(documentId)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val decodedCacheKey = MediaIdCodec.decode(cacheKey) ?: return null
    return ReadAloudSegmentInfo(
        documentId = decodedDocument,
        pageStart = start,
        pageLength = length,
        continuous = continuous,
        sourceOffset = offset,
        cacheKey = decodedCacheKey,
    )
}

fun readAloudStartMediaId(request: ReadAloudStartRequest): String = buildList {
    add(READ_ALOUD_START_PREFIX)
    add(READ_ALOUD_ID_VERSION)
    add(MediaIdCodec.encode(request.documentId))
    add(request.startChar.toString())
    add(MediaIdCodec.encode(request.mimeType))
    add(request.strictOffline.toString())
    add(if (request.continuous) MODE_CONTINUOUS else MODE_PAGE)
}.joinToString(SEPARATOR.toString())

fun parseReadAloudStartMediaId(mediaId: String): ReadAloudStartRequest? {
    if (!isReadAloudStartMediaId(mediaId)) return null
    val fields = mediaId.split(SEPARATOR)
    return when (fields.size) {
        V2_START_FIELDS -> {
            if (fields[1] != READ_ALOUD_ID_VERSION) return null
            startRequest(
                documentId = fields[2],
                startChar = fields[3],
                mimeType = fields[4],
                strictOffline = fields[5],
                mode = fields[6],
            )
        }
        V1_START_FIELDS -> startRequest(
            documentId = fields[1],
            startChar = fields[2],
            mimeType = fields[3],
            strictOffline = fields[4],
            mode = fields[5],
        )
        else -> null
    }
}

private fun startRequest(
    documentId: String,
    startChar: String,
    mimeType: String,
    strictOffline: String,
    mode: String,
): ReadAloudStartRequest? {
    val document = MediaIdCodec.decode(documentId)?.takeIf { it.isNotBlank() } ?: return null
    val offset = startChar.toLongOrNull()?.takeIf { it >= 0L } ?: return null
    val mime = MediaIdCodec.decode(mimeType) ?: return null
    val continuous = when (mode) {
        MODE_CONTINUOUS -> true
        MODE_PAGE -> false
        else -> return null
    }
    return ReadAloudStartRequest(
        documentId = document,
        startChar = offset,
        mimeType = mime,
        // Absent in the oldest layout; treat anything but an explicit "false" as strict.
        strictOffline = strictOffline != "false",
        continuous = continuous,
    )
}

fun isReadAloudMediaId(mediaId: String): Boolean =
    mediaId.startsWith("$READ_ALOUD_PREFIX$SEPARATOR")

fun isReadAloudStartMediaId(mediaId: String): Boolean =
    mediaId.startsWith("$READ_ALOUD_START_PREFIX$SEPARATOR")

internal fun isValidReadAloudSourceRange(pageStart: Long, pageLength: Int, sourceOffset: Long): Boolean =
    pageStart >= 0L &&
        pageStart < Long.MAX_VALUE &&
        pageLength >= 0 &&
        pageStart <= Long.MAX_VALUE - pageLength &&
        sourceOffset in 0L..pageLength.toLong()

/**
 * Percent-encoding for media IDs: UTF-8 bytes outside the unreserved set become `%XX`.
 *
 * Strict by design: decoding a malformed escape returns `null` so a corrupt ID is rejected instead of being
 * silently reinterpreted as a different document.
 */
internal object MediaIdCodec {
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.~"
    private const val HEX = "0123456789ABCDEF"

    fun encode(value: String): String = buildString(value.length) {
        value.toByteArray(StandardCharsets.UTF_8).forEach { byte ->
            val code = byte.toInt() and 0xFF
            val char = code.toChar()
            if (code < 0x80 && UNRESERVED.indexOf(char) >= 0) {
                append(char)
            } else {
                append('%')
                append(HEX[code shr 4])
                append(HEX[code and 0x0F])
            }
        }
    }

    fun decode(value: String): String? {
        if (value.indexOf('%') < 0) return value
        val bytes = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '%') {
                if (index + 2 >= value.length) return null
                val high = hexValue(value[index + 1]) ?: return null
                val low = hexValue(value[index + 2]) ?: return null
                bytes.write((high shl 4) or low)
                index += 3
            } else {
                // Unescaped characters are UTF-8 already; re-encode so the buffer stays byte-oriented.
                val end = value.indexOf('%', index).let { if (it < 0) value.length else it }
                bytes.write(value.substring(index, end).toByteArray(StandardCharsets.UTF_8))
                index = end
            }
        }
        return String(bytes.toByteArray(), StandardCharsets.UTF_8)
    }

    private fun hexValue(char: Char): Int? = when (char) {
        in '0'..'9' -> char - '0'
        in 'a'..'f' -> char - 'a' + 10
        in 'A'..'F' -> char - 'A' + 10
        else -> null
    }
}

/** Markdown to speech text conversion with offset-preserving semantics. */
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
