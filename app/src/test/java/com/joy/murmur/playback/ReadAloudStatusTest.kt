package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadAloudStatusTest {
    @Test
    fun failureCanBeClearedOnlyForItsDocument() {
        ReadAloudStatus.clearFailure()
        ReadAloudStatus.reportFailure("doc-a", "Speech generation failed")

        ReadAloudStatus.clearFailure("doc-b")
        assertEquals("doc-a", ReadAloudStatus.failure.value?.documentId)

        ReadAloudStatus.clearFailure("doc-a")
        assertNull(ReadAloudStatus.failure.value)
    }

    @Test
    fun blankFailureUsesActionableFallback() {
        ReadAloudStatus.clearFailure()
        ReadAloudStatus.reportFailure("doc-a", "  ")

        assertEquals("Murmur couldn't continue Read Aloud.", ReadAloudStatus.failure.value?.message)
        ReadAloudStatus.clearFailure()
    }
}
