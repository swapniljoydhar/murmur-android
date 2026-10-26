# Murmur

Murmur is an Android-only, local-first media and reading application centered around music, audiobooks, documents, and Read Aloud.

## Core Philosophy

> **Simple enough to understand immediately. Powerful enough to keep using for years.**

Murmur may contain sophisticated engineering underneath the UI, but users should not have to understand that complexity.

## Features

### Music
- Local music library with MusicStore integration
- Playlists, queue management, search
- Background playback via MediaSessionService

### Audiobooks
- M4B, M4A, MP3 support
- Per-book position and chapter tracking
- Playback speed control, bookmarks
- 200+ hour audiobook support

### Documents
- TXT, Markdown, HTML, EPUB, PDF support
- Structural parsing with lazy rendering
- Read Aloud with sentence-level TTS

### Read Aloud
- System TTS and optional neural TTS (Sherpa-ONNX)
- Bounded speech generation with look-ahead buffer
- Tap-to-read, return-to-text
- Strict offline speech mode

## Architecture

- **Single playback owner**: PlaybackController → PlaybackService → ExoPlayer → MediaSession
- **Local-first**: No INTERNET permission, all data stored locally
- **Room + DataStore**: Durable persistence for positions, bookmarks, settings
- **MediaSessionService**: Background playback with lock screen controls

## Requirements

- Android API 26+
- No internet permission required for normal operation
- Local media files only

## License

See docs/LICENSING.md for dependency licensing information.