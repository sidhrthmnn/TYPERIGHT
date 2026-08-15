# FlowKey AI Keyboard Memory & Architecture Notes

## Overview
FlowKey is a flagship Gboard-equivalent Android Input Method Editor (IME) built with Jetpack Compose, Kotlin, standard Android `InputMethodService`, Room, WisprFlow-style continuous Voice Dictation, and Gemma 2B Local On-Device Model integration.

## Core Classes & Architecture
- **`LocalInferenceEngine.kt` & `LocalAiModelManager.kt`**: Local Edge AI inference engine running on-device Gemma 2B (`gemma-2-2b`) and Llama 3.2 models fully offline without cloud API dependencies. Executes rewrite, summarize, tone adjustment (Professional, Casual, Friendly, Witty), shorten, and expand operations on selected input buffer text.
- **`VoiceRecordingSttService.kt`**: Audio recording & STT service managing PCM audio stream, SpeechRecognizer, and feeding real-time audio/text into WhisperCppBrain for WisprFlow-style continuous transcription and speech repair.
- **`MicrophonePermissionHelper.kt`**: Helper class for checking, requesting, and managing runtime `RECORD_AUDIO` permissions for WisprFlow voice dictation and real-time audio visualization.
- **`KeyboardService.kt`**: Core abstract base class extending `InputMethodService`. Manages soft keyboard window visibility, input connection lifecycle, system IME switching, tactile haptics, and permission verification.
- **`TypeRightKeyboardService.kt`**: Main IME service implementation. Provides QWERTY/Numbers/Symbols/Emoji views, gesture swipe-to-type, candidates bar, auto-correction, WisprFlow voice dictation, Gemma 2B Local Polish assistant sheet (handling selected text buffer rewrites/summaries/tone fixes), and clipboard manager.
- **`DictionaryManager.kt`**: High-performance Trie-based dictionary engine (`TrieDictionary`) supporting O(k) prefix lookups, Levenshtein fuzzy autocorrect, spatial QWERTY proximity maps, bigram prediction, and swipe path decoding (`decodeSwipePath`).
- **`MainActivity.kt`**: FlowKey Setup & Companion App featuring interactive 3-step setup checklist, Live Typing Playground Sandbox with `imePadding()` for soft keyboard responsiveness, WisprFlow Voice Dictation Demo, Gemma Local Model selector & stats, Theme & Haptic Settings.

## Recent UI & Gboard Typing Engine Improvements
- **Key Label Overflow Fix**: Updated `KeyButton` to scale fonts dynamically (`12.sp` for multi-character keys like `?123`, `ABC`, `123`; `18.sp` for character keys) with strict `softWrap = false` and `TextOverflow.Clip` to prevent text truncation (`...`).
- **Gboard-Style Touch Popups**: Magnified key preview bubbles with high contrast container and bold 26.sp text pop up directly above the user's touch location (-55.dp Y offset).
- **Spacebar Cursor Drag**: Swipe left/right on spacebar moves cursor position smoothly word by word or character by character.
- **Spacebar Autocorrect Commit**: Pressing spacebar automatically commits top prediction candidate if a typo is detected.
- **Keyboard Hide Button**: Added dedicated `ArrowDropDown` key to collapse the keyboard input window smoothly.
- **Responsive Insets**: Applied `imePadding()` to `MainActivity.kt` container to ensure text sandbox and setup options adjust cleanly when the IME is active.
- **Publish Version**: Updated `versionCode` to `34` and `versionName` to `"34.0"` in `app/build.gradle.kts` for publication release.
