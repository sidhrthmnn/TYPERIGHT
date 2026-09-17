# Keyboard review — 17 September 2026

## Scope and outcome

Repository-wide inventory and targeted review of input handling, candidate generation,
dictionary learning, language-model ranking, proofreading, voice hand-off, settings,
clipboard capture, system spell checking, and build configuration. This is not a
claim that every line or every device behavior has been verified.

The focus is reliable, responsive typing inspired by familiar mobile keyboards.
TypeRight does not embed Gboard or Apple's proprietary prediction models, and
equivalent accuracy or latency has not been established.

## Implemented fixes

- One bounded local decoder for keyboard and system spell checker; removed sentence
  proofreading and repeated fuzzy searches from per-keystroke candidate generation.
- Curated immediate typo corrections, conservative statistical correction, separate
  prefix completion, valid-word/code/name protection, and immediate undo suppression.
- Best next-word candidate in the center; live personalized n-gram model instead of
  a separate stale copy; removed probability floors that flattened candidate scores.
- Cancellable, snapshot-bound predictions; no dictionary search during Compose rendering.
- Bounded SymSpell input and duplicate deletion elimination; synchronized top-K trie
  lookup; atomic language-model count updates.
- Password and no-suggestion fields do not reach the predictor. Passwords, URL/email
  fields and editors requesting no personalized learning do not train personal models.
- Apostrophes and combining marks stay in composition. Punctuation no longer adds
  spaces inside URLs/decimals. Double-space period respects its setting and timing.
- Backspace handles selections and Unicode graphemes; undo verifies the corrected
  suffix; inserting a next-word suggestion no longer deletes the following word.
- Proofread/assistant results apply only to their captured editor and unchanged text.
  Typing cancels outstanding service polish requests. Background failures do not kill
  the prediction stream, and inference cancellation is propagated.
- AI diagnostics redact text, sensitive clipboard items are not automatically captured,
  and disabled clipboard history is respected.
- Modern theme defaults are consistent across preference readers; the light theme
  no longer selects the retro style. Tools include an accessible draggable height box.
- Fixed missing AI-engine enum branches that prevented compilation, restored standard
  debug signing, added a checksum-pinned Gradle wrapper and GitHub build/test workflow.

## Reproduce validation

Use JDK 21, Android SDK Platform 36.1, and the checked-in wrapper:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows use `gradlew.bat`. Missing `google-services.json` produces a warning;
local keyboard tests and the debug build do not need production cloud credentials.

Regression tests cover candidate ordering, common typos, case, suppression, valid
words, long/code tokens, sensitive fields, model personalization, punctuation,
grapheme deletion, real InputConnection edits, stale proofreading, and spell-check
request identity/offsets. Existing inference tests are retained.

`lintDebug` was attempted but crashed in Android Lint's Kotlin FIR/UAST resolver while
analyzing `DictionaryManager.kt` (`KotlinIllegalArgumentExceptionWithAttachments`).
Lint is not passing or silently suppressed; this toolchain issue remains open.

## Remaining release work

- Physical-device latency/frame-time measurements, cold-start profiling, and keyboard
  editor compatibility (messaging apps, browsers, password managers, landscape,
  accessibility, multilingual input and long-press/swipe behavior).
- Corpus-based top-three prediction recall and false-autocorrection evaluation. The
  local language model is relatively small; larger models need quality/size/privacy
  trade-off evaluation rather than a claim of Gboard parity.
- Voice recognition and cloud polish integration need live-provider/device testing.
  Legacy class/feature names mentioning neural/TFLite/AICore are not evidence that
  a downloadable trained model is present or hardware inference was exercised.
- Database schema migrations still use destructive fallback; backup/clipboard retention
  policy and production signing require a separate release-hardening pass.
- The resize box adjusts height, not floating placement or free two-axis resizing.
  Existing explicit theme choices are preserved; this is not a complete visual redesign.

No cloud model keys, signing credentials, generated APKs, or user text are committed.
