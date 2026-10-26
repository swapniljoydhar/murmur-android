package com.joy.murmur.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import com.joy.murmur.data.settings.DataStoreSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Manages audio output profiles for different devices.
 * Profiles store EQ, gain, headroom, balance, crossfeed settings per output device.
 */
class OutputProfileManager(
    private val context: Context,
    private val settingsRepository: DataStoreSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profilesFlow: MutableStateFlow<List<OutputProfile>> = MutableStateFlow(emptyList())
    private val activeProfileFlow: MutableStateFlow<OutputProfile?> = MutableStateFlow(null)
    
    val profiles: Flow<List<OutputProfile>> = profilesFlow
    val activeProfile: Flow<OutputProfile?> = activeProfileFlow

    init {
        scope.launch { loadProfiles() }
    }

    private suspend fun loadProfiles() {
        settingsRepository.outputProfiles.collect { json ->
            val list = Json { ignoreUnknownKeys = true }.decodeFromString<List<OutputProfile>>(json)
            profilesFlow.value = list
            updateActiveProfile()
        }
    }

    private suspend fun saveProfiles() {
        val json = Json { prettyPrint = true }.encodeToString(profilesFlow.value)
        settingsRepository.setOutputProfiles(json)
    }

    private fun updateActiveProfile() {
        val devices = getCurrentOutputDevices()
        val matched = profilesFlow.value.firstOrNull { profile ->
            profile.matches(devices)
        }
        activeProfileFlow.value = matched
    }

    /**
     * Gets current output devices from AudioManager.
     */
    private fun getCurrentOutputDevices(): List<AudioDeviceInfo> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
    }

    /**
     * Creates a new profile for the currently active output device.
     */
    suspend fun createProfileForCurrentOutput(name: String? = null): OutputProfile {
        val devices = getCurrentOutputDevices()
        val primary = devices.firstOrNull() ?: return OutputProfile(
            id = UUID.randomUUID().toString(),
            name = name ?: "New Profile",
            deviceType = "Unknown",
            productName = "Unknown",
            eqBands = List(10) { 0f },
            replayGainPreamp = 0f,
            headroomDb = -1f,
            balance = 0f,
            crossfeedLevel = 0f,
            createdAt = System.currentTimeMillis(),
        )

        val profile = OutputProfile(
            id = UUID.randomUUID().toString(),
            name = name ?: primary.productName.toString(),
            deviceType = deviceTypeToString(primary.type),
            productName = primary.productName.toString(),
            eqBands = List(10) { 0f },
            replayGainPreamp = 0f,
            headroomDb = -1f,
            balance = 0f,
            crossfeedLevel = 0f,
            createdAt = System.currentTimeMillis(),
        )
        
        profilesFlow.update { it + profile }
        saveProfiles()
        updateActiveProfile()
        return profile
    }

    /**
     * Updates an existing profile.
     */
    suspend fun updateProfile(profile: OutputProfile) {
        profilesFlow.update { list ->
            list.map { if (it.id == profile.id) profile else it }
        }
        saveProfiles()
        updateActiveProfile()
    }

    /**
     * Deletes a profile.
     */
    suspend fun deleteProfile(profileId: String) {
        profilesFlow.update { it.filter { it.id != profileId } }
        saveProfiles()
        updateActiveProfile()
    }

    /**
     * Gets the active profile for current output.
     */
    fun getActiveProfile(): OutputProfile? = activeProfileFlow.value

    /**
     * Checks if a profile should be auto-applied for current output.
     * Returns false if output identity is ambiguous.
     */
    fun shouldAutoApply(): Boolean {
        val devices = getCurrentOutputDevices()
        val matching = profilesFlow.value.filter { it.matches(devices) }
        return matching.size == 1
    }

    private fun deviceTypeToString(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "Unknown"
    }
}

/**
 * Audio output profile containing all processing settings for a specific device.
 */
@Serializable
data class OutputProfile(
    val id: String,
    val name: String,
    val deviceType: String,       // "Speaker", "Wired Headset", "Bluetooth", "USB", "HDMI", "Unknown"
    val productName: String,      // Device product name for matching
    val eqBands: List<Float>,     // 10-band EQ gains in dB
    val replayGainPreamp: Float,  // ReplayGain preamp in dB
    val headroomDb: Float,        // Headroom limiter dB
    val balance: Float,           // Balance (-1.0 to 1.0)
    val crossfeedLevel: Float,    // Crossfeed level (0.0 to 1.0)
    val createdAt: Long,
) {
    /**
     * Checks if this profile matches the given output devices.
     * Matches by device type AND product name for reliable identification.
     */
    fun matches(devices: List<AudioDeviceInfo>): Boolean {
        return devices.any { device ->
            val typeMatches = deviceTypeToString(device.type) == deviceType
            val nameMatches = device.productName.toString() == productName
            typeMatches && nameMatches
        }
    }

    private fun deviceTypeToString(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        else -> "Unknown"
    }
}