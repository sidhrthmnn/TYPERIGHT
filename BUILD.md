# Build Instructions

## Prerequisites

- Android Studio Hedgehog or later
- JDK 11+
- Android SDK 24+ (minimum API level)
- Android SDK 36 (target)

## Building from Command Line

### Debug Build
```bash
./gradlew clean assembleDebug
```

### Release Build
```bash
# First, setup signing config
export KEYSTORE_PATH=/path/to/keystore.jks
export STORE_PASSWORD=your_password
export KEY_PASSWORD=your_password

# Then build
./gradlew assembleRelease
```

### Running Tests
```bash
./gradlew test
```

## Building in Android Studio

1. Open project in Android Studio
2. Build → Make Project (Ctrl+F9)
3. Run → Run 'app' (Shift+F10)

## Build Output

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release.apk`
- **App Bundle**: `app/build/outputs/bundle/release/app-release.aab`

## Troubleshooting

### Hilt Compilation Errors
- Ensure all activities/fragments have `@AndroidEntryPoint`
- Run `./gradlew clean`
- Invalidate Android Studio cache

### Database Errors
- Delete app data: `adb shell pm clear com.aistudio.typeright.jkwpzq`
- App will recreate database on next launch

### Memory Issues
- Increase Gradle daemon memory in `gradle.properties`
- `org.gradle.jvmargs=-Xmx4g`

For more details, see [SETUP.md](SETUP.md)
