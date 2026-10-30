package com.joy.murmur.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the spoken/rendered index maps used for highlighting and tap-to-read. Markdown cleanup itself is
 * covered by [MarkdownSpeechMappingTest].
 */
class ReadAloudUtilsTest {

    @Test
    fun spokenIndexMapIsIndexPreservingSoItMatchesSpeechOffsets() {
        val source = "one *two* three"
        val map = ReadAloudUtils.buildSpokenIndexMap(source)
        assertEquals(source.length, map.size)
        // Surviving characters keep their own index: the generator's offsets are plain string
        // indexes into the same text. An earlier version numbered only non-space characters, which
        // shifted every offset by the number of preceding spaces.
        assertEquals(0, map[0])
        assertEquals(source.indexOf("two"), map[source.indexOf("two")])
        assertEquals(source.indexOf("three"), map[source.indexOf("three")])
        // Stripped markup is not spoken.
        assertEquals(-1, map[source.indexOf('*')])
    }

    @Test
    fun spokenIndexMapForPlainTextAcceptsEveryCharacter() {
        val source = "1 * 2 is not markdown here\n- and neither is this\n"
        val map = ReadAloudUtils.buildSpokenIndexMap(source, ReadAloudUtils.speechTextFor(source, "text/plain"))
        map.indices.forEach { index -> assertEquals("index $index must stay spoken", index, map[index]) }
    }

    @Test
    fun speechTextOnlyStripsMarkdownForMarkdownDocuments() {
        val source = "## Title"
        assertEquals(source, ReadAloudUtils.speechTextFor(source, "text/plain"))
        assertTrue(ReadAloudUtils.speechTextFor(source, "text/markdown").startsWith(" "))
    }

    @Test
    fun tapOnSpokenTextMapsToItsSpokenOffsetAndTapsOnSyntaxDoNot() {
        val source = "one *two* three"
        val map = ReadAloudUtils.buildSpokenIndexMap(source)
        // "one" is spoken as is, so rendered and spoken offsets agree there.
        assertEquals(0, ReadAloudUtils.spokenOffsetForTap(0, map))
        // The asterisks are markup: tapping them must not resolve to a spoken offset.
        val firstStar = source.indexOf('*')
        assertNull(ReadAloudUtils.spokenOffsetForTap(firstStar, map))
        assertNull("out-of-range taps are ignored", ReadAloudUtils.spokenOffsetForTap(-1, map))
        assertNull(ReadAloudUtils.spokenOffsetForTap(source.length, map))
    }

    @Test
    fun emptyTextProducesEmptyMaps() {
        assertEquals(0, ReadAloudUtils.buildSpokenIndexMap("").size)
        assertEquals(0, ReadAloudUtils.buildInverseSpokenIndexMap(IntArray(0)).size)
    }
}
