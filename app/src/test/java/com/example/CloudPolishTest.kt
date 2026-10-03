package com.example
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudPolishTest {
    @Test fun noCredentialsOrFallbackAreEnabledByDefault() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(CloudPolishEngine.isConfigured(context))
        assertFalse(KeyboardSettings(context).cloudFallbackEnabled)
        assertEquals(ActiveAiEngine.OFFLINE, KeyboardSettings(context).activeAiEngine)
    }
    @Test fun requestAndResponsePreserveOriginalMeaning() {
        val input = "I dont want to cancel at 5?"
        val request = JSONObject(CloudPolishEngine.requestBody(input, PolishMode.PROOFREAD, null))
        assertTrue(request.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text").contains(input))
        val good = """{"candidates":[{"content":{"parts":[{"text":"I don't want to cancel at 5?"}]}}]}"""
        assertEquals("I don't want to cancel at 5?", CloudPolishEngine.parseOutput(good, input, PolishMode.PROOFREAD))
        try { CloudPolishEngine.parseOutput(good.replace("5?", "6?"), input, PolishMode.PROOFREAD); fail("Must preserve numbers") }
        catch (expected: IllegalStateException) { }
    }
}
