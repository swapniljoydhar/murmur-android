package com.joy.murmur.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joy.murmur.playback.OutputInfo
import com.joy.murmur.playback.PlaybackController
import com.joy.murmur.playback.ProcessingInfo
import com.joy.murmur.playback.SourceInfo
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun SoundScreen(
    playbackController: PlaybackController,
) {
    val scope = rememberCoroutineScope()
    val sourceInfoState = remember { mutableStateOf<SourceInfo?>(null) }
    val processingInfoState = remember { mutableStateOf<ProcessingInfo?>(null) }
    val outputInfoState = remember { mutableStateOf<OutputInfo?>(null) }
    val checkingState = remember { mutableStateOf(false) }
    val errorState = remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            checkingState.value = true
            errorState.value = null
            try {
                val info = playbackController.getSourceInfo()
                sourceInfoState.value = info
            } catch (e: Exception) {
                errorState.value = "Could not load source info: ${e.message}"
            } finally {
                checkingState.value = false
            }
        }
    }

    // Load processing and output info
    LaunchedEffect(Unit) {
        scope.launch {
            processingInfoState.value = playbackController.getProcessingInfo()
            outputInfoState.value = playbackController.getOutputInfo()
        }
    }

    // Auto-refresh on first load
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp).verticalScroll(rememberScrollState())) {
        Text("Sound", style = MaterialTheme.typography.headlineSmall)
        Text("Factual audio path information. No marketing claims.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Source section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Source", style = MaterialTheme.typography.titleMedium)
                    if (checkingState.value) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.padding(start = 8.dp).size(20.dp))
                    }
                }
                Divider(Modifier.padding(vertical = 8.dp))
                SourceInfoSection(sourceInfoState.value, errorState.value)
            }
        }

        // Processing section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Processing", style = MaterialTheme.typography.titleMedium)
                Divider(Modifier.padding(vertical = 8.dp))
                ProcessingInfoSection(processingInfoState.value)
            }
        }

        // Output section
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Output", style = MaterialTheme.typography.titleMedium)
                Divider(Modifier.padding(vertical = 8.dp))
                OutputInfoSection(outputInfoState.value)
            }
        }

        // Bit-perfect status
        OutlinedCard(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Bit-perfect Status", style = MaterialTheme.typography.titleMedium)
                Divider(Modifier.padding(vertical = 8.dp))
                BitPerfectStatusSection(sourceInfoState.value, outputInfoState.value, processingInfoState.value)
            }
        }

        // Refresh button
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
            Button(onClick = { refresh() }) {
                Text("Refresh")
            }
        }
    }
}

@Composable
fun SourceInfoSection(sourceInfo: SourceInfo?, error: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        error?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            return@SourceInfoSection
        }

        sourceInfo?.let {
            InfoRow("Format", it.format)
            InfoRow("Codec", it.codec)
            InfoRow("Sample Rate", "${it.sampleRate} Hz")
            InfoRow("Bit Depth", "${it.bitDepth}-bit")
            InfoRow("Channels", it.channels)
            InfoRow("Bitrate", it.bitrate)
            InfoRow("Duration", it.duration)
            if (it.replayGainTrack != null) InfoRow("ReplayGain (Track)", "${it.replayGainTrack} dB")
            if (it.replayGainAlbum != null) InfoRow("ReplayGain (Album)", "${it.replayGainAlbum} dB")
            if (it.replayGainPeak != null) InfoRow("ReplayGain Peak", String.format(Locale.ROOT, "%.3f", it.replayGainPeak))
        } ?: InfoRow("Format", "Unknown (no media playing)")
    }
}

@Composable
fun ProcessingInfoSection(processingInfo: ProcessingInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        processingInfo?.let {
            ProcessingRow("ReplayGain", it.replayGainEnabled, it.replayGainStatus)
            ProcessingRow("Equalizer", it.equalizerEnabled, it.equalizerStatus)
            ProcessingRow("Crossfeed", it.crossfeedEnabled, it.crossfeedStatus)
            ProcessingRow("Headroom Limiter", it.headroomEnabled, it.headroomStatus)
            ProcessingRow("Balance", it.balanceEnabled, it.balanceStatus)
            ProcessingRow("Dither", it.ditherEnabled, it.ditherStatus)
        } ?: InfoRow("Processing", "Unknown")
    }
}

@Composable
fun OutputInfoSection(outputInfo: OutputInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        outputInfo?.let {
            InfoRow("Route", it.route)
            InfoRow("Device", it.deviceName)
            InfoRow("Output Sample Rate", "${it.outputSampleRate} Hz")
            InfoRow("Output Bit Depth", "${it.outputBitDepth}-bit")
            InfoRow("Output Channels", it.outputChannels)
            InfoRow("Latency", "${it.latencyMs} ms")
            InfoRow("Volume", String.format(Locale.ROOT, "%.0f%%", it.volume * 100))
        } ?: InfoRow("Output", "Unknown")
    }
}

@Composable
fun BitPerfectStatusSection(sourceInfo: SourceInfo?, outputInfo: OutputInfo?, processingInfo: ProcessingInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hasProcessing = processingInfo?.let {
            it.replayGainEnabled || it.equalizerEnabled || it.crossfeedEnabled ||
            it.headroomEnabled || it.balanceEnabled || it.ditherEnabled
        } == true
        
        val sampleRateMatch = sourceInfo?.sampleRate?.let { src ->
            outputInfo?.outputSampleRate?.let { out -> src == out }
        } == true
        
        val bitDepthMatch = sourceInfo?.bitDepth?.let { src ->
            outputInfo?.outputBitDepth?.let { out -> src == out }
        } == true
        
        val channelsMatch = sourceInfo?.channels?.let { src ->
            outputInfo?.outputChannels?.let { out -> src == out }
        } == true

        when {
            hasProcessing -> {
                Text("Not confirmed — Active processing applied", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text("ReplayGain, EQ, crossfeed, headroom limiter, balance, or dither is active. The signal is being modified.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !sampleRateMatch -> {
                Text("Not confirmed — Sample rate mismatch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text("Source: ${sourceInfo?.sampleRate} Hz → Output: ${outputInfo?.outputSampleRate} Hz. Resampling is occurring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !bitDepthMatch -> {
                Text("Not confirmed — Bit depth mismatch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text("Source: ${sourceInfo?.bitDepth}-bit → Output: ${outputInfo?.outputBitDepth}-bit. Bit depth conversion is occurring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            !channelsMatch -> {
                Text("Not confirmed — Channel mismatch", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Text("Source: ${sourceInfo?.channels} ch → Output: ${outputInfo?.outputChannels} ch. Channel conversion is occurring.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> {
                Text("Bit-perfect confirmed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
                Text("No processing active. Sample rate, bit depth, and channel count match source to output.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = label, modifier = Modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, modifier = Modifier, style = MaterialTheme.typography.bodyMedium, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
    }
}

@Composable
fun ProcessingRow(label: String, enabled: Boolean, status: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(text = label, modifier = Modifier, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = if (enabled) status else "Disabled", modifier = Modifier, style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
    }
}