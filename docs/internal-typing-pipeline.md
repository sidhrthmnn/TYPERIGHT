# Internal typing pipeline (191.0)

Normal typing requires no GGUF download or inference. The existing AI-polish model
catalog, cloud configuration, settings, keyboard appearance and other app features
remain available. The learner has no new user interface; the existing learning
switch and reset control govern it.

`TypingCoordinator` tracks owned editor changes and serial completed-word ranges.
Append-only typing preserves pending checks. Workers rank completed words even on
a cache miss. Before editing, the coordinator validates the editor, epoch, range,
selection and following text, finishes composition, changes only the validated
suffix, and restores the active composing word. Compose can publish queued edits
on a later frame. Unsafe selection/editor changes cancel work without teaching the
cancelled spelling. The exact original word is available to immediate backspace.
Delayed acknowledgements of owned cursor changes preserve pending checks; a real
cursor move after those acknowledgements cancels them. Available following text
contributes to contextual ranking and remains unchanged during replacement.

`CandidateRanker` owns correction, prefix and next-word scoring. Prefix completions
are suggestions. Word/span language evidence, canonical spelling, distance,
transpositions, key proximity, nullable touches, frequency, context, personal
usage, accepted/rejected choices and recency contribute to ranking. Real words
require strong span evidence; ambiguous unknown spellings remain reviewable.
Malayalam/Hindi transliteration, native script words, names, contacts, slang,
identifiers and explicit vocabulary receive protection. Dictionary, Gboard,
grammar and system spellchecker entry points adapt this pipeline.

Startup workers load canonical SCOWL spelling and frequency indexes. Vocabulary,
context, profile and ML scoring snapshots are immutable. Typing-time predictions
perform no Room queries; cache identity includes task, language, layout and
vocabulary/context/profile/model versions. Optional sentence/pause GGUF review is
separate, as is explicit AI polish.
An additional bounded lexical proposal cache avoids repeating dictionary edit
searches across different touch samples. Vocabulary revisions invalidate it;
context and personal ML scores are still calculated from current snapshots.

The personal residual model uses pairwise FTRL-Proximal with 4,096 hashed features.
Strong explicit choices and accepted polish, negative undo/reversal feedback and
weak retained corrections update it off the IME thread. Ignoring a suggestion is
not rejection. Confirmed words train intended-key offsets and variance only from
real nullable samples with matching layout and unambiguous alignment. Legacy
pressed-key calibration is discarded. Writes are batched; the model lives in
`noBackupFilesDir`. Vocabulary and context counts are bounded, without message
archives. Passwords, URL/email fields and Android no-personalized-learning inputs
cannot train it. Backup rules exclude adaptive preferences and the legacy Room
database. Reset clears adaptive stores and caches, preserving explicit entries.

Algorithm reference: [McMahan et al., Ad Click Prediction: a View from the Trenches](https://research.google/pubs/ad-click-prediction-a-view-from-the-trenches/).
Editor contract: [Android InputConnection](https://developer.android.com/reference/android/view/inputmethod/InputConnection).

## Reproduction and evaluation

- `python tools/train_typing_ranker.py` reproduces baseline weights and 48,906
  synthetic errors from canonical corpus words without reading preset typo maps.
  SHA256 of the intended word creates disjoint training (29,272), calibration
  (9,867) and holdout (9,767) partitions. Synthetic errors supplement the authored
  multilingual, slang, name, contextual and editing fixtures; these are not
  claims of real-world Gboard equivalence.
- `./gradlew testDebugUnitTest` evaluates all holdout errors, intended-word controls,
  required misses, feedback persistence/reset and actual handler editing sequences.
  Reports are under `app/build/reports/autocorrect/`.
- `python tools/calibrate_typing_ranker.py app/build/reports/autocorrect/independent.json`
  sweeps probabilities using calibration scores only. The frozen selection is
  `tools/typing-calibration.json`; subsequent holdout tests enforce 99.5% automatic
  precision, 90% recall on unique closest spellings and <=0.1% correct-text changes.
  Overall recall is more conservative on ambiguous cases.
- `EditingInputConnectionDeviceTest` exercises TypeRight callbacks with real
  native, Compose and WebView input connections, cold prediction caches, rapid
  consecutive corrections and exact undo. It measures warm uncached worker ranking
  separately from IME callback work. Native editor processing executes inline in
  this harness; the report includes both delegated-editor end-to-end latency and
  IME-exclusive latency. The 2 ms target applies to IME-exclusive handling, not
  another app's rendering/IPC work. Device timings are emulator observations, not
  a guarantee for every physical phone.
- CI reproduces training artifacts, runs unit checks and uploads the versioned
  installable debug APK plus reports.

## Observed validation, 2026-10-03

`tools/typing-validation.json` records the local APK checksum and final results.
All 166 unit and 19 Android device checks passed. Native, Compose and WebView
fixtures preserve rapid input and exact undo. The Pixel_9 Android 16 x86_64
emulator measured warm worker p95 9.05 ms, IME-exclusive p95 0.63 ms and delegated
native-editor end-to-end p95 9.55 ms.

The 9,767 synthetic holdout errors achieved 99.765% automatic precision and 99.959%
automatic recall on 7,370 unambiguous cases. Correct-text holdout controls had zero
changes. Overall holdout recall, including ambiguity, was 78.13%; ambiguous cases
remain suggestions. The 237 authored cases include 131 English typos, 74 protected
controls and Malayalam/Hindi code switching. All protected controls stayed literal.
The four required misses (`finaly`, `libary`, `buisness`, `differnt`) autocorrect.
