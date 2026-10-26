# Murmur product gap audit and completion plan

> **Planning artifact, not app source.** This document records a static source review and a proposed sequence. It does not claim that a feature works, that a build passes, or that device acceptance has occurred. Update status only after validating the exact tree and recording evidence.

## Snapshot and review limits

- Review date: 2026-10-26 (Asia/Dhaka).
- Checkout: `main`, HEAD `ecc8245` (`chore: remove unnecessary files and submodules`).
- Review type: static inspection of the supplied product vision, checked-in source, Gradle/manifest configuration, tests present in the tree, and existing project documentation.
- The initial static review did not run builds or tests. A later bounded PDF/read-aloud implementation slice was built and validated as recorded below; no install or device walk-through was possible.
- The working tree already contains deletions of `OFFLINE_TTS_REQUIREMENTS_REVIEW.md`, `OFFLINE_TTS_TEST_PROCEDURE.md`, and `test.txt`, plus a modified `rea` gitlink. These changes were not modified. Git also reports no `.gitmodules` mapping for `rea`; resolve its intended state before relying on recursive submodule commands.
- Kin semantic MCP tools (`semantic_locate`, `get_context_pack`, `trace_data_flow`) were not exposed in this session. The audit therefore used source and project docs directly.

## Executive assessment

Murmur has a substantial local-first Android foundation and multiple real feature implementations. It is not yet evidenced as a finished application. The highest-priority work is closing gaps between code that exists and behavior users can reach, correcting unsafe or misleading paths, and proving the core reading/listening lifecycle. The project does not need a larger feature count before that work.

The best product position is one calm app for local music, audiobooks, documents, and offline-capable Read Aloud, with reliable transitions between reading and listening. Do not try to reproduce every advanced function in dedicated audio players or e-reader suites. Select a reference feature only when it directly improves Murmur's core flow and can be delivered honestly.

## What source inspection confirms

| Area | Present in source | Gap or limit visible in source | Current confidence |
|---|---|---|---|
| App foundation | Single Kotlin/Compose app; Room and DataStore; Media3 playback service; min API 26; no direct `INTERNET` permission in the checked-in manifest | Merged manifest and clean exact-tree build have not been checked in this review | Source confirmed; runtime unverified |
| Music | MediaStore/SAF catalog paths, browse/search, playlists, shared player and widget code | No evidence of multiple independent persistent queues, tag editing, synced lyric display, or Android Auto library integration; Sound screen is not referenced by active app navigation | Partial; device behavior unverified |
| Audiobooks | SAF library, per-book progress/bookmarks, fixed-duration service sleep timer, parsed metadata chapters, shared playback | No silence skipping or Android Auto. Only chapters found in embedded container/ID3/Vorbis metadata are shown; provider, focus, route, process-death, and long-duration runtime behavior remain device-unverified. | Implementation source/build verified; device acceptance deferred |
| Reader | TXT, Markdown, HTML, EPUB, PDF page rendering; saved positions/bookmarks; search; EPUB TOC with chapter navigation in Reader | Versioned v1 character/page anchors with metadata fingerprint fallback; in-chapter EPUB fragments are not mapped. API-35 PDF text access; below API 35 rendering only. No OCR. Large-file limits are documented; runtime performance remains unmeasured. | Implementation source/build verified; device acceptance deferred |
| System Read Aloud | Android `TextToSpeech`, voice enumeration/eligibility, strict-offline preference, WAV cache, generated audio through shared playback | The active app path is `PlaybackService` plus `SystemTtsSpeechGenerator`; its source-position/return-to-text behavior still needs a connected end-to-end audit. The separate `TtsScheduler`/`BatchTtsManager` path has no app construction sites, and `TtsScheduler.extractSectionText()` returns empty text; it is not usable or part of current playback. Sentence/paragraph structural mapping is not represented in the current document model. | Partial; uncached offline synthesis unverified |
| Neural TTS | Model-manifest data types and storage directory/settings are present | No `NeuralTtsSpeechGenerator` implementation, runtime dependency, import/validation flow, or reachable model-management UI was found | Not implemented; intentionally optional |
| Batch speech export | `BatchTtsManager` and Room job/work-unit entities exist | No app/UI call site found; background scheduling, checkpoints, output finalization, restart, and cancellation have not been demonstrated | Not user-ready |
| Sound controls | Processor classes, output-profile data, and `SoundScreen` exist | `SoundScreen` is not referenced by active navigation; processor classes existing does not prove the service applies them to audible output | Not user-ready |
| Storage / backup | Storage usage and cleanup UI/services; database import/export methods | `StorageCenterScreen` is not referenced by active navigation. Current import copies into a live Room database file without a validated snapshot/restore transaction; export copies the DB file while the database may be active. This must not be exposed as reliable backup until redesigned and validated. | Unsafe/inaccessible path; release blocker if exposed |
| OEM help | Diagnostic model and `OemFixScreen` are wired into a route | No representative OEM/device validation in this review | Source present; runtime unverified |
| Tests | Unit and instrumentation test files exist for TTS utilities/cache/scheduler, navigation, persistence, playback database, and long-form acceptance scaffolding | Test files are not test results; this review ran none | Present, not executed |

### Specific source-level findings to resolve early

1. `StorageCenterScreen` advertises backup and restore, but its underlying raw database file copy is not a safe live-database backup/restore protocol. Before exposing it, design a versioned logical export, validate and preview it, apply transactionally, and preserve the old state on failure. Otherwise remove or clearly disable the restore actions until that path exists.
2. The separate `TtsScheduler` path is not wired into the app and is incomplete: `extractSectionText()` returns an empty string, and `seekTo()` closes its worker channel permanently. Do not integrate or repair this scheduler in isolation. The active path is `PlaybackService` plus `SystemTtsSpeechGenerator`; only replace it with scheduling/batch generation after that becomes a product requirement and the full source-reading, cancellation, queue-replacement, and lifecycle contract is implemented and tested.
3. **Resolved in source, runtime unverified:** `PdfTextExtractor` now uses `PdfRenderer.Page.getTextContents()` with an API 35 gate. Below API 35 the app reports text extraction as unsupported; scanned/image-only pages return no text. OCR remains out of scope. PDF extraction/search/read-aloud instrumentation cases compile but have not been run on a device.
4. **Resolved in documentation:** `docs/TTS.md` and `docs/READER.md` now describe the active system TTS/reader paths and state that neural generation, sentence highlighting, and in-chapter EPUB fragment navigation are not implemented. Keep these docs tied to source and validation evidence.
5. `MurmurApp.kt` is a 2,000-line UI/navigation file. Do not start with a broad rewrite. Extract a screen only when it is part of a bounded feature change, and preserve current routes and state behavior.

## Competitive review rule

Reference products solve narrower jobs and often have years of polish. Their advertised features are useful research input, not an automatic backlog. Compare the user's actual workflow, local/offline constraints, lifecycle quality, and accessibility. If a reference is already clearly better in a specialized sub-area, keep Murmur's scope narrow and avoid claiming parity; only add the smallest feature that strengthens Murmur's combined reader/listener experience.

| Reference group | What is already strong in references | Murmur decision |
|---|---|---|
| Smart AudioBook Player / Voice | Mature audiobook-focused controls, sleep timers, bookmarks, chapter handling; Voice additionally documents silence skipping and Android Auto | Finish resume, chapter, bookmark, timer, and background behavior first. Defer silence skipping, custom media-key remapping, Android Auto, and audiobook-specific library polish until core use is proven and users prioritize them. |
| Musicolet / Auxio / LibrePlayer | Strong local music library UX; examples include multiple queues, tag/lyrics tools, folder browsing, gapless and ReplayGain | Murmur already has basic catalog/search/playlists. Do not chase tag editing, lyric authoring, or multiple queues in the first completion pass. First verify queue persistence, media changes, permissions, and truthful audio processing. |
| Moon+ Reader / KOReader / Librera | Much broader formats, typographic controls, annotations, reflow, and specialist PDF/e-book navigation | Keep Murmur focused on local document access, stable reading position, and reliable Read Aloud. Consider another format only with demonstrated user need, a maintained parser, size/security limits, and a licensing review. Do not pursue a general-purpose e-reader feature matrix. |
| Voice Aloud Reader / Lector | Broad intake and mature read-aloud workflows; Lector specifically documents sentence following/highlighting | Sentence mapping, seek-to-text, Return to Text, background/media controls, and uncached offline synthesis are central gaps worth closing because they form Murmur's combined reader/listener promise. Web extraction, cloud intake, OCR, and extra format breadth are deferred. |
| Audiobookshelf | Strong server-backed sync/library workflow | Out of scope for Murmur's no-account/no-backend local-first contract. Do not add server sync or remote streaming. |
| Piper / Sherpa-ONNX | Local neural speech runtimes/models are viable technical options; Sherpa documents Android TTS and Piper-family model support. The original `rhasspy/piper` repository is archived. | Keep neural TTS behind a research gate. Select a maintained runtime only after system TTS works reliably; pin versions, test supported ABIs/performance, and audit each runtime/model/tokenizer/voice license separately. Never imply the runtime license covers every voice model. |

This is a selective comparison of product direction, not a full hands-on review of every app/version/device. Store descriptions and upstream READMEs establish advertised scope, not comparative quality or behavior on the user's device.

## Prioritized completion plan

### Gate 0 — Protect and re-baseline the checkout

**Goal:** know exactly what tree will be developed without disturbing current user changes.

1. Record the intended disposition of the existing deleted review files and `rea` gitlink. Do not restore, stage, commit, or remove those changes as part of unrelated work.
2. Inspect the exact diff and determine why `.gitmodules` lacks a mapping for `rea`; avoid submodule update commands until the intended repository state is known.
3. Confirm SDK/JDK/toolchain availability, then build the exact working tree before feature edits. Capture build output and merged-manifest output. This is a future validation action, not performed by this audit.
4. Refresh this status snapshot from current source and execution evidence; the existing `PROJECT_STATUS.md` is dated and should not be treated as live truth.

**Exit:** a reproducible current baseline with working-tree changes clearly separated, successful exact-tree build or a documented blocker, and merged manifest checked for accidental network permission.

### P0 — Finish and make the core paths reachable

**Goal:** every visible promise leads to a working, recoverable user flow.

1. Map Continue → Music/Audiobooks/Read & Listen → player/reader → resume after process death.
2. Connect the document source to Read Aloud; keep the generated TTS item in the internal playback path and restore the exact document location on pause, seek, stop, failure, and process recreation.
3. Implement TTS cancellation, queue replacement, cache concurrency, voice-data errors, missing URI permission, playback failure, and strict-offline no-fallback behavior. Defer proof of uncached offline synthesis on a physical device to the final device-validation gate after all implementation phases.
4. Keep the unused `TtsScheduler`/batch generation path out of the core flow. If future product work selects it, implement and validate the complete lifecycle instead of reusing the current incomplete classes.
5. Make empty, loading, missing-file, unsupported-format, and no-offline-voice states understandable and recoverable.

**Implementation exit:** the reader-to-speech-to-reader path, durable position handling, and strict-offline guards are implemented; the exact tree passes unit tests and builds both app and instrumentation APKs. P0 implementation is complete at `981d137`.

**Deferred final acceptance:** physical reader-to-speech-to-reader behavior, uncached synthesis with connectivity disabled, process recreation, and actual TTS-engine behavior remain unverified. Per the user's sequencing request, perform these device checks only after implementation phases P0–P5 are complete; do not treat source/build evidence as device acceptance.

### P1 — Reader correctness, anchors, and bounded work

**Goal:** the formats Murmur already lists can be opened, searched, resumed, and read aloud without misleading claims or unbounded memory use.

1. Replace section-only position identity with stable, versioned anchors sufficient for the chosen renderers; define migration/fallback behavior when a source file changes.
2. Trace TXT, Markdown, HTML, EPUB, and PDF ingestion end-to-end. Establish memory and size bounds and cancellation for each format; distinguish page rendering from text extraction.
3. Implement API-35 PDF text access behind the correct SDK gate; define explicit unsupported behavior below it. Add local OCR only after a separate performance, privacy, and licensing decision.
4. Make EPUB parsing navigation-aware and security-bounded; ensure archive paths, decompression, oversized files, malformed markup, and external resources fail safely.
5. Deliver sentence/paragraph-to-source mapping, current-sentence following, Tap-to-Read, Return to Text, and position restoration only where the underlying format has reliable source mapping.
6. Audit accessibility and settings actually implemented. Do not advertise missing reader themes, rulers, auto-scroll, highlights, or notes.

**Exit:** representative and adversarial document fixtures for all supported formats pass reader/search/position/TTS checks, including large input measurements and PDF text/image-only cases.

### P2 — Complete audiobook daily-use behavior

**Goal:** the audiobook experience is dependable for ordinary and long books.

1. Connect a simple sleep timer to the shared service/player. Start with duration and stop-at-chapter-end only if real chapter boundaries are available; do not invent chapters.
2. Validate per-book resume, bookmarks, chapter seek, speed, interruption/audio focus, headphones, route changes, and removed/revoked source recovery.
3. Review chapter parser fallbacks: expose only parsed chapter metadata; label heuristic results if retained, and do not present them as embedded truth.
4. Verify long durations and timestamps use `Long`, then test distant seeks and process death with synthetic duration plus real representative files.
5. Add silence skipping, auto-rewind tuning, series grouping, and Android Auto only if prioritized after this baseline.

**Exit:** per-book state is durable and correct across switching books and process death; timer and supported chapter flows pass on device; the 200+ hour claim has measured evidence or is narrowed.

### P3 — Complete music and sound claims

**Goal:** local music remains easy and reliable, while every audio control changes actual playback as described.

1. Validate MediaStore grant/revocation, SAF import, incremental reconciliation, playlists, stale media, queue persistence, notifications, headset controls, audio focus, and Bluetooth disconnect/reconnect.
2. Wire Sound controls only after tracing the processor chain into the one ExoPlayer instance and confirming persisted settings are applied. Delete or hide dead controls rather than showing no-op toggles.
3. Verify ReplayGain metadata parsing, headroom/clipping behavior, EQ stability, and output-profile matching; show system/output-path limits honestly.
4. Test gapless behavior by supported format/decoder and remove any broad promise unsupported by results.
5. Defer multiple independent queues, tag editing, synced lyrics, and Auto integration; revisit only from user feedback and a distinct Murmur product reason.

**Exit:** source-level processor settings map to measurable playback behavior; catalog and playback survive permission/media changes; no unsupported quality claims remain.

### P4 — Safe storage and backup

**Goal:** storage cleanup cannot harm original files, and backup restore cannot corrupt the live database.

1. Route Storage Center from Settings only after its actions are safe and complete.
2. Replace live-database file copying with a versioned logical export/import format or a correct closed/snapshotted database backup protocol. Include checksums, schema compatibility, limits, and a preview.
3. Apply restore through validated temporary state and a transaction/swap strategy with rollback; test interruption, malformed input, duplicate IDs, insufficient space, and old-version upgrades.
4. Keep source files outside app-owned cleanup and clearly distinguish cache, model assets, exported audio, and library metadata.
5. Decide whether backup includes settings, playlists, bookmarks, and progress, and communicate exactly what does not transfer (source files/SAF grants).

**Exit:** destructive restore scenarios preserve prior valid data; export and import work across reinstall/device migration where SAF access can be re-granted.

### P5 — Release hardening and optional features

1. Audit TalkBack, focus order, touch targets, contrast, landscape/tablet layouts, large font scaling, notifications, and API 26–current behavior.
2. Audit manifest, transitive permissions, dependency versions/licenses, archive parsers, SAF grants, cache bounds, crash recovery, and release configuration.
3. Run targeted unit/instrumentation/device checks for implemented behavior; keep executed evidence separate from test scaffolding.
4. Evaluate neural TTS only after P0 system TTS exit. Compare Sherpa-ONNX/Piper integration using pinned Android artifacts, ABI coverage, low/mid-range benchmarks, memory, cancellation, cold start, offline uncached synthesis, and per-asset license review.
5. Keep batch full-book speech export deferred until a user-facing need is confirmed and its durable work manager, checkpoint, output, storage-full, and Android background-limit design is demonstrated.

**Exit:** no release claim exceeds tested devices/scenarios; known limitations are explicit; clean checkout and release build are reproducible.

## Recommended execution order

1. Gate 0 baseline and dirty-tree accounting.
2. P0 core reachability, offline speech, recovery, and lifecycle correctness.
3. P1 reader anchors, format bounds, PDF honesty, and read-aloud mapping.
4. P2 audiobook sleep timer/resume/long-form reliability.
5. P3 music correctness and real audio processing.
6. P4 safe backup/restore and storage management.
7. P5 accessibility, security, licensing, compatibility, and release validation.
8. Reconsider optional neural TTS, batch export, lyrics, tag editing, extra formats, and Android Auto after core acceptance and user prioritization.

Do not begin a broad architecture rewrite. For each slice, inspect adjacent code and migrations, make a narrow change, add focused tests when implementation begins, build the exact tree, inspect the diff, and record source/build evidence. Defer all physical-device acceptance checks until P0–P5 implementation is complete, then run them as one final gate.

## Validation update — 2026-10-26

- Changed PDF extraction to the API 35 public text-content API; added PDF page search and page-index position handling for read-aloud continuation.
- Updated PDF reader affordances to explain unsupported API levels and textless/scanned pages.
- Added PDF instrumentation cases. Fixed stale navigation/long-form instrumentation sources so the Android test APK compiles; the long-form class currently verifies model-level duration/offset behavior and is not device acceptance evidence.
- Exact-tree commands completed successfully: `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:assembleDebugAndroidTest`.
- The generated debug merged manifest was inspected and contains no `android.permission.INTERNET`; it does contain `ACCESS_NETWORK_STATE`.
- A follow-up Read Aloud fix makes Markdown cleanup length-preserving so speech chunk offsets still identify positions in the original source, and accounts for leading whitespace removed from generated chunks. Unit cases cover link/emphasis/code-block cleanup and index preservation; playback-position restoration remains device-unverified.
- The open reader now observes persisted document-position changes and reloads at the latest Read Aloud segment position. This is coarse segment-level following, not sentence highlighting; the Compose/service interaction remains device-unverified.
- Service-side Read Aloud start/continuation failures now publish a document-scoped process-local status, preserve the last persisted document position, and expose a retry path from the reader. Tests cover status scoping/fallback text; service-to-UI retry behavior still needs device validation.
- The Activity, reader, and playback service instantiate separate `SystemTtsSpeechGenerator`s over the same cache directory. A process-wide mutex now serializes their voice changes, synthesis, and cache eviction; concurrent multi-instance cache stress remains unverified on-device.
- Process restoration now filters ephemeral `read_aloud_start|...` queue markers and no longer starts near-end synthesis automatically. Restored queue playback stays paused for explicit user resumption. Read Aloud media errors persist the current segment's source offset and publish a return-to-reader retry message. Process-death and missing-cache runtime behavior remain unverified.
- A pure restore-policy unit test verifies marker removal, paused state, and position handling. It does not substitute for killing/restarting the service on a device.
- The previously unreachable SleepTimer was replaced with a fixed-duration service-owned timer and surfaced on all three player types. It persists a deadline across service recreation and pauses directly at expiry. The old alarm-receiver path was removed because it was undeclared and the service did not handle its pause action. Presets are 15/30/45/60 minutes; chapter-end/custom duration remain deferred. Only a pure remaining-time policy test is available; service lifecycle/expiry still need device validation.
- Read Aloud service synthesis and repository access remain on IO, while player inspection/mutation is marshalled to the ExoPlayer main looper and guarded against queue changes during generation. This fixes a source-level looper violation; actual continuation remains device-unverified.
- Document, audiobook, and direct music SAF imports now require a retained read grant before storing or queuing selected URIs. The UI reports files skipped when the provider does not grant persistent access, and document/audio libraries report unsupported formats separately. This closes the previous path where a library row or restored queue could depend only on a temporary picker grant. It is build-verified; provider-specific grant/revocation behavior has not been exercised on-device. The document empty state now includes PDF and explains that text extraction requires Android 15+.
- Read Aloud document source anchors are now persisted on segment transitions, pause events, playback errors, and service destruction. The anchor remains segment-level and does not map elapsed audio time to the active sentence; runtime pause/process-death behavior is unverified.
- PlaybackService now tracks marker-start and continuation synthesis jobs, cancels them when playback leaves Read Aloud or switches to another document, and the TTS generator checks coroutine cancellation between cache/synthesis chunks and stops its engine on a failed/cancelled chunk. The stale-media identity guard remains as a second boundary. Cancellation cleanup and rapid queue replacement still need device stress validation.
- The reader's direct synthesis path now checks that both the source page and playback queue identity are unchanged before replacing the queue with generated speech. This prevents an old synthesis result from overwriting newer user playback; rapid user interaction still needs device validation.
- EPUB TOC inspection found and fixed a broken spine-only fallback: relative chapter hrefs now resolve from the actual OPF package path rather than the XML payload. Unit tests cover a minimal OPF without nav/NCX and reject archive traversal paths. Complex EPUBs and provider-backed archives remain unverified.
- EPUB TOC is now reachable from Reader. TOC entries resolve to offsets in the same spine-ordered plain text used by the reader; entries without readable spine text remain disabled. A focused unit test covers inter-chapter separators and empty chapters, and `:app:testDebugUnitTest :app:assembleDebug` passed on this tree. Fragment-level anchors and representative EPUB/device navigation remain unverified.
- Rechecked scheduler call sites: `TtsScheduler` and `BatchTtsManager` have no app construction sites; the scheduler's `extractSectionText()` is a stub returning empty text, in addition to the channel-closure defect. The audit now treats this code as deferred and explicitly excludes it from current Read Aloud integration. The active `PlaybackService` path is unchanged; no runtime claim was added.
- Corrected `docs/TTS.md` and `docs/READER.md` to remove claims for an implemented neural generator/scheduler and to document the real system TTS path, segment-level following, EPUB chapter-start navigation, and current limitations. Documentation-only change; no build was needed.
- The active Read Aloud media-ID parser now rejects negative source ranges, source offsets beyond the page, and ranges whose next-page arithmetic would overflow. Focused tests cover accepted and rejected boundaries; `:app:testDebugUnitTest :app:assembleDebug` passed. Persisted queue recovery remains device-unverified.
- Read Aloud position writes now carry per-document revisions and share a mutex. A newer pause/transition/error/destroy write supersedes older pending writes for the same document without invalidating pending saves for another book. Tracker tests cover supersession and document isolation. `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest` passed; instrumentation was compiled, not run.
- Strict Offline Speech now classifies Android voices advertising `KEY_FEATURE_NOT_INSTALLED` as `MISSING_DATA`, rejects them before calling `TextToSpeech.setVoice()`, labels them accordingly, and excludes them from ready-offline counts. Settings eligibility checks the requested voice instead of the engine's unrelated currently active voice. Focused policy tests were added. The exact-tree unit/build/instrumentation-APK commands passed; engine behavior still requires device validation.
- EPUB extraction now caps archive entry count at 10,000 and entry names at 4,096 characters in addition to its decompressed-byte and captured-content bounds. A regression test verifies that empty ZIP entries cannot bypass the work bound. This does not replace review of parser behavior on a representative EPUB corpus.
- TXT/Markdown/HTML indexing now checks coroutine cancellation between input buffers. EPUB decompression checks cancellation between ZIP reads/entries, and indexing/search/TOC extraction passes the active context through to the parser; EPUB chapter indexing checks between chapters. The parser unit test confirms a cancellation callback stops archive processing. A provider stream blocked inside an individual read still cannot be interrupted until that read returns.
- Audiobook chapter parsing no longer manufactures 30-minute "Part" entries for long files or timestamps for title-only Vorbis comments. It accepts standard Vorbis timestamp/name comments plus explicit legacy START/END keys, omits starts outside a known duration, and marks results as VORBIS. The ID3 fallback now reads only the bounded tag through the content URI stream and parses 32-bit CHAP millisecond fields plus v2.3/v2.4 frame sizes; it rejects unsupported tag unsynchronization and malformed/truncated tags. Focused tests cover both ID3 versions and Vorbis boundaries. Real Ogg/MP3/M4B fixtures and device chapter seeking remain unverified.
- Instrumentation was not executed: `adb devices` showed no connected devices and the configured emulator's system image is absent. No runtime PDF, TTS, or end-to-end acceptance claim is made.
- The pre-existing deletions (`OFFLINE_TTS_REQUIREMENTS_REVIEW.md`, `OFFLINE_TTS_TEST_PROCEDURE.md`, `test.txt`) and modified `rea` gitlink remain untouched.

## P1 implementation progress — 2026-10-27

- Reader positions now have a versioned v1 coordinate contract: PDF page indexes and UTF-16 character offsets in extracted plain text for TXT, Markdown, HTML, and EPUB. Room schema migration 13→14 adds the nullable source fingerprint and anchor version with a legacy-compatible default; an instrumentation migration test preserves the old offset and verifies both added columns.
- The source fingerprint uses provider URI, size, and last-modified metadata. A first available fingerprint preserves migrated progress; a changed fingerprint resets progress to the beginning while preserving bookmarks. Unsupported metadata retains the last known fingerprint and position. A changed/unknown anchor version resets to the supported version. The reader reports a reset and keeps plain-TXT tap offsets aligned with the saved character coordinate.
- Added a 50 Mi-character streaming-search work cap for plain text and a 1 Mi-character PDF extracted-text cap per page. Existing code bounds reader page chunks, HTML input, EPUB entry count/name/decompressed/captured bytes, extracted EPUB text, PDF page count/render pixels, and indexing text; parser and streaming loops check cancellation between work units. EPUB metadata entry limit is 1 MiB, chapter/package entry limit is 4 MiB, captured data limit is 16 MiB, and archive uncompressed limit is 64 MiB.
- P1 platform and EPUB security work already present in this tree remains in place: API-35-gated PDF text extraction with explicit below-API-35 and scanned-page behavior; XML external entities/DOCTYPE disabled; archive traversal rejected; EPUB nav/NCX/spine TOC resolved against local archive entries only.

## P3 implementation progress — 2026-10-27

- Source tracing confirmed the old `SoundScreen` had no active navigation caller, the service did not attach any Murmur processors to ExoPlayer, ReplayGain metadata update was an empty method, and the screen inferred output bit depth as 16-bit and latency as zero. Removed the unreachable screen and the unused processor construction/status reporting. Updated `docs/AUDIO.md` to state plainly that processing, ReplayGain, gapless, and bit-perfect output are not implemented or verified.
- Incremental MediaStore updates now throw the existing `MusicPermissionRequiredException` if audio permission is revoked, instead of silently preserving potentially stale catalog entries. The active library observer catches the failure and runs the established full-load permission path.
- Exact-tree commands completed: `:app:testDebugUnitTest`, `:app:assembleDebug`, and `:app:assembleDebugAndroidTest` (BUILD SUCCESSFUL). Instrumentation APK was compiled only; no device test was run, per the user's instruction to defer device testing until all phases are implemented.
- P3 source changes are complete for the current phase baseline. Device validation of permission revocation, catalog mutation, queue restoration, notifications, audio focus, headset controls, Bluetooth routes, codec-specific gapless behavior, and any future DSP remains a final-gate requirement. No DSP or audio-quality claim is made.
- Reader docs now state the actual format coordinate model, metadata-fingerprint limitation, supported tap-to-position scope, persisted reader settings, explicit missing features, and input limits. Read Aloud following remains segment-level; Android TTS-generated audio has no verified sentence timing-to-source mapping, so sentence highlighting/auto-follow is not claimed.
- Focused unit tests cover anchor reset/baseline/fallback/version behavior, bounded streaming search, EPUB traversal/DOCTYPE/oversized-entry/cancellation behavior, and EPUB TOC offsets. Instrumentation covers Room-field persistence, cached-index invalidation, the 13→14 migration, API-35 selectable PDF text, and scanned PDFs. Exact-tree commands `:app:test :app:assembleDebug :app:assembleDebugAndroidTest` passed with 59 unit tests and zero failures; app and instrumentation APKs built. Instrumentation was compiled, not executed, per the user's request to defer device testing.
- P1 implementation is complete. Sentence highlighting and continuous sentence auto-follow remain intentionally unsupported because generated system-TTS playback has no verified sentence-to-audio-time mapping. Metadata fingerprint limitations and missing reader features are documented. Physical acceptance and large-file performance measurements remain part of the final P0–P5 device gate.

## P2 implementation progress — 2026-10-27

- P2 review found audiobook chapter provenance was discarded by Room and all restored chapter rows were labeled `CONTAINER`. The database now stores the parser's source (`CONTAINER`, `ID3`, or `VORBIS`); older migrated rows become `UNKNOWN`, and the player labels embedded, estimated, and unknown markers distinctly. Migration 14→15 preserves existing chapter data without claiming its source.
- The audiobook progress slider now normalizes its UI value to 0–1 and converts back to `Long` milliseconds, avoiding raw long-duration values being represented directly as `Float`. Unit coverage checks round trips near the beginning, midpoint, and end of a 237-hour duration plus clamping.
- Playback snapshots now surface Media3 playback-error presence. The audiobook player provides a route back to the Audiobooks library when a file is unavailable or access has been removed; player errors trigger progress persistence, and play retries prepare an idle failed player.
- Added instrumentation cases for 237-hour per-book progress across book switches and database recreation, chapter-source persistence, and the Room migration defaults. Reworded the long-form document so the 237-hour fixture proves value/storage range only, not full-length runtime playback.
- Exact-tree commands `:app:test :app:assembleDebug :app:assembleDebugAndroidTest` passed with 61 unit tests and zero failures; app and instrumentation APKs built. The audiobook progress and chapter provenance/migration instrumentation sources compile but were not executed, per the user's sequencing request. UI/service error recovery is build-verified only and awaits device testing.
- P2 implementation is complete. Audio-focus behavior, wired/Bluetooth route changes, service/process death, real long-duration codec seeks, and file-provider grant revocation remain in the deferred final device acceptance gate; the 237-hour fixture is model/storage evidence only, not proof of a 237-hour playback session.

## Primary references consulted for the competitive and platform review

- [Smart Audiobook Player on Google Play](https://play.google.com/store/apps/details?id=ak.alizandro.smartaudiobookplayer)
- [Voice Audiobook Player on Google Play](https://play.google.com/store/apps/details?id=de.ph1b.audiobook)
- [Moon+ Reader Pro on Google Play](https://play.google.com/store/apps/details?id=com.flyersoft.moonreaderp)
- [Musicolet on Google Play](https://play.google.com/store/apps/details?id=in.krosbits.musicolet)
- [Auxio upstream repository](https://github.com/OxygenCobalt/Auxio)
- [LibrePlayer repository supplied by the user](https://github.com/MaxFour/LibrePlayer)
- [LocalTracklist repository](https://github.com/quietswitch/LocalTracklist) (a separate current repository; its relationship to the supplied LibrePlayer link was not established in this review)
- [@Voice feature guides](https://hyperionics.com/atVoice/features/index.asp)
- [KOReader upstream repository](https://github.com/koreader/koreader)
- [Librera Reader upstream repository](https://github.com/foobnix/LibreraReader)
- [Audiobookshelf project](https://github.com/advplyr/audiobookshelf)
- [Areada upstream repository](https://github.com/iTsMe-Zen/Areada)
- [Lector upstream repository](https://github.com/QuantEmber/lector)
- [Sherpa-ONNX Android documentation](https://github.com/k2-fsa/sherpa-onnx/tree/master/android)
- [Sherpa-ONNX TTS model documentation](https://github.com/k2-fsa/sherpa-onnx/blob/master/c-api/docs/tts.dox)
- [Piper upstream repository](https://github.com/rhasspy/piper)
- [Android `PdfRenderer.Page` reference](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page)
