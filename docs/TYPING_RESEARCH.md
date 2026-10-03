# Typing research and implemented changes

This update keeps automatic typing assistance local and separates fast keystroke
correction from deliberate language-model polishing.

## Evidence and decisions

[Google's description of Gboard](https://research.google/blog/the-machine-intelligence-behind-gboard/)
describes combining spatial evidence, language context and word frequency, and
using rejection feedback to avoid repeated unwanted corrections. TypeRight retains
its existing touch evidence and undo/learning path. The unified ranker requires
calculated posterior confidence and a score margin (see [calibration](UNIFIED_AUTOCORRECT.md)), and treats Android spell-checker
results as suggestions rather than unconditional space-triggered replacements.
Short, ambiguous tokens and personal vocabulary receive conservative handling.
Personal prefix completions remain tap choices; explicitly configured shortcuts
retain their intentional expansion behavior.

[Google's language-model research](https://research.google/blog/improving-gboard-language-models-via-private-federated-analytics/)
shows the importance of representative context and vocabulary. TypeRight uses its
open, attributed English frequency corpus and local learned words. It does not
contain Gboard's proprietary dictionary or reproduce its federated training.
Next-word ranking interpolates unigram through six-gram evidence (five prior words) according
to observed counts: a context with count N receives weight N / (N + 12). Sparse
high-order matches back off; frequently repeated personal context can dominate.
Corpus unigram frequencies are kept distinct from small curated startup counts.

[Android's IME guidance](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
emphasizes responsiveness, loading expensive data on demand and careful handling
of sensitive input. Prediction observation uses `distinctUntilChanged` and
`collectLatest`, cancelling obsolete debounced requests while typing quickly.
The existing sensitive-field learning restrictions, background correction index
and cached boundary correction remain in place, with worker ranking on a cache
miss. GGUF inference is outside per-keystroke prediction: optional small-model
sentence review follows a pause, and larger models serve explicit polishing.

[Google's grammar-correction work](https://research.google/blog/grammar-correction-as-you-type-on-pixel-6/)
demonstrates task-specific correction models rather than spelling-only substitution.
TypeRight recommends [GRMR](https://huggingface.co/qingy2024/GRMR-1.5B-Instruct), an
English editing fine-tune, while keeping general models selectable. Model-specific
prompts use bounded, read-only editor context and preserve meaning. Numbers,
URLs, email addresses, questions and proofreading negation are checked after
inference. Output is not rewritten again by heuristic typo rules.

These sources inform design choices. Relative-confidence thresholds were swept
on an authored calibration partition and checked on holdout fixtures. They and
the interpolation constant have not been validated in a TypeRight user study.

## Regression coverage and limits

The tests include ambiguous misspellings, recognized personal words and prefix
completions, sparse versus repeated high-order contexts, previously unknown learned
words, rapid request cancellation and retention of numbers/questions/refusals.
Model tests also cover consent, independent installation state, prompt escaping,
custom URLs/checksums and saved selections.

This is not a measured equivalence claim to Gboard. Future evaluation should use
representative mobile devices and language-specific typo/message sets, measuring
false corrections, accepted suggestions, typing latency and model memory/energy.
GRMR's training is English; select a multilingual model for other languages.
