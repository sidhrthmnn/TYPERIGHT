package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.content.Intent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers persistence and navigation boundaries introduced by the companion app redesign. */
@RunWith(AndroidJUnit4::class)
class AppSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val target = File(compose.activity.getExternalFilesDir("ui-checks"), "$name.png")
        target.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun shortcutsOpenRequestedTabWhenAppIsAlreadyRunning() {
        compose.onNodeWithTag("nav_settings").performClick()
        listOf(2 to "ai", 1 to "typing", 2 to "ai").forEach { (index, name) ->
            compose.runOnUiThread {
                // Preserve the launch intent identity tracked by ActivityScenario during cleanup.
                compose.activity.startActivity(Intent(compose.activity.intent)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("target_tab", index))
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithTag("app_page_$index").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("nav_$name").assertIsSelected()
            if (index == 1) compose.onNodeWithTag("custom_word_input").assertExists()
            else compose.onNodeWithTag("ai_engine_card").assertExists()
            capture("app-$name")
        }
    }

    @Test fun settingChangesPersistAndSearchSurvivesRecreation() {
        val settings = KeyboardSettings(compose.activity)
        val original = settings.autocorrectEnabled
        try {
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("pref_autocorrect_switch").performScrollTo().performClick()
            compose.waitUntil(5_000) { settings.autocorrectEnabled != original }
            compose.activityRule.scenario.recreate()
            val toggle = compose.onNodeWithTag("pref_autocorrect_switch").performScrollTo()
            if (original) toggle.assertIsOff() else toggle.assertIsOn()
            compose.onNodeWithTag("settings_search").performScrollTo().performTextInput("sound")
            compose.onNodeWithTag("pref_sound_switch").assertExists()
            compose.onNodeWithTag("pref_number_row_switch").assertDoesNotExist()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings_search").assertTextContains("sound")
            compose.onNodeWithTag("pref_sound_switch").assertExists()
            assertEquals(!original, settings.autocorrectEnabled)
        } finally { settings.autocorrectEnabled = original }
    }

    @Test fun navigationPreservesSearchAndBackReturnsToSettings() {
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_search").performTextInput("theme")
        compose.onNodeWithTag("nav_ai").performClick()
        compose.onNodeWithTag("ai_engine_card").assertExists()
        compose.onNodeWithTag("manage_languages_button").performScrollTo().performClick()
        compose.onNodeWithText("Select Languages").assertExists()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithTag("nav_settings").assertIsSelected()
        compose.onNodeWithTag("settings_search").assertTextContains("theme")
        compose.onNodeWithTag("theme_chip_light").assertExists()
        compose.onNodeWithTag("pref_sound_switch").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("pref_sound_switch").assertExists()
    }

    @Test fun adaptiveTypingControlsPersistAndClearOnlyTheProfile() {
        val settings = KeyboardSettings(compose.activity)
        val originalLearning = settings.personalizedLearningEnabled
        val originalClipboard = settings.clipboardEnabled
        val profile = PersonalTypingProfile.get(compose.activity)
        try {
            profile.observe("tea", listOf("morning"))
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("settings_search").performTextInput("learning")
            compose.onNodeWithTag("pref_learning_switch").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            assertEquals(!originalLearning, KeyboardSettings(compose.activity).personalizedLearningEnabled)
            compose.onNodeWithTag("pref_clear_learning").performScrollTo().performClick()
            compose.onNodeWithText("Clear adaptive typing profile?").assertExists()
            compose.onNodeWithText("Clear", useUnmergedTree = true).performClick()
            compose.runOnIdle { org.junit.Assert.assertTrue(profile.candidates("", emptyList()).isEmpty()) }
            compose.onNodeWithTag("pref_clipboard_switch").performScrollTo().performClick()
            compose.activityRule.scenario.recreate()
            assertEquals(!originalClipboard, KeyboardSettings(compose.activity).clipboardEnabled)
            compose.onNodeWithTag("pref_clipboard_switch").performScrollTo().performClick()
            compose.onNodeWithTag("pref_screenshot_access").performScrollTo().assertExists()
            capture("settings-smart-typing")
        } finally {
            settings.personalizedLearningEnabled = originalLearning
            settings.clipboardEnabled = originalClipboard
            profile.clear()
        }
    }

    @Test fun themeChoicesPersistAndRender() {
        val settings = KeyboardSettings(compose.activity)
        val original = settings.theme
        val originalDark = settings.isDarkMode
        try {
            compose.onNodeWithTag("nav_settings").performClick()
            listOf(KeyboardSettings.THEME_LIGHT, KeyboardSettings.THEME_DARK, KeyboardSettings.THEME_NIGHT).forEach { theme ->
                compose.onNodeWithTag("theme_chip_${theme.lowercase()}").performScrollTo().performClick()
                compose.waitUntil(5_000) { settings.dataStore.currentSnapshot().theme == theme }
                compose.onNodeWithTag("theme_chip_${theme.lowercase()}").assertIsSelected()
                compose.onNodeWithTag("app_page_3").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, -100_000f) }
                capture("settings-${theme.lowercase()}")
            }
        } finally { settings.theme = original; settings.isDarkMode = originalDark }
    }
}
