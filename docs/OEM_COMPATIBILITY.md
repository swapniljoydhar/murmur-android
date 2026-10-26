# Murmur OEM Compatibility

## Background Playback Health States

- **HEALTHY**: Background playback should work normally
- **POTENTIALLY_RESTRICTED**: Some restrictions detected, playback may stop when screen is off
- **LIKELY_RESTRICTED**: Heavy restrictions detected, background playback will likely fail
- **UNKNOWN**: Unable to determine restriction status

## Detection Signals

- Battery optimization settings
- Power saver mode
- App restricted state
- Foreground service state
- MediaSession state
- Player state
- Notification state
- Service lifecycle
- Process lifecycle
- Recent interruption
- Manufacturer/model

## OEM Fix Screen

Shows:
- Manufacturer
- Model
- Android version
- OEM skin (where applicable)
- Notification state
- Battery optimization state
- Playback-service state
- Compatibility state

## Vendor Intents

Uses vendor-specific settings intents where reliably tested:
- Samsung, Xiaomi, OPPO, Vivo, Motorola, Nothing, OnePlus

Generic Android Settings intents used as fallback.

## Never

- Claim OEM kill was detected with certainty without reliable evidence
- Use undocumented OEM exploits
- Use fake foreground services or wake-lock abuse

## Test Matrix

Tested on: Samsung One UI, Xiaomi HyperOS, Redmi, POCO, OPPO ColorOS, OnePlus OxygenOS, realme UI, Vivo, Motorola/Lenovo, Nothing, Meizu Flyme, Tecno, Infinix, stock-like Android

Huawei devices are tested to distinguish between standard Android APK support and non-Android-compatible ecosystems.