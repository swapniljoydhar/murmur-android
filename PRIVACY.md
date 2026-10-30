# Murmur Privacy & Legal Notice

**Effective date:** October 30, 2026

Murmur (`com.joy.murmur`) is a 100% offline, local-first Android application for playing personal music and audiobooks, reading local documents, and listening via Android system Text-to-Speech ("Read Aloud").

## 1. Zero Network & Zero Data Collection

- **No Internet Permission:** Murmur does not request or declare `android.permission.INTERNET` or `ACCESS_NETWORK_STATE`.
- **Zero Analytics or Telemetry:** Murmur includes no analytics SDKs, crash reporting services, advertising identifiers, session replay libraries, pixels, or remote configuration endpoints.
- **No Accounts or Servers:** Murmur does not create accounts, authenticate users, or transmit library metadata, reading positions, or audio history to any server.
- **System Text-to-Speech (TTS):** Read Aloud delegates synthesis to the Android OS `TextToSpeech` engine installed on your device. By default, **Strict Offline Speech** is enabled, preventing selection of voices that report requiring a network connection. If you disable Strict Offline Speech, your chosen third-party or OS TTS engine may use its own system permissions to synthesize speech according to that engine provider's privacy policy.

## 2. On-Device Data Storage & Encryption Disclosure

All application state stays on your device inside Android's application sandbox (`Context.filesDir`, `Context.cacheDir`, Room SQLite, and Jetpack DataStore), protected at rest by Android OS File-Based Encryption (FBE) on modern devices:

- **Library & Reading State:** Playlists, audiobook resume positions, document character/page anchors, and bookmarks are stored locally in Murmur's app-private Room database (`murmur-playback.db`).
- **Unencrypted App-Private Speech Cache (`cacheDir/read-aloud`):** Synthesized Read Aloud audio segments are cached as **unencrypted PCM WAV files** inside Murmur's private cache directory (`context.cacheDir/read-aloud`) together with a Room cache index (`tts_cache_entries`) so repeated pages play without re-synthesis. You can clear all cached speech files at any time via **Settings → Storage → Clear generated speech cache**, or by uninstalling the app.
- **OS Cloud Backup Excluded:** `android:allowBackup="false"` and custom backup/data-extraction rules exclude Murmur's databases and caches from automatic OS cloud backups and device-to-device transfers.
- **User-Controlled Logical Backups:** If you export a backup in **Settings → Storage**, Murmur writes an **unencrypted JSON backup file** exclusively to the destination URI you select in the Android Storage Access Framework (SAF) picker. Protecting exported backup files is under your control.

## 3. Permissions & File Access

- **Storage Access Framework (`DocumentFile` / SAF):** You can import music folders, audiobook folders, individual audio files, and documents (`TXT`, `Markdown`, `HTML`, `EPUB`, `PDF`) using Android's system picker without granting broad storage permissions. Murmur retains read-only URI grants only for items you explicitly select.
- **Optional Audio Library Scan:** If you choose to scan the device's MediaStore music catalog, Murmur requests `READ_MEDIA_AUDIO` (Android 13+) or `READ_EXTERNAL_STORAGE` (Android 12 and below) solely for local indexing. An optional daily local refresh worker (`MusicCatalogRefreshWorker`) runs strictly on-device without network access when enabled in Music settings.
- **Notifications & Foreground Playback:** `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, and `POST_NOTIFICATIONS` (optional on Android 13+) are used solely to display standard `MediaStyle` playback controls while audio plays in the background.

## 4. Regulatory & Legal Posture

- **GDPR / UK GDPR / CCPA / CPRA:** Murmur acts solely as local user-controlled software on your device; the developer collects, processes, sells, or shares **zero personal data**. All data access, portability (JSON backup), and erasure (Storage Center reset or app uninstall) are executed locally by you on your device.
- **Children's Privacy (COPPA):** Murmur collects zero personal information, persistent identifiers, geolocation, audio recordings, or behavioral telemetry from any user of any age.
- **California Invasion of Privacy Act (CIPA):** Murmur embeds zero third-party SDKs, trackers, chat widgets, or session-recording interceptors.
- **CAN-SPAM & Commercial Messaging:** Murmur collects no email addresses or phone numbers and sends zero marketing communications.
- **Automatic Renewal Laws (California ARL):** Murmur contains no paid subscriptions, free-to-paid trials, in-app purchases, or recurring billing.
- **Copyright & User-Supplied Media (DMCA):** Murmur ships with zero copyrighted music, audiobooks, or books and provides no content directory, search engine, or download service. Users are solely responsible for ensuring they have lawful rights to the local files they open on their device.
