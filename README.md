# Murmur

Murmur is an Android-only, 100% offline, local-first sanctuary for personal music, audiobooks, documents, and system Read Aloud.

## Features

- **Local Music**: MediaStore catalog scanning plus zero-permission `DocumentFile` folder & multi-file SAF import, playlists, search, and background playback with `MediaStyle` notifications and persistent mini-player controls
- **Audiobooks**: Local file and folder import via `DocumentFile` SAF pickers, automatic resume position, bookmarks, playback speed control, sleep timer, and embedded chapter navigation
- **Read & Listen (`TXT`, `Markdown`, `HTML`, `EPUB`, `PDF`)**: Distraction-free typography (`Lora` & `Plus Jakarta Sans`), bookmarks, search, EPUB table of contents, tap-to-read sentence seeking, and Android system `TextToSpeech` Read Aloud with strict offline voice verification (see [docs/READER.md](docs/READER.md))
- **Durable Local State**: Room v17 database, DataStore preferences, Room-persisted Read Aloud WAV cache index with exact RIFF/WAVE header duration parsing, and validated user-exported JSON backup/restore

> **Android 15 (API 35+) Requirement for PDF Text & Read Aloud:**
> Murmur renders PDF pages visually on all supported Android versions (Android 8.0 / API 26+), **but PDF text extraction, in-document PDF search, and PDF Read Aloud require Android 15 (API 35) or newer** (`PdfRenderer.Page.getTextContents()`), and only work on PDFs with selectable text (no OCR on scanned image PDFs).

## Architecture & Privacy

- **Module & SDKs**: Single Android app module (`com.joy.murmur`); `minSdk = 26`, `targetSdk = 36`, `compileSdk = 37`
- **Zero Network**: Murmur does **not** declare `android.permission.INTERNET` and embeds zero analytics, crash reporters, ads, or cloud SDKs. Android system TTS engines are separate OS apps; Murmur's **Strict Offline Speech** toggle (enabled by default) blocks voices that report requiring network access.
- **Local TTS Cache Disclosure**: Read Aloud synthesizes text into **unencrypted PCM WAV files** stored in Murmur's app-private cache directory (`cacheDir/read-aloud`), indexed in Room (`tts_cache_entries`), and protected by Android's app sandbox and OS file-based encryption. You can clear this cache anytime in **Settings → Storage**.
- **Privacy & Legal Notice**: Available in-app (**Home → Privacy & Legal Notice** or **Settings → Privacy**) and in [PRIVACY.md](PRIVACY.md).

## Build & Verification

Run unit and Robolectric JVM tests with `gradle :app:testDebugUnitTest` and assemble with `gradle :app:assembleDebug`. Dependency and license details are documented in [docs/LICENSING.md](docs/LICENSING.md).
