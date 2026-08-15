package com.example

import android.content.Context
import android.content.SharedPreferences

class KeyboardSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("typeright_prefs", Context.MODE_PRIVATE)

    companion object {
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
        const val KEY_CLIPBOARD_ENABLED = "keyboard_clipboard_enabled"
        const val KEY_NUMBER_ROW_ENABLED = "keyboard_number_row_enabled"
        const val KEY_STRICTLY_USE_GEMINI = "strictly_use_gemini"

        const val THEME_LIGHT = "Minimal Light"
        const val THEME_DARK = "Minimal Dark"

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

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, "gemini-3.5-flash") ?: "gemini-3.5-flash"
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    fun isModelDownloaded(model: String): Boolean {
        return true
    }

    fun setModelDownloaded(model: String, downloaded: Boolean) {
        prefs.edit().putBoolean("model_downloaded_$model", downloaded).apply()
    }

    var whisperModel: String
        get() = prefs.getString(KEY_WHISPER_MODEL, "gemini-nano") ?: "gemini-nano"
        set(value) = prefs.edit().putString(KEY_WHISPER_MODEL, value).apply()

    var voiceLanguage: String
        get() = prefs.getString(KEY_VOICE_LANGUAGE, "en-US") ?: "en-US"
        set(value) = prefs.edit().putString(KEY_VOICE_LANGUAGE, value).apply()

    var voiceInputMode: String
        get() = prefs.getString(KEY_VOICE_INPUT_MODE, VOICE_MODE_CLOUD) ?: VOICE_MODE_CLOUD
        set(value) = prefs.edit().putString(KEY_VOICE_INPUT_MODE, value).apply()

    var clipboardEnabled: Boolean
        get() = prefs.getBoolean(KEY_CLIPBOARD_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CLIPBOARD_ENABLED, value).apply()

    var numberRowEnabled: Boolean
        get() = prefs.getBoolean(KEY_NUMBER_ROW_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NUMBER_ROW_ENABLED, value).apply()

    var theme: String
        get() = if (isDarkMode) THEME_DARK else THEME_LIGHT
        set(value) {
            isDarkMode = (value == THEME_DARK)
        }

    var height: String
        get() = prefs.getString(KEY_HEIGHT, HEIGHT_NORMAL) ?: HEIGHT_NORMAL
        set(value) = prefs.edit().putString(KEY_HEIGHT, value).apply()

    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    var autocorrectEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTOCORRECT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOCORRECT_ENABLED, value).apply()

    var swipeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_ENABLED, value).apply()

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
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var dynamicThemeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_THEME_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_THEME_ENABLED, value).apply()

    var accentColor: String
        get() = prefs.getString(KEY_ACCENT_COLOR, "#70C7C1") ?: "#70C7C1"
        set(value) = prefs.edit().putString(KEY_ACCENT_COLOR, value).apply()

    var strictlyUseGemini: Boolean
        get() = true
        set(_) {}
}
