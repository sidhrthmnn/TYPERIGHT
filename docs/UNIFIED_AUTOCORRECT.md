# Ranked multilingual autocorrection

`CandidateRanker` owns replacement decisions. `DictionaryManager`,
`GboardPredictionEngine`, `LocalGrammarSpellPredictor` and
`NeuralCorrectionEngine` supply vocabulary, touch evidence and contextual
proposals or delegate their compatibility APIs to the ranker. They no longer
select competing automatic replacements. `NGramLanguageModel` and
`PersonalTypingProfile` supply context and local feedback.

## Timing and behavior

| Trigger | Work | Editor behavior |
| --- | --- | --- |
| Keystroke | Cancel obsolete predictions; generate/rank candidates on `Dispatchers.Default` after a 20 ms debounce | Update suggestions only; no language model |
| Space/punctuation | Read a matching ranked cache entry, or commit literally and rank on a worker | HIGH replaces; MEDIUM offers a suggestion; LOW preserves text |
| Sentence/typing pause | Optional downloaded Qwen3 0.6B/1.7B, after 650 ms at a boundary or 1,200 ms within a word | Review a minimal edit; never silently replace a sentence |
| Explicit AI polish | Selected GGUF model; optionally configured cloud backend/fallback | Existing review/apply flow |

The boundary job captures the editor session, text before/after the cursor,
selection and tap coordinates. It applies only to that exact snapshot. New
typing, deletion or switching editors cancels it. A cancelled check does not
teach a curated typo as personal vocabulary. Backspace immediately after an
automatic change restores the exact original word, including its capitalization,
and records negative feedback for that source/replacement pair.

## Candidates, evidence and confidence

Candidate sources include the frequency corpus, a prefix-delete spelling index,
Damerau-Levenshtein distance, adjacent-key and actual touch likelihoods,
transpositions, insertions/deletions/repeats, phonetic matching, contextual
confusion sets, user vocabulary and explicitly accepted corrections. Prefix
completions can appear in the strip but cannot automatically complete a word
unless an explicit typo mapping or accepted correction supports it.

Each candidate exposes its edit, keyboard, frequency, context, personal,
accepted/rejected and language evidence. The ranker combines these features,
normalizes relative scores with softmax, and measures the gap to the next
candidate. This relative confidence is calculated, not a fixed value attached
to a typo rule. It is not a population-calibrated probability of correctness.

The automatic gate additionally requires clear typo/acceptance/touch evidence,
no rejection, and protection of recognized real words. Balanced uses posterior
0.99 and margin 0.40; mild uses 0.995 and strong 0.97. MEDIUM requires posterior
0.50 and margin 0.05. Thresholds were swept on a separate calibration partition;
0.99 retained all correct calibration proposals while keeping a conservative
gate. The sampled thresholds produced identical proposals on this small set,
so a real user study could justify different settings. Known real-word errors
such as sea/see and there/their are suggestions or optional sentence edits.

## Languages and personal vocabulary

The existing 46,693-word English corpus is joined by 59 attributed frequency
tables (466,543 source rows). Script, a word's dictionary membership and the
previous five words inform span-level language selection. Unknown words have
an unknown language instead of automatically becoming English errors.
Malayalam and Hindi transliteration receive explicit protection; for example,
`Njan tomorrow officil varilla` stays intact while `Njan tomorow officil varilla`
can correct its English typo. Native lookup uses Unicode case folding and NFC.

Names, optional locally read contacts, custom words, URLs, email addresses,
numbers, usernames, abbreviations, mixed-case identifiers and slang are
protected. Contact access is optional and contact names never go to a server.
Frequently used unknown words become trusted after three observations.
Explicitly accepted polish teaches only plausible small typo pairs, not arbitrary
rewrites. Existing real-word pairs need three acceptances in matching context.
Undo removes positive evidence and strongly suppresses that replacement;
an explicit later acceptance can reverse negative feedback.

The bounded profile keeps word counts, up to five-word transitions and correction
pairs, not complete messages. Writes are debounced off the typing thread.
Learning can be disabled or reset, and password/sensitive fields and Android's
no-personalized-learning flag prevent recording feedback. Temporary suppression
still permits undo without storing private-field input.

Sentence editing is off by default and requires a downloaded small model.
Qwen3 1.7B is recommended over 0.6B for contextual accuracy. The minimal-edit
validator requires the same word count, a small edit budget, intact separators,
names, personal words, numbers, slang and code switching. Outputs outside these
constraints are discarded. Sentence edits require recognized vocabulary and matching scripts; unknown
native words remain protected. Explicit multilingual AI polish remains available.
Transliteration/slang preservation is also checked after AI proofreading.

## Performance and evaluation

English/multilingual tables and spelling/phonetic indexes load in background.
The ranked cache is bounded to 96 entries; multilingual correction indexes keep
six hot languages. Edit distance reuses per-worker scratch storage. The native
runtime keeps one selected model mapped between requests, frees it when switching
models or removing its download, and creates a fresh inference context per request. No model is initialized
or evaluated during keystroke or boundary correction.

The authored fixture set in `app/src/test/resources/autocorrect-cases.json`
contains 237 cases, including 131 common English typos plus adjacent keys,
transpositions, missing/repeated letters, contextual real words, names, slang,
native scripts, Malayalam/English and Hindi/English spans. Calibration has 69
cases and holdout has 168. The tests prioritize false corrections over recall.
See [recorded metrics](autocorrect/metrics.json) for every decision and latency,
and [the undo trace](autocorrect/undo.json). The controlled one-correction/one-undo
trace has undo rate 1.0; it is not a measured production undo rate. Runtime counters
store only session aggregate applied/undone counts, without typed text.

These are regression fixtures and emulator measurements, not equivalence to
Gboard/SwiftKey or proof of equal quality in every supported language. Corpus
coverage, transliteration lists and contextual rules are finite. Physical-phone
latency, memory, battery/thermals and representative multilingual messages still
need field evaluation.

```sh
python scripts/build_multilingual_dictionaries.py
python scripts/build_multilingual_dictionaries.py --check-sources
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
adb shell am instrument -w -e class com.example.CandidatePipelineDeviceTest com.aistudio.typeright.jkwpzq.test/androidx.test.runner.AndroidJUnitRunner
```

CI runs unit tests and saves the generated calibration/holdout/latency/undo reports.
Model inference tests are opt-in and require installed weights; normal CI does
not download model files.
