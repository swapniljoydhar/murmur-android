package com.joy.murmur.playback

import android.net.Uri
import java.nio.charset.StandardCharsets
import java.util.UUID

enum class PlaybackContentType(val storageValue: String, val displayName: String) {
    MUSIC("music", "Music"),
    AUDIOBOOK("audiobook", "Audiobook"),
    READ_ALOUD("read_aloud", "Read Aloud"),
}

enum class QueueInsertMode { REPLACE_AND_PLAY, NEXT, END }

data class SelectedAudio(
    val id: String,
    val uri: Uri,
    val title: String,
    val contentType: PlaybackContentType,
    val subtitle: String? = null,
)

fun Uri.toStableMediaId(): String = "document:${UUID.nameUUIDFromBytes(toString().toByteArray(StandardCharsets.UTF_8))}"

data class PlaybackSnapshot(
    val items: List<SelectedAudio> = emptyList(),
    val currentIndex: Int = -1,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
) {
    val current: SelectedAudio?
        get() = items.getOrNull(currentIndex)
}
