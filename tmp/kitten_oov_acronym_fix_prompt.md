## Fix Kitten TTS silently dropping OOV acronyms (HNPU / NPU)

### Context
We’re running Kitten-nano TTS on Qualcomm Hexagon NPU via QHexRT in the Android starter app. Live text uses on-device Kokoro/misaki g2p (`kokoro_lexicon.txt` + `kokoro_vocab.txt`), then feeds phoneme ids into Kitten.

Varlen Graph A (Lmax=128) and the affricate/diphthong g2p bridge are already working. This is a separate bug: some words are never spoken.

### Observed symptom
When speaking text that includes acronyms like `HNPU` or `NPU` (also `Qualcomm`), those tokens are silently skipped. Example:

> “Load one HNPU model, speak several prompts…”
> Heard as ≈ “Load one model, speak several prompts…”

This is easy to misread as a model / NPU synthesis failure. It is NOT the Kitten acoustic model dropping audio.

### Root cause (already confirmed)
In `QHexRT/QHexRT/src/hostop/kokoro_g2p.cpp`:

1. Words are lowercased and looked up in the lexicon (`lookup()`).
2. If OOV and morphology fallback fails, `lookup()` returns `""`.
3. Empty phoneme strings are dropped with no letter-spelling fallback:

```cpp
if (!p.empty()) { out += p; out += ' '; }
```

Lexicon checks on the v75 bundle (`kokoro_lexicon.txt`, ~178k entries):

| Token | Result |
|---|---|
| `hexagon`, `neural`, `processing`, `unit` | present |
| `hnpu`, `npu`, `qualcomm` | OOV → dropped |
| single letters `h`, `n`, `p`, `u` | present (letter pronunciations exist) |

So “Hexagon neural processing unit” works; “HNPU” / “NPU” do not.

Kitten path: `QHexRT/QHexRT/src/hostop/kitten_tts.cpp` → `kitten_g2p_ids()` → `KokoroG2P::phonemes()`.
`normalize_kitten_text()` is intentionally an identity (no app-side word rewrites); pronunciation is supposed to live in lexicon/g2p.

### Goal
Make OOV acronyms / short all-caps tokens speak instead of disappearing, without regressing normal English.

Preferred direction (pick best after inspecting code + misaki behavior):

1. Letter-spell fallback for OOV when a token looks like an acronym (e.g. all-caps / mostly consonants / short UNKNOWN token): expand `HNPU` → `H N P U` using existing single-letter lexicon entries.
2. And/or add high-value lexicon entries for product terms (`hnpu`, `npu`, maybe `qualcomm`) if letter-spelling sounds wrong.
3. Avoid hardcoding a long special-case list in `kitten_tts.cpp` if a general g2p OOV policy is cleaner.

### Constraints / do-nots
- Do NOT bring back the old place-name / glue heuristics that were deleted for varlen.
- Do NOT “fix” this by rewriting HNPU → a long phrase only in the starter app UI; fix belongs in g2p / lexicon so all Kitten (and Kokoro) callers benefit.
- Keep parity with Kitten’s StyleTTS2 vocab / affricate (`ʧ`→`tʃ`) and diphthong rewrite path in `kitten_g2p_ids`.
- Prefer a small, testable change with a unit/host test (`kitten_g2p_test` / `g2p_test` already exist under `QHexRT/QHexRT/tools/`).

### Acceptance checks
1. `phonemes("Load one HNPU model")` includes phones for HNPU (letter-spelled or lexicon), not a hole.
2. Same for `"NPU"` and a mixed sentence with normal words + acronym.
3. Normal words unchanged (e.g. Southampton / speech / Hexagon neural processing unit).
4. Optional: on-device Kitten speak of the starter suite phrase containing “HNPU” no longer skips it.

### Key files
- `QHexRT/QHexRT/src/hostop/kokoro_g2p.cpp` / `.h` — OOV drop site
- `QHexRT/QHexRT/src/hostop/kitten_tts.cpp` — `kitten_g2p_ids`, `normalize_kitten_text`
- Lexicon: `.../kitten-tts-nano/_bundle/v75/kokoro_lexicon.txt` (and publish/HF copies)
- Tools: `QHexRT/QHexRT/tools/kitten_g2p_test.cpp`, `g2p_test.cpp`
- App repro: `kotlin-starter-example/.../TextToSpeechScreen.kt` suite phrase with “HNPU”

### Please
Inspect the current g2p tokenize/lookup path, propose the smallest correct fix, implement it, and add a focused test proving `HNPU`/`NPU` are no longer dropped.
