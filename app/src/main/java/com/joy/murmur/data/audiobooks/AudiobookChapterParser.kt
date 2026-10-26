package com.joy.murmur.data.audiobooks

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.inspector.MetadataRetriever
import com.joy.murmur.domain.audiobooks.AudiobookChapter
import com.joy.murmur.domain.audiobooks.ChapterSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class AudiobookChapterParser(private val context: Context) {

    companion object {
        private const val MP4_ATOM_CHA = 0x63686170.toInt() // "chap"
        private const val MP4_ATOM_CHC = 0x63686320.toInt() // "chc "
        private const val MP4_ATOM_CHCT = 0x63686374.toInt() // "chct"
        private const val MP4_ATOM_CHCO = 0x6368636F.toInt() // "chco"
        private const val MP4_ATOM_CHCN = 0x6368636E.toInt() // "chcn"
        private const val MP4_ATOM_ILST = 0x696C7374.toInt() // "ilst"
        private const val MP4_ATOM_FREE = 0x66726565.toInt() // "free"
        private const val MP4_ATOM_SKIP = 0x736B6970.toInt() // "skip"
        private const val MP4_ATOM_WIDE = 0x77696465.toInt() // "wide"
        private const val MAX_ATOM_SIZE = 100 * 1024 * 1024L // 100MB safety limit
        private const val ID3_HEADER_SIZE = 10
        private const val MAX_ID3_TAG_SIZE = 16 * 1024 * 1024L // 16MB safety limit
    }

    @UnstableApi
    suspend fun parseChapters(uri: Uri, durationMs: Long): List<AudiobookChapter> = withContext(Dispatchers.IO) {
        // Try Media3 chapter frames first (handles most MP4/M4B iTunes chapters)
        val media3Chapters = tryMedia3ChapterFrames(uri, durationMs)
        if (media3Chapters.isNotEmpty()) return@withContext media3Chapters

        // Try ID3v2 CHAP/TOC frames (MP3)
        val id3Chapters = tryId3Chapters(uri, durationMs)
        if (id3Chapters.isNotEmpty()) return@withContext id3Chapters

        // Try Vorbis/OGG CHAPTER tags
        val vorbisChapters = tryVorbisChapters(uri, durationMs)
        if (vorbisChapters.isNotEmpty()) return@withContext vorbisChapters

        emptyList()
    }

    @UnstableApi
    private fun tryMedia3ChapterFrames(uri: Uri, durationMs: Long): List<AudiobookChapter> {
        return try {
            val mediaItem = MediaItem.Builder().setMediaId(uri.toString()).setUri(uri).build()
            val retriever = MetadataRetriever.Builder(context, mediaItem).build()
            retriever.use { retriever ->
                val trackGroups = retriever.retrieveTrackGroups().get(30, java.util.concurrent.TimeUnit.SECONDS)
                val candidates = buildList {
                    for (groupIndex in 0 until trackGroups.length) {
                        val group = trackGroups.get(groupIndex)
                        for (formatIndex in 0 until group.length) {
                            val metadata = group.getFormat(formatIndex).metadata ?: continue
                            for (metadataIndex in 0 until metadata.length()) {
                                val chapter = metadata[metadataIndex] as? ChapterFrame ?: continue
                                if (chapter.isHidden) continue
                                val startMs = chapter.startTimeMs.toLong()
                                if (startMs < 0L || (durationMs > 0L && startMs >= durationMs)) continue
                                val endMs = chapter.endTimeMs.toLong().takeIf { it > startMs }
                                val title = chapter.title?.toString()?.trim()?.takeIf(String::isNotBlank)
                                    ?: "Chapter ${size + 1}"
                                add(ChapterCandidate(title, startMs, endMs))
                            }
                        }
                    }
                }.distinctBy(ChapterCandidate::startMs).sortedBy(ChapterCandidate::startMs)
                candidates.mapIndexed { index, candidate ->
                    AudiobookChapter(
                        id = UUID.randomUUID().toString(),
                        audiobookId = "",
                        index = index,
                        title = candidate.title,
                        startMs = candidate.startMs,
                        endMs = candidate.endMs,
                        source = ChapterSource.CONTAINER,
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryId3Chapters(uri: Uri, durationMs: Long): List<AudiobookChapter> {
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return emptyList()
            input.use { stream ->
                val header = ByteArray(ID3_HEADER_SIZE)
                if (!readExactly(stream, header)) return emptyList()
                if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) return emptyList()
                val tagSize = decodeSynchsafeInteger(header, 6) ?: return emptyList()
                if (tagSize !in 1..MAX_ID3_TAG_SIZE) return emptyList()
                val tag = ByteArray(ID3_HEADER_SIZE + tagSize.toInt())
                header.copyInto(tag)
                if (!readExactly(stream, tag, ID3_HEADER_SIZE, tagSize.toInt())) return emptyList()
                parseId3ChapterTag(tag, durationMs)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryVorbisChapters(uri: Uri, durationMs: Long): List<AudiobookChapter> {
        return try {
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            descriptor?.use { fileDescriptor ->
                tryVorbisChaptersFromFile(fileDescriptor, durationMs)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryVorbisChaptersFromFile(fileDescriptor: ParcelFileDescriptor, durationMs: Long): List<AudiobookChapter> {
        return try {
            val fis = FileInputStream(fileDescriptor.fileDescriptor)
            try {
                // Check for OGG header
                val header = ByteArray(4)
                if (fis.read(header) != 4) return emptyList()
                if (!header.contentEquals("OggS".toByteArray())) return emptyList()
                
                val chapters = mutableListOf<AudiobookChapter>()
                
                // Simple OGG page parsing to find VorbisComment packets
                val fileLength = fis.channel.size()
                var currentPos = 0L
                
                while (currentPos < fileLength) {
                    val pageHeader = ByteArray(27)
                    val read = fis.read(pageHeader)
                    if (read != 27) break
                    currentPos += 27
                    
                    if (!pageHeader.contentEquals("OggS".toByteArray())) break
                    
                    val segmentCount = pageHeader[26].toInt() and 0xFF
                    
                    // Read segment table
                    val segmentTable = ByteArray(segmentCount)
                    val segRead = fis.read(segmentTable)
                    if (segRead != segmentCount) break
                    currentPos += segmentCount
                    
                    // Calculate page size
                    var pageSize = 27 + segmentCount
                    for (i in 0 until segmentCount) {
                        pageSize += segmentTable[i].toInt() and 0xFF
                    }
                    
                    // Read page data
                    val pageDataSize = pageSize - 27 - segmentCount
                    val pageData = ByteArray(pageDataSize)
                    val dataRead = fis.read(pageData)
                    if (dataRead != pageDataSize) break
                    currentPos += pageDataSize
                    
                    // Check if this is a VorbisComment packet (first packet after identification header)
                    if (pageData.size >= 7 && pageData[0].toInt() == 3 && pageData.contentEquals("vorbis".toByteArray())) {
                        parseVorbisComment(pageData, chapters, durationMs)
                    }
                    
                    // Skip remaining data in this page if any
                    val remainingInPage = pageSize - 27 - segmentCount - pageDataSize
                    if (remainingInPage > 0) {
                        fis.skip(remainingInPage.toLong())
                        currentPos += remainingInPage
                    }
                }
                
                if (chapters.isNotEmpty()) {
                    chapters.sortBy { it.startMs }
                    for (i in 0 until chapters.size - 1) {
                        val nextStart = chapters[i + 1].startMs
                        if (chapters[i].endMs == null || chapters[i].endMs!! <= chapters[i].startMs) {
                            chapters[i] = chapters[i].copy(endMs = nextStart)
                        }
                    }
                    if (durationMs > 0 && chapters.last().endMs == null) {
                        chapters[chapters.lastIndex] = chapters.last().copy(endMs = durationMs)
                    }
                    return chapters
                }
                emptyList()
            } finally {
                fis.close()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseVorbisComment(data: ByteArray, chapters: MutableList<AudiobookChapter>, durationMs: Long) {
        var pos = 0
        if (data.size < 4) return
        
        val vendorLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        if (vendorLength < 0 || vendorLength > data.size - pos - 4) return
        pos += 4 + vendorLength
        
        if (pos + 4 > data.size) return
        val commentCount = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4
        if (commentCount < 0 || commentCount > (data.size - pos) / 4) return
        val comments = mutableListOf<String>()
        repeat(commentCount) {
            if (pos + 4 > data.size) return
            val commentLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            
            if (commentLength < 0 || commentLength > data.size - pos) return
            val comment = String(data, pos, commentLength, Charsets.UTF_8)
            pos += commentLength
            comments += comment
        }
        chapters += parseVorbisChapterComments(comments, durationMs)
    }

    private data class ChapterCandidate(
        val title: String,
        val startMs: Long,
        val endMs: Long?
    )
}

internal fun parseVorbisChapterComments(
    comments: List<String>,
    durationMs: Long,
): List<AudiobookChapter> {
    val timestampOrTitle = Regex("^CHAPTER(\\d{1,3})=(.+)$", RegexOption.IGNORE_CASE)
    val namePattern = Regex("^CHAPTER(\\d{1,3})NAME=(.+)$", RegexOption.IGNORE_CASE)
    val startPattern = Regex("^CHAPTER(\\d{1,3})START=(\\d+)$", RegexOption.IGNORE_CASE)
    val endPattern = Regex("^CHAPTER(\\d{1,3})END=(\\d+)$", RegexOption.IGNORE_CASE)
    val titles = mutableMapOf<Int, String>()
    val starts = mutableMapOf<Int, Long>()
    val ends = mutableMapOf<Int, Long>()

    comments.forEach { comment ->
        timestampOrTitle.matchEntire(comment)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@let
            val value = match.groupValues[2].trim()
            val timestamp = parseVorbisTimestamp(value)
            if (timestamp != null) {
                starts[number] = timestamp
                if (number !in titles) titles[number] = "Chapter $number"
            } else if (value.isNotBlank()) {
                titles[number] = value
            }
        }
        namePattern.matchEntire(comment)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@let
            match.groupValues[2].trim().takeIf(String::isNotBlank)?.let { titles[number] = it }
        }
        startPattern.matchEntire(comment)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@let
            match.groupValues[2].toLongOrNull()?.let { starts[number] = it }
        }
        endPattern.matchEntire(comment)?.let { match ->
            val number = match.groupValues[1].toIntOrNull() ?: return@let
            match.groupValues[2].toLongOrNull()?.let { ends[number] = it }
        }
    }

    val candidates = starts.keys.mapNotNull { number ->
        val start = starts[number] ?: return@mapNotNull null
        if (start < 0L || (durationMs > 0L && start >= durationMs)) return@mapNotNull null
        val title = titles[number]?.trim()?.takeIf(String::isNotBlank) ?: "Chapter $number"
        Triple(number, title, start)
    }.distinctBy { it.third }.sortedBy { it.third }

    return candidates.mapIndexed { index, (number, title, start) ->
        val followingStart = candidates.getOrNull(index + 1)?.third
        val parsedEnd = ends[number]?.takeIf { it > start }
            ?.let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
            ?.takeIf { it > start }
        val end = parsedEnd ?: followingStart ?: durationMs.takeIf { it > start }
        AudiobookChapter(
            id = UUID.randomUUID().toString(),
            audiobookId = "",
            index = index,
            title = title,
            startMs = start,
            endMs = end,
            source = ChapterSource.VORBIS,
        )
    }
}

private data class Id3ChapterCandidate(val title: String, val startMs: Long, val endMs: Long?)

internal fun parseId3ChapterTag(tag: ByteArray, durationMs: Long): List<AudiobookChapter> {
    if (tag.size < 10 || tag[0] != 'I'.code.toByte() || tag[1] != 'D'.code.toByte() || tag[2] != '3'.code.toByte()) return emptyList()
    val majorVersion = tag[3].toInt() and 0xFF
    if (majorVersion !in 3..4) return emptyList()
    val tagFlags = tag[5].toInt() and 0xFF
    if (tagFlags and 0x80 != 0) return emptyList() // Tag-level unsynchronization isn't decoded here.
    val tagSize = decodeSynchsafeInteger(tag, 6) ?: return emptyList()
    if (tagSize !in 1..MAX_ID3_CHAPTER_TAG_BYTES || tagSize > tag.size - 10L) return emptyList()
    val tagEnd = 10 + tagSize.toInt() - if (majorVersion == 4 && tagFlags and 0x10 != 0) 10 else 0
    if (tagEnd < 10) return emptyList()

    var offset = 10
    if (tagFlags and 0x40 != 0) {
        if (offset + 4 > tagEnd) return emptyList()
        val extendedSize = if (majorVersion == 4) {
            decodeSynchsafeInteger(tag, offset)?.toInt() ?: return emptyList()
        } else {
            readUnsignedInt(tag, offset)?.toInt() ?: return emptyList()
        }
        val totalExtendedSize = if (majorVersion == 3) extendedSize.toLong() + 4L else extendedSize.toLong()
        if (totalExtendedSize < 4L || totalExtendedSize > tagEnd - offset) return emptyList()
        offset += totalExtendedSize.toInt()
    }

    val candidates = mutableListOf<Id3ChapterCandidate>()
    while (offset + 10 <= tagEnd) {
        if (tag[offset] == 0.toByte()) break
        val frameId = String(tag, offset, 4, Charsets.US_ASCII)
        val frameSize = if (majorVersion == 4) {
            decodeSynchsafeInteger(tag, offset + 4)
        } else {
            readUnsignedInt(tag, offset + 4)
        } ?: break
        if (frameSize <= 0L || frameSize > tagEnd - offset - 10L) break
        val frameFlags = (tag[offset + 8].toInt() and 0xFF) shl 8 or (tag[offset + 9].toInt() and 0xFF)
        if (frameId == "CHAP" && frameFlags == 0) {
            parseId3ChapterFrame(tag, offset + 10, frameSize.toInt(), majorVersion, durationMs)?.let(candidates::add)
        }
        offset += 10 + frameSize.toInt()
    }

    val sorted = candidates.distinctBy(Id3ChapterCandidate::startMs).sortedBy(Id3ChapterCandidate::startMs)
    return sorted.mapIndexed { index, chapter ->
        val nextStart = sorted.getOrNull(index + 1)?.startMs
        val end = chapter.endMs?.takeIf { it > chapter.startMs } ?: nextStart ?: durationMs.takeIf { it > chapter.startMs }
        AudiobookChapter(
            id = UUID.randomUUID().toString(),
            audiobookId = "",
            index = index,
            title = chapter.title.ifBlank { "Chapter ${index + 1}" },
            startMs = chapter.startMs,
            endMs = end,
            source = ChapterSource.ID3,
        )
    }
}

private fun parseId3ChapterFrame(
    tag: ByteArray,
    offset: Int,
    frameSize: Int,
    majorVersion: Int,
    durationMs: Long,
): Id3ChapterCandidate? {
    val frameEnd = offset + frameSize
    var elementEnd = offset
    while (elementEnd < frameEnd && tag[elementEnd] != 0.toByte()) elementEnd++
    if (elementEnd == frameEnd || frameEnd - elementEnd - 1 < 16) return null
    var cursor = elementEnd + 1
    val startMs = readUnsignedInt(tag, cursor) ?: return null
    val endValue = readUnsignedInt(tag, cursor + 4) ?: return null
    cursor += 16 // start/end times and start/end byte offsets are four bytes each.
    if (startMs == 0xFFFF_FFFFL || (durationMs > 0L && startMs >= durationMs)) return null

    var title = ""
    while (cursor + 10 <= frameEnd) {
        if (tag[cursor] == 0.toByte()) break
        val subFrameId = String(tag, cursor, 4, Charsets.US_ASCII)
        val subFrameSize = if (majorVersion == 4) {
            decodeSynchsafeInteger(tag, cursor + 4)
        } else {
            readUnsignedInt(tag, cursor + 4)
        } ?: break
        if (subFrameSize <= 0L || subFrameSize > frameEnd - cursor - 10L) break
        if (subFrameId == "TIT2") {
            title = decodeId3Text(tag, cursor + 10, subFrameSize.toInt())
            break
        }
        cursor += 10 + subFrameSize.toInt()
    }

    val endMs = endValue.takeIf { it != 0xFFFF_FFFFL && it > startMs }
        ?.let { if (durationMs > 0L) it.coerceAtMost(durationMs) else it }
        ?.takeIf { it > startMs }
    return Id3ChapterCandidate(title, startMs, endMs)
}

private fun decodeId3Text(bytes: ByteArray, offset: Int, length: Int): String {
    if (length <= 1 || offset < 0 || offset + length > bytes.size) return ""
    val payload = bytes.copyOfRange(offset + 1, offset + length)
    val charset = when (bytes[offset].toInt() and 0xFF) {
        0 -> Charsets.ISO_8859_1
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> return ""
    }
    return String(payload, charset).replace("\u0000", "").trim()
}

private fun decodeSynchsafeInteger(bytes: ByteArray, offset: Int): Long? {
    if (offset < 0 || offset + 4 > bytes.size) return null
    var value = 0L
    repeat(4) { index ->
        val byte = bytes[offset + index].toInt() and 0xFF
        if (byte and 0x80 != 0) return null
        value = (value shl 7) or byte.toLong()
    }
    return value
}

private fun readUnsignedInt(bytes: ByteArray, offset: Int): Long? {
    if (offset < 0 || offset + 4 > bytes.size) return null
    return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL
}

private fun readExactly(input: InputStream, bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset): Boolean {
    var totalRead = 0
    while (totalRead < length) {
        val count = input.read(bytes, offset + totalRead, length - totalRead)
        if (count < 0) return false
        if (count == 0) continue
        totalRead += count
    }
    return true
}

private const val MAX_ID3_CHAPTER_TAG_BYTES = 16L * 1024L * 1024L

private fun parseVorbisTimestamp(value: String): Long? {
    val match = Regex("^(\\d+):(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?$").matchEntire(value) ?: return null
    val hours = match.groupValues[1].toLongOrNull() ?: return null
    val minutes = match.groupValues[2].toLongOrNull() ?: return null
    val seconds = match.groupValues[3].toLongOrNull() ?: return null
    if (minutes !in 0..59 || seconds !in 0..59 || hours > Long.MAX_VALUE / 3_600_000L) return null
    val fraction = match.groupValues[4].padEnd(3, '0').toLongOrNull() ?: 0L
    val wholeSeconds = hours * 3_600L + minutes * 60L + seconds
    if (wholeSeconds > (Long.MAX_VALUE - fraction) / 1_000L) return null
    return wholeSeconds * 1_000L + fraction
}
