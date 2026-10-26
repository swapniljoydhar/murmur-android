package com.joy.murmur.data.documents

import java.io.StringReader
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.runBlocking

class DocumentSourceFingerprintTest {
    @Test
    fun initialFingerprintPreservesLegacyPosition() {
        val update = resolveReaderPositionAnchor(128L, null, "current", 1)

        assertEquals(128L, update.positionChars)
        assertEquals("current", update.fingerprint)
        assertFalse(update.didReset)
        assertTrue(update.shouldPersist)
    }

    @Test
    fun changedSourceResetsPositionAndKeepsCurrentFingerprint() {
        val update = resolveReaderPositionAnchor(128L, "old", "new", 1)

        assertEquals(0L, update.positionChars)
        assertEquals("new", update.fingerprint)
        assertTrue(update.didReset)
        assertTrue(update.sourceChanged)
    }

    @Test
    fun unavailableFingerprintPreservesPositionAndSavedIdentity() {
        val update = resolveReaderPositionAnchor(128L, "saved", null, 1)

        assertEquals(128L, update.positionChars)
        assertEquals("saved", update.fingerprint)
        assertFalse(update.didReset)
        assertFalse(update.shouldPersist)
    }

    @Test
    fun unknownAnchorVersionResetsAndUpgrades() {
        val update = resolveReaderPositionAnchor(128L, "saved", null, 0)

        assertEquals(0L, update.positionChars)
        assertEquals(1, update.version)
        assertTrue(update.didReset)
        assertTrue(update.shouldPersist)
        assertFalse(update.sourceChanged)
    }

    @Test
    fun boundedReaderSearchReturnsSourceCharacterOffset() = runBlocking {
        val matches = searchReaderBounded(StringReader("alpha beta"), "beta", 1, 10)

        assertEquals(1, matches.size)
        assertEquals(6L, matches.single().positionChars)
    }

    @Test
    fun boundedReaderSearchRejectsInputPastWorkLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { searchReaderBounded(StringReader("abcdef"), "missing", 1, 5) }
        }
    }
}
