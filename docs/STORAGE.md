# Murmur Storage

## Current storage behavior

- Room stores library metadata, playlists, audiobook chapters and progress, reader positions/bookmarks, and other feature-owned durable records.
- DataStore stores app preferences separately from Room.
- Source music, audiobook, and document bytes remain at their user-selected locations. Murmur retains document/media metadata and provider URIs; access can be lost if a provider revokes a grant or the source is moved.
- Generated Read Aloud WAV segments use the app cache and are disposable. Cache eviction is size/age based and avoids files referenced by the active playback queue.
- There are no bundled neural voice models or full-book speech exports.

## Storage Center

Storage Center reports the Room database and its SQLite WAL/SHM files and exposes the logical backup flow. It does not currently offer cache, model, temporary-file, or export cleanup controls. Cleanup never deletes user source files.

## Logical backup and restore

The app's JSON backup is the supported user-controlled backup path. It contains durable Room feature tables, excluding transient playback queue/state. It does not contain DataStore preferences, source-file bytes, generated speech cache, or SAF URI grants. Source URI strings may remain in metadata; after restore, a user may need to select a source again and re-grant access.

The format includes a checksum and schema version, validates supported table/column and cell encodings, and is limited to 64 MiB and 500,000 rows. Restore previews record counts, requires explicit replacement confirmation, refuses while a playback queue is populated, and replaces records transactionally. Only the exact supported Room schema version is accepted; an unsupported or malformed backup is rejected without applying it.

Android Auto Backup and device-transfer rules explicitly exclude app data. This is separate from Murmur's in-app logical backup. Android documents that `allowBackup="false"` alone may not prevent device-to-device transfer on some Android 12+ OEM implementations, so both cloud and device-transfer rules explicitly exclude the data domains. See [Android Auto Backup](https://developer.android.com/identity/data/autobackup).

## Not implemented

- Per-category cache clearing and storage cleanup controls
- Neural voice model storage/management
- Full-book speech export
- Backup of preferences, source files, playback queue/state, or provider grants
