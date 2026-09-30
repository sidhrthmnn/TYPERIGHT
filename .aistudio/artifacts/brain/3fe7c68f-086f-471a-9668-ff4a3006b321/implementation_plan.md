# Keyboard Toolbar Polish & Reliable Full-Text Multi-Style AI Engine

## Title & Summary
Comprehensive refinement of the TypeRight keyboard layout and AI rewrite engine: removes the visual divider line separating suggested words from the keyboard keys to reallocate vertical height for larger icons and clearer suggestion typography, and overhauls the AI polishing pipeline so that it reliably processes the entire input text and strictly adheres to the user-selected tone/style (Professional, Casual, Concise, Elaborate, Rephrase, Auto Format).

---

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed during the interactive clarification interview:

- **Confirmed Decision 1 (Full-Text Scope)**: When AI polishing is triggered, it must process and rewrite the entire text field from beginning to end rather than only a single sentence or fragment around the cursor.
- **Confirmed Decision 2 (Strict Tone Enforcement)**: When a style mode is selected (Professional, Friendly/Casual, Concise/Shorten, Elaborate/Expand, Rephrase, Auto Format), the AI must actively transform the writing into that tone rather than falling back to standard grammar/typo fixes.
- **Confirmed Decision 3 (Visual Proportions)**: The horizontal divider line between the suggested words/toolbar and the keyboard keys is removed, and the vertical space is repurposed for moderately larger icons (22–24dp) and larger, easily readable suggestion text (15–15.5sp) while preserving balanced key heights for typing comfort.

---

## 1. Overview & Core Concept

### What It Does
1. **Seamless, Borderless Keyboard Interface**: Eliminates the dividing rule between the predictive suggestion row / top toolbar and the QWERTY/symbol key matrix. Increases the hit target and visual presence of toolbar icons (Mic, Settings, Writing Tools, Auto-Format, Proofread, Expand/Collapse) and expands suggestion chip text for effortless tap accuracy.
2. **End-to-End Text Ingestion & Replacement**: When triggering AI Polish, Auto Polish, or Writing Tools, the keyboard captures the complete input buffer across all paragraphs, ensuring no trailing or preceding text is omitted.
3. **High-Fidelity Style Transformation**: Bridges `AiPolishBackend` directly to Gemini Flash and enhanced on-device transformation engines with explicit tone prompts, guaranteeing that selecting "Professional", "Friendly", "Concise", "Elaborate", or "Rephrase" noticeably transforms the voice and style of the text.

### Target Audience & Persona
Mobile users and professionals who rely on the soft keyboard for rapid email drafting, messaging, and note-taking, requiring instant proofreading and stylistic transformations that never miss sentences or ignore requested tones.

### Key Value
- Clean, open, modern keyboard aesthetic that eliminates visual clutter and increases accessibility.
- Dependable, full-context AI polishing where the user never has to worry about partial rewrites or ignored tone selections.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Typing & Word Suggestions**:
   - The user types on the keyboard. Predictive suggestions appear seamlessly right above the top row of keys without a hard horizontal line.
   - Suggested words are rendered in 15–15.5sp typography with smooth middle-truncation for long words, separated only by subtle, elegant vertical breathers.
   - Toolbar action icons (Mic, Writing Tools, Auto Format, Settings) are enlarged to 22–24dp icons within 38–40dp touch targets, ensuring comfortable one-tap accessibility.

2. **One-Tap AI Polish / Auto-Polish**:
   - Tapping the Auto Polish / Polish icon or triggering Writing Tools extracts the entire document text.
   - The toolbar displays an active progress indicator or streaming words while the AI refines the text.
   - The entire text is updated in place, preserving cursor integrity, punctuation, and multi-paragraph layout.

3. **Writing Tools Modal & Tone Selection**:
   - Opening the Writing Tools sheet displays the complete captured text in an expansive card.
   - Tapping any style pill (**Proofread**, **Auto Format**, **Rephrase**, **Professional**, **Friendly**, **Concise**, **Elaborate**) immediately commands the model with a decisive prompt directive that actively transforms the entire text into that distinct voice.
   - The user sees highlighted diffs reflecting the new tone and taps the card to apply the changes atomically.

### Visual Identity & Theme Tokens
- **Divider Removal**: Complete removal of `HorizontalDivider` below the toolbar row, allowing keyboard background tones to flow smoothly into the key bed.
- **Toolbar Geometry**: Height tuned from ~38dp to 44dp (scaled dynamically with user keyboard height settings).
- **Icon Sizing**: Icon vectors upgraded from 18–20dp to 22–24dp; touch targets increased to 38–40dp.
- **Suggestion Typography**: Center priority suggestion at 15.5sp (Medium weight), secondary suggestions at 15sp (Regular weight), with high-contrast theme-adaptive color tokens (`LocalKeyboardStyle.keyTextColor`).

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Dual-Engine Routing in `AiPolishBackend`**
  - *Chosen Approach*: Wire `AiPolishBackend` to check for cloud Gemini API availability first when online, while maintaining local GGUF/on-device neural engines for offline mode.
  - *Why*: Cloud Gemini Flash executes full-text tone transformation in ~400ms with rich vocabulary, eliminating the problem where offline regex fallbacks could only fix simple typos.
  - *Alternatives Considered*: Forcing local-only regex heuristics, which proved incapable of generating genuine professional or casual stylistic shifts.

- **Decision 2: Comprehensive Text Buffer Extraction**
  - *Chosen Approach*: In `captureFullEditorText` and `performDirectAiPolish`, prioritize multi-stage extraction (`ExtractedTextRequest`, fallback to combined `getTextBeforeCursor(10000)` + `getSelectedText` + `getTextAfterCursor(10000)`), and ensure full replacement replaces the entire buffer accurately.
  - *Why*: Prevents partial text truncation in complex third-party text fields.

- **Decision 3: Directive Tone Prompts in LLM Instructions**
  - *Chosen Approach*: Explicitly instruct the model to "rewrite the full text from start to finish applying the [TONE] style across all sentences, not just correcting typos".
  - *Why*: Small and fast models tend to take the path of least resistance (fixing typos only) unless explicitly commanded to rewrite the tone.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       TypeRight Keyboard Service                             │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │   Enhanced Borderless Toolbar (44dp, No Divider, 22-24dp Icons)      │  │
│  │   [ ☰ Tools ]  [  Suggested Word 1  │  Suggested 2  │  Suggested 3  ] [ 🎙️ ]│  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │   Keyboard Key Matrix (QWERTY / Symbols / Numeric)                    │  │
│  │   Q  W  E  R  T  Y  U  I  O  P                                       │  │
│  │   A  S  D  F  G  H  J  K  L                                          │  │
│  │   ⇧  Z  X  C  V  B  N  M  ⌫                                          │  │
│  │   ?123   🌐   [              Space              ]   .   ⏎            │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │
                        Editor Full Text Snapshot
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            AiPolishBackend                                  │
│   Checks active engine: Cloud Gemini API vs Local On-Device Model           │
└──────────────────────┬───────────────────────────────┬──────────────────────┘
                       │                               │
             Cloud Available                   Offline / On-Device
                       ▼                               ▼
       ┌──────────────────────────────┐ ┌──────────────────────────────┐
       │       GeminiApiClient        │ │       GgufPolishEngine       │
       │  (Gemini 3.5 Flash / Lite)   │ │   + OnDeviceNeuralEngine     │
       │  - Full Text Enforcement     │ │  - Explicit Tone Directives  │
       │  - Distinct Tone Prompts     │ │  - Expanded Lexical Mapping  │
       └──────────────────────────────┘ └──────────────────────────────┘
                       │                               │
                       └───────────────┬───────────────┘
                                       ▼
                       ┌──────────────────────────────┐
                       │      AiOutputValidator       │
                       │  - Sanitizes output          │
                       │  - Verifies full length      │
                       └───────────────┬──────────────┘
                                       ▼
                       Atomic Commit to InputConnection
```

### Component & State Changes:
1. **`TypeRightKeyboardService.kt`**:
   - Remove `HorizontalDivider(color = keyTextColor.copy(alpha = 0.12f), thickness = 1.dp)` directly above the keyboard keys container.
   - Adjust `toolbarHeight` to 44dp base, granting the toolbar and suggestions more vertical breathing space.
   - Update `IconButton` and `Icon` sizes across the toolbar row from 18–20dp to 22–24dp with 38–40dp click containers.
   - Increase suggestion text size in `itemStyle` to 15.5sp (center) and 15sp (sides).
   - Ensure `performDirectAiPolish()` captures the entire text and applies the requested `PolishMode`.
2. **`AiPolishBackend.kt`**:
   - Check `GeminiApiClient` when API key is configured or engine is set to Cloud/Both/Online, delegating `generatePolish` and `streamPolish` to Gemini for high-fidelity tone rewriting, falling back to local GGUF/neural engine.
3. **`GeminiApiClient.kt` & `GgufPolishEngine.kt`**:
   - Strengthen system instructions for all modes (`PROFESSIONAL`, `CASUAL`, `SHORTEN`, `EXPAND`, `REPHRASE`, `AUTO_FORMAT`) with mandatory directives: "Rewrite every sentence across the entire text into the specified tone. Do not just fix typos."
4. **`AiOutputValidator.kt`**:
   - Ensure full text length validation does not penalize valid tone transformations while preventing truncations.
