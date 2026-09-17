package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "typeright_user_preferences",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, KeyboardSettings.PREFS_NAME))
    }
)

/**
 * Immutable representation of all user preferences managed via Jetpack DataStore.
 */
data class UserPreferences(
    // Theme selection
    val theme: String = KeyboardSettings.THEME_DARK,
    val isDarkMode: Boolean = true,
    val dynamicThemeEnabled: Boolean = false,
    val accentColor: String = "#70C7C1",
    val keyBordersEnabled: Boolean = true,
    val keyBevelEnabled: Boolean = true,
    val retroMonospace: Boolean = false,
    val mechanicalSound: Boolean = false,
    val keyboardHeight: String = KeyboardSettings.HEIGHT_NORMAL,

    // Auto-correction & typing toggles
    val autocorrectEnabled: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val popupOnKeypress: Boolean = true,
    val emojiSuggestionsEnabled: Boolean = true,
    val swipeEnabled: Boolean = true,
    val spaceSwipeEnabled: Boolean = true,
    val backspaceSwipeEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val hapticEnabled: Boolean = true,

    // Voice input settings
    val voiceInputMode: String = KeyboardSettings.VOICE_MODE_CLOUD,
    val voiceLanguage: String = "en-US",
    val whisperModel: String = "gemini-nano",
    val wisprFlowMode: WisprFlowMode = WisprFlowMode.AUTO,

    // AI and Language settings
    val aiModel: String = "gemini-3.5-flash",
    val offlineAiEnabled: Boolean = true,
    val geminiAiEnabled: Boolean = true,
    val nemotronAiEnabled: Boolean = false,
    val keyboardLanguage: String = "English",
    val aiLanguage: String = "English",
    val manglishTransliterationEnabled: Boolean = true,
    val clipboardEnabled: Boolean = true,
    val numberRowEnabled: Boolean = false,
    val oneHandedMode: String = "off",
    val supportTier: String = KeyboardSettings.TIER_AUTO,
    val profanityFilterEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val strictlyUseGemini: Boolean = false,
    val navBarClearance: String = "auto",
    val vocabAutoUpdateEnabled: Boolean = true,
    val vocabUpdateIntervalHours: Int = 12,
    val totalVocabWordsCount: Int = 0,
    val userWordsCount: Int = 0,
    val lastVocabSyncStatus: String = "Ready to sync"
)

/**
 * Jetpack DataStore implementation managing user preferences including theme selection,
 * auto-correction toggles, voice input settings, and AI engine controls.
 */
class UserPreferencesDataStore private constructor(context: Context) {

    private val appContext = context.applicationContext ?: context
    private val dataStore = appContext.dataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    object PreferencesKeys {
        // Theme selection
        val THEME = stringPreferencesKey(KeyboardSettings.KEY_THEME)
        val IS_DARK_MODE = booleanPreferencesKey(KeyboardSettings.KEY_DARK_MODE)
        val DYNAMIC_THEME_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_DYNAMIC_THEME_ENABLED)
        val ACCENT_COLOR = stringPreferencesKey(KeyboardSettings.KEY_ACCENT_COLOR)
        val KEY_BORDERS_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_KEY_BORDERS_ENABLED)
        val KEY_BEVEL_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_KEY_BEVEL_ENABLED)
        val RETRO_MONOSPACE = booleanPreferencesKey(KeyboardSettings.KEY_RETRO_MONOSPACE)
        val MECHANICAL_SOUND = booleanPreferencesKey(KeyboardSettings.KEY_MECHANICAL_SOUND)
        val KEYBOARD_HEIGHT = stringPreferencesKey(KeyboardSettings.KEY_HEIGHT)

        // Auto-correction toggles
        val AUTOCORRECT_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_AUTOCORRECT_ENABLED)
        val DOUBLE_SPACE_PERIOD = booleanPreferencesKey(KeyboardSettings.KEY_DOUBLE_SPACE_PERIOD)
        val POPUP_ON_KEYPRESS = booleanPreferencesKey(KeyboardSettings.KEY_POPUP_ON_KEYPRESS)
        val EMOJI_SUGGESTIONS = booleanPreferencesKey(KeyboardSettings.KEY_EMOJI_SUGGESTIONS)
        val SWIPE_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_SWIPE_ENABLED)
        val SPACE_SWIPE_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_SPACE_SWIPE_ENABLED)
        val BACKSPACE_SWIPE_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_BACKSPACE_SWIPE_ENABLED)
        val SOUND_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_SOUND_ENABLED)
        val HAPTIC_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_HAPTIC_ENABLED)

        // Voice input settings
        val VOICE_INPUT_MODE = stringPreferencesKey(KeyboardSettings.KEY_VOICE_INPUT_MODE)
        val VOICE_LANGUAGE = stringPreferencesKey(KeyboardSettings.KEY_VOICE_LANGUAGE)
        val WHISPER_MODEL = stringPreferencesKey(KeyboardSettings.KEY_WHISPER_MODEL)
        val WISPR_FLOW_MODE = stringPreferencesKey(KeyboardSettings.KEY_WISPR_FLOW_MODE)

        // AI and Language settings
        val AI_MODEL = stringPreferencesKey(KeyboardSettings.KEY_AI_MODEL)
        val OFFLINE_AI_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_OFFLINE_AI_ENABLED)
        val GEMINI_AI_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_GEMINI_AI_ENABLED)
        val NEMOTRON_AI_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_NEMOTRON_AI_ENABLED)
        val KEYBOARD_LANGUAGE = stringPreferencesKey(KeyboardSettings.KEY_KEYBOARD_LANGUAGE)
        val AI_LANGUAGE = stringPreferencesKey(KeyboardSettings.KEY_AI_LANGUAGE)
        val MANGLISH_TRANSLITERATION_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_MANGLISH_TRANSLITERATION_ENABLED)
        val CLIPBOARD_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_CLIPBOARD_ENABLED)
        val NUMBER_ROW_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_NUMBER_ROW_ENABLED)
        val ONE_HANDED_MODE = stringPreferencesKey(KeyboardSettings.KEY_ONE_HANDED_MODE)
        val SUPPORT_TIER = stringPreferencesKey(KeyboardSettings.KEY_SUPPORT_TIER)
        val PROFANITY_FILTER_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_PROFANITY_FILTER_ENABLED)
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_CLOUD_SYNC_ENABLED)
        val STRICTLY_USE_GEMINI = booleanPreferencesKey(KeyboardSettings.KEY_STRICTLY_USE_GEMINI)
        val NAV_BAR_CLEARANCE = stringPreferencesKey(KeyboardSettings.KEY_NAV_BAR_CLEARANCE)
        val VOCAB_AUTO_UPDATE_ENABLED = booleanPreferencesKey(KeyboardSettings.KEY_VOCAB_AUTO_UPDATE_ENABLED)
        val VOCAB_UPDATE_INTERVAL_HOURS = intPreferencesKey(KeyboardSettings.KEY_VOCAB_UPDATE_INTERVAL_HOURS)
        val TOTAL_VOCAB_WORDS_COUNT = intPreferencesKey(KeyboardSettings.KEY_TOTAL_VOCAB_WORDS_COUNT)
        val USER_WORDS_COUNT = intPreferencesKey(KeyboardSettings.KEY_USER_WORDS_COUNT)
        val LAST_VOCAB_SYNC_STATUS = stringPreferencesKey(KeyboardSettings.KEY_LAST_VOCAB_SYNC_STATUS)
    }

    /**
     * Cold Flow emitting [UserPreferences] upon any change in DataStore.
     */
    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val themeVal = prefs[PreferencesKeys.THEME] ?: KeyboardSettings.THEME_DARK
            val isDarkTheme = themeVal == KeyboardSettings.THEME_DARK ||
                    themeVal == KeyboardSettings.THEME_RETRO_CRT_GREEN ||
                    themeVal == KeyboardSettings.THEME_RETRO_AMBER ||
                    themeVal == KeyboardSettings.THEME_RETRO_SYNTHWAVE ||
                    themeVal == KeyboardSettings.THEME_AMOLED

            val wisprModeName = prefs[PreferencesKeys.WISPR_FLOW_MODE] ?: WisprFlowMode.AUTO.name
            val wisprMode = try {
                WisprFlowMode.valueOf(wisprModeName)
            } catch (e: Exception) {
                WisprFlowMode.AUTO
            }

            UserPreferences(
                theme = themeVal,
                isDarkMode = prefs[PreferencesKeys.IS_DARK_MODE] ?: isDarkTheme,
                dynamicThemeEnabled = prefs[PreferencesKeys.DYNAMIC_THEME_ENABLED] ?: false,
                accentColor = prefs[PreferencesKeys.ACCENT_COLOR] ?: "#70C7C1",
                keyBordersEnabled = prefs[PreferencesKeys.KEY_BORDERS_ENABLED] ?: true,
                keyBevelEnabled = prefs[PreferencesKeys.KEY_BEVEL_ENABLED] ?: true,
                retroMonospace = prefs[PreferencesKeys.RETRO_MONOSPACE] ?: true,
                mechanicalSound = prefs[PreferencesKeys.MECHANICAL_SOUND] ?: true,
                keyboardHeight = prefs[PreferencesKeys.KEYBOARD_HEIGHT] ?: KeyboardSettings.HEIGHT_NORMAL,

                autocorrectEnabled = prefs[PreferencesKeys.AUTOCORRECT_ENABLED] ?: true,
                doubleSpacePeriod = prefs[PreferencesKeys.DOUBLE_SPACE_PERIOD] ?: true,
                popupOnKeypress = prefs[PreferencesKeys.POPUP_ON_KEYPRESS] ?: true,
                emojiSuggestionsEnabled = prefs[PreferencesKeys.EMOJI_SUGGESTIONS] ?: true,
                swipeEnabled = prefs[PreferencesKeys.SWIPE_ENABLED] ?: true,
                spaceSwipeEnabled = prefs[PreferencesKeys.SPACE_SWIPE_ENABLED] ?: true,
                backspaceSwipeEnabled = prefs[PreferencesKeys.BACKSPACE_SWIPE_ENABLED] ?: true,
                soundEnabled = prefs[PreferencesKeys.SOUND_ENABLED] ?: true,
                hapticEnabled = prefs[PreferencesKeys.HAPTIC_ENABLED] ?: true,

                voiceInputMode = prefs[PreferencesKeys.VOICE_INPUT_MODE] ?: KeyboardSettings.VOICE_MODE_CLOUD,
                voiceLanguage = prefs[PreferencesKeys.VOICE_LANGUAGE] ?: "en-US",
                whisperModel = prefs[PreferencesKeys.WHISPER_MODEL] ?: "gemini-nano",
                wisprFlowMode = wisprMode,

                aiModel = prefs[PreferencesKeys.AI_MODEL] ?: "gemini-3.5-flash",
                offlineAiEnabled = prefs[PreferencesKeys.OFFLINE_AI_ENABLED] ?: true,
                geminiAiEnabled = prefs[PreferencesKeys.GEMINI_AI_ENABLED] ?: true,
                nemotronAiEnabled = prefs[PreferencesKeys.NEMOTRON_AI_ENABLED] ?: false,
                keyboardLanguage = prefs[PreferencesKeys.KEYBOARD_LANGUAGE] ?: "English",
                aiLanguage = prefs[PreferencesKeys.AI_LANGUAGE] ?: "English",
                manglishTransliterationEnabled = prefs[PreferencesKeys.MANGLISH_TRANSLITERATION_ENABLED] ?: true,
                clipboardEnabled = prefs[PreferencesKeys.CLIPBOARD_ENABLED] ?: true,
                numberRowEnabled = prefs[PreferencesKeys.NUMBER_ROW_ENABLED] ?: false,
                oneHandedMode = prefs[PreferencesKeys.ONE_HANDED_MODE] ?: "off",
                supportTier = prefs[PreferencesKeys.SUPPORT_TIER] ?: KeyboardSettings.TIER_AUTO,
                profanityFilterEnabled = prefs[PreferencesKeys.PROFANITY_FILTER_ENABLED] ?: true,
                cloudSyncEnabled = prefs[PreferencesKeys.CLOUD_SYNC_ENABLED] ?: false,
                strictlyUseGemini = prefs[PreferencesKeys.STRICTLY_USE_GEMINI] ?: false,
                navBarClearance = prefs[PreferencesKeys.NAV_BAR_CLEARANCE] ?: "auto",
                vocabAutoUpdateEnabled = prefs[PreferencesKeys.VOCAB_AUTO_UPDATE_ENABLED] ?: true,
                vocabUpdateIntervalHours = prefs[PreferencesKeys.VOCAB_UPDATE_INTERVAL_HOURS] ?: 12,
                totalVocabWordsCount = prefs[PreferencesKeys.TOTAL_VOCAB_WORDS_COUNT] ?: 0,
                userWordsCount = prefs[PreferencesKeys.USER_WORDS_COUNT] ?: 0,
                lastVocabSyncStatus = prefs[PreferencesKeys.LAST_VOCAB_SYNC_STATUS] ?: "Ready to sync"
            )
        }

    /**
     * Hot StateFlow providing real-time preference updates with initial value.
     */
    val userPreferencesState: StateFlow<UserPreferences> = userPreferencesFlow
        .stateIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            initialValue = readInitialFromSharedPreferences()
        )

    @Volatile
    private var cachedSnapshot: UserPreferences = userPreferencesState.value

    init {
        scope.launch {
            userPreferencesFlow.collect {
                cachedSnapshot = it
            }
        }
    }

    /**
     * Returns an immediate, non-blocking snapshot of user preferences.
     * Ideal for high-frequency IME keystroke loops.
     */
    fun currentSnapshot(): UserPreferences = cachedSnapshot

    // --- Granular Reactive Flows for UI Observation ---

    val themeFlow: Flow<String> = userPreferencesFlow.map { it.theme }.distinctUntilChanged()
    val isDarkModeFlow: Flow<Boolean> = userPreferencesFlow.map { it.isDarkMode }.distinctUntilChanged()
    val dynamicThemeFlow: Flow<Boolean> = userPreferencesFlow.map { it.dynamicThemeEnabled }.distinctUntilChanged()
    val accentColorFlow: Flow<String> = userPreferencesFlow.map { it.accentColor }.distinctUntilChanged()

    val autocorrectEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.autocorrectEnabled }.distinctUntilChanged()
    val soundEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.soundEnabled }.distinctUntilChanged()
    val hapticEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.hapticEnabled }.distinctUntilChanged()
    val swipeEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.swipeEnabled }.distinctUntilChanged()

    val voiceInputModeFlow: Flow<String> = userPreferencesFlow.map { it.voiceInputMode }.distinctUntilChanged()
    val voiceLanguageFlow: Flow<String> = userPreferencesFlow.map { it.voiceLanguage }.distinctUntilChanged()
    val whisperModelFlow: Flow<String> = userPreferencesFlow.map { it.whisperModel }.distinctUntilChanged()
    val wisprFlowModeFlow: Flow<WisprFlowMode> = userPreferencesFlow.map { it.wisprFlowMode }.distinctUntilChanged()

    val offlineAiEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.offlineAiEnabled }.distinctUntilChanged()
    val geminiAiEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.geminiAiEnabled }.distinctUntilChanged()
    val nemotronAiEnabledFlow: Flow<Boolean> = userPreferencesFlow.map { it.nemotronAiEnabled }.distinctUntilChanged()

    // --- Suspending Mutation Functions ---

    // 1. Theme selection methods
    suspend fun setTheme(theme: String) {
        val isDark = theme == KeyboardSettings.THEME_DARK ||
                theme == KeyboardSettings.THEME_RETRO_CRT_GREEN ||
                theme == KeyboardSettings.THEME_RETRO_AMBER ||
                theme == KeyboardSettings.THEME_RETRO_SYNTHWAVE ||
                theme == KeyboardSettings.THEME_AMOLED
        dataStore.edit { prefs ->
            prefs[PreferencesKeys.THEME] = theme
            prefs[PreferencesKeys.IS_DARK_MODE] = isDark
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.IS_DARK_MODE] = enabled }
    }

    suspend fun setDynamicThemeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DYNAMIC_THEME_ENABLED] = enabled }
    }

    suspend fun setAccentColor(colorHex: String) {
        dataStore.edit { it[PreferencesKeys.ACCENT_COLOR] = colorHex }
    }

    suspend fun setKeyBordersEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEY_BORDERS_ENABLED] = enabled }
    }

    suspend fun setKeyBevelEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.KEY_BEVEL_ENABLED] = enabled }
    }

    suspend fun setRetroMonospace(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.RETRO_MONOSPACE] = enabled }
    }

    suspend fun setMechanicalSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MECHANICAL_SOUND] = enabled }
    }

    suspend fun setKeyboardHeight(height: String) {
        dataStore.edit { it[PreferencesKeys.KEYBOARD_HEIGHT] = height }
    }

    // 2. Auto-correction & typing toggles
    suspend fun setAutocorrectEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.AUTOCORRECT_ENABLED] = enabled }
    }

    suspend fun setDoubleSpacePeriod(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.DOUBLE_SPACE_PERIOD] = enabled }
    }

    suspend fun setPopupOnKeypress(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.POPUP_ON_KEYPRESS] = enabled }
    }

    suspend fun setEmojiSuggestionsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.EMOJI_SUGGESTIONS] = enabled }
    }

    suspend fun setSwipeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SWIPE_ENABLED] = enabled }
    }

    suspend fun setSpaceSwipeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SPACE_SWIPE_ENABLED] = enabled }
    }

    suspend fun setBackspaceSwipeEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.BACKSPACE_SWIPE_ENABLED] = enabled }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.SOUND_ENABLED] = enabled }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HAPTIC_ENABLED] = enabled }
    }

    // 3. Voice input settings
    suspend fun setVoiceInputMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.VOICE_INPUT_MODE] = mode }
    }

    suspend fun setVoiceLanguage(language: String) {
        dataStore.edit { it[PreferencesKeys.VOICE_LANGUAGE] = language }
    }

    suspend fun setWhisperModel(model: String) {
        dataStore.edit { it[PreferencesKeys.WHISPER_MODEL] = model }
    }

    suspend fun setWisprFlowMode(mode: WisprFlowMode) {
        dataStore.edit { it[PreferencesKeys.WISPR_FLOW_MODE] = mode.name }
    }

    // 4. AI & Language settings
    suspend fun setAiModel(model: String) {
        dataStore.edit { it[PreferencesKeys.AI_MODEL] = model }
    }

    suspend fun setOfflineAiEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.OFFLINE_AI_ENABLED] = enabled }
    }

    suspend fun setGeminiAiEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.GEMINI_AI_ENABLED] = enabled }
    }

    suspend fun setNemotronAiEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NEMOTRON_AI_ENABLED] = enabled }
    }

    suspend fun setKeyboardLanguage(lang: String) {
        dataStore.edit { it[PreferencesKeys.KEYBOARD_LANGUAGE] = lang }
    }

    suspend fun setAiLanguage(lang: String) {
        dataStore.edit { it[PreferencesKeys.AI_LANGUAGE] = lang }
    }

    suspend fun setManglishTransliterationEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.MANGLISH_TRANSLITERATION_ENABLED] = enabled }
    }

    suspend fun setClipboardEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CLIPBOARD_ENABLED] = enabled }
    }

    suspend fun setNumberRowEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.NUMBER_ROW_ENABLED] = enabled }
    }

    suspend fun setOneHandedMode(mode: String) {
        dataStore.edit { it[PreferencesKeys.ONE_HANDED_MODE] = mode }
    }

    suspend fun setSupportTier(tier: String) {
        dataStore.edit { it[PreferencesKeys.SUPPORT_TIER] = tier }
    }

    suspend fun setProfanityFilterEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.PROFANITY_FILTER_ENABLED] = enabled }
    }

    suspend fun setCloudSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CLOUD_SYNC_ENABLED] = enabled }
    }

    suspend fun setStrictlyUseGemini(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.STRICTLY_USE_GEMINI] = enabled }
    }

    suspend fun setNavBarClearance(clearance: String) {
        dataStore.edit { it[PreferencesKeys.NAV_BAR_CLEARANCE] = clearance }
    }

    suspend fun setVocabAutoUpdateEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.VOCAB_AUTO_UPDATE_ENABLED] = enabled }
    }

    suspend fun setVocabUpdateIntervalHours(hours: Int) {
        dataStore.edit { it[PreferencesKeys.VOCAB_UPDATE_INTERVAL_HOURS] = hours }
    }

    suspend fun setTotalVocabWordsCount(count: Int) {
        dataStore.edit { it[PreferencesKeys.TOTAL_VOCAB_WORDS_COUNT] = count }
    }

    suspend fun setUserWordsCount(count: Int) {
        dataStore.edit { it[PreferencesKeys.USER_WORDS_COUNT] = count }
    }

    suspend fun setLastVocabSyncStatus(status: String) {
        dataStore.edit { it[PreferencesKeys.LAST_VOCAB_SYNC_STATUS] = status }
    }

    /**
     * Resets all preferences back to default values.
     */
    suspend fun resetToDefaults() {
        dataStore.edit { it.clear() }
    }

    /**
     * Asynchronous update helper for fire-and-forget calls from non-coroutine UI scopes.
     */
    fun updateAsync(action: suspend (UserPreferencesDataStore) -> Unit) {
        scope.launch {
            try {
                action(this@UserPreferencesDataStore)
            } catch (e: Exception) {
                // Log and swallow gracefully
            }
        }
    }

    private fun readInitialFromSharedPreferences(): UserPreferences {
        return try {
            val sp = appContext.getSharedPreferences(KeyboardSettings.PREFS_NAME, Context.MODE_PRIVATE)
            val themeVal = sp.getString(KeyboardSettings.KEY_THEME, KeyboardSettings.THEME_DARK) ?: KeyboardSettings.THEME_DARK
            val isDarkTheme = themeVal == KeyboardSettings.THEME_DARK ||
                    themeVal == KeyboardSettings.THEME_RETRO_CRT_GREEN ||
                    themeVal == KeyboardSettings.THEME_RETRO_AMBER ||
                    themeVal == KeyboardSettings.THEME_RETRO_SYNTHWAVE ||
                    themeVal == KeyboardSettings.THEME_AMOLED

            val wisprModeName = sp.getString(KeyboardSettings.KEY_WISPR_FLOW_MODE, WisprFlowMode.AUTO.name) ?: WisprFlowMode.AUTO.name
            val wisprMode = try {
                WisprFlowMode.valueOf(wisprModeName)
            } catch (e: Exception) {
                WisprFlowMode.AUTO
            }

            UserPreferences(
                theme = themeVal,
                isDarkMode = sp.getBoolean(KeyboardSettings.KEY_DARK_MODE, isDarkTheme),
                dynamicThemeEnabled = sp.getBoolean(KeyboardSettings.KEY_DYNAMIC_THEME_ENABLED, false),
                accentColor = sp.getString(KeyboardSettings.KEY_ACCENT_COLOR, "#70C7C1") ?: "#70C7C1",
                keyBordersEnabled = sp.getBoolean(KeyboardSettings.KEY_KEY_BORDERS_ENABLED, true),
                keyBevelEnabled = sp.getBoolean(KeyboardSettings.KEY_KEY_BEVEL_ENABLED, true),
                retroMonospace = sp.getBoolean(KeyboardSettings.KEY_RETRO_MONOSPACE, true),
                mechanicalSound = sp.getBoolean(KeyboardSettings.KEY_MECHANICAL_SOUND, true),
                keyboardHeight = sp.getString(KeyboardSettings.KEY_HEIGHT, KeyboardSettings.HEIGHT_NORMAL) ?: KeyboardSettings.HEIGHT_NORMAL,

                autocorrectEnabled = sp.getBoolean(KeyboardSettings.KEY_AUTOCORRECT_ENABLED, true),
                doubleSpacePeriod = sp.getBoolean(KeyboardSettings.KEY_DOUBLE_SPACE_PERIOD, true),
                popupOnKeypress = sp.getBoolean(KeyboardSettings.KEY_POPUP_ON_KEYPRESS, true),
                emojiSuggestionsEnabled = sp.getBoolean(KeyboardSettings.KEY_EMOJI_SUGGESTIONS, true),
                swipeEnabled = sp.getBoolean(KeyboardSettings.KEY_SWIPE_ENABLED, true),
                spaceSwipeEnabled = sp.getBoolean(KeyboardSettings.KEY_SPACE_SWIPE_ENABLED, true),
                backspaceSwipeEnabled = sp.getBoolean(KeyboardSettings.KEY_BACKSPACE_SWIPE_ENABLED, true),
                soundEnabled = sp.getBoolean(KeyboardSettings.KEY_SOUND_ENABLED, true),
                hapticEnabled = sp.getBoolean(KeyboardSettings.KEY_HAPTIC_ENABLED, true),

                voiceInputMode = sp.getString(KeyboardSettings.KEY_VOICE_INPUT_MODE, KeyboardSettings.VOICE_MODE_CLOUD) ?: KeyboardSettings.VOICE_MODE_CLOUD,
                voiceLanguage = sp.getString(KeyboardSettings.KEY_VOICE_LANGUAGE, "en-US") ?: "en-US",
                whisperModel = sp.getString(KeyboardSettings.KEY_WHISPER_MODEL, "gemini-nano") ?: "gemini-nano",
                wisprFlowMode = wisprMode,

                aiModel = sp.getString(KeyboardSettings.KEY_AI_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash",
                offlineAiEnabled = sp.getBoolean(KeyboardSettings.KEY_OFFLINE_AI_ENABLED, true),
                geminiAiEnabled = sp.getBoolean(KeyboardSettings.KEY_GEMINI_AI_ENABLED, true),
                nemotronAiEnabled = sp.getBoolean(KeyboardSettings.KEY_NEMOTRON_AI_ENABLED, false),
                keyboardLanguage = sp.getString(KeyboardSettings.KEY_KEYBOARD_LANGUAGE, "English") ?: "English",
                aiLanguage = sp.getString(KeyboardSettings.KEY_AI_LANGUAGE, "English") ?: "English",
                manglishTransliterationEnabled = sp.getBoolean(KeyboardSettings.KEY_MANGLISH_TRANSLITERATION_ENABLED, true),
                clipboardEnabled = sp.getBoolean(KeyboardSettings.KEY_CLIPBOARD_ENABLED, true),
                numberRowEnabled = sp.getBoolean(KeyboardSettings.KEY_NUMBER_ROW_ENABLED, false),
                oneHandedMode = sp.getString(KeyboardSettings.KEY_ONE_HANDED_MODE, "off") ?: "off",
                supportTier = sp.getString(KeyboardSettings.KEY_SUPPORT_TIER, KeyboardSettings.TIER_AUTO) ?: KeyboardSettings.TIER_AUTO,
                profanityFilterEnabled = sp.getBoolean(KeyboardSettings.KEY_PROFANITY_FILTER_ENABLED, true),
                cloudSyncEnabled = sp.getBoolean(KeyboardSettings.KEY_CLOUD_SYNC_ENABLED, false),
                strictlyUseGemini = sp.getBoolean(KeyboardSettings.KEY_STRICTLY_USE_GEMINI, false),
                navBarClearance = sp.getString(KeyboardSettings.KEY_NAV_BAR_CLEARANCE, "auto") ?: "auto",
                vocabAutoUpdateEnabled = sp.getBoolean(KeyboardSettings.KEY_VOCAB_AUTO_UPDATE_ENABLED, true),
                vocabUpdateIntervalHours = sp.getInt(KeyboardSettings.KEY_VOCAB_UPDATE_INTERVAL_HOURS, 12),
                totalVocabWordsCount = sp.getInt(KeyboardSettings.KEY_TOTAL_VOCAB_WORDS_COUNT, 0),
                userWordsCount = sp.getInt(KeyboardSettings.KEY_USER_WORDS_COUNT, 0),
                lastVocabSyncStatus = sp.getString(KeyboardSettings.KEY_LAST_VOCAB_SYNC_STATUS, "Ready to sync") ?: "Ready to sync"
            )
        } catch (e: Exception) {
            UserPreferences()
        }
    }

    companion object {
        @Volatile
        private var instance: UserPreferencesDataStore? = null

        fun getInstance(context: Context): UserPreferencesDataStore {
            return instance ?: synchronized(this) {
                instance ?: UserPreferencesDataStore(context).also { instance = it }
            }
        }
    }
}
