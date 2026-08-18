# RunAnywhere AI for Android

<p align="center">
  <img src="https://raw.githubusercontent.com/RunanywhereAI/runanywhere-sdks/main/docs/logo.svg" alt="RunAnywhere" width="120"/>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.runanywhere.runanywhereai">
    <img src="https://img.shields.io/badge/Google%20Play-Download-414141?style=for-the-badge&logo=google-play&logoColor=white" alt="Get it on Google Play" />
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android 7.0+" />
  <img src="https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin 2.4" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/NPU-Snapdragon-C41230?style=flat-square&logo=qualcomm&logoColor=white" alt="Snapdragon NPU" />
  <img src="https://img.shields.io/badge/License-RunAnywhere-blue?style=flat-square" alt="RunAnywhere License" />
</p>

The RunAnywhere consumer app for Android, written in Kotlin.

Ask it questions, talk to it, or show it what your camera sees. The models run on your phone,
so nothing you type or photograph leaves it, and it works with the network off. On Snapdragon
hardware the inference runs on the Hexagon NPU.

## Get it

**[Google Play](https://play.google.com/store/apps/details?id=com.runanywhere.runanywhereai)**. Android 7.0 or newer, ARM64.

<!-- Media slot: one GIF showing chat with tool calling, the voice assistant, and camera
     vision. Waiting on the capture pass that follows the current app bug fixes. -->

## What it looks like

Captured on a physical arm64 device running LFM2 350M, quantised Q4_K_M, through the
llama.cpp backend.

| | |
|---|---|
| ![Chat with a model loaded](docs/screenshots/01-home.png) | ![An answer](docs/screenshots/02-chat.png) |
| The header names the loaded model and shows it is local and ready. The chips below are one-tap prompts. | An answer generated on the device. |
| ![Talk](docs/screenshots/04-voice.png) | ![Documents](docs/screenshots/06-documents.png) |
| Talk picks a speech-to-text, chat, text-to-speech, and voice-detection model for the hardware, and loads all four together. | Document Q&A, with reranking and multi-query expansion as separate switches. |
| ![The Advanced hub](docs/screenshots/08-advanced.png) | ![Settings](docs/screenshots/07-settings.png) |
| Everything past chat lives here: OCR, segmentation, image generation, diarization, transcription, benchmarks. | Sampling, response length, the system prompt, and streaming. |

The rest, including the landscape layout, are in [`docs/screenshots/`](docs/screenshots).

## What you can do

| | |
| --- | --- |
| **Ask** | Streaming chat with thinking mode, tool calling, and per-response analytics |
| **Talk** | Hands-free voice assistant: it listens, transcribes, thinks, and speaks back |
| **Images and live** | Ask about a photo, or about what the camera sees right now |
| **Documents** | Add documents and ask questions, with sources cited |
| **Advanced** | OCR, segmentation, diarization, image generation, read aloud, transcription, voice activity, tools, benchmarks |

Models download from a curated catalog through a sheet you can reach from any screen that
needs one. Cloud providers exist but are opt-in and off by default.

## Snapdragon NPU

On supported Qualcomm Hexagon hardware the app registers the QHexRT backend and inference
runs on the NPU. Registration is rejected internally on parts outside the validated V75,
V79, and V81 set, and the backend is ARM64 only, so it is unavailable on x86_64 emulators.

Private `runanywhere/*_HNPU` model bundles need a Hugging Face token: Settings, then Private
Downloads, paste the token, save. It is held in protected app storage, re-applied on each
start, and never written to source, assets, or logs.

## Build it yourself

```bash
git clone https://github.com/RunanywhereAI/runanywhere-android.git
cd runanywhere-android
./gradlew :app:installDebug
```

You need Android Studio (latest stable), JDK 17, and a few GB of disk for models. No NDK,
CMake, or native toolchain: the SDK ships prebuilt native libraries inside its published
AARs. An ARM64 physical device is strongly preferred, since the NPU backend does not exist
on emulators.

[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) covers the SDK pinning rules, dependency
verification, testing an unreleased SDK build, CI, and troubleshooting.

## Architecture

Four AARs from Maven Central, no local project paths. Three share one version; QHexRT
carries its own, because it stopped being published to Maven Central after `0.20.19`.

```
        RunAnywhere AI (Jetpack Compose, MVVM)
                        │
        ┌───────────────┴────────────────┐
        │   io.github.sanchitmonga22:*   │
        └───────────────┬────────────────┘
                        │
   ┌───────────────┬────┴─────────┬──────────────────┐
   │               │              │                  │
runanywhere-sdk  llamacpp       onnx          qhexrt-android
core + commons   LLM · VLM   embeddings ·      Hexagon NPU
   0.20.24        0.20.24    STT·TTS·VAD        arm64 only
                              0.20.24            0.20.19
                        │
                        ▼
              C++ commons, one core
        shared with Swift, Web, and Electron
```

Business logic lives in the SDK. The app is Compose UI, view models, and thin
`RunAnywhere.*` calls. The catalog registers in the background after the first frame, so
cold start is not blocked behind roughly a hundred `models.register()` JNI calls.

| Reference | |
| --- | --- |
| Building, pinning, tests, CI, troubleshooting | [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) |
| Contributor conventions | [`AGENTS.md`](AGENTS.md) |

## The other apps

| Platform | Repo |
| --- | --- |
| iOS and macOS, Swift | [runanywhere-ios](https://github.com/RunanywhereAI/runanywhere-ios) |
| Windows, Electron | [runanywhere-electron](https://github.com/RunanywhereAI/runanywhere-electron) |
| Web, TypeScript | [runanywhere-web](https://github.com/RunanywhereAI/runanywhere-web) |
| SDK monorepo | [runanywhere-sdks](https://github.com/RunanywhereAI/runanywhere-sdks) |
| Documentation | [docs.runanywhere.ai](https://docs.runanywhere.ai) |
| Discord | [discord.gg/N359FBbDVd](https://discord.gg/N359FBbDVd) |

## License

RunAnywhere License, Apache 2.0 based with additional commercial-use terms. See
[LICENSE](LICENSE).
