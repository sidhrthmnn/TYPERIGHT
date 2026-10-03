# English and Manglish verification (192.0)

The implementation uses the shared ranker, offline language/context evidence and
bounded on-device FTRL learning. Normal typing needs no downloaded model. The
existing AI-polish model choices and learning settings remain available. There
is no learning dashboard or correction dialog.

174 unit tests and 18 Android device checks passed. The editor checks exercise
cold prediction caches, rapid consecutive English/Manglish corrections, exact
undo and following composition in native EditText, Compose BasicTextField and
WebView textarea. A separate cold-start editor run also passed before vocabulary
loading completed; pending corrections survived startup. The WebView harness
waits for a focused DOM field and dispatches operations on Chromium's handler.

| Emulator measurement | p95 |
| --- | ---: |
| Warm worker ranking, current context/personal scores | 13.66 ms |
| IME callbacks excluding delegated editor work | 1.57 ms |
| Native editor-inclusive callbacks | 8.11 ms |

Measurements use the existing Pixel_9 Android 16 x86_64 emulator: 240 warm
rankings and 150 actual IME callbacks. The worker and IME targets remain 15 ms
and 2 ms. Initial runs exposed an Android heap overflow, repeated scoring work
and delayed editor publication. Primitive deletion postings, invariant spelling
caching, one prepared context distribution per request, constant-time swipe
deduplication and validated publication waits resolved those regressions. Host
contention can exceed the warm target; these timings do not guarantee performance
on physical phones. Startup asset-loading latency is separate from warm ranking.

| Frozen development holdout | Result |
| --- | ---: |
| Independent English automatic precision | 99.64% |
| Unambiguous English automatic recall | 99.90% |
| English correct-text false corrections | 0% |
| Manglish typo automatic precision | 99.58% |
| Manglish typo recall | 79.34% (previous main: 0.88%) |
| Valid Manglish controls changed | 0 / 1,376 |
| Mixed next-word top-three matches | 80 / 103 (previous main: 9 / 103) |

The independent English holdout contains 9,767 generated errors and corresponding
correct-text controls. Its unambiguous subset contains only errors with one
closest canonical spelling. The additional bilingual set has 900 English and
905 Manglish errors; its overall English recall is 78.89%, including ambiguous
errors. The 237 authored cases include 131 English typos, names, slang, multilingual
and contextual checks (100% automatic precision; 89.47% overall recall).

Training, calibration and holdout families/templates are disjoint. The runtime
feature export is checked against current candidate competition. Reproduction
and source/asset SHA256 checks passed. The holdout is a development regression
gate, not a hidden external evaluation or a measurement of natural user messages.
The undo rate is a deterministic regression trace, not observed user behavior.

91 confirmed unused methods and two unreachable correction-engine classes were
removed. Android callbacks, Room migration entities and native entry points remain.
The [validation record](../../tools/typing-validation.json) contains exact figures;
`tools/record_typing_validation.py` refuses to record failing suites or missing
IME latency evidence. [Pipeline and reproduction details](../internal-typing-pipeline.md)
include licensed data sources and the published Gboard research used for the design.

Only unit checks and temporary device instrumentation packages were built. No
standalone APK is delivered or uploaded by CI. GitHub CI reproduces the assets and
runs the unit suite; existing model inference results remain in the archived report.
