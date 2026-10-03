package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelSettingsUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun open() {
        compose.runOnUiThread {
            KeyboardSettings(compose.activity).setActiveAiEngine(ActiveAiEngine.OFFLINE)
            KeyboardSettings(compose.activity).aiModel = GgufModelCatalog.DEFAULT_ID
        }
        compose.onNodeWithTag("nav_ai").performClick()
    }
    @Test fun modelSelectionSurvivesRecreationAndAllChoicesAreAvailable() {
        open()
        compose.onNodeWithTag("model_select_local-grmr-1.5b").assertIsSelected()
        compose.onNodeWithTag("model_select_local-gemma-3-1b").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("local-gemma-3-1b", LocalGgufModel.selected(compose.activity).id) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("model_select_local-gemma-3-1b").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("model_select_local-gemma-4-e2b").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("local-gemma-4-e2b", LocalGgufModel.selected(compose.activity).id) }
        listOf("local-qwen3-0.6b", "local-qwen3-1.7b", "local-qwen3-4b", "local-gemma-3n-e2b").forEach { id ->
            compose.onNodeWithTag("model_select_$id").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(id, LocalGgufModel.selected(compose.activity).id) }
        }
        compose.onNodeWithTag("model_select_local-grmr-1.5b").performScrollTo().performClick()
        compose.onNodeWithTag("model_select_local-grmr-1.5b").performScrollTo()
        compose.waitForIdle()
        File(compose.activity.getExternalFilesDir("ui-checks"), "model-library.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun gemma3RequiresReviewBeforeAnyDownloadStarts() {
        open()
        compose.runOnUiThread {
            val model = GgufModelCatalog.resolve(compose.activity, "local-gemma-3-1b")
            LocalGgufModel.acceptTerms(compose.activity, false, model)
            compose.activity.getSharedPreferences(KeyboardSettings.PREFS_NAME, 0).edit().putBoolean("gemma_terms_accepted", false).commit()
        }
        compose.onNodeWithTag("model_download_local-gemma-3-1b").performScrollTo().performClick()
        compose.onNodeWithText("Gemma 3 1B terms").assertExists()
        compose.onNodeWithTag("model_accept_terms").assertExists()
        compose.runOnIdle { assertFalse(LocalGgufModel.state.value.busy) }
        compose.onNodeWithText("Cancel").performClick()
    }
    @Test fun invalidCustomUrlKeepsDialogOpenWithoutStartingDownload() {
        open()
        compose.onNodeWithTag("add_custom_model").performScrollTo().performClick()
        compose.onNodeWithTag("custom_model_name").performTextInput("My model")
        compose.onNodeWithTag("custom_model_url").performTextInput("http://example.com/model.gguf")
        compose.onNodeWithTag("custom_model_sha").performTextInput("a".repeat(64))
        compose.onNodeWithTag("custom_model_add").performClick()
        compose.onNodeWithText("Use a direct HTTPS model URL").assertExists()
        compose.runOnIdle { assertFalse(LocalGgufModel.state.value.busy) }
        compose.onNodeWithText("Cancel").performClick()
    }
}
