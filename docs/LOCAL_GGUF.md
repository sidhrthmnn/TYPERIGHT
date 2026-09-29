# On-device GGUF polish

Open **AI Polish**, select **Local GGUF · Qwen2.5 0.5B**, and tap **Download model (491 MB)**.
Keep the settings screen open during the download. Progress, cancellation and retry are available.
Once installed, polish works without an internet connection or Gemini key. The keyboard's AI
badge cycles **Cloud → Local → Off**. The selection persists across restarts.

Cloud mode uses the existing Gemini configuration. Off mode permits only the existing basic
offline corrections. Local mode never invokes Gemini, even when its model is missing or fails.
Explicit polish reports model errors; voice cleanup can retain basic deterministic corrections.
Voice recognition itself is a separate Android service and is not made offline by this switch.

## Model and runtime

- Model: [Qwen2.5-0.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF), Q4_K_M, Apache 2.0.
- Exact size: 491,400,032 bytes (about 469 MiB). The manifest in `models/qwen-polish.json`
  pins the publisher's revision, download URL and SHA-256. The license is adjacent to it.
- Runtime: [llama.cpp](https://github.com/ggml-org/llama.cpp/tree/74d4f5b041ad837153b0e90fc864b8290e01d8d5),
  pinned at b5046 with an archive checksum. CMake builds a JNI library for arm64-v8a phones
  and x86_64 emulators. NDK 28 and 16 KB ELF alignment are configured.
- CPU inference: up to four threads, 2,048-token context, 384 output tokens, greedy sampling.
  Oversized input and incomplete output fail without replacing the original text. Cancellation
  is checked between prompt batches and generated tokens; a single decode cannot be interrupted.
  Generation has a 120-second deadline. Models/contexts are released after each request, and
  requests are serialized to avoid multiple models occupying memory.
- Prefer short selections and devices with at least 4 GB RAM. Actual latency, available memory
  and rewrite quality vary; a 0.5B model is less capable than the cloud model.
- Downloads use HTTPS, validate exact size and SHA-256, and are atomically installed in
  app-private `no_backup/gguf`. Partial downloads are deleted on cancellation/error. The model
  is excluded from Android backup. Uninstalling the app removes it.

## Reproduce the local download

```sh
python scripts/download_model.py
```

This downloads and verifies the same model into `models/`. The weights are tracked with Git LFS and excluded from APK assets; they exceed GitHub's normal 100 MB file limit. Run `git lfs pull` after cloning to fetch them, or use the verified downloader above. APKs include the native runtime, manifest and license; the app's download button installs the weights on the phone.

## Build and verify

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

The first native build downloads the pinned llama.cpp source. Standard CI does not download
model weights. `GgufBackendTest` checks persistent selection, legacy preferences, missing-model
behavior, off mode and prompt boundaries. `GgufInferenceTest` runs real inference on Android
when the model is installed, otherwise it is skipped. Install the model through the settings
screen before running:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.GgufInferenceTest
```

For an offline smoke check, download first, enable airplane mode, then run the test or polish
`i has a meeting at 5` in the playground. Confirm the output corrects the grammar and keeps `5`.
Also check switching back to cloud, missing-model guidance, cancellation, and changing editor
text during generation. Never apply a result to an editor snapshot that has changed.
