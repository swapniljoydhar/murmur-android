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

- **SystemTtsSpeechGenerator**: System TTS with voice selection, strict offline mode
- **TtsCache**: Local WAV cache with SHA-256 keys

The separate `TtsScheduler` and `BatchTtsManager` are not wired into the app. Neural TTS is not implemented.

## UI Layer

- **Jetpack Compose**: Modern declarative UI
- **Material 3**: Design system
- **Glance**: Home screen widgets

## Key Design Decisions

1. **Local-first**: No INTERNET permission; app-managed data stays in app storage, while source media remains at user-selected locations
2. **Single playback owner**: No independent players per screen
3. **Process death resilience**: Position persisted to Room on pause, item transition, lifecycle events
4. **Bounded TTS generation**: No unbounded pre-generation for long documents
5. **OEM compatibility**: Android background limits still apply; broad OEM-specific resilience is not device-validated
