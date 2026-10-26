package com.joy.murmur.ui

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joy.murmur.playback.BackgroundPlaybackHealth
import com.joy.murmur.playback.OemCompatibility
import com.joy.murmur.playback.OemDiagnosticState

@Composable
fun OemFixScreen(
    diagnostic: OemDiagnosticState,
    oemCompatibility: OemCompatibility,
    onDismiss: () -> Unit,
    onOpenSettings: (Intent) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showDetails by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
) {
            Text("Background Playback Issues", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Your phone may be restricting Murmur's background playback. This is common on some manufacturers.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Health status card
        val healthColor = when (diagnostic.health) {
            BackgroundPlaybackHealth.HEALTHY -> MaterialTheme.colorScheme.tertiary
            BackgroundPlaybackHealth.POTENTIALLY_RESTRICTED -> MaterialTheme.colorScheme.secondary
            BackgroundPlaybackHealth.LIKELY_RESTRICTED, BackgroundPlaybackHealth.UNKNOWN -> MaterialTheme.colorScheme.error
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = healthColor.copy(alpha = 0.1f),
            ),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Playback Health", style = MaterialTheme.typography.titleMedium)
                    Text(
                        diagnostic.health.name.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        color = healthColor,
                    )
                }
                Text(
                    when (diagnostic.health) {
                        BackgroundPlaybackHealth.HEALTHY -> "Background playback should work normally."
                        BackgroundPlaybackHealth.POTENTIALLY_RESTRICTED -> "Some restrictions detected. Playback may stop when screen is off."
                        BackgroundPlaybackHealth.LIKELY_RESTRICTED -> "Heavy restrictions detected. Background playback will likely fail."
                        BackgroundPlaybackHealth.UNKNOWN -> "Unable to determine restriction status."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Device info card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("Device Information", style = MaterialTheme.typography.titleMedium)
                DiagnosticRow("Manufacturer", diagnostic.manufacturer)
                DiagnosticRow("Model", diagnostic.model)
                DiagnosticRow("Android Version", "API ${diagnostic.androidVersion} (${Build.VERSION.RELEASE})")
                diagnostic.oemSkin?.let { DiagnosticRow("OEM Skin", it) }
            }
        }

// Restriction indicators
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Detected Restrictions", style = MaterialTheme.typography.titleMedium)
                RestrictionRow("Battery Optimization", diagnostic.isBatteryOptimizationEnabled, 
                    "App is not exempt from battery optimization")
                RestrictionRow("Power Saver Mode", diagnostic.isPowerSaverActive,
                    "System power saver is active")
                RestrictionRow("App Restricted", diagnostic.isAppRestricted,
                    "App is restricted from running in background")
                RestrictionRow("Foreground Service", diagnostic.isForegroundServiceActive,
                    "Playback service is running")
                RestrictionRow("Active Notification", diagnostic.notificationState,
                    "Playback notification is visible")
            }
        }

// Details toggle
        if (showDetails) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Technical Details", style = MaterialTheme.typography.titleMedium)
                    DiagnosticRow("MediaSession State", diagnostic.mediaSessionState.toString())
                    DiagnosticRow("Player State", diagnostic.playerState.toString())
                    DiagnosticRow("Recent Interruption", diagnostic.recentInterruption.toString())
                    DiagnosticRow("Timestamp", diagnostic.timestamp.toString())
                }
            }
        }

        Button(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Hide Details" else "Show Technical Details")
        }

// Action button
        oemCompatibility.getFixIntent()?.let { intent ->
            Button(
                onClick = { onOpenSettings(intent) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Open Settings to Fix")
            }
        }

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Not Now")
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RestrictionRow(label: String, isActive: Boolean, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            if (isActive) "⚠ Active" else "✓ OK",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
fun OemFixDialog(
    diagnostic: OemDiagnosticState,
    oemCompatibility: OemCompatibility,
    onDismiss: () -> Unit,
    onOpenSettings: (Intent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background Playback Issue") },
        text = {
            Column(modifier = Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Murmur detected that background playback may be restricted on your ${diagnostic.manufacturer} ${diagnostic.model}. " +
                    "This can cause playback to stop when the screen turns off or when you switch apps.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            oemCompatibility.getFixIntent()?.let { intent ->
                Button(onClick = { onOpenSettings(intent); onDismiss() }) {
                    Text("Open Settings")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not Now") }
        },
    )
}
