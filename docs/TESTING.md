# Murmur Testing

## Long-Form Testing

### Audiobooks
- 1 hour, 24 hours, 100 hours, 200+ hours
- Very large chapter counts

### Documents
- 1 MB, 10 MB, 50+ MB
- Huge EPUB, HTML, PDF
- Millions of logical sentences

### TTS
- Short, several hours equivalent
- 50-hour equivalent, 200-hour equivalent
- Large synthetic workloads
- Interrupted jobs, resumed jobs
- Voice changes, cache eviction, far seeking

## Process Death Testing

Kill process during:
- Audiobook playback
- TTS playback
- TTS generation
- Batch export
- Document parsing
- Library indexing
- Cache writes

After restart:
- Durable state remains valid
- Completed work remains completed
- Incomplete work marked correctly
- Playback can reconnect
- Reader location recoverable

## Storage Full Testing

Fill storage during TTS generation:
- Stop safely
- Preserve completed work
- Preserve source documents
- Preserve playback/reading state
- Explain the problem
- Allow cleanup/retry

## Rapid Interaction Testing

- Play then immediate pause
- Rapid next/previous
- Queue modification while playing
- TTS start then sentence jump
- Repeated sentence jumps
- Voice change
- Rotation during TTS
- Process death during TTS
- Output disconnect/reconnect
- Deletion of current source
- Cache cleanup during playback
- Storage exhaustion during generation

## OEM Testing

Test background playback across representative OEM devices:
- Screen locked
- Battery saver
- Background restrictions
- Bluetooth
- Wired output
- App swipe-away
- Process recreation

Document failures.