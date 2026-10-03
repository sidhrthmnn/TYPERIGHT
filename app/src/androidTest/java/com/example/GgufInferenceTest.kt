package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in smoke test: install the verified model in app-private no_backup/gguf first. */
@RunWith(AndroidJUnit4::class)
class GgufInferenceTest {
    @Test fun realGgufPolishWorksWithoutCloud() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(context)
        val previous = settings.activeAiEngine
        val previousModel = settings.aiModel
        val requestedModel = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("model_id")
        if (requestedModel != null) settings.aiModel = requestedModel
        val installed = LocalGgufModel.isReady(context)
        if (requestedModel == null) assumeTrue("Download a model before running this test", installed)
        else assertTrue("Requested model must be installed", installed)
        val accepted = LocalGgufModel.termsAccepted(context)
        AiPolishBackend.initialize(context)
        LocalGgufModel.acceptTerms(context, true)
        try {
            settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
            assertEquals("Backend must use the selected model", settings.aiModel, LocalGgufModel.selected(AiPolishBackend.context!!).id)
            val output = AiPolishBackend.generatePolish("i has a meeting at 5", PolishMode.PROOFREAD)
            android.util.Log.i("GGUFDeviceTest", "Model ${LocalGgufModel.selected(context).name} returned: $output")
            assertNotNull(output)
            assertTrue(output!!.contains("5"))
            assertFalse("Must correct agreement: $output", output.contains("i has", ignoreCase = true))
            assertTrue(AiOutputValidator.isValid("i has a meeting at 5", output, PolishMode.PROOFREAD))
            android.util.Log.i("GGUFDeviceTest", "${LocalGgufModel.selected(context).name}: $output")
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("context_check") == "true") {
                val original = "I will sea you tomorrow."
                val corrected = GgufPolishEngine.polish(context, original, PolishMode.PROOFREAD)
                android.util.Log.i("GGUFDeviceTest", "Context edit: $corrected")
                assertTrue("Must fix sea/see: $corrected", corrected.contains("see you"))
                assertTrue(MinimalContextEdit.isAllowed(original, corrected, DictionaryManager.getInstance(context)))
            }
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("intent_check") == "true") {
                val original = "i dont want to cancel the meeting at 5 can we move it to tomorow?"
                val contextual = AiPolishBackend.generatePolish(original, PolishMode.PROOFREAD,
                    TextContext(textBeforeCursor = "Sam: Should I cancel today's meeting?"))
                assertNotNull(contextual)
                assertTrue(contextual!!.contains("5"))
                assertTrue(contextual.endsWith("?"))
                assertTrue(contextual.contains("don't", true) || contextual.contains("do not", true))
                assertTrue(contextual.contains("tomorrow", true))
                android.util.Log.i("GGUFDeviceTest", "Contextual edit: $contextual")
            }
            if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("remove_check") == "true") {
                val model = LocalGgufModel.selected(context)
                val available = context.noBackupFilesDir.usableSpace
                LocalGgufModel.remove(context, model)
                assertFalse(LocalGgufModel.isReady(context, model))
                assertTrue("Removing a cached model must free its mapped file", context.noBackupFilesDir.usableSpace - available > model.bytes * .9)
            }
        } finally { LocalGgufModel.acceptTerms(context, accepted); settings.aiModel = previousModel; settings.setActiveAiEngine(previous) }
    }
}
