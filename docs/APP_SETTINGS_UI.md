# Companion app settings

The companion app opens on Settings, with persistent navigation to Home, Typing,
AI polish, and Settings. The redesign is scoped to the companion activity; the
keyboard layout, IME theme, prediction engine, and model assets are unchanged.

- Searchable groups for typing, touch and sound, appearance, dictionary, and AI.
- Consistent cards, spacing, typography, full-row switches, and light, dark, and
  midnight palettes. Controls continue to write the existing saved preferences.
- A focused personal dictionary screen with word and shortcut entry first.
- Clear on-device AI setup, model status, Gemma consent, language selection, and
  a draft playground using the existing backend.
- Search and page state survive tab navigation and activity recreation. Back
  returns to Settings, and incoming tab shortcuts work with an open activity.

## Preview

| Light | Dark |
| --- | --- |
| ![Light settings](images/app-settings-light.png) | ![Dark settings](images/app-settings-dark.png) |

## Verification

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`
passes with 95 unit tests. `AppSettingsUiTest` covers saved settings and search
after recreation, tab state and Back, incoming tab shortcuts, and all three
theme choices. The theme test also passes at 320 dp width with 1.3 font scaling.
Screenshots were captured from the running Compose UI on an Android 16 emulator.

To run the companion UI checks on a connected Android device:

```sh
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.AppSettingsUiTest
```
