package com.joy.murmur.domain.music

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/** A locally indexed audio item. Optional fields stay absent when the device has no metadata. */
data class MusicTrack(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val relativePath: String?,
    val mimeType: String?,
    val dateModifiedSeconds: Long,
)

class MusicPermissionRequiredException : SecurityException("Audio library access has not been granted")

interface MusicLibraryRepository {
    /** Emits the last successful local catalog snapshot, including an empty completed scan. */
    fun observeLibrary(): Flow<MusicLibrarySnapshot>

    /** Emits local media-provider changes while a screen is observing with audio access granted. */
    fun observeMediaChanges(): Flow<Set<Long>?>

    /** Refreshes the local snapshot from MediaStore. The caller must request permission contextually. */
    suspend fun refresh(): List<MusicTrack>

    /** Refreshes or removes one changed MediaStore row in the local catalog. */
    suspend fun refreshChangedItem(mediaStoreId: Long)

    /** Imports user-selected SAF audio files or folder contents into the local music catalog without requiring broad MediaStore permission. */
    suspend fun importAudioUris(uris: List<Uri>, folderLabel: String? = null): Int = 0
}

data class MusicLibrarySnapshot(
    val hasScanned: Boolean,
    val tracks: List<MusicTrack>,
)
