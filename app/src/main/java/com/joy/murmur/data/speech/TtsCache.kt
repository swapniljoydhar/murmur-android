package com.joy.murmur.data.speech

import android.os.StatFs
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.mutableListOf

public data class SynthesisIdentity(
    val engine: String,
    val engineVersion: String,
    val voice: String,
    val locale: String,
    val pronunciationVersion: String,
    val filterVersion: String,
    val sampleRate: Int,
    val channelCount: Int,
    val bitDepth: Int,
) {
    override fun toString(): String = "$engine|$engineVersion|$voice|$locale|$pronunciationVersion|$filterVersion|$sampleRate|$channelCount|$bitDepth"
}

public data class SpeechSegmentResult(
    val file: File,
    val segmentIndex: Int,
    val sourceOffset: Int,
    val sentenceIds: List<String>,
)

/**
 * Time-to-live for a pinned segment lease. After this duration, the pin is automatically released
 * to prevent indefinite pinning if a consumer crashes or forgets to release.
 */
const val PIN_LEASE_TTL_MILLIS = 5 * 60 * 1000L // 5 minutes

/**
 * Minimum free space required on cache partition before allowing new synthesis.
 */
const val MIN_FREE_SPACE_BYTES = 50 * 1024 * 1024L // 50 MB

class TtsCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = 256 * 1024 * 1024,
    private val maxAgeMillis: Long = 30 * 24 * 60 * 60 * 1000,
) {
    private val pinned = ConcurrentHashMap<String, PinEntry>() // Pin with expiry
    private val metadata = ConcurrentHashMap<String, CacheEntry>()
    private val totalSize = AtomicLong(0)

    data class PinEntry(
        val acquiredAt: Long = System.currentTimeMillis(),
    )

    data class CacheEntry(
        val file: File,
        val identity: SynthesisIdentity,
        val createdAt: Long,
        val lastAccessed: Long,
        val sizeBytes: Long,
    )

    fun get(key: String): File? {
        val entry = metadata[key]
        if (entry != null) {
            metadata[key] = entry.copy(lastAccessed = System.currentTimeMillis())
            return entry.file
        }
        return null
    }

    /**
     * Checks if there's sufficient free space on the cache partition for the given size.
     */
    fun hasFreeSpace(requiredBytes: Long): Boolean {
        try {
            val statFs = StatFs(cacheDir.path)
            val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            return availableBytes >= (requiredBytes + MIN_FREE_SPACE_BYTES)
        } catch (_: Exception) {
            // If we can't determine free space, allow the operation
            return true
        }
    }

    fun put(key: String, file: File, identity: SynthesisIdentity) {
        val now = System.currentTimeMillis()
        val size = file.length()
        val newEntry = CacheEntry(file, identity, now, now, size)
        val oldEntry = metadata.put(key, newEntry)
        if (oldEntry != null) {
            totalSize.addAndGet(size - oldEntry.sizeBytes)
        } else {
            totalSize.addAndGet(size)
        }
        evictIfNeeded()
    }

    /**
     * Pins a segment with a lease. The pin will auto-expire after [PIN_LEASE_TTL_MILLIS]
     * to prevent indefinite pinning if the consumer forgets to release.
     */
    fun pin(key: String) {
        pinned[key] = PinEntry()
    }

    fun releasePin(key: String) {
        pinned.remove(key)
    }

    /**
     * Cleans up expired pins. Call periodically from the scheduler.
     */
    fun cleanupExpiredPins() {
        val now = System.currentTimeMillis()
        pinned.entries.filter { now - it.value.acquiredAt > PIN_LEASE_TTL_MILLIS }
            .forEach { pinned.remove(it.key) }
    }

    internal fun evictIfNeeded(protectedKeys: Set<String> = emptySet()) {
        val now = System.currentTimeMillis()
        var currentTotal = totalSize.get()
        
        // First pass: evict expired entries
        val expiredCandidates = metadata.entries
            .filter { pinned[it.key] == null && it.key !in protectedKeys }
            .filter { now - it.value.lastAccessed > maxAgeMillis }
            .sortedBy { it.value.lastAccessed }
            .map { it.key }
            .toList()

        for (key in expiredCandidates) {
            currentTotal = totalSize.get()
            if (currentTotal <= maxSizeBytes) break
            metadata.remove(key)?.let { entry ->
                entry.file.delete()
                totalSize.addAndGet(-entry.sizeBytes)
            }
        }

        // Second pass: LRU eviction if still over limit
        currentTotal = totalSize.get()
        if (currentTotal > maxSizeBytes) {
            val lruCandidates = metadata.entries
                .filter { pinned[it.key] == null && it.key !in protectedKeys }
                .sortedBy { it.value.lastAccessed }
                .map { it.key }
                .toList()

            for (key in lruCandidates) {
                currentTotal = totalSize.get()
                if (currentTotal <= maxSizeBytes) break
                metadata.remove(key)?.let { entry ->
                    entry.file.delete()
                    totalSize.addAndGet(-entry.sizeBytes)
                }
            }
        }
    }

    fun evict(key: String) {
        metadata.remove(key)?.let { entry ->
            entry.file.delete()
            totalSize.addAndGet(-entry.sizeBytes)
            pinned.remove(key)
        }
    }

    fun shutdown() {
        metadata.values.forEach { it.file.delete() }
        metadata.clear()
        pinned.clear()
        totalSize.set(0)
    }
}
