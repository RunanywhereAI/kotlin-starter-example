# RunAnywhere AI — Android Example

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.runanywhere.runanywhereai">
    <img src="https://img.shields.io/badge/Google%20Play-Download-414141?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%20API%2024%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android API 24+" />
  <img src="https://img.shields.io/badge/Kotlin-2.4%2B-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.4+" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/License-RunAnywhere-blue?style=flat-square" alt="RunAnywhere License" />
</p>

**A production-ready reference app for the RunAnywhere Kotlin SDK.** Chat, speech, vision, voice agents, RAG, and model management—all running on-device with privacy-first, offline-capable inference.

---

## Requirements

| Item | Minimum |
|------|---------|
| **Android Studio** | Latest stable (Ladybug or newer recommended) |
| **Android SDK** | API 24+ (Android 7.0); compile/target SDK 37 |
| **JDK** | 17 |
| **Disk space** | Several GB for downloaded AI models |
| **Device** | ARM64 physical device recommended; emulator supported for most features |

No NDK, CMake, or native toolchain is required — the SDK ships prebuilt native libraries inside its published AARs.

---

## Setup

> This sample consumes the RunAnywhere SDK **entirely from Maven Central**. There is nothing to stage, build, or link locally: a clean clone compiles as soon as Gradle can reach the network.

### 1. Clone and open the example

```bash
git clone https://github.com/RunanywhereAI/runanywhere-android.git
cd runanywhere-android
```

### 2. Point Gradle at your Android SDK

Export `ANDROID_HOME`, or copy `local.properties.example` to `local.properties` and set `sdk.dir`.

### 3. Verify and run

```bash
./scripts/verify.sh
```

Or open the project in Android Studio and run the **app** configuration, or install from the command line:

```bash
./gradlew :app:installDebug
```

---

## SDK dependency

All SDK artifacts come from Maven Central under the group `io.github.sanchitmonga22`, pinned by the single `runanywhere` version in `gradle/libs.versions.toml` (currently **0.20.18**). Nothing is declared as a local AAR or project path:

```kotlin
// gradle/libs.versions.toml
runanywhere = "0.20.18"

// app/build.gradle.kts
implementation(libs.runanywhere.sdk)       // io.github.sanchitmonga22:runanywhere-sdk
implementation(libs.runanywhere.llamacpp)  // io.github.sanchitmonga22:runanywhere-llamacpp
implementation(libs.runanywhere.onnx)      // io.github.sanchitmonga22:runanywhere-onnx
implementation(libs.runanywhere.qhexrt)    // io.github.sanchitmonga22:runanywhere-qhexrt-android
```

| Coordinate | Role | On Maven Central at 0.20.18 |
|---|---|---|
| `runanywhere-sdk` | Core SDK + commons native libraries | ✅ |
| `runanywhere-llamacpp` | LlamaCPP backend (LLM, VLM) | ✅ |
| `runanywhere-onnx` | Sherpa-ONNX backend (STT, TTS, VAD) | ✅ |
| `runanywhere-qhexrt-android` | QHexRT backend (Qualcomm Hexagon NPU) | ✅ |

All four are published together; never mix versions. To move to a new SDK release, bump `runanywhere` in `gradle/libs.versions.toml`, then regenerate the two reproducibility files that pin the resolved graph — `app/gradle.lockfile` and `gradle/verification-metadata.xml`:

```bash
# 1. Dependency lock. Host-independent, so any OS will do.
./gradlew :app:dependencies --write-locks

# 2. Checksums. Gradle *merges* into the existing file — it adds entries and never
#    removes them — so run this on top of the committed file rather than deleting it.
GRADLE_USER_HOME="$(mktemp -d)" ./gradlew --write-verification-metadata sha256 \
    :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

Two traps make this file easy to get subtly wrong; both were hit while writing the
current one:

- **Use a throwaway `GRADLE_USER_HOME`.** Checksums are only recorded for artifacts
  Gradle actually *downloads* during the run. Against a warm `~/.gradle` the run looks
  successful but silently omits things already cached — in practice a handful of parent
  POMs and BOM metadata (`guava-parent`, `junit-bom`, `kotlin-gradle-plugins-bom`). The
  gap is invisible until someone builds from a genuinely cold cache, i.e. CI.
- **Cover Linux *and* macOS.** A few build-time artifacts are OS-classified
  (`com.android.tools.build:aapt2:…-linux.jar` vs `…-osx.jar`) and Gradle records only
  the host's. The committed file carries both, so one file satisfies the Linux CI runner
  and a macOS developer. Because step 2 merges, the way to keep both is to run it on
  Linux (Docker is fine), commit that file, then run it again on macOS on top. If you can
  only reach one OS, hand-add the missing `<artifact>` line to the
  `com.android.tools.build:aapt2` component — a Linux-only file breaks every macOS
  developer, and a macOS-only file breaks CI.

Then confirm the result the same way CI will, with no bypass flags:

```bash
./gradlew :app:assembleDebug          # dependency verification live
CI=true ./gradlew :app:assembleDebug  # + LockMode.STRICT
```

### Testing an unreleased SDK build (developer-only)

To try a change from a [`runanywhere-sdks`](https://github.com/RunanywhereAI/runanywhere-sdks)
checkout before it is on Maven Central, publish it to `~/.m2` and point this repo at it:

```bash
# In the monorepo — publishes io.github.sanchitmonga22:*:<core/VERSION>
(cd path/to/runanywhere-sdks/bindings/kotlin && ./gradlew publishToMavenLocal)

# Here — opt in per invocation, and relax verification for that one run
./gradlew :app:assembleDebug \
    -Prunanywhere.useLocalSdkAars=true \
    --dependency-verification=lenient
```

`-Prunanywhere.useLocalSdkAars=true` adds `mavenLocal()` ahead of Google and Maven
Central, scoped by `content { includeGroup("io.github.sanchitmonga22") }` so a stale
`~/.m2` copy of any *other* dependency cannot shadow the verified one.

`--dependency-verification=lenient` is **required** alongside it, and is not a bypass
being smuggled in. A locally published AAR has the same coordinates as the released one
but different bytes, so it can never match the sha256 in `gradle/verification-metadata.xml`.
Without the flag the build stops with `artifacts failed verification`, which is the gate
working correctly. Relax it per invocation like this; do **not** add a
`<trust group="io.github.sanchitmonga22"/>` entry to the committed metadata, because that
would permanently un-pin the four artifacts the gate exists to pin.

Both flags are per-invocation only. Never commit `runanywhere.useLocalSdkAars` to
`gradle.properties` and never set it in CI: `ci.yml` exists to prove a clean clone
resolves the SDK from Maven Central, and a local AAR would make that proof vacuous.

---

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:
`ubuntu-latest`, Temurin JDK 17 (plus JDK 21 for the Gradle daemon, which
`gradle/gradle-daemon-jvm.properties` pins), the Android SDK via
`android-actions/setup-android` (`platforms;android-37.0`, `build-tools;37.0.0`),
Gradle caching via `gradle/actions/setup-gradle`, then `./gradlew :app:assembleDebug`.

CI runs the **unmodified** command — no bypass flags — so it exercises exactly the
path a developer does. Both reproducibility gates are enforced there and locally:

| Gate | What enforces it | What it pins |
|---|---|---|
| `gradle/verification-metadata.xml` | Auto-enabled by Gradle whenever the file exists; `./scripts/verify.sh` additionally passes `--dependency-verification strict` | sha256 of every resolved artifact, including the four `io.github.sanchitmonga22` AARs and both OS variants of `aapt2` |
| `app/gradle.lockfile` | `app/build.gradle.kts` flips to `LockMode.STRICT` when `$CI` is set (or with `-Prunanywhere.strictLocks=true`); `LENIENT` otherwise so Android Studio sync stays friction-free | the exact resolved version of every module on every configuration |

Both files are committed and current. If a dependency or SDK bump makes either gate
fail, regenerate them (see [SDK dependency](#sdk-dependency)) — do **not** add
`--dependency-verification=off` or `env -u CI` to the workflow, because that hides the
breakage from CI while every clean clone keeps failing.

---

## Features

| Feature | Description |
|---------|-------------|
| **AI Chat** | Streaming LLM conversations with analytics and thinking-mode support |
| **Speech-to-Text** | Batch and live transcription via Sherpa-ONNX / Whisper |
| **Text-to-Speech** | Neural Piper voices and system TTS fallback |
| **Voice Assistant** | Full STT → LLM → TTS pipeline |
| **Vision (VLM)** | Camera and image understanding |
| **RAG** | Document ingestion and on-device Q&A |
| **Model Management** | Download, load, unload, and delete models |
| **Storage** | Usage overview and cache cleanup |
| **Solutions** | YAML pipeline demos synced from shared catalog |
| **Offline** | Inference runs locally after models are downloaded |

---

## NPU / QHexRT (Snapdragon devices)

On supported Qualcomm Hexagon NPU hardware, the app can register the QHexRT backend for accelerated inference. The QHexRT backend ships as `io.github.sanchitmonga22:runanywhere-qhexrt-android` and is already on the app's dependency list.

To test private `runanywhere/*_HNPU` model bundles:

1. Open **Settings → Downloads**.
2. Enter a Hugging Face token and tap **Save token**.
3. Download and load an HNPU model from the model picker. The SDK resolves the correct Hexagon architecture natively.
4. Tap **Clear** to return to public, no-auth downloads.

The token is passed through the SDK at runtime; it is not stored in source, assets, or logs. Private QHexRT release and device-suite workflows live in a separate checkout—see your internal QHexRT documentation if you maintain that stack.

---

## Project structure

```
RunAnywhereAI/
├── app/src/main/java/com/runanywhere/runanywhereai/
│   ├── RunAnywhereApplication.kt    # SDK init and backend registration
│   ├── ui/screens/                  # Feature screens (chat, voice, vision, …)
│   ├── ui/navigation/               # Compose navigation
│   ├── ui/theme/                    # Material 3 theming (#FF6900 brand)
│   └── data/                        # Model catalog, settings repositories
├── gradle/libs.versions.toml         # SDK Maven coordinates + all dependency versions
├── scripts/
│   ├── verify.sh                    # Strict debug APK build gate
│   └── smoke.sh                     # Fast SDK API coverage check
└── README.md
```

The app resolves every SDK artifact from Maven Central — it contains no local AARs and no relative paths into an SDK source tree, so it builds standalone.

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Could not find io.github.sanchitmonga22:runanywhere-*` | Check the `runanywhere` version in `gradle/libs.versions.toml` is actually published to Maven Central, and that `mavenCentral()` is reachable |
| Gradle dependency verification failures (`N artifacts failed verification`) | Regenerate `gradle/verification-metadata.xml` — follow [SDK dependency](#sdk-dependency) step 2 exactly, including the throwaway `GRADLE_USER_HOME` and the Linux + macOS passes |
| `... is not part of the dependency lock state` (usually only with `CI=true`) | Regenerate the lock: `./gradlew :app:dependencies --write-locks` |
| QHexRT / NPU models unavailable | Confirm the device has a supported Hexagon NPU; HNPU bundles also require a saved HF token |

For a quick static check without a full compile:

```bash
./scripts/smoke.sh
```

---

## Related links

| Resource | Link |
|----------|------|
| **Kotlin SDK** | [github.com/RunanywhereAI/runanywhere-sdks](https://github.com/RunanywhereAI/runanywhere-sdks/tree/main/sdk/runanywhere-kotlin) |
| **Maven Central** | [io.github.sanchitmonga22](https://central.sonatype.com/namespace/io.github.sanchitmonga22) |
| **Play Store** | [com.runanywhere.runanywhereai](https://play.google.com/store/apps/details?id=com.runanywhere.runanywhereai) |
| **Discord** | [discord.gg/N359FBbDVd](https://discord.gg/N359FBbDVd) |
| **Issues** | [GitHub Issues](https://github.com/RunanywhereAI/runanywhere-sdks/issues) |
| **Email** | founders@runanywhere.ai |

---

## License

This project is licensed under the RunAnywhere License (Apache 2.0 based, with additional commercial-use terms). See [LICENSE](https://github.com/RunanywhereAI/runanywhere-sdks/blob/main/LICENSE) for details.
