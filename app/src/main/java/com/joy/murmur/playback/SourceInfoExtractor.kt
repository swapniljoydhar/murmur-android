package com.joy.murmur.playback

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Locale

@OptIn(UnstableApi::class)
class SourceInfoExtractor(private val context: Context) {

    /**
     * Creates SourceInfo from ExoPlayer Format and file metadata.
     */
    suspend fun extractSourceInfo(format: Format, mediaItem: MediaItem): SourceInfo = withContext(Dispatchers.IO) {
        val mimeType = format.sampleMimeType ?: ""
        val codec = format.codecs ?: ""
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        val bitrate = format.bitrate
        
        val (sourceFormat, sourceCodec) = resolveFormatAndCodec(mimeType, codec)
        val bitDepth = resolveBitDepth(mimeType, format)
        val channels = channelCount.toString()
        val bitrateStr = if (bitrate > 0) "${(bitrate / 1000).toString()} kbps" else "Unknown"
        val durationStr = "Unknown"
        
        // Extract ReplayGain from file metadata
        val replayGain = extractReplayGainFromFile(mediaItem)
        
        SourceInfo(
            format = sourceFormat,
            codec = sourceCodec,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            bitrate = bitrateStr,
            duration = durationStr,
            replayGainTrack = replayGain.trackGain,
            replayGainAlbum = replayGain.albumGain,
            replayGainPeak = replayGain.peak,
        )
    }

    private fun resolveFormatAndCodec(mimeType: String, codec: String): Pair<String, String> {
        return when {
            mimeType == MimeTypes.AUDIO_MPEG || mimeType == "audio/mpeg" -> "MP3" to (if (codec.isNotBlank()) codec else "MPEG Layer 3")
            mimeType == MimeTypes.AUDIO_AAC || mimeType == "audio/aac" -> "AAC" to (if (codec.isNotBlank()) codec else "AAC")
            mimeType == MimeTypes.AUDIO_FLAC || mimeType == "audio/flac" -> "FLAC" to "FLAC"
            mimeType == MimeTypes.AUDIO_OPUS || mimeType == "audio/opus" -> "Opus" to "Opus"
            mimeType == MimeTypes.AUDIO_VORBIS || mimeType == "audio/vorbis" -> "OGG Vorbis" to "Vorbis"
            mimeType == MimeTypes.AUDIO_WAV || mimeType == "audio/wav" -> "WAV" to "PCM"
            mimeType == MimeTypes.AUDIO_AMR_NB || mimeType == MimeTypes.AUDIO_AMR_WB -> "AMR" to codec
            mimeType == MimeTypes.AUDIO_RAW || mimeType == "audio/raw" -> "Raw PCM" to "PCM"
            mimeType.startsWith("audio/mp4") || mimeType == "audio/m4a" -> "M4A/AAC" to (if (codec.isNotBlank()) codec else "AAC")
            mimeType == "audio/mp3" -> "MP3" to "MPEG Layer 3"
            mimeType == "audio/ogg" -> "OGG" to (if (codec.isNotBlank()) codec else "Vorbis")
            else -> (mimeType.takeIf { it.isNotBlank() } ?: "Unknown") to (codec.takeIf { it.isNotBlank() } ?: "Unknown")
        }
    }

    private fun resolveBitDepth(mimeType: String, format: Format): Int {
        // ExoPlayer doesn't directly expose bit depth, infer from format
        return when {
            mimeType == MimeTypes.AUDIO_FLAC -> 24 // FLAC typically 16-24 bit
            mimeType == MimeTypes.AUDIO_WAV -> 24 // WAV can be up to 32-bit
            mimeType == MimeTypes.AUDIO_MPEG -> 16 // MP3 is effectively 16-bit
            mimeType == MimeTypes.AUDIO_AAC -> 16
            mimeType == MimeTypes.AUDIO_OPUS -> 16
            mimeType == MimeTypes.AUDIO_VORBIS -> 16
            mimeType.startsWith("audio/mp4") || mimeType == "audio/m4a" -> 16
            else -> 16 // Default assumption
        }
    }

    private fun formatDuration(durationUs: Long): String {
        val seconds = durationUs / 1_000_000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
        else String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }

    /**
     * Extracts ReplayGain metadata from the audio file.
     */
    private fun extractReplayGainFromFile(mediaItem: MediaItem): ReplayGainData {
        val uri = mediaItem.localConfiguration?.uri ?: return ReplayGainData()
        return when {
            uri.scheme == "file" -> extractFromFile(uri.path!!)
            uri.scheme == "content" -> extractFromContentUri(uri)
            else -> ReplayGainData()
        }
    }

    private fun extractFromFile(path: String): ReplayGainData {
        val lowerPath = path.lowercase()
        return when {
            lowerPath.endsWith(".mp3") -> extractReplayGainFromMp3(path)
            lowerPath.endsWith(".flac") -> extractReplayGainFromFlac(path)
            lowerPath.endsWith(".m4a") || lowerPath.endsWith(".mp4") -> extractReplayGainFromMp4(path)
            lowerPath.endsWith(".ogg") -> extractReplayGainFromVorbis(path)
            else -> ReplayGainData()
        }
    }

    private fun extractFromContentUri(uri: Uri): ReplayGainData {
        // For MediaStore URIs, we can't easily extract ReplayGain without opening the file
        // Try to get the file path from MediaStore
        val projection = arrayOf(MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.MIME_TYPE)
        val selection = "${MediaStore.Audio.Media._ID} = ?"
        val id = uri.lastPathSegment?.toLongOrNull() ?: return ReplayGainData()
        
        return try {
            context.contentResolver.query(uri, projection, selection, arrayOf(id.toString()), null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE))
                    filePath?.let { extractFromFile(it) } ?: ReplayGainData()
                } else ReplayGainData()
            } ?: ReplayGainData()
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    /**
     * Extracts ReplayGain from MP3 (ID3v2 TXXX frames).
     */
    private fun extractReplayGainFromMp3(path: String): ReplayGainData {
        return try {
            val raf = RandomAccessFile(path, "r")
            try {
                // Check for ID3v2 tag at start
                val header = ByteArray(10)
                if (raf.read(header) != 10) return ReplayGainData()
                if (!header.contentEquals("ID3".toByteArray())) return ReplayGainData()
                
                val majorVersion = header[3].toInt()
                val flags = header[5].toInt()
                val hasExtendedHeader = (flags and 0x40) != 0
                
                val sizeBytes = header.copyOfRange(6, 10)
                var tagSize = 0L
                for (i in 0 until 4) {
                    tagSize = (tagSize shl 7) + (sizeBytes[i].toInt() and 0x7F)
                }
                
                if (tagSize <= 0 || tagSize > 16 * 1024 * 1024) return ReplayGainData()
                
                var offset = 10L
                if (hasExtendedHeader) {
                    val extHeaderSizeBytes = ByteArray(4)
                    raf.seek(offset)
                    if (raf.read(extHeaderSizeBytes) != 4) return ReplayGainData()
                    val extHeaderSize = ByteBuffer.wrap(extHeaderSizeBytes).order(ByteOrder.BIG_ENDIAN).long
                    offset += extHeaderSize
                }
                
                var trackGain: Float? = null
                var albumGain: Float? = null
                var peak: Float? = null
                
                while (offset < 10 + tagSize - 10) {
                    raf.seek(offset)
                    val frameHeader = ByteArray(10)
                    if (raf.read(frameHeader) != 10) break
                    
                    val frameId = String(frameHeader.copyOfRange(0, 4))
                    val frameSize = ByteBuffer.wrap(frameHeader.copyOfRange(4, 8)).order(ByteOrder.BIG_ENDIAN).long
                    val frameFlags = frameHeader.copyOfRange(8, 10)
                    
                    if (frameSize <= 0 || frameSize > 16 * 1024 * 1024) break
                    
                    if (frameId == "TXXX") {
                        val frameData = ByteArray(frameSize.toInt())
                        raf.seek(offset + 10)
                        if (raf.read(frameData) == frameSize.toInt()) {
                            parseTxxxFrame(frameData)?.let { (desc, value) ->
                                when (desc.lowercase()) {
                                    "replaygain_track_gain" -> trackGain = parseGainDb(value)
                                    "replaygain_album_gain" -> albumGain = parseGainDb(value)
                                    "replaygain_track_peak" -> peak = parsePeak(value)
                                    "replaygain_album_peak" -> if (peak == null) peak = parsePeak(value)
                                }
                            }
                        }
                    }
                    
                    offset += 10 + frameSize
                }
                
                ReplayGainData(trackGain, albumGain, peak)
            } finally {
                raf.close()
            }
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    private fun parseTxxxFrame(data: ByteArray): Pair<String, String>? {
        if (data.isEmpty()) return null
        val encoding = data[0].toInt()
        val text = when (encoding) {
            0 -> String(data.copyOfRange(1, data.size), StandardCharsets.ISO_8859_1)
            1 -> String(data.copyOfRange(1, data.size), StandardCharsets.UTF_16).replace("\u0000", "")
            2 -> String(data.copyOfRange(1, data.size), StandardCharsets.UTF_16BE).replace("\u0000", "")
            3 -> String(data.copyOfRange(1, data.size), StandardCharsets.UTF_8).replace("\u0000", "")
            else -> return null
        }
        val parts = text.split("=", limit = 2)
        if (parts.size == 2) {
            return parts[0].trim() to parts[1].trim()
        }
        return null
    }

    private fun parseGainDb(value: String): Float? {
        val clean = value.replace("dB", "", ignoreCase = true).trim()
        return clean.toFloatOrNull()
    }

    private fun parsePeak(value: String): Float? {
        return value.trim().toFloatOrNull()
    }

    /**
     * Extracts ReplayGain from FLAC (VorbisComment blocks).
     */
    private fun extractReplayGainFromFlac(path: String): ReplayGainData {
        return try {
            val raf = RandomAccessFile(path, "r")
            try {
                // Check for "fLaC" signature
                val signature = ByteArray(4)
                if (raf.read(signature) != 4) return ReplayGainData()
                if (!signature.contentEquals("fLaC".toByteArray())) return ReplayGainData()
                
                var trackGain: Float? = null
                var albumGain: Float? = null
                var peak: Float? = null
                
                while (true) {
                    val blockHeader = ByteArray(4)
                    if (raf.read(blockHeader) != 4) break
                    
                    val lastBlock = (blockHeader[0].toInt() and 0x80) != 0
                    val blockType = blockHeader[0].toInt() and 0x7F
                    val blockSize = ByteBuffer.wrap(blockHeader, 1, 3).order(ByteOrder.BIG_ENDIAN).int
                    
                    if (blockType == 4) { // VorbisComment block
                        val blockData = ByteArray(blockSize)
                        if (raf.read(blockData) == blockSize) {
                            parseVorbisComment(blockData)?.let { (track, album, pk) ->
                                trackGain = track
                                albumGain = album
                                peak = pk
                            }
                        }
                    } else {
                        raf.seek(raf.filePointer + blockSize)
                    }
                    
                    if (lastBlock) break
                }
                
                ReplayGainData(trackGain, albumGain, peak)
            } finally {
                raf.close()
            }
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    private fun parseVorbisComment(data: ByteArray): Triple<Float?, Float?, Float?>? {
        if (data.size < 4) return null
        var pos = 0
        val vendorLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4 + vendorLength
        if (pos + 4 > data.size) return null
        val commentCount = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
        pos += 4
        
        var trackGain: Float? = null
        var albumGain: Float? = null
        var peak: Float? = null
        
        repeat(commentCount) {
            if (pos + 4 > data.size) return null
            val commentLength = ByteBuffer.wrap(data, pos, 4).order(ByteOrder.LITTLE_ENDIAN).int
            pos += 4
            if (pos + commentLength > data.size) return null
            val comment = String(data.copyOfRange(pos, pos + commentLength), StandardCharsets.UTF_8)
            pos += commentLength
            
            val parts = comment.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].lowercase().trim()
                val value = parts[1].trim()
                when (key) {
                    "replaygain_track_gain" -> trackGain = parseGainDb(value)
                    "replaygain_album_gain" -> albumGain = parseGainDb(value)
                    "replaygain_track_peak" -> peak = parsePeak(value)
                    "replaygain_album_peak" -> if (peak == null) peak = parsePeak(value)
                }
            }
        }
        
        return Triple(trackGain, albumGain, peak)
    }

    /**
     * Extracts ReplayGain from MP4/M4A (iTunes-style atoms).
     */
    private fun extractReplayGainFromMp4(path: String): ReplayGainData {
        return try {
            val raf = RandomAccessFile(path, "r")
            try {
                var trackGain: Float? = null
                var albumGain: Float? = null
                var peak: Float? = null
                
                // Simple MP4 atom scanning for ----:com.apple.iTunes:replaygain_* atoms
                val fileSize = raf.length()
                val buffer = ByteArray(8192)
                var pos = 0L
                
                while (pos < fileSize - 8) {
                    raf.seek(pos)
                    val read = raf.read(buffer)
                    if (read < 8) break
                    
                    val atomSize = ByteBuffer.wrap(buffer, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                    val atomType = String(buffer.copyOfRange(4, 8))
                    
                    if (atomSize > 0 && atomSize < 1024 * 1024) {
                        if (atomType == "----") {
                            // Read the full atom to check for iTunes replay gain
                            val atomData = ByteArray(atomSize - 8)
                            raf.seek(pos + 8)
                            if (raf.read(atomData) == atomData.size) {
                                // Check for iTunes replay gain atoms
                                val dataStr = String(atomData, StandardCharsets.UTF_8)
                                if (dataStr.contains("com.apple.iTunes:replaygain_track_gain")) {
                                    trackGain = extractMp4Value(dataStr, "replaygain_track_gain")
                                }
                                if (dataStr.contains("com.apple.iTunes:replaygain_album_gain")) {
                                    albumGain = extractMp4Value(dataStr, "replaygain_album_gain")
                                }
                                if (dataStr.contains("com.apple.iTunes:replaygain_track_peak")) {
                                    peak = extractMp4Value(dataStr, "replaygain_track_peak")
                                }
                                if (dataStr.contains("com.apple.iTunes:replaygain_album_peak")) {
                                    if (peak == null) peak = extractMp4Value(dataStr, "replaygain_album_peak")
                                }
                            }
                        }
                        pos += atomSize
                    } else {
                        pos += 1 // Fallback: byte-by-byte search
                        raf.seek(pos)
                    }
                }
                
                ReplayGainData(trackGain, albumGain, peak)
            } finally {
                raf.close()
            }
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    private fun extractMp4Value(data: String, key: String): Float? {
        // Simple extraction for iTunes replay gain format
        val pattern = "com\\.apple\\.iTunes:$key[^0-9]*([+-]?\\d+(?:\\.\\d+)?)".toRegex()
        return pattern.find(data)?.groupValues?.get(1)?.toFloatOrNull()
    }

    /**
     * Extracts ReplayGain from OGG Vorbis.
     */
    private fun extractReplayGainFromVorbis(path: String): ReplayGainData {
        return try {
            val raf = RandomAccessFile(path, "r")
            try {
                var trackGain: Float? = null
                var albumGain: Float? = null
                var peak: Float? = null
                
                // Scan for VorbisComment packets
                val fileSize = raf.length()
                val buffer = ByteArray(8192)
                var pos = 0L
                
                while (pos < fileSize - 27) {
                    raf.seek(pos)
                    val pageHeader = ByteArray(27)
                    val read = raf.read(pageHeader)
                    if (read != 27) break
                    
                    if (!pageHeader.contentEquals("OggS".toByteArray())) {
                        pos += 1
                        continue
                    }
                    
                    val segmentCount = pageHeader[26].toInt() and 0xFF
                    val segmentTable = ByteArray(segmentCount)
                    raf.seek(pos + 27)
                    if (raf.read(segmentTable) != segmentCount) break
                    
                    var pageSize = 27 + segmentCount
                    for (i in 0 until segmentCount) {
                        pageSize += segmentTable[i].toInt() and 0xFF
                    }
                    
                    val pageDataSize = pageSize - 27 - segmentCount
                    if (pageDataSize > 0 && pageDataSize < 1024 * 1024) {
                        val pageData = ByteArray(pageDataSize)
                        if (raf.read(pageData) == pageDataSize) {
                            // Check for VorbisComment packet (type 3)
                            if (pageData.size >= 7 && pageData[0].toInt() == 3 && 
                                pageData.contentEquals("vorbis".toByteArray())) {
                                parseVorbisComment(pageData)?.let { (track, album, pk) ->
                                    trackGain = track
                                    albumGain = album
                                    peak = pk
                                }
                            }
                        }
                    }
                    
                    pos += pageSize
                }
                
                ReplayGainData(trackGain, albumGain, peak)
            } finally {
                raf.close()
            }
        } catch (_: Exception) {
            ReplayGainData()
        }
    }

    data class ReplayGainData(
        val trackGain: Float? = null,
        val albumGain: Float? = null,
        val peak: Float? = null,
    )
}

/**
 * Real source information extracted from media format.
 */
data class SourceInfo(
    val format: String,           // e.g., "MP3", "FLAC", "AAC", "Opus"
    val codec: String,            // e.g., "MPEG Layer 3", "FLAC", "AAC LC"
    val sampleRate: Int,          // e.g., 44100, 48000
    val bitDepth: Int,            // e.g., 16, 24
    val channels: String,         // e.g., "2", "1", "6"
    val bitrate: String,          // e.g., "320 kbps", "Unknown"
    val duration: String,         // e.g., "3:45", "1:23:45"
    val replayGainTrack: Float? = null,  // Track gain in dB
    val replayGainAlbum: Float? = null,  // Album gain in dB
    val replayGainPeak: Float? = null,   // Peak value (0.0-1.0+)
)

/**
 * Information about active audio processing.
 */
data class ProcessingInfo(
    val replayGainEnabled: Boolean,
    val replayGainStatus: String,
    val equalizerEnabled: Boolean,
    val equalizerStatus: String,
    val crossfeedEnabled: Boolean,
    val crossfeedStatus: String,
    val headroomEnabled: Boolean,
    val headroomStatus: String,
    val balanceEnabled: Boolean,
    val balanceStatus: String,
    val ditherEnabled: Boolean,
    val ditherStatus: String,
)

/**
 * Information about the audio output device.
 */
data class OutputInfo(
    val route: String,
    val deviceName: String,
    val outputSampleRate: Int,
    val outputBitDepth: Int,
    val outputChannels: String,
    val latencyMs: Int,
    val volume: Float,
)