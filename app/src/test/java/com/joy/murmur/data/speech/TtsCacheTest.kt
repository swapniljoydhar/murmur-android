package com.joy.murmur.data.speech

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.joy.murmur.data.playback.PlaybackDatabase
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TtsCacheTest {

    private fun newTempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "tts_cache_test_${UUID.randomUUID()}").apply { mkdirs() }

    @Test
    fun putAndGet_works() = runTest {
        val tempDir = newTempDir()
        val cache = TtsCache(tempDir, maxSizeBytes = 10_000)

        val file = File(tempDir, "test.wav").apply { writeBytes(ByteArray(100)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )

        cache.put("test", file, identity)
        val retrieved = cache.get("test")

        assertNotNull(retrieved)
        assertEquals(file.absolutePath, retrieved!!.absolutePath)
    }

    @Test
    fun get_nonexistent_returnsNull() = runTest {
        val tempDir = newTempDir()
        val cache = TtsCache(tempDir)
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun releaseAllPins_preservesCachedWavFilesOnDisk() = runTest {
        val tempDir = newTempDir()
        val cache = TtsCache(tempDir)
        val file = File(tempDir, "keep_alive.wav").apply { writeBytes(ByteArray(256) { 7 }) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )

        cache.put("keep_alive", file, identity)
        cache.pin("keep_alive")
        cache.releaseAllPins()

        assertTrue("releaseAllPins must NOT delete cached WAV files", file.exists())
        assertNotNull(cache.get("keep_alive"))
    }

    @Test
    fun roomBackedCache_rehydratesAcrossCacheInstances() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PlaybackDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val tempDir = newTempDir()
        try {
            val pcmBytes = 24_000 * 2 // 1 second @ 24 kHz mono 16-bit
            val wavFile = File(tempDir, "segment_persisted.wav").apply {
                writeBytes(createValidWavBytes(sampleRate = 24_000, channels = 1, bitsPerSample = 16, pcmBytes = pcmBytes))
            }
            val identity = SynthesisIdentity(
                engine = "com.google.android.tts", engineVersion = "1", voice = "en-us-x-sfg",
                locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
                sampleRate = 24_000, channelCount = 1, bitDepth = 16,
            )

            val firstCache = TtsCache(tempDir, dao = db.playbackDao())
            val stored = firstCache.putVerifiedWave("segment_persisted", wavFile, identity.toString())
            assertEquals(1_000L, stored.durationMs)
            firstCache.releaseAllPins()

            // Simulate process/service restart with a brand-new TtsCache instance backed by the same Room DAO
            val restartedCache = TtsCache(tempDir, dao = db.playbackDao())
            val fallbackUnusedFile = File(tempDir, "nonexistent_fallback.wav")
            val rehydrated = restartedCache.getVerifiedWave("segment_persisted", fallbackUnusedFile, identity.toString())
            assertNotNull("Second TtsCache instance must rehydrate persisted WAV metadata from Room", rehydrated)
            assertEquals(wavFile.absolutePath, rehydrated!!.file.absolutePath)
            assertEquals(24_000, rehydrated.header.sampleRate)
            assertEquals(1_000L, rehydrated.header.durationMs)
        } finally {
            db.close()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun evictIfNeeded_respectsMaxSizeAndPreservesPinnedFiles() = runTest {
        val tempDir = newTempDir()
        val cache = TtsCache(tempDir, maxSizeBytes = 500)

        val pinnedFile = File(tempDir, "pinned.wav").apply { writeBytes(ByteArray(300)) }
        val unpinnedFile = File(tempDir, "unpinned.wav").apply { writeBytes(ByteArray(300)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )

        cache.put("pinned", pinnedFile, identity)
        cache.pin("pinned")
        cache.put("unpinned", unpinnedFile, identity)

        cache.evictIfNeeded()

        assertTrue("Pinned file should be preserved", pinnedFile.exists())
        assertFalse("Unpinned file should be evicted", unpinnedFile.exists())
    }

    private fun createValidWavBytes(sampleRate: Int, channels: Int, bitsPerSample: Int, pcmBytes: Int): ByteArray {
        val buffer = ByteBuffer.allocate(44 + pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + pcmBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * channels * (bitsPerSample / 8))
        buffer.putShort((channels * (bitsPerSample / 8)).toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmBytes)
        buffer.position(44 + pcmBytes)
        return buffer.array()
    }
}