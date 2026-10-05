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
| 9. Audio quality | Partial / planned | Shared Media3 path exists; ReplayGain, headroom/clipping, actual effects/output-route honesty, and measurement are not a complete sound suite. Avoid fake bit-perfect/gapless claims. |
| 10. Native Android integration | Partial | MediaSession/notification and SAF/MediaStore paths exist; home-screen widget, shortcuts/deep links, full route/noisy/audio-focus and platform version matrix need audit. Android Auto remains out of scope. |
| 11. Storage / backup | Planned | No verified backup/restore UX, transaction snapshot, conflict handling, or storage-center operation. Do not copy source media implicitly. |
| 12. OEM resilience | Planned | Work scheduling exists for catalog refresh; no verified OEM diagnostic flow, reboot/background matrix, or truthful battery-policy guidance. Never promise OEM settings defeat OS limits. |
| 13. Performance / accessibility / security hardening | Partial / planned | Some size/input bounds and local-only architecture exist; no complete low-end performance, TalkBack, contrast, rapid-interaction, permissions, parser fuzz, or release security report. |

The “four remaining phases” reported earlier in chat were a coarse milestone grouping. The supplied master engineering prompt contains thirteen named phases. This table exposes the difference instead of claiming that the entire master plan is finished.

## Immediate handoff state

1. Preserve branch `main` and inspect `git status`; keep the unverified edits separate from doc-only changes.
2. Build the working tree with the JDK/SDK paths recorded in the repo host setup. Fix compilation errors before editing README status to call new behavior implemented.
3. Review and finish or safely remove partial PDF extraction work only after inspecting current code and APIs. On API <35, keep a clear unsupported status; scanned PDFs have no text unless a future local OCR feature is explicitly designed.
4. Complete Read Aloud continuity only with cancellation, queue replacement, process restoration, and source-position behavior considered. Keep the internal TTS work separate from the user's visible media queue.
5. Update code documentation and this status doc only after evidence exists; add/fix tests when the behavior changes.
6. Once feature work is complete, run the validation walkthrough, install/launch on the connected phone, and report exactly which scenarios passed and which were not run.
7. Commit source separately from planning docs where practical; push the new commit to the existing private repository. Do not force-push again unless the current user explicitly requests rewriting an existing remote history.
