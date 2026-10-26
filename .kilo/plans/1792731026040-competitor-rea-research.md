# Murmur Competitor Research via REA and Source Study

## Goal
Inform Murmur's architecture/UX by studying mature apps: adopt, simplify, improve, or reject patterns. No code copying; license-check before any reuse.

## Scope
- In: open-source repos (source study) + proprietary APKs (REA).
- Out: Android Auto (explicitly out of scope), cloning interfaces/branding/assets.

## Open-source (study source directly; do NOT use REA)
- Auxio — local music player: queue, Play Next/Add to Queue, MediaStore, browsing/search.
- LibrePlayer — local media: playback, library, storage.
- KOReader — reader: large-doc navigation, structural rendering, position, bookmarks, EPUB/PDF.
- Librera — reader + TTS: tap-to-read, sentence highlighting, speech queues, export.
- Audiobookshelf (Android) — audiobook grouping, chapters, resume, bookmarks.
- License note: Auxio/KOReader/Librera often GPL/AGPL; study behavior only, don't reuse code without license decision.

## Proprietary (REA on APKs the user provides)
- Smart AudioBook Player — per-book resume, chapters, sleep timer, bookmarks, long books.
- Voice Aloud Reader — read-aloud, sentence highlighting, TTS queues, export.
- Moon+ Reader — large documents, typography, EPUB/PDF, bookmarks.
- NaturalReader — TTS segmentation, export, voice handling.
- Audible — audiobook chapters, resume, completion (behavior only).
- Spotify / Apple Music — queue/Up Next, browsing (behavior only; heavy DRM, low value).

## TTS runtimes (evaluate separately)
- Sherpa-ONNX, Piper (and models) — local neural TTS; audit licenses/assets.

## REA workflow (when APK paths provided)
1. Confirm REA availability (MCP tools or CLI fallback).
2. For each APK: open_binary → inspect_android_package → targeted Android tools.
3. Extract: feature behavior, data model hints, chapter/resume handling, TTS segmentation/queueing, cache/export patterns, long-form handling.
4. Record findings with Evidence IDs, confidence, search boundary, unknowns.
5. Reconcile with Murmur's existing implementation; update docs/ARCHITECTURE.md and docs/LONG_FORM.md as needed (via implementation agent).

## Output
- Findings note per app (behavior, patterns to adopt/reject, license constraints).
- Feeds Murmur phases (especially Phase 4 audiobooks, Phase 5 reader, Phase 6 TTS, Phase 7 long-form).

## Affected Murmur boundaries
- Music: `domain.music.MusicLibraryRepository`, `data.music.MediaStoreMusicLibraryRepository`, queue/playback service.
- Audiobooks: `domain.audiobooks.*`, `data.audiobooks.RoomAudiobookLibraryRepository`, chapter extraction.
- Reader: `domain.documents.ReaderDocument`, `data.documents.RoomDocumentLibraryRepository`, HTML/EPUB/PDF parsing.
- TTS: `data.speech.SystemTtsSpeechGenerator`, speech cache, scheduler (future).
- Playback: `playback.PlaybackController` / `PlaybackService` (Media3).
- Docs: `docs/ARCHITECTURE.md`, `docs/LONG_FORM.md`, `docs/TTS.md`.

## Data flow
Competitor findings → findings notes (behavior + patterns) → design decisions recorded → implementation agent applies targeted changes to the boundaries above. No automatic code generation from this plan.

## Validation
- Findings tied to Evidence IDs / source references.
- License decision recorded before any code reuse.
- Each adopted pattern mapped to a Murmur phase and acceptance test (see master prompt tests 145–152).

## Execution order
1. Confirm REA availability and first APK path.
2. Analyze first proprietary APK; produce findings note.
3. Study first open-source repo (source) in parallel if time permits.
4. Reconcile findings with current implementation; update plan with adopt/reject decisions.
5. Repeat per app; keep bounded (max 2–3 apps before re-evaluating scope).

## Idea inspection findings (Murmur master prompt)
- The 160-rule spec is comprehensive but overloaded; many rules are duplicates or aspirational.
- Repo already implements foundation/playback/music/audiobooks/documents/system TTS; plan must preserve these, not rebuild.
- Scope risk: attempting all P0+P1+P2 at once; should gate by phases and acceptance tests.
- Long-form and process-death requirements are the differentiators; keep central.
- Android Auto explicitly out of scope (already respected).
- Neural TTS, advanced audio, backup, widgets are P1/P2; defer until P0 reliable.
- Need license decisions before any open-source reuse (GPL/AGPL).

## Open questions
- Which proprietary APK path should be analyzed first? Recommended: Smart AudioBook Player.
- Is REA connected in the client, or should I use CLI fallback?
- Should the master prompt be trimmed to P0-first, or keep full 160-rule scope? Recommended: P0-first with P1/P2 deferred.

## Risks
- License incompatibility (GPL/AGPL) if code is copied.
- Proprietary APKs may be obfuscated; findings may be partial.
- REA requires JADX/Java for Android inspection.
