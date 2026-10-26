# Murmur Testing

## Validation snapshot (2026-10-26)

- `:app:assembleDebug`: passed for the current PDF/read-aloud working tree.
- `:app:testDebugUnitTest`: passed for the current working tree.
- `:app:assembleDebugAndroidTest`: passed; instrumentation APK compilation only.
- PDF instrumentation tests were not run. No Android device was connected, and the configured API 37 emulator image is missing. PDF extraction/search/read-aloud therefore remain device-unverified.
- The generated debug merged manifest was inspected: it contains no `android.permission.INTERNET`; it does contain `ACCESS_NETWORK_STATE` (which does not itself grant network access).

PDF-specific cases added: generated text PDF extraction, search result page mapping, and an image/blank-text page returning no extracted text. These are test cases, not device execution results.

Markdown Read Aloud mapping has unit cases for preserving source indexes while removing spoken markup and silencing fenced code blocks. These verify the pure text transformation only; they do not verify TTS playback or reader position restoration on device.

The reader now observes persisted document-position changes so it can follow Read Aloud segment transitions. This Compose/service interaction has not been exercised on a device.

Service-side Read Aloud generation failures now publish a document-scoped status with a retry path in the reader; the player screen directs the user back to the document. Unit tests cover failure status scoping and fallback messaging. End-to-end failure/retry behavior still requires device validation.

All `SystemTtsSpeechGenerator` instances now share a process-wide mutex around voice changes, synthesis, and cache eviction because the activity, reader, and playback service use the same cache directory. Concurrent multi-instance cache behavior still needs device stress validation.

Service restoration drops transient `read_aloud_start|...` queue markers and no longer auto-generates or starts near-end Read Aloud playback during restore. Restored playback stays paused until an explicit play action. Player-side speech failures save the current segment's document offset and report a route back to the reader. These service lifecycle paths require process-death/device validation.

Playback restore policy unit cases verify transient-start filtering, paused state restoration, and position retention/fallback. They test the policy only, not Media3 service lifecycle behavior.

A service-owned fixed-duration sleep timer is reachable from music, audiobook, and Read Aloud player screens. It supports 15, 30, 45, and 60 minute presets, persists its deadline across service recreation, and pauses instead of toggling playback at expiry. A unit case verifies remaining-time clamping. Expiry while backgrounded, service recreation, and process-death behavior have not been exercised on a device. Chapter-end and custom-duration modes are not part of this implementation.

Read Aloud service generation now performs document and TTS work on IO while all ExoPlayer reads and mutations run on the player main looper. Source checks after synthesis prevent stale generation from replacing a newer queue. This threading correction compiles, but continuation under real playback still needs device verification.

## Long-Form Testing

### Audiobooks
- 1 hour, 24 hours, 100 hours, 200+ hours
- Very large chapter counts

### Documents
- 1 MB, 10 MB, 50+ MB
- Huge EPUB, HTML, PDF
- Millions of logical sentences

### TTS
- Short, several hours equivalent
- 50-hour equivalent, 200-hour equivalent
- Large synthetic workloads
- Interrupted jobs, resumed jobs
- Voice changes, cache eviction, far seeking

## Process Death Testing

Kill process during:
- Audiobook playback
- TTS playback
- TTS generation
- Batch export
- Document parsing
- Library indexing
- Cache writes

After restart:
- Durable state remains valid
- Completed work remains completed
- Incomplete work marked correctly
- Playback can reconnect
- Reader location recoverable

## Storage Full Testing

Fill storage during TTS generation:
- Stop safely
- Preserve completed work
- Preserve source documents
- Preserve playback/reading state
- Explain the problem
- Allow cleanup/retry

## Rapid Interaction Testing

- Play then immediate pause
- Rapid next/previous
- Queue modification while playing
- TTS start then sentence jump
- Repeated sentence jumps
- Voice change
- Rotation during TTS
- Process death during TTS
- Output disconnect/reconnect
- Deletion of current source
- Cache cleanup during playback
- Storage exhaustion during generation

## OEM Testing

Test background playback across representative OEM devices:
- Screen locked
- Battery saver
- Background restrictions
- Bluetooth
- Wired output
- App swipe-away
- Process recreation

Document failures.
