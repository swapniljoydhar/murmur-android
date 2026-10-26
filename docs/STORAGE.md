# Murmur Storage

## Storage Categories

Murmur tracks and manages:

- **TTS Cache**: Generated speech audio (disposable)
- **Neural Voices**: Downloaded neural TTS models (disposable)
- **Generated Exports**: User-requested speech exports (user-owned)
- **Database**: Room database (app state, positions, bookmarks)
- **Temporary Files**: Temp downloads and processing (disposable)

## Storage Center

Users can view storage breakdown and clear disposable caches. User source media and documents are never touched by cleanup operations.

## Backup & Restore

### Backed Up

- Playlists
- Favorites
- Queues
- Playback positions
- Reading positions
- Bookmarks
- Highlights
- Notes
- Pronunciation rules
- Settings
- Output profiles

### NOT Backed Up (by default)

- Source media
- Source documents
- TTS cache
- Large neural models

### Restore Safety

- Schema versioning
- Validation before apply
- Preview before commit
- Transactional/restorable
- Does not corrupt existing state on failure

## Storage Center Actions

- Clear TTS Cache
- Clear Temp Files
- Clear Neural Voices
- Clear Exports
- Export Database
- Import Database