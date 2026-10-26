package com.joy.murmur.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadAloudPositionWriteTrackerTest {
    @Test
    fun newerWriteSupersedesOlderWriteForSameDocumentOnly() {
        val tracker = ReadAloudPositionWriteTracker()
        val firstDocumentWrite = tracker.begin("book-a")
        val otherDocumentWrite = tracker.begin("book-b")
        val latestDocumentWrite = tracker.begin("book-a")

        assertFalse(tracker.isCurrent("book-a", firstDocumentWrite))
        assertTrue(tracker.isCurrent("book-a", latestDocumentWrite))
        assertTrue(tracker.isCurrent("book-b", otherDocumentWrite))
    }

    @Test
    fun finishingOldWriteCannotClearNewerWrite() {
        val tracker = ReadAloudPositionWriteTracker()
        val oldWrite = tracker.begin("book-a")
        val latestWrite = tracker.begin("book-a")

        tracker.finish("book-a", oldWrite)

        assertTrue(tracker.isCurrent("book-a", latestWrite))
    }
}
