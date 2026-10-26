# Murmur Reader

## Supported Formats

- TXT (UTF-8, UTF-16, BOM handling)
- Markdown (headings, paragraphs, lists, blockquotes, code blocks, emphasis, links, separators)
- HTML (local parsing, scripts/remote resources stripped)
- EPUB (spine, chapters, sections, table of contents)
- PDF (text-based, best-effort extraction, single-column focus)

## Reader Data Model

Documents use structural content:
```
Document
 └── Section
      ├── Paragraph
      │    ├── Sentence
      │    └── ...
      └── ...
```

Room stores durable application state. Document-local indexes store compact structural information (not millions of Room rows for sentences).

## Reader Position

Position restoration uses structural anchors:
- documentFingerprint
- sectionId
- paragraphId
- sentenceId
- textHash
- sourcePage

Restoration order:
1. Exact structural match
2. Text hash match
3. Nearby structural match
4. Safe fallback

Not raw character offsets only.

## Reader Features

- Font size, line spacing, margins, alignment
- Themes: Paper, Warm, Dark, OLED Black
- Bookmarks
- Auto-scroll
- Reading ruler
- Screen-on while actively reading
- Search within document (on-demand, incremental for large documents)

## Large Documents

- Incremental parsing
- Lazy rendering
- Structural indexing
- Bounded working sets
- No full-document memory loading