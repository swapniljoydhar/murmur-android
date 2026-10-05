package com.joy.murmur.data.audiobooks

import android.net.Uri
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.data.playback.ListeningProgressEntity
import com.joy.murmur.domain.audiobooks.AudiobookProgress
import com.joy.murmur.domain.audiobooks.AudiobookProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class RoomAudiobookProgressRepository(private val playbackStore: PlaybackStore) : AudiobookProgressRepository {
    override fun observeInProgress(): Flow<List<AudiobookProgress>> = playbackStore.observeAudiobookProgress()
        .map { entries -> entries.map(ListeningProgressEntity::toAudiobookProgress) }
        .flowOn(Dispatchers.Default)
}

private fun ListeningProgressEntity.toAudiobookProgress() = AudiobookProgress(
    mediaId = mediaId,
    uri = Uri.parse(uri),
    title = title,
    positionMs = positionMs,
    durationMs = durationMs,
    lastListenedAtMillis = updatedAtMillis,
)
