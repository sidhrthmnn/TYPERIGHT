# TYPERIGHT - Refactored Architecture Guide

## Overview

TypeRight has been completely refactored using modern Android best practices with a clean MVVM + Clean Architecture pattern. This document outlines the new architecture, layers, and key components.

## Architecture Layers

### 1. **Domain Layer** (`domain/`)

Contains pure business logic, independent of Android framework.

#### Models
- `KeyboardState.kt` - Keyboard UI state
- `TextSuggestion.kt` - Text prediction/correction suggestions
- `ToneStyle.kt` - Tone transformation styles
- `VoiceResult.kt` - Voice-to-text results
- `DictionaryEntry.kt` - Dictionary entries
- `Result.kt` - Generic result wrapper (Success/Error/Loading)

#### Repositories (Interfaces)
- `PredictionRepository` - Next-word predictions
- `CorrectionRepository` - Auto-correction with Levenshtein distance
- `PolishingRepository` - Tone transformation and proofreading
- `DictionaryRepository` - Dictionary CRUD and management
- `VoiceRepository` - Voice-to-text conversion
- `ClipboardRepository` - Clipboard history management
- `ThemeRepository` - Theme configuration

#### Use Cases
- `GetPredictionsUseCase` - Fetch word predictions
- `GetCorrectionsUseCase` - Get spelling corrections
- `TransformToneUseCase` - Transform text tone
- `CheckSpellingUseCase` - Spell checking
- `AddCustomWordUseCase` - Add words to custom dictionary

### 2. **Data Layer** (`data/`)

Handles all data operations: local database, remote APIs, and repositories.

#### Local Database
- `TypeRightDatabase` - Room database with 3 entities
- `DictionaryEntity` - Words with frequency tracking
- `ClipboardEntity` - Clipboard history items
- `HistoryEntity` - Typing history

#### DAOs
- `DictionaryDao` - Dictionary CRUD with prefix matching
- `ClipboardDao` - Clipboard history operations
- `HistoryDao` - History management

#### Data Sources
- `LocalDictionaryDataSource` - Local dictionary access
- `LocalClipboardDataSource` - Local clipboard access

#### Repository Implementations
- `PredictionRepositoryImpl` - On-device predictions using Trie
- `CorrectionRepositoryImpl` - Levenshtein distance-based corrections
- `DictionaryRepositoryImpl` - Dictionary management
- `ClipboardRepositoryImpl` - Clipboard history
- `PolishingRepositoryImpl` - Text transformation (professional, casual, etc.)
- `VoiceRepositoryImpl` - Voice input handling
- `ThemeRepositoryImpl` - Theme management with presets

### 3. **Presentation Layer** (`presentation/`)

#### ViewModels
- `KeyboardViewModel` - Manages keyboard state and suggestions
- `PolishingViewModel` - Handles text polishing and tone transformation
- `ThemeViewModel` - Manages theme configuration

#### UI Components (Jetpack Compose)
- `SuggestionStrip.kt` - Displays text suggestions with confidence scores
- `ToneTransformationPanel.kt` - Before/after comparison view
- `SpellingErrorDisplay.kt` - Shows spelling errors with suggestions

#### Screens
- `KeyboardInputScreen.kt` - Main keyboard input interface
- `PolishingScreen.kt` - Text polishing assistant
- `ClipboardScreen.kt` - Clipboard history manager
- `ThemeScreen.kt` - Theme customization

#### Theme
- `Theme.kt` - Material 3 color schemes (light/dark)
- `Type.kt` - Typography definitions

### 4. **Dependency Injection** (`di/`)

Using Hilt for compile-time safe dependency injection.

#### Modules
- `DatabaseModule` - Provides Room database and DAOs
- `RepositoryModule` - Binds repository interfaces to implementations

### 5. **Utilities** (`util/`)

#### Algorithms & Data Structures
- `TrieDictionary.kt` - O(k) prefix matching for fast word lookups
- `BloomFilter.kt` - Space-efficient membership testing
- `QwertyProximityMap.kt` - QWERTY adjacency detection for typos

#### Logging
- `Logger.kt` - Timber-based logging utility

### 6. **Services** (`service/`)

#### IME Service
- `BaseKeyboardService.kt` - Abstract base for IME services
- `TypeRightKeyboardService.kt` - Main keyboard implementation

#### Background Jobs
- `DictionaryUpdateWorker.kt` - WorkManager task for dictionary updates
- `DictionaryUpdateBootReceiver.kt` - Boot completion handler

### 7. **Testing** (`test/`)

Unit and integration tests using JUnit and Mockito.

- `TrieDictionaryTest.kt` - Trie data structure tests
- `CorrectionRepositoryTest.kt` - Spell checking tests
- `ResultTest.kt` - Result wrapper tests

## Key Design Patterns

### Result Wrapper Pattern
```kotlin
seal class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}
```

This eliminates nullable returns and provides explicit error handling.

### Repository Pattern
Repositories provide a single source of truth for data, abstracting data sources (local/remote).

### Dependency Injection (Hilt)
All dependencies are injected at compile-time for type safety and testability.

### MVVM with StateFlow
ViewModels expose immutable UI state via `StateFlow` for reactive updates.

## Algorithms Implemented

### 1. Levenshtein Distance (Edit Distance)
**Purpose:** Auto-correction with typo detection
**Complexity:** O(n*m) where n, m are word lengths
**Use Case:** Finding similar words to misspelled input

```kotlin
fun levenshteinDistance(s1: String, s2: String): Int
// Example: distance("cat", "cart") = 1
```

### 2. Trie (Prefix Tree)
**Purpose:** Fast dictionary lookups and word completion
**Complexity:** O(k) where k is prefix length
**Use Case:** Next-word prediction, autocomplete suggestions

```kotlin
class TrieDictionary {
    fun getWithPrefix(prefix: String): List<String>
    fun search(word: String): Boolean
}
```

### 3. Bloom Filter
**Purpose:** Space-efficient membership testing
**Complexity:** O(k) lookups, minimal space overhead
**Use Case:** Fast negative lookups (word NOT in dictionary)

```kotlin
class BloomFilter {
    fun add(element: String)
    fun mightContain(element: String): Boolean
}
```

### 4. QWERTY Proximity Map
**Purpose:** Detect typos from keyboard adjacency
**Use Case:** Suggesting corrections for "thsi" → "this"

## Features Implementation

### Smart Predictions
- Uses Trie dictionary for O(k) prefix matching
- Confidence scoring based on word frequency
- Real-time streaming predictions

### Auto-Correction
- Levenshtein distance algorithm
- QWERTY proximity detection
- Multi-candidate suggestions

### Text Polishing
- Tone transformation (Professional, Casual, Concise, etc.)
- Grammar and punctuation fixes
- Side-by-side comparison

### Dictionary Management
- On-device storage with Room database
- Custom word support
- Frequency tracking
- Background sync with WorkManager

### Clipboard Management
- History tracking with Room
- Pinned items for quick access
- Search and clear functionality

### Theme System
- Material 3 dynamic theming
- 5 preset themes (Light, Dark, AMOLED, Forest, Ocean)
- Customizable colors and keyboard height

## Testing Strategy

### Unit Tests
- Algorithm tests (Levenshtein, Trie)
- Repository tests
- Use case tests

### UI Tests
- Roborazzi for snapshot testing
- Compose testing framework

### Integration Tests
- Database operations
- Service initialization

## Performance Optimizations

1. **Lazy Loading:** ML models loaded on-demand
2. **Caching:** Memory → Disk → Network hierarchy
3. **Coroutines:** Non-blocking operations
4. **Trie Dictionary:** O(k) instead of O(n) lookups
5. **Bloom Filter:** Fast negative lookups
6. **Database Indexing:** Optimized queries

## Security & Privacy

1. **On-Device Processing:** Core features work offline
2. **Encrypted Storage:** Sensitive data encrypted with EncryptedSharedPreferences
3. **Minimal Permissions:** Only necessary permissions requested
4. **No User Tracking:** No analytics or telemetry
5. **Data Isolation:** Separate database for each user

## Build Configuration

### Dependencies Added
- **Hilt:** 2.51.1 - Dependency injection
- **Timber:** 5.0.1 - Logging
- **WorkManager:** 2.9.0 - Background jobs
- **EncryptedSharedPreferences:** 1.1.0-alpha06 - Secure storage
- **DataStore:** 1.0.0 - Preferences management

### Build Types
- **Debug:** Full logging, Hilt testing
- **Release:** Minified, Proguard rules applied, Signing config

## Migration Guide

### From Old Architecture

1. **Replace direct Activity code:**
   ```kotlin
   // Old
   class MainActivity : Activity() {
       fun fetchPredictions() { /* direct DB call */ }
   }
   
   // New
   @AndroidEntryPoint
   class MainActivity : ComponentActivity() {
       @Inject lateinit var viewModel: KeyboardViewModel
   }
   ```

2. **Use ViewModels:**
   ```kotlin
   @HiltViewModel
   class KeyboardViewModel @Inject constructor(
       private val getPredictionsUseCase: GetPredictionsUseCase
   ) : ViewModel()
   ```

3. **Use Repositories:**
   ```kotlin
   // Instead of direct DAO access
   val result = predictionRepository.getNextWordPredictions(text)
   ```

## File Structure Summary

```
app/src/main/java/com/aistudio/typeright/
├── TypeRightApplication.kt          # App entry point
├── di/                              # Dependency injection
│   ├── DatabaseModule.kt
│   └── RepositoryModule.kt
├── domain/                          # Business logic
│   ├── model/                       # Data models
│   ├── repository/                  # Interfaces
│   └── usecase/                     # Use cases
├── data/                            # Data layer
│   ├── local/
│   │   ├── database/                # Room DB
│   │   ├── entity/                  # DB entities
│   │   └── datasource/              # Local data sources
│   └── repository/                  # Implementations
├── presentation/                    # UI layer
│   ├── activity/
│   ├── viewmodel/                   # MVVM ViewModels
│   └── ui/
│       ├── screen/                  # Compose screens
│       ├── components/              # Reusable components
│       └── theme/                   # Material 3 theme
├── service/                         # IME & background services
├── util/                            # Utilities & algorithms
└── test/                            # Unit tests
```

## Next Steps

1. Implement `TypeRightKeyboardService` with full IME functionality
2. Add voice-to-text integration (Google Speech-to-Text API)
3. Implement cloud sync for custom dictionaries
4. Add more unit and UI tests
5. Integrate ML models for advanced predictions
6. Add analytics and crash reporting
7. Performance profiling and optimization

## Contributing

When adding new features:

1. Create domain models in `domain/model/`
2. Define repository interface in `domain/repository/`
3. Implement in `data/repository/`
4. Create use case in `domain/usecase/`
5. Inject in ViewModel and expose via StateFlow
6. Compose UI in `presentation/ui/`
7. Add unit tests in `test/`

## References

- [Hilt Documentation](https://dagger.dev/hilt/)
- [Room Database](https://developer.android.com/training/data-storage/room)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)
- [Material Design 3](https://m3.material.io/)
