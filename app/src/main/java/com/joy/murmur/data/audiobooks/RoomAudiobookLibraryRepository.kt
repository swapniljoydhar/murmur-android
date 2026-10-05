package com.joy.murmur.data.audiobooks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.inspector.MetadataRetriever
import java.io.ByteArrayOutputStream
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
import com.joy.murmur.playback.toStableMediaId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

class RoomAudiobookLibraryRepository(context: Context, private val store: AudiobookStore) : AudiobookLibraryRepository {
    private val appContext = context.applicationContext

    override fun observeBooks(): Flow<List<Audiobook>> = store.observeBooks()
        .map { books -> books.map(AudiobookWithProgress::toDomain) }
        .flowOn(Dispatchers.Default)

    override fun observeBookmarks(audiobookId: String): Flow<List<AudiobookBookmark>> = store.observeBookmarks(audiobookId)
        .map { bookmarks -> bookmarks.map(AudiobookBookmarkEntity::toDomain) }

    override fun observeChapters(audiobookId: String): Flow<List<AudiobookChapter>> = store.observeChapters(audiobookId)
        .map { chapters -> chapters.mapIndexed { index, chapter -> chapter.toDomain(audiobookId, index) } }

    override suspend fun addBooks(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val uniqueUris = uris.distinctBy(Uri::toString)
        val addedAt = System.currentTimeMillis()
        val books = uniqueUris.map { uri -> uri.toAudiobookEntity(addedAt) }
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
        val mediaItem = MediaItem.Builder().setMediaId(book.id).setUri(book.uri).build()
        val chapters = MetadataRetriever.Builder(appContext, mediaItem).build().use { retriever ->
            val trackGroups = retriever.retrieveTrackGroups().get(90, java.util.concurrent.TimeUnit.SECONDS)
            buildList {
                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups.get(groupIndex)
                    for (formatIndex in 0 until group.length) {
                        val metadata = group.getFormat(formatIndex).metadata ?: continue
                        for (metadataIndex in 0 until metadata.length()) {
                            val chapter = metadata[metadataIndex] as? ChapterFrame ?: continue
                            if (chapter.isHidden) continue
                            val startMs = chapter.startTimeMs.toLong()
                            if (startMs < 0L || (book.durationMs > 0L && startMs >= book.durationMs)) continue
                            val endMs = chapter.endTimeMs.toLong().takeIf { it > startMs }
                            val title = chapter.title?.toString()?.trim()?.takeIf(String::isNotBlank)
                                ?: "Chapter ${size + 1}"
                            add(ChapterCandidate(title, startMs, endMs))
                        }
                    }
                }
            }.distinctBy(ChapterCandidate::startMs).sortedBy(ChapterCandidate::startMs)
        }
        store.replaceChapters(
            audiobookId,
            chapters.mapIndexed { index, chapter ->
                AudiobookChapterEntity(audiobookId, index, chapter.title, chapter.startMs, chapter.endMs)
            },
        )
    }

    private fun Uri.toAudiobookEntity(addedAtMillis: Long): AudiobookEntity {
        val retriever = MediaMetadataRetriever()
        val metadata = try {
            retriever.setDataSource(appContext, this)
            AudiobookMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).usableMetadata(),
                author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).usableMetadata(),
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
                coverArt = retriever.embeddedPicture?.toThumbnailJpeg(),
            )
        } catch (_: Exception) {
            AudiobookMetadata(title = null, author = null, durationMs = 0L, coverArt = null)
        } finally {
            retriever.release()
        }
        val displayName = appContext.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index)?.takeIf(String::isNotBlank) else null
            } else null
        }
        val fallback = displayName?.substringBeforeLast('.', displayName)?.trim()?.takeIf(String::isNotBlank)
            ?: lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "Audiobook file"
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
}

private fun AudiobookBookmark.toEntity() = AudiobookBookmarkEntity(id, audiobookId, positionMs, label, createdAtMillis)

private fun AudiobookBookmarkEntity.toDomain() = AudiobookBookmark(id, audiobookId, positionMs, label, createdAtMillis)

private data class ChapterCandidate(val title: String, val startMs: Long, val endMs: Long?)

private fun AudiobookChapterEntity.toDomain(audiobookId: String, index: Int) = AudiobookChapter(
    id = "$audiobookId:$chapterIndex",
    audiobookId = audiobookId,
    index = index,
    title = title,
    startMs = startMs,
    endMs = endMs,
)

private fun formatBookmarkPosition(positionMs: Long): String {
    val totalSeconds = positionMs / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private data class AudiobookMetadata(val title: String?, val author: String?, val durationMs: Long, val coverArt: ByteArray?)

private fun ByteArray.toThumbnailJpeg(): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longestEdge = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = Integer.highestOneBit((longestEdge / 320).coerceAtLeast(1))
    val bitmap = BitmapFactory.decodeByteArray(
        this,
        0,
        size,
        BitmapFactory.Options().apply { inSampleSize = sampleSize },
    ) ?: return null
    return try {
        ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 82, output)
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}

private fun String?.usableMetadata(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }

private fun AudiobookWithProgress.toDomain(): Audiobook {
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
