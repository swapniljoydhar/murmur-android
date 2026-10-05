# Murmur engineering roadmap and execution plan

> **Planning artifact, not app source.** Phase descriptions are proposed work and acceptance criteria, not proof that a feature exists. Check the source and current user request before acting. See [`README.md`](README.md) for the agent boundary and [`PROJECT_STATUS.md`](PROJECT_STATUS.md) for the dated implementation snapshot.

## Product goal and non-goals

Murmur is an Android-only, local-first app that lets a person choose their own music, audiobooks, and documents and move between listening and reading without managing technical machinery. Keep the main navigation legible: Continue, Music, Audiobooks, and one combined Read & Listen place. Favor correctness, data safety, and reliability over feature count. Do not bundle invented media or make network connectivity a requirement.

Product invariants:

1. No account, ads, analytics, telemetry, cloud sync, online recommendation, online OCR, or `INTERNET` permission in the normal product.
2. User-selected documents stay at their original SAF URI; never silently copy, rename, delete, or alter them.
3. A stopped, interrupted, removed, or unavailable source yields recoverable state and an honest explanation, not a fabricated success.
4. One playback owner (the Media3 service) controls audible output. UI screens do not create a second ExoPlayer.
5. System TTS may be network-backed because the platform engine can be; show the engine status and enforce Strict Offline Speech. Unknown means unknown.
6. Never claim gapless, word-synced, bit-perfect, exact-resume, or offline behavior unless implementation and measurement support that exact claim.
7. Keep normal TTS work bounded by a small active window, not document length. Permanent whole-book audio generation is a separate opt-in job and is not implied by Read Aloud.

## Phase 1 — Foundation (baseline exists)

**Goal:** reproducible single-module project with clear layers, navigation, theme and local settings.

**Relevant code:** root Gradle files and version catalog; `app/src/main/AndroidManifest.xml`; `MainActivity.kt`; `ui/MurmurApp.kt`; `ui/theme`; `domain/settings`; `data/settings`.

**Current baseline:** Kotlin/Compose Material 3, target API 37, min API 26, `com.joy.murmur`, DataStore settings, Continue navigation, no intentional internet capability.

**Follow-up walkthrough:** clean checkout → run `gradlew.bat --version` → assemble debug → inspect merged manifest → launch fresh install → visit every primary destination → toggle appearance, kill process, relaunch, verify persisted choice and calm empty states.

**Exit:** clean build succeeds with pinned wrappers; all destination links work; manifest has no internet permission; settings persist after process death; no content is invented.

## Phase 2 — Playback owner (baseline exists; resilience remains)

**Goal:** one central Media3 service/session handles music, audiobooks, and TTS.

**Relevant code:** `playback/PlaybackService.kt`, `playback/PlaybackController.kt`, `playback/PlaybackModels.kt`, Room schema/migrations and MainActivity wiring.

**Baseline:** queue operations, active item and position persistence, per-media audiobook progress, system media controls/notification, restored queue paused.

**Complete/verify:** rapid play-next/add/reorder/remove/clear; stale URI removed; service survives screen navigation; pause/resume; process death; headset buttons; audio focus/interruption; Bluetooth route change; noisy output unplug; notification actions; queue database migration and rollback prevention. Verify resume seeks within duration bounds and never autoplays after reboot/process restoration without an explicit policy.

**Solution constraints:** serialize queue persistence, avoid UI/service double ownership, make `MediaItem.mediaId` stable, check URI grants, cancel stale requests and distinguish not-found from permission denial.

**Exit:** instrumented tests for queue invariants and Room transaction paths plus connected-device checks for media controls, process recreation, and non-autoplay restore.

## Phase 3 — Music (baseline exists; catalog/device QA remains)

**Goal:** local discovery and queueing from user-owned audio.

**Relevant code:** `data/music/MediaStoreMusicLibraryRepository.kt`, `data/music/MusicCatalogRefreshWorker.kt`, `domain/music`, Room catalog and playlist entities, Music UI.

**Baseline:** permission only after Scan device, SAF direct file selection, songs/artists/albums/folders, metadata search, room snapshot, item notifications, full reconciliation for broad changes, opt-in battery-aware daily refresh, playlists.

**Walkthrough:** deny permission → verify no scan; grant → scan; add/change/delete a local song → ensure incremental/full behavior; revoke permission → ensure no background access; enable periodic refresh and verify unique scheduling, disable and verify cancel; create and reorder a playlist; remove a file and ensure the playlist snapshot remains understandable.

**Risks/solutions:** MediaStore ID/URI changes, permission revocation, provider callbacks arriving in bursts, stale rows after media removal, worker duplication, battery/data usage. Debounce item IDs, reconcile broad invalidations, gate worker, keep refresh opt-in/off by default.

**Exit:** Room snapshot is atomic and recoverable; no scan on app start; works with zero media and permission denial; no network access.

## Phase 4 — Audiobooks (baseline exists; long-form acceptance remains)

**Goal:** library, embedded metadata/art, resume, bookmark, supported chapters, and stable long sessions.

**Relevant code:** `data/audiobooks`, `domain/audiobooks`, audiobook Room schema/migrations, audiobook and player composables, shared playback.

**Baseline:** SAF selection, local library, known metadata/artwork, Room progress and bookmarks, chapter frames exposed by Media3, seek/speed/queue.

**Walkthrough:** add supported formats; missing author/art/chapter; bookmark and jump; process kill during play; remove source URI; resume after reboot without auto-play; test >24h position values using `Long`; test 237-hour synthetic duration metadata without allocating a huge file; test seek boundary/unknown duration.

**Risks/solutions:** unsupported chapter structures, incorrect duration, URI grant loss, signed 32-bit overflow, image memory. Keep timestamps `Long`, bound artwork decode, treat chapters as optional metadata, validate seek against known duration, persist URI grants on picker import.

**Exit:** long duration is not narrowed to `Int`; progress survives process death; malformed/missing metadata remains safe and honest. Physical 237-hour playback is a lab test, not a promise based on synthetic metadata.

## Phase 5 — Reader (partially implemented)

**Goal:** accessible, low-friction local reading for TXT/Markdown/HTML/EPUB/PDF with save position/search/bookmarks.

**Relevant code:** `domain/documents/ReaderDocument.kt`, `data/documents/RoomDocumentLibraryRepository.kt`, reader Room schema/migrations, `ReaderScreen` and renderers in `ui/MurmurApp.kt`.

**Baseline:** SAF document selection; TXT/Markdown pages; HTML to plain text with script/style/head omitted and input cap; EPUB package/spine processing with XML hardening/archive caps; PDF rendered to bounded PNG images; reader scale, position, bookmarks; bounded text search for TXT/Markdown/HTML/EPUB.

**Work still needed:** structural location identity (EPUB spine item + in-item offset; PDF page; TXT/HTML stable position), large TXT seeking/index strategy, EPUB page parsing reuse, PDF selectable text on supported OS only, visible text for searchable PDF, scanned-PDF behavior without OCR, selection/tap-to-read, table/image/accessibility representation, file removal/revoked-grant UI, long-document memory/performance.

**Preferred strategy:** stream TXT/Markdown incrementally; measure byte/character skip on SAF and add a bounded reusable index only if profiling warrants it. For EPUB, parse package metadata and chapter content bounded; use spine item + offset as structural anchor. For PDF, use platform extraction only behind API checks and label pages with no selectable text; OCR is separate, local-only, optional and never inferred. Do not index entire unbounded documents into Room.

**Exit:** mixed encodings/BOM, line endings, HTML entities, EPUB path traversal/ZIP bomb/XML external entity protection, malformed PDF, revoked URI, >100MB text, missing text, and process restoration are tested. Reader remains usable with TalkBack and scaled text.

## Phase 6 — System TTS (partial; current area of active implementation)

**Goal:** private system TTS through the central Media3 pipeline, with truthful voice/network status and restorable reading location.

**Relevant code:** `data/speech/SystemTtsSpeechGenerator.kt`, DataStore settings, `playback/ReadAloudMediaId.kt`, `PlaybackService.kt`, reader UI.

**Baseline:** platform voice listing/selection, strict offline preference default-on, reported PCM conversion, bounded speech segments and atomic WAV output/cache, shared player integration.

**Finish in this order:**

1. Build and review the current uncommitted local delta; do not assume the PDF/continuous paths compile.
2. Make the speech segment identity explicit: document identity, parser version, page/section and source range, speech-text hash, system engine/voice, and transformation settings that actually affect synthesis.
3. Separate text-reader address units from PDF page units. Persist segment-start while speaking and advance only on successful natural completion. Do not claim exact sentence/word synchronization without timestamps.
4. Continue to the next bounded page only after the current page's final segment ends and the queue has not been replaced/stopped. Cancellation on document removal, app/service death, voice change, seek, and failed engine is required.
5. Keep a bounded look-ahead. Measure transition delay on at least a representative device before increasing prefetch. Avoid producing all speech for a book/document.
6. Add obvious return-to-document and current-page indication. Repeat-current-segment may be offered only when current segment identity is unambiguous.
7. Exercise offline, network-required, unknown, missing voice, missing engine, engine callback timeout/error, storage-full and repeated tap behavior.

**Sentence mapping:** save source start/end and a stable structural anchor per segment. If one segment contains several sentences, word/sentence timing is not available merely because text has punctuation. Keep current segment highlighting only if source range transformation is reliable; Markdown cleanup needs an offset map or an explicitly approximate status. PDF text ordering may differ from visual columns; don't present it as OCR or fully accurate layout extraction.

**Exit:** bounded RAM/queue; repeat, pause, seek, return, and process restoration behave predictably; Strict Offline is enforced after voice change and continuation; errors never silently switch to network voice.

## Phase 7 — Long-form hardening (required before calling the app dependable)

**Goal:** prove no resource use proportional to a 200+ hour item and no data loss across ordinary failure.

**Areas:** bounded speech prefetch/queue; per-segment independent cache identity; synchronization across app/service TTS generators; playback cache pinning; cancellation/backpressure; SAF reopen cost; 64-bit media/position arithmetic; fast seek strategy; process-death recovery; cache corruption and storage-full handling.

**Proposed solution:** a small scheduler (one active synthesis plus a measured near-future horizon); unique continuation job per playback/document generation; monotonic request generation token cancels stale results after seek/queue replacement; cache temp file → validate WAV header/data → atomic rename; one process-wide cache lock or lease manager shared by all generator instances; protect current and queued segments and active writes from eviction; LRU/age + total byte caps; never delete original media; retry only bounded transient failures.

**Tests:** fast synthetic 237-hour metadata fixture; repeated seeks across range; process kill while playing, synthesizing, and after an ended segment; same cache request from UI and service at once; corrupt/truncated WAV; disk full; source URI revoked; voice uninstalled; rapid repeated controls; verify queue and index remain bounded.

**Exit:** deterministic cleanup, no active file eviction, one owner per continuation, bounded generation/queue/RAM observed under profiling, all failure states recover without losing source or bookmark state.

## Phase 8 — Neural TTS (optional research gate)

**Goal:** only add a local neural engine if users need it and it improves quality enough to justify native model size and maintenance.

**Do not implement by default.** First compare Android system TTS on representative offline voices. If still authorized, evaluate runtime/library versions from official primary sources, licenses for runtime and model weights, supported ABIs/min API, package/download size, memory, warm-up, speed, battery, voice quality, storage, and no-network behavior.

**Architecture:** keep a narrow speech-engine interface; implement system TTS first; optional local model adapter must return the same validated PCM/WAV segment abstraction; user explicitly installs/selects the model; model hash/version included in cache key; model absent/corrupt yields system TTS or clear failure only according to selected user preference.

**Exit:** license report, offline packet-capture/network test, ABI/device matrix, memory/latency benchmarks, model removal/recovery, and user approval. Otherwise document deferment and ship system TTS.

## Phase 9 — Audio quality controls (honest subset only)

**Goal:** useful audio controls without false bit-perfect claims.

**Audit first:** active Media3 configuration and Android audio route. Test actual supported `AudioEffect` capabilities and report device/OS limitations. Do not claim sample-rate preservation based on source metadata.

**Potential sequence:** playback volume/headroom prevention; optional user-controlled EQ if effect is supported and lifecycle-safe; ReplayGain only after tag parsing and a defined album/track gain and peak policy; clipping prevention validated with peak fixtures; format/codec info only from parsed source/decoder evidence. Gapless behavior must be measured across formats/device routes before being advertised.

**Risks:** route-specific effects, double processing, clipping, stale effect sessions, licensing/codec support. Default off and explain controls in user terms.

**Exit:** blind-simple UX, no unsupported controls shown as active, repeatable signal tests, route/disconnect handling, and accurate documentation.

## Phase 10 — Native Android integration (partial)

**Baseline:** MediaSession, notification, media buttons, SAF, permission-gated MediaStore, WorkManager for optional catalog refresh.

**Audit/build:** foreground service lifecycle and notification on target API; audio focus; noisy route; headset/Bluetooth controls; persistent URI grants and Open With/share receive; reboot behavior; launcher shortcuts/deep links only if they materially improve opening a user-selected library item; home widget only after app navigation and player state are stable.

**Out of scope:** Android Auto per prior project decision unless the user reopens scope.

**Exit:** API 26 to current supported OS matrix, denied/limited permission path, notification controls, no autoplay at boot, external intents validate URI/MIME and do not trust untrusted extras.

## Phase 11 — Storage center and backup/restore (planned)

**Goal:** let users understand local app data and create recoverable, user-controlled backup without silently copying original media.

**Backup contract:** use SAF destination selected by user; versioned manifest; export only explicitly enumerated app-owned metadata/preferences/bookmarks/playlist definitions/progress. Treat media references as non-portable URI hints, not guaranteed restored files. Generated disposable speech cache is excluded. Source media copied only through a distinct explicit opt-in with clear size estimate and destination confirmation.

**Restore contract:** validate archive size/version/checksum before DB mutation; show preview; use transaction or staging DB then atomic swap; define duplicate/conflict handling; preserve current state on malformed or partial archive; never restore permissions, execute bundled content, or open untrusted links. Keep schema migration path for backup format independent from Room schema.

**Exit:** backup interrupted, disk full, invalid archive, older/newer version, duplicate library entry, missing source media, and process death tests. Clear total/local/cache/source-storage distinction in UX.

## Phase 12 — OEM resilience and diagnostics (planned, platform-honest)

**Goal:** explain observed interruptions and safe Android settings without promising to override the OS/OEM.

**Diagnostics data:** local, user-triggered; collect only relevant service state, notification permission, battery restriction state where public APIs expose it, last interruption category, and worker status. No telemetry upload. Clear and export only by user action.

**OEM help:** detect known behavior carefully; provide neutral Android/OEM-specific steps only when current OS/device evidence supports them. Never use accessibility automation, hidden APIs, or force-disable system restrictions. Let user open system setting intents voluntarily; app remains usable if no workaround exists.

**Test grid:** AOSP/emulator + representative OEM versions; Doze, screen-off, battery saver, reboot, permission revoked, notification denied, service process killed, audio focus loss, storage pressure. Record exact model/build/OS and observed outcome.

**Exit:** diagnostics all local; no false “fixed” status; app can resume or clearly explain an OS-imposed stop.

## Phase 13 — Performance, accessibility, privacy, and release hardening

**Performance:** benchmark startup, catalog query, playlist composition, large text open/scroll/seek, EPUB parse, PDF render/search, speech synth/cache, and scrolling with low-memory device. Bound image decode, recomposition, search results, generated audio queue, parser input and background concurrency.

**Accessibility:** TalkBack traversal/order and labels; semantic headings; text scale up to settings limit; contrast in light/dark; touch target size; button state labels; keyboard/switch access; motion reduction; honest loading/error/empty states. Reader gestures must not be the only path.

**Privacy/security:** merged manifest audit; no INTERNET or unintended network-capable SDK; SAF grant review; ZIP path traversal and expansion caps; hardened XML; no untrusted HTML WebView; input validation; backup archive checks; no secret material committed; dependencies/license review; database migration export review.

**Release:** reproducible release build, signing configuration supplied only by owner and never committed, versioning, dependency/license list, privacy claims review, APK size review (do not alter dependencies/architecture for non-blocking strip warnings), crash/ANR inspection from local test run, staged device validation.

**Exit:** `VALIDATION.md` mandatory checks pass or explicit exceptions are reported; no claims exceed evidence; clean build from clone.

## Cross-phase data and failure rules

- Persist positions in `Long`; never narrow durations/page offsets without range checks.
- Room schema changes require a forward migration and exported schema checked into `app/schemas`; test each supported migration edge or use an explicit well-justified fallback policy.
- User media deletion is never an automatic fix for a stale URI. Remove the app's library reference only after the user's explicit action.
- SAF URI access can expire; detect, explain, offer reselect, and preserve metadata/bookmarks where possible.
- For media queue updates, persist stable IDs and complete snapshots transactionally; clamp restored seek position to valid source bounds when known.
- Make cache disposable and separate from source media and durable user metadata.
- All parser/file work belongs on IO, with cancellation checks and explicit size/page caps.
- For every retry, establish idempotency and a finite retry/backoff budget.

## Recommended work cadence

1. Pick one bounded phase slice and identify acceptance criteria before code.
2. Read nearby code, domain contract, manifest/Gradle and DB schema/migrations.
3. Make the smallest coherent change with tests for the new behavior and a failure path.
4. Build, run relevant unit/instrumented tests, and install on the user's connected device when a screen/system behavior changed.
5. Inspect the resulting UI at the actual screen sizes and validate empty/loading/error states; do not rely on compilation for UX.
6. Update `README.md`, `docs/ARCHITECTURE.md`, and status only to match evidence.
7. Review staged paths for local config, secrets, generated artifacts and unrelated changes; commit; push normally. Force push only on explicit current-user request and only after verifying what remote history it would replace.
8. Report what changed, exact commands/evidence, device scenarios not run, limitations, commit and remote.
