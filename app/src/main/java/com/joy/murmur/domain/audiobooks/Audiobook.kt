package com.joy.murmur.domain.audiobooks

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class Audiobook(
    val id: String,
    val uri: Uri,
    val title: String,
    val author: String?,
    val durationMs: Long,
    val coverArt: ByteArray?,
    val addedAtMillis: Long,
    val positionMs: Long,
    val lastListenedAtMillis: Long?,
) {
    val progressFraction: Float?
        get() = durationMs.takeIf { it > 0L }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }

    val isInProgress: Boolean
        get() = positionMs > 0L && (durationMs <= 0L || positionMs < durationMs)
}

data class AudiobookBookmark(
    val id: String,
    val audiobookId: String,
    val positionMs: Long,
    val label: String,
    val createdAtMillis: Long,
)

interface AudiobookLibraryRepository {
    fun observeBooks(): Flow<List<Audiobook>>
    fun observeBookmarks(audiobookId: String): Flow<List<AudiobookBookmark>>
    fun observeChapters(audiobookId: String): Flow<List<AudiobookChapter>>
    suspend fun addBooks(uris: List<Uri>): Int
    suspend fun removeBook(bookId: String)
    suspend fun addBookmark(audiobookId: String, positionMs: Long): AudiobookBookmark
    suspend fun removeBookmark(bookmarkId: String)
    suspend fun refreshChapters(audiobookId: String)
}

