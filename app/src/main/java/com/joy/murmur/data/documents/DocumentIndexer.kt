package com.joy.murmur.data.documents

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import android.text.Html
import com.joy.murmur.data.playback.ReaderDocumentEntity
import com.joy.murmur.domain.documents.DocumentStructure
import com.joy.murmur.domain.documents.Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Reader
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

import com.joy.murmur.data.documents.EpubParser

class DocumentIndexer(private val context: Context) {

    companion object {
        const val INDEX_VERSION = 1
        const val MAX_TEXT_CHARACTERS = 50L * 1024 * 1024 // 50 MB for large documents
        const val MAX_EPUB_METADATA_BYTES = 1024 * 1024
        const val MAX_EPUB_CHAPTER_BYTES = 4L * 1024 * 1024
        const val MAX_EPUB_CAPTURED_BYTES = 16L * 1024 * 1024
        const val MAX_EPUB_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
        const val MAX_PDF_PAGES = 5000
        const val BOM_PREFIX_SIZE = 3
    }

    suspend fun buildIndex(document: ReaderDocumentEntity): DocumentStructure = withContext(Dispatchers.IO) {
        val mime = document.mimeType.lowercase()
        val (text, sections) = when (mime) {
            "text/plain", "text/markdown", "text/x-markdown" -> indexText(document.uri, mime)
            "text/html" -> indexHtml(document.uri)
            "application/epub+zip" -> indexEpub(document.uri)
            "application/pdf" -> indexPdf(document.uri)
            else -> indexText(document.uri, "text/plain")
        }
        DocumentStructure(
            documentId = document.id,
            mimeType = mime,
            totalChars = text.length.toLong(),
            totalLines = if (text.isEmpty()) 0L else (text.count { it == '\n' } + 1).toLong(),
            sections = sections,
            fingerprint = fingerprintOf(text),
            indexVersion = INDEX_VERSION,
        )
    }

    private suspend fun indexText(uriString: String, mimeType: String): Pair<String, List<Section>> = withContext(Dispatchers.IO) {
        val isMarkdown = mimeType.contains("markdown")
        val text = readText(uriString)
        val sections = if (isMarkdown) markdownSections(text) else blockSections(text)
        text to sections
    }

    private suspend fun indexHtml(uriString: String): Pair<String, List<Section>> = withContext(Dispatchers.IO) {
        val source = readText(uriString)
        val text = htmlToPlainText(source)
        text to blockSections(text)
    }

    private suspend fun indexEpub(uriString: String): Pair<String, List<Section>> = withContext(Dispatchers.IO) {
        val uri = Uri.parse(uriString)
        val coroutineContext = currentCoroutineContext()
        val entries = EpubParser.readEntries(context, uri) { coroutineContext.ensureActive() }
        val container = entries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("This EPUB is missing its package information.")
        val containerXml = EpubParser.parseXml(container)
        val rootfile = EpubParser.elements(containerXml, "rootfile").firstOrNull()
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        val packagePath = rootfile.getAttribute("full-path").takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        val packageXml = entries[packagePath]
            ?: throw IllegalArgumentException("This EPUB book package could not be opened.")
        val packageDocument = EpubParser.parseXml(packageXml)
        val manifest = EpubParser.elements(packageDocument, "item").associate { item ->
            val id = item.getAttribute("id") ?: ""
            id to EpubParser.resolveZipPath(packagePath, item.getAttribute("href"))
        }
        val chapterPaths = EpubParser.elements(packageDocument, "itemref")
            .mapNotNull { ref ->
                val idref = ref.getAttribute("idref")
                if (idref.isNullOrBlank()) null else manifest[idref]
            }
            .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
        if (chapterPaths.isEmpty()) throw IllegalArgumentException("This EPUB doesn't contain readable chapters.")

        // Stream chapters to avoid loading all into memory
        val sections = mutableListOf<Section>()
        var sectionCounter = 0
        val output = StringBuilder()
        
        chapterPaths.forEachIndexed { index, path ->
            coroutineContext.ensureActive()
            val chapter = entries[path] ?: return@forEachIndexed
            val text = htmlToPlainText(chapter.toString(StandardCharsets.UTF_8))
            if (text.isBlank()) return@forEachIndexed
            if (output.isNotEmpty()) output.append("\n\n")
            val startChar = output.length.toLong()
            output.append(text)
            sections += Section(
                id = "section-${++sectionCounter}",
                title = "Chapter ${index + 1}",
                startChar = startChar,
                endChar = output.length.toLong(),
                startLine = (countNewlines(text, 0L) + 1).coerceAtLeast(1L),
                level = 1,
            )
        }
        if (output.isEmpty()) throw IllegalArgumentException("This EPUB doesn't contain readable text.")
        output.toString() to sections
    }

    private suspend fun indexPdf(uriString: String): Pair<String, List<Section>> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            return@withContext "" to emptyList()
        }
        val uri = Uri.parse(uriString)
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("PDF unavailable.")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(renderer.pageCount in 1..MAX_PDF_PAGES) {
                    "PDF indexing is limited to $MAX_PDF_PAGES pages."
                }
                val pageCount = renderer.pageCount
                val sections = mutableListOf<Section>()
                val output = StringBuilder()
                var charOffset = 0L
                
                for (i in 0 until pageCount) {
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val page = renderer.openPage(i)
                    try {
                        val pageText = extractPageText(page)
                        if (pageText.isNotBlank()) {
                            if (output.length + pageText.length + 2 > MAX_TEXT_CHARACTERS) {
                                throw IllegalArgumentException(
                                    "PDF text exceeds the ${MAX_TEXT_CHARACTERS / 1024 / 1024} MB text limit."
                                )
                            }
                            val pageTitle = "Page ${i + 1}"
                            val startChar = charOffset
                            output.append(pageText).append("\n\n")
                            charOffset = output.length.toLong()
                            sections += Section(
                                id = "section-${i + 1}",
                                title = pageTitle,
                                startChar = startChar,
                                endChar = charOffset,
                                startLine = i.toLong() + 1,
                                level = 1,
                            )
                        }
                    } finally {
                        page.close()
                    }
                }
                output.toString() to sections
            }
        }
    }

    private fun extractPageText(page: PdfRenderer.Page): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            extractPageTextApi35(page)
        } else {
            ""
        }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractPageTextApi35(page: PdfRenderer.Page): String = page.textContents
        .asSequence()
        .map { it.text }
        .filter(String::isNotBlank)
        .joinToString(" ")
        .trim()

    private suspend fun readText(uriString: String): String = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        val uri = Uri.parse(uriString)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Document is no longer available")
        input.use { raw ->
            val pushback = PushbackInputStream(raw, BOM_PREFIX_SIZE)
            val prefix = ByteArray(BOM_PREFIX_SIZE)
            val count = pushback.read(prefix)
            val (charset, bomBytes) = detectCharset(prefix, count)
            if (count > bomBytes) pushback.unread(prefix, bomBytes, count - bomBytes)
            InputStreamReader(pushback, charset).use { reader ->
                val output = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = reader.read(buffer)
                    if (read < 0) break
                    if (output.length + read > MAX_TEXT_CHARACTERS) {
                        throw IllegalArgumentException(
                            "Document exceeds the ${MAX_TEXT_CHARACTERS / 1024 / 1024} MB text limit."
                        )
                    }
                    output.append(buffer, 0, read)
                }
                output.toString()
            }
        }
    }

    private fun markdownSections(text: String): List<Section> {
        val sections = mutableListOf<Section>()
        var offset = 0L
        var lineNo = 0L
        var sectionCounter = 0
        var openStart = 0L
        var openLine = 0L
        var openTitle: String? = null
        var openLevel = 1

        fun closeSection(endChar: Long, endLine: Long) {
            openTitle?.let { title ->
                sections += Section(
                    id = "section-${++sectionCounter}",
                    title = title,
                    startChar = openStart,
                    endChar = endChar,
                    startLine = openLine,
                    level = openLevel,
                )
            }
            openTitle = null
        }

        for (rawLine in text.split("\n")) {
            lineNo++
            val lineStart = offset
            val heading = markdownHeading(rawLine)
            if (heading != null) {
                closeSection(lineStart, lineNo - 1)
                openStart = lineStart
                openLine = lineNo
                openTitle = heading.title
                openLevel = heading.level
            }
            offset += rawLine.length + 1
        }
        closeSection(text.length.toLong(), lineNo)

        return if (sections.isEmpty()) {
            listOf(Section("section-1", "Document", 0, text.length.toLong(), 0, 1))
        } else sections
    }

    private fun blockSections(text: String): List<Section> {
        val sections = mutableListOf<Section>()
        var offset = 0L
        var lineNo = 0L
        var sectionCounter = 0
        var blockStart = 0L
        var blockStartLine = 0L
        var blockTitle: String? = null
        var open = false

        fun closeBlock(endChar: Long, endLine: Long) {
            val title = blockTitle
            if (open && title != null) {
                sections += Section(
                    id = "section-${++sectionCounter}",
                    title = title,
                    startChar = blockStart,
                    endChar = endChar,
                    startLine = blockStartLine,
                    level = 1,
                )
            }
            open = false
            blockTitle = null
        }

        for (rawLine in text.split("\n")) {
            lineNo++
            val lineStart = offset
            if (rawLine.isBlank()) {
                closeBlock(lineStart, lineNo - 1)
            } else if (!open) {
                open = true
                blockStart = lineStart
                blockStartLine = lineNo
                blockTitle = rawLine.trim().take(120)
            }
            offset += rawLine.length + 1
        }
        closeBlock(text.length.toLong(), lineNo)

        return if (sections.isEmpty()) {
            listOf(Section("section-1", "Document", 0, text.length.toLong(), 0, 1))
        } else sections
    }

    private fun markdownHeading(line: String): Heading? {
        val trimmed = line.trimStart()
        val match = Regex("^#{1,6}\\s+(.+)").find(trimmed) ?: return null
        val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
        return Heading(match.groupValues[1].trim(), level)
    }

    private fun htmlToPlainText(source: String): String = Html.fromHtml(
        source.replace(Regex("(?is)<(script|style|head)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?s)<!--.*?-->"), " "),
        Html.FROM_HTML_MODE_COMPACT,
    ).toString().replace('\uFFFC', ' ').removePrefix("\uFEFF")

    private fun detectCharset(prefix: ByteArray, count: Int): Pair<Charset, Int> = when {
        count >= 3 && prefix[0] == 0xEF.toByte() && prefix[1] == 0xBB.toByte() && prefix[2] == 0xBF.toByte() ->
            StandardCharsets.UTF_8 to 3
        count >= 2 && prefix[0] == 0xFF.toByte() && prefix[1] == 0xFE.toByte() ->
            StandardCharsets.UTF_16LE to 2
        count >= 2 && prefix[0] == 0xFE.toByte() && prefix[1] == 0xFF.toByte() ->
            StandardCharsets.UTF_16BE to 2
        else -> StandardCharsets.UTF_8 to 0
    }

    private fun fingerprintOf(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun countNewlines(text: String, start: Long): Long {
        var count = 0L
        var index = start.toInt()
        while (true) {
            index = text.indexOf('\n', index)
            if (index < 0) break
            count++
            index++
        }
        return count
    }

    private data class Heading(val title: String, val level: Int)
}
