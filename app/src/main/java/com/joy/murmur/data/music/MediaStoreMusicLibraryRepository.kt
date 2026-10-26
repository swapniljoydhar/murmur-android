package com.joy.murmur.data.music

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.ContentUris
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.joy.murmur.domain.music.MusicLibraryRepository
import com.joy.murmur.domain.music.MusicLibrarySnapshot
import com.joy.murmur.domain.music.MusicPermissionRequiredException
import com.joy.murmur.domain.music.MusicTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import com.joy.murmur.data.playback.MusicCatalogStore
import com.joy.murmur.data.playback.MusicTrackEntity
import java.util.UUID

class MediaStoreMusicLibraryRepository(context: Context, private val catalogStore: MusicCatalogStore) : MusicLibraryRepository {
    private val appContext = context.applicationContext
    private var currentSyncToken: String? = null

    override fun observeLibrary() = combine(catalogStore.observeTracks(), catalogStore.observeState()) { tracks, state ->
        MusicLibrarySnapshot(
            hasScanned = state?.hasScanned == true,
            tracks = tracks.map(MusicTrackEntity::toDomain),
        )
    }.distinctUntilChanged()

    override fun observeMediaChanges(): Flow<Set<Long>?> = callbackFlow {
        val resolver = appContext.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val handler = Handler(Looper.getMainLooper())
        val changedIds = mutableSetOf<Long>()
        var fullRefreshRequired = false
        val flush = Runnable {
            val batch = if (fullRefreshRequired) null else changedIds.toSet()
            changedIds.clear()
            fullRefreshRequired = false
            trySend(batch)
        }
        fun collectChange(uri: Uri?) {
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id == null) fullRefreshRequired = true else changedIds.add(id)
            handler.removeCallbacks(flush)
            handler.postDelayed(flush, 600L)
        }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                collectChange(uri)
            }

            override fun onChange(selfChange: Boolean) {
                collectChange(null)
            }
        }
        resolver.registerContentObserver(collection, true, observer)
        awaitClose {
            handler.removeCallbacks(flush)
            resolver.unregisterContentObserver(observer)
        }
    }

    override suspend fun refresh(): List<MusicTrack> = withContext(Dispatchers.IO) {
        ensurePermission()
        val resolver = appContext.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.IS_MUSIC,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = resolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            cursor.toMusicTracks(collection)
        }.orEmpty()
        
        // Use stable sync token for this refresh session
        val syncToken = UUID.randomUUID().toString()
        currentSyncToken = syncToken
        catalogStore.replaceSnapshot(tracks.map { it.toEntity(syncToken) }, syncToken)
        tracks
    }

    override suspend fun refreshChangedItem(mediaStoreId: Long) = withContext(Dispatchers.IO) {
        // Check current permission state
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED) {
            throw MusicPermissionRequiredException()
        } else {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.RELATIVE_PATH,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.DATE_MODIFIED,
            )
            val selection = "${MediaStore.Audio.Media._ID} = ? AND ${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0"
            val item = appContext.contentResolver.query(collection, projection, selection, arrayOf(mediaStoreId.toString()), null)
                ?.use { cursor -> cursor.toMusicTracks(collection).firstOrNull() }
            
            // Use the current sync token so item updates don't get deleted on next full refresh
            val token = currentSyncToken ?: "item-update"
            if (item == null) {
                catalogStore.removeChangedTrack("mediastore:$mediaStoreId")
            } else {
                catalogStore.upsertChangedTrack(item.toEntity(token))
            }
        }
    }

    private fun ensurePermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(appContext, permission) != PackageManager.PERMISSION_GRANTED) {
            throw MusicPermissionRequiredException()
        }
    }
}

private fun MusicTrack.toEntity(syncToken: String) = MusicTrackEntity(
    id = id,
    uri = uri.toString(),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    relativePath = relativePath,
    mimeType = mimeType,
    dateModifiedSeconds = dateModifiedSeconds,
    syncToken = syncToken,
)

private fun MusicTrackEntity.toDomain() = MusicTrack(
    id = id,
    uri = Uri.parse(uri),
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    relativePath = relativePath,
    mimeType = mimeType,
    dateModifiedSeconds = dateModifiedSeconds,
)

private fun Cursor.toMusicTracks(collection: Uri): List<MusicTrack> {
    val idColumn = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
    val titleColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
    val displayNameColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
    val artistColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
    val albumColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
    val durationColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
    val pathColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
    val mimeColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
    val modifiedColumn = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
    return buildList {
        while (moveToNext()) {
            val rowId = getLong(idColumn)
            val displayName = getString(displayNameColumn).orEmpty()
            val metadataTitle = getString(titleColumn).usableMetadata()
            val fallbackTitle = displayName.substringBeforeLast('.', displayName).trim().ifEmpty { displayName }
            add(
                MusicTrack(
                    id = "mediastore:$rowId",
                    uri = ContentUris.withAppendedId(collection, rowId),
                    title = metadataTitle ?: fallbackTitle,
                    artist = getString(artistColumn).usableMetadata(),
                    album = getString(albumColumn).usableMetadata(),
                    durationMs = getLong(durationColumn).coerceAtLeast(0L),
                    relativePath = getString(pathColumn)?.takeIf(String::isNotBlank),
                    mimeType = getString(mimeColumn)?.takeIf(String::isNotBlank),
                    dateModifiedSeconds = getLong(modifiedColumn).coerceAtLeast(0L),
                ),
            )
        }
    }
}

private fun String?.usableMetadata(): String? = this
    ?.trim()
    ?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", ignoreCase = true) }
