# Murmur Offline & TTS Requirements Review

**Date:** 2026-10-24  
**Status:** Pre-implementation review for Read Aloud hardening  
**Scope:** Audit current `SystemTtsSpeechGenerator`, settings, UI, manifest, and dependencies against the stated product guarantee.

---

## 1. Executive Summary

The codebase already implements a **strong foundation** for offline-first Read Aloud. The architecture correctly:
- Uses Android's `TextToSpeech` API with `Voice.isNetworkConnectionRequired()` 
- Defaults `Strict Offline Speech` to `true`
- Has no `INTERNET` permission in the manifest
- Caches synthesized speech locally with atomic writes
- Shows voice network status in UI

**Critical gaps** remain in: **Neural TTS model download capability** (exists but commented), **missing test procedure**, **voice data missing detection**, and **edge-case handling for unavailable/unknown voices**.

---

## 2. Requirement-by-Requirement Audit

### 2.1 Core Offline Guarantee (No INTERNET Permission)

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Manifest lacks `android.permission.INTERNET` | ✅ PASS | `app/src/main/AndroidManifest.xml:2-62` — only FOREGROUND_SERVICE, READ_MEDIA_AUDIO, READ_EXTERNAL_STORAGE |
| Merged manifest audit | ⚠️ NEEDS VERIFICATION | Not yet run; must run `./gradlew assembleDebug` + `aapt dump badging` on merged manifest |
| No core feature depends on remote service | ✅ PASS (System TTS) | System TTS calls installed engine; Murmur has no network code in `SystemTtsSpeechGenerator` |
| No hidden cloud TTS integration | ⚠️ PARTIAL | `NeuralTtsModelManager` contains download logic from GitHub (lines 23-25, 122-179) but Sherpa-ONNX is commented in `build.gradle.kts:70` and `libs.versions.toml:21,50` |

**Action:** Remove or isolate `NeuralTtsModelManager.downloadModel()` entirely from the core product. If Neural TTS is kept as optional research, it must be behind a separate module with explicit user opt-in and no automatic downloads.

### 2.2 Android TextToSpeech API Support

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Discover installed engines | ✅ PASS | `SystemTtsSpeechGenerator.availableVoices()` uses `engine.voices` |
| No hard-coded vendor assumptions | ✅ PASS | Code uses only standard Android TTS APIs |
| Inspect `Voice.isNetworkConnectionRequired()` | ✅ PASS | `voiceNetworkRequirement():102-107` checks `voice.isNetworkConnectionRequired` |
| Distinguish missing voice data | ❌ MISSING | No detection of `TextToSpeech.ERROR_NOT_INSTALLED` or voice data missing state |
| Distinguish unavailable voices | ⚠️ PARTIAL | `UNAVAILABLE` returned only if engine init fails; no per-voice availability check |

**Gap:** Android TTS engines can report voices that exist but lack downloaded data. The current code treats `voice.isNetworkConnectionRequired` as the only signal. Need to handle `TextToSpeech.ERROR_NOT_INSTALLED` during synthesis and map to "voice data missing" state.

### 2.3 Strict Offline Speech Enabled by Default

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Default `true` | ✅ PASS | `DataStoreSettingsRepository:29-31` defaults to `true`; `SettingsRepository:16` defaults `flowOf(true)` |
| Select only suitable offline voices | ✅ PASS | `synthesize():130-135` throws on `NETWORK_REQUIRED`/`UNKNOWN`/`UNAVAILABLE` when `strictOffline=true` |
| No silent fallback to network voice | ✅ PASS | Explicit exceptions thrown; no automatic voice switching |
| No automatic voice-data/model downloads | ✅ PASS (System TTS) | `SystemTtsSpeechGenerator` has no download logic |
| Explain missing offline voice | ❌ MISSING | UI shows error but no guided "install voice data" action |
| Preserve reading position on failure | ⚠️ PARTIAL | Position saved in `ReaderScreen:976-975` but not explicitly on synthesis failure |
| Treat uncertain as Unknown | ✅ PASS | `VoiceNetworkRequirement.UNKNOWN` used when `voice` is null or indeterminate |

**Gap:** When synthesis fails due to missing voice data, the user sees a generic error. Should offer to open Android TTS settings (`Settings.ACTION_TEXT_TO_SPEECH_SETTINGS`) and preserve `position` for retry.

### 2.4 No Hidden Network-Dependent TTS

| Requirement | Status | Evidence |
|-------------|--------|----------|
| No Google Cloud TTS / remote synthesis API | ✅ PASS | Not integrated |
| System TTS acceptable (calls installed engine) | ✅ PASS | `SystemTtsSpeechGenerator` wraps platform TTS |
| Neural TTS runs locally | ⚠️ RISK | `NeuralTtsModelManager.downloadModel()` downloads from GitHub; Sherpa-ONNX commented out but code exists |
| Imported model validated & licensed | ⚠️ PARTIAL | `verifyModelFiles()` checks checksums (lines 81-92); `ModelManifest` includes `licenses` field |
| No required server download | ❌ VIOLATION | `NeuralTtsModelManager.MODEL_INDEX_URL` and `MODEL_BASE_URL` point to GitHub releases |

**Action:** Either remove `NeuralTtsModelManager.downloadModel()` entirely, or move Neural TTS to a separate optional module with clear "requires internet to download models" disclosure. The core product must not contain code that downloads models from the internet.

### 2.5 Offline Verification Test Procedure

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Repeatable test with Wi-Fi/data disabled | ❌ MISSING | No documented test procedure exists |
| Test fresh sentence (not cached) | ❌ MISSING | `VALIDATION.md:84-91` lists scenarios but no executable procedure |
| Test suitable Google offline voice | ❌ MISSING | Not documented |
| Test other installed engine | ❌ MISSING | Not documented |
| Test missing voice data | ❌ MISSING | Not implemented in code; not tested |
| Test network-required voice | ❌ MISSING | Not documented |
| Test unknown/unavailable status | ❌ MISSING | Not documented |
| Test airplane mode after voice install | ❌ MISSING | Not documented |
| Verify no silent fallback | ❌ MISSING | Not documented |
| Test actual synthesis (not just discovery) | ❌ MISSING | Not documented |
| Document device-specific differences | ❌ MISSING | Not documented |

**Action:** Create `OFFLINE_TTS_TEST_PROCEDURE.md` with step-by-step adb commands and expected outcomes.

### 2.6 User-Facing Promise

| Requirement | Status | Evidence |
|-------------|--------|----------|
| Describe as local-first, offline Read Aloud with supported voice | ✅ PASS | `README.md:38`, `docs/ARCHITECTURE.md:46` |
| Don't claim every system voice is offline | ✅ PASS | UI shows "Network required" / "Unknown" labels |
| Don't claim every device includes offline voice | ⚠️ NEEDS REVIEW | Settings screen text should clarify this |
| Don't guarantee third-party engine behavior | ✅ PASS | UI shows "This voice may use a network connection; Murmur itself does not connect" |

---

## 3. Specific Code Issues to Fix

### 3.1 `SystemTtsSpeechGenerator.kt` — Missing Voice Data Detection

```kotlin
// Current: only checks voice.isNetworkConnectionRequired
// Missing: handling TextToSpeech.ERROR_NOT_INSTALLED during synthesis

// In synthesizeChunk(), onError() should distinguish:
override fun onError(utteranceId: String, errorCode: Int) {
    when (errorCode) {
        TextToSpeech.ERROR_NOT_INSTALLED -> 
            fail(utteranceId, VoiceDataMissingException("Voice data not installed"))
        TextToSpeech.ERROR_NETWORK -> 
            fail(utteranceId, NetworkRequiredException("Voice requires network"))
        // ...
    }
}
```

### 3.2 `NeuralTtsModelManager.kt` — Remove Network Download Capability

**Option A (Recommended):** Delete `downloadModel()`, `downloadFile()`, `MODEL_INDEX_URL`, `MODEL_BASE_URL`. Keep only local model registration/validation.

**Option B:** Move entire `NeuralTtsSpeechGenerator` + `NeuralTtsModelManager` to a separate `neural-tts` module that is **not included** in the default build. Require explicit user action to enable.

### 3.3 `ReaderScreen` (MurmurApp.kt) — Improve Error Handling & Guidance

```kotlin
// Current: generic speechError message
// Needed: specific handling for voice data missing
catch (e: VoiceDataMissingException) {
    speechError = "Voice data not installed. Open TTS settings to download it."
    // Offer button: Intent(Settings.ACTION_TEXT_TO_SPEECH_SETTINGS)
}
catch (e: NetworkRequiredException) {
    speechError = "This voice requires internet. Choose an offline voice or disable Strict Offline Speech."
}
```

### 3.4 `VoiceNetworkRequirement` — Add `MISSING_DATA` State

```kotlin
enum class VoiceNetworkRequirement { 
    KNOWN_OFFLINE, 
    NETWORK_REQUIRED, 
    UNKNOWN, 
    UNAVAILABLE,
    MISSING_DATA  // NEW: voice exists but data not downloaded
}
```

### 3.5 Settings Persistence on Voice Change

`VALIDATION.md:50` requires: "Verify Strict Offline Speech defaults enabled, persists, and is reevaluated after voice changes or engine updates."

Current: `LaunchedEffect(speechGenerator, selectedSpeechVoice)` refreshes voices but doesn't re-check `voiceNetworkRequirement` after engine updates. Add a periodic or foreground re-check.

---

## 4. Test Procedure Specification (To Be Created)

Create `OFFLINE_TTS_TEST_PROCEDURE.md` with:

```markdown
# Offline TTS Test Procedure

## Prerequisites
- Physical device (not emulator) with Android 10+
- ADB access
- Fresh app install (`./gradlew installDebug`)

## Test Matrix

| Test Case | Setup | Steps | Expected |
|-----------|-------|-------|----------|
| 1. Known-offline voice | Enable Google TTS offline voice for en-US | Disable Wi-Fi/data → Open Reader → "Read from here" | Speech plays; no network traffic |
| 2. Network-required voice | Select Google cloud voice (if available) | Enable Strict Offline → "Read from here" | Error: "requires network" |
| 3. Unknown voice | Select voice with unknown status | Enable Strict Offline → "Read from here" | Error: "unknown network requirement" |
| 4. Missing voice data | Uninstall voice data for selected voice | "Read from here" | Error: "Voice data not installed" + open TTS settings button |
| 5. Unavailable engine | Disable all TTS engines | Open Reader | Error: "System speech engine unavailable" |
| 6. Airplane mode after install | Install voice data → enable airplane mode | "Read from here" | Speech plays offline |
| 7. No silent fallback | Network voice selected, Strict Offline ON | "Read from here" | Error shown; never switches to network voice |
| 8. Position preserved | Start Read Aloud → error → fix voice → retry | Position unchanged | Reading resumes from same position |

## Verification Commands
```bash
# Disable network
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE

# Verify no network traffic from app
adb shell dumpsys package com.joy.murmur | grep -i internet
adb logcat -s SystemTtsSpeechGenerator

# Check merged manifest
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep permission
```
```

---

## 5. Priority Action Items

| Priority | Item | File | Effort |
|----------|------|------|--------|
| **P0** | Remove `NeuralTtsModelManager.downloadModel()` and network URLs | `NeuralTtsModelManager.kt` | 30 min |
| **P0** | Add `MISSING_DATA` to `VoiceNetworkRequirement` + handle `ERROR_NOT_INSTALLED` | `SystemTtsSpeechGenerator.kt` | 1 hr |
| **P0** | Create `OFFLINE_TTS_TEST_PROCEDURE.md` | New file | 1 hr |
| **P1** | Add "Open TTS Settings" action for missing voice data | `MurmurApp.kt` (ReaderScreen) | 30 min |
| **P1** | Preserve reading position on synthesis failure | `MurmurApp.kt` | 15 min |
| **P1** | Re-check voice network status on engine update/foreground | `MurmurApp.kt` | 30 min |
| **P2** | Clarify "not every device has offline voice" in Settings UI | `MurmurApp.kt` (AppearanceSettingsScreen) | 15 min |
| **P2** | Run merged manifest audit (`aapt dump badging`) | CI/local | 10 min |

---

## 6. Architecture Consistency Check

The implementation correctly follows the existing architecture:
- **Single playback owner**: `PlaybackService` (Media3) — Read Aloud uses same pipeline
- **Bounded synthesis**: `TtsScheduler` limits concurrent generations (3) and look-ahead (60s)
- **Atomic cache**: `SystemTtsSpeechGenerator` writes `.part` → validates → renames
- **Source offsets**: `SpeechChunk.sourceOffset` maps segment → source position
- **Strict Offline default**: Enforced at synthesis time, not discovery time

No speculative abstractions or new layers needed. The fixes are surgical improvements to existing components.

---

## 7. Sign-Off Criteria

Before merging Read Aloud implementation:

- [ ] `NeuralTtsModelManager.downloadModel()` removed or isolated
- [ ] `VoiceNetworkRequirement.MISSING_DATA` implemented and tested
- [ ] `OFFLINE_TTS_TEST_PROCEDURE.md` created and executed on ≥2 devices
- [ ] Merged manifest verified free of `INTERNET` permission
- [ ] All test matrix cases pass (Section 4)
- [ ] Device-specific limitations documented
- [ ] Settings UI clarifies "offline voice availability varies by device"

---

**Prepared by:** Automated review based on source audit  
**Next step:** Execute P0 items, then run test procedure on physical devices.