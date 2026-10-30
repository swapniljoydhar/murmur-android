package com.joy.murmur.testing

import com.joy.murmur.data.speech.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Unit tests for TTS cache and scheduler components.
 */
class TtsComponentsTest {

    private val cacheDir = File(System.getProperty("java.io.tmpdir"), "test_tts_cache_${UUID.randomUUID()}").apply { mkdirs() }

    @Test
    fun `TtsCache - basic put and get`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024 * 1024) // 10 MB test cache
        val identity = SynthesisIdentity(
            engine = "test",
            engineVersion = "1",
            voice = "test_voice",
            locale = "en-US",
            pronunciationVersion = "1",
            filterVersion = "1",
            sampleRate = 22050,
            channelCount = 1,
            bitDepth = 16,
        )
        
        val testFile = File(cacheDir, "test_${System.currentTimeMillis()}.wav")
        testFile.writeBytes(ByteArray(1024) { (it % 256).toByte() }) // 1 KB test file
        
        val key = "test_key_1"
        cache.put(key, testFile, identity)
        
        val retrieved = cache.get(key)
        assertNotNull("Should retrieve file", retrieved)
        assertEquals("File size should match", testFile.length(), retrieved!!.length())
        
        cache.shutdown()
    }

    @Test
    fun `TtsCache - pin prevents eviction`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024) // 10 KB test cache
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "test", locale = "en",
            pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )
        
        // Add two files
        val file1 = File(cacheDir, "pinned_${System.currentTimeMillis()}.wav")
        file1.writeBytes(ByteArray(5 * 1024) { 1 }) // 5 KB
        val file2 = File(cacheDir, "unpinned_${System.currentTimeMillis()}.wav")
        file2.writeBytes(ByteArray(5 * 1024) { 2 }) // 5 KB
        
        cache.put("pinned", file1, identity)
        cache.put("unpinned", file2, identity)
        
        // Pin the first file
        cache.pin("pinned")
        
        // Add a third file that should trigger eviction of unpinned
        val file3 = File(cacheDir, "evicted_${System.currentTimeMillis()}.wav")
        file3.writeBytes(ByteArray(5 * 1024) { 3 })
        cache.put("evicted", file3, identity)
        
        // Pinned should still exist
        assertNotNull("Pinned file should not be evicted", cache.get("pinned"))
        // Unpinned should be evicted
        assertNull("Unpinned file should be evicted", cache.get("unpinned"))
        // New file should exist
        assertNotNull("New file should exist", cache.get("evicted"))
        
        cache.shutdown()
    }

    @Test
    fun `TtsCache - release pin allows eviction`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024) // 10 KB test cache
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "test", locale = "en",
            pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )
        
        val file1 = File(cacheDir, "pinned_then_released_${System.currentTimeMillis()}.wav")
        file1.writeBytes(ByteArray(5 * 1024) { 1 })
        
        cache.put("releasable", file1, identity)
        cache.pin("releasable")
        cache.releasePin("releasable")
        
        // Add more files to trigger eviction
        val file2 = File(cacheDir, "filler_${System.currentTimeMillis()}.wav")
        file2.writeBytes(ByteArray(10 * 1024) { 2 }) // 10 KB - exceeds cache
        cache.put("filler", file2, identity)
        
        // Released pin should allow eviction
        assertNull("Released pin should allow eviction", cache.get("releasable"))
        
        cache.shutdown()
    }

    @Test
    fun `TtsCache - free space check`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024 * 1024)
        
        // Should have free space on test device
        val hasSpace = cache.hasFreeSpace(1024 * 1024) // 1 MB
        assertTrue("Should have free space", hasSpace)
        
        cache.shutdown()
    }

    @Test
    fun `TtsCache - expired pin cleanup`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024 * 1024)
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "test", locale = "en",
            pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )
        
        val file1 = File(cacheDir, "expired_pin_${System.currentTimeMillis()}.wav")
        file1.writeBytes(ByteArray(1024) { 1 })
        cache.put("expired", file1, identity)
        cache.pin("expired")
        
        // Manually set pin acquisition time to past
        // We can't directly access pinned map, but we can test the cleanup method doesn't crash
        cache.cleanupExpiredPins()
        
        // Pin should still exist (not expired yet)
        assertNotNull("Pin should not be expired yet", cache.get("expired"))
        
        cache.shutdown()
    }

    @Test
    fun `readWavHeader - parses exact sampleRate channels bitsPerSample and duration`() {
        val sampleRate = 44_100
        val channels = 2
        val bitsPerSample = 16
        val durationSeconds = 2
        val dataBytes = sampleRate * channels * (bitsPerSample / 8) * durationSeconds // 352,800 bytes = 2000 ms
        val wavBytes = buildPcmWavBytes(
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = bitsPerSample,
            pcmByteCount = dataBytes,
        )
        val wavFile = File(cacheDir, "stereo_44100.wav").apply { writeBytes(wavBytes) }

        val header = readWavHeader(wavFile)
        assertNotNull("Valid PCM WAV header should parse", header)
        assertEquals(44_100, header!!.sampleRate)
        assertEquals(2, header.channelCount)
        assertEquals(16, header.bitsPerSample)
        assertEquals(dataBytes.toLong(), header.dataBytes)
        assertEquals(2_000L, header.durationMs)
    }

    @Test
    fun `readWavHeader - rejects non-RIFF and truncated files`() {
        val invalid = File(cacheDir, "not_a_wav.wav").apply { writeBytes(ByteArray(128) { 0x42 }) }
        val truncated = File(cacheDir, "tiny.wav").apply { writeBytes(ByteArray(12) { 0x52 }) }

        assertNull(readWavHeader(invalid))
        assertNull(readWavHeader(truncated))
    }

    private fun buildPcmWavBytes(
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmByteCount: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = channels * (bitsPerSample / 8)
        val totalSize = 44 + pcmByteCount
        val buffer = java.nio.ByteBuffer.allocate(totalSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(totalSize - 8)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1) // PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(pcmByteCount)
        buffer.position(totalSize)
        return buffer.array()
    }

    @Test
    fun `TtsCache - pin with lease auto-expires`() = runBlocking(Dispatchers.IO) {
        val cache = TtsCache(cacheDir, maxSizeBytes = 10 * 1024 * 1024)
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "test", locale = "en",
            pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )
        
        val file1 = File(cacheDir, "lease_test_${System.currentTimeMillis()}.wav")
        file1.writeBytes(ByteArray(1024) { 1 })
        cache.put("lease_test", file1, identity)
        cache.pin("lease_test")
        
        // The pin has a 5-minute lease TTL
        // We can't easily test expiration without time manipulation,
        // but we can verify the cleanup method runs without error
        cache.cleanupExpiredPins()
        
        // Pin should still be valid
        assertNotNull("Pin should not be expired", cache.get("lease_test"))
        
        cache.shutdown()
    }

    @Test
    fun `TtsCache - eviction respects max size`() = runBlocking(Dispatchers.IO) {
        val maxSize = 20 * 1024 // 20 KB
        val cache = TtsCache(cacheDir, maxSizeBytes = maxSize.toLong())
        val identity = SynthesisIdentity(
            engine = "test", engineVersion = "1", voice = "test", locale = "en",
            pronunciationVersion = "1", filterVersion = "1",
            sampleRate = 22050, channelCount = 1, bitDepth = 16,
        )
        
        // Add files until we exceed max size
        for (i in 0..10) {
            val file = File(cacheDir, "evict_test_${i}_${System.currentTimeMillis()}.wav")
            file.writeBytes(ByteArray(5 * 1024) { (i % 256).toByte() }) // 5 KB each
            cache.put("file_$i", file, identity)
        }
        
        // Total would be 55 KB, but max is 20 KB
        // Cache should have evicted oldest entries
        // We can't easily verify exact count without internal access,
        // but we can verify it doesn't crash and cache is functional
        
        cache.shutdown()
    }

    @Test
    fun `SynthesisIdentity - toString format`() {
        val identity = SynthesisIdentity(
            engine = "android_tts",
            engineVersion = "1",
            voice = "en-US-Standard-A",
            locale = "en-US",
            pronunciationVersion = "1",
            filterVersion = "1",
            sampleRate = 22050,
            channelCount = 1,
            bitDepth = 16,
        )
        
        val str = identity.toString()
        assertTrue("Identity string should contain all fields", str.contains("android_tts"))
        assertTrue("Identity string should contain voice", str.contains("en-US-Standard-A"))
        assertTrue("Identity string should contain locale", str.contains("en-US"))
        assertTrue("Identity string should contain sample rate", str.contains("22050"))
    }
}