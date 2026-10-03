# Verification on 3 October 2026

## Build and regression fixtures

`testDebugUnitTest`, `assembleDebug` and `assembleDebugAndroidTest` succeeded:
154 unit tests, zero failures/errors. The 237 authored correction fixtures
include 131 English typos and a separate 69-case calibration/168-case holdout
partition. [metrics.json](metrics.json) records every ranked decision.

| Metric | Result |
| --- | ---: |
| Automatic correction precision | 100% |
| False automatic correction rate | 0% |
| Automatic correction recall | 69.92% |
| Holdout automatic recall | 70.79% |
| JVM worker ranking p50 / p95 | 1.32 / 7.17 ms |
| JVM cached lookup p95 | 0.025 ms |

These rates apply to the fixture set, not arbitrary real messages. Conservative
protection and confidence gates deliberately reduce automatic recall. The
[undo trace](undo.json) measures one applied change followed by one undo
(rate 1.0); production undo behavior has not been measured.

## Android 16 emulator

Pixel 9 x86_64 emulator, six GB configured RAM, CPU native inference. Sixteen
distinct Android tests passed across targeted runs: app settings (5), model
settings/download choices (3), clipboard (3), voice toolbar (2), ranked
pipeline/credential encryption (2) and dictionary loading (1).

The clipboard integration run requires TypeRight to be the selected IME because
Android restricts clipboard reads by background apps. The original selected
IME was restored after verification.

Warm worker ranking, 550 word checks including English errors, Malayalam/English
spans, Devanagari, names and slang:

| Metric | Final result |
| --- | ---: |
| Background ranking p50 | 1.303 ms |
| Background ranking p95 | 14.298 ms |
| Matching cached lookup p95 | 0.079 ms |

The first run exceeded the 50 ms worker target (65.925 ms p95). Reusing flat
distance matrices and caching the immutable Latin key costs reduced that result
without weakening the test threshold. Cached lookup never runs the scorer or a
language model on the IME thread.

## Real GGUF inference

Verified full model downloads were installed one at a time in app-private
storage. Tests used real native inference; no cloud key was configured.

| Model | Checks |
| --- | --- |
| Qwen3 1.7B Q4_K_M | Agreement, sea/see, preserving refusal/time/question/tomorrow and cached-model removal passed (37.346 s). |
| Qwen3 4B Q4_K_M | Agreement, sea/see and intent checks passed (126.302 s for the three prompts). Cached-model removal also freed its storage. |
| Gemma 3n E2B Q4_K_M | Agreement, sea/see and cached-model removal passed (88.743 s). The longer intent prompt produced an edit rejected by the output validator, so its extended quality check failed. |
| Gemma 4 E2B Q4_0 | Agreement, sea/see, intent and cached-model removal passed (61.966 s). |
| Qwen3 0.6B Q4_K_M | Native loading/inference worked, but agreement and sea/see quality checks failed. Keep it experimental; 1.7B is the recommended small sentence model. |

Gemma 3n repository tensor shards also loaded together in the desktop native
runtime. All eleven repository GGUF files passed catalog byte-count/SHA-256
verification. All 59 adapted language dictionaries passed local checksums;
upstream source checksums and adaptation recipes were verified separately.

Tests preserve names, numbers, slang and transliteration through validators and
require review of optional sentence edits. A rejected model output leaves the
editor unchanged. Larger models are available for explicit AI polish, not
keystroke correction. Existing downloaded emulator models were restored after
testing.

Physical-phone latency, thermal/battery behavior and multilingual model accuracy
need field evaluation. The opt-in cloud request/response contract and Android
Keystore storage were tested; a live cloud call was not tested without a user
API key. These results do not establish equivalence to Gboard or SwiftKey.
