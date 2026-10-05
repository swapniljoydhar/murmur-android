package com.joy.murmur.data.speech

import android.content.Context
import android.media.AudioFormat
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.joy.murmur.data.playback.PlaybackDatabase
import com.joy.murmur.data.playback.PlaybackStore
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class VoiceNetworkRequirement { KNOWN_OFFLINE, NETWORK_REQUIRED, UNKNOWN, UNAVAILABLE }

data class SpeechVoiceOption(val name: String, val localeTag: String, val requiresNetwork: Boolean) {
    val label: String
        get() = "${localeTag.ifBlank { "Unknown language" }} · ${if (requiresNetwork) "Network required" else "On device"}"
}

data class GeneratedSpeechFile(val file: File, val segmentIndex: Int, val sourceOffset: Int)

class SystemTtsSpeechGenerator(context: Context) {
    private data class Capture(
        val rawFile: File,
        val destination: File,
        val result: CompletableDeferred<File>,
        var sampleRate: Int = 0,
        var channelCount: Int = 0,
        var encoding: Int = 0,
        var output: FileOutputStream? = null,
    )

    private val appContext = context.applicationContext
    private val outputDirectory = File(appContext.cacheDir, "read-aloud").apply { mkdirs() }
    private val playbackStore = PlaybackStore(PlaybackDatabase.getInstance(appContext))
    private val ready = CompletableDeferred<Unit>()
    private val captures = ConcurrentHashMap<String, Capture>()
    private val synthesisMutex = Mutex()
    private val engine = TextToSpeech(appContext) { status ->
        if (status == TextToSpeech.SUCCESS) ready.complete(Unit)
        else ready.completeExceptionally(IllegalStateException("The system speech engine isn't available."))
    }

    init {
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit

            override fun onBeginSynthesis(utteranceId: String, sampleRateInHz: Int, audioFormat: Int, channelCount: Int) {
                val capture = captures[utteranceId] ?: return
                synchronized(capture) {
                    capture.sampleRate = sampleRateInHz
                    capture.encoding = audioFormat
                    capture.channelCount = channelCount
                    capture.output = FileOutputStream(capture.rawFile)
                }
            }

            override fun onAudioAvailable(utteranceId: String, audio: ByteArray) {
                val capture = captures[utteranceId] ?: return
                synchronized(capture) { capture.output?.write(audio) }
            }

            override fun onDone(utteranceId: String) {
                val capture = captures.remove(utteranceId) ?: return
                try {
                    val output = synchronized(capture) { capture.output.also { capture.output = null } }
                    output?.fd?.sync()
                    output?.close()
                    capture.result.complete(writeWave(capture))
                } catch (error: Exception) {
                    capture.result.completeExceptionally(error)
                }
            }

            override fun onError(utteranceId: String) = fail(utteranceId, IllegalStateException("The system speech engine couldn't synthesize this page."))

            override fun onError(utteranceId: String, errorCode: Int) = fail(
                utteranceId,
                IllegalStateException("The system speech engine couldn't synthesize this page (error $errorCode)."),
            )
        })
    }

    suspend fun voiceNetworkRequirement(): VoiceNetworkRequirement {
        if (runCatching { awaitReady() }.isFailure) return VoiceNetworkRequirement.UNAVAILABLE
        return engine.voice?.let { voice ->
            if (voice.isNetworkConnectionRequired) VoiceNetworkRequirement.NETWORK_REQUIRED else VoiceNetworkRequirement.KNOWN_OFFLINE
        } ?: VoiceNetworkRequirement.UNKNOWN
    }

    suspend fun availableVoices(): List<SpeechVoiceOption> {
        awaitReady()
        return engine.voices.orEmpty()
            .map { voice -> SpeechVoiceOption(voice.name, voice.locale.toLanguageTag(), voice.isNetworkConnectionRequired) }
            .distinctBy(SpeechVoiceOption::name)
            .sortedWith(compareBy(SpeechVoiceOption::localeTag, SpeechVoiceOption::requiresNetwork, SpeechVoiceOption::name))
    }

    suspend fun selectVoice(voiceName: String?) {
        awaitReady()
        if (voiceName.isNullOrBlank()) {
            engine.voice = engine.defaultVoice
            return
        }
        val selected = engine.voices.orEmpty().firstOrNull { it.name == voiceName }
            ?: throw IllegalArgumentException("That speech voice is no longer installed on this device.")
        check(engine.setVoice(selected) == TextToSpeech.SUCCESS) { "The system speech engine couldn't select that voice." }
    }

    suspend fun synthesize(text: String, identity: String, strictOffline: Boolean): List<GeneratedSpeechFile> = synthesisMutex.withLock {
        awaitReady()
        when (voiceNetworkRequirement()) {
            VoiceNetworkRequirement.NETWORK_REQUIRED -> if (strictOffline) throw IllegalStateException("The selected system voice requires a network connection. Turn off Strict Offline Speech to use it.")
            VoiceNetworkRequirement.UNKNOWN -> if (strictOffline) throw IllegalStateException("This system voice's network requirement is unknown. Turn off Strict Offline Speech only if you accept that it may use a network.")
            VoiceNetworkRequirement.UNAVAILABLE -> throw IllegalStateException("The system speech engine isn't available on this device.")
            VoiceNetworkRequirement.KNOWN_OFFLINE -> Unit
        }
        val queuedFiles = playbackStore.queue().mapNotNull { item ->
            Uri.parse(item.uri).takeIf { it.scheme == "file" }?.path
        }.toSet()
        withContext(Dispatchers.IO) {
            evictSpeechCache(queuedFiles)
            val voiceIdentity = listOf(engine.defaultEngine.orEmpty(), engine.voice?.name.orEmpty(), engine.voice?.locale?.toLanguageTag().orEmpty())
                .joinToString("|")
            splitSpeechText(text, (TextToSpeech.getMaxSpeechInputLength() - 128).coerceAtLeast(256)).mapIndexed { index, chunk ->
                val digest = sha256("$identity|$voiceIdentity|${chunk.text}")
                val destination = File(outputDirectory, "$digest.wav")
                if (destination.isFile && destination.length() > 44L) {
                    destination.setLastModified(System.currentTimeMillis())
                    GeneratedSpeechFile(destination, index, chunk.sourceOffset)
                } else {
                    synthesizeChunk(chunk.text, destination)
                    destination.setLastModified(System.currentTimeMillis())
                    GeneratedSpeechFile(destination, index, chunk.sourceOffset)
                }
            }
        }
    }

    fun shutdown() {
        captures.values.forEach { capture ->
            synchronized(capture) { runCatching { capture.output?.close() } }
            capture.result.cancel()
            capture.rawFile.delete()
        }
        captures.clear()
        engine.stop()
        engine.shutdown()
    }

    private suspend fun awaitReady() = withTimeout(15_000L) { ready.await() }

    private suspend fun synthesizeChunk(text: String, destination: File) {
        val utteranceId = UUID.randomUUID().toString()
        val raw = File.createTempFile("speech-", ".pcm", outputDirectory)
        val capture = Capture(raw, destination, CompletableDeferred())
        captures[utteranceId] = capture
        val engineOutput = File.createTempFile("engine-", ".wav", outputDirectory)
        try {
            val result = engine.synthesizeToFile(text, Bundle(), engineOutput, utteranceId)
            check(result == TextToSpeech.SUCCESS) { "The system speech engine rejected a speech segment." }
            withTimeout(120_000L) { capture.result.await() }
        } catch (error: Exception) {
            captures.remove(utteranceId)
            synchronized(capture) { runCatching { capture.output?.close() } }
            raw.delete()
            destination.delete()
            throw error
        } finally {
            engineOutput.delete()
        }
    }

    private fun fail(utteranceId: String, error: Exception) {
        captures.remove(utteranceId)?.let { capture ->
            synchronized(capture) { runCatching { capture.output?.close() } }
            capture.rawFile.delete()
            capture.result.completeExceptionally(error)
        }
    }

    private fun evictSpeechCache(queuedPaths: Set<String>) {
        val now = System.currentTimeMillis()
        val waveFiles = outputDirectory.listFiles { file -> file.isFile && file.extension == "wav" }.orEmpty()
        var totalBytes = waveFiles.sumOf(File::length)
        waveFiles.sortedBy(File::lastModified).forEach { file ->
            val queued = file.absolutePath in queuedPaths
            val expired = now - file.lastModified() > SPEECH_CACHE_MAX_AGE_MILLIS
            if (!queued && (expired || totalBytes > SPEECH_CACHE_MAX_BYTES)) {
                val size = file.length()
                if (file.delete()) totalBytes -= size
            }
        }
        outputDirectory.listFiles { file ->
            file.isFile && (file.name.endsWith(".part") || file.name.startsWith("speech-") || file.name.startsWith("engine-")) &&
                now - file.lastModified() > SPEECH_TEMP_MAX_AGE_MILLIS
        }.orEmpty().forEach(File::delete)
    }

    private fun writeWave(capture: Capture): File {
        val destination = capture.destination
        require(capture.sampleRate in 8_000..192_000) { "The speech engine returned an unsupported sample rate." }
        require(capture.channelCount in 1..2) { "The speech engine returned an unsupported channel count." }
        val bits = if (capture.encoding == AudioFormat.ENCODING_PCM_8BIT) 8 else 16
        require(capture.encoding in setOf(AudioFormat.ENCODING_PCM_8BIT, AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) {
            "The speech engine returned an unsupported audio format."
        }
        val sourceBytesPerSample = when (capture.encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_16BIT -> 2
            else -> 4
        }
        val sourceSize = capture.rawFile.length()
        require(sourceSize > 0L && sourceSize % (sourceBytesPerSample * capture.channelCount) == 0L) { "The system speech engine returned incomplete audio." }
        val convertedSize = if (bits == 8) sourceSize else sourceSize / sourceBytesPerSample * 2L
        require(convertedSize <= Int.MAX_VALUE - 44L) { "This speech segment is too large to cache safely." }
        val partial = File(destination.parentFile, "${destination.name}.part")
        try {
            BufferedOutputStream(FileOutputStream(partial)).use { output ->
                writeWaveHeader(output, convertedSize.toInt(), capture.sampleRate, capture.channelCount, bits)
                BufferedInputStream(FileInputStream(capture.rawFile)).use { input ->
                    if (capture.encoding == AudioFormat.ENCODING_PCM_16BIT) {
                        input.copyTo(output)
                    } else if (capture.encoding == AudioFormat.ENCODING_PCM_8BIT) {
                        val bytes = ByteArray(8192)
                        while (true) {
                            val count = input.read(bytes)
                            if (count < 0) break
                            output.write(bytes, 0, count)
                        }
                    } else {
                        val bytes = ByteArray(8192)
                        while (true) {
                            val count = input.read(bytes)
                            if (count < 0) break
                            val aligned = count - count % 4
                            val floats = ByteBuffer.wrap(bytes, 0, aligned).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                            while (floats.hasRemaining()) {
                                val sample = (floats.get().coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt()
                                output.write(sample and 0xFF)
                                output.write((sample ushr 8) and 0xFF)
                            }
                        }
                    }
                }
            }
            if (destination.exists()) destination.delete()
            check(partial.renameTo(destination)) { "Murmur couldn't finalize the generated speech file." }
            return destination
        } finally {
            capture.rawFile.delete()
            partial.delete()
        }
    }

    private fun writeWaveHeader(output: BufferedOutputStream, dataSize: Int, sampleRate: Int, channels: Int, bits: Int) {
        val byteRate = sampleRate * channels * bits / 8
        val blockAlign = channels * bits / 8
        fun writeAscii(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
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

    private fun splitSpeechText(text: String, limit: Int): List<SpeechChunk> {
        val output = mutableListOf<SpeechChunk>()
        val sentenceMatches = Regex("[^.!?。！？\\n]+(?:[.!?。！？]+|\\n+|$)").findAll(text).toList()
        var currentStart = -1
        var current = StringBuilder()
        fun flush() {
            val speech = current.toString().trim()
            if (speech.isNotEmpty()) output += SpeechChunk(currentStart.coerceAtLeast(0), speech)
            current = StringBuilder()
            currentStart = -1
        }
        sentenceMatches.forEach { match ->
            val sentence = match.value.trim()
            if (sentence.isEmpty()) return@forEach
            if (sentence.length > limit) {
                flush()
                var offset = 0
                while (offset < sentence.length) {
                    var end = minOf(offset + limit, sentence.length)
                    if (end < sentence.length) {
                        val boundary = sentence.lastIndexOf(' ', end)
                        if (boundary > offset + limit / 2) end = boundary
                    }
                    output += SpeechChunk(match.range.first + offset, sentence.substring(offset, end).trim())
                    offset = end
                    while (offset < sentence.length && sentence[offset].isWhitespace()) offset++
                }
            } else {
                val addition = if (current.isEmpty()) sentence else " $sentence"
                if (current.length + addition.length > limit) flush()
                if (current.isEmpty()) currentStart = match.range.first
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            }
        }
        flush()
        return output
    }

    private data class SpeechChunk(val sourceOffset: Int, val text: String)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xFF) }

    private companion object {
        const val SPEECH_CACHE_MAX_BYTES = 256L * 1024L * 1024L
        const val SPEECH_CACHE_MAX_AGE_MILLIS = 30L * 24L * 60L * 60L * 1_000L
        const val SPEECH_TEMP_MAX_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    }
}
