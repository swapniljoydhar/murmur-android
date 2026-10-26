package com.joy.murmur.playback

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.media3.session.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BackgroundPlaybackHealth {
    HEALTHY,
    POTENTIALLY_RESTRICTED,
    LIKELY_RESTRICTED,
    UNKNOWN,
}

data class OemDiagnosticState(
    val health: BackgroundPlaybackHealth,
    val manufacturer: String,
    val model: String,
    val androidVersion: Int,
    val oemSkin: String?,
    val isBatteryOptimizationEnabled: Boolean,
    val isPowerSaverActive: Boolean,
    val isAppRestricted: Boolean,
    val isForegroundServiceActive: Boolean,
    val mediaSessionState: Int,
    val playerState: Int,
    val notificationState: Boolean,
    val recentInterruption: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)

class OemCompatibility(private val context: Context) {
    private val appContext = context.applicationContext
    private val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val appOpsManager = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = appContext.packageManager

    private val _diagnosticState = MutableStateFlow<OemDiagnosticState>(OemDiagnosticState(
        health = BackgroundPlaybackHealth.UNKNOWN,
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.SDK_INT,
        oemSkin = detectOemSkin(),
        isBatteryOptimizationEnabled = false,
        isPowerSaverActive = false,
        isAppRestricted = false,
        isForegroundServiceActive = false,
        mediaSessionState = 0,
        playerState = 0,
        notificationState = false,
        recentInterruption = false,
    ))
    val diagnosticState: StateFlow<OemDiagnosticState> = _diagnosticState.asStateFlow()

    fun updateDiagnostics(
        isForegroundServiceActive: Boolean,
        mediaSessionState: Int,
        playerState: Int,
        notificationState: Boolean,
    ) {
        val health = evaluateHealth(
            isForegroundServiceActive,
            mediaSessionState,
            playerState,
            notificationState,
        )
        _diagnosticState.value = OemDiagnosticState(
            health = health,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.SDK_INT,
            oemSkin = detectOemSkin(),
            isBatteryOptimizationEnabled = isBatteryOptimizationEnabled(),
            isPowerSaverActive = isPowerSaverActive(),
            isAppRestricted = isAppRestricted(),
            isForegroundServiceActive = isForegroundServiceActive,
            mediaSessionState = mediaSessionState,
            playerState = playerState,
            notificationState = notificationState,
            recentInterruption = false,
        )
    }

    fun recordInterruption() {
        _diagnosticState.value = _diagnosticState.value.copy(recentInterruption = true)
    }

    private fun evaluateHealth(
        isForegroundServiceActive: Boolean,
        mediaSessionState: Int,
        playerState: Int,
        notificationState: Boolean,
    ): BackgroundPlaybackHealth {
        var riskScore = 0
        
        if (!isForegroundServiceActive) riskScore += 3
        if (isAppRestricted()) riskScore += 3
        if (isBatteryOptimizationEnabled()) riskScore += 2
        if (isPowerSaverActive()) riskScore += 2
        if (!notificationState) riskScore += 2
        if (mediaSessionState == androidx.media3.session.MediaController.STATE_IDLE) riskScore += 1
        if (playerState == androidx.media3.common.Player.STATE_IDLE) riskScore += 1
        
        return when {
            riskScore == 0 -> BackgroundPlaybackHealth.HEALTHY
            riskScore <= 3 -> BackgroundPlaybackHealth.POTENTIALLY_RESTRICTED
            riskScore <= 6 -> BackgroundPlaybackHealth.LIKELY_RESTRICTED
            else -> BackgroundPlaybackHealth.LIKELY_RESTRICTED
        }
    }

    private fun isBatteryOptimizationEnabled(): Boolean {
        return !powerManager.isIgnoringBatteryOptimizations(appContext.packageName)
    }

    private fun isPowerSaverActive(): Boolean {
        return powerManager.isPowerSaveMode
    }

    private fun isAppRestricted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ops = appOpsManager.unsafeCheckOpNoThrow("android:run_in_background", android.os.Process.myUid(), appContext.packageName)
            return ops != AppOpsManager.MODE_ALLOWED
        }
        return false
    }

    private fun detectOemSkin(): String? {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return when {
            manufacturer.contains("samsung") -> "One UI"
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> "HyperOS/MIUI"
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> "ColorOS/OxygenOS"
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> "Funtouch OS"
            manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> "My UX"
            manufacturer.contains("nothing") -> "Nothing OS"
            manufacturer.contains("google") -> "Pixel Experience"
            else -> null
        }
    }

    fun getFixIntent(): Intent? {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        
        val intent = when {
            manufacturer.contains("samsung") -> createSamsungIntent()
            manufacturer.contains("xiaomi") || brand.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> createXiaomiIntent()
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> createOppoIntent()
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> createVivoIntent()
            manufacturer.contains("motorola") || manufacturer.contains("lenovo") -> createMotorolaIntent()
            manufacturer.contains("nothing") -> createNothingIntent()
            else -> createGenericIntent()
        }
        
        // Validate intent resolves to an activity
        return if (intent.resolveActivity(packageManager) != null) intent else createGenericIntent()
    }

    private fun createSamsungIntent(): Intent {
        return Intent().apply {
            setAction("android.settings.APPLICATION_DETAILS_SETTINGS")
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun createXiaomiIntent(): Intent {
        return try {
            // Try HyperOS intent first
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            try {
                // Fallback to older MIUI intent
                Intent("com.miui.securitycenter.permission.PERM_EDITOR").apply {
                    putExtra("pkgname", appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                createGenericIntent()
            }
        }
    }

    private fun createOppoIntent(): Intent {
        return try {
            Intent().apply {
                setClassName("com.coloros.safecenter", "com.coloros.safecenter.permission.PermissionAppDetailActivity")
                putExtra("packageName", appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            try {
                Intent().apply {
                    setClassName("com.oppo.safecenter", "com.oppo.safecenter.permission.PermissionAppDetailActivity")
                    putExtra("packageName", appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                createGenericIntent()
            }
        }
    }

    private fun createVivoIntent(): Intent {
        return try {
            Intent().apply {
                setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
                putExtra("packagename", appContext.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (_: Exception) {
            try {
                Intent().apply {
                    setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PermissionManagerActivity")
                    putExtra("packagename", appContext.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } catch (_: Exception) {
                createGenericIntent()
            }
        }
    }

    private fun createMotorolaIntent(): Intent = createGenericIntent()
    private fun createNothingIntent(): Intent = createGenericIntent()

    private fun createGenericIntent(): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", appContext.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

