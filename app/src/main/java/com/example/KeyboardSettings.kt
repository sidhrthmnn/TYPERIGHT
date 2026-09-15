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
        const val KEY_SOUND_ENABLED = "sound_enabled"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_AUTOCORRECT_ENABLED = "autocorrect_enabled"
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

        // Retro Theme Presets
        const val THEME_RETRO_BEIGE = "Retro Beige (Model M)"
        const val THEME_RETRO_CRT_GREEN = "Retro CRT Terminal"
        const val THEME_RETRO_AMBER = "Retro Amber Terminal"
        const val THEME_RETRO_MAC1984 = "Retro 1984 Macintosh"
        const val THEME_RETRO_SYNTHWAVE = "Retro 80s Synthwave"

        const val THEME_LIGHT = "Minimal Light"
        const val THEME_DARK = "Minimal Dark"
        const val THEME_MATERIAL_YOU = "Material You"
        const val THEME_AMOLED = "AMOLED Black"
        const val THEME_MINT = "Pixel Mint"
        const val THEME_CORAL = "Coral Glow"

        const val HEIGHT_SHORT = "Short"
        const val HEIGHT_NORMAL = "Normal"
        const val HEIGHT_TALL = "Tall"

        const val TIER_AUTO = "Auto-detect"
        const val TIER_1 = "Tier 1: Full AI (Voice + Polish)"
        const val TIER_2 = "Tier 2: Voice Only"
        const val TIER_3 = "Tier 3: Standard Keyboard (No AI)"

        const val VOICE_MODE_CLOUD = "Fast Mode (Cloud)"
        const val VOICE_MODE_LOCAL = "Private Mode (On-Device)"
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
        get() = prefs.getString(KEY_AI_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
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
        get() = prefs.getString(KEY_THEME, THEME_RETRO_BEIGE) ?: THEME_RETRO_BEIGE
        set(value) {
            prefs.edit().putString(KEY_THEME, value).apply()
            // Keep isDarkMode in sync for components that check dark mode
            isDarkMode = (value == THEME_DARK || value == THEME_RETRO_CRT_GREEN || value == THEME_RETRO_AMBER || value == THEME_RETRO_SYNTHWAVE || value == THEME_AMOLED)
            dataStore.updateAsync { it.setTheme(value) }
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
        get() = prefs.getString(KEY_HEIGHT, HEIGHT_NORMAL) ?: HEIGHT_NORMAL
        set(value) {
            prefs.edit().putString(KEY_HEIGHT, value).apply()
            dataStore.updateAsync { it.setKeyboardHeight(value) }
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
        get() = prefs.getBoolean(KEY_DARK_MODE, false)
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
        get() = prefs.getBoolean(KEY_GEMINI_AI_ENABLED, true)
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
        get() = when {
            offlineAiEnabled && geminiAiEnabled && !nemotronAiEnabled -> ActiveAiEngine.BOTH
            offlineAiEnabled && nemotronAiEnabled && !geminiAiEnabled -> ActiveAiEngine.NEMOTRON
            offlineAiEnabled && !geminiAiEnabled && !nemotronAiEnabled -> ActiveAiEngine.OFFLINE
            !offlineAiEnabled && nemotronAiEnabled && !geminiAiEnabled -> ActiveAiEngine.NEMOTRON
            !offlineAiEnabled && geminiAiEnabled && !nemotronAiEnabled -> ActiveAiEngine.ONLINE
            offlineAiEnabled && geminiAiEnabled && nemotronAiEnabled -> ActiveAiEngine.BOTH
            else -> ActiveAiEngine.NONE
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
        when (engine) {
            ActiveAiEngine.BOTH -> {
                offlineAiEnabled = true
                geminiAiEnabled = true
                nemotronAiEnabled = false
            }
            ActiveAiEngine.OFFLINE -> {
                offlineAiEnabled = true
                geminiAiEnabled = false
                nemotronAiEnabled = false
            }
            ActiveAiEngine.ONLINE -> {
                offlineAiEnabled = false
                geminiAiEnabled = true
                nemotronAiEnabled = false
            }
            ActiveAiEngine.NEMOTRON -> {
                offlineAiEnabled = false
                geminiAiEnabled = false
                nemotronAiEnabled = true
            }
            ActiveAiEngine.NONE -> {
                offlineAiEnabled = false
                geminiAiEnabled = false
                nemotronAiEnabled = false
            }
        }
    }
}

enum class ActiveAiEngine(
    val title: String,
    val shortLabel: String,
    val symbol: String,
    val description: String
) {
    BOTH("Both Engines", "Both", "⚡☁️", "Offline on-device + Online Gemini Cloud"),
    OFFLINE("Offline AI", "Offline", "⚡", "Fast on-device neural & grammar engine"),
    ONLINE("Online Gemini", "Online", "☁️", "Advanced cloud Gemini intelligence"),
    NEMOTRON("Online Nemotron", "Nemotron", "🟢", "Advanced cloud NVIDIA Nemotron intelligence"),
    NONE("AI Off", "Off", "⚪", "AI assistants disabled")
}
