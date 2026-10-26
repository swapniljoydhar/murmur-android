package com.joy.murmur.data.documents

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.concurrent.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EpubParserTest {
    @Test
    fun spineFallbackResolvesChapterHrefFromPackageDirectory() {
        val packageDocument = """
            <package xmlns="http://www.idpf.org/2007/opf">
              <manifest>
                <item id="chapter" href="Text/chapter-1.xhtml" media-type="application/xhtml+xml" />
              </manifest>
              <spine><itemref idref="chapter" /></spine>
            </package>
        """.trimIndent().toByteArray()

        val toc = EpubParser.parseToc(
            entries = mapOf("OEBPS/content.opf" to packageDocument),
            packagePath = "OEBPS/content.opf",
        )

        assertEquals(1, toc.size)
        assertEquals("OEBPS/Text/chapter-1.xhtml", toc.single().href)
        assertEquals("Chapter 1", toc.single().title)
    }

    @Test
    fun chapterOffsetsMatchReaderSeparatorsAndSkipEmptyChapters() {
        val offsets = EpubParser.epubChapterPositions(
            listOf("one.xhtml" to "A", "empty.xhtml" to "  ", "two.xhtml" to "B"),
        )

        assertEquals(mapOf("one.xhtml" to 0L, "two.xhtml" to 3L), offsets)
    }

    @Test
    fun resolveZipPathRejectsTraversalOutsideArchive() {
        assertThrows(IllegalArgumentException::class.java) {
            EpubParser.resolveZipPath("OEBPS/content.opf", "../../outside.xhtml")
        }
    }

    @Test
    fun epubXmlRejectsDoctypeAndExternalEntityDeclarations() {
        val hostileXml = """
            <!DOCTYPE package [<!ENTITY external SYSTEM "file:///private/secret">]>
            <package>&external;</package>
        """.trimIndent().toByteArray()

        assertThrows(Exception::class.java) { EpubParser.parseXml(hostileXml) }
    }

    @Test
    fun archiveRejectsAnOversizedChapterEntry() {
        val archiveBytes = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("chapter.xhtml"))
                zip.write(ByteArray(EpubParser.MAX_CHAPTER_BYTES.toInt() + 1) { 'x'.code.toByte() })
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        assertThrows(IllegalArgumentException::class.java) {
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use(EpubParser::readEpubEntries)
        }
    }

    @Test
    fun archiveEntryCountIsBoundedEvenWhenEntriesAreEmpty() {
        val archiveBytes = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                repeat(EpubParser.MAX_ARCHIVE_ENTRIES + 1) { index ->
                    zip.putNextEntry(ZipEntry("empty-$index"))
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }

        assertThrows(IllegalArgumentException::class.java) {
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use(EpubParser::readEpubEntries)
        }
    }

    @Test
    fun archiveReadingChecksCancellationBetweenEntries() {
        val archiveBytes = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                repeat(3) { index ->
                    zip.putNextEntry(ZipEntry("chapter-$index.xhtml"))
                    zip.write(ByteArray(32) { index.toByte() })
                    zip.closeEntry()
                }
            }
            bytes.toByteArray()
        }
        var checks = 0

        assertThrows(CancellationException::class.java) {
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
                EpubParser.readEpubEntries(zip) {
                    checks++
                    if (checks == 4) throw CancellationException("test cancellation")
                }
            }
        }
    }
}
