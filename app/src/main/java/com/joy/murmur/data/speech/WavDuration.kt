package com.joy.murmur.data.speech

import java.io.File

/**
 * Reads the exact duration of a PCM WAV file from its RIFF header.
 *
 * Generated speech segments are cached on disk and reused across process restarts, so their duration
 * has to be recovered without synthesizing anything. It used to be estimated from the file size with
 * a hard-coded 22 050 Hz / mono / 16-bit byte rate, which is wrong for engines that synthesize at
 * 44.1 or 48 kHz, for stereo voices and for 8-bit or float output. The value is written into the
 * persisted segment map, so an incorrect estimate silently mis-maps tap-to-read and highlighting for
 * every cached segment after the first play.
 *
 * The header we write ourselves records the real sample rate, channel count and bit depth, so reading
 * it back is exact rather than approximate. Anything that does not look like a complete WAV file
 * returns `null`, which lets the caller treat the entry as a cache miss and rebuild it.
 */
internal object WavDuration {
    /** Enough for the `fmt ` and `data` chunks even when a large `LIST`/`INFO` chunk precedes them. */
    private const val MAX_HEADER_BYTES = 64 * 1024
    private const val RIFF_HEADER_BYTES = 12

    fun of(file: File): Long? {
        if (!file.isFile) return null
        val fileLength = file.length()
        if (fileLength <= RIFF_HEADER_BYTES.toLong()) return null
        val header = ByteArray(minOf(fileLength, MAX_HEADER_BYTES.toLong()).toInt())
        // InputStream.readNBytes is API 33+; minSdk is 26, so read the window by hand.
        val read = runCatching {
            file.inputStream().use { stream ->
                var total = 0
                while (total < header.size) {
                    val count = stream.read(header, total, header.size - total)
                    if (count <= 0) break
                    total += count
                }
                total
            }
        }.getOrNull() ?: return null
        return of(header, read, fileLength)
    }

    fun of(bytes: ByteArray, availableBytes: Int, fileLength: Long): Long? {
        if (availableBytes < RIFF_HEADER_BYTES || fileLength <= 0L) return null
        if (ascii(bytes, 0) != "RIFF" || ascii(bytes, 8) != "WAVE") return null

        var offset = RIFF_HEADER_BYTES
        var channels = 0
        var sampleRate = 0
        var byteRate = 0
        var bitsPerSample = 0
        var haveFormat = false
        var dataSize = -1L
        var dataOffset = -1L

        while (offset + 8 <= availableBytes) {
            val chunkId = ascii(bytes, offset)
            val chunkSize = unsignedInt(bytes, offset + 4)
            // -1 means the size field itself was cut off; anything larger than the file is corrupt.
            if (chunkSize < 0L || chunkSize > fileLength) return null
            val body = offset + 8
            when (chunkId) {
                "fmt " -> {
                    if (chunkSize < 16L || body + 16 > availableBytes) return null
                    channels = unsignedShort(bytes, body + 2)
                    sampleRate = unsignedInt(bytes, body + 4).toInt()
                    byteRate = unsignedInt(bytes, body + 8).toInt()
                    bitsPerSample = unsignedShort(bytes, body + 14)
                    haveFormat = true
                }
                "data" -> {
                    dataSize = chunkSize
                    dataOffset = body.toLong()
                    break
                }
            }
            if (chunkSize == 0L) return null
            offset = body + chunkSize.toInt() + (chunkSize.toInt() and 1)
        }

        if (!haveFormat || dataOffset < 0L || dataSize <= 0L) return null
        if (channels !in 1..2) return null
        if (sampleRate !in 8_000..192_000) return null
        if (bitsPerSample !in setOf(8, 16, 24, 32)) return null
        // A truncated file must not be reported as playable audio.
        if (dataOffset + dataSize > fileLength) return null

        val effectiveByteRate = when {
            byteRate > 0 -> byteRate.toLong()
            else -> sampleRate.toLong() * channels * bitsPerSample / 8L
        }
        if (effectiveByteRate <= 0L) return null
        return dataSize * 1000L / effectiveByteRate
    }

    private fun ascii(bytes: ByteArray, offset: Int): String {
        if (offset + 4 > bytes.size) return ""
        return String(bytes, offset, 4, Charsets.US_ASCII)
    }

    private fun unsignedShort(bytes: ByteArray, offset: Int): Int {
        if (offset + 2 > bytes.size) return -1
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun unsignedInt(bytes: ByteArray, offset: Int): Long {
        if (offset + 4 > bytes.size) return -1L
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }
}
