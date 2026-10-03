package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CandidatePipelineTest {
    private lateinit var context: Context
    private lateinit var dictionary: DictionaryManager
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        KeyboardSettings(context).sharedPreferences.edit().clear().commit()
        PersonalTypingProfile.get(context).clear()
        dictionary = DictionaryManager.getInstance(context)
    }
    @Test fun benchmarkAndThresholdSweep() = runBlocking(Dispatchers.Default) {
        dictionary.correctionPipeline.awaitDictionaries(listOf("hi", "ml", "fr"))
        KeyboardSettings(context).personalizedLearningEnabled = false
        val fixtures = JSONArray(javaClass.getResourceAsStream("/autocorrect-cases.json")!!.bufferedReader().readText())
        val records = JSONArray(); val timings = mutableListOf<Double>()
        var changed = 0; var correct = 0; var typos = 0; var recalled = 0; var protected = 0; var falseChanges = 0; var suggested = 0
        for (i in 0 until fixtures.length()) {
            val case = fixtures.getJSONObject(i)
            val typed = case.getString("typed"); val expected = case.getString("expected")
            val prior = case.getJSONArray("context").let { words -> (0 until words.length()).map { words.getString(it) } }
            val start = System.nanoTime()
            val result = dictionary.correctionPipeline.rank(typed, prior)
            val elapsed = (System.nanoTime() - start) / 1e6
            timings.add(elapsed)
            val output = result.automatic ?: typed
            if (output != typed) { changed++; if (output == expected) correct++ }
            if (typed == expected) { protected++; if (output != typed) falseChanges++ }
            else if (case.getString("kind") != "contextual") { typos++; if (output == expected) recalled++ }
            if (typed != expected && result.suggestion == expected) suggested++
            records.put(JSONObject().put("typed", typed).put("expected", expected).put("output", output)
                .put("suggestion", result.suggestion).put("confidence", result.confidence).put("margin", result.margin)
                .put("tier", result.tier.name).put("language", result.language).put("split", case.getString("split"))
                .put("kind", case.getString("kind")).put("latencyMs", elapsed))
        }
        val precision = if (changed == 0) 0.0 else correct.toDouble() / changed
        val falseRate = falseChanges.toDouble() / protected
        val recall = recalled.toDouble() / typos
        val sweep = JSONArray()
        for (threshold in listOf(.85, .90, .94, .97, .99, .995)) {
            var eligible = 0; var right = 0
            for (i in 0 until records.length()) {
                val record = records.getJSONObject(i)
                // Sweep uses the calibration partition only; holdout is reported separately.
                if (record.getString("split") != "calibration" || record.getDouble("confidence") < threshold || record.getDouble("margin") < .4) continue
                if (record.getString("suggestion") != record.getString("typed")) {
                    eligible++; if (record.getString("suggestion") == record.getString("expected")) right++
                }
            }
            sweep.put(JSONObject().put("threshold", threshold).put("proposals", eligible)
                .put("precision", if (eligible == 0) 0.0 else right.toDouble() / eligible))
        }
        val cachedTimings = (0..999).map {
            val start = System.nanoTime(); dictionary.correctionPipeline.cached("teh", emptyList(), null)
            (System.nanoTime() - start) / 1e6
        }.sorted()
        val partitions = JSONObject()
        for (split in listOf("calibration", "holdout")) {
            val rows = (0 until records.length()).map { records.getJSONObject(it) }.filter { it.getString("split") == split }
            val auto = rows.filter { it.getString("output") != it.getString("typed") }
            val mistakes = rows.filter { it.getString("typed") != it.getString("expected") && it.getString("kind") != "contextual" }
            val controls = rows.filter { it.getString("typed") == it.getString("expected") }
            partitions.put(split, JSONObject().put("cases", rows.size).put("precision", if (auto.isEmpty()) 0.0 else auto.count { it.getString("output") == it.getString("expected") }.toDouble() / auto.size)
                .put("recall", mistakes.count { it.getString("output") == it.getString("expected") }.toDouble() / mistakes.size)
                .put("falseCorrectionRate", controls.count { it.getString("output") != it.getString("expected") }.toDouble() / controls.size))
        }
        val report = JSONObject().put("environment", "Robolectric JVM; warm dictionaries; worker ranking; no LLM")
            .put("partitions", partitions).put("cases", fixtures.length()).put("englishTypos", 131).put("automaticPrecision", precision)
            .put("falseCorrectionRate", falseRate).put("automaticRecall", recall).put("correctSuggestions", suggested)
            .put("workerP50Ms", timings.sorted()[timings.size / 2]).put("workerP95Ms", timings.sorted()[(timings.size * .95).toInt()])
            .put("cachedP95Ms", cachedTimings[950]).put("calibrationSweep", sweep).put("records", records)
        File("build/reports/autocorrect").mkdirs(); File("build/reports/autocorrect/metrics.json").writeText(report.toString(2))
        println("AUTOCORRECT_METRICS " + report.toString().substringBefore(",\"records\""))
        assertTrue("At least 100 English typos", typos >= 100)
        assertTrue("Precision $precision", precision >= .99)
        assertEquals("False correction rate $falseRate", 0.0, falseRate, 0.0)
        assertTrue("Conservative recall $recall", recall >= .55)
        assertTrue("Cached p95 must fit frame budget", cachedTimings[950] < 1.0)
        assertTrue("Warm worker ranking p95", timings.sorted()[(timings.size * .95).toInt()] < 50.0)
    }
    @Test fun rejectedCorrectionsPersistAndInvalidateCachedChoice() = runBlocking(Dispatchers.Default) {
        val ranker = dictionary.correctionPipeline
        assertEquals("the", ranker.rank("teh").automatic)
        dictionary.suppressCorrection("teh", "the")
        assertNull(ranker.cached("teh", emptyList(), null))
        assertNull(ranker.rank("teh").automatic)
        dictionary.personalProfile.flush()
        val restored = PersonalTypingProfile(context)
        assertTrue(restored.isTrusted("teh"))
        assertTrue(restored.rejectionPenalty("teh", "the") > 0)
    }
    @Test fun repeatedUnknownVocabularyIsTrustedAndFiveWordContextIsLearned() {
        val profile = dictionary.personalProfile
        repeat(3) { profile.observe("zorbify", listOf("we", "will", "send", "the", "new")) }
        assertTrue(profile.isTrusted("zorbify"))
        assertTrue(profile.boost("zorbify", listOf("we", "will", "send", "the", "new")) > profile.boost("zorbify", emptyList()))
        val model = dictionary.nGramModel
        repeat(5) { model.observeHigherOrder("zorbify", listOf("we", "will", "send", "the", "new")) }
        assertTrue(model.getProbability("zorbify", listOf("we", "will", "send", "the", "new")) > model.getProbability("zorbify", emptyList()))
    }
    @Test fun tapGeometryChangesPosteriorWithoutForcingARealWord() = runBlocking(Dispatchers.Default) {
        val ranker = dictionary.correctionPipeline
        ranker.awaitDictionaries()
        val spatial = dictionary.gboardEngine.spatialModel
        val helloTaps = "hello".map { spatial.getKeyCentroid(it)!! }
        val rawTaps = "hellp".map { spatial.getKeyCentroid(it)!! }
        val good = ranker.rank("hellp", listOf("say"), helloTaps).candidates.first { it.word == "hello" }
        val bad = ranker.rank("hellp", listOf("say"), rawTaps).candidates.first { it.word == "hello" }
        assertTrue(good.score > bad.score)
        assertNull(ranker.rank("sea", listOf("the"), "see".map { spatial.getKeyCentroid(it)!! }).automatic)
    }
    @Test fun contextualModelIsOptionalAndMinimalEditsProtectStyleAndMeaning() = runBlocking(Dispatchers.Default) {
        assertFalse(KeyboardSettings(context).contextualCorrectionEnabled)
        assertNull(NeuralCorrectionEngine.getInstance(context).contextualCorrection("I will sea you tomorrow.", "local-qwen3-4b"))
        assertTrue(MinimalContextEdit.isAllowed("I will sea you tomorrow.", "I will see you tomorrow.", dictionary))
        assertFalse(MinimalContextEdit.isAllowed("Njan tomorrow officil varilla", "I will not come tomorrow", dictionary))
        assertFalse(MinimalContextEdit.isAllowed("John is waiting outside.", "Joan is waiting outside.", dictionary))
        assertFalse(MinimalContextEdit.isAllowed("Send Niamh 55 dollars.", "Send Liam 55 dollars.", dictionary))
        assertFalse(MinimalContextEdit.isAllowed("I will not go tomorrow.", "I will go tomorrow.", dictionary))
        assertFalse(MinimalContextEdit.isAllowed("hi\nhello", "hi hello", dictionary))
        assertFalse(AiOutputValidator.isValid("Njan tomorrow officil varilla", "Njan tomorrow official varilla", PolishMode.PROOFREAD))
        assertTrue(AiOutputValidator.isValid("Njan tomorow officil varilla", "Njan tomorrow officil varilla", PolishMode.PROOFREAD))
        assertFalse(AiOutputValidator.isValid("main kal office jaunga", "I will go to the office tomorrow", PolishMode.PROOFREAD))
    }
    @Test fun learnedNativeVocabularyPhoneticsAndExplicitAcceptanceUseTheSameRanker() = runBlocking(Dispatchers.Default) {
        val ranker = dictionary.correctionPipeline
        ranker.awaitDictionaries(listOf("hi", "ml", "de"))
        assertTrue(MultilingualLexicon.get(context).frequency("weiß", "de") > 0)
        assertNull(ranker.rank("weiß", listOf("ich")).automatic)
        assertTrue(dictionary.isRecognizedInAnyLanguage("നാളെ"))
        val profile = dictionary.personalProfile
        repeat(3) { profile.observe("zorbify", emptyList()) }
        assertTrue(ranker.rank("zorbify").protected)
        profile.acceptPolish("zorbify", "hello", dictionary::isRecognizedInAnyLanguage)
        // Unrelated words cannot become learned replacements just because polish changed them.
        assertNull(profile.correction("zorbify", emptyList()))
        profile.reject("mesage", "message")
        assertNull(ranker.rank("mesage").automatic)
        profile.acceptPolish("mesage", "message", dictionary::isRecognizedInAnyLanguage)
        assertEquals("message", ranker.rank("mesage").automatic)
        assertEquals("message", ranker.cached("mesage", emptyList(), null)?.automatic)
        dictionary.learnWord("mesage", explicit = true)
        repeat(3) { profile.acceptPolish("send the mesage", "send the message", dictionary::isRecognizedInAnyLanguage) }
        assertEquals("message", ranker.rank("mesage", listOf("send", "the")).automatic)
        assertEquals("message", ranker.cached("mesage", listOf("send", "the"), null)?.automatic)
        assertTrue(ranker.rank("fone").candidates.any { it.word == "phone" && CandidateOrigin.PHONETIC in it.origins })
        assertTrue(ranker.nextWords(listOf("Njan", "nale")).any { it in MultilingualLexicon.romanizedMalayalam })
        assertTrue(ranker.nextWords(listOf("mujhe", "kal")).any { it in MultilingualLexicon.romanizedHindi })
    }
    @Test fun requestedModelsHavePinnedDownloadsAndQwenThinkingIsDisabled() {
        val requested = setOf("local-qwen3-0.6b", "local-qwen3-1.7b", "local-qwen3-4b", "local-gemma-3n-e2b", "local-gemma-4-e2b")
        val models = GgufModelCatalog.bundled(context).filter { it.id in requested }
        assertEquals(5, models.size)
        assertTrue(models.all { it.sha256.length == 64 && it.url.contains("/resolve/") && it.bytes > 400_000_000 })
        val prompt = PolishPromptBuilder.build("I will sea you", PolishMode.PROOFREAD, "qwen3")
        assertTrue(prompt.contains("/no_think")); assertTrue(prompt.endsWith("<think>\n\n</think>\n\n"))
        assertTrue(GgufModelCatalog.resolve(context, "local-gemma-3n-e2b").requiresConsent)
    }
}
