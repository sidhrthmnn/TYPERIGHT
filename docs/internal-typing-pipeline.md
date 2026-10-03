# Internal typing pipeline (192.0)

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
identifiers and explicit vocabulary receive protection. Dictionary, Gboard, grammar and system spellchecker entry points adapt this pipeline.
Gesture proposals also pass through this ranker after worker-side decoding.

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
  reports. There is no standalone APK build/upload job.

## English and Manglish vocabulary

`tools/import_manglish.py` imports the three pinned Malayalam romanization
lexicons from Google's [Dakshina v1.0](https://github.com/google-research-datasets/dakshina).
Raw licensed sources, SHA256 provenance, attribution and CC BY-SA 4.0 are retained.
The 66,439 Latin forms include 276 authored conversational spellings and productive
English-stem suffixes such as `officeil`, `meetinginu` and `projectinte`.
Attestation votes are separate from native subtitle frequency proxies. Valid
`nale/naale` and `sheri/shari` variants are protected; phonetic normalization only
retrieves proposals and never asserts equivalent meaning. A character 3/4-gram
model uses training families only. Unknown words retain explicit uncertainty.

English and Manglish rank together. Nearby English spans can still correct
`tomorow` and contextual `sea` while Manglish spans protect intentional loans and
variants. AI-polish and optional sentence validation share these span guards.
Latin mode stays Latin; script conversion is an explicit Malayalam setting, with
legacy values normalized. The authored, fictional conversational n-gram baseline
replaces a fixed list of Manglish predictions. Swipe decoding runs on a worker,
includes Manglish words, prunes by gesture geometry, and ranks using context and
personal evidence. Swipe casing stays lowercase unless caps lock is active.

Deletion indexes now use sorted primitive fingerprint/word-ID postings instead of
retaining a string/map/list object per deletion. Fingerprint collisions retrieve
extra proposals only; exact distance still validates every candidate. English and
Manglish lexical proposal caches are bounded; current touch, language, context and
personal scores are always recomputed on workers. Learned swipe templates are
bounded to 256, with reset generations and serialized writes. Legacy duplicate
Markov/touch engines and 91 unused methods were removed; Android callbacks, Room
migration entities and JNI entrypoints remain.

The ranker also reuses up to 2,048 invariant spelling/geometry comparisons and
prepares n-gram distributions once per request instead of once per candidate.
ASCII tokens avoid unnecessary Unicode composition/case folding. Startup swipe
buckets use constant-time word deduplication, and vocabulary updates reuse the
same initialized dictionary. Chromium publication is allowed up to 256 ms only
when the older snapshot matches an owned edit; replacements still validate the
entire range, selection and following text. The WebView instrumentation bridge
waits for a focused DOM text field and posts every edit to its connection handler.

## Training, calibration and limitations

The generic spelling prior is refined by pairwise FTRL using 2,690 actual runtime
candidate competitions (1,797 English, 893 Manglish). `tools/data/runtime-ranking-training.jsonl.gz`
records the exact 21-dimensional scoring vectors and alternative words. Unit tests
verify feature parity against runtime; the trainer consumes training families only.
Existing personal model slots and hash labels keep their meanings; new language,
variant and mixed-context features use unused slots/labels. Learning remains local
and invisible, and no full messages are retained.

Confidence gates are calibrated separately for English and Manglish. Run the unit
evaluations, `tools/calibrate_typing_ranker.py` and `tools/calibrate_bilingual.py`;
only calibration partitions are read. The frozen gates are loaded once from
`ranker-calibration.tsv`, not assigned per typo. `tools/verify_typing_assets.py`
checks source/asset hashes, disjoint variant families and conversational templates,
and training membership. CI reproduces vocabulary, language assets, ranker weights
and evaluation splits byte-for-byte.

The English benchmark contains 9,767 word-family-disjoint synthetic holdout errors,
with intended-word controls and independent closest-candidate labels. The additional
bilingual holdout contains 900 English and 905 Manglish synthetic errors, 1,376 valid
Manglish controls, and 103 next-word targets from disjoint authored templates. These
fixtures supplement the human-authored typo, name, slang, contextual, multilingual
and editing regressions; they do not establish natural-chat accuracy. Calibration
and training are disjoint from holdout; the frozen holdout is also a development
regression gate, not a hidden external evaluation. Overall recall deliberately
includes ambiguity; unambiguous English recall is reported separately.

The implemented methods follow published Gboard work on joint lexical/spatial/context
evidence, personal touch calibration and gesture search pruning:
[decoding architecture](https://aclanthology.org/2024.emnlp-industry.93/),
[personal spatial models](https://arxiv.org/abs/2209.11311), and
[on-device prediction](https://research.google/blog/the-machine-intelligence-behind-gboard/).
TypeRight uses its existing offline ranker/FTRL architecture; it does not reproduce
Google's private model, training corpus or measured product quality.

Current validation results are recorded in `tools/typing-validation.json`.
Device instrumentation generates temporary packages; no APK is delivered or
uploaded as a build artifact. The existing GGUF catalog and other app features remain.
