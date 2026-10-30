package com.joy.murmur.data.speech

import java.io.File

/**
 * Eviction policy for the on-disk Read Aloud speech cache.
 *
 * The cache lives in `cacheDir/read-aloud`, so it survives process death but is also unbounded unless
 * something trims it. Two rules apply, in this order, oldest file first:
 *
 *  - a file older than [MAX_AGE_MILLIS] is dropped, and
 *  - once the cache is larger than [MAX_BYTES], the oldest files are dropped until it fits.
 *
 * Files whose key is in `protectedKeys` are never evicted: those are the segments still referenced by
 * the persisted playback queue, which would otherwise disappear mid-book. Leftover temporary files
 * (`.part` writes and `speech-`/`engine-` scratch files from an interrupted synthesis) older than
 * [TEMP_MAX_AGE_MILLIS] are removed unconditionally.
 *
 * This is split out of the generator so the policy can be unit-tested without a speech engine.
 */
internal object SpeechCacheEviction {
    const val MAX_BYTES = 256L * 1024L * 1024L
    const val MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
    const val TEMP_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L

    data class Result(val deletedFiles: Int, val deletedBytes: Long)

    fun evict(
        directory: File,
        protectedKeys: Set<String>,
        nowMillis: Long = System.currentTimeMillis(),
        maxBytes: Long = MAX_BYTES,
        maxAgeMillis: Long = MAX_AGE_MILLIS,
        tempMaxAgeMillis: Long = TEMP_MAX_AGE_MILLIS,
    ): Result {
        if (!directory.isDirectory) return Result(0, 0)
        var deletedFiles = 0
        var deletedBytes = 0L

        val waveFiles = directory.listFiles { file -> file.isFile && file.extension == "wav" }.orEmpty()
        var totalBytes = waveFiles.sumOf(File::length)
        waveFiles.sortedBy(File::lastModified).forEach { file ->
            val protectedFile = file.nameWithoutExtension in protectedKeys
            val expired = nowMillis - file.lastModified() > maxAgeMillis
            if (!protectedFile && (expired || totalBytes > maxBytes)) {
                val size = file.length()
                if (file.delete()) {
                    totalBytes -= size
                    deletedBytes += size
                    deletedFiles++
                }
            }
        }

        directory.listFiles { file -> file.isFile && isTemporary(file) && nowMillis - file.lastModified() > tempMaxAgeMillis }
            .orEmpty()
            .forEach { file ->
                val size = file.length()
                if (file.delete()) {
                    deletedBytes += size
                    deletedFiles++
                }
            }

        return Result(deletedFiles, deletedBytes)
    }

    /** Interrupted-write and engine scratch files, which are never referenced by the queue. */
    fun isTemporary(file: File): Boolean =
        file.name.endsWith(".part") || file.name.startsWith("speech-") || file.name.startsWith("engine-")
}
