package com.joy.murmur.data.audiobooks

import com.joy.murmur.domain.audiobooks.ChapterSource
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Id3ChapterParserTest {
    @Test
    fun parsesId3v23ChapterTimesAndUtf8Titles() {
        val tag = id3Tag(
            version = 3,
            chapters = listOf(
                chapterFrame("one", 0L, 0xFFFF_FFFFL, "Opening", 3),
                chapterFrame("two", 12_345L, 20_000L, "The middle", 3),
            ),
        )

        val chapters = parseId3ChapterTag(tag, 30_000L)

        assertEquals(listOf("Opening", "The middle"), chapters.map { it.title })
        assertEquals(listOf(0L, 12_345L), chapters.map { it.startMs })
        assertEquals(listOf(12_345L, 20_000L), chapters.map { it.endMs })
        assertTrue(chapters.all { it.source == ChapterSource.ID3 })
    }

    @Test
    fun parsesId3v24SynchsafeFrameSizesAndUsesDurationForLastEnd() {
        val tag = id3Tag(
            version = 4,
            chapters = listOf(chapterFrame("only", 1_500L, 0xFFFF_FFFFL, "Single", 4)),
        )

        val chapters = parseId3ChapterTag(tag, 9_000L)

        assertEquals(1, chapters.size)
        assertEquals(1_500L, chapters.single().startMs)
        assertEquals(9_000L, chapters.single().endMs)
    }

    @Test
    fun rejectsTruncatedTagsAndChaptersBeyondKnownDuration() {
        val outOfRange = id3Tag(
            version = 3,
            chapters = listOf(chapterFrame("late", 60_000L, 65_000L, "Late", 3)),
        )

        assertTrue(parseId3ChapterTag(outOfRange, 60_000L).isEmpty())
        assertTrue(parseId3ChapterTag(outOfRange.copyOf(outOfRange.size - 1), 90_000L).isEmpty())
    }

    private fun chapterFrame(elementId: String, startMs: Long, endMs: Long, title: String, version: Int): ByteArray {
        val titleBytes = title.toByteArray(Charsets.UTF_8)
        val titleFrame = id3Frame("TIT2", byteArrayOf(3) + titleBytes, version)
        val chapterPayload = ByteArrayOutputStream().apply {
            write(elementId.toByteArray(Charsets.ISO_8859_1))
            write(0)
            write(uint32(startMs))
            write(uint32(endMs))
            write(byteArrayOf(-1, -1, -1, -1))
            write(byteArrayOf(-1, -1, -1, -1))
            write(titleFrame)
        }.toByteArray()
        return id3Frame("CHAP", chapterPayload, version)
    }

    private fun id3Frame(id: String, payload: ByteArray, version: Int): ByteArray = ByteArrayOutputStream().apply {
        write(id.toByteArray(Charsets.US_ASCII))
        write(if (version == 4) synchsafe(payload.size) else uint32(payload.size.toLong()))
        write(byteArrayOf(0, 0))
        write(payload)
    }.toByteArray()

    private fun id3Tag(version: Int, chapters: List<ByteArray>): ByteArray {
        val frames = ByteArrayOutputStream().apply { chapters.forEach(::write) }.toByteArray()
        return byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0) +
            synchsafe(frames.size) + frames
    }

    private fun uint32(value: Long): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value.toInt())
        .array()

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        ((value ushr 21) and 0x7F).toByte(),
        ((value ushr 14) and 0x7F).toByte(),
        ((value ushr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )
}
