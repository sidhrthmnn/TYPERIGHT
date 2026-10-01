# Type Right Keyboard

## Build and verification

With JDK 21 and Android SDK Platform 36.1 installed, run
`./gradlew :app:testDebugUnitTest :app:assembleDebug` (`gradlew.bat` on Windows).
See [the keyboard review](docs/KEYBOARD_REVIEW.md) for verified improvements,
regression coverage, and remaining device-testing and release limitations.
See [companion app settings](docs/APP_SETTINGS_UI.md) for the app redesign,
screenshots, and UI verification.
See [voice input](docs/VOICE_INPUT.md) for live editor updates, the icon-only
toolbar, final-result handling, and speech-service requirements.

Type Right is an intelligent, modern Android keyboard designed to make typing faster, smarter, and effortless. Combining smart text predictions, an AI-powered writing assistant, extensive visual customization, and dynamic vocabulary updates, Type Right helps you write with speed, confidence, and style in any app.

---

## Key Features & Capabilities

### 1. Smart Predictive Typing & Correction
* **Intelligent Next-Word Predictions**: Anticipates the next word as you type, adapting to your personal vocabulary and conversation style.
* **Instant Auto-Correction**: Fixes typos, accidental letter swaps, double-typing mistakes, and missed spaces on the fly.
* **Multi-Candidate Suggestion Strip**: Displays multiple relevant suggestions right above the keyboard for quick one-tap insertion.
* **Live Grammar & Spell Checking**: Highlights and fixes spelling errors and grammatical inconsistencies in real time.

---

### 2. AI Writing Assistant & Tone Polishing
* **Multi-Style Tone Rephrasing**: Transform your draft into different tones with a single tap:
  * **Professional**: Refined and formal language tailored for work emails and business communications.
  * **Casual**: Relaxed, conversational, and natural phrasing for chatting with friends.
  * **Concise**: Trims wordiness and cuts straight to the point.
  * **Friendly**: Warm, encouraging, and approachable messaging.
  * **Academic / Expressive**: Deep, eloquent, and sophisticated vocabulary.
* **Smart Proofreader**: Corrects subtle phrasing issues, punctuation errors, and sentence structure without losing your original voice.
* **Voice Ramble Cleanup**: Converts spontaneous, rambling voice notes into structured, clear, and cohesive text.
* **Side-by-Side Comparison**: Review suggested edits with clear before-and-after highlights before applying them.

---

### 3. Voice Dictation & Audio Input
* **Voice-to-Text Input**: Speak naturally and see partial results update directly in the current text field. Done waits for the final correction; Cancel discards the voice composition.
* **Real-Time Audio Waveform**: Visual feedback showing live voice capture as you speak.
* **Multi-Language Voice Support**: Uses the installed Android speech service and its available language packs, preferring on-device recognition.
* **Post-Dictation Polish**: Ramble mode applies on-device AI cleanup after confirmation. Ordinary dictation preserves your words.

---

### 4. Dynamic Vocabulary & Trending Words
* **Continuous Vocabulary Updates**: Automatically enriches your dictionary with modern internet terminology, popular slang, and trending phrases.
* **Personal Vocabulary Learning**: Safely learns and remembers unique words, names, and acronyms you use often.
* **Custom Dictionary Management**: Add, view, or remove personalized words directly within the app settings.
* **Automatic Stale Word Cleanup**: Keeps suggestions crisp and relevant by removing obsolete, unused terms over time.

---

### 5. Built-in Clipboard Manager & Quick Snippets
* **Clipboard History**: Access recently copied text snippets, links, and messages with a single tap.
* **Pinned Snippets**: Save frequently used text—such as addresses, email signatures, bank details, or reply templates—for immediate access.
* **One-Tap Insertion**: Paste any saved clip directly into the active text field without switching apps.

---

### 6. Expressive Media: Emojis, GIFs & Kaomojis
* **Searchable Emoji Picker**: Comprehensive emoji catalog organized by categories with lightning-fast keyword search.
* **Trending GIFs**: Discover and share animated GIFs directly from the keyboard.
* **Expressive Kaomojis**: A rich collection of Japanese-style emoticons and ascii art (*(•‿•)*, *(¬_¬)*, *(╯°□°)╯︵ ┻━┻*) ready for instant sharing.

---

### 7. Customization & Visual Themes
* **Dynamic Material Theming**: Harmonizes the keyboard color palette with your device wallpaper and system theme.
* **Preset Themes**: Choose between Clean Light, Midnight Dark, AMOLED Black, Forest Green, Vibrant Blue, and Custom Accent colors.
* **Adjustable Keyboard Height**: Switch between Compact, Default, and Tall layouts to suit your screen size and typing comfort.
* **Dedicated Number Row**: Toggle a permanent number row on top for rapid numerical data entry.
* **Sensory Feedback**: Customize keypress sound effects and haptic vibration intensity.

---

### 8. Intuitive Gesture Controls
* **Spacebar Cursor Gliding**: Slide your thumb across the spacebar to move the text cursor precisely between letters.
* **Quick Swipe Deletion**: Swipe left from the backspace key to quickly erase whole words or sentences.
* **Quick Access Toolbar**: Convenient shortcuts for settings, themes, AI tools, voice input, and clipboard management.

---

### 9. Privacy & Control
* **On-Device Core Processing**: Standard typing, predictions, and dictionary lookups operate entirely on your device.
* **Configurable AI Modes**: Select local Gemma AI polish or turn AI off.
* **Profanity Filter**: Optional filter to keep suggestions clean and family-friendly.

## Local GGUF AI polish

AI Polish settings offer Local GGUF and Off. Local mode runs Gemma 4 E2B Instruct QAT Q4_0 on the Android device after a verified one-time 3.35 GB download. The actual weights are committed as three GGUF shards through Git LFS. The keyboard includes 46,693 frequency-ranked English words for predictions and corrections. See [setup, model provenance, and verification](docs/LOCAL_GGUF.md).
