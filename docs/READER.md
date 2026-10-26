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

Reader storage currently uses document sections and character ranges for text formats, with PDF positions represented as page indexes. Paragraph/sentence anchors are not currently implemented. Room stores durable application state.

## Reader Position

Text document positions are character offsets; PDF positions are page indexes. The reader does not currently provide structural paragraph/sentence restoration or reflow-stable anchors.

## Reader Features

- Text size for supported text formats
- Bookmarks and saved position; the open reader follows persisted Read Aloud segment positions
- Search in supported text formats; PDF search requires API 35+ and extractable page text
- Read Aloud from the current text page with generated speech offsets mapped back to the source document; Markdown syntax cleanup preserves source indexes

Read Aloud position following is segment-level. Sentence highlighting and continuous in-page synchronization are not implemented.

If service-side speech generation fails, the player reports the error and offers a route back to the document. The reader can retry from the last saved position. The failure status is process-local; the durable document position remains in Room.

System TTS voice changes, synthesis, and cache eviction are serialized across the app's generator instances to protect their shared cache directory.

Service restoration ignores unfinished Read Aloud start markers and keeps restored queues paused. If a cached segment cannot play, the service preserves its source offset and reports a retry path back to the reader. The user must resume or retry explicitly.

## Large Documents

Input and indexing limits exist, but lazy reading and bounded working-set behavior are not yet established for all formats. Large-document performance still needs measurement.
