# Murmur Completion Plan

## Current State Analysis

### ✅ Completed (P0 Core Features)
- **Long-form audiobook support** (200+ hours): `AudiobookChapterParser`, `AudiobookMetadataExtractor`, chapter parsing for MP4/M4B/MP3
- **Large document support**: `DocumentIndexer` with structural sections for TXT, MD, HTML, EPUB, PDF
- **Bounded TTS scheduler**: `TtsScheduler` with look-ahead buffer, priority queue, cancellation on seek
- **TTS cache**: `TtsCache` with atomic writes, pinning/leases, LRU eviction
- **OEM compatibility**: `OemCompatibility`, `BackgroundPlaybackMonitor`, `OemFixScreen` with vendor intents
- **Backup/restore**: `BackupManager` with ZIP-based backup, schema versioning, preview
- **Storage center**: `StorageCenter` with usage breakdown, cleanup, database export/import
- **Widgets/shortcuts/deep links**: `NowPlayingWidget`, shortcuts XML, `murmur://` deep links
- **System TTS**: `SystemTtsSpeechGenerator` with voice selection, strict offline mode
- **Media3 playback**: Single `PlaybackController` → `PlaybackService` → `MediaSession`

### ❌ NOT Implemented (Critical Gaps)

| Feature | Status | Priority |
|---------|--------|----------|
| Neural TTS (Sherpa-ONNX) | Missing | P1 |
| Audio processing (EQ, ReplayGain, crossfeed, headroom) | Missing | P1 |
| Output profiles per device | Missing | P1 |
| Batch TTS generation (full book) | Missing | P1 |
| Sound screen with honest audio info | Missing | P1 |
| UX/UI polish per Figma | Missing | P0 |
| Mini player persistent | Partial | P0 |
| Strict Offline Speech refinement | Partial | P0 |

### 🎨 UX/UI Issues Identified

1. **Theme incomplete**: `MurmurTheme` only provides color scheme, no custom typography/shapes
2. **Missing `MurmurTheme.typography`**: Code references `MurmurTheme.typography.headlineMedium` etc. but `MurmurTheme.kt` only exposes `colorScheme`
3. **Widget placeholders**: Uses "?" for prev/play/next icons
4. **Inconsistent spacing/sizing**: Hardcoded dp values throughout
5. **No elevation system**: Cards use defaults
6. **No motion/animation**: Static transitions
7. **Reader screen**: PDF shows "PDF Read Aloud isn't available yet" but no fallback

---

## Phase 1: UX/UI Foundation (P0 - Blocking)

### 1.1 Complete MurmurTheme
**Files**: `app/src/main/java/com/joy/murmur/ui/theme/MurmurTheme.kt`

Add:
- Custom typography scale (headline, title, body, label)
- Shape system (small, medium, large, extra-large)
- Elevation levels (0, 1, 2, 3, 4)
- Spacing scale (4, 8, 12, 16, 20, 24, 32, 40, 48)
- Motion tokens (duration, easing)

```kotlin
// Add to MurmurTheme.kt
val MurmurTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.Normal, lineHeight = 64.sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.Normal, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Normal, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

val MurmurShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MurmurTheme(
    appearanceMode: AppearanceMode,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (appearanceMode) { ... }
    
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        typography = MurmurTypography,
        shapes = MurmurShapes,
        content = content,
    )
}

// Extension for easy access
val MaterialTheme.murmurTypography: Typography
    get() = MurmurTypography
val MaterialTheme.murmurShapes: Shapes
    get() = MurmurShapes
```

### 1.2 Replace all `MurmurTheme.typography` → `MaterialTheme.typography` (or `MaterialTheme.murmurTypography`)
**Files**: All UI files in `MurmurApp.kt`, `OemFixScreen.kt`, `StorageCenterScreen.kt`

### 1.3 Fix Widget Icons
**File**: `app/src/main/java/com/joy/murmur/widget/NowPlayingWidget.kt`
- Replace "?" with proper icons: ◀◀, ▶/⏸, ▶▶
- Use `ImageVector` or `Text` with proper Unicode

### 1.4 Standardize Component Library
**New file**: `app/src/main/java/com/joy/murmur/ui/components/MurmurComponents.kt`
Create reusable components:
- `MurmurCard`, `MurmurOutlinedCard`, `MurmurElevatedCard`
- `MurmurButton`, `MurmurOutlinedButton`, `MurmurTextButton`
- `MurmurTabRow`, `MurmurSlider`, `MurmurSwitch`
- `MurmurTextField`, `MurmurSearchField`
- `MurmurEmptyState`, `MurmurErrorState`

---

## Phase 2: Neural TTS (Sherpa-ONNX) (P1)

### 2.1 Add Sherpa-ONNX Dependencies
**File**: `gradle/libs.versions.toml`
```toml
[versions]
sherpaOnnx = "1.12.0"  # Check latest

[libraries]
sherpa-onnx = { module = "com.k2fsa:sherpa-onnx", version.ref = "sherpaOnnx" }
```

### 2.2 Create Neural TTS Model Manager
**New file**: `app/src/main/java/com/joy/murmur/data/speech/NeuralTtsModelManager.kt`

```kotlin
class NeuralTtsModelManager(private val context: Context) {
    private val modelsDir = File(context.filesDir, "neural-voices").apply { mkdirs() }
    private val loadedModels = mutableMapOf<String, OfflineTts>()
    private val loadingLocks = mutableMapOf<String, CompletableDeferred<OfflineTts>>()
    
    suspend fun loadModel(modelId: String): OfflineTts
    suspend fun unloadModel(modelId: String)
    fun isModelLoaded(modelId: String): Boolean
    fun getModelInfo(modelId: String): ModelManifest?
    suspend fun downloadModel(modelId: String, progress: (Float) -> Unit)
}
```

### 2.3 Create Model Manifest System
**New file**: `app/src/main/java/com/joy/murmur/data/speech/NeuralTtsManifest.kt`

```kotlin
data class ModelManifest(
    val id: String,
    val name: String,
    val language: String,
    val languageTag: String,
    val version: String,
    val tokenizer: TokenizerInfo,
    val modelFiles: List<ModelFileInfo>,
    val voiceMetadata: VoiceMetadata,
    val licenses: List<LicenseInfo>,
    val checksums: Map<String, String>, // file -> SHA256
)

data class VoiceMetadata(
    val sampleRate: Int,
    val numSpeakers: Int,
    val isMultiLingual: Boolean,
    val speakerNames: List<String>?,
    val defaultSpeaker: Int,
)
```

### 2.4 Implement NeuralTtsSpeechGenerator
**New file**: `app/src/main/java/com/joy/murmur/data/speech/NeuralTtsSpeechGenerator.kt`

Implement `SpeechGenerator` interface:
- Lazy model loading via `NeuralTtsModelManager`
- ONNX session management
- Tokenizer integration (BPE/SentencePiece)
- Streaming synthesis with progress callbacks
- Cache integration via `TtsCache`

### 2.5 Add Neural TTS to Settings
**Files**: `DataStoreSettingsRepository.kt`, `SettingsRepository.kt`, `MurmurApp.kt`
- Add `selectedNeuralVoice` flow
- Add voice picker UI for neural voices
- Show model download status

---

## Phase 3: Audio Processing Pipeline (P1)

### 3.1 Create Audio Processing Chain
**New file**: `app/src/main/java/com/joy/murmur/playback/AudioProcessor.kt`

```kotlin
interface AudioProcessor {
    val id: String
    val name: String
    suspend fun process(input: AudioBuffer, config: AudioConfig): AudioBuffer
    val defaultConfig: AudioConfig
}

data class AudioConfig(
    val enabled: Boolean = true,
    val parameters: Map<String, Float> = emptyMap(),
)
```

### 3.2 Implement Processors
**New files**:
- `ReplayGainProcessor.kt` - Album/track gain, peak normalization
- `EqualizerProcessor.kt` - 10-band parametric EQ with presets
- `CrossfeedProcessor.kt` - Meier crossfeed for headphones
- `HeadroomLimiterProcessor.kt` - Soft clipping prevention with configurable headroom
- `BalanceProcessor.kt` - L/R balance
- `DitherProcessor.kt` - TPDF dithering for bit-depth reduction

### 3.3 Integrate with ExoPlayer
**File**: `app/src/main/java/com/joy/murmur/playback/PlaybackService.kt`

Use `ExoPlayer.setAudioProcessorChain()` or `AudioProcessor` chain:
```kotlin
val audioProcessors = listOf(
    ReplayGainProcessor(),
    EqualizerProcessor(),
    CrossfeedProcessor(),
    HeadroomLimiterProcessor(),
)
player.setAudioProcessorChain(audioProcessors)
```

### 3.4 Create Sound Screen
**New file**: `app/src/main/java/com/joy/murmur/ui/SoundScreen.kt`

Sections:
- **Source Info**: format, codec, sample rate, bit depth, channels, bitrate
- **Processing**: ReplayGain status, EQ bands, crossfeed, headroom/limiter
- **Output**: route, device name, output format/rate (actual)

### 3.5 Output Profiles
**New file**: `app/src/main/java/com/joy/murmur/data/playback/OutputProfileManager.kt`

```kotlin
data class OutputProfile(
    val id: String,
    val name: String,
    val deviceMatcher: DeviceMatcher,
    val eq: EqualizerConfig?,
    val gainDb: Float,
    val headroomDb: Float,
    val crossfeedEnabled: Boolean,
    val crossfeedConfig: CrossfeedConfig?,
    val balance: Float,
)
```

---

## Phase 4: Batch TTS Generation (P1)

### 4.1 Create Batch Job System
**New file**: `app/src/main/java/com/joy/murmur/data/speech/BatchTtsManager.kt`

```kotlin
data class BatchTtsJob(
    val id: String,
    val documentId: String,
    val status: JobStatus,
    val totalChapters: Int,
    val completedChapters: Int,
    val currentChapter: Int?,
    val outputUri: Uri?,
    val createdAt: Long,
    val updatedAt: Long,
    val error: String?,
)

enum class JobStatus { PENDING, RUNNING, PAUSED, INTERRUPTED, COMPLETED, CANCELLED, FAILED, NEEDS_ATTENTION }
```

### 4.2 Work Unit Checkpointing
- Chapter/section-based work units
- Persistent progress in Room (`BatchJobEntity`, `BatchWorkUnitEntity`)
- Resume on app launch
- Respect Android background limits (6h/24h for mediaProcessing)

### 4.3 Export Management
- Separate permanent storage from cache (SAF)
- Chapter-based export structure
- Optional full-book merge

---

## Phase 5: Polish & Hardening (P0/P1)

### 5.1 Reader Screen Improvements
- Add PDF Read Aloud support (extract text per page)
- Improve Markdown rendering (tables, footnotes, task lists)
- Add reading ruler overlay
- Auto-scroll with configurable speed

### 5.2 Mini Player Polish
- Persistent bottom sheet across all screens
- Artwork animation
- Expand to full player

### 5.3 Accessibility
- TalkBack labels for all interactive elements
- Semantic content descriptions
- Sufficient contrast (verify with Material3)
- Touch target sizes (48dp minimum)

### 5.4 Performance
- Lazy lists for large libraries
- Image loading with Coil (add dependency)
- Startup optimization (defer non-critical init)

---

## Implementation Order

```
Week 1-2: UX/UI Foundation (Phase 1)
  → MurmurTheme completion
  → Component library
  → Fix all typography references
  → Widget icons

Week 2-3: Neural TTS (Phase 2)
  → Sherpa-ONNX integration
  → Model manifest system
  → Lazy loading
  → Settings integration

Week 3-4: Audio Processing (Phase 3)
  → Processor chain
  → ReplayGain, EQ, Crossfeed, Headroom
  → Output profiles
  → Sound screen

Week 4-5: Batch TTS (Phase 4)
  → Job system
  → Checkpointing
  → Export management

Week 5-6: Polish (Phase 5)
  → Reader improvements
  → Mini player
  → Accessibility
  → Testing
```

---

## Validation Checklist

### UX/UI
- [ ] All screens use `MaterialTheme.typography` consistently
- [ ] Custom shapes applied to cards/buttons
- [ ] Elevation system visible
- [ ] Widget shows proper icons
- [ ] Dark/Light/System themes work correctly
- [ ] Animations on navigation/transitions

### Neural TTS
- [ ] Model downloads work offline
- [ ] Models load lazily (not at startup)
- [ ] Multiple voices selectable
- [ ] Synthesis quality acceptable
- [ ] Cache integration works

### Audio Processing
- [ ] ReplayGain applies when tags present
- [ ] EQ bands adjustable with presets
- [ ] Crossfeed toggle for headphones
- [ ] Headroom prevents clipping
- [ ] Output profiles auto-apply
- [ ] Sound screen shows honest info

### Batch TTS
- [ ] 200h book generates checkpointed
- [ ] Resume after process death
- [ ] Respects Android background limits
- [ ] Export to user storage
- [ ] Storage estimation before start

### General
- [ ] Build passes `./gradlew :app:assembleDebug`
- [ ] No lint errors
- [ ] All P0 acceptance tests pass (145-152)

---

## Open Questions

1. **Sherpa-ONNX model source**: Which models to bundle? (piper-voices? sherpa-onnx releases?)
2. **Audio processing library**: Use ExoPlayer's built-in EQ or custom JNI?
3. **Figma design tokens**: Need actual design specs (colors, spacing, corner radius) from provided Figma links
4. **Minimum SDK**: Currently API 26 - confirm for Sherpa-ONNX compatibility
5. **APK size budget**: Neural TTS models add 50-200MB per voice