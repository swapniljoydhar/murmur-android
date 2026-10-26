# Murmur project status

| Field | Value |
|---|---|
| Snapshot date | 2026-10-06 (Asia/Dhaka) |
| Repository | `https://github.com/swapniljoydhar/murmur-android` (private) |
| Branch | `main` |
| Pushed source checkpoint | `f9b4aad` — `Create Murmur Android app foundation` |
| Application ID / namespace | `com.joy.murmur` |

This document is a checkpoint, not a live build report. Re-check the working tree and remote before relying on it.

## Executive status

Murmur is a single-module Android app for local music, audiobooks, documents, and system read aloud. The app shell, a substantial local-media baseline, a Media3 playback owner, Room/DataStore persistence, music catalog and playlist flows, audiobook progress/bookmark/chapter flows, and TXT/Markdown/HTML/EPUB/PDF reading are present. System TTS synthesis and speech playback are present. The latest pushed source compiled with `:app:assembleDebug` during this work session.

That is not evidence that every feature works on the user's device. The phone has not been used for this checkpoint's final end-to-end pass. Very long duration (237-hour) tests, low-end/OEM tests, audio-route measurements, accessibility audits, restore/backup tests, and release signing/release validation are not claimed complete.

The working tree contains later, **uncommitted and unverified** edits in:

- `app/src/main/java/com/joy/murmur/data/documents/RoomDocumentLibraryRepository.kt`
- `app/src/main/java/com/joy/murmur/data/speech/SystemTtsSpeechGenerator.kt`
- `app/src/main/java/com/joy/murmur/domain/documents/ReaderDocument.kt`
- `app/src/main/java/com/joy/murmur/playback/PlaybackService.kt`
- `app/src/main/java/com/joy/murmur/playback/ReadAloudMediaId.kt`
- `app/src/main/java/com/joy/murmur/ui/MurmurApp.kt`

Those edits include end-of-page reader-progress synchronization, cross-generator cache-operation locking, a shared markdown-to-speech helper, and a partial Android 15+ PDF text extraction/search path. They are **not part of `f9b4aad`**, were not compiled after the latest edits, and must be reviewed and built before any agent claims them complete or pushes them as app code. The PDF work in particular may need UI/service wiring and compatibility fixes. The present user instruction was to stop feature work and create/push the planning documents; it was not authorization to mark these edits finished.

## Verified repository facts

- The repository was newly created under `swapniljoydhar` as a **private** repository named `murmur-android`.
- The pushed branch is `main`, and the local branch tracks `origin/main`.
- The initial push was a force push to a newly-created, empty repository. Remote `main` pointed to `f9b4aad7bf6ef5d39229816df40e7b93f985bcc9` when checked.
- The working tree is a Windows Android project. The local-only `local.properties`, `.gradle`, build outputs, IDE state, native intermediates, and captures are ignored by `.gitignore`.
- A staged-source scan for common private-key, token, API-key, password, and secret assignment patterns had no matches. This is a narrow pattern scan, not a comprehensive secret audit.
- The manifest has been described in the project README and architecture doc as having no `INTERNET` permission. Recheck `app/src/main/AndroidManifest.xml` and the merged manifest when validating a release.
- Toolchain facts recorded in the conversation: Microsoft OpenJDK 17.0.20.1 LTS, Gradle Wrapper 9.4.1, Kotlin 2.3.0, Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools / ADB 37.0.1. Verify versions on the host before future release work.

## What is in the pushed app checkpoint

### Foundation and settings

- Single `:app` Kotlin/Compose application, namespace and ID `com.joy.murmur`, min API 26 and target API 37 in the checked-in Gradle configuration.
- Compose Material 3 UI, DataStore-backed appearance mode, reader text scale, strict offline speech preference, selected TTS voice, and background music refresh opt-in.
- Continue landing destination, feature navigation, dark/light/system appearance, honest empty states, combined Documents + Read & Listen flow.

### Shared playback

- Media3 ExoPlayer owned by `PlaybackService`, exposed through a MediaSession and app controller.
- Shared playback path for music, audiobooks, and generated speech; queue controls, transport, system media controls/notification, restored queue state kept paused, and Room persistence for queue, current item, position, duration, and per-item listening progress.
- Do not infer bit-perfect output or gapless guarantees from use of Media3 alone.

### Music

- SAF individual-file selection and explicit, permission-gated MediaStore scan.
- Song/artist/album/folder browsing and metadata search; Room catalog snapshot restoration, item-level change handling, collection reconciliation, optional battery-aware periodic refresh (off by default).
- Local playlists with create, rename, delete, add/remove tracks, order, and play flows.
- No bundled example library, online metadata, or network service is intended.

### Audiobooks

- SAF-selected audiobook library, search, local metadata and supported embedded cover art, resume, bookmarks, chapters when Media3 exposes supported embedded chapter frames, and shared player controls/speed/queue.
- Missing embedded chapter metadata remains an honest empty result; no chapter guessing or web lookup.
- Room migrations and exported schemas exist through version 10 in the pushed checkpoint; inspect all migration paths before adding more.

### Documents and Read Aloud

- SAF library covers TXT, Markdown, HTML, EPUB, and rendered PDF pages. Reader saves positions and bookmarks; text size is stored locally. Text search exists for TXT/Markdown/HTML/EPUB with bounded UI result counts. HTML and EPUB parsing enforce input limits. Reader does not copy source documents into app storage.
- PDF baseline in the pushed checkpoint is bounded page rendering only; selectable PDF text/search/speech are not yet proven in the pushed version.
- System TTS uses platform voice information, strict-offline gating (default on), bounded synthesis segments, engine-reported PCM format, generated WAV cache, and the shared Media3 path.
- Pushed checkpoint includes continuous next-page Read Aloud for text documents, voice selection, and bounded age/size cache eviction; `assembleDebug` succeeded after that pushed source. This is build evidence only, not physical-device verification. Sentence-level synchronization, reliable source mapping across Markdown transformations, return-to-text, process-death continuation, cache races across service/UI generators, and PDF text support remain unfinished or unverified.

## Roadmap completion snapshot

| Master phase | Snapshot | Evidence / caveat |
|---|---|---|
| 1. Foundation | Implemented | Project, navigation, preferences, architecture exist; verify clean clone/build and device flows. |
| 2. Playback | Implemented baseline | Media3 service/session and Room restoration exist; full interruption, reboot, route, and long-duration acceptance still required. |
| 3. Music | Implemented baseline | Catalog, permission flow, playlists, incremental refresh, optional worker exist; device/media mutation QA remains. |
| 4. Audiobooks | Implemented baseline | Library, progress, bookmarks, supported chapter metadata exist; very-long M4B, parser formats, process death and seek acceptance remain. |
| 5. Reader | Partial | Formats, basic positions/bookmarks/search and PDF render exist; structural anchors, very-large-file efficiency, PDF extraction, malformed/scan-only handling and UX/accessibility audit remain. |
| 6. System TTS | Partial | Platform synthesis, offline policy, voice selection, bounded chunks/cache, and text-page continuation exist; reliable source mapping/sentence sync, seek/repeat/return-to-text, error behavior, cross-generator cache concurrency, process-death continuation, and device validation remain. |
| 7. Long-form hardening | Planned / partial | No evidence yet for 200+ hour audiobook or TTS acceptance, process-death continuation, bounded scheduler measurements, or extreme seek/race/storage-full acceptance. |
| 8. Neural TTS | Deferred optional | Must not block the system-TTS baseline; evaluate only with user approval, offline/license/native-library audit, and measurable benefit. |
| 9. Audio quality | Deferred / not implemented | Murmur processors are not connected to ExoPlayer; ReplayGain is not applied. The misleading Sound screen and unsupported bit-perfect/gapless/output claims were removed. Device route/audio-quality validation remains deferred. |
| 10. Native Android integration | Partial | MediaSession/notification and SAF/MediaStore paths exist; home-screen widget, shortcuts/deep links, full route/noisy/audio-focus and platform version matrix need audit. Android Auto remains out of scope. |
| 11. Storage / backup | Implemented baseline | Versioned, checksummed logical backup with schema validation, preview, explicit replacement confirmation, and transactional restore is available from Settings. Preferences, playback queue/state, source files, generated speech, and SAF grants are excluded. SAF/device acceptance remains deferred. |
| 12. OEM resilience | Planned | Work scheduling exists for catalog refresh; no verified OEM diagnostic flow, reboot/background matrix, or truthful battery-policy guidance. Never promise OEM settings defeat OS limits. |
| 13. Performance / accessibility / security hardening | Partial / planned | Some size/input bounds and local-only architecture exist; no complete low-end performance, TalkBack, contrast, rapid-interaction, permissions, parser fuzz, or release security report. |

The “four remaining phases” reported earlier in chat were a coarse milestone grouping. The supplied master engineering prompt contains thirteen named phases. This table exposes the difference instead of claiming that the entire master plan is finished.

## Current working-tree update — 2026-10-26

The earlier checkpoint above is historical. The current checkout is at `ecc8245` with additional uncommitted changes. A bounded PDF/read-aloud slice now replaces the reflective PDF text lookup with Android 15/API 35 `PdfRenderer.Page.getTextContents()`, supports page-wise PDF text search and page-index position/continuation, and presents an explicit unsupported path below API 35 or for pages with no extractable text. A focused PDF instrumentation test source was added. PDF OCR is not implemented.

The next source-level fix preserves Markdown character indexes while stripping spoken markup and adjusts speech chunk offsets for trimmed leading whitespace. This prevents generated segment offsets from shifting merely because Markdown syntax was removed. Unit tests cover this transformation, but not actual Read Aloud playback or the reader UI's return-to-position behavior.

The reader also now follows persisted position changes while open, allowing its displayed page to catch up to Read Aloud segment progress. This is not sentence highlighting or time-continuous following and still requires device verification.

Service-side Read Aloud start and continuation failures now publish a process-local, document-scoped status. The reader offers a retry from the saved position, while the Read Aloud player directs users back to the document. The service only persists the next page position after synthesis succeeds; unit tests cover status behavior, but full retry behavior is unverified on-device.

The Activity, reader, and playback service construct separate system TTS generators sharing the same cache directory. Their voice changes, synthesis, and cache eviction now use a process-wide mutex. This prevents concurrent generator instances from writing or evicting cache entries at the same time; device stress testing remains outstanding.

Restoration now removes ephemeral `read_aloud_start|...` entries and does not automatically generate/start near-end speech after service creation. Restored queues remain paused until an explicit play action. A player error on a Read Aloud segment stores that segment's source position and offers a return-to-reader retry. Process-death and missing-cache behavior still require device validation.

The restore policy is isolated and unit-tested for transient-marker removal, paused restoration, and keeping or resetting positions as appropriate. This does not prove actual Media3 process recreation.

A fixed-duration sleep timer now runs in the playback service and is available in Music, Audiobooks, and Read Aloud players. It persists a deadline across service recreation and pauses the player directly at expiry. The old alarm receiver path was removed because it was not manifest-declared and its requested service action was unhandled. Presets are 15, 30, 45, and 60 minutes; chapter-end and custom timers remain deferred. Unit coverage only verifies remaining-time clamping; actual background expiry and service/process recreation remain unverified on-device.

Read Aloud service synthesis and repository access run on IO, while ExoPlayer access is marshalled to its main looper. Queue identity is rechecked before replacing/appending generated media, preventing stale synthesis from mutating a newer playback selection. This is build-verified but not runtime-tested.

Document, audiobook, and direct music SAF imports now require a retained read grant before saving or queuing the selected URI. The UI reports selections skipped because a provider did not grant persistent access and distinguishes unsupported audio/document formats. The document empty state includes PDF and states that PDF text extraction requires Android 15 or newer. This behavior is source- and build-verified; provider-specific grant/revocation behavior is still untested on-device.

Read Aloud source position now saves at segment transitions, pause events, playback errors, and service destruction. The saved location is the segment's source anchor; Murmur does not yet map elapsed audio time to a sentence within that segment. This is build-verified and needs pause/process-death device checks.

PlaybackService now tracks marker-start and continuation synthesis jobs, cancels them when playback leaves Read Aloud or switches to another document, and the system TTS generator checks cancellation between cached or synthesized chunks and stops the engine after a failed/cancelled chunk. The stale-media identity guard remains as an additional queue-mutation check. This source change needs cancellation/rapid-replacement stress validation on-device.

Before the reader queues generated audio, it now confirms the source page and current playback queue still match the state present when synthesis began. A changed page or queue leaves the newer state in place and asks the user to retry. This guard is build-verified; timing behavior needs device validation.

EPUB TOC spine fallback now resolves relative chapter links against the OPF file path. A source bug had passed OPF XML text as the base path, breaking EPUBs without a usable nav/NCX. Unit tests cover spine fallback and archive traversal rejection; real-world EPUB/provider compatibility remains unverified.

EPUB ZIP reading now limits archives to 10,000 entries and entry names to 4,096 characters, alongside existing uncompressed-byte and captured-content limits. A unit test covers archives that exceed the entry bound using empty entries. The chosen limits still need validation against a representative EPUB collection.

Document indexing and EPUB parsing now check coroutine cancellation between text buffers, ZIP reads/entries, and chapters. EPUB repository operations pass their coroutine context into archive reading, with a focused unit test proving cancellation aborts parsing. A provider input stream blocked inside a single read remains dependent on that read returning.

Validation after the reader/EPUB changes: `:app:testDebugUnitTest` passed, including `EpubParserTest` (4 tests, 0 failures); `:app:assembleDebugAndroidTest` and `:app:assembleDebug` passed; `git diff --check` passed. Instrumentation sources compiled but were not run on a device.

Audiobook chapter handling now removes the long-file 30-minute heuristic and requires actual parsed starts for Vorbis chapter entries. The Vorbis mapper supports timestamp/name comments and explicit START/END keys, rejects boundaries at or past known duration, and identifies the source as VORBIS. The ID3 fallback now reads a capped tag from the selected content URI and parses v2.3/v2.4 CHAP frames with four-byte time fields; unsupported unsynchronized tags fail closed. Three ID3 and three Vorbis unit tests cover these mappings, but actual Ogg/MP3/M4B files and device seeking remain unverified.

Validation after the audiobook parser changes: `:app:testDebugUnitTest` passed, including `Id3ChapterParserTest` (3 tests) and `VorbisChapterMappingTest` (3 tests); `:app:assembleDebugAndroidTest`, `:app:assembleDebug`, and `git diff --check` passed. Instrumentation sources compiled but were not run.

Validation after the audiobook changes: `:app:testDebugUnitTest` passed, including `VorbisChapterMappingTest` (3 tests, 0 failures); `:app:assembleDebugAndroidTest`, `:app:assembleDebug`, and `git diff --check` passed. Instrumentation was compiled but not executed.

Validation on this exact source tree succeeded for `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebugAndroidTest`. The last command proves instrumentation sources compile; PDF instrumentation tests were not run. No device was connected, and the configured API 37 emulator system image is missing, so PDF rendering/extraction, TTS playback, and all device acceptance remain unverified. The generated debug merged manifest was inspected: no `INTERNET` permission is present; `ACCESS_NETWORK_STATE` is present. See `docs/TESTING.md` and `planning/PRODUCT_GAP_AUDIT.md` for the current evidence and remaining work.

## Immediate handoff state

1. Preserve branch `main` and inspect `git status`; keep the unverified edits separate from doc-only changes.
2. Build the working tree with the JDK/SDK paths recorded in the repo host setup. Fix compilation errors before editing README status to call new behavior implemented.
3. Review and finish or safely remove partial PDF extraction work only after inspecting current code and APIs. On API <35, keep a clear unsupported status; scanned PDFs have no text unless a future local OCR feature is explicitly designed.
4. Complete Read Aloud continuity only with cancellation, queue replacement, process restoration, and source-position behavior considered. Keep the internal TTS work separate from the user's visible media queue.
5. Update code documentation and this status doc only after evidence exists; add/fix tests when the behavior changes.
6. Once feature work is complete, run the validation walkthrough, install/launch on the connected phone, and report exactly which scenarios passed and which were not run.
7. Commit source separately from planning docs where practical; push the new commit to the existing private repository. Do not force-push again unless the current user explicitly requests rewriting an existing remote history.

## P5 implementation update — 2026-10-27

This is the latest P5 record; older snapshot sections above are historical. P5 removes an unused exact-alarm permission, explicitly excludes app-private data from Android cloud/device-transfer backup rules, cleans stale dependency catalog entries, and corrects README/architecture/storage/licensing scope. The app's in-app logical backup remains available and separately documented. No `INTERNET` permission was introduced.

P5 exact-tree validation passed: `.\gradlew.bat test lintDebug assembleDebug assembleDebugAndroidTest assembleRelease` completed successfully. The unit reports contain 64 tests with zero failures/errors; lint reports zero errors, 41 warnings, and one hint. Debug and release merged manifests contain no `INTERNET`, `SCHEDULE_EXACT_ALARM`, or `POST_NOTIFICATIONS`; the release APK built unsigned, and instrumentation sources compiled but were not executed. The native strip warnings for `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` remain non-fatal.

This is not physical-device acceptance. TalkBack, touch-target measurement, contrast on real displays, large-font/layout behavior, notifications, API 26–current runtime behavior, OEM lifecycle, SAF provider behavior, and instrumentation execution remain in the deferred device gate. The dependency licensing document identifies direct dependency families but does not claim a complete transitive SBOM/legal audit. Neural TTS and full-book export remain deferred.

P5 implementation commit `924ac7a` (`chore(p5): harden backup and release documentation`) was pushed successfully to `origin/main`. The working tree should be checked after this status-only follow-up is committed.
