package com.joy.murmur.data.speech

import android.os.StatFs
import com.joy.murmur.data.playback.PlaybackDao
import com.joy.murmur.data.playback.TtsCacheEntryEntity
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * Exact PCM metadata parsed from a RIFF/WAVE file header.
 */
data class WavHeaderInfo(
    val sampleRate: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val dataBytes: Long,
    val durationMs: Long,
)

/**
 * Parses a standard RIFF/WAVE PCM header (including files with optional metadata chunks before `data`)
 * so cached speech files never rely on hardcoded sample-rate guesses.
 */
fun readWavHeader(file: File): WavHeaderInfo? {
    if (!file.isFile || file.length() <= 44L) return null
    return runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val preamble = ByteArray(12)
            raf.readFully(preamble)
            val riff = String(preamble, 0, 4, Charsets.US_ASCII)
            val wave = String(preamble, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") return null

            var sampleRate = 0
            var channelCount = 0
            var bitsPerSample = 0
            var audioFormat = 0
            var dataBytes = -1L

            var offset = 12L
            val fileLength = raf.length()
            val chunkHeader = ByteArray(8)

            while (offset + 8L <= fileLength) {
                raf.seek(offset)
                raf.readFully(chunkHeader)
                val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .int
                    .toLong() and 0xFFFF_FFFFL
                val chunkDataStart = offset + 8L

                when (chunkId) {
                    "fmt " -> {
                        if (chunkSize < 16L || chunkDataStart + 16L > fileLength) return null
                        val fmtBytes = ByteArray(16)
                        raf.readFully(fmtBytes)
                        val buf = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                        audioFormat = buf.short.toInt() and 0xFFFF
                        channelCount = buf.short.toInt() and 0xFFFF
                        sampleRate = buf.int
                        buf.int // byteRate
                        buf.short // blockAlign
                        bitsPerSample = buf.short.toInt() and 0xFFFF
                    }
                    "data" -> {
                        val availableData = (fileLength - chunkDataStart).coerceAtLeast(0L)
                        dataBytes = minOf(chunkSize, availableData)
                        break
                    }
                }

                // RIFF chunks are word-aligned (padded to even byte boundary)
                val paddedSize = chunkSize + (chunkSize % 2L)
                offset = chunkDataStart + paddedSize
            }

            if (audioFormat != 1 ||
                sampleRate !in 8_000..192_000 ||
                channelCount !in 1..2 ||
                bitsPerSample !in setOf(8, 16, 24, 32) ||
                dataBytes <= 0L
            ) {
                return null
            }

            val bytesPerSecond = sampleRate.toLong() * channelCount.toLong() * (bitsPerSample / 8).toLong()
            if (bytesPerSecond <= 0L) return null
            val durationMs = (dataBytes * 1_000L) / bytesPerSecond
            WavHeaderInfo(
                sampleRate = sampleRate,
                channelCount = channelCount,
                bitsPerSample = bitsPerSample,
                dataBytes = dataBytes,
                durationMs = durationMs,
            )
        }
    }.getOrNull()
}

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
    private val maxAgeMillis: Long = 30L * 24 * 60 * 60 * 1000,
    private val dao: PlaybackDao? = null,
) {
    private val pinned = ConcurrentHashMap<String, PinEntry>()
    private val metadata = ConcurrentHashMap<String, CacheEntry>()
    private val totalSize = AtomicLong(0)
    private val hydrated = AtomicBoolean(false)
    private val hydrationMutex = Mutex()

    data class PinEntry(
        val acquiredAt: Long = System.currentTimeMillis(),
    )

    data class CacheEntry(
        val file: File,
        val identity: SynthesisIdentity,
        val createdAt: Long,
        val lastAccessed: Long,
        val sizeBytes: Long,
        val durationMs: Long = 0L,
    )

    data class VerifiedWaveHit(
        val file: File,
        val header: WavHeaderInfo,
    )

    /**
     * Hydrates the in-memory LRU index from Room (`tts_cache_entries`) and verifies on-disk WAV headers.
     * Stale rows whose files were evicted by the OS are pruned automatically.
     */
    suspend fun hydrateIfNeeded() {
        val databaseDao = dao ?: return
        if (hydrated.get()) return
        hydrationMutex.withLock {
            if (hydrated.get()) return
            withContext(Dispatchers.IO) {
                cacheDir.mkdirs()
                val persisted = databaseDao.getAllTtsCacheEntries()
                var computedTotal = 0L
                persisted.forEach { row ->
                    val file = File(row.filePath)
                    val wav = readWavHeader(file)
                    if (wav == null) {
                        databaseDao.deleteTtsCacheEntry(row.cacheKey)
                    } else {
                        val actualSize = file.length()
                        val entry = CacheEntry(
                            file = file,
                            identity = parseIdentityOrDefault(row.identity, wav),
                            createdAt = row.createdAtMillis,
                            lastAccessed = row.lastAccessedMillis,
                            sizeBytes = actualSize,
                            durationMs = wav.durationMs,
                        )
                        metadata[row.cacheKey] = entry
                        computedTotal += actualSize
                    }
                }
                totalSize.set(computedTotal)
                hydrated.set(true)
            }
        }
    }

    /**
     * Looks up a verified WAV file from Room/memory cache, or adopts an existing valid WAV file on disk,
     * returning exact PCM duration and format metadata from its RIFF header.
     */
    suspend fun getVerifiedWave(key: String, fallbackFile: File, fallbackIdentity: String): VerifiedWaveHit? =
        withContext(Dispatchers.IO) {
            hydrateIfNeeded()
            val now = System.currentTimeMillis()
            val existing = metadata[key]
            val candidateFile = existing?.file ?: fallbackFile
            val wav = readWavHeader(candidateFile)
            if (wav == null) {
                if (existing != null) {
                    metadata.remove(key)
                    totalSize.addAndGet(-existing.sizeBytes)
                }
                dao?.deleteTtsCacheEntry(key)
                if (candidateFile.exists()) candidateFile.delete()
                return@withContext null
            }

            val size = candidateFile.length()
            val updatedIdentity = existing?.identity ?: parseIdentityOrDefault(fallbackIdentity, wav)
            val createdAt = existing?.createdAt ?: candidateFile.lastModified().takeIf { it > 0L } ?: now
            val newEntry = CacheEntry(
                file = candidateFile,
                identity = updatedIdentity,
                createdAt = createdAt,
                lastAccessed = now,
                sizeBytes = size,
                durationMs = wav.durationMs,
            )
            val previous = metadata.put(key, newEntry)
            if (previous != null) {
                totalSize.addAndGet(size - previous.sizeBytes)
            } else {
                totalSize.addAndGet(size)
            }
            candidateFile.setLastModified(now)
            dao?.upsertTtsCacheEntry(
                TtsCacheEntryEntity(
                    cacheKey = key,
                    filePath = candidateFile.absolutePath,
                    identity = updatedIdentity.toString(),
                    sampleRate = wav.sampleRate,
                    channelCount = wav.channelCount,
                    bitsPerSample = wav.bitsPerSample,
                    durationMs = wav.durationMs,
                    sizeBytes = size,
                    createdAtMillis = createdAt,
                    lastAccessedMillis = now,
                ),
            )
            VerifiedWaveHit(candidateFile, wav)
        }

    /**
     * Records a freshly synthesized WAV file in both memory and Room (`tts_cache_entries`), then runs LRU eviction.
     */
    suspend fun putVerifiedWave(
        key: String,
        file: File,
        identityString: String,
        protectedKeys: Set<String> = emptySet(),
    ): WavHeaderInfo = withContext(Dispatchers.IO) {
        hydrateIfNeeded()
        val wav = readWavHeader(file)
            ?: throw IllegalStateException("Generated speech file has an invalid WAV header.")
        val now = System.currentTimeMillis()
        val size = file.length()
        val identity = parseIdentityOrDefault(identityString, wav)
        val entry = CacheEntry(
            file = file,
            identity = identity,
            createdAt = now,
            lastAccessed = now,
            sizeBytes = size,
            durationMs = wav.durationMs,
        )
        val oldEntry = metadata.put(key, entry)
        if (oldEntry != null) {
            totalSize.addAndGet(size - oldEntry.sizeBytes)
        } else {
            totalSize.addAndGet(size)
        }
        dao?.upsertTtsCacheEntry(
            TtsCacheEntryEntity(
                cacheKey = key,
                filePath = file.absolutePath,
                identity = identity.toString(),
                sampleRate = wav.sampleRate,
                channelCount = wav.channelCount,
                bitsPerSample = wav.bitsPerSample,
                durationMs = wav.durationMs,
                sizeBytes = size,
                createdAtMillis = now,
                lastAccessedMillis = now,
            ),
        )
        val evictedKeys = evictInternal(protectedKeys)
        evictedKeys.forEach { evictedKey -> dao?.deleteTtsCacheEntry(evictedKey) }
        wav
    }

    fun get(key: String): File? {
        val entry = metadata[key] ?: return null
        if (!entry.file.isFile) {
            metadata.remove(key)
            totalSize.addAndGet(-entry.sizeBytes)
            return null
        }
        metadata[key] = entry.copy(lastAccessed = System.currentTimeMillis())
        return entry.file
    }

    /**
     * Checks if there's sufficient free space on the cache partition for the given size.
     */
    fun hasFreeSpace(requiredBytes: Long): Boolean {
        return try {
            val statFs = StatFs(cacheDir.path)
            val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
            availableBytes >= (requiredBytes + MIN_FREE_SPACE_BYTES)
        } catch (_: Exception) {
            true
        }
    }

    fun put(key: String, file: File, identity: SynthesisIdentity) {
        val now = System.currentTimeMillis()
        val size = file.length()
        val wav = readWavHeader(file)
        val newEntry = CacheEntry(file, identity, now, now, size, wav?.durationMs ?: 0L)
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
     * Releases all in-memory pin leases when a playback service stops WITHOUT deleting cached WAV files.
     */
    fun releaseAllPins() {
        pinned.clear()
    }

    /**
     * Cleans up expired pins.
     */
    fun cleanupExpiredPins() {
        val now = System.currentTimeMillis()
        pinned.entries.filter { now - it.value.acquiredAt > PIN_LEASE_TTL_MILLIS }
            .forEach { pinned.remove(it.key) }
    }

    internal fun evictIfNeeded(protectedKeys: Set<String> = emptySet()) {
        evictInternal(protectedKeys)
    }

    private fun evictInternal(protectedKeys: Set<String>): List<String> {
        cleanupExpiredPins()
        val evicted = mutableListOf<String>()
        val now = System.currentTimeMillis()

        // First pass: evict expired entries regardless of total size
        val expiredCandidates = metadata.entries
            .filter { pinned[it.key] == null && it.key !in protectedKeys }
            .filter { now - it.value.lastAccessed > maxAgeMillis }
            .sortedBy { it.value.lastAccessed }
            .map { it.key }

        for (key in expiredCandidates) {
            metadata.remove(key)?.let { entry ->
                entry.file.delete()
                totalSize.addAndGet(-entry.sizeBytes)
                evicted += key
            }
        }

        // Second pass: LRU eviction if over maxSizeBytes
        if (totalSize.get() > maxSizeBytes) {
            val lruCandidates = metadata.entries
                .filter { pinned[it.key] == null && it.key !in protectedKeys }
                .sortedBy { it.value.lastAccessed }
                .map { it.key }

            for (key in lruCandidates) {
                if (totalSize.get() <= maxSizeBytes) break
                metadata.remove(key)?.let { entry ->
                    entry.file.delete()
                    totalSize.addAndGet(-entry.sizeBytes)
                    evicted += key
                }
            }
        }
        return evicted
    }

    fun evict(key: String) {
        metadata.remove(key)?.let { entry ->
            entry.file.delete()
            totalSize.addAndGet(-entry.sizeBytes)
            pinned.remove(key)
        }
    }

    /**
     * Explicitly wipes all cached files from memory and disk (used by tests and explicit cache clears).
     * Do NOT call this from service `onDestroy()` — use [releaseAllPins] instead.
     */
    fun shutdown() {
        metadata.values.forEach { it.file.delete() }
        metadata.clear()
        pinned.clear()
        totalSize.set(0)
    }

    private fun parseIdentityOrDefault(raw: String, wav: WavHeaderInfo): SynthesisIdentity {
        val parts = raw.split('|')
        return if (parts.size >= 9) {
            SynthesisIdentity(
                engine = parts[0],
                engineVersion = parts[1],
                voice = parts[2],
                locale = parts[3],
                pronunciationVersion = parts[4],
                filterVersion = parts[5],
                sampleRate = wav.sampleRate,
                channelCount = wav.channelCount,
                bitDepth = wav.bitsPerSample,
            )
        } else {
            SynthesisIdentity(
                engine = parts.getOrNull(0).orEmpty().ifBlank { "android_tts" },
                engineVersion = "1",
                voice = parts.getOrNull(1).orEmpty(),
                locale = parts.getOrNull(2).orEmpty(),
                pronunciationVersion = "1",
                filterVersion = "1",
                sampleRate = wav.sampleRate,
                channelCount = wav.channelCount,
                bitDepth = wav.bitsPerSample,
            )
        }
    }
}
