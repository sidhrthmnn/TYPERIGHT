package com.example

import android.content.Context
import android.content.SharedPreferences

class KeyboardSettings(context: Context) {
    private val appContext = context.applicationContext ?: context
    val sharedPreferences: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = sharedPreferences
    val dataStore: UserPreferencesDataStore = UserPreferencesDataStore.getInstance(appContext)

    companion object {
        const val PREFS_NAME = "typeright_prefs"
        const val KEY_THEME = "keyboard_theme"
        const val KEY_HEIGHT = "keyboard_height"
        const val KEY_CUSTOM_KEYBOARD_HEIGHT_PERCENT = "custom_keyboard_height_percent"
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_AUTOCORRECT_ENABLED = "autocorrect_enabled"
        const val KEY_AUTOCORRECT_SENSITIVITY = "autocorrect_sensitivity"
        const val KEY_SWIPE_ENABLED = "swipe_enabled"
        const val KEY_SUPPORT_TIER = "support_tier" // "auto", "tier_1", "tier_2", "tier_3"
        const val KEY_PROFANITY_FILTER_ENABLED = "profanity_filter_enabled"
        const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
        const val KEY_DARK_MODE = "keyboard_dark_mode"
        const val KEY_DYNAMIC_THEME_ENABLED = "keyboard_dynamic_theme_enabled"
        const val KEY_ACCENT_COLOR = "keyboard_accent_color"
        const val KEY_AI_MODEL = "keyboard_ai_model"
        const val KEY_WHISPER_MODEL = "keyboard_whisper_model"
        const val KEY_VOICE_LANGUAGE = "keyboard_voice_language"
        const val KEY_VOICE_INPUT_MODE = "keyboard_voice_input_mode"
        const val KEY_KEYBOARD_LANGUAGE = "keyboard_language"
        const val KEY_AI_LANGUAGE = "keyboard_ai_language"
        const val KEY_AI_SELECT_ALL_LANGUAGES = "ai_select_all_languages"
        const val KEY_AI_SELECTED_LANGUAGES = "ai_selected_languages"
        const val KEY_MANGLISH_TRANSLITERATION_ENABLED = "manglish_transliteration_enabled"
        const val KEY_CLIPBOARD_ENABLED = "keyboard_clipboard_enabled"
        const val KEY_NUMBER_ROW_ENABLED = "keyboard_number_row_enabled"
        const val KEY_STRICTLY_USE_GEMINI = "strictly_use_gemini"
        const val KEY_OFFLINE_AI_ENABLED = "offline_ai_enabled"
        const val KEY_GEMINI_AI_ENABLED = "gemini_ai_enabled"
        const val KEY_NEMOTRON_AI_ENABLED = "nemotron_ai_enabled"
        const val KEY_VOCAB_AUTO_UPDATE_ENABLED = "vocab_auto_update_enabled"
        const val KEY_VOCAB_UPDATE_INTERVAL_HOURS = "vocab_update_interval_hours"
        const val KEY_LAST_VOCAB_SYNC_TIMESTAMP = "last_vocab_sync_timestamp"
        const val KEY_TOTAL_VOCAB_WORDS_COUNT = "total_vocab_words_count"
        const val KEY_LAST_VOCAB_SYNC_STATUS = "last_vocab_sync_status"
        const val KEY_TRENDING_WORDS_COUNT = "trending_words_count"
        const val KEY_USER_WORDS_COUNT = "user_words_count"

        const val KEY_WISPR_FLOW_MODE = "wispr_flow_mode"
        const val KEY_KEY_BORDERS_ENABLED = "key_borders_enabled"
        const val KEY_SPACE_SWIPE_ENABLED = "space_swipe_enabled"
        const val KEY_BACKSPACE_SWIPE_ENABLED = "backspace_swipe_enabled"
        const val KEY_DOUBLE_SPACE_PERIOD = "double_space_period"
        const val KEY_POPUP_ON_KEYPRESS = "popup_on_keypress"
        const val KEY_ONE_HANDED_MODE = "one_handed_mode" // "off", "left", "right"
        const val KEY_EMOJI_SUGGESTIONS = "emoji_suggestions"
        const val KEY_NAV_BAR_CLEARANCE = "keyboard_nav_bar_clearance" // "auto", "3button", "gesture", "none"
        const val KEY_KEY_BEVEL_ENABLED = "keyboard_key_bevel_enabled"
        const val KEY_RETRO_MONOSPACE = "keyboard_retro_monospace"
        const val KEY_MECHANICAL_SOUND = "keyboard_mechanical_sound"

        // Keyboard Themes: Light (Arrangement), Dark (Standard), and Night (AMOLED Deep Black)
        const val THEME_LIGHT = "Light"
        const val THEME_DARK = "Dark"
        const val THEME_NIGHT = "Night"

        // Compatibility constants mapped appropriately
        const val THEME_AMOLED = "Night"
        const val THEME_RETRO_BEIGE = "Light"
        const val THEME_RETRO_CRT_GREEN = "Night"
        const val THEME_RETRO_AMBER = "Dark"
        const val THEME_RETRO_MAC1984 = "Light"
        const val THEME_RETRO_SYNTHWAVE = "Night"
        const val THEME_MATERIAL_YOU = "Dark"
        const val THEME_MINT = "Light"
        const val THEME_CORAL = "Light"

        const val HEIGHT_SHORT = "Short"
        const val HEIGHT_NORMAL = "Normal"
        const val HEIGHT_TALL = "Tall"
        const val HEIGHT_CUSTOM = "Custom"

        const val TIER_AUTO = "Auto-detect"
        const val TIER_1 = "Tier 1: Full AI (Voice + Polish)"
        const val TIER_2 = "Tier 2: Voice Only"
        const val TIER_3 = "Tier 3: Standard Keyboard (No AI)"

        const val VOICE_MODE_CLOUD = "Fast Mode (Cloud)"
        const val VOICE_MODE_LOCAL = "Private Mode (On-Device)"

        const val SENSITIVITY_MILD = "Mild"
        const val SENSITIVITY_BALANCED = "Balanced"
        const val SENSITIVITY_AGGRESSIVE = "Aggressive"
    }

    var wisprFlowMode: WisprFlowMode
        get() {
            val name = prefs.getString(KEY_WISPR_FLOW_MODE, WisprFlowMode.AUTO.name) ?: WisprFlowMode.AUTO.name
            return try {
                WisprFlowMode.valueOf(name)
            } catch (e: Exception) {
                WisprFlowMode.AUTO
            }
        }
        set(value) {
            prefs.edit().putString(KEY_WISPR_FLOW_MODE, value.name).apply()
            dataStore.updateAsync { it.setWisprFlowMode(value) }
        }

    var keyBordersEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEY_BORDERS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEY_BORDERS_ENABLED, value).apply()
            dataStore.updateAsync { it.setKeyBordersEnabled(value) }
        }

    var spaceSwipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPACE_SWIPE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SPACE_SWIPE_ENABLED, value).apply()
            dataStore.updateAsync { it.setSpaceSwipeEnabled(value) }
        }

    var backspaceSwipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKSPACE_SWIPE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BACKSPACE_SWIPE_ENABLED, value).apply()
            dataStore.updateAsync { it.setBackspaceSwipeEnabled(value) }
        }

    var doubleSpacePeriod: Boolean
        get() = prefs.getBoolean(KEY_DOUBLE_SPACE_PERIOD, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DOUBLE_SPACE_PERIOD, value).apply()
            dataStore.updateAsync { it.setDoubleSpacePeriod(value) }
        }

    var popupOnKeypress: Boolean
        get() = prefs.getBoolean(KEY_POPUP_ON_KEYPRESS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_POPUP_ON_KEYPRESS, value).apply()
            dataStore.updateAsync { it.setPopupOnKeypress(value) }
        }

    var oneHandedMode: String
        get() = prefs.getString(KEY_ONE_HANDED_MODE, "off") ?: "off"
        set(value) {
            prefs.edit().putString(KEY_ONE_HANDED_MODE, value).apply()
            dataStore.updateAsync { it.setOneHandedMode(value) }
        }

    var emojiSuggestionsEnabled: Boolean
        get() = prefs.getBoolean(KEY_EMOJI_SUGGESTIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_EMOJI_SUGGESTIONS, value).apply()
            dataStore.updateAsync { it.setEmojiSuggestionsEnabled(value) }
        }

    var navBarClearance: String
        get() = prefs.getString(KEY_NAV_BAR_CLEARANCE, "auto") ?: "auto"
        set(value) {
            prefs.edit().putString(KEY_NAV_BAR_CLEARANCE, value).apply()
            dataStore.updateAsync { it.setNavBarClearance(value) }
        }

    var aiModel: String
        get() = (prefs.getString(KEY_AI_MODEL, "gemini-3.1-flash-lite-preview") ?: "gemini-3.1-flash-lite-preview").let {
            if (it.contains("3.5-flash-lite") || it.contains("2.5-flash-lite")) "gemini-3.1-flash-lite-preview" else it
        }
        set(value) {
            prefs.edit().putString(KEY_AI_MODEL, value).apply()
            dataStore.updateAsync { it.setAiModel(value) }
        }

    fun isModelDownloaded(model: String): Boolean {
        return true
    }

    fun setModelDownloaded(model: String, downloaded: Boolean) {
        prefs.edit().putBoolean("model_downloaded_$model", downloaded).apply()
    }

    var whisperModel: String
        get() = prefs.getString(KEY_WHISPER_MODEL, "gemini-nano") ?: "gemini-nano"
        set(value) {
            prefs.edit().putString(KEY_WHISPER_MODEL, value).apply()
            dataStore.updateAsync { it.setWhisperModel(value) }
        }

    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US"
        set(value) {
            prefs.edit().putString(KEY_VOICE_LANGUAGE, value).apply()
            dataStore.updateAsync { it.setVoiceLanguage(value) }
        }

    var keyboardLanguage: String
        get() = prefs.getString(KEY_KEYBOARD_LANGUAGE, "English") ?: "English"
        set(value) {
            prefs.edit().putString(KEY_KEYBOARD_LANGUAGE, value).apply()
            dataStore.updateAsync { it.setKeyboardLanguage(value) }
        }

    var aiLanguage: String
        get() = prefs.getString(KEY_AI_LANGUAGE, "English") ?: "English"
        set(value) {
            prefs.edit().putString(KEY_AI_LANGUAGE, value).apply()
            dataStore.updateAsync { it.setAiLanguage(value) }
        }

    var isAllAiLanguagesSelected: Boolean
        get() = prefs.getBoolean(KEY_AI_SELECT_ALL_LANGUAGES, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AI_SELECT_ALL_LANGUAGES, value).apply()
        }

    fun getSelectedAiLanguageCodes(): Set<String> {
        if (isAllAiLanguagesSelected) {
            return ModelLanguages.ALL_CODES
        }
        val raw = prefs.getString(KEY_AI_SELECTED_LANGUAGES, null) ?: return ModelLanguages.DEFAULT_SELECTED_CODES
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setSelectedAiLanguageCodes(codes: Set<String>) {
        prefs.edit().putString(KEY_AI_SELECTED_LANGUAGES, codes.joinToString(",")).apply()
    }

    fun selectSingleAiLanguage(code: String) {
        if (code.equals("ALL", ignoreCase = true)) {
            isAllAiLanguagesSelected = true
            setSelectedAiLanguageCodes(setOf("ALL"))
        } else {
            isAllAiLanguagesSelected = false
            setSelectedAiLanguageCodes(setOf(code))
        }
    }

    fun getSingleSelectedAiLanguageCode(): String {
        if (isAllAiLanguagesSelected) return "ALL"
        return getSelectedAiLanguageCodes().firstOrNull() ?: "en"
    }

    fun getActiveAiLanguageDisplay(): String {
        if (isAllAiLanguagesSelected) return "All Languages (140+)"
        val codes = getSelectedAiLanguageCodes()
        if (codes.isEmpty()) return "English"
        val names = codes.mapNotNull { ModelLanguages.findByCode(it)?.name }
        return when {
            names.size == 1 -> {
                val lang = ModelLanguages.findByCode(codes.first())
                if (lang != null && lang.nativeName.isNotBlank() && !lang.nativeName.equals(lang.name, ignoreCase = true)) {
                    "${lang.name} (${lang.nativeName})"
                } else {
                    names.first()
                }
            }
            names.size in 2..3 -> names.joinToString(", ")
            else -> "${names.take(2).joinToString(", ")}, +${names.size - 2} more (${names.size})"
        }
    }

    fun isAiLanguageSelected(code: String): Boolean {
        if (isAllAiLanguagesSelected) return true
        return getSelectedAiLanguageCodes().contains(code)
    }

    fun toggleAiLanguage(code: String, selected: Boolean) {
        if (code.equals("ALL", ignoreCase = true)) {
            if (selected) selectAllAiLanguages() else deselectAllAiLanguages()
            return
        }
        val current = getSelectedAiLanguageCodes().toMutableSet()
        if (selected) {
            current.add(code)
        } else {
            current.remove(code)
        }
        isAllAiLanguagesSelected = false
        setSelectedAiLanguageCodes(current)
    }

    fun selectAllAiLanguages() {
        isAllAiLanguagesSelected = true
        setSelectedAiLanguageCodes(ModelLanguages.ALL_CODES)
    }

    fun deselectAllAiLanguages() {
        isAllAiLanguagesSelected = false
        setSelectedAiLanguageCodes(emptySet())
    }

    fun resetToDefaultAiLanguages() {
        isAllAiLanguagesSelected = false
        setSelectedAiLanguageCodes(ModelLanguages.DEFAULT_SELECTED_CODES)
    }

    fun getActiveAiLanguagePromptGuidance(): String {
        if (isAllAiLanguagesSelected) {
            return "Active language scope: ALL 140+ languages supported by Gemma 3 (including English, European, Indic/South Asian, East/SE Asian, Middle Eastern, African, and Americas/Pacific languages, as well as transliterated or code-mixed text). Accurately detect, preserve, and respect the input's natural language and script without unwanted cross-language translation."
        }
        val codes = getSelectedAiLanguageCodes()
        val languageList = codes.mapNotNull { ModelLanguages.findByCode(it) }
        return if (languageList.isNotEmpty()) {
            val names = languageList.joinToString(", ") { lang ->
                if (lang.nativeName.isNotBlank() && !lang.nativeName.equals(lang.name, ignoreCase = true)) {
                    "${lang.name} (${lang.nativeName})"
                } else {
                    lang.name
                }
            }
            "Target & considered language(s): $names. Edit, spell-check, and polish adhering strictly to the grammar, orthography, vocabulary, and nuances of these selected languages. Preserve the author's intended language and script; never translate away from the input text's original language."
        } else {
            "Target language: English. Edit, spell-check, and polish adhering strictly to grammar, orthography, and vocabulary. Preserve the author's intended language and script."
        }
    }

    var manglishTransliterationEnabled: Boolean
        get() = prefs.getBoolean(KEY_MANGLISH_TRANSLITERATION_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MANGLISH_TRANSLITERATION_ENABLED, value).apply()
            dataStore.updateAsync { it.setManglishTransliterationEnabled(value) }
        }

    var voiceInputMode: String
        get() = prefs.getString(KEY_VOICE_INPUT_MODE, VOICE_MODE_CLOUD) ?: VOICE_MODE_CLOUD
        set(value) {
            prefs.edit().putString(KEY_VOICE_INPUT_MODE, value).apply()
            dataStore.updateAsync { it.setVoiceInputMode(value) }
        }

    var clipboardEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CLIPBOARD_ENABLED, value).apply()
            dataStore.updateAsync { it.setClipboardEnabled(value) }
        }

    var numberRowEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NUMBER_ROW_ENABLED, value).apply()
            dataStore.updateAsync { it.setNumberRowEnabled(value) }
        }

    var theme: String
        get() {
            val t = prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
            return when {
                t.equals(THEME_LIGHT, ignoreCase = true) || t.equals("Light Arrangement", ignoreCase = true) -> THEME_LIGHT
                t.equals(THEME_NIGHT, ignoreCase = true) || t.equals("Night", ignoreCase = true) || t.equals("AMOLED Black", ignoreCase = true) -> THEME_NIGHT
                else -> THEME_DARK
            }
        }
        set(value) {
            val normalized = when {
                value.equals(THEME_LIGHT, ignoreCase = true) || value.equals("Light Arrangement", ignoreCase = true) -> THEME_LIGHT
                value.equals(THEME_NIGHT, ignoreCase = true) || value.equals("Night", ignoreCase = true) -> THEME_NIGHT
                else -> THEME_DARK
            }
            prefs.edit().putString(KEY_THEME, normalized).apply()
            isDarkMode = normalized != THEME_LIGHT
            dataStore.updateAsync { it.setTheme(normalized) }
        }

    var keyBevelEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEY_BEVEL_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEY_BEVEL_ENABLED, value).apply()
            dataStore.updateAsync { it.setKeyBevelEnabled(value) }
        }

    var retroMonospace: Boolean
        get() = prefs.getBoolean(KEY_RETRO_MONOSPACE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_RETRO_MONOSPACE, value).apply()
            dataStore.updateAsync { it.setRetroMonospace(value) }
        }

    var retroMonospaceEnabled: Boolean
        get() = retroMonospace
        set(value) { retroMonospace = value }

    var mechanicalSound: Boolean
        get() = prefs.getBoolean(KEY_MECHANICAL_SOUND, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MECHANICAL_SOUND, value).apply()
            dataStore.updateAsync { it.setMechanicalSoundEnabled(value) }
        }

    var mechanicalSoundEnabled: Boolean
        get() = mechanicalSound
        set(value) { mechanicalSound = value }

    var height: String
        get() = prefs.getString(KEY_HEIGHT, HEIGHT_SHORT) ?: HEIGHT_SHORT
        set(value) {
            prefs.edit().putString(KEY_HEIGHT, value).apply()
            dataStore.updateAsync { it.setKeyboardHeight(value) }
        }

    /** Percentage of usable screen height used when the user selects Custom size. */
    var customKeyboardHeightPercent: Float
        get() = prefs.getFloat(KEY_CUSTOM_KEYBOARD_HEIGHT_PERCENT, 28.5f).coerceIn(20f, 45f)
        set(value) {
            prefs.edit().putFloat(KEY_CUSTOM_KEYBOARD_HEIGHT_PERCENT, value.coerceIn(20f, 45f)).apply()
        }

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()
            dataStore.updateAsync { it.setSoundEnabled(value) }
        }

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()
            dataStore.updateAsync { it.setHapticEnabled(value) }
        }

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOCORRECT_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOCORRECT_ENABLED, value).apply()
            dataStore.updateAsync { it.setAutocorrectEnabled(value) }
        }

    var autocorrectSensitivity: String
        get() = prefs.getString(KEY_AUTOCORRECT_SENSITIVITY, SENSITIVITY_BALANCED) ?: SENSITIVITY_BALANCED
        set(value) {
            prefs.edit().putString(KEY_AUTOCORRECT_SENSITIVITY, value).apply()
            dataStore.updateAsync { it.setAutocorrectSensitivity(value) }
        }

    var swipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SWIPE_ENABLED, value).apply()
            dataStore.updateAsync { it.setSwipeEnabled(value) }
        }

    var supportTier: String
        get() = prefs.getString(KEY_SUPPORT_TIER, TIER_AUTO) ?: TIER_AUTO
        set(value) = prefs.edit().putString(KEY_SUPPORT_TIER, value).apply()

    var profanityFilterEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROFANITY_FILTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PROFANITY_FILTER_ENABLED, value).apply()

    var cloudSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLOUD_SYNC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CLOUD_SYNC_ENABLED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()
            dataStore.updateAsync { it.setDarkMode(value) }
        }

    var dynamicThemeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_THEME_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_DYNAMIC_THEME_ENABLED, value).apply()
            dataStore.updateAsync { it.setDynamicThemeEnabled(value) }
        }

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "#70C7C1") ?: "#70C7C1"
        set(value) {
            prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()
            dataStore.updateAsync { it.setAccentColor(value) }
        }

    var strictlyUseGemini: Boolean
        get() = prefs.getBoolean(KEY_STRICTLY_USE_GEMINI, false)
        set(value) {
            prefs.edit().putBoolean(KEY_STRICTLY_USE_GEMINI, value).apply()
            dataStore.updateAsync { it.setStrictlyUseGemini(value) }
        }

    var offlineAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_OFFLINE_AI_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_OFFLINE_AI_ENABLED, value).commit()
            dataStore.updateAsync { it.setOfflineAiEnabled(value) }
        }

    var geminiAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_GEMINI_AI_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GEMINI_AI_ENABLED, value).commit()
            dataStore.updateAsync { it.setGeminiAiEnabled(value) }
        }

    var nemotronAiEnabled: Boolean
        get() = prefs.getBoolean(KEY_NEMOTRON_AI_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_NEMOTRON_AI_ENABLED, value).commit()
            dataStore.updateAsync { it.setNemotronAiEnabled(value) }
        }

    val activeAiEngine: ActiveAiEngine
        get() = when (prefs.getString("polish_backend", "local")) {
            "off" -> ActiveAiEngine.NONE
            else -> ActiveAiEngine.OFFLINE
        }

    var vocabAutoUpdateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOCAB_AUTO_UPDATE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VOCAB_AUTO_UPDATE_ENABLED, value).apply()

    var vocabUpdateIntervalHours: Int
        get() = prefs.getInt(KEY_VOCAB_UPDATE_INTERVAL_HOURS, 12)
        set(value) = prefs.edit().putInt(KEY_VOCAB_UPDATE_INTERVAL_HOURS, value).apply()

    var lastVocabSyncTimestamp: Long
        get() = prefs.getLong(KEY_LAST_VOCAB_SYNC_TIMESTAMP, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_VOCAB_SYNC_TIMESTAMP, value).apply()

    var totalVocabWordsCount: Int
        get() = prefs.getInt(KEY_TOTAL_VOCAB_WORDS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_VOCAB_WORDS_COUNT, value).apply()

    var lastVocabSyncStatus: String
        get() = prefs.getString(KEY_LAST_VOCAB_SYNC_STATUS, "Ready to sync") ?: "Ready to sync"
        set(value) = prefs.edit().putString(KEY_LAST_VOCAB_SYNC_STATUS, value).apply()

    var trendingWordsCount: Int
        get() = prefs.getInt(KEY_TRENDING_WORDS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TRENDING_WORDS_COUNT, value).apply()

    var userWordsCount: Int
        get() = prefs.getInt(KEY_USER_WORDS_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_USER_WORDS_COUNT, value).apply()

    fun setActiveAiEngine(engine: ActiveAiEngine) {
        val backendString = if (engine == ActiveAiEngine.NONE) "off" else "local"
        prefs.edit().putString("polish_backend", backendString).commit()
        if (engine == ActiveAiEngine.NONE) {
            offlineAiEnabled = false
            geminiAiEnabled = false
            nemotronAiEnabled = false
        } else {
            offlineAiEnabled = true
            geminiAiEnabled = false
            nemotronAiEnabled = false
        }
    }
}

enum class ActiveAiEngine(
    val title: String,
    val shortLabel: String,
    val symbol: String,
    val description: String
) {
    BOTH("Local On-Device AI", "Local", "📱", "Runs 100% on this phone offline"),
    OFFLINE("Local On-Device AI (Gemma 3 1B)", "Local", "📱", "Runs 100% on this phone offline without cloud dependency"),
    ONLINE("Local On-Device AI", "Local", "📱", "Runs 100% on this phone offline"),
    NEMOTRON("Local On-Device AI", "Local", "📱", "Runs 100% on this phone offline"),
    NONE("AI Off", "Off", "⚪", "AI assistants disabled")
}
