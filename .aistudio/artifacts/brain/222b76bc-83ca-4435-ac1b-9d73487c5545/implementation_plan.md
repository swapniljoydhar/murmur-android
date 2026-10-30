# Murmur Architecture Cleanup, Editorial Home Redesign & Playback Upgrade

Transform Murmur from a stark prototype with dead background scaffolding into a cohesive, warm editorial offline audio-and-reading sanctuary—eliminating confirmed bugs and security gaps while delivering a live resume Home hub, `DocumentFile` library importing, a full-featured Material 3 Mini-Player, `MediaStyle` system notifications, and a custom ExoPlayer scrubbing bar.

## User Review & Critical Decisions

> [!IMPORTANT]
> All four product and architecture choices from Phase 1 are locked in below. Zero source files have been modified; implementation begins only when you click **Proceed** (or reply **go**).

- **Confirmed Decision 1 (Subsystem Hygiene)**: Delete the unused, broken TTS lookahead/batch scheduler classes, unwired DSP audio processor scaffolding, and duplicate output-profile manager so every remaining control and setting corresponds to live, verified audio behavior.
- **Confirmed Decision 2 (Editorial Home Aesthetic)**: Replace the gray three-box Continue screen with a **Warm Editorial Hub** featuring a live "Continue Listening / Reading" hero card, recent library shelves, quick import actions, and locally bundled editorial serif + humanist sans typography.
- **Confirmed Decision 3 (Offline Privacy & Legal Posture)**: Add a dedicated offline **Privacy & Legal Notice** screen and repository privacy documentation confirming zero network permissions, zero telemetry, and zero personal data collection—without adding an intrusive age-gate modal.
- **Confirmed Decision 4 (Local Music Background Refresh)**: Retain the optional daily `WorkManager` local MediaStore scan toggle alongside manual library refresh and direct SAF file/folder imports.

---

## 1. Overview & Core Concept

- **What It Does**: Provides a calm, 100% offline Android player and reader for local music, long-form audiobooks, and documents (TXT, Markdown, HTML, EPUB, PDF) with system Read Aloud—complete with instant resume, folder/file importing, persistent bottom mini-player transport, interactive scrubbing, and system lock-screen media controls.
- **Target Audience / Persona**: Privacy-conscious listeners and readers who keep music, audiobooks, and books on their own device and expect reliable position memory, honest offline controls, and a refined reading-room interface.
- **Key Value**: Replaces broken/unwired scaffolding and a lifeless gray landing screen with trustworthy persistence, exact WAV cache restoration across process restarts, hardened local security boundaries, and tactile Material 3 playback ergonomics.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Editorial Home & One-Tap Resume**: Opening Murmur greets the user with a live **Continue Hero Card** showing the active or last-played track, audiobook, or Read Aloud document, artwork/category emblem, elapsed/remaining time, progress bar, and an immediate Resume/Pause button. When nothing has been played yet, a warm illustrated welcome card offers one-tap **Import Audio** and **Open Document** actions. Below the hero card sit rich **Music**, **Audiobooks**, and **Read & Listen** sanctuary cards with live item counts and quick-add triggers.
  2. **`DocumentFile` File & Folder Import**: In Music and Audiobooks (and directly from Home quick actions), users can pick either individual audio files or an entire local folder tree via Android Storage Access Framework (`DocumentFile`). Murmur persists read permissions, recursively discovers supported audio files inside selected folders, extracts metadata/duration/artwork, and adds them straight to the local Room library.
  3. **Persistent Material 3 Mini-Player**: Whenever an item is loaded in the queue, a docked surface-elevated Mini-Player bar sits above the system navigation inset (`WindowInsets.navigationBars`), displaying a thin live progress track, artwork/type badge, title & subtitle, and accessible 48dp **Previous**, **Play/Pause**, and **Next** transport controls. Tapping the bar opens the full Now Playing / Audiobook / Read Aloud sheet.
  4. **Custom Interactive Playback Seek Bar & `MediaStyle` Notification**: Inside Now Playing, Audiobook Player, and Read Aloud screens, a custom scrubbing bar tracks ExoPlayer's real-time `positionMs` and `durationMs` smoothly, previews target timestamps during drag without playback jitter, and commits seeks cleanly on release (with `Long`-safe math for 200+ hour audiobooks). Outside the app, Media3's `MediaStyle` foreground notification exposes Play/Pause, Skip Previous/Next, scrubbing, and a `PendingIntent` session activity that deep-links directly back into Murmur's player view.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: **Warm Editorial Sanctuary**—inspired by linen book cloth, warm paper, and brushed brass/deep teal ink rather than flat gray utility boxes.
  - *Color Palette & Mood*:
    - **Light Mode (Warm Paper & Deep Teal Ink)**: Background `#F7F4EE`, Elevated Surface `#FFFDF9`, Surface Variant `#EAE4D8`, Primary Deep Teal `#245E63`, Warm Amber Accent `#B86B35`, Primary Ink `#182224`, Muted Slate `#5A6668`.
    - **Dark Mode (Midnight Reading Room)**: Background `#111718`, Elevated Surface `#182123`, Surface Variant `#253134`, Primary Soft Seafoam `#7CC2C9`, Warm Amber Accent `#E09A63`, Primary Ink `#EAE6DF`, Muted Slate `#A6B3B5`.
  - *Typography & Hierarchy*: Locally bundled static Google Fonts—**Lora** (editorial serif for display/screen headlines and book/track hero titles) paired with **Plus Jakarta Sans** (clean geometric-humanist sans for body copy, timestamps, metadata pills, and controls), scaled in `sp`.
  - *Component Styling & Layout*: Generous 20dp–24dp outer margins on an 8dp rhythm, asymmetric Continue hero banner with subtle radial atmospheric tint, tonal surface layering instead of harsh wireframes, standard Material Symbols icons (filled for primary transport, outlined for secondary actions), custom adaptive launcher icon, and `48.dp` minimum touch targets with `testTag` identifiers on all primary actions.

- **Interactive Feedback & Motion**:
  - Live 250ms playhead updates while playing; decoupled drag state on the custom seek bar so the thumb never snaps backward mid-scrub.
  - Gentle spring transitions (`< 250ms`) on Mini-Player appearance, play/pause state morphing, and folder-import progress feedback.
  - Clear offline status banners for Android 15 (API 35+) PDF text extraction availability and strict offline voice verification.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Delete Unwired TTS Lookahead & Broken DSP Processors**
  - *Chosen Approach*: Remove the uninstantiated batch/lookahead TTS classes, unwired DSP processor classes, unused processor-settings table/scaffolding, and duplicate output-profile manager; move Media3 inspector to debug-only dependencies.
  - *Why*: Eliminates ~1,200 lines of broken code (closed channels on seek, double callbacks, illegal mutex unlocking, mono-only broken biquad loops) and upholds Murmur's core rule that every visible or persisted setting has real audible behavior.
  - *Alternatives Considered*: Rewriting a multi-band DSP engine from scratch was passed over per your confirmation to keep the audio pipeline lean, bit-transparent, and honest.

- **Decision 2: Single Canonical Read Aloud Media-ID Codec + Room-Backed WAV Cache**
  - *Chosen Approach*: Consolidate Read Aloud media IDs into one codec that `Uri.encode`s every string field (`documentId`, `cacheKey`, and metadata tokens) and persist synthesized WAV metadata (`cacheKey`, `filePath`, `identityHash`, `sampleRate`, `channelCount`, `bitsPerSample`, `durationMs`, `sizeBytes`, `lastAccessedMillis`) in Room (schema v17) while reading exact PCM format fields from the 44-byte RIFF/WAVE header on cache hits. Also stop deleting cached WAV files when `PlaybackService` stops.
  - *Why*: Prevents `|` delimiter injection/field-shifting bugs, preserves synthesized speech across app restarts, and replaces inaccurate hardcoded 22,050 Hz duration guesses with exact millisecond durations.

- **Decision 3: `DocumentFile` Tree & Multi-File Library Import**
  - *Chosen Approach*: Support both `OpenMultipleDocuments` (individual files) and `OpenDocumentTree` (`DocumentFile.fromTreeUri` recursive traversal with bounded depth/file count and persisted URI grants) for Music and Audiobooks.
  - *Why*: Lets users organize audiobooks and music albums in device folders and import them cleanly without broad storage permissions (`MANAGE_EXTERNAL_STORAGE` is never requested).

- **Decision 4: Hardened Local Security & Honest Offline Legal Disclosures**
  - *Chosen Approach*: Restrict `MediaSession.Callback.onConnect` to verified same-UID, `SYSTEM_UID`, `BLUETOOTH_UID`, and Media3 system notification/companion controllers; cap incoming `ACTION_SEND_MULTIPLE` shared URIs to 100 items behind a single-flight mutex; remove shared external storage `getExternalFilesDir` export paths in favor of user-chosen SAF `DocumentFile` URIs; run WAL checkpointing around logical backups; and provide an in-app **Privacy & Legal Notice** screen + `PRIVACY.md`.
  - *Why*: Resolves all three `FAIL` items from the 5-point security audit and documents COPPA/GDPR/CIPA/CAN-SPAM/ARL/DMCA compliance without adding dark patterns or unnecessary age-gate friction to an offline app.

---

## 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        UI Layer (Jetpack Compose + M3)                       │
│                                                                              │
│  ┌──────────────────────────────┐   ┌─────────────────────────────────────┐  │
│  │ Editorial Home ("Continue")  │   │ Library Screens (Music / Books /    │  │
│  │  • Live Resume Hero Card     │   │                  Read & Listen)     │  │
│  │  • Sanctuary Category Cards  │   │  • SAF Multi-File & Folder Picker   │  │
│  │  • Privacy & Legal Access    │   │    (DocumentFile Tree Traversal)    │  │
│  └──────────────┬───────────────┘   └──────────────────┬──────────────────┘  │
│                 │                                      │                     │
│  ┌──────────────▼──────────────────────────────────────▼──────────────────┐  │
│  │ Full Player Screens (Music / Audiobook / Read Aloud)                   │  │
│  │  • Custom PlaybackSeekBar (250ms sync, drag preview, Long-safe math)   │  │
│  │  • Chapter / Bookmark / Sleep Timer & Voice Controls                   │  │
│  └──────────────┬─────────────────────────────────────────────────────────┘  │
│                 │                                                            │
│  ┌──────────────▼─────────────────────────────────────────────────────────┐  │
│  │ Persistent M3 Mini-Player Bar (Prev / Play-Pause / Next + Progress)    │  │
│  └──────────────┬─────────────────────────────────────────────────────────┘  │
└─────────────────┼────────────────────────────────────────────────────────────┘
                  │ MediaController (SessionToken + Live Position Polling)
┌─────────────────▼────────────────────────────────────────────────────────────┐
│              Playback & Synthesis Service (MediaSessionService)              │
│                                                                              │
│  • ExoPlayer + Hardened MediaSession Callback (UID & System Verification)    │
│  • DefaultMediaNotificationProvider + SessionActivity PendingIntent          │
│  • Single Canonical ReadAloudMediaId Codec (All String Fields Uri.encoded)   │
│  • SystemTtsSpeechGenerator ──▶ Exact RIFF/WAVE Header Parser                │
└─────────────────┬───────────────────────────────────────┬────────────────────┘
                  │                                       │
┌─────────────────▼─────────────────────┐ ┌───────────────▼────────────────────┐
│   Room SQLite Sandbox (Schema v17)    │ │    SAF & App-Private Filesystem    │
│  • Queue, PlaybackState, Progress     │ │  • cacheDir/read-aloud/*.wav       │
│  • Music, Playlists, Audiobooks, Docs │ │  • Persisted SAF Content/Tree URIs │
│  • NEW: TtsCacheEntryEntity (LRU/WAV) │ │  • Validated SAF Logical Backups   │
│  • WAL Checkpoint + Atomic Restore    │ │    (No External Storage Leak)      │
└───────────────────────────────────────┘ └────────────────────────────────────┘
```

- **Data Model & State**:
  - **Room Schema v17**: Adds `tts_cache_entries` (`cacheKey`, `filePath`, `identity`, `sampleRate`, `channelCount`, `bitsPerSample`, `durationMs`, `sizeBytes`, `createdAtMillis`, `lastAccessedMillis`) with a deterministic SQL migration from v16 (and drops/excludes dead processor/batch tables cleanly or preserves backward-compatible migration).
  - **Live Playback Snapshot**: `PlaybackController` combines event-driven updates with a lightweight 250ms main-looper position ticker only while `isPlaying == true` so both the Mini-Player progress indicator and the custom `PlaybackSeekBar` advance smoothly without wasting battery when paused.
  - **Canonical Read Aloud Segment Info**: Unified `ReadAloudSegmentInfo` model encoding and decoding all string components safely via `Uri.encode`/`Uri.decode`.

- **Interactive Component & State Mapping**:
  - **Editorial Home Resume Card**: Observes `PlaybackSnapshot`, most recent `Audiobook` progress, and most recent `ReaderDocument` position -> tapping **Resume** invokes `togglePlayPause()` (if loaded) or reloads the saved item at its exact millisecond/character offset; tapping the card opens the corresponding player/reader view.
  - **`DocumentFile` Picker**: Launches `OpenMultipleDocuments` or `OpenDocumentTree` -> takes persistable read URI permissions -> traverses `DocumentFile.listFiles()` on `Dispatchers.IO` (bounded to prevent cyclic/huge directory stalls) -> upserts tracks/books into Room and shows a completion summary banner.
  - **Custom `PlaybackSeekBar`**: Maintains local `scrubFraction: Float?` while dragging -> displays live preview timestamp (`(scrubFraction * durationMs).toLong()`) -> calls `controller.seekTo(targetMs)` in `onValueChangeFinished` before clearing `scrubFraction`.
  - **Mini-Player & System Notification**: Wires Previous (`seekToPrevious`), Play/Pause (`togglePlayPause`), and Next (`seekToNext`) in both Compose and `MediaSessionService`, with notification tap launching `MainActivity` via `PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT` targeting `murmur://player`.
  - **Verification Suite**: Updates and expands unit + Robolectric tests covering Read Aloud codec round-trips with `|` characters, Room v16->v17 TTS cache persistence & WAV header duration extraction, EPUB TOC boundary matching (`stock.xhtml` non-match), `MediaSession` UID rejection, `MainActivity` 100-URI share cap, `DocumentFile` folder import, and custom seek bar / Mini-Player Compose tags.
