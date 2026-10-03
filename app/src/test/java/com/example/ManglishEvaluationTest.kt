package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

/** Frozen word-family and conversation-template splits, with current-main comparison. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManglishEvaluationTest {
    @Test fun shippedTrainingFeaturesMatchRuntimeScoringEvidence() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>(); KeyboardSettings(owner).personalizedLearningEnabled=false
        val ranker=DictionaryManager.getInstance(owner).correctionPipeline; ranker.awaitDictionaries()
        val rows=GZIPInputStream(File("../tools/data/runtime-ranking-training.jsonl.gz").inputStream()).bufferedReader().use { it.readLines() }
        var verified=0
        for(line in rows.filterIndexed { index,_ -> index%11==0 }) {
            val row=JSONObject(line); val context=row.getJSONArray("context")
            val result=ranker.rank(row.getString("typed"),(0 until context.length()).map { context.getString(it) })
            val chosen=result.candidates.firstOrNull { it.word==row.getString("expected") }
            assertNotNull("Runtime must generate the training winner: ${row.getString("typed")}",chosen)
            val vector=row.getJSONArray("chosen")
            assertArrayEquals(FloatArray(vector.length()) { vector.getDouble(it).toFloat() },chosen!!.baselineFeatures,.00001f)
            val names=row.getJSONArray("alternativeWords"); val alternatives=row.getJSONArray("alternatives")
            for(index in 0 until names.length()) {
                val candidate=result.candidates.firstOrNull { it.word==names.getString(index) } ?: continue
                val expected=alternatives.getJSONArray(index)
                assertArrayEquals(FloatArray(expected.length()) { expected.getDouble(it).toFloat() },candidate.baselineFeatures,.00001f)
                verified++
            }
        }
        assertTrue("Actual candidate competition must be verified",verified>500)
    }
    @Test fun exportRuntimeTrainingCompetition() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        KeyboardSettings(owner).personalizedLearningEnabled=false
        val ranker=DictionaryManager.getInstance(owner).correctionPipeline; ranker.awaitDictionaries()
        val data=JSONObject(javaClass.getResourceAsStream("/manglish-cases.json")!!.bufferedReader().readText()).getJSONArray("corrections")
        val output=File("build/reports/autocorrect/runtime-training.jsonl.gz"); output.parentFile!!.mkdirs()
        GZIPOutputStream(output.outputStream()).bufferedWriter().use { writer ->
            for(i in 0 until data.length()) {
                val row=data.getJSONObject(i)
                if(row.getString("split")!="training" || row.getString("typed")==row.getString("expected")) continue
                val typed=row.getString("typed"); val expected=row.getString("expected")
                val contexts=listOf(row.optJSONArray("context")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()) +
                    if(row.getString("language")=="en") listOf(listOf("njan","innu")) else emptyList()
                for(prior in contexts) {
                    val result=ranker.rank(typed,prior)
                    val winner=result.candidates.firstOrNull { it.word==expected } ?: continue
                    val competitors=result.candidates.filter { it.word!=expected && (winner.variantFamily==null || it.variantFamily!=winner.variantFamily) }.take(6)
                    if(competitors.isEmpty()) continue
                    writer.appendLine(JSONObject().put("typed",typed).put("expected",expected).put("family",row.getString("family"))
                        .put("language",row.getString("language")).put("context",JSONArray(prior)).put("chosen",JSONArray(winner.baselineFeatures!!.toList()))
                        .put("alternativeWords",JSONArray(competitors.map { it.word }))
                        .put("alternatives",JSONArray(competitors.map { JSONArray(it.baselineFeatures!!.toList()) })).toString())
                }
            }
            // Context supervision uses the actual runtime context origin and numeric vector.
            for((typed,expected,prior) in listOf(Triple("sea","see",listOf("i","will")),Triple("has","have",listOf("we")),Triple("were","was",listOf("he")))) {
                val result=ranker.rank(typed,prior); val winner=result.candidates.firstOrNull { it.word==expected } ?: continue
                writer.appendLine(JSONObject().put("typed",typed).put("expected",expected).put("family","context:$typed").put("language","en")
                    .put("context",JSONArray(prior)).put("chosen",JSONArray(winner.baselineFeatures!!.toList()))
                    .put("alternativeWords",JSONArray(result.candidates.filter { it.word!=expected }.map { it.word }))
                    .put("alternatives",JSONArray(result.candidates.filter { it.word!=expected }.map { JSONArray(it.baselineFeatures!!.toList()) })).toString())
            }
        }
    }
    @Test fun independentBilingualEvaluation() = runBlocking(Dispatchers.Default) {
        val owner = ApplicationProvider.getApplicationContext<Context>()
        KeyboardSettings(owner).personalizedLearningEnabled = false
        val ranker = DictionaryManager.getInstance(owner).correctionPipeline
        ranker.awaitDictionaries()
        val data = JSONObject(javaClass.getResourceAsStream("/manglish-cases.json")!!.bufferedReader().readText())
        val rows = data.getJSONArray("corrections")
        val report = JSONObject(); val detailed = JSONArray(); val calibration = JSONArray()
        val families=mutableMapOf<String,MutableSet<String>>()
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            if(row.getString("typed")!=row.getString("expected")) families.getOrPut(row.getString("split")) { mutableSetOf() }.add(row.getString("language")+":"+row.getString("family"))
        }
        for(a in families.keys) for(b in families.keys) if(a!=b) assertTrue("Variant families must be disjoint",families[a]!!.intersect(families[b]!!).isEmpty())
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i)
            if(row.getString("split")!="calibration") continue
            val prior=row.optJSONArray("context")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
            val ranked=ranker.rank(row.getString("typed"),prior)
            calibration.put(JSONObject().put("typed",row.getString("typed")).put("expected",row.getString("expected"))
                .put("language",row.getString("language")).put("suggestion",ranked.best?.word).put("eligible",ranked.autoEligible)
                .put("scores",JSONArray(ranked.candidates.map { it.score })))
        }
        for (lang in listOf("en", "ml-Latn")) {
            var total = 0; var changed = 0; var hits = 0; var protected = 0; var protectedChanges = 0
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (row.getString("language") != lang || row.getString("split") != "holdout") continue
                val typed = row.getString("typed"); val expected = row.getString("expected")
                val context = row.optJSONArray("context")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val result = ranker.rank(typed, context); val output = result.automatic ?: typed
                if (typed == expected) { protected++; if (output != typed) protectedChanges++ }
                else { total++; if(output != typed) changed++; if(output == expected) hits++ }
                detailed.put(JSONObject().put("typed",typed).put("expected",expected).put("output",output)
                    .put("language",lang).put("detected",result.language).put("tier",result.tier).put("confidence",result.confidence)
                    .put("candidates",JSONArray(result.candidates.take(5).map { "${it.word}:${it.score}" })))
            }
            report.put(lang,JSONObject().put("typos",total).put("automaticChanges",changed).put("hits",hits)
                .put("recall",hits.toDouble()/maxOf(1,total)).put("precision",hits.toDouble()/maxOf(1,changed))
                .put("protected",protected).put("protectedChanges",protectedChanges))
        }
        val prediction=data.getJSONArray("predictions"); var hits=0
        for (i in 0 until prediction.length()) {
            val row=prediction.getJSONObject(i); val words=row.getJSONArray("context")
            if(row.getString("expected") in ranker.nextWords((0 until words.length()).map { words.getString(it) })) hits++
        }
        report.put("prediction",JSONObject().put("cases",prediction.length()).put("top3Hits",hits).put("top3Recall",hits.toDouble()/prediction.length()))
        File("build/reports/autocorrect").mkdirs()
        val baseline=System.getenv("TYPERIGHT_BASELINE") == "1"
        File("build/reports/autocorrect/${if(baseline) "manglish-baseline" else "manglish"}.json").writeText(report.toString(2))
        File("build/reports/autocorrect/manglish-details.json").writeText(detailed.toString(2))
        File("build/reports/autocorrect/bilingual-calibration.json").writeText(calibration.toString(2))
        println("BILINGUAL_METRICS $report")
        if (!baseline) {
            assertEquals("Valid Manglish words",0,report.getJSONObject("ml-Latn").getInt("protectedChanges"))
            assertTrue("Manglish typo coverage",report.getJSONObject("ml-Latn").getInt("typos") >= 200)
            assertTrue("Protected controls",report.getJSONObject("ml-Latn").getInt("protected") >= 1000)
            assertTrue("Manglish automatic precision",report.getJSONObject("ml-Latn").getDouble("precision")>=.995)
            assertTrue("English automatic precision",report.getJSONObject("en").getDouble("precision")>=.995)
            // Frozen measurements from main before this change, on these same fixtures.
            assertTrue("Manglish recall improves",report.getJSONObject("ml-Latn").getDouble("recall")>.00884)
            assertTrue("Mixed next-word top-three accuracy improves",report.getJSONObject("prediction").getDouble("top3Recall")>.08738)
        }
    }
}
