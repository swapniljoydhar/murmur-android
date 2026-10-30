# Murmur Reader

## Supported Formats

- TXT (UTF-8, UTF-16, BOM handling)
- Markdown (headings, paragraphs, lists, blockquotes, code blocks, emphasis, links, separators)
- HTML (local parsing, scripts/remote resources stripped)
- EPUB (spine, chapters, sections, table of contents)
- PDF page rendering on supported Android versions
- Selectable PDF text extraction, search, and read-aloud on Android 15 (API 35) and newer, when the PDF exposes text

PDF text extraction uses `PdfRenderer.Page.getTextContents()` on API 35+. Below API 35, Murmur can render PDF pages but does not extract/search/read them aloud. Scanned or image-only pages have no OCR support and cannot be searched or read aloud.

## Reader Data Model

Reader position anchor version 1 uses a PDF page index for PDF and a UTF-16 character offset in extracted plain text for TXT, Markdown, HTML, and EPUB. Room stores the position, anchor version, and a source fingerprint. Paragraph/sentence anchors and reflow-stable restoration are not implemented.

## Reader Position

On providers that expose size and last-modified metadata, the source fingerprint combines those values with the document URI. If it changes, Murmur restarts at the beginning, preserves bookmarks, invalidates the cached document index, and asks the reader to review bookmarks. Legacy positions are kept when Murmur first records a fingerprint, and their cached index is rebuilt. If provider metadata is unavailable, the existing position is kept. Metadata fingerprints can miss rewrites with unchanged size and timestamps.

## Reader Features

- Text size for supported text formats
- Tap plain TXT text to set the character position used by Read from here; other formats use their available page/chapter-level navigation
- Bookmarks and saved position; the open reader follows persisted Read Aloud segment positions
- EPUB table of contents with navigation to readable spine-chapter starts; in-chapter fragment links are not mapped
- Search in supported text formats; PDF search requires API 35+ and extractable page text
- Read Aloud from the current text page with generated speech offsets mapped back to the source document; Markdown syntax cleanup preserves source indexes, and the speech engine is given exactly the text the index map was built from
- **Sentence highlighting during Read Aloud** (plain TXT only): the sentence index is `positionMs * sentenceCount / segmentDurationMs`, and the highlighted range comes from the persisted sentence offsets. Durations are assumed to be split evenly across sentences because system TTS reports no per-sentence timing, so long or punctuation-heavy sentences can drift by a sentence. There is no highlight when no sentence map exists, and no sentence highlight for Markdown, HTML, EPUB or PDF pages.
- **Tap-to-Read** (plain TXT only): tapping a spoken sentence selects that sentence, and playback seeks to its share of the current segment's duration. Tapping a character that markup cleanup stripped is ignored and falls through to setting the reading position. Tapping a sentence that belongs to a *later* segment jumps to that segment's start rather than to the sentence inside it, because only the current segment's map is loaded.
- Sentence maps are written by both producers — the reader when Read Aloud starts on the visible page, and the playback service when it synthesizes the continuation page — so highlighting works from the first page, not only after the reader moves on.
- **Return to Text**: Stop Read Aloud → reader scrolls to last spoken sentence position (via `savePosition` + observed position change).

Read Aloud position following is segment-level. Continuous in-page synchronization to audio time is not implemented (system TTS provides no word/sentence timing).

Reader text-size and Strict Offline Speech preferences are persisted. Reader themes, reading rulers, auto-scroll, highlights, and notes are not implemented.

If service-side speech generation fails, the player reports the error and offers a route back to the document. The reader can retry from the last saved position. The failure status is process-local; the durable document position remains in Room.

System TTS voice changes, synthesis, and cache eviction are serialized across the app's generator instances to protect their shared cache directory.

Service restoration ignores unfinished Read Aloud start markers and keeps restored queues paused. If a cached segment cannot play, the service preserves its source offset and reports a retry path back to the reader. The user must resume or retry explicitly.

## Large Documents

Current limits are explicit: plain text indexing/search are capped at 50 Mi characters; HTML reading/search at 4 Mi characters; EPUB archive extraction at 64 MiB uncompressed, 16 MiB captured, and 4 MiB per package/chapter entry, with extracted reading text capped at 4 Mi characters; PDF indexing/search at 5,000 pages, rendered pages at 4 million pixels, and extracted text at 1 Mi character per page. Text reader pages are at most 24,000 characters. Parsing and streaming loops check coroutine cancellation between reads/pages/entries. These are safety limits, not measured performance promises; the EPUB parser holds its bounded captured entries in memory, and large-file runtime measurements remain outstanding.
