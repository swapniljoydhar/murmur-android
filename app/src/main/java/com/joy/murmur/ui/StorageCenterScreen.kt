package com.joy.murmur.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joy.murmur.data.storage.StorageCenter
import com.joy.murmur.data.storage.StorageCenter.StorageUsage
import kotlinx.coroutines.launch

@Composable
fun StorageCenterScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageCenter = remember { StorageCenter(context) }
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        refreshing = true
        message = null
        scope.launch {
            try {
                usage = storageCenter.getStorageUsage()
            } catch (e: Exception) {
                message = "Failed to load storage info: ${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    fun clearTtsCache() {
        scope.launch {
            try {
                val freed = storageCenter.clearTtsCache()
                message = "Cleared TTS cache (${formatBytes(freed)} freed)"
                refresh()
            } catch (e: Exception) {
                message = "Failed to clear TTS cache: ${e.message}"
            }
        }
    }

    fun clearTempFiles() {
        scope.launch {
            try {
                val freed = storageCenter.clearTempFiles()
                message = "Cleared temp files (${formatBytes(freed)} freed)"
                refresh()
            } catch (e: Exception) {
                message = "Failed to clear temp files: ${e.message}"
            }
        }
    }

    fun clearNeuralVoices() {
        scope.launch {
            try {
                val freed = storageCenter.clearNeuralVoices()
                message = "Cleared neural voices (${formatBytes(freed)} freed)"
                refresh()
            } catch (e: Exception) {
                message = "Failed to clear neural voices: ${e.message}"
            }
        }
    }

    fun clearExports() {
        scope.launch {
            try {
                val freed = storageCenter.clearExports()
                message = "Cleared exports (${formatBytes(freed)} freed)"
                refresh()
            } catch (e: Exception) {
                message = "Failed to clear exports: ${e.message}"
            }
        }
    }

    fun exportDatabase(uri: Uri) {
        scope.launch {
            try {
                storageCenter.exportDatabase(uri)
                message = "Database exported successfully"
            } catch (e: Exception) {
                message = "Failed to export database: ${e.message}"
            }
        }
    }

    fun importDatabase(uri: Uri) {
        scope.launch {
            try {
                storageCenter.importDatabase(uri)
                message = "Database imported successfully"
                refresh()
            } catch (e: Exception) {
                message = "Failed to import database: ${e.message}"
            }
        }
    }

    val pickExportDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let { exportDatabase(it) }
    }
    val pickImportFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { importDatabase(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Storage Center", style = MaterialTheme.typography.headlineMedium)
        Text("Manage Murmur's local storage. Your music, audiobooks, and documents are never touched.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        usage?.let { u ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Storage Usage", style = MaterialTheme.typography.titleMedium)
                    StorageRow("TTS Speech Cache", u.ttsCacheBytes)
                    StorageRow("Neural Voices", u.neuralVoicesBytes)
                    StorageRow("Generated Exports", u.exportsBytes)
                    StorageRow("Database", u.databaseBytes)
                    StorageRow("Temporary Files", u.tempBytes)
                    HorizontalDivider()
                    StorageRow("Total Used", u.totalBytes, isTotal = true)
                    StorageRow("Available Space", u.availableBytes, isTotal = true)
                }
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Cleanup", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clearTtsCache() }, enabled = usage?.ttsCacheBytes ?: 0L > 0) {
                        Text("Clear TTS Cache")
                    }
                    Text(formatBytes(usage?.ttsCacheBytes ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clearTempFiles() }, enabled = usage?.tempBytes ?: 0L > 0) {
                        Text("Clear Temp Files")
                    }
                    Text(formatBytes(usage?.tempBytes ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clearNeuralVoices() }, enabled = usage?.neuralVoicesBytes ?: 0L > 0) {
                        Text("Clear Neural Voices")
                    }
                    Text(formatBytes(usage?.neuralVoicesBytes ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { clearExports() }, enabled = usage?.exportsBytes ?: 0L > 0) {
                        Text("Clear Exports")
                    }
                    Text(formatBytes(usage?.exportsBytes ?: 0L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (refreshing) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.fillMaxWidth().padding(16.dp))
        } else {
            Button(onClick = { refresh() }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickExportDir.launch(null) }) { Text("Export Database") }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { pickImportFile.launch(arrayOf("application/vnd.sqlite3")) }) { Text("Import Database") }
                }
                Text("Backup includes playlists, positions, bookmarks, settings. Source media and documents are not included.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, isTotal: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = if (isTotal) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(bytes), style = if (isTotal) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes / 1024).toString()} KB"
        bytes < 1024 * 1024 * 1024 -> "${(bytes / (1024 * 1024)).toString()} MB"
        else -> "${(bytes / (1024 * 1024 * 1024)).toString()} GB"
    }
}