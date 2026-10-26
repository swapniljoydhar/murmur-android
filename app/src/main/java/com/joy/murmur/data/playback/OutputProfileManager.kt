import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class OutputProfile(
    val id: String,
    val name: String,
    val deviceMatcher: DeviceMatcher,
    val replayGainConfig: ReplayGainConfig? = null,
    val equalizerConfig: EqualizerConfig? = null,
    val crossfeedConfig: CrossfeedConfig? = null,
    val headroomConfig: HeadroomConfig? = null,
    val balanceConfig: BalanceConfig? = null,
    val ditherConfig: DitherConfig? = null,
)

@Serializable
data class DeviceMatcher(
    val type: Int? = null,
    val nameContains: String? = null,
    val addressContains: String? = null,
    val isBluetooth: Boolean? = null,
    val isWired: Boolean? = null,
    val isUsb: Boolean? = null,
) {
    fun matches(device: AudioDeviceInfo): Boolean {
        if (type != null && device.type != type) return false
        if (nameContains != null && device.productName?.contains(nameContains!!, ignoreCase = true) != true) return false
        if (addressContains != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (device.address?.contains(addressContains!!, ignoreCase = true) != true) return false
            } else {
                // On older APIs, address is not available, so we can't match on it
                return false
            }
        }
        if (isBluetooth == true && device.type != AudioDeviceInfo.TYPE_BLUETOOTH_A2DP && device.type != AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return false
        if (isWired == true && device.type != AudioDeviceInfo.TYPE_WIRED_HEADSET && device.type != AudioDeviceInfo.TYPE_WIRED_HEADPHONES) return false
        if (isUsb == true && device.type != AudioDeviceInfo.TYPE_USB_DEVICE && device.type != AudioDeviceInfo.TYPE_USB_HEADSET && device.type != AudioDeviceInfo.TYPE_USB_ACCESSORY) return false
        return true
    }
}

@Serializable
data class ReplayGainConfig(
    val enabled: Boolean = true,
    val preampDb: Float = 0f,
    val useAlbumGain: Boolean = true,
)

@Serializable
data class EqualizerConfig(
    val enabled: Boolean = false,
    val preset: String = "Flat",
    val customGains: Map<String, Float> = emptyMap(),
)

@Serializable
data class CrossfeedConfig(
    val enabled: Boolean = false,
    val crossfeedLevel: Float = 0.5f,
    val highFreqAttenuation: Float = 0.7f,
)

@Serializable
data class HeadroomConfig(
    val enabled: Boolean = true,
    val headroomDb: Float = -1.0f,
    val lookaheadMs: Float = 2.0f,
    val attackMs: Float = 1.0f,
    val releaseMs: Float = 100.0f,
)

@Serializable
data class BalanceConfig(
    val enabled: Boolean = false,
    val balance: Float = 0f,
)

@Serializable
data class DitherConfig(
    val enabled: Boolean = false,
    val bitDepth: Int = 16,
)

private val Context.outputProfilesDataStore: DataStore<Preferences> by preferencesDataStore(name = "output_profiles")

private val OUTPUT_PROFILES_KEY = stringPreferencesKey("output_profiles_json")
private val ACTIVE_PROFILE_KEY = stringPreferencesKey("active_profile_id")

private val json = Json { ignoreUnknownKeys = true }

class OutputProfileManager(private val context: Context) {
    private val dataStore = context.outputProfilesDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val profiles = mutableListOf<OutputProfile>()
    private val _activeProfile = MutableStateFlow<OutputProfile?>(null)
    val activeProfile = _activeProfile.asStateFlow()
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        scope.launch { loadProfiles() }
        addDefaultProfiles()
        startDeviceMonitoring()
    }

    private suspend fun loadProfiles() = withContext(Dispatchers.IO) {
        val jsonString = dataStore.data
            .map { it[OUTPUT_PROFILES_KEY] as? String }
            .first()
        
        if (jsonString != null) {
            try {
                val decoded = json.decodeFromString<kotlin.collections.List<OutputProfile>>(jsonString)
                profiles.addAll(decoded)
            } catch (e: Exception) {
                // Corrupted data, start fresh
            }
        }
        
        val activeId = dataStore.data
            .map { it[ACTIVE_PROFILE_KEY] as? String }
            .first()
        if (activeId != null) {
            _activeProfile.value = profiles.firstOrNull { it.id == activeId }
        }
    }

    private fun addDefaultProfiles() {
        if (profiles.isEmpty()) {
            profiles.addAll(listOf(
                OutputProfile(
                    id = "default",
                    name = "Default",
                    deviceMatcher = DeviceMatcher(),
                    replayGainConfig = ReplayGainConfig(),
                    headroomConfig = HeadroomConfig(),
                ),
                OutputProfile(
                    id = "headphones",
                    name = "Headphones",
                    deviceMatcher = DeviceMatcher(isWired = true),
                    replayGainConfig = ReplayGainConfig(),
                    equalizerConfig = EqualizerConfig(enabled = true, preset = "Flat"),
                    crossfeedConfig = CrossfeedConfig(enabled = true),
                    headroomConfig = HeadroomConfig(),
                ),
                OutputProfile(
                    id = "bluetooth",
                    name = "Bluetooth",
                    deviceMatcher = DeviceMatcher(isBluetooth = true),
                    replayGainConfig = ReplayGainConfig(),
                    equalizerConfig = EqualizerConfig(enabled = true, preset = "Flat"),
                    headroomConfig = HeadroomConfig(headroomDb = -0.5f),
                ),
                OutputProfile(
                    id = "usb_dac",
                    name = "USB DAC",
                    deviceMatcher = DeviceMatcher(isUsb = true),
                    replayGainConfig = ReplayGainConfig(preampDb = -3f),
                    equalizerConfig = EqualizerConfig(enabled = false),
                    headroomConfig = HeadroomConfig(headroomDb = -2.0f),
                    ditherConfig = DitherConfig(enabled = true, bitDepth = 24),
                ),
                OutputProfile(
                    id = "speaker",
                    name = "Speaker",
                    deviceMatcher = DeviceMatcher(type = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER),
                    replayGainConfig = ReplayGainConfig(preampDb = 3f),
                    equalizerConfig = EqualizerConfig(enabled = true, preset = "Bass Boost"),
                    headroomConfig = HeadroomConfig(headroomDb = 0f),
                ),
            ))
            scope.launch { saveProfiles() }
        }
    }

    private fun startDeviceMonitoring() {
        // TODO: Implement AudioManager.OnCommunicationDeviceChangedListener or similar
        // For now, just set default profile
        _activeProfile.value = profiles.firstOrNull { it.id == "default" }
    }

    fun getProfileForCurrentDevice(): OutputProfile {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.firstOrNull { device ->
            profiles.any { it.deviceMatcher.matches(device) }
        }?.let { matchedDevice ->
            profiles.first { it.deviceMatcher.matches(matchedDevice) }
        } ?: profiles.first { it.id == "default" }
    }

    fun getAllProfiles(): List<OutputProfile> = profiles.toList()

    suspend fun addProfile(profile: OutputProfile) {
        profiles.removeAll { it.id == profile.id }
        profiles.add(profile)
        saveProfiles()
    }

    suspend fun removeProfile(profileId: String) {
        profiles.removeAll { it.id == profileId }
        saveProfiles()
    }

    suspend fun setActiveProfile(profileId: String?) {
        val profile = profileId?.let { id -> profiles.firstOrNull { it.id == id } } ?: profiles.firstOrNull { it.id == "default" }
        _activeProfile.value = profile
        if (profile != null) {
            dataStore.edit { it[ACTIVE_PROFILE_KEY] = profile.id }
        } else {
            dataStore.edit { it.remove(ACTIVE_PROFILE_KEY) }
        }
    }

    private suspend fun saveProfiles() = withContext(Dispatchers.IO) {
        val jsonString = json.encodeToString(profiles)
        dataStore.edit { it[OUTPUT_PROFILES_KEY] = jsonString }
    }
}