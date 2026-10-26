package com.joy.murmur.data.documents

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.joy.murmur.domain.documents.DocumentSearchMatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** PDF text support provided by Android 15 and later. */
internal object PdfTextExtractor {
    private const val MAX_PDF_PAGES = 5_000
    private const val MAX_PDF_PAGE_TEXT_CHARACTERS = 1_048_576
    private const val SNIPPET_RADIUS = 36

    suspend fun extractPageText(context: Context, uri: Uri, pageIndex: Int): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        return withContext(Dispatchers.IO) {
            extractPageTextApi35(context, uri, pageIndex)
        }
    }

    suspend fun searchDocument(
        context: Context,
        uri: Uri,
        query: String,
        limit: Int,
    ): List<DocumentSearchMatch>? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return null
        require(query.isNotBlank()) { "Enter text to search for." }
        require(limit in 1..100) { "The PDF search result limit must be between 1 and 100." }

        return withContext(Dispatchers.IO) {
            searchDocumentApi35(context, uri, query, limit)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractPageTextApi35(context: Context, uri: Uri, pageIndex: Int): String? {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        return descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                if (pageIndex !in 0 until renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    extractBoundedPageText(page)
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun searchDocumentApi35(
        context: Context,
        uri: Uri,
        query: String,
        limit: Int,
    ): List<DocumentSearchMatch> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("This PDF is no longer available.")
        return descriptor.use { fileDescriptor ->
            PdfRenderer(fileDescriptor).use { renderer ->
                require(renderer.pageCount <= MAX_PDF_PAGES) {
                    "PDF text search is limited to $MAX_PDF_PAGES pages."
                }
                val matches = mutableListOf<DocumentSearchMatch>()
                var sawExtractableText = false
                for (pageIndex in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    renderer.openPage(pageIndex).use { page ->
                        val text = extractBoundedPageText(page).orEmpty()
                        sawExtractableText = sawExtractableText || text.isNotBlank()
                        var searchFrom = 0
                        while (matches.size < limit) {
                            val index = text.indexOf(query, searchFrom, ignoreCase = true)
                            if (index < 0) break
                            val start = (index - SNIPPET_RADIUS).coerceAtLeast(0)
                            val end = (index + query.length + SNIPPET_RADIUS).coerceAtMost(text.length)
                            val prefix = if (start > 0) "…" else ""
                            val suffix = if (end < text.length) "…" else ""
                            matches += DocumentSearchMatch(
                                positionChars = pageIndex.toLong(),
                                snippet = prefix + text.substring(start, end).trim() + suffix,
                            )
                            searchFrom = index + query.length.coerceAtLeast(1)
                        }
                    }
                    if (matches.size >= limit) break
                }
                require(sawExtractableText) { "This PDF has no extractable text. Scanned PDFs aren't supported." }
                matches
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun extractBoundedPageText(page: PdfRenderer.Page): String? {
        val text = StringBuilder()
        page.textContents.forEach { content ->
            if (content.text.isBlank()) return@forEach
            val separatorLength = if (text.isEmpty()) 0 else 1
            require(text.length + separatorLength + content.text.length <= MAX_PDF_PAGE_TEXT_CHARACTERS) {
                "PDF page text exceeds Murmur's 1 MB per-page limit."
            }
            if (separatorLength != 0) text.append(' ')
            text.append(content.text)
        }
        return text.toString().trim().takeIf(String::isNotEmpty)
    }
}
