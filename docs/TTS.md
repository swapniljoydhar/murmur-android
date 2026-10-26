# Murmur TTS (Text-to-Speech)

## Architecture

Murmur's TTS system is built around the `SpeechGenerator` interface, with two implementations:

### SystemTtsSpeechGenerator

Uses Android's built-in TextToSpeech engine:
- Voice selection with language and locale
- Strict offline mode: blocks voices known to require network access
- WAV cache with SHA-256 keys
- Pronunciation filtering support

### NeuralTtsSpeechGenerator (Optional)

Uses Sherpa-ONNX for neural TTS:
- Lazy model loading
- Model manifest with checksums and license verification
- Speaker selection
- Reflection-based API access (to avoid hard dependency)

## SpeechGenerator Interface

```kotlin
interface SpeechGenerator {
    val currentSynthesisIdentity: SynthesisIdentity
    fun maxSpeechInputLength(): Int
    suspend fun synthesize(text: String, identity: SynthesisIdentity): File
}
```

## TTS Cache

Generated speech is cached locally with SHA-256 keys derived from:
- Text content
- Voice and engine identity
- Pronunciation version
- Synthesis-affecting settings

Cache files are written atomically (temp file → rename) to prevent corruption.

## TTS Scheduler

Bounded generation with:
- Look-ahead buffer (bounded by time, not count)
- Sentence segmentation respecting engine limits
- Cancellation on seek
- Cache lookup before generation
- Process-death recovery via Room checkpointing

## Strict Offline Speech

When enabled:
- Blocks voices known to require network access
- Does not silently fall back to network-required speech
- Reports "Unknown" when Android cannot determine network requirement