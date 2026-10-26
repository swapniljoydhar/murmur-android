package com.joy.murmur.data.audiobooks

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.inspector.MetadataRetriever
import com.joy.murmur.domain.audiobooks.AudiobookChapter
import com.joy.murmur.domain.audiobooks.ChapterSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.RandomAccessFile
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
        private const val MAX_CHAPTERS = 10000
        private const val ID3_HEADER_SIZE = 10
        private const val ID3_FRAME_HEADER_SIZE = 10
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

        // Fallback: heuristic chapter detection
        tryHeuristicChapters(uri, durationMs)
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
            val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            descriptor?.use { fileDescriptor ->
                val file = java.io.File(fileDescriptor.toString())
                tryId3ChaptersFromFile(file, durationMs)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun tryId3ChaptersFromFile(file: java.io.File, durationMs: Long): List<AudiobookChapter> {
        return try {
            val raf = RandomAccessFile(file, "r")
            try {
                // Read ID3v2 header
                val header = ByteArray(ID3_HEADER_SIZE)
                if (raf.read(header) != ID3_HEADER_SIZE) return emptyList()
                
                // Check for "ID3" identifier
                if (!header.contentEquals("ID3".toByteArray())) return emptyList()
                
                // Parse version
                val majorVersion = header[3].toInt()
                
                // Parse flags
                val flags = header[5].toInt()
                val hasExtendedHeader = (flags and 0x40) != 0
                
                // Parse size (synchsafe integer)
                val sizeBytes = header.copyOfRange(6, 10)
                var tagSize = 0L
                for (i in 0 until 4) {
                    tagSize = (tagSize shl 7) + (sizeBytes[i].toInt() and 0x7F)
                }
                
                if (tagSize <= 0 || tagSize > MAX_ID3_TAG_SIZE) return emptyList()
                
                // Skip extended header if present
                var offset = ID3_HEADER_SIZE.toLong()
                if (hasExtendedHeader) {
                    val extHeaderSizeBytes = ByteArray(4)
                    raf.seek(offset)
                    if (raf.read(extHeaderSizeBytes) != 4) return emptyList()
                    val extHeaderSize = ByteBuffer.wrap(extHeaderSizeBytes).order(ByteOrder.BIG_ENDIAN).long
                    offset += extHeaderSize
                }
                
                val chapters = mutableListOf<AudiobookChapter>()
                var chapterIndex = 0
                
                // Parse frames
                while (offset < ID3_HEADER_SIZE + tagSize - ID3_FRAME_HEADER_SIZE) {
                    raf.seek(offset)
                    val frameHeader = ByteArray(ID3_FRAME_HEADER_SIZE)
                    if (raf.read(frameHeader) != ID3_FRAME_HEADER_SIZE) break
                    
                    val frameId = String(frameHeader.copyOfRange(0, 4))
                    val frameSize = ByteBuffer.wrap(frameHeader.copyOfRange(4, 8)).order(ByteOrder.BIG_ENDIAN).long
                    val frameFlags = frameHeader.copyOfRange(8, 10)
                    
                    if (frameSize <= 0 || frameSize > MAX_ID3_TAG_SIZE) break
                    if (frameId == "CHAP") {
                        val frameData = ByteArray(frameSize.toInt())
                        raf.seek(offset + ID3_FRAME_HEADER_SIZE)
                        if (raf.read(frameData) == frameSize.toInt()) {
                            parseChapFrame(frameData, chapters, chapterIndex, durationMs)
                            chapterIndex++
                        }
                    }
                    
                    offset += ID3_FRAME_HEADER_SIZE + frameSize
                    raf.seek(offset)
                }
                
                if (chapters.isNotEmpty()) {
                    chapters.sortBy { it.startMs }
                    for (i in 0 until chapters.size - 1) {
                        chapters[i] = chapters[i].copy(endMs = chapters[i + 1].startMs)
                    }
                    if (durationMs > 0 && chapters.last().endMs == null) {
                        chapters[chapters.lastIndex] = chapters.last().copy(endMs = durationMs)
                    }
                    return chapters
                }
                emptyList()
            } finally {
                raf.close()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseChapFrame(data: ByteArray, chapters: MutableList<AudiobookChapter>, chapterIndex: Int, durationMs: Long) {
        var pos = 0
        val idEnd = data.indexOfFirst { it == 0.toByte() }.takeIf { it >= 0 } ?: return
        val elementId = String(data.copyOfRange(0, idEnd))
        pos = idEnd + 1
        
        if (pos + 32 > data.size) return
        
        val startTimeMs = ByteBuffer.wrap(data, pos, 8).order(ByteOrder.BIG_ENDIAN).long
        pos += 8
        val endTimeMs = ByteBuffer.wrap(data, pos, 8).order(ByteOrder.BIG_ENDIAN).long
        pos += 8
        pos += 16 // Skip start/end offsets
        
        // Parse sub-frames for title (TIT2)
        var subPos = pos
        var title = "Chapter ${chapterIndex + 1}"
        while (subPos + ID3_FRAME_HEADER_SIZE <= data.size) {
            val subFrameHeader = data.copyOfRange(subPos, subPos + ID3_FRAME_HEADER_SIZE)
            val subFrameId = String(subFrameHeader.copyOfRange(0, 4))
            val subFrameSize = ByteBuffer.wrap(subFrameHeader.copyOfRange(4, 8)).order(ByteOrder.BIG_ENDIAN).int
            
            if (subFrameSize <= 0 || subPos + ID3_FRAME_HEADER_SIZE + subFrameSize > data.size) break
            
            if (subFrameId == "TIT2") {
                val subFrameData = data.copyOfRange(subPos + ID3_FRAME_HEADER_SIZE, subPos + ID3_FRAME_HEADER_SIZE + subFrameSize)
                title = parseId3TextFrame(subFrameData)
                break
            }
            
            subPos += ID3_FRAME_HEADER_SIZE + subFrameSize
        }
        
        val startMs = startTimeMs.coerceAtLeast(0L).coerceAtMost(durationMs)
        val endMs = if (endTimeMs == -1L || endTimeMs == 0L) {
            null
        } else {
            endTimeMs.coerceAtLeast(startMs).coerceAtMost(durationMs).takeIf { it > startMs }
        }
        
        chapters.add(AudiobookChapter(
            id = UUID.randomUUID().toString(),
            audiobookId = "",
            index = chapterIndex,
            title = title.trim(),
            startMs = startMs,
            endMs = endMs,
            source = ChapterSource.CONTAINER,
        ))
    }

    private fun parseId3TextFrame(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val encoding = data[0].toInt()
        return when (encoding) {
            0 -> String(data.copyOfRange(1, data.size), java.nio.charset.StandardCharsets.ISO_8859_1)
            1 -> String(data.copyOfRange(1, data.size), java.nio.charset.StandardCharsets.UTF_16).replace("\u0000", "")
            2 -> String(data.copyOfRange(1, data.size), java.nio.charset.StandardCharsets.UTF_16BE).replace("\u0000", "")
            3 -> String(data.copyOfRange(1, data.size), java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", "")
            else -> ""
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
                var chapterIndex = 0
                
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
                        parseVorbisComment(pageData, chapters, chapterIndex, durationMs)
                        chapterIndex = chapters.size
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
                        chapters[i] = chapters[i].copy(endMs = chapters[i + 1].startMs)
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

    private fun parseVorbisComment(data: ByteArray, chapters: MutableList<AudiobookChapter>, chapterIndex: Int, durationMs: Long) {
        var pos = 0
        if (data.size < 4) return
        
        val vendorLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4 + vendorLength
        
        if (pos + 4 > data.size) return
        val commentCount = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4
        
        val chapterRegex = "^CHAPTER(\\d{3})=(.+)".toRegex()
        val chapterTimeRegex = "^CHAPTER(\\d{3})START=(\\d+)".toRegex()
        val chapterEndRegex = "^CHAPTER(\\d{3})END=(\\d+)".toRegex()
        
        val chapterTitles = mutableMapOf<Int, String>()
        val chapterStarts = mutableMapOf<Int, Long>()
        val chapterEnds = mutableMapOf<Int, Long>()
        
        repeat(commentCount) {
            if (pos + 4 > data.size) return
            val commentLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            
            if (pos + commentLength > data.size) return
            val comment = String(data.copyOfRange(pos, pos + commentLength))
            pos += commentLength
            
            chapterRegex.find(comment)?.let { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@repeat
                val title = match.groupValues[2].trim()
                if (title.isNotBlank()) chapterTitles[num] = title
            }
            
            chapterTimeRegex.find(comment)?.let { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@repeat
                val time = match.groupValues[2].toLongOrNull() ?: return@repeat
                chapterStarts[num] = time
            }
            
            chapterEndRegex.find(comment)?.let { match ->
                val num = match.groupValues[1].toIntOrNull() ?: return@repeat
                val time = match.groupValues[2].toLongOrNull() ?: return@repeat
                chapterEnds[num] = time
            }
        }
        
        chapterTitles.keys.sorted().forEach { num ->
            val title = chapterTitles[num] ?: "Chapter ${num + 1}"
            val startMs = chapterStarts[num]?.coerceAtLeast(0L) ?: (num.toLong() * 30 * 60 * 1000).coerceAtMost(durationMs)
            val endMs = chapterEnds[num]?.takeIf { it > startMs }?.coerceAtMost(durationMs)
            
            chapters.add(AudiobookChapter(
                id = UUID.randomUUID().toString(),
                audiobookId = "",
                index = num,
                title = title.trim(),
                startMs = startMs,
                endMs = endMs,
                source = ChapterSource.CONTAINER,
            ))
        }
    }

    private fun tryHeuristicChapters(uri: Uri, durationMs: Long): List<AudiobookChapter> {
        return try {
            if (durationMs <= 10 * 60 * 60 * 1000L) return emptyList()

            val chapterIntervalMs = 30 * 60 * 1000L // 30 minutes
            val chapterCount = (durationMs / chapterIntervalMs).coerceAtMost(MAX_CHAPTERS.toLong()).toInt()
            if (chapterCount <= 1) return emptyList()

            (0 until chapterCount).map { index ->
                val startMs = index * chapterIntervalMs
                val endMs = if (index == chapterCount - 1) durationMs else (index + 1) * chapterIntervalMs
                AudiobookChapter(
                    id = UUID.randomUUID().toString(),
                    audiobookId = "",
                    index = index,
                    title = "Part ${index + 1}",
                    startMs = startMs,
                    endMs = endMs,
                    source = ChapterSource.HEURISTIC,
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class ChapterCandidate(
        val title: String,
        val startMs: Long,
        val endMs: Long?
    )
}