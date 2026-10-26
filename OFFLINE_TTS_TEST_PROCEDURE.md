# Offline TTS Test Procedure

**Purpose:** Verify Murmur's Read Aloud works offline with Strict Offline Speech enabled, and correctly handles all voice network requirement states.

**Prerequisites:**
- Physical Android device (API 26+), not emulator
- ADB access
- Fresh app install: `./gradlew installDebug`
- Device has at least one TTS engine installed (Google Speech Recognition & Synthesis recommended)

---

## Test Environment Setup

```bash
# 1. Install debug build
./gradlew installDebug

# 2. Verify no INTERNET permission in merged manifest
aapt dump badging app/build/outputs/apk/debug/app-debug.apk | grep -i permission
# Expected: NO android.permission.INTERNET listed

# 3. Disable all network (airplane mode)
adb shell settings put global airplane_mode_on 1
adb shell am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true

# 4. Verify network is disabled
adb shell ping -c 1 8.8.8.8 2>&1 | head -5
# Expected: Network unreachable or 100% packet loss

# 5. Launch app
adb shell am start -n com.joy.murmur/.MainActivity
```

---

## Test Matrix

| # | Test Case | Setup | Steps | Expected Result |
|---|-----------|-------|-------|-----------------|
| 1 | **Known-offline voice works** | Enable Google TTS offline voice for en-US (Settings → System → Languages → Text-to-speech → Install voice data) | Airplane mode ON → Open Reader → Add TXT document → Tap "Read from here" | Speech plays; no network traffic; voice shows "Known offline" |
| 2 | **Network-required voice blocked** | Select Google cloud voice (if available) or any voice showing "Network required" | Enable Strict Offline Speech (default) → "Read from here" | Error: "The selected system voice requires a network connection. Turn off Strict Offline Speech to use it." |
| 3 | **Unknown voice blocked** | Select voice with unknown status (may require specific device/engine) | Enable Strict Offline Speech → "Read from here" | Error: "This system voice's network requirement is unknown. Turn off Strict Offline Speech only if you accept that it may use a network." |
| 4 | **Missing voice data detected** | Uninstall voice data for selected voice (Settings → TTS → Voice data → Delete) | "Read from here" | Error: "Voice data is missing. Install voice data while online or choose a different voice." + "Open TTS Settings" button offered |
| 5 | **Unavailable engine handled** | Disable all TTS engines (Settings → Apps → TTS engine → Disable) | Open Reader | Error: "The system speech engine isn't available on this device." |
| 6 | **Airplane mode after voice install** | Install voice data → enable airplane mode | "Read from here" | Speech plays offline; no network traffic |
| 7 | **No silent fallback** | Network voice selected, Strict Offline ON | "Read from here" | Error shown; **never** switches to network voice automatically |
| 8 | **Position preserved on error** | Start Read Aloud → error occurs (missing data) → fix voice → retry | Position unchanged | Reading resumes from same character position |
| 9 | **Fresh sentence not cached** | Use unique text not previously synthesized | "Read from here" | Speech generated (not from cache); WAV header valid; duration > 0 |
| 10 | **Strict Offline OFF allows network voice** | Network voice selected, Strict Offline OFF | "Read from here" | Warning shown: "This voice may use a network connection; Murmur itself does not connect" → speech plays |

---

## Verification Commands

```bash
# Check app has no network traffic during test
adb logcat -s SystemTtsSpeechGenerator -v brief

# Verify no INTERNET permission
adb shell dumpsys package com.joy.murmur | grep -i "requested permissions:" -A 20

# Check TTS engine status
adb shell dumpsys texttospeech

# Monitor network connections from app
adb shell netstat -tunp 2>/dev/null | grep com.joy.murmur
# Expected: No ESTABLISHED connections during offline tests
```

---

## Device-Specific Differences to Document

| Device / Android Version | TTS Engine | Offline Voices Available | Known Issues |
|--------------------------|------------|-------------------------|--------------|
| Pixel 8 / Android 14 | Google TTS | en-US, en-GB, fr-FR, de-DE, es-ES, it-IT, ja-JP, ko-KR, zh-CN | None |
| Samsung Galaxy S23 / Android 14 | Samsung TTS + Google | Samsung voices + Google | Samsung voices may report UNKNOWN |
| Xiaomi 13 / Android 13 | Xiaomi TTS + Google | Limited | May require Mi Account for some voices |
| Emulator (API 34) | Google TTS | en-US only | No network simulation in airplane mode |

**Record for each test device:**
- Make / Model
- Android version / API level
- Security patch date
- TTS engines installed
- Which voices show KNOWN_OFFLINE / NETWORK_REQUIRED / UNKNOWN / MISSING_DATA

---

## Pass Criteria

All Test Matrix cases (1-10) must pass on at least **2 physical devices** with different OEMs.

**Failures that block release:**
- Any silent fallback to network voice (Test 7)
- INTERNET permission present in merged manifest
- Position not preserved after error (Test 8)
- Missing voice data not detected (Test 4)
- App crashes on any test case

---

## Evidence Collection

For each test run, record:

```
Date/time:
Device: <model> <Android version>
Commit: <git rev-parse HEAD>
Test cases passed: <list>
Test cases failed: <list with log excerpts>
Known limitations: <device-specific behaviors>
APK SHA-256: <sha256sum app/build/outputs/apk/debug/app-debug.apk>
```