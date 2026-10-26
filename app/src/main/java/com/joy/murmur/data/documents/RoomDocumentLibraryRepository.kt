package com.joy.murmur.data.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Html
import android.provider.OpenableColumns
import com.joy.murmur.data.playback.DocumentStructureEntity
import com.joy.murmur.data.playback.ReaderBookmarkEntity
import com.joy.murmur.data.playback.ReaderDocumentEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.domain.documents.DocumentLibraryRepository
import com.joy.murmur.domain.documents.DocumentSearchMatch
import com.joy.murmur.domain.documents.DocumentStructure
import com.joy.murmur.domain.documents.PdfPage
import com.joy.murmur.domain.documents.ReaderBookmark
import com.joy.murmur.domain.documents.ReaderDocument
import com.joy.murmur.domain.documents.Section
import com.joy.murmur.playback.toStableMediaId
import com.joy.murmur.data.documents.PdfTextExtractor
import com.joy.murmur.data.documents.EpubParser
import com.joy.murmur.data.documents.TocEntry
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Reader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.withContext

class RoomDocumentLibraryRepository(context: Context, private val store: ReaderDocumentStore) : DocumentLibraryRepository {
    private val appContext = context.applicationContext

    override fun observeDocuments(): Flow<List<ReaderDocument>> = store.observeDocuments().map { rows ->
        rows.map { ReaderDocument(it.id, Uri.parse(it.uri), it.title, it.mimeType, it.addedAtMillis, it.positionChars) }
    }

    override fun observeBookmarks(documentId: String): Flow<List<ReaderBookmark>> = store.observeBookmarks(documentId).map { rows ->
        rows.map { ReaderBookmark(it.id, it.documentId, it.positionChars, it.label, it.createdAtMillis) }
    }

    override suspend fun addDocuments(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val rows = uris.distinctBy(Uri::toString).mapNotNull { uri ->
            val name = displayName(uri)
            val mime = appContext.contentResolver.getType(uri)?.lowercase()
            val extension = name.substringAfterLast('.', "").lowercase()
            val supportedMime = mime in setOf("text/plain", "text/markdown", "text/x-markdown", "text/html", "application/epub+zip", "application/pdf")
            val supportedExtension = extension in setOf("txt", "md", "markdown", "html", "htm", "epub", "pdf")
            if (!supportedMime && !supportedExtension) return@mapNotNull null
            val normalizedMime = when {
                extension in setOf("md", "markdown") || mime?.contains("markdown") == true -> "text/markdown"
                extension in setOf("html", "htm") || mime == "text/html" -> "text/html"
                extension == "epub" || mime == "application/epub+zip" -> "application/epub+zip"
                extension == "pdf" || mime == "application/pdf" -> "application/pdf"
                else -> "text/plain"
            }
            ReaderDocumentEntity(uri.toStableMediaId(), uri.toString(), name.substringBeforeLast('.', name), normalizedMime, System.currentTimeMillis(), 0L)
        }
        store.saveDocuments(rows)
        rows.size
    }

    override suspend fun removeDocument(documentId: String) = store.removeDocument(documentId)

    override suspend fun savePosition(documentId: String, positionChars: Long) = store.savePosition(documentId, positionChars.coerceAtLeast(0L))

    override suspend fun addBookmark(documentId: String, positionChars: Long, label: String) {
        store.saveBookmark(ReaderBookmarkEntity(UUID.randomUUID().toString(), documentId, positionChars.coerceAtLeast(0L), label.take(160), System.currentTimeMillis()))
    }

    override suspend fun removeBookmark(bookmarkId: String) = store.removeBookmark(bookmarkId)
    override suspend fun buildDocumentIndex(documentId: String): DocumentStructure {
        val entity = store.getDocumentStructure(documentId)
        if (entity != null) {
            return entity.toDomain()
        }
        // Build index if not exists
        val document = store.getDocument(documentId) ?: throw IllegalArgumentException("Document not found")
        val readerDoc = ReaderDocument(document.id, Uri.parse(document.uri), document.title, document.mimeType, document.addedAtMillis, document.positionChars)
        val structure = DocumentIndexer(appContext).buildIndex(document)
        store.saveDocumentStructure(structure.toEntity())
        return structure
    }

    override suspend fun getDocumentStructure(documentId: String): DocumentStructure? {
        return store.getDocumentStructure(documentId)?.toDomain()
    }

    override suspend fun saveDocumentStructure(structure: DocumentStructure) {
        store.saveDocumentStructure(structure.toEntity())
    }

    override fun observeDocumentStructure(documentId: String): Flow<DocumentStructure?> {
        return store.observeDocumentStructure(documentId).map { it?.toDomain() }
    }

    override suspend fun readPage(document: ReaderDocument, startChar: Long, maxChars: Int): String = withContext(Dispatchers.IO) {
        require(document.mimeType != "application/pdf") { "PDF pages are rendered as images and do not contain extracted text yet." }
        if (document.mimeType == "text/html") return@withContext readHtmlPage(document, startChar, maxChars)
        if (document.mimeType == "application/epub+zip") return@withContext readEpubPage(document.uri, startChar, maxChars)
        val input = appContext.contentResolver.openInputStream(document.uri) ?: throw IllegalStateException("Document is no longer available")
        input.use { raw ->
            val pushback = PushbackInputStream(raw, 3)
            val prefix = ByteArray(3)
            val count = pushback.read(prefix)
            val (charset, bomBytes) = detectCharset(prefix, count)
            if (count > bomBytes) pushback.unread(prefix, bomBytes, count - bomBytes)
            InputStreamReader(pushback, charset).use { reader ->
                val pageLimit = maxChars.coerceIn(1, 24_000)
                var remaining = startChar.coerceAtLeast(0L)
                val discard = CharArray(8192)
                while (remaining > 0L) {
                    currentCoroutineContext().ensureActive()
                    val read = reader.read(discard, 0, minOf(discard.size.toLong(), remaining).toInt())
                    if (read < 0) return@withContext ""
                    remaining -= read
                }
                val output = StringBuilder(pageLimit)
                val buffer = CharArray(4096)
                while (output.length < pageLimit) {
                    currentCoroutineContext().ensureActive()
                    val read = reader.read(buffer, 0, minOf(buffer.size, pageLimit - output.length))
                    if (read < 0) break
                    output.append(buffer, 0, read)
                }
                output.toString()
            }
        }
    }

    override suspend fun renderPdfPage(document: ReaderDocument, pageIndex: Int, targetWidthPx: Int): PdfPage = withContext(Dispatchers.IO) {
        require(document.mimeType == "application/pdf") { "This document isn't a PDF." }
        val descriptor = appContext.contentResolver.openFileDescriptor(document.uri, "r")
            ?: throw IllegalStateException("This PDF is no longer available.")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF doesn't contain any pages." }
                val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safePageIndex).use { page ->
                    val width = minOf(targetWidthPx.coerceIn(360, 1600), page.width.coerceAtLeast(1))
                    val naturalHeight = (width.toDouble() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
                    val pixelScale = kotlin.math.sqrt(MAX_PDF_PIXELS.toDouble() / (width.toDouble() * naturalHeight)).coerceAtMost(1.0)
                    val bitmapWidth = (width * pixelScale).toInt().coerceAtLeast(1)
                    val bitmapHeight = (naturalHeight * pixelScale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val png = ByteArrayOutputStream().use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Murmur couldn't render this PDF page." }
                            output.toByteArray()
                        }
                        PdfPage(safePageIndex, renderer.pageCount, png)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    override suspend fun extractPdfPageText(document: ReaderDocument, pageIndex: Int): String? = withContext(Dispatchers.IO) {
        require(document.mimeType == "application/pdf") { "This document isn't a PDF." }
        
        // Use PdfTextExtractor (API 29+) for text extraction
        val text = PdfTextExtractor.extractPageText(appContext, document.uri, pageIndex)
        if (text != null) return@withContext text
        
        // Fallback: return empty string for older API levels
        val descriptor = appContext.contentResolver.openFileDescriptor(document.uri, "r")
            ?: throw IllegalStateException("This PDF is no longer available.")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF doesn't contain any pages." }
                val safePageIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safePageIndex).use { page ->
                    // Text extraction not available on this API level
                    ""
                }
            }
        }
    }

    override suspend fun searchDocument(document: ReaderDocument, query: String, limit: Int): List<DocumentSearchMatch> = withContext(Dispatchers.IO) {
        val needle = query.trim()
        require(needle.isNotEmpty() && needle.length <= 128) { "Enter between 1 and 128 characters to search." }
        val safeLimit = limit.coerceIn(1, 100)
        when (document.mimeType) {
            "application/pdf" -> {
                // Use PdfTextExtractor (API 29+) for search
                val text = PdfTextExtractor.extractAllText(appContext, document.uri)
                if (text != null) searchText(text, needle, safeLimit)
                else throw IllegalArgumentException("PDF text search requires Android 10 (API 29) or higher.")
            }
            "text/html" -> searchText(readHtmlText(document.uri), needle, safeLimit)
            "application/epub+zip" -> searchText(readEpubText(document.uri), needle, safeLimit)
            else -> {
                val input = appContext.contentResolver.openInputStream(document.uri)
                    ?: throw IllegalStateException("Document is no longer available")
                input.use { raw ->
                    val pushback = PushbackInputStream(raw, 3)
                    val prefix = ByteArray(3)
                    val count = pushback.read(prefix)
                    val (charset, bomBytes) = detectCharset(prefix, count)
                    if (count > bomBytes) pushback.unread(prefix, bomBytes, count - bomBytes)
                    InputStreamReader(pushback, charset).use { reader -> searchReader(reader, needle, safeLimit) }
                }
            }
        }
    }

    private suspend fun readHtmlPage(document: ReaderDocument, startChar: Long, maxChars: Int): String {
        val plainText = readHtmlText(document.uri)
        val start = startChar.coerceAtLeast(0L).coerceAtMost(plainText.length.toLong()).toInt()
        val end = (start.toLong() + maxChars.coerceIn(1, 24_000)).coerceAtMost(plainText.length.toLong()).toInt()
        return plainText.substring(start, end)
    }

    private suspend fun readHtmlText(uri: Uri): String {
        val input = appContext.contentResolver.openInputStream(uri) ?: throw IllegalStateException("Document is no longer available")
        val source = input.use { stream ->
            InputStreamReader(stream, Charsets.UTF_8).use { reader ->
                val maximumCharacters = MAX_HTML_CHARACTERS
                val html = StringBuilder()
                val buffer = CharArray(8192)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = reader.read(buffer)
                    if (count < 0) break
                    if (html.length + count > maximumCharacters) {
                        throw IllegalArgumentException("This HTML file is larger than the current 4 MB reading limit.")
                    }
                    html.append(buffer, 0, count)
                }
                html.toString()
            }
        }
        return htmlToPlainText(source)
    }

    private suspend fun readEpubPage(uri: Uri, startChar: Long, maxChars: Int): String {
        val bookText = readEpubText(uri)
        val start = startChar.coerceAtLeast(0L).coerceAtMost(bookText.length.toLong()).toInt()
        val end = (start.toLong() + maxChars.coerceIn(1, 24_000)).coerceAtMost(bookText.length.toLong()).toInt()
        return bookText.substring(start, end)
    }

    private suspend fun readEpubText(uri: Uri): String {
        val archiveEntries = EpubParser.readEntries(appContext, uri)
        val container = archiveEntries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("This EPUB is missing its package information.")
        val containerXml = EpubParser.parseXml(container)
        val rootfile = EpubParser.elements(containerXml, "rootfile").firstOrNull()
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        val packagePath = rootfile.getAttribute("full-path").takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        val packageXml = archiveEntries[packagePath]
            ?: throw IllegalArgumentException("This EPUB book package could not be opened.")
        val packageDocument = EpubParser.parseXml(packageXml)
        val manifest = EpubParser.elements(packageDocument, "item").associate { item ->
            item.getAttribute("id") to EpubParser.resolveZipPath(packagePath, item.getAttribute("href"))
        }
        val chapterPaths = EpubParser.elements(packageDocument, "itemref")
            .mapNotNull { manifest[it.getAttribute("idref")] }
            .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
        if (chapterPaths.isEmpty()) throw IllegalArgumentException("This EPUB doesn't contain readable chapters.")
        val bookText = StringBuilder()
        chapterPaths.forEach { path ->
            currentCoroutineContext().ensureActive()
            val chapter = archiveEntries[path] ?: return@forEach
            val text = htmlToPlainText(chapter.toString(Charsets.UTF_8))
            if (text.isNotBlank()) {
                if (bookText.isNotEmpty()) bookText.append("\n\n")
                if (bookText.length + text.length > MAX_HTML_CHARACTERS) {
                    throw IllegalArgumentException("This EPUB is larger than Murmur's current 4 MB reading limit.")
                }
                bookText.append(text)
            }
        }
        if (bookText.isEmpty()) throw IllegalArgumentException("This EPUB doesn't contain readable text.")
        return bookText.toString()
    }

    private suspend fun searchReader(reader: Reader, query: String, limit: Int): List<DocumentSearchMatch> {
        val matches = mutableListOf<DocumentSearchMatch>()
        val buffer = CharArray(8192)
        var consumed = 0L
        var tail = ""
        while (matches.size < limit) {
            currentCoroutineContext().ensureActive()
            val count = reader.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            val window = tail + String(buffer, 0, count)
            val windowStart = consumed - tail.length
            var searchFrom = (tail.length - query.length + 1).coerceAtLeast(0)
            while (matches.size < limit) {
                val index = window.indexOf(query, searchFrom, ignoreCase = true)
                if (index < 0) break
                if (index + query.length > tail.length) {
                    matches += DocumentSearchMatch(
                        positionChars = (windowStart + index).coerceAtLeast(0L),
                        snippet = snippetAround(window, index, query.length),
                    )
                }
                searchFrom = index + 1
            }
            consumed += count
            tail = window.takeLast((query.length - 1).coerceAtLeast(0))
        }
        return matches
    }

    private suspend fun searchText(text: String, query: String, limit: Int): List<DocumentSearchMatch> {
        val matches = mutableListOf<DocumentSearchMatch>()
        var searchFrom = 0
        while (matches.size < limit) {
            currentCoroutineContext().ensureActive()
            val index = text.indexOf(query, searchFrom, ignoreCase = true)
            if (index < 0) break
            matches += DocumentSearchMatch(index.toLong(), snippetAround(text, index, query.length))
            searchFrom = index + 1
        }
        return matches
    }

    private fun snippetAround(text: String, matchIndex: Int, queryLength: Int): String {
        val start = (matchIndex - 48).coerceAtLeast(0)
        val end = (matchIndex + queryLength + 72).coerceAtMost(text.length)
        val prefix = if (start > 0) "â€¦" else ""
        val suffix = if (end < text.length) "â€¦" else ""
        return prefix + text.substring(start, end).replace(Regex("\\s+"), " ").trim() + suffix
    }

    private fun htmlToPlainText(source: String): String = Html.fromHtml(
        source.replace(Regex("(?is)<(script|style|head)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?s)<!--.*?-->"), " "),
        Html.FROM_HTML_MODE_COMPACT,
    ).toString().replace('\uFFFC', ' ').removePrefix("\uFEFF")

    private fun displayName(uri: Uri): String {
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank)?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank) ?: "Untitled document"
    }

    private fun detectCharset(prefix: ByteArray, count: Int): Pair<Charset, Int> = when {
        count >= 3 && prefix[0] == 0xEF.toByte() && prefix[1] == 0xBB.toByte() && prefix[2] == 0xBF.toByte() -> Charsets.UTF_8 to 3
        count >= 2 && prefix[0] == 0xFF.toByte() && prefix[1] == 0xFE.toByte() -> Charsets.UTF_16LE to 2
        count >= 2 && prefix[0] == 0xFE.toByte() && prefix[1] == 0xFF.toByte() -> Charsets.UTF_16BE to 2
        else -> Charsets.UTF_8 to 0
    }

    override suspend fun getEpubToc(documentId: String): List<TocEntry> = withContext(Dispatchers.IO) {
        val document = store.getDocument(documentId) ?: throw IllegalArgumentException("Document not found")
        require(document.mimeType == "application/epub+zip") { "Document is not an EPUB" }
        val uri = Uri.parse(document.uri)
        val entries = EpubParser.readEntries(appContext, uri)
        val container = entries["META-INF/container.xml"]
            ?: throw IllegalArgumentException("This EPUB is missing its package information.")
        val containerXml = EpubParser.parseXml(container)
        val rootfile = EpubParser.elements(containerXml, "rootfile").firstOrNull()
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        val packagePath = rootfile.getAttribute("full-path").takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("This EPUB has no readable book package.")
        return@withContext EpubParser.parseToc(entries, packagePath)
    }

    private companion object {
        const val MAX_HTML_CHARACTERS = 4 * 1024 * 1024
        const val MAX_EPUB_METADATA_BYTES = 1024 * 1024
        const val MAX_EPUB_CHAPTER_BYTES = 4 * 1024 * 1024
        const val MAX_EPUB_CAPTURED_BYTES = 16 * 1024 * 1024
        const val MAX_EPUB_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L
        const val MAX_PDF_PIXELS = 4_000_000L
    }
}

private fun DocumentStructureEntity.toDomain(): DocumentStructure {
    val sections = Json { ignoreUnknownKeys = true }.decodeFromString<List<Section>>(sectionsJson)
    return DocumentStructure(
        documentId = documentId,
        mimeType = mimeType,
        totalChars = totalChars,
        totalLines = totalLines,
        sections = sections,
        fingerprint = fingerprint,
        indexVersion = indexVersion,
    )
}

private fun DocumentStructure.toEntity(): DocumentStructureEntity {
    return DocumentStructureEntity(
        documentId = documentId,
        mimeType = mimeType,
        totalChars = totalChars,
        totalLines = totalLines,
        sectionsJson = Json { prettyPrint = true }.encodeToString(sections),
        fingerprint = fingerprint,
        indexVersion = indexVersion,
        createdAtMillis = System.currentTimeMillis(),
        updatedAtMillis = System.currentTimeMillis(),
    )
}
