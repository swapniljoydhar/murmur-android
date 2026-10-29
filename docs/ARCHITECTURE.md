# Murmur Architecture

## Overview

Murmur follows a single-playback-owner architecture where all audio (music, audiobooks, Read Aloud TTS) flows through one ExoPlayer instance managed by a MediaSessionService.

## Playback Layer

```
PlaybackController
       ↓
Primary ExoPlayer
       ↓
MediaSession
       ↓
MediaSessionService
```

- **PlaybackController**: High-level API for queue management, playback control, snapshot publishing
- **PlaybackService**: MediaSessionService hosting the ExoPlayer and MediaSession
- **ExoPlayer**: Primary audio player for all content types
- **MediaSession**: Exposes playback to system UI, Bluetooth, lock screen

## Data Layer

- **Room Database**: Durable storage for positions, bookmarks, playlists, queue state
- **DataStore**: Preferences for appearance, speech settings, text scale
- **MediaStore**: Device-wide music discovery
- **SAF**: Storage Access Framework for user-selected folders/files

## Speech Layer

- **SystemTtsSpeechGenerator**: System TTS with voice selection, strict offline mode. Writes WAV chunks to
  `cache/read-aloud`, keyed by SHA-256 over synthesis identity, voice identity and chunk text, and evicts
  by age and total size while sparing files still referenced by the playback queue.
- **SynthesisIdentity**: the engine/voice/locale/format tuple that makes two synthesis results distinct.

There is no look-ahead scheduler and no batch generator. An earlier `TtsScheduler`/`BatchTtsManager`/
`TtsQueueManager`/`TtsCache`/`SharedTtsCache` set was removed because nothing constructed it and it could
not have worked (`extractSectionText()` returned an empty string, `seekTo()` closed its worker channel
permanently, and it double-unlocked a mutex inside `withLock`). Read Aloud synthesizes one document page at a
time in the reader and hands the generated files to the shared player, which synthesizes the next page when
the queue reaches the end. Neural TTS is not implemented.

## UI Layer

- **Jetpack Compose**: Modern declarative UI
- **Material 3**: Design system

There is no home-screen widget. An earlier `AppWidgetProvider` stub was removed because it refreshed its
`RemoteViews` with `notifyAppWidgetViewDataChanged` (which only affects collection widgets, so it never
updated) and its buttons called `startService` from a broadcast receiver, which is not allowed once the app
is in the background. System media controls (notification, lock screen, Bluetooth/headset) are the
supported playback surface.

## Incoming intents

`MainActivity` never navigates itself. It parses the launch/re-launch intent into a
`MurmurIntentRequest` (`IntentRequest.kt`) and exposes it as Compose state; `MurmurApp` consumes it once
through `onRequestHandled`, using the `NavHostController` that is actually attached to the `NavHost`.

- `murmur://` links map from host + path segments to a destination inside `MurmurApp`, so the nav graph
  stays the single source of truth for routes.
- `ACTION_VIEW`, `ACTION_SEND` and `ACTION_SEND_MULTIPLE` payloads (including `ClipData`) are classified
  as audio or document off the main thread, then either queued for playback or added to the document
  library before the UI navigates. Nothing is imported without a persistable read grant.
- Parsing a plain launcher intent yields nothing; a `savedInstanceState != null` relaunch does not replay
  the original link.

## Key Design Decisions

1. **Local-first**: No INTERNET permission; app-managed data stays in app storage, while source media remains at user-selected locations
2. **Single playback owner**: No independent players per screen
3. **Process death resilience**: Position persisted to Room on pause, item transition, lifecycle events
4. **Bounded TTS generation**: No unbounded pre-generation for long documents
5. **OEM compatibility**: Android background limits still apply; broad OEM-specific resilience is not device-validated
