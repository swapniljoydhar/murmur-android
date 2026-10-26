# Murmur Licensing

## Dependencies

All dependencies used in Murmur must be audited for:
- Runtime
- Parser libraries
- TTS runtime
- Native libraries
- Neural models
- Tokenizer
- Phonemizer
- Lexicon
- Voice assets
- Fonts
- Bundled resources

## Policy

Do not ship anything with unclear redistribution rights.
Do not casually introduce GPL/AGPL or incompatible licensed code.

## Dependency Review

Before introducing a library:
1. Why is it needed?
2. Is it maintained?
3. Is the license compatible?
4. What is the APK/storage impact?
5. Does it require network access?
6. Can AndroidX/platform APIs replace it?

## Neural TTS Models

Any neural TTS models (e.g., Sherpa-ONNX) must have:
- Pinned exact runtime version
- Verified native libraries
- Audited licenses
- Audited model licenses
- Audited tokenizer/phonemizer/lexicon
- Audited voice assets
- Audited transitive dependencies

Do not distribute uncertain assets.