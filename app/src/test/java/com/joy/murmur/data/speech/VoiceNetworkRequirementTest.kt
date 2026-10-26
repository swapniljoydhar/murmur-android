package com.joy.murmur.data.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceNetworkRequirementTest {
    @Test
    fun voiceRequiringDownloadedDataIsNotClassifiedAsOfflineReady() {
        assertEquals(
            VoiceNetworkRequirement.MISSING_DATA,
            classifyVoiceNetworkRequirement(requiresNetwork = false, requiresInstall = true),
        )
    }

    @Test
    fun networkRequirementTakesPrecedenceWhenBothConditionsAreReported() {
        assertEquals(
            VoiceNetworkRequirement.NETWORK_REQUIRED,
            classifyVoiceNetworkRequirement(requiresNetwork = true, requiresInstall = true),
        )
    }

    @Test
    fun fullyInstalledOnDeviceVoiceIsClassifiedOfflineReady() {
        assertEquals(
            VoiceNetworkRequirement.KNOWN_OFFLINE,
            classifyVoiceNetworkRequirement(requiresNetwork = false, requiresInstall = false),
        )
    }

    @Test
    fun strictOfflineRejectsVoiceThatMayDownloadOrSubstituteData() {
        assertEquals(
            "This voice needs additional data and may download or substitute another voice. Install its data before using it with Strict Offline Speech.",
            strictOfflineVoiceFailure(VoiceNetworkRequirement.MISSING_DATA, strictOffline = true),
        )
    }

    @Test
    fun nonStrictModeAllowsVoiceDataInstallation() {
        assertEquals(null, strictOfflineVoiceFailure(VoiceNetworkRequirement.MISSING_DATA, strictOffline = false))
    }
}
