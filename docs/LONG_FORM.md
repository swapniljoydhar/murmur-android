# Murmur Long-Form Architecture

## Long Audiobooks (200+ Hours)

Murmur treats long audiobooks as a first-class use case. A 200+ hour M4B remains one logical audiobook unless the user explicitly requests splitting.

### File Handling

- Uses Media3/ExoPlayer to seek within the source file
- Does NOT preload or decode the entire audiobook
- Does NOT create artificial chunks for ordinary playback
- Does NOT repeatedly scan the whole source

### Time Types

All time values use `Long` (milliseconds) to support durations beyond `Int.MAX_VALUE`:
- Duration
- Position
- Chapter timestamps
- Sleep-timer calculations

### Chapters

Uses actual chapter metadata from the container. If the platform exposes chapters inadequately, a focused container parser may be added:
- Narrow scope only
- Input validation
- Random-access reads (no full-file memory loading)
- Malformed container testing

### Resume

Per-book persistence of:
- Position (Long, milliseconds)
- Current chapter (when available)
- Completion state
- Playback preferences

Book A and Book B never share one global position.

## Huge Documents

Designed for very large TXT, EPUB, HTML, and PDF documents:

### Strategies

- **Incremental parsing**: Parse only what's needed for the current view
- **Lazy rendering**: Render only visible content
- **Structural indexing**: Compact on-disk index for navigation
- **Bounded working sets**: Never load entire documents into memory

### Character Limits

`maxSpeechInputLength()` is respected for TTS generation. Speech segments are split at sentence boundaries without exceeding engine limits.

## Speech Segmentation

A `SpeechSegment` may contain one sentence or multiple short sentences. Segmentation respects:
- Engine character limits
- Token limits
- Phoneme limits
- Practical latency limits

Every speech segment retains explicit mapping to document structure (documentId, sectionId, paragraphId, sentence IDs).

## Internal TTS Buffer

Bounded look-ahead window:
- Targets tens of seconds to a few minutes of buffered speech
- Based on engine speed, device performance, cache state, battery conditions
- Never pre-generates hundreds of hours for normal Read Aloud

## Cache

Generated speech is cached locally:
- Independently addressable cache units
- SHA-256 keys incorporating voice, engine, locale, settings
- Atomic writes (temp file → rename → commit metadata)
- Bounded storage with LRU eviction

## Cache Pinning / Leases

Cache cleanup never deletes:
- Currently playing speech
- Next speech in queue
- Speech actively being read
- Speech needed by transitions
- Speech currently being generated

## Batch TTS Generation

For full book text-to-speech generation:

### Job System

- Job → Work units → Checkpoint → Pause/Resume → Next work unit
- Work units are chapters/sections (small enough that losing one is inexpensive)
- Never: "Entire 237-hour book = one WorkRequest"

### Resumption

- Durable progress persisted after each work unit
- Process death resumes from last completed work unit
- No regeneration of completed chapters

### Android Background Limits

- Respects 6-hour/24-hour mediaProcessing foreground service limit (Android 15+)
- Respects WorkManager job quota (Android 16+)
- Never promises uninterrupted 200+ hour background execution

## Recovery

Process death during:
- Audiobook playback → Position restored from Room
- TTS playback → Position restored from Room
- TTS generation → Work units checkpointed, resume from last completed
- Batch export → Checkpointed, resume from last completed unit
- Document parsing → Isolated per-file, other content unaffected
- Cache writes → Atomic writes prevent corruption

## Performance

- No O(total-duration) startup behavior
- Document/audiobook duration does not slow normal navigation
- Cold startup, warm startup, library-open time all bounded

## Known Limitations

- Neural TTS requires optional Sherpa-ONNX AAR (license audit required)
- 200+ hour batch generation respects Android background limits
- PDF text extraction is best-effort (no OCR in v1)