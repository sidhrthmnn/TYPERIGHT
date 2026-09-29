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
        assumeTrue("Download model before running this test", LocalGgufModel.isReady(context))
        val settings = KeyboardSettings(context)
        val previous = settings.activeAiEngine
        val accepted = LocalGgufModel.termsAccepted(context)
        LocalGgufModel.acceptTerms(context, true)
        try {
            settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
            val output = AiPolishBackend.generatePolish("i has a meeting at 5", PolishMode.PROOFREAD)
            assertNotNull(output)
            assertTrue(output!!.contains("5"))
            assertFalse(output.contains("i has"))
            assertTrue(AiOutputValidator.isValid("i has a meeting at 5", output, PolishMode.PROOFREAD))
        } finally { settings.setActiveAiEngine(previous); LocalGgufModel.acceptTerms(context, accepted) }
    }
}
