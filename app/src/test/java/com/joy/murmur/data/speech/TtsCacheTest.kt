package com.joy.murmur.data.speech

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TtsCacheTest {

    private val tempDir = File("/tmp/tts_cache_test_${System.currentTimeMillis()}")

    @Test
    fun putAndGet_works() = runTest {
        tempDir.mkdirs()
        val cache = TtsCache(tempDir, maxSizeBytes = 10000)
        
        val file = File(tempDir, "test.wav").apply { writeBytes(ByteArray(100)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16
        )

        cache.put("test", file, identity)
        val retrieved = cache.get("test")
        
        assertNotNull(retrieved)
        assertEquals(file.absolutePath, retrieved!!.absolutePath)
    }

    @Test
    fun get_nonexistent_returnsNull() = runTest {
        tempDir.mkdirs()
        val cache = TtsCache(tempDir)
        
        assertNull(cache.get("nonexistent"))
    }

    @Test
    fun pin_releasePin_tracksCount() = runTest {
        tempDir.mkdirs()
        val cache = TtsCache(tempDir)
        
        val file = File(tempDir, "pinned.wav").apply { writeBytes(ByteArray(100)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16
        )

        cache.put("pinned", file, identity)
        cache.pin("pinned")
        cache.pin("pinned")
        
        cache.releasePin("pinned")
        cache.releasePin("pinned")
        
        // After releasing all pins, file can be evicted
        cache.evictIfNeeded()
    }

    @Test
    fun evictIfNeeded_respectsMaxSize() = runTest {
        tempDir.mkdirs()
        val cache = TtsCache(tempDir, maxSizeBytes = 500)
        
        val file1 = File(tempDir, "file1.wav").apply { writeBytes(ByteArray(300)) }
        val file2 = File(tempDir, "file2.wav").apply { writeBytes(ByteArray(300)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16
        )

        cache.put("file1", file1, identity)
        cache.put("file2", file2, identity)
        
        // Total = 600 > 500, should evict one
        cache.evictIfNeeded()
        
        val remaining = listOf(file1, file2).count { it.exists() }
        assertEquals(1, remaining)
    }

    @Test
    fun evictIfNeeded_preservesPinnedFiles() = runTest {
        tempDir.mkdirs()
        val cache = TtsCache(tempDir, maxSizeBytes = 500)
        
        val pinnedFile = File(tempDir, "pinned.wav").apply { writeBytes(ByteArray(300)) }
        val unpinnedFile = File(tempDir, "unpinned.wav").apply { writeBytes(ByteArray(300)) }
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "voice",
            locale = "en-US", pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16
        )

        cache.put("pinned", pinnedFile, identity)
        cache.pin("pinned")  // Pin before adding second file
        cache.put("unpinned", unpinnedFile, identity)
        
        cache.evictIfNeeded()
        
        assertTrue("Pinned file should be preserved", pinnedFile.exists())
        assertFalse("Unpinned file should be evicted", unpinnedFile.exists())
    }
}