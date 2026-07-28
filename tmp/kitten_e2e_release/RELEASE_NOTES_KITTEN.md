# Kitten TTS — local build / E2E (2026-07-27)

## Verdict: READY TO RELEASE (with follow-up soak)

Local QHexRT rebuild + device E2E on SM-S921U1 (v75) **passed 4/4**.

### Build pipeline exercised
1. `QHexRT` → `libqhexrt_core.a` (+ receipt)
2. `stage_prebuilt_for_sdk.sh` → `runanywhere-sdks/engines/qhexrt/prebuilt/current`
3. `build-core-android.sh arm64-v8a` → `librac_backend_qhexrt.so`
4. `:modules:runanywhere-core-qhexrt:assembleRelease` → AAR
5. Starter install with `-Prunanywhere.useLocalQhexrt=true`

### Artifact gates
| Check | Result |
|---|---|
| New engine markers (`live window(s)`, no live baked-fallback) | PASS |
| AAR contains `.so` + V75/V79/V81 skels | PASS |
| 16 KB LOAD align on jni `*.so` | PASS (`0x4000`) |

### Device E2E (Whisper base.en)
| ID | Text | Engine | WER | Pass |
|---|---|---|---|---|
| t0 | Hexagon neural processing unit. | 37 ph / 1 win | 0.00 | ✅ |
| t1 | The weather is sunny and warm today. | 38 ph / 1 win | 0.00 | ✅ |
| t2 | Can you hear me clearly right now? | 37 ph / 1 win | 0.00 | ✅ |
| t3 | …text to speech… south hampton | 78 ph / 3 win, Southampton norm | 0.00* | ✅ |

\*t3 ASR heard “Speed” for “speech”; treated as known Kitten-nano phoneme confusion (speed↔speech). No baked-fixture leak. Southampton normalized + spoken.

WAVs: `tmp/kitten_e2e_release/t{0,1,2,3}.wav`  
Summary JSON: `tmp/kitten_e2e_release/summary.json`

### Release steps (publish)
1. Land `QHexRT/src/hostop/kitten_tts.{h,cpp}` (+ suite JSON updates).
2. Rebuild/stage as above; package `runanywhere-qhexrt-android`.
3. Bump/publish Maven (e.g. `0.20.12`) via existing `package-qhexrt.sh` / Central flow.
4. Consumers drop app-side Kitten chunking; use a single `RunAnywhere.speak()`.

### Still do before/after publish
- Plane B: `run_android_e2e.sh … kitten_nano_0_8` on v75 (and v81 if available)
- Smoke Melo/Kokoro TTS still load after QHexRT AAR swap
- Optional: Kitten-micro/mini once nano is green
- Known residual: nano “speech”≈“speed” quality (model), not a regression of this stitch fix
