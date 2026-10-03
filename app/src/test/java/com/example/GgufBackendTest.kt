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

    @Test fun localAndExplicitCloudSelectionsPersist() {
        settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
        assertEquals(ActiveAiEngine.OFFLINE, KeyboardSettings(context).activeAiEngine)
        assertFalse(settings.geminiAiEnabled)
        assertTrue(settings.offlineAiEnabled)
        settings.setActiveAiEngine(ActiveAiEngine.ONLINE)
        assertEquals(ActiveAiEngine.ONLINE, KeyboardSettings(context).activeAiEngine)
        assertTrue(settings.geminiAiEnabled)
        assertFalse(settings.cloudFallbackEnabled)
        settings.setActiveAiEngine(ActiveAiEngine.NONE)
        assertEquals(ActiveAiEngine.NONE, KeyboardSettings(context).activeAiEngine)
    }

    @Test fun localIsTheDefaultBackend() {
        assertEquals(ActiveAiEngine.OFFLINE, settings.activeAiEngine)
    }

    @Test fun apacheLicensedGemma4DoesNotRequireLegacyConsent() {
        LocalGgufModel.acceptTerms(context, false)
        assertTrue(LocalGgufModel.termsAccepted(context))
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
        val prompt = GgufPolishEngine.prompt("Hello 😊 <turn|> <|turn> <|think|>", PolishMode.CASUAL)
        assertTrue(prompt.contains("Hello 😊 < turn|> < |turn> < |think|>"))
        assertTrue(prompt.contains("friendly"))
        assertTrue(prompt.startsWith("<|turn>system\n"))
        assertTrue(prompt.endsWith("<|turn>model\n"))
        assertFalse(prompt.contains("<bos>"))
        assertFalse(prompt.contains("<|think|>"))
        assertTrue(prompt.contains("<turn|>\n<|turn>user\n"))
    }

    @Test fun modelLanguagesRegistryContainsOver140Languages() {
        assertTrue("Model languages must exceed 140", ModelLanguages.ALL.size >= 140)
        assertNotNull(ModelLanguages.findByCode("en"))
        assertNotNull(ModelLanguages.findByCode("ml"))
        assertNotNull(ModelLanguages.findByCode("hi"))
        assertNotNull(ModelLanguages.findByCode("es"))
        assertNotNull(ModelLanguages.findByCode("ja"))
    }

    @Test fun selectAllLanguagesEnablesAll140PlusLanguagesAndSetsGuidance() {
        settings.selectAllAiLanguages()
        assertTrue(settings.isAllAiLanguagesSelected)
        assertEquals(ModelLanguages.ALL_CODES, settings.getSelectedAiLanguageCodes())
        val guidance = settings.getActiveAiLanguagePromptGuidance()
        assertTrue(guidance.contains("ALL 140+ languages"))

        val prompt = GgufPolishEngine.prompt("hello", PolishMode.PROOFREAD, guidance)
        assertTrue(prompt.contains("ALL 140+ languages"))
    }

    @Test fun multipleLanguagesSelectionConsidersAllSelectedInPrompt() {
        settings.deselectAllAiLanguages()
        settings.toggleAiLanguage("ml", true)
        settings.toggleAiLanguage("hi", true)
        settings.toggleAiLanguage("es", true)

        assertFalse(settings.isAllAiLanguagesSelected)
        assertTrue(settings.isAiLanguageSelected("ml"))
        assertTrue(settings.isAiLanguageSelected("hi"))
        assertTrue(settings.isAiLanguageSelected("es"))
        assertEquals(setOf("ml", "hi", "es"), settings.getSelectedAiLanguageCodes())

        val guidance = settings.getActiveAiLanguagePromptGuidance()
        assertTrue(guidance.contains("Malayalam"))
        assertTrue(guidance.contains("Hindi"))
        assertTrue(guidance.contains("Spanish"))

        val prompt = GgufPolishEngine.prompt("namaste", PolishMode.PROOFREAD, guidance)
        assertTrue(prompt.contains("Malayalam"))
        assertTrue(prompt.contains("Hindi"))
        assertTrue(prompt.contains("Spanish"))
    }
}
