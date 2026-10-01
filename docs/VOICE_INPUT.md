# Voice input

The keyboard's voice toolbar contains microphone activity, Cancel, and Done
icons. It displays no transcript or status text; accessibility descriptions
remain available to screen readers. Normal dictation appears directly in the
current text field, with revised partial results replacing the composing text.
Done waits for the recognizer's final correction before committing the text.
Cancel removes the current voice composition and rejects later callbacks.

Android's on-device speech recognizer is preferred when available. Otherwise the
installed system recognizer is used, with an offline preference. Availability
and recognition quality depend on that service and its language packs. This
change does not bundle Google's Gboard speech engine or claim equivalent phone
latency. The former fallback that captured audio without producing a transcript
has been removed; missing services and repeated failures show actionable errors.

Pauses retain completed segments and resume listening. Duplicate partial results
are suppressed, ordinary words such as "like" are preserved, and microphone
levels are smoothed. Recognition stays on the main thread. Session and editor
guards prevent cancelled or stale results entering another text field. Done
allows up to 1.5 seconds for a final result, then retains the last partial result
if the provider does not respond. Switching from dictation to Ramble mode first
finishes the existing transcript. Ramble cancellation also cancels pending AI
polish so a discarded draft cannot be inserted later.

## Verification

`VoiceRecordingSttServiceTest` exercises final-result timing, fallback timing,
partial revisions, pauses, preservation of ordinary words, stale callbacks, and
repeated provider errors with Android recognizer callbacks under Robolectric.
`VoiceTranscriptBufferTest` checks segment assembly. `VoiceInputToolbarTest` runs
on Android and checks that neither recording nor processing displays any visible
text, Cancel remains available, and Done is disabled during final processing.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.VoiceInputToolbarTest
```

The full unit suite passed 105 tests, and the companion settings and voice toolbar
passed six Android UI tests on an Android 16 emulator. Physical microphone quality
and latency still require testing on a phone with its installed speech service.
