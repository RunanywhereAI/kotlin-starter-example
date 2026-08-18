# Development reference

Detail moved out of the root README so it stays a consumer-facing page. Everything here
is about building, pinning, and testing the app, not about using it.

## Requirements

| Item | Minimum |
|------|---------|
| Android Studio | A release that supports AGP 9.2 and Gradle 9.6 |
| Android SDK | API 24 (Android 7.0); compile and target SDK 37 |
| JDK | 17 for the build, plus 21 for the Gradle daemon (see below) |
| Disk space | Several GB, for downloaded models |
| Device | arm64 physical device recommended; the debug variant also builds x86_64 for emulators |

Two JDKs, because they serve different things. The app compiles against Java 17
(`compileOptions` in `app/build.gradle.kts`), while `gradle/gradle-daemon-jvm.properties`
pins `toolchainVersion=21` for the Gradle daemon itself. If no local JDK 21 is present,
Gradle provisions one over the network on every run.

No NDK, CMake, or native toolchain is required. The SDK ships prebuilt native libraries
inside its published AARs.

## Setup

Clone the repo:

```bash
git clone https://github.com/RunanywhereAI/runanywhere-android.git
cd runanywhere-android
```

Point Gradle at your Android SDK: export `ANDROID_HOME`, or copy
`local.properties.example` to `local.properties` and set `sdk.dir`.

Then build:

```bash
./scripts/verify.sh
```

Or open the project in Android Studio and run the `app` configuration, or install from
the command line:

```bash
./gradlew :app:installDebug
```

The app runs without a control plane. `RUNANYWHERE_BASE_URL` and `RUNANYWHERE_API_KEY`
are optional (settable via environment or `local.properties`); with both blank the SDK
initializes in its development environment. `app/build.gradle.kts` fails the
configuration phase if exactly one of the two is set.

## SDK dependency

All SDK artifacts come from Maven Central under the group `io.github.sanchitmonga22`,
pinned in `gradle/libs.versions.toml`. Three of the four share one version; QHexRT carries
its own, because it was excluded from Maven Central publishing after 0.20.19 and cannot
advance past it there. Nothing is declared as a local AAR or project path:

```kotlin
// gradle/libs.versions.toml
runanywhere = "0.20.24"
runanywhereQhexrt = "0.20.19"

// app/build.gradle.kts
implementation(libs.runanywhere.sdk)       // io.github.sanchitmonga22:runanywhere-sdk
implementation(libs.runanywhere.llamacpp)  // io.github.sanchitmonga22:runanywhere-llamacpp
implementation(libs.runanywhere.onnx)      // io.github.sanchitmonga22:runanywhere-onnx
implementation(libs.runanywhere.qhexrt)    // io.github.sanchitmonga22:runanywhere-qhexrt-android
```

| Coordinate | Role |
|---|---|
| `runanywhere-sdk` | Core SDK and the commons native library |
| `runanywhere-llamacpp` | llama.cpp backend (LLM, VLM) |
| `runanywhere-onnx` | ONNX Runtime (embeddings) and Sherpa-ONNX (STT, TTS, VAD) in one AAR |
| `runanywhere-qhexrt-android` | QHexRT backend (Qualcomm Hexagon NPU), arm64 only |

The four move in lockstep; never mix versions across them. To move to a new SDK release,
bump `runanywhere` in `gradle/libs.versions.toml`, then regenerate the two reproducibility
files that pin the resolved graph, `app/gradle.lockfile` and
`gradle/verification-metadata.xml`:

```bash
# 1. Dependency lock. Host-independent, so any OS will do.
./gradlew :app:dependencies --write-locks

# 2. Checksums. Gradle merges into the existing file (it adds entries and never
#    removes them) so run this on top of the committed file rather than deleting it.
GRADLE_USER_HOME="$(mktemp -d)" ./gradlew --write-verification-metadata sha256 \
    :app:assembleDebug :app:testDebugUnitTest :app:lintRelease
```

Two traps make the checksum file easy to get subtly wrong:

- Use a throwaway `GRADLE_USER_HOME`. Checksums are only recorded for artifacts Gradle
  actually downloads during the run. Against a warm `~/.gradle` the run looks successful
  but silently omits things already cached, in practice a handful of parent POMs and BOM
  metadata (`guava-parent`, `junit-bom`, `kotlin-gradle-plugins-bom`). The gap is
  invisible until someone builds from a genuinely cold cache, i.e. CI.
- Cover Linux and macOS. A few build-time artifacts are OS-classified
  (`com.android.tools.build:aapt2:...-linux.jar` vs `...-osx.jar`) and Gradle records only
  the host's. The committed file carries both, so one file satisfies the Linux CI runner
  and a macOS developer. Because step 2 merges, the way to keep both is to run it on Linux
  (Docker is fine), commit that file, then run it again on macOS on top. If you can only
  reach one OS, hand-add the missing `<artifact>` line to the
  `com.android.tools.build:aapt2` component. A Linux-only file breaks every macOS
  developer, and a macOS-only file breaks CI.

Then confirm the result the same way CI will, with no bypass flags:

```bash
./gradlew :app:assembleDebug          # dependency verification live
CI=true ./gradlew :app:assembleDebug  # + LockMode.STRICT
```

### Testing an unreleased SDK build

To try a change from a [`runanywhere-sdks`](https://github.com/RunanywhereAI/runanywhere-sdks)
checkout before it is on Maven Central, publish it to `~/.m2` and point this repo at it:

```bash
# In the monorepo. Publishes io.github.sanchitmonga22:*:<core/VERSION>
(cd path/to/runanywhere-sdks/bindings/kotlin && ./gradlew publishToMavenLocal)

# Here. Opt in per invocation, and relax verification for that one run
./gradlew :app:assembleDebug \
    -Prunanywhere.useLocalSdkAars=true \
    --dependency-verification=lenient
```

`-Prunanywhere.useLocalSdkAars=true` adds `mavenLocal()` ahead of Google and Maven
Central, scoped by `content { includeGroup("io.github.sanchitmonga22") }` so a stale
`~/.m2` copy of any other dependency cannot shadow the verified one.

`--dependency-verification=lenient` is required alongside it, and is not a bypass being
smuggled in. A locally published AAR has the same coordinates as the released one but
different bytes, so it can never match the sha256 in
`gradle/verification-metadata.xml`. Without the flag the build stops with
`artifacts failed verification`, which is the gate working correctly. Relax it per
invocation like this; do not add a `<trust group="io.github.sanchitmonga22"/>` entry to
the committed metadata, because that would permanently un-pin the four artifacts the gate
exists to pin.

Both flags are per-invocation only. Never commit `runanywhere.useLocalSdkAars` to
`gradle.properties` and never set it in CI: `ci.yml` exists to prove a clean clone
resolves the SDK from Maven Central, and a local AAR would make that proof vacuous.

## Continuous integration

`.github/workflows/ci.yml` runs on every push to `main` and every pull request:
`ubuntu-latest`, Temurin JDK 17 and 21, the Android SDK via `android-actions/setup-android`
(`platform-tools`, `platforms;android-37.0`, `build-tools;37.0.0`), Gradle caching via
`gradle/actions/setup-gradle`, then `./gradlew :app:assembleDebug --no-daemon --stacktrace`
and an APK upload.

CI runs the unmodified command, no bypass flags, so it exercises the same path a developer
does. Both reproducibility gates are enforced there and locally:

| Gate | What enforces it | What it pins |
|---|---|---|
| `gradle/verification-metadata.xml` | Auto-enabled by Gradle whenever the file exists; `./scripts/verify.sh` additionally passes `--dependency-verification strict` | sha256 of every resolved artifact, including the four `io.github.sanchitmonga22` AARs and both OS variants of `aapt2` |
| `app/gradle.lockfile` | `app/build.gradle.kts` flips to `LockMode.STRICT` when `$CI` is set (or with `-Prunanywhere.strictLocks=true`); `LENIENT` otherwise, so Android Studio sync stays friction-free | the exact resolved version of every module on every configuration |

If a dependency or SDK bump makes either gate fail, regenerate the files (see
[SDK dependency](#sdk-dependency)). Do not add `--dependency-verification=off` or
`env -u CI` to the workflow, because that hides the breakage from CI while every clean
clone keeps failing.


## Project layout

```
app/src/main/java/com/runanywhere/runanywhereai/
  RunAnywhereApplication.kt   SDK init, backend registration, catalog seeding
  MainActivity.kt             Compose host
  ui/navigation/              Type-safe routes and the drawer destinations
  ui/screens/                 One package per screen
  ui/theme/                   Material 3 theming, brand orange #FF6900
  data/                       Model catalog, settings, conversations, RAG, benchmarks
  tools/                      Built-in tool-calling implementations
  download/                   Model download service and progress state
app/build.gradle.kts          Variants, signing, dependency locking, SBOM, Play gate
gradle/libs.versions.toml     SDK coordinates and every dependency version
gradle/verification-metadata.xml  sha256 of every resolved artifact
app/gradle.lockfile           Resolved dependency graph
scripts/verify.sh             Strict debug APK build gate
scripts/smoke.sh              Fast static SDK API coverage check
.github/workflows/ci.yml      Clean-clone build gate
```

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `Could not find io.github.sanchitmonga22:runanywhere-*` | Check the `runanywhere` version in `gradle/libs.versions.toml` is published to Maven Central, and that `mavenCentral()` is reachable |
| `N artifacts failed verification` | Regenerate `gradle/verification-metadata.xml`, following [SDK dependency](#sdk-dependency) step 2 exactly, including the throwaway `GRADLE_USER_HOME` and the Linux and macOS passes |
| `... is not part of the dependency lock state` (usually only with `CI=true`) | Regenerate the lock: `./gradlew :app:dependencies --write-locks` |
| Gradle downloads a JDK on every run | Install a local JDK 21 for the daemon toolchain |
| `RUNANYWHERE_BASE_URL and RUNANYWHERE_API_KEY must either both be set or both be blank` | Set both, or clear both |
| NPU models unavailable | Confirm the device has a supported Hexagon NPU and an arm64 build; HNPU bundles also need a saved HF token |

For a quick static check without a full compile:

```bash
./scripts/smoke.sh
```

## Related links

| Resource | Link |
|----------|------|
| Kotlin SDK | [runanywhere-sdks/bindings/kotlin](https://github.com/RunanywhereAI/runanywhere-sdks/tree/main/bindings/kotlin) |
| Maven Central | [io.github.sanchitmonga22](https://central.sonatype.com/namespace/io.github.sanchitmonga22) |
| Play Store | [com.runanywhere.runanywhereai](https://play.google.com/store/apps/details?id=com.runanywhere.runanywhereai) |
| Discord | [discord.gg/N359FBbDVd](https://discord.gg/N359FBbDVd) |
| Issues | [GitHub Issues](https://github.com/RunanywhereAI/runanywhere-sdks/issues) |
| Email | founders@runanywhere.ai |

