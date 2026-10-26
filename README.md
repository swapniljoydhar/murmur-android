# Murmur

Murmur is an Android-only, local-first app for music, audiobooks, documents, and system Read Aloud.

## Current app

- Local music browsing, playlists, and background playback through a shared Media3 session
- Audiobook library, resume position, bookmarks, and available embedded chapters
- TXT, Markdown, HTML, EPUB, and PDF reading, with format-specific limits documented in [docs/READER.md](docs/READER.md)
- Android system TextToSpeech playback for supported reader content; strict offline voice checks are available
- Room-backed feature data, DataStore preferences, and validated logical backup/restore

These are implemented code paths, not a claim of complete device acceptance. Read [planning/PROJECT_STATUS.md](planning/PROJECT_STATUS.md) for current evidence and limitations.

## Architecture and privacy

- Single Android app module; namespace and application ID `com.joy.murmur`
- Minimum API 26; target API 37 in the current project configuration
- UI, domain, and data packages; Room for feature data and DataStore for preferences
- No `INTERNET` permission. Android system TTS engines may have their own network behavior; Murmur's strict offline option filters voices using system-reported metadata.
- Android OS cloud backup and device-transfer are excluded; user-controlled backup uses Murmur's validated logical format.

## Build

Use the checked-in Gradle Wrapper and a compatible Android SDK. Build commands and verification status are recorded in [planning/VALIDATION.md](planning/VALIDATION.md). Dependency and license review scope is documented in [docs/LICENSING.md](docs/LICENSING.md).

## Product documentation

See [docs/](docs/) for reader, Read Aloud, storage, and other feature limits. The Markdown files under `planning/` are project/agent planning records, not app source, runtime configuration, or authorization to implement deferred work.
