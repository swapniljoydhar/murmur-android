package com.joy.murmur.playback

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class ReadAloudPositionWriteTracker {
    private val sequence = AtomicLong()
    private val latestByDocument = ConcurrentHashMap<String, Long>()

    fun begin(documentId: String): Long = sequence.incrementAndGet().also { token ->
        latestByDocument.compute(documentId) { _, latest -> maxOf(token, latest ?: token) }
    }

    fun isCurrent(documentId: String, token: Long): Boolean = latestByDocument[documentId] == token

    fun finish(documentId: String, token: Long) {
        latestByDocument.remove(documentId, token)
    }
}
