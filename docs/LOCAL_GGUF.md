# On-device model library

Open **AI polish**, enable **On-device AI**, select a model, and tap its download
button. Download progress, cancel/retry, installed status and removal are shown
per model. Selection survives reopening the app. Existing explicit Gemma choices
are retained; new users default to GRMR for English editing. Local, optional cloud
and Off are available. Sentence correction is a separate opt-in control; Qwen3
1.7B is its recommended small multilingual model.

| Model | Download | Intended use | License |
| --- | --- | --- | --- |
| GRMR 1.5B Instruct Q4_K_M | 986 MB | English spelling, grammar, clarity and wording | Apache-2.0 |
| Gemma 3 1B Instruct Q4_K_M | 806 MB | Compact, primarily English general writing | Gemma Terms of Use |
| Qwen3 0.6B Q4_K_M | 484 MB | Experimental; limited grammar accuracy | Apache-2.0 |
| Qwen3 1.7B Q4_K_M | 1.28 GB | Recommended small multilingual sentence proofreading | Apache-2.0 |
| Qwen3 4B Q4_K_M | 2.50 GB | Larger multilingual AI polish | Apache-2.0 |
| Gemma 3n E2B Instruct Q4_K_M | 2.79 GB | Multilingual AI polish; effective size is not total parameter count | Gemma Terms of Use |
| Gemma 4 E2B Instruct QAT Q4_0 | 3.35 GB | Multilingual writing and tone transformations | Apache-2.0 |

[GRMR](https://huggingface.co/qingy2024/GRMR-1.5B-Instruct) is a Qwen2.5-based
language model fine-tuned for grammar correction and readability. Its documented
editing prompt is used with greedy decoding, concise meaning-preservation rules
and explicit original-number reminders after nearby context. It is the recommended English editing
model; multilingual requests should select an appropriate general model. Its
provider's benchmark claims are not an independent TypeRight quality evaluation.

**Add another GGUF model** accepts a direct HTTPS link, provider checksum and
prompt format (ChatML, Qwen3, Llama 3, Gemma 3/4, or GRMR). Choose an architecture supported
by the pinned llama.cpp runtime and a size that fits available phone memory.
The 8 GB download limit is not a memory compatibility guarantee. Custom models'
licenses, languages and editing quality depend on their providers; read their terms.
Custom downloads are stored on the phone, not automatically added to this repository.

## Downloads, storage and provenance

- `models/model-catalog.json` is the source of model URLs, pinned revisions,
  hashes, sizes, formatting and attribution. Licenses and NOTICE ship with the app.
- All seven catalog models are present in Git LFS. Gemma 4 has three tensor shards;
  Qwen3 4B and Gemma 3n each have two. All 11 files stay below 2 GB per file.
  Compatible llama.cpp tools load each first shard and locate its siblings.
- Android downloads the original single-file model. Installation requires HTTPS,
  GGUF magic, matching expected size and SHA-256, then an atomic rename. Partial
  or cancelled downloads are removed; successful installation keeps other models.
- Weights live in app-private `no_backup/gguf`, excluded from backups and the APK.
  Keep the page open while downloading, with model size plus at least 64 MB free.
  Removing a download frees storage; selecting another model needs no re-download.
- Gemma 3 and Gemma 3n require terms acceptance before download. GRMR, Qwen3 and Gemma 4
  use Apache-2.0 and do not inherit that consent requirement.
- llama.cpp v0.5.0 / b11146 is pinned at
  `d2e54583c7452353eb35d40431281f6ee984332f`. ARM64 and x86_64 libraries use NDK 28
  and 16 KB ELF alignment. The CPU runtime maps weights, uses up to four threads,
  a 2,048-token context and a 384-token output limit. It caches one model and frees
  each request's inference context. Switching or removing a model releases the
  cached mapping, reclaiming storage without restarting. Prefer short selections.

## Meaning-preserving polish

Each model gets its own prompt format. Requests identify the selected text and
include at most 300 preceding, 160 following and 160 previous-sentence characters
from the active editor as quoted read-only data. The keyboard cannot see messages
in another app's conversation that the editor does not expose. User turn/header
markers are escaped. Instructions preserve intent, natural voice, language, names,
negation, facts, questions, numbers, URLs and emojis and return replacement text only.

Output validation rejects changed numbers, dropped URLs/email addresses, a lost
question mark and lost/added English negation during proofreading. Valid model
output is no longer passed through a second heuristic rewrite. These checks reduce
common failures; they do not prove semantic equivalence. Review edits before use.

## Reproduce and verify

```sh
git lfs pull
python scripts/download_model.py --verify-repository
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The script defaults to downloading GRMR; `--model local-gemma-3-1b` or
`--model local-gemma-4-e2b` selects another original, and `--all` retrieves all
originals. `--verify-repository` checks all 11 committed weight files. Originals
above 2 GB are ignored by Git because the repository stores tensor shards.
Do not concatenate shards. For tools requiring a single file, use
`llama-gguf-split --merge` with the first shard and an output outside the repository.
Normal CI builds and unit tests do not download model weights.

`ModelChoiceTest` covers persistence, coexistence, consent, custom validation,
prompt boundaries, context and intent checks. `TypingBestPracticesTest` covers
ambiguous corrections, personal completions, sparse context and rapid input.
`ModelSettingsUiTest` checks picker recreation, consent and custom URL validation.
The optional `GgufModelDownloadDeviceTest` fetches real model weights when passed
`download_model_id`; `GgufInferenceTest` uses an installed model selected by `model_id`:

```sh
adb shell am instrument -w -e class com.example.GgufInferenceTest -e model_id local-grmr-1.5b com.aistudio.typeright.jkwpzq.test/androidx.test.runner.AndroidJUnitRunner
```

Verification for the previous model update: all five repository weight files passed size and
SHA-256 checks. The Android APK and test APK built for ARM64/x86_64, and all 119
unit tests passed. Actual Gemma 3, Gemma 4 and GRMR inference passed on an Android
16 x86_64 emulator with Wi-Fi and mobile data disabled. GRMR also corrected a
contextual reply to "I don't want to cancel the meeting at 5. Can we move it to
tomorrow?", preserving the refusal, time and question. An actual GRMR HTTPS download
through the Android installer passed checksum and coexistence checks while Gemma
3 remained installed. A network connection interruption affected an earlier attempt; partial
cleanup worked and retry succeeded. The custom HTTPS/checksum path also passed
an actual download, persisted the server-supplied size and kept existing models.

Physical-phone latency, thermals and battery consumption require device benchmarks.
Emulator inference does not establish Gboard-equivalent speed or quality.

## Optional cloud and sentence correction

AI polish offers Gemini with a user-provided API key and model ID. Cloud fallback
is off by default and must be explicitly enabled; a key alone never uploads text.
Explicit cloud polish sends the selected text and bounded editor context to Google.
Keystroke/boundary ranking and pause correction remain local. Keys use AES-GCM
with Android Keystore and are excluded from backups. Request/response handling
and key storage are tested; a live paid Gemini request was not exercised without
a configured key.

Sentence correction requires its own switch and downloaded Qwen3 0.6B or 1.7B.
The 1.7B model is recommended. Minimal edits are reviewed before acceptance and
cancelled by new typing. See [the unified pipeline](UNIFIED_AUTOCORRECT.md) and
[Android/model measurements](autocorrect/device-results.md) for this update.

All 11 repository weights and 59 multilingual tables passed size/checksum
validation. Added weights total approximately 7.05 GB; the full library is
approximately 12.2 GB. Weights are separate downloads from the APK.

## Predictive dictionary

The keyboard now includes **46,693 frequency-ranked English words**, adapted from Hermit Dave's [FrequencyWords English 2018 50k list](https://github.com/hermitdave/FrequencyWords). This is an open corpus implementation; Google's Gboard dictionary is not included.

The asset improves prefix completions, known-word recognition, fuzzy spelling candidates and unigram backoff for unknown contexts. Existing bigram/trigram/quadgram predictions, user vocabulary and sensitive-field protections remain active. Existing language and swipe engines remain in place.

A shared sorted vocabulary avoids building a large trie for each DictionaryManager. A shared symmetric-delete correction index is prepared in the background; five-character prefix keys and primitive word IDs bound its memory while full-word distance still validates corrections. Curated corrections work while that index warms up. Explicit typo corrections take precedence over misspellings found in the subtitle corpus; learned words and recognized slang remain protected. Unigram counts reference the shared corpus rather than copying it per keyboard. Frequency and personal-context ranking choose candidates, and the enabled profanity filter applies to suggestions.

The adapted dataset is **CC-BY-SA-4.0**. Its attribution, license, pinned source revision, source checksum, filtering and normalization recipe are in `app/src/main/assets/dictionaries/`. The script reproduces and checks the committed asset. It adds roughly 0.5 MB of uncompressed vocabulary data and needs no dictionary network request during typing.
