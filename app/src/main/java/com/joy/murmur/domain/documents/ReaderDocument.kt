package com.joy.murmur.domain.documents

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class ReaderDocument(
    val id: String,
    val uri: Uri,
    val title: String,
    val mimeType: String,
    val addedAtMillis: Long,
    val positionChars: Long,
)

data class ReaderBookmark(
    val id: String,
    val documentId: String,
    val positionChars: Long,
    val label: String,
    val createdAtMillis: Long,
)

data class PdfPage(
    val pageIndex: Int,
    val pageCount: Int,
    val pngBytes: ByteArray,
)

data class DocumentSearchMatch(val positionChars: Long, val snippet: String)

interface DocumentLibraryRepository {
    fun observeDocuments(): Flow<List<ReaderDocument>>
    fun observeBookmarks(documentId: String): Flow<List<ReaderBookmark>>
    suspend fun addDocuments(uris: List<Uri>): Int
    suspend fun removeDocument(documentId: String)
    suspend fun savePosition(documentId: String, positionChars: Long)
    suspend fun addBookmark(documentId: String, positionChars: Long, label: String)
    suspend fun removeBookmark(bookmarkId: String)
    suspend fun readPage(document: ReaderDocument, startChar: Long, maxChars: Int = 12_000): String
    suspend fun renderPdfPage(document: ReaderDocument, pageIndex: Int, targetWidthPx: Int): PdfPage
    suspend fun searchDocument(document: ReaderDocument, query: String, limit: Int = 50): List<DocumentSearchMatch>
}
