# Implementation Plan: High-Speed Streaming AI Polish & Clean Session Lifecycle

Accelerate Writing Tools AI Polish by upgrading to the fastest modern Gemini Flash Lite model (`gemini-3.1-flash-lite-preview`), streaming polished words into the UI in real time, eliminating non-existent model fallback delays, and strictly resetting in-flight and cached states on keyboard reopen so stale results never leak across sessions.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences were confirmed and form the foundation of this implementation:

- **AI Model Selection**: Prioritize **`gemini-3.1-flash-lite-preview`** (the official, fastest low-latency Flash Lite model) with secondary fallback to `gemini-3.5-flash`. Remove non-existent model strings (`gemini-3.5-flash-lite`, `gemini-flash-lite-latest`) that were previously triggering 404 network errors and 5-second timeouts.
- **Handling Network Delays**: **Stream results word-by-word as they generate**. The user immediately sees polished text appearing dynamically rather than waiting on a static progress spinner.
- **Session Lifecycle**: **Always start fresh and cancel previous pending jobs**. When Writing Tools is opened or when the keyboard is hidden/reopened, any in-flight background tasks are immediately cancelled, preventing stale previous results from appearing later.

---

## 1. Overview & Core Concept

- **Problem Root Causes**:
  1. *Model Name Mismatch*: `GeminiApiClient` attempted calls to non-existent model endpoints (`gemini-3.5-flash-lite`, `gemini-flash-lite-latest`), causing HTTP 404 errors and network wait cycles until hitting the 5-second timeout.
  2. *False "Existing Text" Result*: When the cloud request timed out, the system fell back to local deterministic rules. When the local rules found no grammar changes, it returned the unpolished original text as the "result".
  3. *Background Job Leak & Stale Reappearance*: When the keyboard was dismissed, the background coroutine in `PolishCoordinator` continued running. When it finally finished or cached in the background, `_uiState` retained the completed state. Upon reopening the keyboard minutes later, the UI immediately rendered the old result from the previous session before new text was captured.
- **Solution**:
  1. Target `gemini-3.1-flash-lite-preview` directly with tight HTTP connection timeouts (2.5s connect, 4s read).
  2. Implement Server-Sent Events (SSE) streaming (`streamGenerateContent?alt=sse`) in `GeminiApiClient` and `PolishCoordinator` to stream text word-by-word into the UI.
  3. Wire strict lifecycle cleanup: cancel active jobs on keyboard hide (`onFinishInputView`), reset UI state on panel entry (`DisposableEffect`), and bind results to current editor snapshot IDs so mismatched or stale text is never rendered.

---

## 2. User Experience & Visual Design

- **Key User Flows**:
  1. *Opening Writing Tools*:
     - User taps Writing Tools / AI Polish in the toolbar or tools drawer.
     - The panel opens in a clean initial state: snapshot ID is recorded, and streaming initiates immediately for the active text.
  2. *Live Word-by-Word Streaming*:
     - Text starts flowing into the card within milliseconds of API response generation.
     - A subtle streaming pulse indicator shows that content is actively writing.
     - The user can watch the polished or rephrased version materialize word-by-word.
  3. *Instant Tap-to-Apply*:
     - Once complete (or even while reviewing), tapping the card applies the finalized polished text into the text field and returns to normal typing.
  4. *Session Isolation*:
     - If the user leaves the keyboard, switches apps, or closes the panel, all background streaming immediately cancels. Reopening Writing Tools captures fresh text from the current input field without any ghost text from prior sessions.

- **Visual Feedback**:
  - Material 3 card styling with smooth dynamic diff highlighting once generation completes.
  - Active progress feedback during initial stream connection, transitioning directly into streaming text.

---

## 3. Key Product Decisions & Trade-Offs

- **Streaming Architecture**:
  - *Chosen Approach*: SSE streaming via `streamGenerateContent?alt=sse` with `Flow<String>` emissions into `PolishUiState.Streaming(partialText, mode)`.
  - *Why*: Eliminates perceived latency; users see words appear in under 500ms rather than waiting 3–5 seconds for complete batch generation.
- **Fail-Fast Fallback**:
  - *Chosen Approach*: 2.5-second timeout on initial stream connection. If connection fails or offline, immediately trigger on-device neural polish and clearly tag the output.
  - *Why*: Prevents users from getting stuck in long wait states.
- **Snapshot Binding**:
  - *Chosen Approach*: Tag every polish job with a unique `sessionId` and `textHash`. Drop any incoming stream or result if the current editor session does not match.
  - *Why*: Completely guarantees that text from a previous text box or previous session can never display in a new session.

---

## 4. Technical Architecture & Data Strategy

```
┌──────────────────────────────────────────────────────────────┐
│                  TypeRightKeyboardService                    │
│  - onFinishInputView() -> coordinator.cancelCurrent()        │
└──────────────────────────────┬───────────────────────────────┘
                               │
                ┌──────────────▼──────────────┐
                │     GboardProofreadPanel    │
                │  - DisposableEffect cleanup │
                │  - Observes uiState StateFlow│
                └──────────────┬──────────────┘
                               │
                ┌──────────────▼──────────────┐
                │      PolishCoordinator      │
                │  - triggerPolishStream()    │
                │  - Snapshot ID validation   │
                │  - Cancels previous job     │
                └──────────────┬──────────────┘
                               │
        ┌──────────────────────┴──────────────────────┐
        ▼                                             ▼
┌───────────────────────────┐             ┌─────────────────────────┐
│     GeminiApiClient       │             │ OnDeviceNeuralPolish    │
│ - gemini-3.1-flash-lite-  │             │ (Offline Fallback)      │
│   preview                 │             └─────────────────────────┘
│ - streamGenerateContent   │
│ - 2.5s fail-fast timeout  │
└───────────────────────────┘
```

### Key Code Modifications:

1. **`GeminiApiClient.kt`**:
   - Update model list to verified valid identifiers: `gemini-3.1-flash-lite-preview` as primary, `gemini-3.5-flash` as fallback.
   - Implement `streamGeneratePolish(input, mode): Flow<String>` using HTTP SSE streaming (`streamGenerateContent?alt=sse`) to emit text chunks as they arrive.
   - Set snappy OkHttp socket timeouts (connect: 2.5s, read: 4s).

2. **`PolishCoordinator.kt`**:
   - Add `PolishUiState.Streaming(partialText: String, mode: PolishMode, snapshotId: Long)`.
   - Update `triggerPolish` to execute the streaming flow, emitting incremental text to `_uiState`.
   - Add `resetForSession(sessionId: Long)` to clear prior results when a new session starts.
   - Enforce strict `snapshot.sessionId` matching so late responses from discarded sessions are automatically dropped.

3. **`TypeRightKeyboardService.kt`**:
   - In `GboardProofreadPanel`:
     - In `DisposableEffect(Unit)`: call `coordinator.resetForSession()` on entry and `coordinator.cancelCurrent()` on disposal.
     - When `uiState is PolishUiState.Streaming`, display the partial text live with a typing indicator.
   - In `onFinishInputView()` and `resetEditorState()`: call `PolishCoordinator.getInstance(this).cancelCurrent()` to ensure background jobs never survive keyboard dismissal.

---

## 5. Verification & Testing Plan

1. **Compilation**: Run `compile_applet` to verify clean build without warnings.
2. **Speed & Model Latency Verification**:
   - Verify that requests target `gemini-3.1-flash-lite-preview` directly without 404 retries.
   - Verify that first tokens begin streaming to the UI within 300–600ms on active network.
3. **Real-Time Word-by-Word Streaming**:
   - Open Writing Tools on a test sentence (e.g., "i am writing this emial to you about the proyect").
   - Confirm that the polished text streams word-by-word into the card rather than showing a long blank spinner.
4. **Session Isolation & No-Stale-Text Verification**:
   - Start a polish request, immediately close Writing Tools or dismiss the keyboard, wait 5 seconds.
   - Reopen Writing Tools on a different sentence or empty box.
   - Confirm that the previous sentence's polish result NEVER appears, and the new session starts completely fresh.
5. **Offline & Fallback Verification**:
   - Simulate network failure or timeout: verify graceful fallback to smart on-device polish without indefinite hanging.
