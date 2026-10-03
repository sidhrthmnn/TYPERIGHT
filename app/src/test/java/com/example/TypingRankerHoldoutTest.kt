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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TypingRankerHoldoutTest {
    @Test fun independentWordSplitAndCorrectTextControls() = runBlocking(Dispatchers.Default) {
        val owner = ApplicationProvider.getApplicationContext<Context>()
        val settings = KeyboardSettings(owner); settings.personalizedLearningEnabled = false
        val ranker = DictionaryManager.getInstance(owner).correctionPipeline
        ranker.awaitDictionaries()
        val data = JSONArray(javaClass.getResourceAsStream("/typing-generated.json")!!.bufferedReader().readText())
        val splits = mutableMapOf<String, MutableSet<String>>()
        for (i in 0 until data.length()) { val row=data.getJSONObject(i); splits.getOrPut(row.getString("split")) { mutableSetOf() }.add(row.getString("expected")) }
        assertTrue(splits["training"]!!.intersect(splits["holdout"]!!).isEmpty())
        assertTrue(splits["training"]!!.intersect(splits["calibration"]!!).isEmpty())
        val reports=JSONObject()
        for(split in listOf("calibration","holdout")) {
            val rows=(0 until data.length()).map { data.getJSONObject(it) }.filter { it.getString("split")==split }
            val records=JSONArray(); var changes=0; var hits=0; var unique=0; var uniqueHits=0; var falseChanges=0
            for(row in rows) {
                val typed=row.getString("typed"); val expected=row.getString("expected")
                val ranked=ranker.rank(typed); val output=ranked.automatic ?: typed
                val alternatives=ranked.candidates.filter { it.word != typed && CandidateOrigin.DICTIONARY in it.origins }
                val distance=CandidateRanker.editDistance(typed,expected)
                // Unambiguous means the intended word is the only closest dictionary spelling.
                // This label is independent of rank scores, confidence and the automatic decision.
                val unambiguous=alternatives.count { it.distance <= distance }==1 && alternatives.any { it.word==expected && it.distance==distance }
                if(unambiguous) { unique++; if(output==expected) uniqueHits++ }
                if(output!=typed) { changes++; if(output==expected) hits++ }
                if((ranker.rank(expected).automatic ?: expected)!=expected) falseChanges++
                records.put(JSONObject().put("typed",typed).put("expected",expected).put("output",output).put("suggestion",ranked.best?.word).put("confidence",ranked.confidence).put("unambiguous",unambiguous).put("language",ranked.language).put("eligible",ranked.autoEligible).put("scores",JSONArray(ranked.candidates.map { it.score })))
            }
            val precision=if(changes==0) 0.0 else hits.toDouble()/changes
            val recall=if(unique==0) 0.0 else uniqueHits.toDouble()/unique
            reports.put(split,JSONObject().put("cases",rows.size).put("automaticPrecision",precision).put("unambiguousCases",unique).put("automaticRecall",recall).put("falseCorrectionRate",falseChanges.toDouble()/rows.size).put("records",records))
        }
        File("build/reports/autocorrect").mkdirs()
        File("build/reports/autocorrect/independent.json").writeText(reports.toString(2))
        println("INDEPENDENT_METRICS "+reports.toString().take(500))
        // Targets are checked on the frozen holdout, never the training partition.
        val holdout=reports.getJSONObject("holdout")
        assertTrue("Precision ${holdout.getDouble("automaticPrecision")}",holdout.getDouble("automaticPrecision") >= .995)
        assertTrue("Recall ${holdout.getDouble("automaticRecall")}",holdout.getDouble("automaticRecall") >= .90)
        assertTrue("Correct controls",holdout.getDouble("falseCorrectionRate") <= .001)
    }
    @Test fun requiredMissesAndStrongContext() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        KeyboardSettings(owner).personalizedLearningEnabled=false
        val ranker=DictionaryManager.getInstance(owner).correctionPipeline; ranker.awaitDictionaries()
        val results=JSONObject()
        for((typed,expected) in mapOf("finaly" to "finally","libary" to "library","buisness" to "business","differnt" to "different")) {
            val r=ranker.rank(typed)
            results.put(typed,JSONObject().put("expected",expected).put("output",r.automatic ?: typed).put("candidates",JSONArray(r.candidates.map { "${it.word}:${it.score}:${it.distance}" })))
        }
        results.put("sea",ranker.rank("sea",listOf("i","will")).automatic ?: "sea")
        val their = ranker.rank("their",listOf("go","over"),following=listOf("now"))
        results.put("their",their.automatic ?: "their").put("theirDetails",their.toString())
        File("build/reports/autocorrect").mkdirs(); File("build/reports/autocorrect/required.json").writeText(results.toString(2))
        for((typed,expected) in mapOf("finaly" to "finally","libary" to "library","buisness" to "business","differnt" to "different")) assertEquals(typed,expected,results.getJSONObject(typed).getString("output"))
        assertEquals("see",results.getString("sea")); assertEquals("there",results.getString("their"))
        assertNull(ranker.rank("sea",listOf("the","blue")).automatic)
        assertNull(ranker.rank("there",listOf("we","met","with")).automatic)
    }
}
