# Murmur Read Aloud

## Implemented path

The reader asks `SystemTtsSpeechGenerator` to synthesize the current document page with Android's installed `TextToSpeech` engine. Generated WAV files are cached under the app cache directory and queued through the shared Media3 playback service. The service can synthesize the next page for continuous reading and saves a document character offset (or PDF page index) at segment boundaries and playback lifecycle events.

Voice selection and synthesis are serialized across generator instances in the process because the Activity, reader, and playback service can each own an engine instance while sharing the cache. Cache files are keyed with SHA-256 over synthesis identity, voice identity, and chunk text. Cache eviction is size/age based and avoids deleting files currently referenced by the playback queue.

## Strict Offline Speech

When Strict Offline Speech is enabled, Murmur rejects a voice reported as network-required, a voice whose network requirement is unknown, and missing voice data. Android's TTS service supplies the voice metadata and performs synthesis; behavior can differ by engine and installed voice data. An uncached offline synthesis run must be verified on a device before making a device-specific claim.

## Limits and deferred code

- Reader following and recovery are segment-level. Murmur does not highlight the active sentence or continuously synchronize scroll position to audio time.
- The optional neural generator is not implemented. Neural manifest/settings models do not provide a synthesis engine or model-management UI.
- The separate `TtsScheduler` and `BatchTtsManager` classes are not constructed by the app. `TtsScheduler` currently has no source-text provider (`extractSectionText()` returns empty text) and closes its queue on seek; do not integrate it as-is.
- Batch/full-book export, neural model downloads, cloud speech, and OCR are not part of the current implementation.
