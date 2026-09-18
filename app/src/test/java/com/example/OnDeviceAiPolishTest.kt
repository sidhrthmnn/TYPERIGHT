package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnDeviceAiPolishTest {

    @Test
    fun testModelManifestMetadata() {
        val model = ModelManifest.QWEN3_1_7B
        assertEquals("Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", model.fileName)
        assertEquals("Offline AI — Qwen3 1.7B", model.displayName)
        assertEquals("qwen3-1.7b-litertlm", model.modelId)
        assertEquals(977184032L, model.expectedSizeBytes)
        assertEquals("2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b", model.expectedSha256)
        assertTrue(model.downloadUrl.startsWith("https://"))
        assertEquals("Apache 2.0", model.license)
    }

    @Test
    fun testPolishPromptFactoryFormatsCorrectly() {
        val promptProofread = PolishPromptFactory.getSystemInstruction(PolishMode.PROOFREAD)
        assertTrue(promptProofread.contains("Proofread"))
        assertTrue(promptProofread.contains("text editing engine"))

        val promptProf = PolishPromptFactory.getSystemInstruction(PolishMode.PROFESSIONAL)
        assertTrue(promptProf.contains("Professional"))

        val promptCasual = PolishPromptFactory.getSystemInstruction(PolishMode.CASUAL)
        assertTrue(promptCasual.contains("Friendly"))

        val promptShorten = PolishPromptFactory.getSystemInstruction(PolishMode.SHORTEN)
        assertTrue(promptShorten.contains("Shorten"))

        val userMessage = PolishPromptFactory.buildUserMessage("helo wrld")
        assertTrue(userMessage.contains("helo wrld"))
        assertTrue(userMessage.contains("Text to edit:"))
    }

    @Test
    fun testAiOutputValidatorSanitizesOutput() {
        // Strip think tags
        val rawWithThink = "<think>Let's fix spelling</think>Hello world"
        val cleaned = AiOutputValidator.sanitize(rawWithThink, "helo wrld")
        assertEquals("Hello world", cleaned)

        // Strip markdown backticks
        val rawWithCode = "```\nHello world\n```"
        val cleanedCode = AiOutputValidator.sanitize(rawWithCode, "helo wrld")
        assertEquals("Hello world", cleanedCode)

        // Strip surrounding quotes
        val rawWithQuotes = "\"Hello world\""
        val cleanedQuotes = AiOutputValidator.sanitize(rawWithQuotes, "helo wrld")
        assertEquals("Hello world", cleanedQuotes)

        // Strip commentary header lines
        val rawWithCommentary = "Here is the corrected text:\nHello world"
        val cleanedCommentary = AiOutputValidator.sanitize(rawWithCommentary, "helo wrld")
        assertEquals("Hello world", cleanedCommentary)
    }

    @Test
    fun testModelRepositoryPaths() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = ModelRepository.getInstance(context)
        val dir = repo.getModelDir()

        assertTrue(dir.absolutePath.endsWith("litert_models"))
        val modelFile = repo.getModelFile()
        assertEquals("Qwen3-1.7B_dynamic_wi4b32_afp32.litertlm", modelFile.name)

        // Should be NotInstalled initially in unit test environment
        if (!modelFile.exists()) {
            assertFalse(repo.isModelInstalled())
        }
    }

    @Test
    fun testKeyboardHeightShortEnforced() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(context)
        assertEquals(KeyboardSettings.HEIGHT_SHORT, settings.height)
    }
}
