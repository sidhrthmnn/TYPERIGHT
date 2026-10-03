package com.example

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CandidatePipelineDeviceTest {
    @Test fun warmMultilingualRankingAndCacheStayFast() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(context); val previous = settings.personalizedLearningEnabled
        settings.personalizedLearningEnabled = false
        try {
            withContext(Dispatchers.Default) {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                val ranker = DictionaryManager.getInstance(context).correctionPipeline
                ranker.awaitDictionaries(listOf("hi", "ml", "fr"))
                val times = mutableListOf<Double>()
                repeat(50) {
                    for (word in listOf("teh", "recieve", "helllo", "thid", "njan", "varilla", "नमस्ते", "Siddharth", "lol", "office", "officil")) {
                        val start = System.nanoTime(); ranker.rank(word, listOf("njan", "tomorrow")); times.add((System.nanoTime() - start) / 1e6)
                    }
                }
                val sorted = times.sorted(); val p95 = sorted[(sorted.size * .95).toInt()]
                assertNull(ranker.rank("officil", listOf("njan", "tomorrow")).automatic)
                assertEquals("the", ranker.rank("teh").automatic)
                val cached = (0..999).map { val start = System.nanoTime(); ranker.cached("teh", emptyList(), null); (System.nanoTime() - start) / 1e6 }.sorted()[950]
                android.util.Log.i("PipelineDeviceTest", "p50=${sorted[sorted.size / 2]}ms p95=${p95}ms cachedP95=${cached}ms")
                assertTrue("Cached lookup p95 $cached ms", cached < 2.0)
                assertTrue("Background ranking p95 $p95 ms", p95 < 50.0)
            }
        } finally { settings.personalizedLearningEnabled = previous }
    }
    @Test fun cloudKeyIsEncryptedAndCanBeRemoved() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("typeright_cloud_credentials", Context.MODE_PRIVATE)
        // Do not overwrite a user's configured key in an instrumentation run.
        org.junit.Assume.assumeFalse(CloudPolishEngine.isConfigured(context))
        val testKey = "test-key-for-encryption-only-123456"
        try {
            CloudPolishEngine.saveKey(context, testKey)
            assertTrue(CloudPolishEngine.isConfigured(context))
            assertFalse(prefs.getString("key", "")!!.contains(testKey))
        } finally { CloudPolishEngine.saveKey(context, "") }
        assertFalse(CloudPolishEngine.isConfigured(context))
    }
}
