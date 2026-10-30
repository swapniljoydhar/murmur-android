# Murmur source audit — 2026-10-29

Target: `app/src/main` (83 Kotlin files, ~14.9k lines incl. tests) at commit `cd4d0ff`, branch
`arena/4d74ef26-murmur-android`, measured against the product vision in `murmur_idea.txt`.

---

## 0. Method and hard limits (read this first)

What I actually did:

* Read every production source file in `app/src/main/java/com/joy/murmur` (~6.5k lines), the manifest,
  Gradle/version catalog, `res/`, Room schemas, all 17 test files and the `docs/` + `planning/` documents.
* Grepped the whole tree for constructor sites, call sites, permission strings, logging, exported
  components, `TODO`, and dead-symbol reachability. Every "dead code" claim below is backed by a grep
  that returns only the declaration (or declaration + test).
* Cross-checked claims in your list against source, and checked whether the project's own docs already
  disclose the problem.

What I **could not** do in this sandbox, and therefore do **not** claim:

* No JDK, no Android SDK, no Gradle in this environment (`java`, `javac`, `sdkmanager`, `adb` all absent;
  `$ANDROID_HOME` points at the repo directory). **I did not run `./gradlew build`, `test`, `lint`,
  `assembleDebug`, or any instrumentation test.** Nothing below is a test result.
* Therefore: no merged-manifest inspection, no APK, no `aapt dump badging`, no runtime verification of
  TTS, MediaSession, notifications, foreground-service rules, or OEM behaviour. Wherever behaviour is
  platform-dependent I say "unverified — needs a device/emulator".
* I could not resolve whether the pinned toolchain (`AGP 9.2.0`, Gradle 9.4.1, Kotlin 2.2.10,
  `compileSdk`/`targetSdk` 37, Compose BOM 2026.09.00, media3 1.11.1) is real, mutually compatible and
  available. See finding B-21.
* The `rea` git submodule is empty in this checkout, so I could not read it — it is third-party
  reverse-engineering tooling, not app code.

Severities used: **P0** user-visible crash / data loss, **P1** broken or dishonest core feature,
**P2** correctness/perf/privacy defect with a workaround, **P3** hygiene.

---

## 1. Verdict on your list of 10

| # | Your claim | Verdict | What is actually true |
|---|---|---|---|
| 1 | Lookahead/batch layer is dead and broken | **Confirmed, all sub-claims** | See A-1. Slightly worse than you thought: `TtsQueueManager` and `SharedTtsCache(Lock)` are also unreachable, and `TtsSchedulerTest` tests a *copy* of the logic, not production code. |
| 2 | MainActivity deep-link/share handling broken | **Confirmed — and it is a P0 crash**, remotely triggerable | See A-2. Also `ACTION_SEND` stream type is wrong, and no code path imports a shared URI. |
| 3 | `WidgetActionReceiver` `startService` throws in background | **Confirmed, but conditional** | See A-3. The widget also never refreshes at all, so the actions are mostly moot. |
| 4 | Two parallel media-ID systems | **Confirmed, with one correction** | See A-4. `|`-injection is real; the "neural" branch is dead. Correction: the *17/18*-field parser is the one used in production; the 7-field one is the one you called live. Both are live depending on call site, which is the point. |
| 5 | TTS cache index is memory-only; mixed identity; 22.05 kHz assumption | **Confirmed, and the pinning is a no-op** | See A-5. Sharper version: `PlaybackService` pins into a `TtsCache` instance that **never receives a `put()`**, and eviction is done by a *different* mechanism (filename scan in `SystemTtsSpeechGenerator`) that ignores those pins. |
| 6 | Backup export/restore transaction risk | **Mostly refuted** | See A-6. Writes/reads are inside `RoomDatabase.withTransaction`; restore is one transaction and rolls back on failure. The real defects are a different set (destructive `delete()` of the user's target file on export failure, hard-coded table allow-list that breaks on any schema change, no WAL checkpoint). |
| 7 | PDF text is API 35+ only | **Confirmed** | See A-7. It is disclosed in `README`, `docs/READER.md` and in-app copy — the disclosure requirement is already met. |
| 8 | EPUB TOC heuristic `contains("toc")` captures wrong files | **Refuted as described; different real bug exists** | See A-8. `parseToc()` matches only `endsWith("nav.xhtml")` / `endsWith("toc.ncx")`; `contains("toc")` only widens the *capture budget*. The genuine EPUB bug is the DOCTYPE ban, which breaks TOC/indexing for most real EPUB 3 books. |
| 9 | Processors are aspirational / might imply an active EQ | **Confirmed dead code; "implied active effect" refuted** | See A-9. No processor ever reaches ExoPlayer's audio sink, and there is no settings UI for them either, so nothing claims an effect is on. It is ~600 lines of unreachable DSP. |
| 10 | Duplicate `OutputProfileManager`; SDK/AGP on preview track; media3-inspector in release; deprecated app theme; unused WorkManager | **Mixed** | See A-10. Confirmed: two `OutputProfileManager`s (one entirely unused), `Theme.Material.Light.NoActionBar` (no night theme). **Corrected:** `media3-inspector` is *used in production* by `AudiobookChapterParser` (`MetadataRetriever`), not just shipped by accident; and WorkManager *is* used (`MusicCatalogRefreshWorker`). |

---

## 2. Confirmed defects (A-series)

### A-1 — `TtsScheduler` / `BatchTtsManager` / `TtsQueueManager` / `SharedTtsCache` are unreachable, broken code — **P1 (honesty), dead**

Reachability (grep over `app/src`, excluding `app/src/test`):

```
TtsScheduler(        -> declaration only (data/speech/TtsScheduler.kt:55)
BatchTtsManager(     -> declaration only (data/speech/BatchTtsManager.kt:58)
TtsQueueManager(     -> declaration only (playback/TtsQueueManager.kt:23)
SharedTtsCache(      -> declaration only (data/speech/SharedTtsCacheLock.kt:66)
TtsCache(            -> PlaybackService.kt:75 only (pins, never `put`)
```

Defects inside them (all still present):

| Defect | Location | Effect |
|---|---|---|
| `extractSectionText(...) = ""` | `TtsScheduler.kt:318` | Segmentation always yields zero segments → scheduler can never schedule anything |
| `generationQueue.close()` in `seekTo()` on a `val` channel | `TtsScheduler.kt:105` | Any seek permanently kills the scheduler (channel can't reopen) |
| `segmentId = UUID.randomUUID()` | `TtsScheduler.kt:279` | Cache keys never repeat across sessions → cache design defeated |
| Double callback on success | `TtsScheduler.kt:137` then `:140` | Consumer gets `success` then `failure("Storage full")` |
| `generationMutex.unlock()/lock()` inside `withLock` | `TtsScheduler.kt:121-125` | `IllegalMonitorStateException`, plus double unlock in `finally` |
| `queuedTaskCount` plain `Int` mutated from 2 workers + producer coroutines | `TtsScheduler.kt:118, 205, 248` | Racy count |
| `player` and `playbackStore` constructor params unused | `TtsScheduler.kt:55-61` | Dead parameters |
| Whole class operates on `ExoPlayer` (not `MediaController`) and mutates the player from `Dispatchers.IO` | `TtsQueueManager.kt:88-90` | Illegal thread affinity for Media3 controllers; would crash if wired |
| `TtsSchedulerTest.TestableTtsScheduler` is a hand-copied clone of `TtsScheduler.segmentText` | `app/src/test/.../TtsSchedulerTest.kt:53-140` | The unit tests give green signal for logic that production never runs; they cannot regress production |

Note: `docs/TTS.md` already admits `extractSectionText()` returns empty and that `TtsScheduler` must not
be integrated as-is. Credit for the disclosure; the code should still be deleted or finished, because
`BatchTtsManager`/`SharedTtsCache`/`TtsQueueManager` advertise a whole subsystem that does not exist, and
`BatchJobEntity`/`BatchWorkUnitEntity` persist document text (`batch_work_units.text`) for it.

### A-2 — Deep links, app shortcuts and incoming shares crash or do nothing — **P0**

`MainActivity.kt`:

```kotlin
66:  private val navController: NavHostController by lazy { NavHostController(this) }
71:  handleIntent(intent)                       // called from onCreate, before setContent
88:  handleIntent(intent)                       // and again from onNewIntent
100: "continue" -> navController?.navigate("continue")
119: intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach { uris.add(it) }
129: // Navigate to documents screen, the library will handle the import
```

1. **Orphan controller.** This `NavHostController` is never handed to `NavHost`. `MurmurApp()` creates
   its own with `rememberNavController()` (`MurmurApp.kt:218`). `NavController.navigate(route)` requires
   a graph; with none set it throws (`IllegalStateException`/`IllegalArgumentException` depending on
   Navigation version). `handleIntent` is invoked from `onCreate` *before* `setContent`, so every
   `murmur://…` intent — including the **app shortcuts declared in `res/xml/shortcuts.xml`**
   (`murmur://continue`, `murmur://music`, `murmur://audiobooks`, `murmur://documents`) and every
   `VIEW` filter in the manifest — hits this path. Expected user-visible result: long-press shortcut or
   "Open with Murmur" → crash. *Unverified at runtime (no device), but the code path is deterministic.*
2. **`DeepLinkHandler.onStart` re-runs `handleIntent`** (`MainActivity.kt:141-143`), so after a
   configuration change the same intent is processed again — double navigation if the controller were
   fixed, double crash today.
3. **`ACTION_SEND` is read with the wrong API.** Senders put a single `Parcelable` `Uri` in
   `EXTRA_STREAM` (only `SEND_MULTIPLE` uses an `ArrayList`), so `getParcelableArrayListExtra` returns
   `null`; `intent.data` is also `null` for `SEND`. Shares are silently dropped.
4. **Even when a share *is* parsed, nothing is imported.** The handler only navigates to `documents`.
   The comment says "the library will handle the import"; `DocumentLibraryScreen` has no
   shared-URI handoff (`MurmurApp.kt:916-962`). This is exactly the "fake button" pattern the vision
   forbids, and `AndroidManifest.xml` advertises 8 `SEND`/`SEND_MULTIPLE` filters + 4 `VIEW` filters for it.
5. **Security consequence:** `MainActivity` is `exported="true"`, so *any* app on the device can fire
   `murmur://music` at Murmur and crash it (remote-triggerable denial of service, trivially repeatable).
   Low impact, but it is an availability bug reachable from outside the app.

Fix direction: parse intents into a small `PendingIntentAction` holder (or `SingleTop` state) before
`setContent`, pass it into `MurmurApp`, and navigate from inside the composable with the real
`NavHostController`; and implement the actual import (`addDocuments(uris)` / `playSelected(...)`) for
`VIEW`/`SEND`/`SEND_MULTIPLE`, or remove the filters.

### A-3 — The home-screen widget does not work (crash on action, never refreshes) — **P0/P1**

* `WidgetActionReceiver.kt:14` — `context.startService(Intent(context, PlaybackService::class.java))`.
  A `BroadcastReceiver` running in the background may not `startService` on API 26+; if the service is not
  already a running foreground service, this throws `IllegalStateException`. Widget play/pause/skip is
  therefore broken exactly in the situation widgets are used for (app not in foreground, service stopped).
  *Confirmed by code; runtime exception unverified.*
* `WidgetManager.updateAllWidgets()` (`NowPlayingWidget.kt:33-41`) calls
  `notifyAppWidgetViewDataChanged(ids, 0)`. That API only refreshes **collection** (`RemoteViewsService`)
  widgets. This widget is a static `RemoteViews` layout, so **it never updates** after the first
  `onUpdate` — no title, no is-playing state, ever.
* `PlaybackService.onStartCommand` handles `widget_action` by poking `player` directly
  (`PlaybackService.kt:221-232`) instead of issuing session commands, so the widget bypasses
  `MediaController`, `MediaSession` accounting and — on API 35+/37 — the "user-initiated media start"
  bookkeeping the vision explicitly requires you to follow.
* `NowPlayingWidget.kt:57` — `runBlocking(Dispatchers.IO)` inside `onUpdate` → DB open + query on the main
  thread of a broadcast receiver (ANR/StrictMode risk).
* `WidgetConfigureActivity` is `android:exported="false"` (`AndroidManifest.xml:163-165`) yet declared as
  the widget's `android:configure` and advertised as `reconfigurable`
  (`res/xml/murmur_app_widget_info.xml`). The launcher is a different app; it cannot start a
  non-exported activity → configure/reconfigure fails with `SecurityException`. Only
  `widgetFeatures="configuration_optional"` keeps the widget addable at all.
* `res/layout/murmur_app_widget.xml` is unreferenced; `androidx.glance:glance-appwidget` is declared but
  unused (the provider is a classic `AppWidgetProvider`).

Recommendation: either drop the widget from v1 (the vision allows deferring widgets explicitly) or
rebuild it on `MediaController` + `GlanceAppWidget` with `exported="true"` config activity.

### A-4 — Two media-ID schemes, one of which is field-injection prone — **P1**

* `playback/ReadAloudUtils.kt:24` — 7 fields, `Uri.encode` applied to `documentId` **and** `cacheKey`,
  parsed with `split('|', limit = 7)` (`:41`). Used by the live producer (`PlaybackService.kt:454, 547`,
  `TtsQueueManager.kt:52`) and by the reader (`MurmurApp.kt:1095`).
* `playback/ReadAloudMediaId.kt:36` — 18 fields; encodes `documentId` but **not** `cacheKey`,
  `sectionId`, `paragraphId`, `sentenceRange*`, `textHash`, `engine`, `voice`, `locale`,
  `documentFingerprint`. `split('|', limit = 18)` (`:73`) then mis-aligns every downstream field if any of
  those values contains `|` (voices are engine-defined `name|locale|…`-style strings, cache keys and
  hashes are safe today, paragraph ids are not guaranteed).
* The same file also parses a `neural_read_aloud_start` prefix for a feature that does not exist
  (`:76, 105`), and silently rewrites `engine` back to `"android_tts"` for non-neural IDs (`:105`) —
  i.e. it both invents and discards identity.
* `ReadAloudUtils.ReadAloudSegmentInfo` and the top-level `ReadAloudSegmentInfo` are two distinct types
  with the same shape in the same package; the nested one is used only by the tests.
* `PlaybackService` uses the 17-field parser, `TtsQueueManager`/tests use the 7-field one. There is no
  version tag in the ID, so a format change silently degrades parsed fields instead of failing.

Fix: one scheme, one parser, URL-safe encoding of every free-form field (or Base64url the whole suffix),
a format version field, and a round-trip property test that includes `|`, `%`, spaces and Unicode.

### A-5 — Speech cache: pinning is a no-op, two owners, wrong durations, index lost on process death — **P1**

**Status (fixed).** The second owner and the pin API are gone (removed with A-1), so the generator is the only writer and eviction is its only reclaimer. Duration now comes from the WAV header rather than the 22 050 Hz assumption, and a truncated entry is treated as a miss instead of being played with a wrong length. One *correction to this finding*: an index was never actually needed — cache names are content-addressed (`sha256(identity|voice|text)`), so a file is found again by recomputing its name, not by lookup. The metadata that process death really lost was the duration, and that is now inside the file. Eviction and header parsing are separate unit-testable objects with tests.

* Two independent caches over the *same* directory `cache/read-aloud`:
  * `SystemTtsSpeechGenerator` (`data/speech/SystemTtsSpeechGenerator.kt:210-268`) keys files by
    `sha256(identity|voiceIdentity|chunkText)`, and evicts by scanning filenames, age (>30 d) and total
    size (>256 MB) at the *start of each synthesis* (`:308`), protecting only files whose name matches a
    URI in the persisted queue at that moment.
  * `PlaybackService.ttsCache = TtsCache(File(cacheDir, "read-aloud"))` (`PlaybackService.kt:75`) which
    is used **only** for `pin()`/`releasePin()`; `TtsCache.put()` is never called in production, so
    `metadata` is always empty and `pin()` protects nothing. `TtsCache.evictIfNeeded`, `minFreeSpace`,
    `shutdown()` and `SharedTtsCache` are all unreachable.
  * Net effect: the "cache leases cannot delete audio in use" requirement is met only by the filename
    heuristic in `evictSpeechCache`, i.e. by accident, and only while the URI is still in the queue.
    `TtsCache.shutdown()` — if it were ever wired to a "clear cache" button — would delete files the
    generator still considers valid.
* **Duration identity is wrong for non-22050 voices**: cached files get
  `estimateDurationFromFile()` = `dataSize / (22050*1*16/8)` (`:255-262`). Google's engine commonly
  synthesizes 24 kHz, and stereo is permitted by `writeWave`. Duration then drives
  `segmentDurationMs → sentenceDuration → highlight index`, so *every* sentence highlight computed from a
  cache hit is systematically stretched or compressed. Freshly synthesized segments are correct; cache
  hits are not — i.e. highlighting is right the first time a document is read and wrong on every later
  session.
* `TtsCache`'s `totalSize` bookkeeping (`data/speech/TtsCache.kt:75-88`) is also inconsistent with the
  generator's file-level cache; there is no persistent index, so after process death the generator's
  wavs are anonymous to every in-memory structure and only the filename scan can reclaim them.
* `TtsCache` has `hasFreeSpace` with a 50 MB floor, but the generator's own `hasFreeSpace()` checks 50 MB
  per chunk and the 256 MB cap is only enforced at synthesis start → a long session can overshoot the cap.

### A-6 — Backup/restore: the real defects are not the ones suspected — **P2**

What is *fine*: `createBackupBytesInternal()` and `restoreBackupBytesInternal()` both run inside
`db.withTransaction` (`StorageCenter.kt:74-90, 118-142`), restore is a single transaction with
`CONFLICT_ABORT` and a `PRAGMA foreign_key_check` at the end, and `MurmurBackupFormat` validates size,
table/column shape, cell types, caps rows at 500 k and 8 Mi-values, and verifies a SHA-256 envelope
checksum with `MessageDigest.isEqual`. Partial restore is therefore bounded to "all or nothing".

What is wrong:

1. **`createBackup` can delete a user's file.** `StorageCenter.kt:69-73`: it opens the destination with
   `"wt"` (truncate), and on *any* write failure it calls `target.delete()`. If the user picked an existing
   file (e.g. a previous backup) and the write fails, the file is gone. Should be "write to temp / report
   failure", never delete a user-chosen document.
2. **Restore is schema-brittle by construction.** `validateSchema` requires
   `content.tables.keys == databaseTables.toSet()` (`:150`) where `databaseTables` is a hard-coded list
   (`:27-33`). Adding a Room table or renaming one makes every older backup un-restorable, and the error
   is "Backup tables do not match this Murmur database." Note the list also omits `playback_state`,
   `playback_queue` and `read_aloud_segment_map` — so restoring a backup on another device silently loses
   Read Aloud segment maps (highlighting) even though the backup format claims "document structure".
   This should be versioned (SQLite `user_version`-style) rather than set-compared.
3. Whole DB image is built in memory (`ByteArray`, capped at 64 MiB) and written in one `write()` — fine
   for the cap, but no WAL checkpoint is taken before the read; the transaction makes the read consistent,
   so this is acceptable, just worth a comment.
4. No user-visible path to clear the speech cache, model packs or temp files — the Storage screen is
   read-only apart from backup/restore (`StorageCenterScreen.kt:11-12`), which the vision asks for in
   Phase 8.

### A-7 — PDF text: API 35 gate — **Confirmed, already disclosed**

`PdfTextExtractor.extractPageText`/`searchDocument` return `null` below
`VERSION_CODES.VANILLA_ICE_CREAM` (`data/documents/PdfTextExtractor.kt:24, 40`);
`RoomDocumentLibraryRepository.readPage` throws a descriptive error (`:148`);
`DocumentIndexer.indexPdf` silently returns `"" to emptyList()` below API 35 (`:122-124`), which builds an
empty document structure instead of failing. Disclosed in `docs/READER.md`, `README.md` and in-app copy
(`MurmurApp.kt:1320-1326, 947-949`). No action beyond making `indexPdf` fail loudly rather than index
zero text.

### A-8 — EPUB: the DOCTYPE ban breaks real books; the "toc" name heuristic is harmless — **P1**

`EpubParser.parseXml` (`:83-93`) sets
`http://apache.org/xml/features/disallow-doctype-decl = true`. This is the standard XXE hardening and the
repo even tests it (`EpubParserTest.epubXmlRejectsDoctypeAndExternalEntityDeclarations`). But it also
rejects *benign* document type declarations, and real EPUB 3 `nav.xhtml` files essentially always start
with `<!DOCTYPE html>`. Consequences:

* `getEpubToc()` → `parseToc()` → `parseNavXhtml()` → `parseXml(navBytes)` throws for those books; the
  reader shows an error and falls back to nothing (spine fallback is only reached if the NCX also fails —
  and NCX files `toc.ncx` normally have no DOCTYPE, so NCX-only books survive).
* `DocumentIndexer.indexEpub` parses `container.xml` and `.opf` (usually DOCTYPE-free), so indexing usually
  still works — the breakage is specific to EPUB 3 navigation documents.
* Note the JVM unit test passes while Android's `DocumentBuilderFactory` may not even recognise
  `disallow-doctype-decl` (it can throw `ParserConfigurationException` at `newDocumentBuilder()`); either
  outcome (DOCTYPE rejected, or factory rejects the feature) is a real risk that no test in the repo
  covers, because the tests run on the JVM's Xerces.

Correct hardening keeps external entity/DTD *resolution* disabled but permits the declaration
(`setFeature(..., external-general-entities, false)` + `setFeature(".../load-external-dtd", false)` +
`setExpandEntityReferences(false)` + a custom `EntityResolver` that returns empty). Add an EPUB-3 fixture
with `<!DOCTYPE html>` to the test suite.

On the name heuristic you flagged: `isToc` in `readEpubEntries` (`:48`) does use
`name.contains("toc", ignoreCase = true)`, but that only decides the *capture budget* (4 MiB vs 0).
TOC discovery uses `endsWith("nav.xhtml")` / `endsWith("toc.ncx")` (`:130, 143`). The real cost is
needless memory: `stock.xhtml`, `protocol.html`, `contact.xhtml` etc. all contain "toc" and get fully
buffered (bounded by the 16 MiB `MAX_CAPTURED_BYTES`). Tighten to `nav.xhtml`/`toc.ncx`/`*.ncx`.

### A-9 — Audio processors are unreachable; no false effect claims — **P1 (dead), claim refuted**

`playback/{Equalizer,ReplayGain,HeadroomLimiter,Crossfeed,Dither,Balance}Processor` + `AudioProcessor.kt`
(~600 lines) are never referenced. The only occurrence of the Media3 processor API in production is a
**stray import** in `PlaybackService.kt:12` (`import androidx.media3.common.audio.AudioProcessor`) that is
unused. `ExoPlayer.Builder` is created with no `DefaultAudioSink`/processor chain
(`PlaybackService.kt:79-88`). There is also no UI anywhere for these settings
(`grep -i 'equalizer|replaygain|crossfeed|headroom|dither' ui/` → zero hits), and
`SourceInfoExtractor` (ReplayGain parsing, 481 lines) is only reachable through
`PlaybackController.getSourceInfo()`, which has **no callers**.

So: nothing claims an effect is active (good), but ~1,100 lines of DSP/metadata code plus
`processor_settings` table, `data/playback/OutputProfileManager` and `playback/OutputProfileManager` are
maintenance debt that a future contributor will wire up believing they work. `EqualizerProcessor`'s
coefficient layout (`[b0/a0, b1/a0, b2/a0, 1f, a1/a0, a2/a0]` with a literal `1f` in the denominator slot)
does not match a direct-form biquad and would be audibly wrong if enabled — verify before wiring.
Recommendation: delete, or gate behind a `docs/AUDIO.md`-documented "not implemented" state.

### A-10 — Corrections to your list

* `media3-inspector` is **production code**, not a stray debug tool: `AudiobookChapterParser.kt:8, 55-95`
  uses `androidx.media3.inspector.MetadataRetriever` for M4B chapter frames. That is a legitimate use, but
  it is `@UnstableApi` and it is a debug-oriented artifact, so it should be a documented trade-off in
  `docs/LICENSING.md`/`docs/AUDIO.md`, not silently in `implementation(...)`.
* WorkManager **is** used: `MusicCatalogRefreshWorker` + `MusicCatalogRefreshScheduler`
  (`data/music/MusicCatalogRefreshWorker.kt`), scheduled from `MusicLibraryScreen` (`MurmurApp.kt:397-403`).
* `Theme.Material.Light.NoActionBar` (`AndroidManifest.xml:19`) is confirmed and does matter: there is no
  `values-night` and no `values/themes.xml`, so the window background is light in dark mode (visible flash
  before Compose draws, and a light preview in Recents). Cheap fix: a `Theme.Murmur` with
  `DayNight`/`windowBackground` + `SplashScreen` API.
* Two `OutputProfileManager` classes confirmed: `playback/OutputProfileManager.kt` (constructed in
  `MainActivity.kt:45-47`, then never used) and `data/playback/OutputProfileManager.kt` (no constructor
  call anywhere). Both dead; `OutputProfile` models are duplicated too.

---

## 3. New findings you did not list (B-series)

### B-1 — The service-side Read Aloud *start* path is unreachable dead code — **P1**

`PlaybackController.startReadAloud()` (`playback/PlaybackController.kt:260`) is the **only** producer of
`read_aloud_start|…` media IDs, and it has **no callers** (grep: declaration only). The reader synthesizes
audio itself and calls `playSelected(...)` instead (`MurmurApp.kt:1074-1120`).

Consequences:
* `PlaybackService`'s whole `read_aloud_start` branch (`:95-119`), its `startReadAloud(mediaId)` (`:466+`),
  the `mimeType`/`strictOffline`/`continuous` fields it re-derives, `ReadAloudStatus.clearFailure` there,
  and the `PlaybackRestorePolicy` filter that strips those IDs (`data/playback/PlaybackRestorePolicy.kt:13`)
  all exist to support a path nothing takes.
* `PlaybackController.startReadAloud` also sets `.setUri(documentUri)` on a media item that ExoPlayer would
  immediately try to *decode* (a `text/plain`, `application/epub+zip` or `application/pdf` URI). If anyone
  wires it up, the user gets a guaranteed `UnrecognizedInputFormatException`. Remove it, or route the item
  through a placeholder that cannot be prepared.
* `docs/ARCHITECTURE.md`/`docs/TTS.md` describe "the service can synthesize the next page", which is true
  for continuation only; the start path is UI-side. Worth stating plainly in docs.

### B-2 — Sentence highlighting and Tap-to-Read are broken for the *first* page, and Tap-to-Read never seeks to a sentence — **P1 (documented behaviour is wrong)**

1. Segment maps are written by `PlaybackService.persistSegmentMap(...)`, called only from the
   *continuation* path (`PlaybackService.kt:452, 545`). The reader's own synthesis
   (`MurmurApp.kt:1074-1120`) persists nothing. `ReaderScreen` looks up
   `dao.getSegmentMap(documentId, sourceOffset)` (`MurmurApp.kt:1029-1042`), so on the page you press
   "Read from here" on, `currentSegmentMap == null` → `currentSentenceIdx` stays `null` → **no highlighting
   at all** (there is no segment-level fallback despite `docs/READER.md` claiming one), and the
   tap-to-read branch (`if (currentSegmentMap != null …)`, `MurmurApp.kt:1428-1438`) never runs.
2. `ReadAloudUtils.mapTapOffsetToSegment` (`playback/ReadAloudUtils.kt:161-180`) computes `sentenceIdx`
   over `sentenceStartOffsets` and then **returns `segmentMap.sourceOffset` regardless**. So even when a
   map exists, a tap maps to the start of the whole synthesized segment (up to 12 000 characters away), not
   to the tapped sentence.
3. `docs/READER.md` states "Tap any sentence … speech seeks to that sentence (±1 sentence tolerance)" and
   "Segment highlighting … fallback segment-level when no sentence map is available". Both statements are
   false against the current code. Under the vision's "README must not claim features absent from code",
   this is a documentation defect *and* a functional defect.

Fix: persist a segment map from the reader's synthesis (it already has `GeneratedSpeechFile.sentenceStartOffsets`),
use the computed `sentenceIdx` to select the target segment (or seek within the WAV by
`(sentenceOffsets[i]/segmentDuration)`), and correct the doc text.

### B-3 — Two writers to the playback queue table; DB churn — **P2**

* `PlaybackService` persists a full snapshot (delete-all + insert-all of the queue, plus
  `playback_state` and `listening_progress`) on **every** `onEvents` callback
  (`PlaybackService.kt:194-197`) *and* every 5 s in a `while(true)` loop (`:207-213`). `onEvents` fires on
  every state/position-parameter change, not just every 5 s.
* `PlaybackController.enqueue()` reads `store.queue()` and calls `store.replaceQueue(combined)`
  (`PlaybackController.kt:221-235`) from its own view of the world, then applies `insertAt` computed from
  the *DB* list to the *live player* (`:246-256`). The two views can disagree (read-aloud items appended by
  the service, restore in progress, a concurrent snapshot write between read and write).
* Result: possible duplicate/lost queue entries, wrong insertion index, and O(queue) DB writes several
  times a second on a long audiobook queue. Also `snapshotPlayerState()` rebuilds the whole queue list
  (`:576-600`) on every tick.
* Fix: one writer (the service, as owner), coarse-grained change detection (dirty flag on media-item
  transitions and periodic position writes only), and `addMediaItems(index, ...)` driven by the player's
  own position rather than a DB-derived index.

### B-4 — Reader synthesis is unbounded, uncancellable, and serialized with service playback — **P2**

`ReaderScreen.startReadAloud` synthesizes the entire 12 000-character page in one call
(`MurmurApp.kt:1083-1090`) with `synthesizing = true` and no cancel affordance. `synthesizeChunk` allows
`withTimeout(120_000)` per chunk (`SystemTtsSpeechGenerator.kt:286`) and chunks are sequential, so a slow
engine can hold "Preparing speech…" for many minutes; the only way out is Back (composition disposal).
Because `synthesisMutex` is a process-wide companion mutex (`SystemTtsSpeechGenerator.kt:407-409`), this
also **blocks the service's continuation synthesis** for the same duration while read-aloud is playing.
The vision explicitly requires a bounded, cancellable, look-ahead scheduler near the play position — which
is exactly the dead `TtsScheduler`. So the honesty issue is the reverse of "unimplemented": a *worse*
scheduler is live and the designed one is dead.

### B-5 — Bookmark/anchor model does not match the stated anchor contract — **P2**

* Reader bookmarks store only a raw `positionChars` (`reader_bookmarks` schema), despite
  `docs/READER.md`'s own admission ("Paragraph/sentence anchors and reflow-stable restoration are not
  implemented"). A one-character insertion near the start of a file invalidates every bookmark and the
  saved position for TXT/HTML/EPUB — and because `sourceFingerprint` is size+mtime-based, a
  size-preserving edit or a provider that omits metadata (`sourceFingerprint` returns `null`,
  `RoomDocumentLibraryRepository.kt:357-380`) will *not* even detect the change; the user is dropped into
  the middle of unrelated text with no warning. The vision requires structural anchors and "tell the user
  instead of silently jumping".
* `validateDocumentSource` resets to 0 on change (good) but never informs the reader about bookmarks
  (the doc claims "asks the reader to review bookmarks" — nothing in `MurmurApp.kt` does that).

### B-6 — Strict Offline Speech labels every non-network voice "Known Offline" — **P2 (honesty)**

`MurmurApp.kt:2426-2433` derives `requirement` for non-selected voices as
`requiresNetwork → NETWORK_REQUIRED`, `requiresInstall → MISSING_DATA`, `else → KNOWN_OFFLINE`, and
`:2415` renders that as the text **"Known Offline"** with a "✓". The selected voice takes the same
classification path (`SystemTtsSpeechGenerator.kt:35-39`). The vision is explicit: "A voice reporting no
network requirement is useful evidence, not absolute proof… Never label a voice guaranteed offline based
only on that property." A third-party engine that needs the network but misreports the flag will be shown
as "Known Offline" and pass the Strict Offline gate.
Also `UNAVAILABLE`/`UNKNOWN` are almost unreachable for enumerated voices (only for a `null` voice), so the
honest "Unknown" state the design requires does not really exist.
Fix: separate `declaredOffline` from `verifiedOffline`; show "Declared offline by engine" unless the user
ran a local verification; keep a documented manual test path (the repo already has
`OFFLINE_TTS_TEST_PROCEDURE.md` — surface it in-app instead of only in the repo).

Adjacent honesty bugs on the same screen:

* `MurmurApp.kt:2519` tells the user to "import a compatible local speech model". No model import exists
  (`NeuralTtsManifest` is a data class with no importer, `neural-voices/` is never written).
* `StorageCenterScreen.kt:104` shows a "Neural voice assets" row that is always 0 for a feature that
  does not exist.
* `createTtsSettingsIntent()` (`MurmurApp.kt:136-140`) builds `Intent("com.android.settings.TTS_SETTINGS")`
  and launches it with `context.startActivity(...)` **outside any try/catch**
  (`MurmurApp.kt:2365-2369`). That action is not public API; on devices without it, "Open TTS Settings"
  crashes the app. Use `Settings.ACTION_ACCESSIBILITY_SETTINGS`/engine settings as fallback and check
  `resolveActivity`.

### B-7 — Reader paging is O(position) per page and indexing is eager — **P2 (vision: incremental, never full load)**

* `readPage` for TXT/HTML reopens the stream and **discards `startChar` characters** on every call
  (`RoomDocumentLibraryRepository.kt:143-190`), so reading page N of a 40 M-character document re-reads
  40 M characters each time. Doing so is cancellable (good) but O(n²) across a session.
* `DocumentIndexer.buildIndex` loads the whole document into a `StringBuilder` (limit
  `MAX_TEXT_CHARACTERS = 50 * 1024 * 1024`, `:37`), then `text.split("\n")` for sectioning
  (`:236, 276`) — 50 M UTF-16 chars ≈ 100 MB, plus the split list. On a 2 GB or 3 GB device this is an
  OOM candidate, and the limit is described to the user as "50 MB" while it is 50 M *characters* (~100 MB
  of UTF-16, more for non-ASCII). EPUB indexing concatenates all spine chapters into one `StringBuilder`
  (`:100-130`) — the exact "load the whole book" pattern the vision forbids, bounded only by the 16 MiB
  capture cap.
* `searchText` and the "whole text" path require the same in-memory text.
* Fix: page/chunk the index (store per-section offsets and read sections on demand), make
  `MAX_TEXT_CHARACTERS` a byte budget with a stated unit, and stream sectioning line by line.

### B-8 — Accessibility and theme gaps — **P2/P3**

* Contrast: `onSurfaceVariant = #66777A` on `background = #EEF2F2` ≈ **4.1:1**, on
  `surfaceVariant = #E5EEEE` ≈ **3.9:1** (`ui/theme/MurmurTheme.kt:19-29`). `bodySmall` is 12 sp and this
  colour is used for virtually all secondary text → fails WCAG AA (4.5:1) for normal text.
* No `values-night`/`DayNight` app theme (see A-10) and no `SplashScreen` handling.
* Reader text scaling multiplies spans manually (`MurmurApp.kt:1408-1418`, `:1450-1456`) instead of
  relying on `fontScale`, so with a system font scale of 2.0 the effective sizes are `1.6 × 2.0` in places
  and clip in fixed-height containers (`height(140.dp)` for search results, `:1291`).
* Icon-only controls use text labels throughout (good), `MurmurIconButton` exists but is not used with
  content descriptions anywhere (`ui/components/MurmurComponents.kt:169-178`); PDF page `Image` has a
  description (good); reader tap area has a semantics `onClick` fallback (good).
* The editorially nice `MurmurTopBar` shows "Back" as text in the top bar *and* the mini-player occupies
  the bottom bar; with large fonts the top bar title + "Back" + "Settings" can collide.

### B-9 — i18n: 142 hard-coded UI strings — **P3 (explicit vision requirement)**

`grep -o 'Text("' ui/MurmurApp.kt | wc -l` → **142**; `strings.xml` contains 25 entries, all for
shortcuts/widgets. Every user-facing sentence in the reader, settings, storage, now-playing and error paths
is a Kotlin literal, so future localization is a rewrite. Move to `strings.xml` (or at least
`strings.xml` + `stringResource`) and add a lint `HardcodedText` gate.

### B-10 — Build, schema and repo hygiene — **P3**

* `app/schemas/…PlaybackDatabase/` has `1,2,3,4,6,…,15` — **`5.json` and `16.json` are missing** while
  `version = 16` and `exportSchema = true`. Missing 5 means a 4→5→6 chain cannot be verified;
  missing 16 means the committed DB version has no exported schema (so a fresh `ksp` run will produce a
  diff, and `MigrationTestHelper` cannot validate 15→16).
* No `buildTypes` block at all (`app/build.gradle.kts`): no `release` config, no `isMinifyEnabled`, no
  signing config, no `proguard-rules.pro`. Fine for a debug-only milestone; not a release deliverable.
* No `kotlin-android` plugin is applied — the module relies on AGP 9's built-in Kotlin support. That may be
  intentional for AGP 9; it is unverified here (no toolchain) and should be stated in `docs/`.
* Out-of-scope files inside the app repository: a git submodule pointing at
  `https://github.com/morluto/rea.git` (empty in this checkout), plus `.agents/` (176 KB) and `.kilo/`
  (92 KB) prompt/skill files. These are not app source; they make the repo look like an agent workspace and
  break `git clone --recurse-submodules` without network.
* `versionName = "0.1.0"`, `versionCode = 1`; `docs/` and `planning/` are genuinely good and should stay.

### B-11 — Misc correctness nits worth fixing while in the area — **P3**

* `PlaybackService.onCreate` restores the queue but never calls `prepare()` and leaves
  `restorationComplete` false until the DB read finishes; any `onEvents` before that is dropped (fine) but
  a user pressing play during restore can race. Restored `read_aloud|…` items point at `file://` WAVs in
  the cache; if the cache was cleared externally (or by another install) the user gets
  "Read Aloud audio couldn't be played."
* `PlaybackService.onDestroy` runs `runBlocking(Dispatchers.IO)` on the main thread (DB writes + document
  position write). If the OS kills the service under memory pressure this becomes an ANR risk.
* `SleepTimer` persists its deadline in `SharedPreferences` (fine) but is only rescheduled when
  `PlaybackService` is recreated. If the service is killed by the system, the timer silently dies while the
  UI keeps counting down (`SleepTimerControl` polls the same preference, `MurmurApp.kt:1905-1916`), so the
  countdown reaches zero with playback still going.
* `SleepTimerControl` polls `SharedPreferences` every second, and `MurmurApp` calls
  `playbackController.refreshPosition()` every second in a `while (playbackController != null)` loop
  (`MurmurApp.kt:211-215`) — two permanent 1 Hz loops for as long as the UI is composed. Prefer the
  existing `StateFlow` + `Player.Listener`, or a single 250-500 ms tick from the service.
* `ReadAloudStatus` is a process-local `object` (`playback/ReadAloudStatus.kt`): failures are invisible
  if the process that reported them is recreated (correctly documented, worth keeping in mind for UI).
* `MurmurApp` builds `OemCompatibility(LocalContext.current)` inside a composable twice
  (`MurmurApp.kt:316, 333`) — new instance per recomposition; and see B-12.
* `MediaStoreMusicLibraryRepository.refreshChangedItem` writes with `syncToken = currentSyncToken ?: "item-update"`
  (`data/music/MediaStoreMusicLibraryRepository.kt:158`): after a process restart, an item update is stored
  under a token that the next full refresh will delete, so an offline edit can be lost until the next
  `refresh()`. Prefer a monotonic "seen in scan N" column.
* `MusicLibraryScreen`'s SAF picker plays selected files but never adds them to the catalog, so
  "choose files instead" produces a queue you cannot find again in the library (`MurmurApp.kt:443-451`).
* `readPage`'s PDF branch coerces `startChar` into `Int` page index (`:145`) — a PDF page index is fine, but
  the same field carries character offsets for text documents, so the two units share one column
  (`positionChars`); mixed across formats this is confusing but currently consistent per-document.
* `AudiobookLibraryRepository.addBooks` accepts any URI without MIME/size validation
  (`data/audiobooks/RoomAudiobookLibraryRepository.kt:55-61`), while the UI can report "Some selected files
  weren't recognized as supported audio" (`MurmurApp.kt:1738`) — that message is unreachable because
  `added == retainedUris.size` always.
* Audiobook cover art is stored as a JPEG BLOB in `audiobook_library.coverArt` (compressed to ~320 px, so
  small, but it is still binary in SQLite, against the vision's "no large blobs in the DB").

### B-12 — The OEM diagnostics feature is dead and, if reached, misleading — **P2**

* `Destination.OemFix` is registered in the NavHost (`MurmurApp.kt:312`) but **nothing navigates to it**
  (no `navigate(OemFix.route)` anywhere), and `OemFixDialog` has no callers.
* `BackgroundPlaybackMonitor` is never constructed; `OemCompatibility.updateDiagnostics()` and
  `recordInterruption()` are called only from that dead monitor.
* So `OemFixScreen` would render a permanently `UNKNOWN` health with `isForegroundServiceActive=false`,
  `notificationState=false`, etc. — the initialisation block in `MurmurApp.kt:316-330` hard-codes those
  `false` values as if they were measurements, and `OemCompatibility.evaluateHealth` scores them as facts.
  That is exactly the "don't imply knowledge you don't have" failure mode, and it also means the vision's
  OEM troubleshooting deliverable (Phase 9 / `docs/OEM_COMPATIBILITY.md`) is not actually available in the
  app.

---

## 4. Security and privacy assessment

**Good, verified in source:**

* **No `INTERNET` permission** in `app/src/main/AndroidManifest.xml`; also no `ACCESS_NETWORK_STATE`,
  no `MANAGE_EXTERNAL_STORAGE`, no `QUERY_ALL_PACKAGES`. Narrow `<queries>` for `TTS_SERVICE` only.
* No ad/analytics/telemetry/crash SDK; grep for `http`, `OkHttp`, `Firebase`, `URL(`, `HttpURLConnection`,
  `Socket` across `app/src/main` returns nothing.
* Only one logging call in the whole app (`BatchTtsManager.kt:359`, dead code) — no document text, paths,
  filenames or media content are logged.
* `allowBackup="false"` **and** `data_extraction_rules`/`full_backup_content` exclude every domain, so
  neither cloud backup nor device transfer can export Murmur's DB, prefs or cache.
* SAF-first access, `takePersistableUriPermission` with graceful failure messaging
  (`MurmurApp.kt:910-916`), no `MANAGE_EXTERNAL_STORAGE`, no filesystem path guessing for documents.
* XXE hardening is present and tested; EPUB has entry-count, entry-name-length, per-entry and total
  uncompressed caps (zip-bomb defence) and `resolveZipPath` rejects `../` traversal
  (`EpubParser.kt:63-95, 116-125`); EPUB entries are never written to disk, so archive traversal cannot
  escape.
* HTML is stripped with `Html.fromHtml` (`RoomDocumentLibraryRepository.htmlToPlainText`) — no WebView, no
  remote resource loading. Scripts/styles/head are removed first.
* Backup format has a checksum, strict schema validation, size caps, and restores inside one transaction.
* Exported components are justified: `MainActivity` (launcher/VIEW/SEND), `PlaybackService` (exported but
  guarded by a `signature` permission **and** a UID/package allow-list in
  `LocalControllerCallback.onConnect`, `PlaybackService.kt:625-648`), `NowPlayingWidget` (must be
  exported). `WidgetActionReceiver` and `WidgetConfigureActivity` are `exported="false"`; all
  `PendingIntents` use `FLAG_IMMUTABLE`.
* No workarounds for OEM process killers, no wake locks, no restart loops, no accessibility-service
  sniffing.

**Risk register (all noted above, summarised by category):**

| Category | Item |
|---|---|
| Availability | Exported `MainActivity` + graph-less `navigate()` = any local app can crash Murmur (A-2.5) |
| Destructive | `createBackup` deletes a user-chosen existing file on write failure (A-6.1) |
| Data integrity | Dual queue writers / DB-derived insertion indices (B-3); bookmark anchors invalidated by any edit (B-5) |
| Privacy-adjacent | Document text is sent to the user's installed TTS engine (unavoidable, must be stated in-app, not only in docs); `batch_work_units.text` stores document text in SQLite for a dead feature (A-1) |
| Untrusted input | EPUB DOCTYPE ban is over-broad (DoS-by-unsupported-file, not a security hole); PDF/EPUB caps present but `indexPdf` fails silently (A-7) |
| Wrong-but-confident UI | "Known Offline" from a single flag (B-6); OEM health screen with fabricated inputs (B-12); Tap-to-Read accuracy claim (B-2) |

---

## 5. What is genuinely solid (for fairness)

* **Playback ownership is architecturally right**: one `ExoPlayer` in `MediaSessionService`, one
  `MediaSession`, one `MediaController` path; audio focus enabled via
  `player.setAudioAttributes(..., handleAudioFocus = true)`; `MediaSessionService` handles becoming-noisy;
  foreground service type declared; `PlaybackRestorePolicy` deliberately strips transient
  `read_aloud_start` items and restores paused — a thoughtful detail.
* **TTS voice gating** is implemented at the right layer (`selectVoice` before `setVoice`, so a
  network-required voice is never selected under Strict Offline; `KEY_FEATURE_NOT_INSTALLED` is checked;
  failures produce human-readable messages, and `strictOfflineSpeech` really does default to `true` in
  `DataStoreSettingsRepository` and in the interface default).
* **Synthesis is real**: `synthesizeToFile` + `onBeginSynthesis`/`onAudioAvailable` capture, WAV header
  written by hand with format validation and `.part`+rename atomicity, float/8-bit/16-bit conversion,
  per-chunk `withTimeout`, cleanup of temp files, a process-wide synthesis mutex shared by all generator
  instances.
* **Room**: 16 versions with real `Migration` objects (no `fallbackToDestructiveMigration`), transactions
  around queue/playlist/structure writes, indexes on FK columns, no document body stored in the DB.
* **Document parsing** is defensively written: BOM detection, UTF-8/16, cancelling loops, byte/entry caps,
  `MAX_PDF_PIXELS`, `PdfRenderer` with `use {}`, and honest, user-facing error strings for unsupported
  cases.
* **Documentation** (`docs/`, `planning/`) is unusually honest and already discloses PDF API 35, the dead
  `TtsScheduler`, missing OCR, missing bit-perfect claims and the fact that no device pass was done. That
  is the right instinct — the audit's main criticism is that the *code* has not caught up with the docs.

---

## 6. Recommended fix order

1. **P0 — stop the crashing.** `MainActivity` nav controller + intent plumbing (A-2). Decide the widget's
   fate: remove it (A-3) or rebuild it on `MediaController`/Glance.
2. **P1 — delete or finish the dead subsystems.** `TtsScheduler`, `BatchTtsManager`, `SharedTtsCache(Lock)`,
   `TtsQueueManager`, all six processor files, both `OutputProfileManager`s, `SourceInfoExtractor`,
   `PlaybackController.startReadAloud`/`getSourceInfo`, `BackgroundPlaybackMonitor`/`OemFixScreen`/
   `OemFixDialog`, batch tables, `NeuralTtsManifest`. Add a lint/`detekt` unused-symbol check so it does not
   grow back. Fix or delete the tests that cover copies of production logic.
3. **P1 — make the reader honest.** Persist segment maps from the reader's own synthesis, use
   `sentenceIdx` in `mapTapOffsetToSegment`, fix `docs/READER.md`, add a "no sentence map → segment-level
   highlight" fallback (B-2). Unify the media-ID format with a version field and full encoding (A-4).
4. **P1 — cache correctness.** One cache owner; persist a small SQLite/JSON index of
   `(key → file, durationMs, identity)`; store real durations in the index instead of assuming 22 050 Hz;
   make `PlaybackService` pin through the same object the generator evicts from; add "clear speech cache"
   to the Storage screen (A-5, A-6.4).
5. **P2 — queue/persistence.** Single writer, dirty-flag persistence, remove the 5 s `while(true)` write loop
   and the 1 s UI polling loops (B-3, B-11).
6. **P2 — EPUB.** Allow `<!DOCTYPE>` while keeping entity/DTD resolution disabled; add an EPUB 3 fixture
   with a nav document; tighten `isToc` (A-8).
7. **P2 — honesty pass.** "Declared offline" vs "verified offline" (B-6); remove the neural-model sentence
   and the neural storage row; make `OemFixScreen` reachable-and-real or remove the route (B-12);
   `try/catch` + `resolveActivity` around the TTS settings intent.
8. **P3 — vision compliance sweep.** `strings.xml` migration (B-9), theme/night + contrast (B-8), exported
   Room schemas for 5 and 16, release `buildTypes`, remove the submodule/agent directories from the app
   repo (B-10), incremental document indexing (B-7), and a documented decision on media3-inspector + the
   pinned preview-track versions.

## 7. Verification debt (things nobody has proven yet)

These are explicitly *not* proven by anything in the repo, and I could not prove them either:

* Clean `./gradlew clean assembleDebug test lint` on the pinned toolchain, and that AGP 9.2.0/Gradle
  9.4.1/SDK 37/Kotlin 2.2.10 actually resolve and are mutually compatible.
* **Merged** manifest has no `INTERNET` (source manifest does not; dependency merge unverified).
* Deep-link/shortcut crash (A-2) on a real Navigation version — 15-minute check on any emulator.
* Widget action crash (A-3) and non-refresh behaviour on a launcher.
* Real offline synthesis in Airplane Mode with an uncached page (procedure exists in
  `OFFLINE_TTS_TEST_PROCEDURE.md`, no recorded result).
* EPUB 3 TOC on a real book (A-8) and Android's handling of `disallow-doctype-decl`.
* Foreground-service/background-start behaviour on target SDK 37 (the vision's Android 17 conditions).
* `MediaController` thread-affinity assumptions in `PlaybackController.publish/refreshPosition` from
  Compose's main dispatcher.
* Anything about notifications, lock-screen controls, audio focus, headphone unplug, screen-off playback,
  237-hour media, process death during Read Aloud, or OEM variants.

Suggested minimal commands once a JDK 17 + SDK 37 host exists:

```bash
./gradlew --version
./gradlew clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
./gradlew :app:processDebugMainManifest   # then inspect app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
grep -R "android.permission.INTERNET" app/build/intermediates/merged_manifests/ || echo "no INTERNET"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.intent.action.VIEW -d "murmur://continue" com.joy.murmur   # A-2 repro
```

## 8. Note on uncommitted planning claims

`planning/PROJECT_STATUS.md` describes six files as "uncommitted and unverified" and claims only
`f9b4aad` was compiled. The current tree **is clean at `cd4d0ff`** and contains those edits, so the document
is now out of date on that point — but its broader statement ("not evidence that every feature works on
your device") still holds, and this audit reached the same conclusion by a different route.

---

## 9. Fix log

| Finding | Status | Change |
|---|---|---|
| A-2 deep links / shortcuts / shares crash or are dropped | **Fixed** | `MainActivity` no longer constructs a `NavHostController`; it parses the intent into `MurmurIntentRequest` and exposes it as Compose state. `MurmurApp` handles it once with the real controller (`onRequestHandled`), maps `murmur://host` to a route, and imports `VIEW`/`SEND`/`SEND_MULTIPLE` payloads (`EXTRA_STREAM` as single Parcelable, plus `ClipData`) into the playback queue or document library. Adds `launchMode="singleTop"`, pure parsing rules in `MurmurIntentRules` and `MurmurIntentRulesTest`. |
| A-3 widget cannot work | **Fixed by removal** | Deleted `widget/NowPlayingWidget`, `WidgetActionReceiver`, `WidgetConfigureActivity`, their layouts/drawables/strings/colors and the `appwidget-provider` XML. Removed the two receivers and the configure activity from the manifest, the `widget_action` branch in `PlaybackService.onStartCommand`, and the now-unused `glance`, `appcompat` and `cardview` dependencies. `docs/ARCHITECTURE.md` records the decision; system media controls are the supported playback surface. |
| A-1 dead speech orchestration | **Fixed by removal** | Deleted `TtsScheduler` (incl. `SpeechSegment`/`GenerationTask`/`GenerationState`/`SpeechGenerator`), `BatchTtsManager`, `SharedTtsCache(Lock)`, `TtsQueueManager` and `TtsCache`, plus the three unit-test files that covered them or hand-copied their logic. Kept `SynthesisIdentity` in its own file. Removed the no-op `TtsCache.pin`/`releasePin` calls from `PlaybackService`. `docs/ARCHITECTURE.md`, `docs/TTS.md` and `planning/PRODUCT_GAP_AUDIT.md` now state that no scheduler/batch layer exists. Cache behaviour is unchanged and still filename-based until A-5. |
| A-4 duplicate media-ID codecs | **Fixed** | `ReadAloudMediaId.kt` is now the single codec: one versioned `read_aloud|2|…` segment format and one `read_aloud_start|2|…` start format, every free-form field percent-encoded with a strict local codec (no `Uri.encode`, so it stays JVM-testable) and every field validated on parse. The four ad-hoc `split('|')` parsers in `PlaybackService`, `MurmurApp` (×2) and the string-built start ID in `PlaybackController` all route through it; unversioned legacy IDs still parse so persisted queues survive. `ReadAloudUtils` keeps only the text/spoken-index helpers. New `ReadAloudMediaIdTest` locks round trips, `|`/`%`/Unicode hostile inputs, legacy IDs and malformed rejects. |
| A-5 speech cache correctness | **Fixed** | `WavDuration` reads the real sample rate, channel count and bit depth from the RIFF header, replacing `estimateDurationFromFile`'s hard-coded 22 050 Hz mono 16-bit byte rate; a cache hit now reports the same `durationMs` as the synthesis that wrote the file, which matters because that value is persisted as `segmentDurationMs` and drives highlighting/tap-to-read. A header that does not parse (truncated interrupted write, garbage) is treated as a cache miss and rebuilt. `SpeechCacheEviction` is extracted from the generator as a pure object (age, 256 MB cap, oldest-first, queue protection, stale `.part`/`speech-`/`engine-` scratch files) with 9 tests; `WavDurationTest` covers 48 kHz mono, 44.1 kHz stereo, 22.05 kHz mono, 8-bit, chunk-walking, truncation and non-WAV input. Also added `onStop` handling (AOSP's default `onStop` has an empty body, so cancellation was invisible) so an interrupted utterance fails fast instead of holding the process-wide synthesis mutex for the 120 s timeout. Note: the `InputStream.readNBytes` shortcut would have needed API 33 (minSdk 26), so the header window is read by hand. |
