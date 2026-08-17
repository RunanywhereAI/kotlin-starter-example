# Android RunAnywhereAI example

This file applies to `RunanywhereAI/runanywhere-android`, a standalone Android app that
consumes the RunAnywhere Kotlin SDK from Maven Central. Run commands from the repository
root unless noted otherwise.

Single Gradle module, `:app`. minSdk 24, compile and target SDK 37, Java 17, AGP 9.2.1,
Gradle 9.6.0. The Gradle daemon needs JDK 21 (`gradle/gradle-daemon-jvm.properties` pins
`toolchainVersion=21`) even though the app compiles against 17.

## Common commands

```bash
./scripts/smoke.sh                         # Fast static SDK-usage check
./scripts/verify.sh                        # Strict debug APK build gate
./gradlew :app:assembleDebug               # Debug APK
./gradlew :app:testDebugUnitTest           # JVM tests
./gradlew :app:lintRelease                 # Release lint
```

Instrumented tests compile against the debug variant by default; pass
`-Prunanywhere.testBuildType=release` to run them against the minified, signed release
variant instead.

## SDK dependency

The app consumes the RunAnywhere SDK entirely from Maven Central. There are no local AARs
and no relative paths into any SDK source tree. Coordinates live in
`gradle/libs.versions.toml` under the single `runanywhere` version:

| Coordinate | Role |
|---|---|
| `io.github.sanchitmonga22:runanywhere-sdk` | Core SDK and the commons native library |
| `io.github.sanchitmonga22:runanywhere-llamacpp` | llama.cpp backend (LLM, VLM) |
| `io.github.sanchitmonga22:runanywhere-onnx` | ONNX Runtime (embeddings) and Sherpa-ONNX (STT, TTS, VAD) in one AAR |
| `io.github.sanchitmonga22:runanywhere-qhexrt-android` | QHexRT backend (Qualcomm Hexagon NPU), arm64 only |

The four move in lockstep; never mix versions across them. To move to a new SDK release,
bump `runanywhere` in `gradle/libs.versions.toml`, then regenerate both reproducibility
files. The README's "SDK dependency" section has the exact procedure and its two traps:
regenerate the checksums against a throwaway `GRADLE_USER_HOME` (a warm cache silently
omits parent POMs and BOM metadata), and keep both the `-linux.jar` and `-osx.jar`
`com.android.tools.build:aapt2` entries so the one committed file satisfies CI and macOS
developers alike.

Dependency verification and strict dependency locking are enforced in CI with no bypass
flags. If a bump breaks them, regenerate the files; never re-add
`--dependency-verification=off` or `env -u CI` to `.github/workflows/ci.yml`.

For a monorepo change that is not yet on Maven Central, publish it with
`publishToMavenLocal` and build with `-Prunanywhere.useLocalSdkAars=true
--dependency-verification=lenient`. Both flags are per-invocation only; never commit
either, and never set them in CI.

The published POMs supply the SDK's own transitive dependencies (wire-runtime, okhttp,
coroutines-core, okio, kotlin-stdlib, kotlinx-serialization-json, androidx core-ktx), so
`app/build.gradle.kts` declares only what the app itself uses. The one exception is
`kotlinx-coroutines-android`, declared directly so the Android artifact does not skew
below the `-core` version the SDK POM pins.

## Build configuration

`app/build.gradle.kts` reads backend configuration from environment variables first, then
the gitignored `local.properties`. `RUNANYWHERE_BASE_URL` and `RUNANYWHERE_API_KEY` must
be both set or both blank; the configuration phase fails otherwise. Blank means the SDK
initializes in its development environment, which is the normal open-source path.

ABI filters differ per variant on purpose: release ships arm64-v8a only (QHexRT is
arm64-only hardware and a single slice roughly halves the APK), debug adds x86_64 so
emulator development still works with CPU backends.

`bundleRelease` and `signReleaseBundle` depend on `verifyPlayRelease`, which checks real
upload signing, an HTTPS control-plane URL, and the upload certificate SHA-256 against
`UPLOAD_CERT_SHA256`. `bundleRelease` also depends on `generateReleaseSbom`.

## Production release requirements

A production build needs a real org-scoped API key and backend base URL — the same pair
used by `runanywhere-ios`'s `RunAnywhereLocalSecrets.plist`, `runanywhere-electron`'s
`.env`, and `runanywhere-web`'s Vercel production env. Set them in the gitignored
`local.properties` as `runanywhere.apiKey` / `runanywhere.baseUrl`; ask a maintainer for
current production credentials. Never hardcode them in any committed file.

A production build must resolve the SDK only from Maven Central — never with
`-Prunanywhere.useLocalSdkAars=true`, which is for local monorepo iteration only and must
never be used for a release artifact. Before testing a "production" build, confirm no
local Gradle property or env var is forcing the local-AAR path.

Emulator/CI passing is not sufficient. Smoke-test on real hardware: install the built APK
on a connected physical device (`adb devices`, `adb install -r ...` or
`./gradlew :app:installDebug`) and confirm cold start, model-catalog population, and at
least one real model load/inference — ideally one exercising the QHexRT/Hexagon NPU
backend, since that is this app's differentiator and cannot be validated on x86 emulators.

A signed release AAB additionally needs the real Play upload keystore
(`KEYSTORE_PATH`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` env vars, never committed
or written to `local.properties`) and must pass `verifyPlayRelease`'s upload-cert check.

## Scripts

| Script | Purpose and normal use |
|---|---|
| `smoke.sh` | Grep-based check that the expected SDK entry points are still called from `app/src/main`. Set `RUN_BUILD_GATES=1` to call `verify.sh` too. |
| `verify.sh` | Debug APK build gate with `--dependency-verification strict`. Needs only the Android SDK and network access. |

`smoke.sh` matches source text, so it catches a deleted call site but proves nothing about
whether the code compiles or runs. `verify.sh` is the real gate.

After editing these scripts, run `bash -n scripts/*.sh`, `scripts/smoke.sh`, and
`git diff --check`.

`app/src/main/java/.../ui/screens/solutions/SolutionsYaml.kt` is generated from the
canonical solution YAMLs in the SDK monorepo and committed verbatim. Regenerate it there,
not here. Its header comment names paths from before the 0.20.17 monorepo restructure
(`examples/android/...`, `sdk/runanywhere-commons/...`) and the script it names does not
exist in this repo; the current sources live under `core/` and `bindings/` in
`RunanywhereAI/runanywhere-sdks`.

## Architecture notes

`RunAnywhereApplication.onCreate` initializes repositories, then runs SDK setup on an IO
scope. Order matters: `LlamaCPP.register()` and `ONNX.register()` run before
`RunAnywhere.initialize()`, or a concurrent `loadModel()` can fail with -422 while only
the platform backend is registered. `QHexRT.register()` runs after `initialize()`, because
it extracts DSP skels through the SDK-owned application `Context`. Both CPU backend
registrations are wrapped in try/catch and report through `BackendAvailability` so a
missing native library degrades the model picker instead of aborting setup.

Catalog seeding runs after `GlobalState.markReady()`, not before. The roughly 105
sequential `models.register()` JNI calls take about 13 s on a Snapdragon 8 Elite, which in
front of the splash was the largest source of cold-start abandonment. A failure there is
non-fatal: it means fewer rows in the picker, never the init-error screen.

Routes are type-safe `@Serializable` objects in `ui/navigation/Destinations.kt`. The
drawer shows six `ConsumerDestination` entries in two groups; everything else is reached
through `MoreScreen`. Model management is a sheet
(`ui/screens/models/ModelSelectionSheet.kt`), not a route.

## Design system

Brand primary is RunAnywhere orange #FF6900, the logo color. Theming is all Jetpack
Compose Material 3: `ui/theme/Color.kt` (`BrandOrange = 0xFFFF6900`, the `Primary*` tonal
ramp around that hue, and `BrandGradient` pairing it with `BrandRed`) and `ui/theme/Theme.kt`
(`lightColorScheme` / `darkColorScheme`, no dynamic color, so the brand is guaranteed).
`res/values/colors.xml` holds only structural values: black, white, and
`brand_window_background`, which mirrors `Neutral6` so the launch window paints the dark
scheme's background and there is no light flash before Compose draws. When changing brand
colors, edit `Color.kt` and keep it in sync with the RunAnywhere design guideline.
