package com.joy.murmur.domain.audiobooks

enum class ChapterSource {
    CONTAINER, ID3, VORBIS, HEURISTIC, UNKNOWN
}

data class AudiobookChapter(
    val id: String,
    val audiobookId: String,
    val index: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    val source: ChapterSource = ChapterSource.CONTAINER,
) {
    val durationMs: Long
        get() = endMs?.let { it - startMs } ?: 0L
}
