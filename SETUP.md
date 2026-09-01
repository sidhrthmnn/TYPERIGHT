# TYPERIGHT Keyboard - Installation & Setup Guide

## Prerequisites

- Android Studio Hedgehog or later
- JDK 11+
- Android SDK 24+ (minimum)
- Android SDK 36 (target)

## Building the Project

### 1. Clone Repository
```bash
git clone https://github.com/sidhrthmnn/TYPERIGHT.git
cd TYPERIGHT
```

### 2. Setup Environment Variables
```bash
# Copy example env file
cp .env.example .env

# Edit .env with your configuration
# Add API keys and build secrets
```

### 3. Build with Gradle
```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Run tests
./gradlew test

# Run Android instrumented tests
./gradlew connectedAndroidTest
```

## Project Structure

```
app/src/main/java/com/aistudio/typeright/
├── di/                          # Dependency Injection (Hilt)
├── domain/                      # Business Logic Layer
│   ├── model/                   # Domain models
│   ├── repository/              # Repository interfaces
│   └── usecase/                 # Use case classes
├── data/                        # Data Access Layer
│   ├── local/                   # Local database
│   │   ├── database/            # Room database setup
│   │   ├── entity/              # Database entities
│   │   ├── dao/                 # Data Access Objects
│   │   └── datasource/          # Local data sources
│   └── repository/              # Repository implementations
├── presentation/                # UI Layer (Jetpack Compose)
│   ├── activity/                # Activities
│   ├── viewmodel/               # MVVM ViewModels
│   └── ui/
│       ├── screen/              # Compose screens
│       ├── components/          # Reusable components
│       └── theme/               # Material 3 theme
├── service/                     # IME Services
├── util/                        # Utilities & algorithms
└── test/                        # Unit tests
```

## Key Files

### Configuration Files
- `app/build.gradle.kts` - App-level gradle configuration
- `settings.gradle.kts` - Project settings
- `gradle.properties` - Gradle properties
- `.env` - Environment variables (create from .env.example)

### Manifest
- `app/src/main/AndroidManifest.xml` - App manifest with services

### Application Entry Point
- `TypeRightApplication.kt` - Hilt-enabled application class

## Running the App

### On Android Device
```bash
# Install debug build
./gradlew installDebug

# Run
./gradlew runDebug
```

### On Emulator
```bash
# Start emulator first
# Then install and run
./gradlew installDebug
./gradlew runDebug
```

## Enabling as System IME

1. Open Settings
2. Go to System > Language & input > Virtual keyboard
3. Tap "Manage on-screen keyboards"
4. Enable "TypeRight"
5. Go back and select TypeRight as default input method

## Development Workflow

### Adding a New Feature

1. **Create Domain Model:**
   ```kotlin
   // domain/model/MyFeature.kt
   data class MyFeature(val data: String)
   ```

2. **Define Repository Interface:**
   ```kotlin
   // domain/repository/MyRepository.kt
   interface MyRepository {
       suspend fun getFeature(): Result<MyFeature>
   }
   ```

3. **Implement Repository:**
   ```kotlin
   // data/repository/MyRepositoryImpl.kt
   class MyRepositoryImpl @Inject constructor(
       private val dataSource: LocalDataSource
   ) : MyRepository { ... }
   ```

4. **Create Use Case:**
   ```kotlin
   // domain/usecase/GetFeatureUseCase.kt
   class GetFeatureUseCase @Inject constructor(
       private val repository: MyRepository
   ) { ... }
   ```

5. **Add to ViewModel:**
   ```kotlin
   @HiltViewModel
   class MyViewModel @Inject constructor(
       private val useCase: GetFeatureUseCase
   ) : ViewModel() { ... }
   ```

6. **Create Compose Screen:**
   ```kotlin
   @Composable
   fun MyFeatureScreen(viewModel: MyViewModel = hiltViewModel()) { ... }
   ```

7. **Write Tests:**
   ```kotlin
   // test/MyRepositoryTest.kt
   class MyRepositoryTest { ... }
   ```

## Testing

### Unit Tests
```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests "*TrieDictionaryTest*"

# Run with coverage
./gradlew testDebugUnitTest --jacoco
```

### Instrumented Tests
```bash
# Run on device/emulator
./gradlew connectedAndroidTest
```

### UI Tests (Roborazzi)
```bash
# Generate screenshots
./gradlew roborazziDebug

# Compare with baseline
./gradlew verifyRoborazziDebug
```

## Debugging

### Using Android Studio Debugger
1. Set breakpoints in code
2. Run with `./gradlew installDebug`
3. Debug in Android Studio

### Viewing Logs
```bash
# Real-time logs
./gradlew installDebug -i

# Or use adb
adb logcat | grep "TYPERIGHT"
```

### Database Inspection
```bash
# Access Room database
adb shell
cd /data/data/com.aistudio.typeright.jkwpzq/databases/
ls -la

# Use sqlite3
sqlite3 typeright.db
.tables
.schema
```

## Performance Profiling

### Using Android Profiler
1. Build and run app
2. Open Android Profiler (View > Tool Windows > Profiler)
3. Monitor CPU, Memory, Network, Energy

### Measuring Algorithm Performance
```kotlin
// Trie performance test
val startTime = System.nanoTime()
trie.getWithPrefix("word")
val endTime = System.nanoTime()
println("Time: ${(endTime - startTime) / 1_000_000.0} ms")
```

## Troubleshooting

### Build Fails
```bash
# Clean build
./gradlew clean
./gradlew assembleDebug

# Invalidate cache
./gradlew build --refresh-dependencies
```

### Hilt Compilation Error
- Ensure `@AndroidEntryPoint` on Activity/Fragment
- Verify all dependencies have proper scopes
- Clean build: `./gradlew clean`

### Database Issues
```bash
# Delete app data
adb shell pm clear com.aistudio.typeright.jkwpzq

# Rebuild database
# App will recreate on next launch
```

### IME Not Showing
1. Make sure app is installed
2. Enable in Settings > Language & input
3. Check logcat for errors
4. Verify manifest permissions

## Release Build

### Create Signing Config
```bash
# Generate keystore
keytool -genkey -v -keystore my-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload

# Set environment variables
export KEYSTORE_PATH=/path/to/my-upload-key.jks
export STORE_PASSWORD=your_password
export KEY_PASSWORD=your_password
```

### Build Release APK
```bash
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```

### Build Release Bundle (AAB)
```bash
./gradlew bundleRelease

# Output: app/build/outputs/bundle/release/app-release.aab
```

## Contributing

1. Create feature branch: `git checkout -b feature/my-feature`
2. Make changes following architecture guidelines
3. Add tests for new code
4. Run tests: `./gradlew test`
5. Commit: `git commit -am 'Add my feature'`
6. Push: `git push origin feature/my-feature`
7. Create Pull Request

## Resources

- [Android Developer Docs](https://developer.android.com/docs)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Hilt Documentation](https://dagger.dev/hilt/)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)

## Support

For issues or questions:
1. Check existing GitHub issues
2. Search documentation
3. Create detailed issue with:
   - Android version
   - Device/Emulator
   - Steps to reproduce
   - Logcat output

---

**Last Updated:** September 1, 2026
**Version:** 129.0
