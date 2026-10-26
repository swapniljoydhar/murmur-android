package com.joy.murmur.data.audiobooks

import com.joy.murmur.domain.audiobooks.ChapterSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VorbisChapterMappingTest {
    @Test
    fun chaptersWithoutParsedStartsAreNotInvented() {
        val chapters = parseVorbisChapterComments(
            comments = listOf("CHAPTER001NAME=Opening", "CHAPTER002=Middle"),
            durationMs = 12 * 60 * 60 * 1000L,
        )

        assertTrue(chapters.isEmpty())
    }

    @Test
    fun standardVorbisChapterTimestampsAreParsedAndBoundedByDuration() {
        val chapters = parseVorbisChapterComments(
            comments = listOf(
                "CHAPTER01=00:00:00.000",
                "CHAPTER01NAME=First",
                "CHAPTER02=00:00:30.500",
                "CHAPTER02NAME=Second",
                "CHAPTER03=00:01:30.000",
                "CHAPTER03NAME=Outside duration",
            ),
            durationMs = 60_000L,
        )

        assertEquals(2, chapters.size)
        assertEquals(listOf("First", "Second"), chapters.map { it.title })
        assertEquals(listOf(0L, 30_500L), chapters.map { it.startMs })
        assertEquals(30_500L, chapters[0].endMs)
        assertEquals(60_000L, chapters[1].endMs)
        assertTrue(chapters.all { it.source == ChapterSource.VORBIS })
    }

    @Test
    fun legacyVorbisStartKeysAreAcceptedOnlyWhenTheyProvideRealStarts() {
        val chapters = parseVorbisChapterComments(
            comments = listOf(
                "CHAPTER001=Opening",
                "CHAPTER001START=0",
                "CHAPTER002=Next",
                "CHAPTER002START=30000",
            ),
            durationMs = 60_000L,
        )

        assertEquals(listOf(0L, 30_000L), chapters.map { it.startMs })
    }
}
