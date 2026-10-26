package com.joy.murmur.data.speech

import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Shared cache lock for coordinating TTS cache access between UI and Service.
 * This ensures atomic cache operations across process boundaries.
 */
class SharedTtsCacheLock {
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val globalLock = Mutex()

    /**
     * Get or create a per-key mutex for fine-grained locking.
     */
    fun getKeyLock(key: String): Mutex {
        return locks.computeIfAbsent(key) { Mutex() }
    }

    /**
     * Global lock for operations that need to coordinate across multiple keys.
     */
    suspend fun withGlobalLock(block: suspend () -> Unit): Unit = globalLock.withLock { block() }

    /**
     * Acquire locks for multiple keys in a consistent order to avoid deadlocks.
     */
    suspend fun withKeyLocks(keys: List<String>, block: suspend () -> Unit) {
        val sortedKeys = keys.sorted()
        sortedKeys.forEach { key ->
            getKeyLock(key).lock()
        }
        try {
            block()
        } finally {
            sortedKeys.forEach { key ->
                getKeyLock(key).unlock()
            }
        }
    }

    /**
     * Non-suspending version for use in non-coroutine contexts.
     * Uses tryLock to avoid blocking.
     */
    fun tryWithGlobalLock(block: () -> Boolean): Boolean {
        return globalLock.tryLock().let { success ->
            if (success) {
                try {
                    return block()
                } finally {
                    globalLock.unlock()
                }
            } else {
                false
            }
        }
    }
}

/**
 * Wrapper around TtsCache that provides thread-safe operations with shared locking.
 */
class SharedTtsCache(
    private val cache: TtsCache,
    private val lock: SharedTtsCacheLock,
) {
    suspend fun get(key: String): File? = lock.getKeyLock(key).withLock {
        cache.get(key)
    }

    suspend fun put(key: String, file: File, identity: SynthesisIdentity) = lock.getKeyLock(key).withLock {
        cache.put(key, file, identity)
    }

    suspend fun pin(key: String) = lock.getKeyLock(key).withLock {
        cache.pin(key)
    }

    suspend fun releasePin(key: String) = lock.getKeyLock(key).withLock {
        cache.releasePin(key)
    }

    suspend fun evict(key: String) = lock.getKeyLock(key).withLock {
        cache.evict(key)
    }

    suspend fun shutdown() = lock.withGlobalLock {
        cache.shutdown()
    }
}