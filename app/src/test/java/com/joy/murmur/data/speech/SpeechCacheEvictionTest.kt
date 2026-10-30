package com.joy.murmur.data.speech

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The speech cache had no tests at all: eviction was private, unreachable from a JVM test, and pinned
 * files were never actually protected (the pin API was a no-op, removed with the dead TTS scheduler).
 * These tests pin the policy that keeps a book's segued WAVs alive while trimming everything else.
 */
class SpeechCacheEvictionTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1_000L

    /** Creates one cache directory holding every file, so the policy sees them together. */
    private fun cacheDir(vararg files: Pair<String, Pair<Int, Long>>): File {
        val dir = folder.newFolder()
        files.forEach { (name, spec) ->
            File(dir, name).apply {
                writeBytes(ByteArray(spec.first))
                setLastModified(now - spec.second)
            }
        }
        dir.setLastModified(now)
        return dir
    }

    @Test
    fun keepsFreshFilesThatFitTheBudget() {
        val dir = cacheDir("a.wav" to (10 to day), "b.wav" to (10 to day))
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now)
        assertEquals(0, result.deletedFiles)
        assertEquals(2, dir.listFiles()!!.size)
    }

    @Test
    fun deletesExpiredFilesButNeverQueueProtectedOnes() {
        val dir = cacheDir(
            "expired.wav" to (10 to 40 * day),
            "protected.wav" to (10 to 40 * day),
            "fresh.wav" to (10 to day),
        )
        val result = SpeechCacheEviction.evict(dir, protectedKeys = setOf("protected"), nowMillis = now)
        assertEquals(1, result.deletedFiles)
        assertFalse(File(dir, "expired.wav").exists())
        assertTrue(File(dir, "protected.wav").exists())
        assertTrue(File(dir, "fresh.wav").exists())
    }

    @Test
    fun trimsOverBudgetCacheOldestFirst() {
        val dir = cacheDir(
            "oldest.wav" to (10 to 3 * day),
            "middle.wav" to (10 to 2 * day),
            "newest.wav" to (10 to day),
        )
        // Room for two of the three files.
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now, maxBytes = 20)
        assertEquals(1, result.deletedFiles)
        assertEquals(10L, result.deletedBytes)
        assertFalse(File(dir, "oldest.wav").exists())
        assertTrue(File(dir, "middle.wav").exists())
        assertTrue(File(dir, "newest.wav").exists())
    }

    @Test
    fun skipsProtectedFilesWhenTrimmingAndRemovesTheNextOldestInstead() {
        val dir = cacheDir(
            "oldest.wav" to (10 to 3 * day),
            "middle.wav" to (10 to 2 * day),
            "newest.wav" to (10 to day),
        )
        val result = SpeechCacheEviction.evict(
            dir,
            protectedKeys = setOf("oldest"),
            nowMillis = now,
            maxBytes = 20,
        )
        assertEquals(1, result.deletedFiles)
        assertTrue("the queued segment must survive", File(dir, "oldest.wav").exists())
        assertFalse(File(dir, "middle.wav").exists())
        assertTrue(File(dir, "newest.wav").exists())
    }

    @Test
    fun removesStaleScratchFilesFromInterruptedSynthesis() {
        val dir = cacheDir(
            "live.wav" to (10 to day),
            "digest.wav.part" to (10 to 2 * day),
            "speech-1234.pcm" to (10 to 2 * day),
            "engine-1234.wav" to (10 to 2 * day),
        )
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now)
        assertEquals(3, result.deletedFiles)
        assertTrue(File(dir, "live.wav").exists())
        assertFalse(File(dir, "digest.wav.part").exists())
        assertFalse(File(dir, "speech-1234.pcm").exists())
        assertFalse(File(dir, "engine-1234.wav").exists())
    }

    @Test
    fun keepsRecentScratchFilesThatMayStillBeInFlight() {
        val dir = cacheDir("speech-1234.pcm" to (10 to 1_000L))
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now)
        assertEquals(0, result.deletedFiles)
        assertTrue(File(dir, "speech-1234.pcm").exists())
    }

    @Test
    fun missingDirectoryIsNotAnError() {
        val result = SpeechCacheEviction.evict(File(folder.root, "absent"), emptySet(), nowMillis = now)
        assertEquals(SpeechCacheEviction.Result(0, 0), result)
    }

    @Test
    fun reportsBytesActuallyFreed() {
        val dir = cacheDir("expired.wav" to (4_096 to 40 * day))
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now)
        assertEquals(1, result.deletedFiles)
        assertEquals(4_096L, result.deletedBytes)
    }

    @Test
    fun ignoresNonWaveFilesWhenAccountingForTheBudget() {
        val dir = cacheDir("notes.txt" to (10_000 to day), "a.wav" to (10 to day))
        val result = SpeechCacheEviction.evict(dir, protectedKeys = emptySet(), nowMillis = now, maxBytes = 20)
        assertEquals(0, result.deletedFiles)
        assertTrue(File(dir, "notes.txt").exists())
    }
}
