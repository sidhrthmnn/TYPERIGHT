# Gemma 4 on-device polish

Open **AI polish**, enable **On-device AI**, and choose **Download model · 3.35 GB**.
Allow 3.5 GB of free storage and keep the screen open during download. The verified
model runs offline on a 64-bit Android device. Local and Off remain the available
engines; the cloud removal on main is preserved.

## Model and runtime

- **Gemma 4 E2B Instruct QAT Q4_0**, Google's smaller mobile/edge model variant:
  the official GGUF is 3,349,516,256 bytes. This is the GGUF Q4_0 version, which
  differs from Google's smaller LiteRT mobile formats. It runs with the project's
  native GGUF backend; it is not a LiteRT or AICore model.
- [Official weights](https://huggingface.co/google/gemma-4-E2B-it-qat-q4_0-gguf),
  pinned to revision `675cff42a74c774d6cb76f76d8eacb49b48c9b93`. The manifest
  records the original URL, size, SHA-256, and repository shard checksums.
- The actual model weights are stored as three tensor-preserving GGUF shards in
  Git LFS, each below 2 GB. Run `git lfs pull` and
  `python scripts/download_model.py --verify-repository` after cloning. Compatible
  llama.cpp tools can load `models/gemma-4-E2B-it-Q4_0-00001-of-00003.gguf` directly,
  automatically finding the other two shards in that folder.
- Android downloads the original single-file GGUF from the pinned Google source
  and verifies its size and SHA-256 before atomic installation in app-private
  `no_backup/gguf`. Model weights are excluded from the APK and device backups.
  Successful installation removes the old app-private Gemma 3 and Qwen files.
- Gemma 4 is [Apache-2.0 licensed](https://developers.google.com/edge/litert-lm/models/gemma-4).
  The license and NOTICE are included in the project and APK. The obsolete Gemma 3
  consent gate is not applied to this model.
- llama.cpp **v0.5.0 / b11146** is pinned to
  `d2e54583c7452353eb35d40431281f6ee984332f` and a verified archive checksum.
  NDK 28 builds ARM64 and x86_64 libraries with 16 KB ELF alignment.
- The runtime memory-maps weights and reads Gemma 4 per-layer embedding rows on
  demand. CPU execution uses up to four threads, a 2,048-token context, and a
  384-token output limit. Serialized requests release the model/context afterward.
- Gemma 4 system/user/model turn markers replace Gemma 3 formatting. Thinking is
  disabled for short replacement edits, and user-supplied reserved markers are
  escaped. Existing output validation and preamble filtering remain active.
- Prefer short selections. Available memory, processor speed, and the Android
  speech service affect device behavior; physical-phone performance is not
  inferred from emulator or desktop checks. AI polish runs offline, while system
  dictation availability depends on the installed speech recognizer and language packs.

## Reproduce and verify

```sh
git lfs pull
python scripts/download_model.py --verify-repository
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

`python scripts/download_model.py` separately retrieves and verifies the original
single-file GGUF for development. It is ignored by Git because the repository
already contains the same weights as shards. CI needs no model download to build
or run unit tests.

`GgufInferenceTest` runs actual inference when the downloaded model is installed,
otherwise it is skipped:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.GgufInferenceTest
```

Verification for this upgrade: all three repository shards passed size and
SHA-256 checks, and the original model and repository shards both produced a
corrected sentence with the pinned desktop runtime. ARM64 and x86_64 Android
libraries built successfully. The full unit suite passed 105 tests, and six
settings/voice toolbar Android UI tests passed. With networking disabled on an
Android 16 x86_64 emulator, the actual app-native `GgufInferenceTest` passed
without skipping, using the checksum-verified original GGUF. It corrected
"i has a meeting at 5", retained the number, and passed the app's output validator.

## Predictive dictionary

The keyboard now includes **46,693 frequency-ranked English words**, adapted from Hermit Dave's [FrequencyWords English 2018 50k list](https://github.com/hermitdave/FrequencyWords). This is an open corpus implementation; Google's Gboard dictionary is not included.

The asset improves prefix completions, known-word recognition, fuzzy spelling candidates and unigram backoff for unknown contexts. Existing bigram/trigram/quadgram predictions, user vocabulary and sensitive-field protections remain active. Existing language and swipe engines remain in place.

A shared sorted vocabulary avoids building a large trie for each DictionaryManager. A shared symmetric-delete correction index is prepared in the background; five-character prefix keys and primitive word IDs bound its memory while full-word distance still validates corrections. Curated corrections work while that index warms up. Explicit typo corrections take precedence over misspellings found in the subtitle corpus; learned words and recognized slang remain protected. Unigram counts reference the shared corpus rather than copying it per keyboard. Frequency and personal-context ranking choose candidates, and the enabled profanity filter applies to suggestions.

The adapted dataset is **CC-BY-SA-4.0**. Its attribution, license, pinned source revision, source checksum, filtering and normalization recipe are in `app/src/main/assets/dictionaries/`. The script reproduces and checks the committed asset. It adds roughly 0.5 MB of uncompressed vocabulary data and needs no dictionary network request during typing.
