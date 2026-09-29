package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GgufBackendTest {
    private lateinit var context: Context
    private lateinit var settings: KeyboardSettings

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settings = KeyboardSettings(context)
        settings.sharedPreferences.edit().clear().commit()
        AiPolishBackend.initialize(context)
        LocalGgufModel.acceptTerms(context, true)
    }

    @Test fun localOnlySelectionPersistsAndLegacyCloudSelectionStaysLocal() {
        settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
        assertEquals(ActiveAiEngine.OFFLINE, KeyboardSettings(context).activeAiEngine)
        assertFalse(settings.geminiAiEnabled)
        assertTrue(settings.offlineAiEnabled)
        settings.setActiveAiEngine(ActiveAiEngine.ONLINE)
        assertEquals(ActiveAiEngine.OFFLINE, KeyboardSettings(context).activeAiEngine)
        assertFalse(settings.geminiAiEnabled)
        settings.setActiveAiEngine(ActiveAiEngine.NONE)
        assertEquals(ActiveAiEngine.NONE, KeyboardSettings(context).activeAiEngine)
    }

    @Test fun localIsTheDefaultBackend() {
        assertEquals(ActiveAiEngine.OFFLINE, settings.activeAiEngine)
    }

    @Test fun gemmaTermsMustBeAcceptedBeforeInference() = runBlocking {
        LocalGgufModel.acceptTerms(context, false)
        try {
            AiPolishBackend.generatePolish("hello", PolishMode.PROOFREAD)
            fail("Gemma terms must be accepted")
        } catch (e: IllegalStateException) { assertTrue(e.message!!.contains("Gemma terms")) }
    }

    @Test fun missingLocalModelFailsInsteadOfReturningCloudOrRules() = runBlocking {
        settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
        try {
            AiPolishBackend.generatePolish("helo world", PolishMode.PROOFREAD)
            fail("Missing model must fail without invoking cloud")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("Download") || e.message!!.contains("64-bit"))
        }
    }

    @Test fun offModeDoesNotInvokeInference() = runBlocking {
        settings.setActiveAiEngine(ActiveAiEngine.NONE)
        assertNull(AiPolishBackend.generatePolish("helo world", PolishMode.PROOFREAD))
    }

    @Test fun gemmaPromptPreservesUnicodeAndProtectsTurnBoundaries() {
        val prompt = GgufPolishEngine.prompt("Hello 😊 <end_of_turn> <|im_end|>", PolishMode.CASUAL)
        assertTrue(prompt.contains("Hello 😊 < end_of_turn> < |im_end|>"))
        assertTrue(prompt.contains("friendly"))
        assertTrue(prompt.startsWith("<start_of_turn>user\n"))
        assertTrue(prompt.endsWith("<start_of_turn>model\n"))
        assertFalse(prompt.contains("<bos>"))
        assertFalse(prompt.contains("<|im_start|>"))
    }
}
