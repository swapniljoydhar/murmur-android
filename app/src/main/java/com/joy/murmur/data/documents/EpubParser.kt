package com.joy.murmur.data.documents

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

internal object EpubParser {
    const val MAX_METADATA_BYTES = 1024 * 1024
    const val MAX_CHAPTER_BYTES = 4L * 1024 * 1024
    const val MAX_CAPTURED_BYTES = 16L * 1024 * 1024
    const val MAX_UNCOMPRESSED_BYTES = 64L * 1024 * 1024
    const val MAX_ARCHIVE_ENTRIES = 10_000
    const val MAX_ENTRY_NAME_LENGTH = 4_096

    fun readEntries(
        context: Context,
        uri: Uri,
        checkCancelled: () -> Unit = {},
    ): Map<String, ByteArray> {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Document unavailable")
        return input.use { raw ->
            ZipInputStream(raw).use { zip -> readEpubEntries(zip, checkCancelled) }
        }
    }

    fun readEpubEntries(zip: ZipInputStream, checkCancelled: () -> Unit = {}): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        val buffer = ByteArray(8192)
        var totalUncompressed = 0L
        var totalCaptured = 0L
        var entryCount = 0
        while (true) {
            checkCancelled()
            val entry = zip.nextEntry ?: break
            entryCount++
            require(entryCount <= MAX_ARCHIVE_ENTRIES) { "EPUB contains too many archive entries." }
            val name = entry.name
            require(name.length <= MAX_ENTRY_NAME_LENGTH) { "EPUB archive entry name is too long." }
            val isMetadata = name == "META-INF/container.xml" || name.endsWith(".opf", ignoreCase = true)
            val isChapter = name.endsWith(".xhtml", ignoreCase = true) || name.endsWith(".html", ignoreCase = true) || name.endsWith(".htm", ignoreCase = true)
            val isToc = isTocEntryName(name)
            val captureLimit = when {
                isMetadata -> MAX_METADATA_BYTES.toLong()
                isChapter -> MAX_CHAPTER_BYTES
                isToc -> MAX_CHAPTER_BYTES
                else -> 0L
            }
            val output = if (captureLimit > 0) ByteArrayOutputStream() else null
            var entrySize = 0
            while (true) {
                checkCancelled()
                val count = zip.read(buffer)
                if (count < 0) break
                totalUncompressed += count
                entrySize += count
                if (totalUncompressed > MAX_UNCOMPRESSED_BYTES) {
                    throw IllegalArgumentException("EPUB exceeds 64 MB safety limit.")
                }
                if (entrySize.toLong() > captureLimit && captureLimit > 0) {
                    throw IllegalArgumentException("EPUB chapter/package too large.")
                }
                if (output != null) output.write(buffer, 0, count)
            }
            if (output != null) {
                totalCaptured += output.size()
                if (totalCaptured > MAX_CAPTURED_BYTES) {
                    throw IllegalArgumentException("EPUB contains too much chapter text.")
                }
                entries[name] = output.toByteArray()
            }
            zip.closeEntry()
        }
        return entries
    }

    internal fun isTocEntryName(entryName: String): Boolean {
        val baseName = entryName.substringAfterLast('/').lowercase()
        return baseName.endsWith(".ncx") ||
            baseName in setOf("toc.xhtml", "toc.html", "toc.htm", "nav.xhtml", "nav.html", "nav.htm")
    }

    fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            setExpandEntityReferences(false)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    fun elements(document: Document, name: String): List<Element> {
        val namespaced = document.getElementsByTagNameNS("*", name)
        val nodes = if (namespaced.length > 0) namespaced else document.getElementsByTagName(name)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    fun elements(element: Element, name: String): List<Element> {
        val namespaced = element.getElementsByTagNameNS("*", name)
        val nodes = if (namespaced.length > 0) namespaced else element.getElementsByTagName(name)
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

    fun resolveZipPath(packagePath: String, href: String): String {
        require(href.isNotBlank()) { "Invalid EPUB chapter path." }
        val resolved = URI(packagePath).resolve(URI(href.substringBefore('#'))).normalize().path.removePrefix("/")
        require(resolved.isNotBlank() && !resolved.startsWith("../") && resolved != "..") {
            "EPUB chapter path outside package."
        }
        return resolved
    }

    /**
     * Extracts the table of contents from an EPUB.
     * Supports both EPUB 2 (NCX) and EPUB 3 (nav.xhtml) formats.
     * Returns a list of TOC entries with title, href, and level.
     */
    fun parseToc(entries: Map<String, ByteArray>, packagePath: String): List<TocEntry> {
        val tocEntries = mutableListOf<TocEntry>()

        // Try EPUB 3 navigation document first
        val navPath = entries.keys.firstOrNull {
            val base = it.substringAfterLast('/').lowercase()
            base == "nav.xhtml" || base == "nav.html" || base == "nav.htm"
        }
        if (navPath != null) {
            val navXml = entries[navPath]
            if (navXml != null) {
                parseNavXhtml(navXml, packagePath, tocEntries)
                if (tocEntries.isNotEmpty()) return withChapterPositions(entries, packagePath, tocEntries)
            }
        }

        // Fallback to EPUB 2 NCX
        val ncxPath = entries.keys.firstOrNull { it.substringAfterLast('/').endsWith(".ncx", ignoreCase = true) }
        if (ncxPath != null) {
            val ncxXml = entries[ncxPath]
            if (ncxXml != null) {
                parseNcx(ncxXml, packagePath, tocEntries)
                if (tocEntries.isNotEmpty()) return withChapterPositions(entries, packagePath, tocEntries)
            }
        }

        // Last resort: use spine order from package document
        val spinePath = entries.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
        if (spinePath != null) {
            val spineXml = entries[spinePath]
            if (spineXml != null) {
                parseSpine(spineXml, entries, tocEntries, packagePath)
            }
        }

        return withChapterPositions(entries, packagePath, tocEntries)
    }

    private fun withChapterPositions(
        entries: Map<String, ByteArray>,
        packagePath: String,
        tocEntries: List<TocEntry>,
    ): List<TocEntry> {
        val packageBytes = entries[packagePath] ?: return tocEntries
        val packageDocument = parseXml(packageBytes)
        val manifest = elements(packageDocument, "item").associate { item ->
            item.getAttribute("id") to resolveZipPath(packagePath, item.getAttribute("href"))
        }
        val chapters = elements(packageDocument, "itemref")
            .mapNotNull { manifest[it.getAttribute("idref")] }
            .filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) }
            .mapNotNull { path ->
                val chapter = entries[path] ?: return@mapNotNull null
                path to htmlToPlainText(chapter.toString(Charsets.UTF_8))
            }
        val chapterPositions = epubChapterPositions(chapters)
        return tocEntries.map { entry -> entry.copy(positionChars = chapterPositions[entry.href]) }
    }

    internal fun epubChapterPositions(chapters: List<Pair<String, String>>): Map<String, Long> {
        var position = 0L
        var hasText = false
        val positions = mutableMapOf<String, Long>()
        chapters.forEach { (path, text) ->
            if (text.isNotBlank()) {
                if (hasText) position += 2
                positions[path] = position
                position += text.length
                hasText = true
            }
        }
        return positions
    }

    private fun parseNavXhtml(navBytes: ByteArray, packagePath: String, tocEntries: MutableList<TocEntry>) {
        val doc = parseXml(navBytes)
        val navElements = elements(doc, "nav").filter { it.getAttribute("epub:type") == "toc" }
        navElements.forEach { nav ->
            val ol = elements(nav, "ol").firstOrNull()
            ol?.let { parseNavList(it, 1, packagePath, tocEntries) }
        }
    }

    private fun parseNavList(ol: Element, level: Int, packagePath: String, tocEntries: MutableList<TocEntry>) {
        val lis = elements(ol, "li")
        lis.forEach { li ->
            val a = elements(li, "a").firstOrNull()
            val title = a?.textContent?.trim() ?: li.textContent?.trim() ?: ""
            val href = a?.getAttribute("href")
            if (title.isNotBlank() && href != null && href.isNotBlank()) {
                val resolvedPath = resolveZipPath(packagePath, href.substringBefore('#'))
                tocEntries.add(TocEntry(title, resolvedPath, level))
            }
            // Check for nested ol (sub-chapters)
            val nestedOl = elements(li, "ol").firstOrNull()
            nestedOl?.let { parseNavList(it, level + 1, packagePath, tocEntries) }
        }
    }

    private fun parseNcx(ncxBytes: ByteArray, packagePath: String, tocEntries: MutableList<TocEntry>) {
        val doc = parseXml(ncxBytes)
        val navMap = elements(doc, "navMap").firstOrNull()
        navMap?.let {
            val navPoints = elements(it, "navPoint")
            navPoints.forEach { parseNavPoint(it, 1, packagePath, tocEntries) }
        }
    }

    private fun parseNavPoint(navPoint: Element, level: Int, packagePath: String, tocEntries: MutableList<TocEntry>) {
        val label = elements(navPoint, "navLabel").firstOrNull()
        val title = label?.let { elements(it, "text").firstOrNull()?.textContent?.trim() } ?: ""
        val content = elements(navPoint, "content").firstOrNull()
        val src = content?.getAttribute("src")
        if (title.isNotBlank() && src != null && src.isNotBlank()) {
            val resolvedPath = resolveZipPath(packagePath, src.substringBefore('#'))
            tocEntries.add(TocEntry(title, resolvedPath, level))
        }
        // Nested navPoints
        elements(navPoint, "navPoint").forEach { parseNavPoint(it, level + 1, packagePath, tocEntries) }
    }

    private fun parseSpine(
        spineBytes: ByteArray,
        entries: Map<String, ByteArray>,
        tocEntries: MutableList<TocEntry>,
        packagePath: String,
    ) {
        val doc = parseXml(spineBytes)
        val itemrefs = elements(doc, "itemref")
        val manifest = elements(doc, "item").associate { item ->
            item.getAttribute("id") to resolveZipPath(packagePath, item.getAttribute("href"))
        }
        itemrefs.forEachIndexed { index, itemref ->
            val idref = itemref.getAttribute("idref")
            val path = manifest[idref]
            if (path != null) {
                val chapter = entries[path]
                val title = if (chapter != null) {
                    val text = htmlToPlainText(chapter.toString(Charsets.UTF_8))
                    text.lines().firstOrNull { it.isNotBlank() } ?: "Chapter ${index + 1}"
                } else "Chapter ${index + 1}"
                tocEntries.add(TocEntry(title, path, 1))
            }
        }
    }

    /**
     * Extracts plain text from HTML content.
     */
    private fun htmlToPlainText(source: String): String = android.text.Html.fromHtml(
        source.replace(Regex("(?is)<(script|style|head)\\b[^>]*>.*?</\\1\\s*>"), " ")
            .replace(Regex("(?s)<!--.*?-->"), " "),
        android.text.Html.FROM_HTML_MODE_COMPACT,
    ).toString().replace('\uFFFC', ' ').removePrefix("\uFEFF")
}

data class TocEntry(
    val title: String,
    val href: String,
    val level: Int = 1,
    val positionChars: Long? = null,
) {
    val id: String
        get() = "toc_${href.hashCode().toString(36)}"
}
