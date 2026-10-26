# Murmur dependency and asset licensing

## Scope reviewed for P5

The project uses the Gradle version catalog in `gradle/libs.versions.toml`. The release runtime dependency graph was resolved from Gradle's `releaseRuntimeClasspath`; declared versions can be constrained by the Compose BOM. The dependency families directly used by the app are:

| Family | Purpose | License/source pointer |
|---|---|---|
| AndroidX Activity, AppCompat, Compose/Material 3, Navigation, DataStore, Work, DocumentFile, Room, Media3, Glance | Android UI, persistence, background work, SAF, playback, widget support | AndroidX projects: [Apache-2.0](https://github.com/androidx) |
| Kotlin standard library and kotlinx.serialization | Language runtime and JSON encoding | [Kotlin](https://github.com/JetBrains/kotlin) and [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), Apache-2.0 |
| JUnit 4 | Test-only unit test framework | [JUnit 4](https://github.com/junit-team/junit4), EPL-1.0 |

The project does not currently bundle neural TTS engines, voice/model assets, phonemizers, lexicons, or user media. Android platform TTS is invoked through the platform API; its engine and installed voices are supplied/managed by the device and are outside this app dependency inventory.

## Limits of this review

This is a direct-dependency-family review, not a generated Software Bill of Materials (SBOM), legal opinion, or exhaustive license audit of every resolved transitive artifact. Before a public release, generate and retain a release-variant SBOM and review each resolved artifact's license and notices, including transitive artifacts and any native libraries. Verify notices are included in the distributed artifact where required. Do not infer that an AndroidX umbrella repository statement replaces per-artifact release verification.

## Dependency policy

Before adding a library, record why it is needed, whether a platform or existing dependency suffices, maintenance status, permissions/network behavior, APK/storage impact, and license/notice obligations. Do not ship code or assets with unclear redistribution rights or casually add GPL/AGPL components.

Neural TTS remains deferred. Any future proposal must separately pin and audit the Android runtime, ABI/native artifacts, model, tokenizer, phonemizer, lexicon, and each voice asset. No neural model downloads or bundled voice assets are currently present.
