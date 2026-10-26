package com.joy.murmur.data.audiobooks

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudiobookMetadataExtractor(private val context: Context) {

    data class AudiobookMetadata(
        val title: String?,
        val author: String?,
        val durationMs: Long,
        val coverArt: ByteArray?,
        val mimeType: String?,
        val bitrate: Int?,
    )

    suspend fun extractMetadata(uri: Uri): AudiobookMetadata = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        val displayName = tryGetDisplayName(uri)
        val fallbackTitle = displayName?.substringBeforeLast('.', displayName)?.trim()?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
            ?: "Audiobook file"

        val metadata = try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.usableMetadata()
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.usableMetadata()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            val bitrate = bitrateStr?.toIntOrNull()
            val coverArt = retriever.embeddedPicture?.toThumbnailJpeg()

            AudiobookMetadata(
                title = title,
                author = author,
                durationMs = durationMs,
                coverArt = coverArt,
                mimeType = mimeType,
                bitrate = bitrate,
            )
        } catch (_: Exception) {
            AudiobookMetadata(
                title = null,
                author = null,
                durationMs = 0L,
                coverArt = null,
                mimeType = null,
                bitrate = null,
            )
        } finally {
            retriever.release()
        }

        AudiobookMetadata(
            title = metadata.title ?: fallbackTitle,
            author = metadata.author,
            durationMs = metadata.durationMs,
            coverArt = metadata.coverArt,
            mimeType = metadata.mimeType,
            bitrate = metadata.bitrate,
        )
    }

    private fun tryGetDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) return@use cursor.getString(index)?.takeIf(String::isNotBlank)
                }
                null
            }
        } catch (_: Exception) {
            null
        }
    }

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
}
