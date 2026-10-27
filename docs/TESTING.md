# Murmur Testing

## Validation snapshot (2026-10-27)

- `:app:assembleDebug`: passed for the current working tree.
- `:app:testDebugUnitTest`: passed (64+ unit tests).
- `:app:lintDebug`: 0 errors.
- `:app:assembleDebugAndroidTest`: passed; instrumentation APK compilation includes new `ReadAloudStressTest`.
- `:app:assembleRelease`: passed, unsigned APK builds, ProGuard/R8 no reflection crashes.
- The generated debug/release merged manifests were inspected: no `android.permission.INTERNET`, no `android.permission.SCHEDULE_EXACT_ALARM`, no `android.permission.POST_NOTIFICATIONS`; only `ACCESS_NETWORK_STATE` (acceptable from transitive deps).
- PDF instrumentation tests were not run. No Android device was connected. PDF extraction/search/read-aloud therefore remain device-unverified.

## Unit Test Coverage

- Markdown Read Aloud mapping: preserves source indexes while removing spoken markup and silencing fenced code blocks. Verifies pure text transformation only; does not verify TTS playback or reader position restoration on device.
- TTS cache: put/get, pin/releasePin, eviction respects max size, eviction preserves pinned files.
- Playback restore policy: transient-start filtering, paused state restoration, position retention/fallback.
- Audiobook position slider: normalized slider round-trips distant positions in 237-hour audiobooks.
- Read Aloud mapping: spoken index map building, inverse mapping, tap-to-segment offset mapping (syntax vs spoken chars).
- Sleep timer: remaining-time clamping.
- Read Aloud stress test (integration): rapid seeks across 50+ segments → no FileNotFoundException, voice change cache consistency, concurrent synthesis and eviction protection.
- EPUB parser: chapter parsing.
- Document source fingerprint: position validation.
- Vorbis/ID3 chapter mapping.

## Service & Reader Integration (Not Device-Verified)

- The reader observes persisted document-position changes so it can follow Read Aloud segment transitions.
- Service-side Read Aloud generation failures publish a document-scoped status with a retry path in the reader; the player screen directs the user back to the document.
- All `SystemTtsSpeechGenerator` instances share a process-wide mutex around voice changes, synthesis, and cache eviction.
- Cache pinning in `PlaybackService.onMediaItemTransition` (pin) and `Player.STATE_ENDED` (releasePin) protects currently playing/queued segments.
- Service restoration drops transient `read_aloud_start|...` queue markers; restored playback stays paused.
- Player-side speech failures save the current segment's document offset and report a route back to the reader.
- Read Aloud service generation performs document and TTS work on IO while all ExoPlayer reads and mutations run on the player main looper.
- `ReadAloudPositionWriteTracker` coalesces position writes per document.

## Long-Form Testing (Not Device-Verified)

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

## Process Death Testing (Not Device-Verified)

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

## Storage Full Testing (Not Device-Verified)

Fill storage during TTS generation:
- Stop safely
- Preserve completed work
- Preserve source documents
- Preserve playback/reading state
- Explain the problem
- Allow cleanup/retry

## Rapid Interaction Testing (Not Device-Verified)

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

## OEM Testing (Not Device-Verified)

Test background playback across representative OEM devices:
- Screen locked
- Battery saver
- Background restrictions
- Bluetooth
- Wired output
- App swipe-away
- Process recreation

Document failures.
