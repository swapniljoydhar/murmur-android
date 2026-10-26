package com.joy.murmur.data.speech

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import com.joy.murmur.domain.documents.DocumentStructure
import com.joy.murmur.domain.documents.Section
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class SpeechSegment(
    val segmentId: String,
    val documentId: String,
    val sectionId: String,
    val paragraphId: String,
    val sentenceIds: List<String>,
    val startChar: Long,
    val endChar: Long,
    val text: String,
    val textHash: String,
    val audioFile: File?,
    val durationMs: Long,
    val synthesisIdentity: SynthesisIdentity,
)

enum class GenerationPriority { CRITICAL, HIGH, NORMAL, LOW }

data class GenerationTask(
    val taskId: String,
    val segment: SpeechSegment,
    val priority: GenerationPriority,
    val callback: (Result<File>) -> Unit,
)

class TtsScheduler(
    private val context: Context,
    private val generator: SpeechGenerator,
    private val cache: TtsCache,
    private val playbackStore: PlaybackStore,
    private val player: ExoPlayer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // Bounded channel with capacity to prevent unbounded memory growth
    private val generationQueue = Channel<GenerationTask>(100) // Max 100 queued tasks
    private val activeGenerations = ConcurrentHashMap<String, Job>()
    private val generationMutex = Mutex()
    private val maxConcurrentGenerations = 2 // Reduced to avoid TTS engine overload
    private val lookAheadTimeMs = 60_000L
    private var currentDocumentId: String? = null
    private var currentSectionId: String? = null
    private var documentStructure: DocumentStructure? = null
    private var queuedTaskCount = 0 // Track queue size manually
    private var lastLookAheadPosition: Long = 0
    private var pinCleanupJob: Job? = null

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    init {
        repeat(maxConcurrentGenerations) {
            scope.launch { generationWorker() }
        }
        // Periodic pin cleanup
        pinCleanupJob = scope.launch {
            while (true) {
                delay(60_000L) // Every minute
                cache.cleanupExpiredPins()
            }
        }
    }

    fun setDocument(documentId: String, structure: DocumentStructure) {
        currentDocumentId = documentId
        documentStructure = structure
        currentSectionId = structure.sections.firstOrNull()?.id
        lastLookAheadPosition = -1
    }

    fun seekTo(documentId: String, sectionId: String, charPosition: Long) {
        // Cancel all active generations immediately
        activeGenerations.values.forEach { it.cancel() }
        activeGenerations.clear()
        
        // Clear queued tasks that are now obsolete
        generationQueue.close()
        // Create new queue
        // Note: Channel can't be recreated easily, so we'll track documentId to filter
        currentDocumentId = documentId
        currentSectionId = sectionId
        lastLookAheadPosition = -1
        scope.launch { rebuildLookAhead(charPosition) }
    }

    fun onSegmentCompleted(segmentId: String) {
        cache.releasePin(segmentId)
        scope.launch { advanceLookAhead() }
    }

    private suspend fun generationWorker() {
        for (task in generationQueue) {
            generationMutex.withLock {
                while (activeGenerations.size >= maxConcurrentGenerations) {
                    generationMutex.unlock()
                    delay(100L)
                    generationMutex.lock()
                }
            }
            queuedTaskCount--
            val job = scope.launch {
                try {
                    // Check if task is still relevant (documentId matches)
                    if (task.segment.documentId != currentDocumentId) {
                        task.callback(Result.failure(IllegalStateException("Task obsolete")))
                        return@launch
                    }
                    val result = generator.synthesize(task.segment.text, task.segment.synthesisIdentity)
                    task.callback(Result.success(result))
                    // Check if cache has space before pinning
                    if (!cache.hasFreeSpace(1024 * 1024)) { // Need at least 1MB for metadata
                        task.callback(Result.failure(IllegalStateException("Storage full")))
                        return@launch
                    }
                    cache.pin(task.segment.segmentId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Handle storage full gracefully
                    if (e.message?.contains("storage", ignoreCase = true) == true ||
                        e.message?.contains("space", ignoreCase = true) == true) {
                        task.callback(Result.failure(IllegalStateException("Storage full: ${e.message}")))
                    } else {
                        task.callback(Result.failure(e))
                    }
                } finally {
                    activeGenerations.remove(task.taskId)
                    updateGenerationState()
                }
            }
            activeGenerations[task.taskId] = job
            updateGenerationState()
        }
    }

    private fun updateGenerationState() {
        val active = activeGenerations.size
        val queued = queuedTaskCount
        _generationState.value = if (active > 0 || queued > 0) {
            GenerationState.Generating(active, queued)
        } else {
            GenerationState.Idle
        }
    }

    private suspend fun rebuildLookAhead(fromChar: Long) {
        val structure = documentStructure ?: return
        val generatorIdentity = generator.currentSynthesisIdentity
        var accumulatedTime = 0L
        var charPos = fromChar
        lastLookAheadPosition = fromChar

        for (section in structure.sections) {
            if (charPos < section.startChar) continue
            if (charPos > section.endChar) { charPos = section.endChar; continue }
            
            val sectionText = extractSectionText(section, charPos)
            val segments = segmentText(sectionText, generatorIdentity, section, charPos)
            
            for (segment in segments) {
                val cached = cache.get(segment.segmentId)
                if (cached != null) {
                    cache.pin(segment.segmentId)
                } else {
                    val priority = when {
                        accumulatedTime == 0L -> GenerationPriority.CRITICAL
                        accumulatedTime < 10_000L -> GenerationPriority.HIGH
                        else -> GenerationPriority.NORMAL
                    }
                    // Check queue capacity before sending
                    val sendResult = generationQueue.trySend(GenerationTask(
                        taskId = UUID.randomUUID().toString(),
                        segment = segment,
                        priority = priority,
                        callback = { result ->
                            result.onSuccess { file ->
                                cache.put(segment.segmentId, file, segment.synthesisIdentity)
                            }
                        }
                    ))
                    if (sendResult.isSuccess) {
                        queuedTaskCount++
                    }
                }
                accumulatedTime += segment.durationMs
                charPos = segment.endChar
                if (accumulatedTime >= lookAheadTimeMs) break
            }
            if (accumulatedTime >= lookAheadTimeMs) break
        }
    }

    private suspend fun advanceLookAhead() {
        val structure = documentStructure ?: return
        val generatorIdentity = generator.currentSynthesisIdentity
        var charPos = lastLookAheadPosition
        
        // Find where we left off
        for (section in structure.sections) {
            if (charPos < section.startChar) continue
            if (charPos > section.endChar) { charPos = section.endChar; continue }
            
            val sectionText = extractSectionText(section, charPos)
            val segments = segmentText(sectionText, generatorIdentity, section, charPos)
            
            for (segment in segments) {
                val cached = cache.get(segment.segmentId)
                if (cached != null) {
                    cache.pin(segment.segmentId)
                } else {
                    val accumulatedTime = segment.startChar - lastLookAheadPosition
                    val priority = when {
                        accumulatedTime == 0L -> GenerationPriority.CRITICAL
                        accumulatedTime < 10_000L -> GenerationPriority.HIGH
                        else -> GenerationPriority.NORMAL
                    }
                    val sendResult = generationQueue.trySend(GenerationTask(
                        taskId = UUID.randomUUID().toString(),
                        segment = segment,
                        priority = priority,
                        callback = { result ->
                            result.onSuccess { file ->
                                cache.put(segment.segmentId, file, segment.synthesisIdentity)
                            }
                        }
                    ))
                    if (sendResult.isSuccess) {
                        queuedTaskCount++
                        lastLookAheadPosition = segment.endChar
                    } else {
                        // Queue full, stop advancing
                        return
                    }
                }
            }
        }
    }

    private fun segmentText(text: String, identity: SynthesisIdentity, section: Section, startChar: Long): List<SpeechSegment> {
        val maxChars = (generator.maxSpeechInputLength() - 128).coerceAtLeast(256)
        val segments = mutableListOf<SpeechSegment>()
        var offset = 0
        while (offset < text.length) {
            val end = minOf(offset + maxChars, text.length)
            var segmentEnd = end
            if (end < text.length) {
                val boundary = text.lastIndexOf(' ', end)
                if (boundary > offset + maxChars / 2) segmentEnd = boundary
            }
            val segmentText = text.substring(offset, segmentEnd).trim()
            if (segmentText.isNotEmpty()) {
                val segmentId = UUID.randomUUID().toString()
                val textHash = sha256("$segmentText|$identity")
                segments += SpeechSegment(
                    segmentId = segmentId,
                    documentId = currentDocumentId ?: "",
                    sectionId = section.id,
                    paragraphId = "",
                    sentenceIds = extractSentenceIds(segmentText),
                    startChar = startChar + offset,
                    endChar = startChar + segmentEnd,
                    text = segmentText,
                    textHash = textHash,
                    audioFile = null,
                    durationMs = estimateDuration(segmentText),
                    synthesisIdentity = identity,
                )
            }
            offset = segmentEnd
            while (offset < text.length && text[offset].isWhitespace()) offset++
        }
        return segments
    }

    private fun extractSentenceIds(text: String): List<String> {
        return text.split(Regex("[.!?]+"))
            .filter { it.isNotBlank() }
            .map { sentence ->
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(sentence.trim().toByteArray(kotlin.text.Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }
                "sent_${hash.take(16)}"
            }
    }

    private fun estimateDuration(text: String): Long {
        val words = text.length / 5
        return (words * 60_000L / 150).coerceAtLeast(1000L)
    }

    private fun extractSectionText(section: Section, fromChar: Long): String = ""

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(kotlin.text.Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    fun shutdown() {
        pinCleanupJob?.cancel()
        scope.cancel()
        activeGenerations.values.forEach { it.cancel() }
        activeGenerations.clear()
        generationQueue.close()
    }
}

sealed interface GenerationState {
    data class Generating(val activeCount: Int, val queuedCount: Int) : GenerationState
    object Idle : GenerationState
    data class Error(val message: String) : GenerationState
}

interface SpeechGenerator {
    val currentSynthesisIdentity: SynthesisIdentity
    suspend fun synthesize(text: String, identity: SynthesisIdentity): File
    fun maxSpeechInputLength(): Int
}
