package com.joy.murmur.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.joy.murmur.data.storage.StorageCenter
import com.joy.murmur.data.storage.StorageCenter.StorageUsage
import com.joy.murmur.data.storage.MurmurBackupSummary
import kotlinx.coroutines.launch

@Composable
fun StorageCenterScreen(playbackInUse: Boolean) {
    val context = LocalContext.current
    val storageCenter = remember(context) { StorageCenter(context) }
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var backupUri by remember { mutableStateOf<Uri?>(null) }
    var backupSummary by remember { mutableStateOf<MurmurBackupSummary?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        loading = true
        error = null
        scope.launch {
            try {
                usage = storageCenter.getStorageUsage()
            } catch (_: Exception) {
                error = "Storage information is unavailable right now."
            } finally {
                loading = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { refresh() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            loading = true
            message = null
            try {
                val summary = storageCenter.createBackup(uri)
                message = "Backup created with ${summary.totalRecords} records."
            } catch (failure: Exception) {
                error = failure.message ?: "Could not create backup."
            } finally {
                loading = false
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            error = null
            try {
                backupSummary = storageCenter.inspectBackup(uri)
                backupUri = uri
            } catch (failure: Exception) {
                backupSummary = null
                backupUri = null
                error = failure.message ?: "This backup cannot be restored."
            } finally {
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Storage", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Storage usage is read-only. Your music, audiobooks, documents, and generated files are not changed here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        usage?.let { current ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("App storage", style = MaterialTheme.typography.titleMedium)
                    StorageRow("Read Aloud cache", current.ttsCacheBytes)
                    StorageRow("Neural voice assets", current.neuralVoicesBytes)
                    StorageRow("Generated exports", current.exportsBytes)
                    StorageRow("Database and SQLite journal", current.databaseBytes)
                    StorageRow("Temporary files", current.tempBytes)
                    HorizontalDivider()
                    StorageRow("Total", current.totalBytes, bold = true)
                    StorageRow("Available", current.availableBytes, bold = true)
                }
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
        if (loading) CircularProgressIndicator()
        else Button(onClick = ::refresh) { Text("Refresh") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Backup & Restore", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Backups contain playlists, library metadata, bookmarks, reading/listening progress, audiobook chapters, and document structure. App preferences, the live playback queue, source media, generated speech cache, and SAF access grants are excluded. Re-select source files after restoring on another device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !loading, onClick = { exportLauncher.launch("murmur-backup.json") }) { Text("Create backup") }
                    Button(enabled = !loading && !playbackInUse, onClick = { importLauncher.launch(arrayOf("application/json", "text/json")) }) { Text("Choose backup") }
                }
                if (playbackInUse) Text("Clear the current playback queue before restoring.", color = MaterialTheme.colorScheme.error)
                backupSummary?.let { summary ->
                    Text("Backup preview", style = MaterialTheme.typography.titleSmall)
                    Text("${summary.totalRecords} records across ${summary.recordsByTable.size} data groups")
                    TextButton(onClick = { confirmRestore = true }, enabled = !loading && !playbackInUse) { Text("Restore this backup…") }
                }
            }
        }
    }

    if (confirmRestore && backupUri != null && !playbackInUse) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Replace Murmur data?") },
            text = {
                Text("Restore replaces Murmur's saved library, bookmarks, and progress with the validated backup. This cannot be undone. Preferences and source files are not included; source access may need to be granted again.")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    val source = backupUri ?: return@TextButton
                    scope.launch {
                        loading = true
                        error = null
                        try {
                            val restored = storageCenter.restoreBackup(source)
                            message = "Restored ${restored.totalRecords} records."
                            backupSummary = null
                            backupUri = null
                            refresh()
                        } catch (failure: Exception) {
                            error = failure.message ?: "Restore failed. Your existing data was preserved."
                        } finally {
                            loading = false
                        }
                    }
                }) { Text("Replace data") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun StorageRow(label: String, bytes: Long, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val style = if (bold) MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        else MaterialTheme.typography.bodyMedium
        Text(label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatBytes(bytes), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> "${bytes / (1024 * 1024 * 1024)} GB"
}
