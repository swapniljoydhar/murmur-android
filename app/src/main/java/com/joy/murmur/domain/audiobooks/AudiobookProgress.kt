package com.joy.murmur.domain.audiobooks

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class AudiobookProgress(
    val mediaId: String,
    val uri: Uri,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastListenedAtMillis: Long,
) {
    val progressFraction: Float?
        get() = durationMs.takeIf { it > 0L }?.let { (positionMs.toFloat() / it).coerceIn(0f, 1f) }
}

interface AudiobookProgressRepository {
    /** Emits saved, unfinished audiobook positions, most recently listened to first. */
    fun observeInProgress(): Flow<List<AudiobookProgress>>
}
