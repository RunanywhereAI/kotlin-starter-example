# RunAnywhere Kotlin SDK Starter

Android starter app for the **RunAnywhere Kotlin SDK** — privacy-first, on-device AI with Jetpack Compose. Includes **QHexRT** (Qualcomm Hexagon NPU) support from Maven Central.

## Features

| Demo | CPU (always available) | HNPU when Hexagon V75/V79/V81 |
|------|------------------------|-------------------------------|
| Chat / Tools / Structured / RAG | SmolLM2 360M (llama.cpp) | LFM2.5, Qwen3, Bonsai, … |
| Speech-to-Text | Sherpa Whisper Tiny | Whisper Base, Moonshine, Parakeet, Canary, … |
| Text-to-Speech | Piper Lessac | Magpie, Kitten, MeloTTS, Kokoro, … |
| Embeddings / RAG | MiniLM L6 v2 (ONNX) | Nemotron-3-Embed, NV-EmbedQA, … |
| Vision | SmolVLM 256M (llama.cpp) | InternVL, Qwen3-VL, Nemotron OCR, … |
| VAD | Silero (ONNX) | — |

**Both catalogs ship together.** On NPU phones the app prefers an HNPU default, but every screen picker and **Model Lab** still lists the original CPU models so you can switch anytime. Non-NPU devices keep CPU-only.

## Requirements

- Android Studio Hedgehog+ / JDK 17
- minSdk 26, targetSdk 37
- **arm64-v8a** physical device recommended (required for QHexRT)
- Free storage for model downloads (HNPU bundles are hundreds of MB to ~2 GB)

## Setup

```bash
cd kotlin-starter-example
# Ensure Android SDK path is available (local.properties or ANDROID_HOME)
./gradlew :app:installDebug
```

Dependencies resolve from **Maven Central** (`io.github.sanchitmonga22:runanywhere-*:0.20.11`):

```kotlin
implementation("io.github.sanchitmonga22:runanywhere-sdk:0.20.11")
implementation("io.github.sanchitmonga22:runanywhere-llamacpp:0.20.11")
implementation("io.github.sanchitmonga22:runanywhere-onnx:0.20.11")
implementation("io.github.sanchitmonga22:runanywhere-qhexrt-android:0.20.11")
```

`runanywhere-qhexrt-android` is **binary-only** (engine `.so` + QAIRT host libs + DSP skels + thin Kotlin registration API). Engine C++ source is not published.

## Bootstrap sequence

```kotlin
LlamaCPP.register()
ONNX.register()
RunAnywhere.initialize(context, SDKEnvironment.SDK_ENVIRONMENT_DEVELOPMENT)
QHexRT.register()                 // after initialize (needs Context for skels)
modelService.bootstrapModels()    // CPU always; HNPU rows via QHexRT.registerModelForDevice
```

## First launch (QHexRT device)

1. Open the app — home card should say **Hexagon NPU ready** with arch (e.g. `v75`) and SoC.
2. Open **QHexRT Lab** — full curated HNPU catalog (LLM / STT / TTS / embed / vision-OCR). Tap **Load** on any row.
3. Or open **Chat / Speech / Voice / Embeddings / Vision** and use the model dropdown to switch HNPU vs CPU backends.
4. Optional: set `hf.token=` in `local.properties` for gated repos (e.g. Magpie TTS), then rebuild.

CPU fallbacks remain in every picker.

## Architecture

```
app/src/main/java/com/runanywhere/kotlin_starter_example/
├── MainActivity.kt                 # SDK + QHexRT bootstrap
├── services/ModelService.kt        # Catalog, prefer-NPU selection, download/load
└── ui/
    ├── components/ModelLoaderWidget.kt
    └── screens/                    # Feature demos
```

## Troubleshooting

| Issue | Fix |
|-------|-----|
| Home stays on “Starting SDK…” | Check logcat `MainActivity` / `QHexRT` for bootstrap errors |
| Models are CPU-only on a Snapdragon phone | Need Hexagon **V75/V79/V81**; older SoCs fall back to CPU |
| Download fails for HNPU | Check network / Hugging Face availability; some gated repos need `RunAnywhere.setHfToken` |
| Install fails / huge APK | Expected — QHexRT + QAIRT native libs are large; arm64-only packaging is intentional |

## Resources

- [RunAnywhere SDKs](https://github.com/RunanywhereAI/runanywhere-sdks)
- Release: [v0.20.11](https://github.com/RunanywhereAI/runanywhere-sdks/releases/tag/v0.20.11)

**Built with RunAnywhere SDK v0.20.11**
