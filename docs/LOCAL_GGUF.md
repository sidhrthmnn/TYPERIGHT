# Gemma 3 on-device polish

Open AI Polish settings, select Local On-Device AI, review and accept the Gemma terms, and tap **Download model (806 MB)**. Keep the screen open during download. After the verified download, text polish runs offline on a 64-bit Android device. The current app supports Local and Off; existing local-only routing remains in place.

## Model and runtime

- **Gemma 3 1B Instruct Q4_K_M**: 806,058,240 bytes, published as GGUF by [ggml-org](https://huggingface.co/ggml-org/gemma-3-1b-it-GGUF), derived from Google DeepMind's Gemma 3 1B Instruct. The 1B text model is the mobile-size Gemma 3 variant. Larger 4B/12B/27B weights are not bundled.
- `models/gemma-polish.json` pins the revision, URL, exact size, and SHA-256. The actual weights are committed through Git LFS. `git lfs pull` retrieves them after cloning.
- Gemma is subject to the [Gemma Terms of Use](https://ai.google.dev/gemma/terms), including the [Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy). Copies of the terms, policy and required NOTICE are distributed in models/ and APK assets. The setup checkbox records acceptance before downloading or running Gemma.
- JNI uses llama.cpp **b5165**, pinned to `1d735c0b4fa0551c51c2f4ac888dd9a01f447985` with an archive checksum, which supports Gemma 3. Android NDK 28 builds ARM64 and x86_64 libraries with 16 KB ELF alignment.
- Prompt formatting uses Gemma's `user`/`model` turns and a single automatically inserted BOS token. User-supplied reserved markers cannot close the template. The model is instructed to edit text, preserve questions and commands, and return only the edit.
- CPU execution uses up to four threads, a 2,048-token context and 384-token output limit. The model/context is released after each serialized request. Truncated outputs and unsafe edits are rejected. Cancellation is checked between prompt batches and tokens; a single decode cannot be interrupted. The generation deadline is 180 seconds.
- Prefer short selections and phones with at least 4 GB RAM; speed and available memory vary. Physical-phone performance has not been benchmarked. Text generation is offline; the separate Android speech-recognition service is not made offline by this setting.
- Downloads use HTTPS and exact size/SHA-256 checks before atomic installation in app-private `no_backup/gguf`. The model is excluded from backup. After successful Gemma installation, the obsolete app-private Qwen weight file is removed. Qwen files remain recoverable in Git history but are removed from the current branch.

## Reproduce and verify

```sh
python scripts/download_model.py
python scripts/build_dictionary.py
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The APK includes the runtime, licenses and model manifest, and excludes the large GGUF weights. The model is downloaded once on the device. CI does not need to download the weights to compile the app or run unit tests.

`GgufInferenceTest` runs actual inference when the model is installed, otherwise it is skipped. For a real smoke check, install/download Gemma first, disable networking, then run:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.GgufInferenceTest
```

Try `i has a meeting at 5` and verify that grammar improves while `5` is retained. Also verify missing-model guidance, terms acceptance, cancellation, and changing editor text during generation.

## Predictive dictionary

The keyboard now includes **46,693 frequency-ranked English words**, adapted from Hermit Dave's [FrequencyWords English 2018 50k list](https://github.com/hermitdave/FrequencyWords). This is an open corpus implementation; Google's Gboard dictionary is not included.

The asset improves prefix completions, known-word recognition, fuzzy spelling candidates and unigram backoff for unknown contexts. Existing bigram/trigram/quadgram predictions, user vocabulary and sensitive-field protections remain active. Existing language and swipe engines remain in place.

A shared sorted vocabulary avoids building a large trie for each DictionaryManager. A shared symmetric-delete correction index is prepared in the background; five-character prefix keys and primitive word IDs bound its memory while full-word distance still validates corrections. Curated corrections work while that index warms up. Explicit typo corrections take precedence over misspellings found in the subtitle corpus; learned words and recognized slang remain protected. Unigram counts reference the shared corpus rather than copying it per keyboard. Frequency and personal-context ranking choose candidates, and the enabled profanity filter applies to suggestions.

The adapted dataset is **CC-BY-SA-4.0**. Its attribution, license, pinned source revision, source checksum, filtering and normalization recipe are in `app/src/main/assets/dictionaries/`. The script reproduces and checks the committed asset. It adds roughly 0.5 MB of uncompressed vocabulary data and needs no dictionary network request during typing.

## Device smoke verification

The actual pinned Gemma weights passed `GgufInferenceTest` on an Android 36 x86_64 Pixel 9 emulator with airplane mode enabled, Wi-Fi/mobile data disabled and no network route. Grammar correction preserved the number in `i has a meeting at 5`; the test completed in 14.1 seconds. This is an emulator smoke check, not a physical-phone speed benchmark.

`FrequencyDictionaryDeviceTest` built the entire compact index under Android's app heap and checked vocabulary completion, a long-word typo, and `helo` → `hello` in 6.7 seconds. Both ARM64 and x86_64 runtime libraries, the debug APK and instrumentation APK build successfully. The APK was checked to contain the pinned manifest, all model licenses/notices and the dictionary, while excluding GGUF weights.

The complete local unit suite passes: 92 tests, zero failures/errors. Regression coverage includes local-only routing, terms acceptance, Gemma prompt boundaries, corpus prefix/ranking, known typos, long-word corrections, context predictions, and keyboard editing.
