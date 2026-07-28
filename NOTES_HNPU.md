# HNPU / Kitten exploration notes (Maven QHexRT 0.20.12)

App default: **Maven Central** `runanywhere-qhexrt-android:0.20.12`  
Local engine only when: `./gradlew :app:installDebug -Prunanywhere.useLocalQhexrt=true`

## Reboot root cause (confirmed 2026-07-27)

Not an Android “app crash” and not primarily our Kotlin unload flags.

Samsung dump: `/data/log/dumpstate_lastkmsg_37_20260727_183546_KP.log.gz`  
- **Upload cause:** `KERNEL PANIC`  
- **PC:** `plist_add+0x80/0x12c`  
- **LR:** `pm_qos_update_target`  
- **Call chain:** `fastrpc_device_ioctl` → `fastrpc_control` → `fastrpc_internal_control` → `dev_pm_qos_add_request` → `plist_add` (module `frpc_adsprpc`)  
- **Thread:** `DefaultDispatch` (QNN/HTP / app dispatcher)  
- **Process:** `starter_example` pid 17029  
- Immediately before: `fastrpc_cleanup_session` for that tgid + `fastrpc_wait_for_completion: poll mode timeout`  
- **dma-buf:** `heaviest_task_dma_buf: starter_example … 659868KB` (~645 MB)  
- Same signature appears **many times** in `history_of_auto_comment.txt` and KP dumps 33–37 today.

**Interpretation:** Qualcomm CDSP FastRPC + PM QoS path is panicking the kernel while our app holds a large Hexagon/dmabuf footprint (load / session teardown / control ioctl). Soft `unload_all` is not a reliable recovery on this S24 / SM8650 build.

### Safer exploration

1. Prefer **QHexRT Lab → Hard reset HNPU (kill app)** between HNPU models (process death drops fastrpc + dmabuf).  
2. One HNPU model per process lifetime when possible.  
3. Avoid rapid Load → Unload → Load cycles.  
4. Do **not** run Voice pipeline with HNPU STT+TTS+LLM together.  
5. Soft “Unload all” is still useful for CPU models; for HNPU, hard reset is safer.

## Known Kitten issues (historical on Maven 0.20.11; re-verify on 0.20.12)

| Symptom | Likely cause | Status |
|---|---|---|
| Speaks “Hexagon Neural Processing Unit” instead of your text | Over-Lmax (~40 phones) fell back to baked fixture | Fixed in local QHexRT source; **not** in Maven 0.20.11 |
| “Hello! … text-to-speech…” → mangled / missing Hello | Short window after `!` → T&lt;W vocoder silence + bad chunking | Needs larger Lmax re-forge |
| “speech” heard as “speed” | Bridged Kokoro g2p / nano model quality | Model limitation |
| “south hampton” weak | Prefer lexicon form Southampton | Local engine normalizes; Maven may not |
| Long lines pause / choppy | No native multi-window stitch in Maven AAR | Local engine stitches; Maven does not |

## Vision / VLM download note

Vision used to auto-select **InternVL3.5 1B (~3.1 GB HNPU)**. On ~8GB phones the
app RAM gate requires `model size + 1.5 GB` free, so Load often fails with a
“needs ~3.1 GB + 1.5 GB headroom…” message (easy to misread as “more model
support”). Prefer:

- **SmolVLM 256M (CPU / llama.cpp)** — general image Q&A, ~365 MB, two GGUF files  
- **Nemotron OCR (HNPU)** — OCR-only, ~121 MB (now preferred HNPU default)  
- InternVL / Qwen3-VL — only with lots of free RAM + hard reset; reboot risk  

## What works well (Maven)

- Kokoro-82M EN (HNPU) — longer text, better intelligibility  
- Magpie TTS (HNPU) — when gated HF token is set  
- Piper (CPU) — reliable fallback  
- LFM2.5 LLM alone (after hard reset)
- Nemotron OCR (HNPU) / SmolVLM (CPU) for vision smoke


## Follow-ups

- [ ] Engine/SDK: avoid fastrpc poll-mode / pm_qos thrash on unload; ensure dmabuf free on unload  
- [ ] Re-forge Kitten Graph A with L≥128  
- [ ] Publish new `runanywhere-qhexrt-android` with live-window stitch + no baked fallback  
- [ ] Cap concurrent Hexagon allocations / refuse load when dma-buf footprint high  
- [ ] Plane B soak: kitten_nano + kokoro with **hard reset** between (not soft unload)
