package com.joy.murmur.playback

import com.joy.murmur.data.playback.PlaybackDao
import com.joy.murmur.data.speech.GeneratedSpeechFile

/**
 * Persists the sentence map for freshly synthesized Read Aloud segments.
 *
 * Both producers write here: the reader when the user starts Read Aloud on the visible page, and the
 * service when it synthesizes the continuation page. Previously only the service wrote, so the page
 * the user actually pressed "Read from here" on had no map — which is why highlighting and
 * tap-to-read did nothing until the reader moved to the next page.
 */
internal suspend fun persistReadAloudSegmentMap(
    dao: PlaybackDao,
    documentId: String,
    pageStart: Long,
    pageLength: Int,
    generated: List<GeneratedSpeechFile>,
) {
    val entities = ReadAloudSegmentMapping.buildEntities(documentId, pageStart, pageLength, generated)
    if (entities.isNotEmpty()) dao.insertSegmentMaps(entities)
}
