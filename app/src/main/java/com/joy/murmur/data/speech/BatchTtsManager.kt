package com.joy.murmur.data.speech

import android.content.Context
import android.net.Uri
import com.joy.murmur.data.playback.BatchJobEntity
import com.joy.murmur.data.playback.BatchJobStatus
import com.joy.murmur.data.playback.BatchWorkUnitEntity
import com.joy.murmur.data.playback.BatchWorkUnitStatus
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.ReaderDocumentEntity
import com.joy.murmur.data.playback.ReaderDocumentStore
import com.joy.murmur.data.documents.RoomDocumentLibraryRepository
import com.joy.murmur.domain.documents.DocumentStructure
import com.joy.murmur.domain.documents.ReaderDocument
import com.joy.murmur.domain.documents.Section
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class BatchTtsJob(
    val id: String,
    val documentId: String,
    val status: BatchJobStatus,
    val totalChapters: Int,
    val completedChapters: Int,
    val currentChapter: Int?,
    val outputUri: Uri?,
    val voiceId: String,
    val voiceSpeed: Float,
    val mergeOutput: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
)

sealed interface BatchGenerationState {
    data class Generating(val jobId: String, val activeCount: Int, val queuedCount: Int, val completedCount: Int) : BatchGenerationState
    object Idle : BatchGenerationState
    data class Error(val message: String) : BatchGenerationState
}

class BatchTtsManager(
    private val context: Context,
    private val generator: SpeechGenerator,
    private val cache: TtsCache,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generationQueue = Channel<BatchWorkUnitEntity>(1000)
    private val activeGenerations = ConcurrentHashMap<String, Job>()
    private val generationMutex = Mutex()
    private val maxConcurrentGenerations = 2
    private val maxDailyProcessingMs = 6 * 60 * 60 * 1000L // 6 hours per Android 15+
    
    private val _generationState = MutableStateFlow<BatchGenerationState>(BatchGenerationState.Idle)
    val generationState: kotlinx.coroutines.flow.StateFlow<BatchGenerationState> = _generationState.asStateFlow()
    
    private var currentJobId: String? = null
    private var dailyProcessingMs = 0L
    private var lastProcessingDate = 0L

    init {
        (1..maxConcurrentGenerations).forEach { scope.launch { generationWorker() } }
    }

    suspend fun createBatchJob(
        documentId: String,
        structure: DocumentStructure,
        voiceId: String,
        voiceSpeed: Float,
        mergeOutput: Boolean,
        outputDir: File,
    ): BatchTtsJob {
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val workUnits = createWorkUnits(documentId, structure, jobId)
        val totalChapters = structure.sections.size
        
        val job = BatchJobEntity(
            id = jobId,
            documentId = documentId,
            status = BatchJobStatus.PENDING.value,
            totalChapters = totalChapters,
            completedChapters = 0,
            currentChapter = null,
            outputUri = null,
            voiceId = voiceId,
            voiceSpeed = voiceSpeed,
            mergeOutput = mergeOutput,
            createdAtMillis = now,
            updatedAtMillis = now,
            error = null,
        )
        
        withContext(Dispatchers.IO) {
            val db = PlaybackDatabase.getInstance(context)
            db.playbackDao().insertBatchJob(job)
            db.playbackDao().insertBatchWorkUnits(workUnits)
        }
        
        currentJobId = jobId
        return job.toDomain()
    }

private suspend fun createWorkUnits(documentId: String, structure: DocumentStructure, jobId: String): List<BatchWorkUnitEntity> {
        val units = mutableListOf<BatchWorkUnitEntity>()

        // Load actual document text for each section
        val docStore = ReaderDocumentStore(PlaybackDatabase.getInstance(context))
        val document = docStore.getDocument(documentId)
            ?: throw IllegalArgumentException("Document not found: $documentId")

        val repository = RoomDocumentLibraryRepository(context, docStore)

        for (section in structure.sections) {
            val sectionText = repository.readPage(
                ReaderDocument(document.id, Uri.parse(document.uri), document.title, document.mimeType, document.addedAtMillis, 0),
                section.startChar,
                (section.endChar - section.startChar).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            )

            val maxChars = (generator.maxSpeechInputLength() - 128).coerceAtLeast(256)
            var offset = 0

            while (offset < sectionText.length) {
                val end = minOf(offset + maxChars, sectionText.length)
                var segmentEnd = end
                if (end < sectionText.length) {
                    val boundary = sectionText.lastIndexOf(' ', end)
                    if (boundary > offset + maxChars / 2) segmentEnd = boundary
                }
                val segmentText = sectionText.substring(offset, segmentEnd).trim()
                if (segmentText.isNotEmpty()) {
                    val unitId = UUID.randomUUID().toString()
                    units.add(BatchWorkUnitEntity(
                        id = unitId,
                        jobId = jobId,
                        chapterIndex = structure.sections.indexOf(section),
                        sectionId = section.id,
                        startChar = section.startChar + offset,
                        endChar = section.startChar + segmentEnd,
                        text = segmentText,
                        status = BatchWorkUnitStatus.PENDING.value,
                        outputFile = null,
                        retryCount = 0,
                        createdAtMillis = System.currentTimeMillis(),
                        updatedAtMillis = System.currentTimeMillis(),
                    ))
                }
                offset = segmentEnd
                while (offset < sectionText.length && sectionText[offset].isWhitespace()) offset++
            }
        }
        return units
    }

    suspend fun startJob(jobId: String) {
        currentJobId = jobId
        val units = withContext(Dispatchers.IO) {
            PlaybackDatabase.getInstance(context).playbackDao().getBatchWorkUnits(jobId)
        }
        
        val pendingUnits = units.filter { it.status == BatchWorkUnitStatus.PENDING.value }
        pendingUnits.forEach { generationQueue.send(it) }
        
        updateJobStatus(jobId, BatchJobStatus.RUNNING, null, null, null)
    }

    suspend fun pauseJob(jobId: String) {
        generationQueue.close()
        activeGenerations.values.forEach { it.cancel() }
        activeGenerations.clear()
        updateJobStatus(jobId, BatchJobStatus.PAUSED, null, null, null)
    }

    suspend fun resumeJob(jobId: String) {
        val units = withContext(Dispatchers.IO) {
            PlaybackDatabase.getInstance(context).playbackDao().getBatchWorkUnitsByStatus(jobId, BatchWorkUnitStatus.PENDING.value)
        }
        units.forEach { generationQueue.send(it) }
        updateJobStatus(jobId, BatchJobStatus.RUNNING, null, null, null)
    }

    suspend fun cancelJob(jobId: String) {
        generationQueue.close()
        activeGenerations.values.forEach { it.cancel() }
        activeGenerations.clear()
        updateJobStatus(jobId, BatchJobStatus.CANCELLED, null, null, null)
    }

    private suspend fun generationWorker() {
        for (unit in generationQueue) {
            generationMutex.withLock {
                if (activeGenerations.size >= maxConcurrentGenerations) {
                    // Re-queue and wait
                    scope.launch { delay(100L); generationQueue.send(unit) }
                    return@withLock
                }
            }
            
            val job = scope.launch {
                try {
                    updateWorkUnitStatus(unit.id, BatchWorkUnitStatus.GENERATING, null, unit.retryCount)
                    
                    val result = generator.synthesize(unit.text, generator.currentSynthesisIdentity)
                    
                    updateWorkUnitStatus(unit.id, BatchWorkUnitStatus.COMPLETED, result.absolutePath, unit.retryCount)
                    cache.put(unit.id, result, generator.currentSynthesisIdentity)
                    
                    checkJobCompletion(unit.jobId)
                } catch (e: Exception) {
                    val newRetryCount = unit.retryCount + 1
                    if (newRetryCount < 3) {
                        updateWorkUnitStatus(unit.id, BatchWorkUnitStatus.PENDING, null, newRetryCount)
                        generationQueue.send(unit.copy(retryCount = newRetryCount))
                    } else {
                        updateWorkUnitStatus(unit.id, BatchWorkUnitStatus.FAILED, null, newRetryCount)
                        updateJobStatus(unit.jobId, BatchJobStatus.FAILED, null, null, e.message)
                    }
                } finally {
                    activeGenerations.remove(unit.id)
                }
            }
            activeGenerations[unit.id] = job
        }
    }

    private suspend fun updateWorkUnitStatus(unitId: String, status: BatchWorkUnitStatus, outputFile: String?, retryCount: Int) {
        withContext(Dispatchers.IO) {
            PlaybackDatabase.getInstance(context).playbackDao().updateBatchWorkUnit(
                unitId, status.value, outputFile, retryCount, System.currentTimeMillis()
            )
        }
    }

    private suspend fun updateJobStatus(
        jobId: String,
        status: BatchJobStatus,
        completedChapters: Int?,
        currentChapter: Int?,
        error: String?,
    ) {
        withContext(Dispatchers.IO) {
            val db = PlaybackDatabase.getInstance(context)
            val job = db.playbackDao().getBatchJob(jobId) ?: return@withContext
            db.playbackDao().updateBatchJob(
                jobId,
                status.value,
                completedChapters ?: job.completedChapters,
                currentChapter,
                job.outputUri,
                System.currentTimeMillis(),
                error,
            )
        }
    }

    private suspend fun checkJobCompletion(jobId: String) {
        val db = PlaybackDatabase.getInstance(context)
        val allUnits = withContext(Dispatchers.IO) { db.playbackDao().getBatchWorkUnits(jobId) }
        val completed = allUnits.count { it.status == BatchWorkUnitStatus.COMPLETED.value }
        val failed = allUnits.count { it.status == BatchWorkUnitStatus.FAILED.value }
        val total = allUnits.size
        
        if (completed + failed == total) {
            if (failed > 0) {
                updateJobStatus(jobId, BatchJobStatus.FAILED, completed, null, "Some work units failed")
            } else {
                // Merge outputs if needed
                val job = withContext(Dispatchers.IO) { db.playbackDao().getBatchJob(jobId) }
                job?.let { jobEntity ->
                    val outputFile = if (jobEntity.mergeOutput) {
                        mergeOutputs(jobId, allUnits.filter { it.status == BatchWorkUnitStatus.COMPLETED.value }.mapNotNull { it.outputFile?.let { File(it) } })
                    } else {
                        null
                    }
                    val outputUri = outputFile?.let { Uri.fromFile(it) }
                    updateJobStatus(jobId, BatchJobStatus.COMPLETED, completed, null, outputUri?.toString())
                }
            }
        } else {
            // Update progress
            val currentChapter = allUnits.firstOrNull { it.status != BatchWorkUnitStatus.COMPLETED.value }?.chapterIndex
            updateJobStatus(jobId, BatchJobStatus.RUNNING, completed, currentChapter, null)
        }
    }

private fun mergeOutputs(jobId: String, files: List<File>): File? {
        if (files.isEmpty()) return null

        val outputDir = File(context.filesDir, "batch-exports").apply { mkdirs() }
        val outputFile = File(outputDir, "$jobId.wav")

        try {
            FileOutputStream(outputFile).use { output ->
                var first = true
                var totalDataSize = 0L
                var sampleRate = 0
                var channels = 0
                var bits = 16

                // First pass: validate all files have same format, calculate total size
                for (file in files) {
                    file.inputStream().use { input ->
                        val header = ByteArray(44)
                        if (input.read(header) != 44) {
                            throw IllegalArgumentException("Invalid WAV: ${file.name}")
                        }
                        // Parse WAV header
                        val thisSampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val thisChannels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        val thisBits = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
                        val thisDataSize = ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()

                        if (first) {
                            sampleRate = thisSampleRate
                            channels = thisChannels
                            bits = thisBits
                            first = false
                        } else if (thisSampleRate != sampleRate || thisChannels != channels || thisBits != bits) {
                            throw IllegalArgumentException("WAV format mismatch: ${file.name}")
                        }
                        totalDataSize += thisDataSize
                    }
                }

                require(totalDataSize <= Int.MAX_VALUE) { "Merged file too large" }

                // Write final header
                writeWaveHeader(output, totalDataSize.toInt(), sampleRate, channels, bits)

                // Second pass: copy data chunks
                for (file in files) {
                    file.inputStream().use { input ->
                        input.skip(44L) // Skip header
                        input.copyTo(output)
                    }
                }
            }
            return outputFile
        } catch (e: Exception) {
            outputFile.delete()
            android.util.Log.e("BatchTtsManager", "Merge failed", e)
            return null
        }
    }

    private fun writeWaveHeader(output: FileOutputStream, dataSize: Int, sampleRate: Int, channels: Int, bits: Int) {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        fun writeAscii(value: String) = output.write(value.toByteArray(kotlin.text.Charsets.US_ASCII))
        fun writeShort(value: Int) {
            output.write(value and 0xFF)
            output.write((value ushr 8) and 0xFF)
        }
        fun writeInt(value: Int) {
            output.write(value and 0xFF)
            output.write((value ushr 8) and 0xFF)
            output.write((value ushr 16) and 0xFF)
            output.write((value ushr 24) and 0xFF)
        }
        writeAscii("RIFF")
        writeInt(dataSize + 36)
        writeAscii("WAVEfmt ")
        writeInt(16)
        writeShort(1)
        writeShort(channels)
        writeInt(sampleRate)
        writeInt(byteRate)
        writeShort(blockAlign)
        writeShort(bits)
        writeAscii("data")
        writeInt(dataSize)
    }

    suspend fun getJob(jobId: String): BatchTtsJob? {
        return withContext(Dispatchers.IO) {
            PlaybackDatabase.getInstance(context).playbackDao().getBatchJob(jobId)?.toDomain()
        }
    }

    fun observeJobs(): kotlinx.coroutines.flow.Flow<List<BatchTtsJob>> {
        return PlaybackDatabase.getInstance(context).playbackDao().observeBatchJobs()
            .map { it.map { it.toDomain() } }
    }

    fun shutdown() {
        (scope.coroutineContext[Job] as? Job)?.cancel()
        activeGenerations.values.forEach { it.cancel() }
        activeGenerations.clear()
    }
}

private fun BatchJobEntity.toDomain(): BatchTtsJob {
    return BatchTtsJob(
        id = id,
        documentId = documentId,
        status = BatchJobStatus.values()[status],
        totalChapters = totalChapters,
        completedChapters = completedChapters,
        currentChapter = currentChapter,
        outputUri = outputUri?.let { Uri.parse(it) },
        voiceId = voiceId,
        voiceSpeed = voiceSpeed,
        mergeOutput = mergeOutput,
        createdAt = createdAtMillis,
        updatedAt = updatedAtMillis,
        error = error,
    )
}