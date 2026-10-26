package com.joy.murmur.data.audiobooks

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import com.joy.murmur.data.playback.AudiobookEntity
import com.joy.murmur.data.playback.AudiobookBookmarkEntity
import com.joy.murmur.data.playback.AudiobookChapterEntity
import com.joy.murmur.data.playback.AudiobookStore
import com.joy.murmur.data.playback.AudiobookWithProgress
import com.joy.murmur.data.playback.ListeningProgressEntity
import com.joy.murmur.domain.audiobooks.Audiobook
import com.joy.murmur.domain.audiobooks.AudiobookBookmark
import com.joy.murmur.domain.audiobooks.AudiobookChapter
import com.joy.murmur.domain.audiobooks.AudiobookLibraryRepository
import com.joy.murmur.domain.audiobooks.ChapterSource
import com.joy.murmur.playback.toStableMediaId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomAudiobookLibraryRepository(context: Context, private val store: AudiobookStore) : AudiobookLibraryRepository {
    private val appContext = context.applicationContext
    private val metadataExtractor = AudiobookMetadataExtractor(appContext)
    private val chapterParser = AudiobookChapterParser(appContext)

    override fun observeBooks(): Flow<List<Audiobook>> = store.observeBooks()
        .map { books -> books.map { it.toAudiobookDomain() } }
        .flowOn(Dispatchers.Default)

    override fun observeBookmarks(audiobookId: String): Flow<List<AudiobookBookmark>> = store.observeBookmarks(audiobookId)
        .map { bookmarks -> bookmarks.map(AudiobookBookmarkEntity::toDomain) }

    override fun observeChapters(audiobookId: String): Flow<List<AudiobookChapter>> = store.observeChapters(audiobookId)
        .map { chapters -> chapters.mapIndexed { index, chapter -> chapter.toDomain(audiobookId, index) } }

    override suspend fun addBooks(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val uniqueUris = uris.distinctBy(Uri::toString)
        val addedAt = System.currentTimeMillis()
        val books = uniqueUris.map { uri -> withContext(Dispatchers.IO) { uri.toAudiobookEntity(addedAt, metadataExtractor) } }
        store.saveBooks(books)
        books.size
    }

    override suspend fun removeBook(bookId: String) {
        store.removeBook(bookId)
    }

    override suspend fun addBookmark(audiobookId: String, positionMs: Long): AudiobookBookmark {
        val position = positionMs.coerceAtLeast(0L)
        val bookmark = AudiobookBookmark(
            id = UUID.randomUUID().toString(),
            audiobookId = audiobookId,
            positionMs = position,
            label = formatBookmarkPosition(position),
            createdAtMillis = System.currentTimeMillis(),
        )
        store.saveBookmark(bookmark.toEntity())
        return bookmark
    }

    override suspend fun removeBookmark(bookmarkId: String) = store.removeBookmark(bookmarkId)

    @UnstableApi
    override suspend fun refreshChapters(audiobookId: String) = withContext(Dispatchers.IO) {
        val book = store.getBook(audiobookId) ?: throw IllegalArgumentException("This audiobook is no longer in your library.")
        val chapters = chapterParser.parseChapters(Uri.parse(book.uri), book.durationMs)
        store.replaceChapters(
            audiobookId,
            chapters.mapIndexed { index, chapter ->
                AudiobookChapterEntity(
                    audiobookId = audiobookId,
                    chapterIndex = index,
                    title = chapter.title,
                    startMs = chapter.startMs,
                    endMs = chapter.endMs,
                    source = chapter.source.name,
                )
            },
        )
    }

    private fun formatBookmarkPosition(positionMs: Long): String {
        val totalSeconds = positionMs / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
    }

    private fun AudiobookWithProgress.toAudiobookDomain(): Audiobook {
        val listened = progress
        return Audiobook(
            id = audiobook.id,
            uri = Uri.parse(audiobook.uri),
            title = audiobook.title,
            author = audiobook.author,
            durationMs = audiobook.durationMs.takeIf { it > 0L } ?: listened?.durationMs ?: 0L,
            coverArt = audiobook.coverArt,
            addedAtMillis = audiobook.addedAtMillis,
            positionMs = listened?.positionMs ?: 0L,
            lastListenedAtMillis = listened?.updatedAtMillis,
        )
    }
}

private fun AudiobookBookmark.toEntity() = AudiobookBookmarkEntity(id, audiobookId, positionMs, label, createdAtMillis)

private fun AudiobookBookmarkEntity.toDomain() = AudiobookBookmark(id, audiobookId, positionMs, label, createdAtMillis)

private fun AudiobookChapterEntity.toDomain(audiobookId: String, index: Int) = AudiobookChapter(
    id = "$audiobookId:$chapterIndex",
    audiobookId = audiobookId,
    index = index,
    title = title,
    startMs = startMs,
    endMs = endMs,
    source = ChapterSource.entries.firstOrNull { it.name == source } ?: ChapterSource.UNKNOWN,
)

private suspend fun Uri.toAudiobookEntity(addedAtMillis: Long, extractor: AudiobookMetadataExtractor): AudiobookEntity {
    val metadata = extractor.extractMetadata(this)
    val fallback = metadata.title ?: "Audiobook file"
    return AudiobookEntity(
        id = toStableMediaId(),
        uri = toString(),
        title = metadata.title ?: fallback,
        author = metadata.author,
        durationMs = metadata.durationMs,
        coverArt = metadata.coverArt,
        addedAtMillis = addedAtMillis,
    )
}


