# Murmur validation walkthrough and release gates

> **Test plan, not app source.** This document lists checks to run; it does not certify that they have passed. Record date, commit, host/toolchain, device model/API, exact commands, and outcomes in a separate evidence note when a full pass is performed.

## 1. Before testing

1. `git status --short` and record commit. Do not test a claim against an uncommitted delta without recording that fact.
2. Confirm JDK 17, wrapper version, SDK platform/build tools, device `adb devices` state and Android API.
3. Check `app/src/main/AndroidManifest.xml` plus merged debug/release manifests for absence of `android.permission.INTERNET`; inspect dependencies for unintended network services.
4. Start with existing code tests, then relevant targeted tests, then a clean build. Keep installed-user data if it contains user media; do not clear app data except on an explicitly disposable test profile/device.

## 2. Build and automated verification

From PowerShell at the project root, set the host JDK/SDK if they are not already configured:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:ANDROID_HOME = 'C:\Android\Sdk'
.\gradlew.bat --version
.\gradlew.bat test
.\gradlew.bat assembleDebug
adb devices
```

If a supported physical device appears as `device` (not `unauthorized`/`offline`), run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat installDebug
adb shell am start -n com.joy.murmur/.MainActivity
```

The repository currently has instrumentation coverage for navigation, Room playback persistence, and DataStore settings. Inspect the actual test tree before relying on those names or assuming additional unit tests exist. A successful build proves compilation/package tasks only; it does not prove playback, UX, TTS voice availability, or persistence on a physical device.

## 3. First-run and navigation UX

- Install fresh on a disposable device/profile with no local audio/documents selected.
- Launch into Continue. Verify no demo media or fake recommendations appear.
- Visit Music, Audiobooks, and the combined Read & Listen destination; use system Back and in-app navigation.
- Verify zero-content states explain the next useful action without requesting permissions on entry.
- Test small and large font/display scales, system/light/dark theme, landscape and gesture navigation where applicable.
- Inspect TalkBack labels, focus order, heading semantics, contrast, touch targets, loading/error states, disabled controls, and keyboard/switch access.

## 4. Settings and process recreation

- Change each stored setting independently: appearance, reader text size, Strict Offline Speech, selected voice, background music refresh.
- Force-stop and relaunch. Verify preference values return.
- Verify background music refresh defaults disabled and no worker runs until explicitly enabled.
- Verify Strict Offline Speech defaults enabled, persists, and is reevaluated after voice changes or engine updates.

## 5. Music catalog

- Deny audio permission; tap around all Music sections and confirm no scan occurs until Scan device is selected.
- Grant permission; scan an empty library and a non-empty library.
- Search by title/artist/album; open song, artist, album, and folder group; play now/next/add; exercise queue reorder/remove/clear.
- Create/rename/delete a playlist; add/remove tracks; reorder tracks and playlists; play a playlist.
- Add/edit/remove one local file while the app is active. Check item notifications update only affected rows and broad changes reconcile without duplicate tracks.
- Force-stop/relaunch and verify Room restores the catalog snapshot and playlists.
- Revoke permission and remove media; verify stale catalog entries resolve honestly and no background job queries without permission.
- Enable/disable daily refresh and inspect unique WorkManager state and battery-not-low constraints.

## 6. Audiobooks and shared playback

- Import supported M4A/M4B/MP3 and a format with no embedded chapter frames.
- Verify metadata/art/chapter absence is honest; test search, bookmarks, chapter seeks, speed, transport and queue.
- Pause mid-item, force-stop process, relaunch, resume explicitly, and verify saved progress. Reboot and ensure no prohibited autoplay.
- Test missing/revoked URI, unknown duration, malformed metadata and seek at 0, last valid position and beyond duration.
- Exercise system notification, headset media buttons, audio focus interruption, route change and unplug/noisy output.
- Simulate large duration values above 24 hours, 100 hours and 237 hours using a lightweight fixture; verify `Long` paths and bounded UI. This does not substitute for real long playback.

## 7. Documents and reader

Prepare user-owned or disposable fixtures: UTF-8 text with/without BOM, UTF-16LE/BE, CRLF/LF, Markdown heading/list/blockquote/link/code, HTML with script/style/head and entities, valid EPUB with spine order and nested paths, malicious ZIP path/XML external entity/oversized ZIP, text PDF, image-only scanned PDF, corrupted PDF, and revoked SAF grant.

- Import, open, scroll/page, change text scale, bookmark/jump/remove bookmark, search/click result, navigate away/back, kill/relaunch, and verify position.
- Verify HTML script/style/head do not appear as readable text; EPUB follows spine order; PDF rendering stays under pixel bounds.
- Search should return bounded results with cross-chunk matches preserved; PDF support must be API-gated and clearly distinguish selectable text from scanned-image pages.
- Exercise a large TXT (e.g., 100 MB) on representative low-end hardware. Record open/seek latency, read/scroll memory and cancellation when leaving screen. Never infer large-file performance from a small fixture.
- Remove or revoke a URI. Show an actionable error and preserve durable user notes/metadata when possible.

## 8. Read Aloud / speech engine

- Test known-offline, network-required, unknown and unavailable voice states. With Strict Offline on, prove network/unknown voices are blocked and no silent fallback occurs. With it off, warn truthfully.
- Generate short, long, empty, punctuation-heavy, emoji/non-Latin and Markdown text. Validate WAV header fields, duration > 0, complete frames, sample rate/channels/PCM conversion, atomic cache publication.
- Test page-only and continuous request, pause/resume, seek, repeat-current-segment if offered, return-to-text, changing voice, queue replacement, leaving Reader, document removal, process death and service recreation.
- Verify internal generated speech does not pollute the user's visible music/audiobook Up Next list.
- Race synthesis against cache eviction and play-now; verify active/queued/generated paths remain valid. Test corrupted WAV and disk full.
- Measure the delay between speech segment transitions on real devices. Do not assert gapless playback from code structure alone.
- Confirm source offsets map correctly. Markdown cleanup changes string length; PDF extraction order may differ from page layout. If mapping isn't reliable, use a clear page/segment-level status and do not fake sentence or word highlighting.
- Test huge document seek without queueing all segments or starting one coroutine per sentence.

## 9. Resilience and OEM matrix

For each target device record make/model, Android release/API, security patch, battery mode, notification permission and app battery restriction. Test screen-off idle, battery saver, Doze, app process kill, force-stop, reboot, notification denied, audio focus loss, Bluetooth connect/disconnect, wired unplug, low storage, media removed, TTS engine disabled/removed, permission revoked, and worker interrupted.

Expected results: no false promise that OS/OEM limits were disabled; queue/source metadata recover; playback does not auto-start against policy; resumable work is bounded/idempotent; missing sources offer a recover/reselect action; failures remain local and user-visible.

## 10. Backup/storage (when implemented)

- Preview expected backup contents and byte estimate before export.
- Test destination cancellation, permission denial, insufficient space, process death, corrupt/truncated/oversized/unknown-version archive, unsupported future version, duplicate IDs and missing original media.
- Restore into staging/transaction; verify existing live data survives every pre-commit failure. Never overwrite source media unless the user explicitly chose a media-copy operation.
- Verify cache files are excluded and URI grants are not represented as portable permissions.

## 11. Performance, accessibility, security, release

- Capture cold/warm startup; list scroll; first audio play; large reader page/seek; EPUB parse; PDF render/search; TTS cold synth/cache hit; resume after process kill. Keep device/API and fixture sizes with the measurements.
- Inspect Room migration coverage and generated schemas; test supported old DB versions through every migration route.
- Inspect third-party licenses, native ABIs and package size. Treat non-fatal native strip warnings as non-blocking unless a real release-size or packaging failure is measured.
- Review dependency manifest, merged manifest, exported components/intents, SAF URI validation, parser limits, XML entity disablement, ZIP bounds, log contents and repository secret scan.
- Release build signing must use owner-managed secrets outside the repo; never commit keystore/passwords. No release claim is complete until reproducible build/install and owner signing procedure are confirmed.

## 12. Evidence report template

```text
Date/time and timezone:
Commit / branch / dirty state:
Host and JDK / Gradle / Kotlin / SDK:
Commands and exit status:
Device model / Android API / build:
Scenarios passed:
Scenarios failed, with log excerpt:
Scenarios not run and why:
Known limitations:
APK artifact path and SHA-256:
Remote commit pushed (if any):
```
