package com.joy.murmur.data.speech

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The speech cache reuses WAV files across process restarts, so the duration reported for a cached
 * segment must equal the duration that was reported when it was synthesized. These tests pin the
 * exact byte-rate cases that the previous "assume 22 050 Hz mono 16-bit" estimate got wrong.
 */
class WavDurationTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun writeWave(
        name: String = "speech.wav",
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        dataSize: Int,
        extraChunkBeforeData: String? = null,
    ): File {
        val file = File(folder.root, name)
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val extra = extraChunkBeforeData?.toByteArray(Charsets.US_ASCII) ?: ByteArray(0)
        val extraHeader = if (extra.isEmpty()) 0 else 8 + extra.size
        file.outputStream().use { out ->
            out.write("RIFF".toByteArray(Charsets.US_ASCII))
            out.write(leInt(36 + extraHeader + dataSize))
            out.write("WAVE".toByteArray(Charsets.US_ASCII))
            if (extra.isNotEmpty()) {
                out.write("LIST".toByteArray(Charsets.US_ASCII))
                out.write(leInt(extra.size))
                out.write(extra)
            }
            out.write("fmt ".toByteArray(Charsets.US_ASCII))
            out.write(leInt(16))
            out.write(leShort(1))
            out.write(leShort(channels))
            out.write(leInt(sampleRate))
            out.write(leInt(byteRate))
            out.write(leShort(blockAlign))
            out.write(leShort(bitsPerSample))
            out.write("data".toByteArray(Charsets.US_ASCII))
            out.write(leInt(dataSize))
            out.write(ByteArray(dataSize))
        }
        return file
    }

    private fun leInt(value: Int) = ByteArray(4) { index -> ((value shr (8 * index)) and 0xFF).toByte() }

    private fun leShort(value: Int) = ByteArray(2) { index -> ((value shr (8 * index)) and 0xFF).toByte() }

    @Test
    fun readsDurationFromTheRealByteRateNotAnAssumedOne() {
        // 48 kHz mono 16-bit for one second. The old constant (22 050 Hz) reported ~2176 ms here,
        // which is what corrupted the persisted segment map after the first play.
        val file = writeWave(sampleRate = 48_000, channels = 1, bitsPerSample = 16, dataSize = 96_000)
        assertEquals(1_000L, WavDuration.of(file))
    }

    @Test
    fun readsDurationForStereoAt44k() {
        val file = writeWave(sampleRate = 44_100, channels = 2, bitsPerSample = 16, dataSize = 176_400)
        assertEquals(1_000L, WavDuration.of(file))
    }

    @Test
    fun agreesWithTheClassicEngineDefault() {
        // The one case the removed estimator happened to get right: 22 050 Hz mono 16-bit.
        val file = writeWave(sampleRate = 22_050, channels = 1, bitsPerSample = 16, dataSize = 44_100)
        assertEquals(1_000L, WavDuration.of(file))
    }

    @Test
    fun readsDurationFor8BitMonoOutput() {
        val file = writeWave(sampleRate = 11_025, channels = 1, bitsPerSample = 8, dataSize = 11_025)
        assertEquals(1_000L, WavDuration.of(file))
    }

    @Test
    fun walksPastExtraChunksBeforeTheDataChunk() {
        val file = writeWave(
            sampleRate = 24_000,
            channels = 1,
            bitsPerSample = 16,
            dataSize = 48_000,
            extraChunkBeforeData = "INFOISFT\u0006\u0000\u0000\u0000Lavf",
        )
        assertEquals(1_000L, WavDuration.of(file))
    }

    @Test
    fun rejectsTruncatedFilesSoTheCallerRebuildsThem() {
        val file = writeWave(sampleRate = 22_050, channels = 1, bitsPerSample = 16, dataSize = 44_100)
        // Simulate an interrupted write: the header still claims a full second of audio.
        java.io.RandomAccessFile(file, "rw").use { it.setLength(file.length() - 20_000) }
        assertNull(WavDuration.of(file))
    }

    @Test
    fun rejectsFilesThatAreNotWaveAudio() {
        val text = File(folder.root, "not-audio.wav").apply { writeText("RIFF-ish but not really") }
        assertNull(WavDuration.of(text))
        val empty = File(folder.root, "empty.wav").apply { writeText("") }
        assertNull(WavDuration.of(empty))
        assertNull(WavDuration.of(File(folder.root, "missing.wav")))
    }

    @Test
    fun rejectsZeroLengthAudioRatherThanReportingADeadSegment() {
        val file = writeWave(sampleRate = 22_050, channels = 1, bitsPerSample = 16, dataSize = 0)
        assertNull(WavDuration.of(file))
    }

    @Test
    fun durationIsRoundedDownToWholeMilliseconds() {
        // 1000 bytes at 44 100 B/s is 22.67 ms.
        val file = writeWave(sampleRate = 22_050, channels = 1, bitsPerSample = 16, dataSize = 1_000)
        assertEquals(22L, WavDuration.of(file))
    }
}
