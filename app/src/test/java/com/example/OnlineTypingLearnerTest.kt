package com.example

import android.content.Context
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class OnlineTypingLearnerTest {
    private lateinit var owner: Context
    @Before fun setup() { owner=ApplicationProvider.getApplicationContext(); KeyboardSettings(owner).personalizedLearningEnabled=true }
    @Test fun explicitChoicesImproveRankingAndSurviveRestart() = runBlocking(Dispatchers.Default) {
        val dictionary=DictionaryManager.getInstance(owner); val ranker=dictionary.correctionPipeline
        ranker.awaitDictionaries(); ranker.learner.clear(); ranker.learner.awaitIdle()
        val prior=listOf("we","will")
        val before=ranker.prefix("ca",prior).candidates.filter { it.word != "ca" }
        val chosen=before.last().word
        val initial=before.indexOfFirst { it.word==chosen }
        repeat(30) { ranker.feedback("ca",chosen,prior,task=RankingTask.PREFIX); ranker.learner.awaitIdle() }
        val after=ranker.prefix("ca",prior).candidates
        assertTrue("$chosen should improve",after.indexOfFirst { it.word==chosen } < initial)
        val saved=ranker.learner.snapshot.weights
        assertTrue(saved.any { it != 0f })
        val restored=OnlineTypingLearner(owner); restored.awaitIdle()
        assertArrayEquals(saved,restored.snapshot.weights,0f)
        ranker.learner.clear(); ranker.learner.awaitIdle()
    }
    @Test fun disabledLearningAndResetStopEveryAdaptiveUpdate() = runBlocking(Dispatchers.Default) {
        val dictionary=DictionaryManager.getInstance(owner); val ranker=dictionary.correctionPipeline; ranker.awaitDictionaries()
        val repo=UserDictionaryRepository.getInstance(owner)
        repo.addCustomEntry("zorbify",dictionaryManager=dictionary)
        dictionary.personalProfile.observe("plugh",listOf("i","will"))
        ranker.feedback("teh","the",emptyList()); ranker.learner.awaitIdle()
        assertTrue(ranker.learner.snapshot.weights.any { it != 0f })
        KeyboardSettings(owner).personalizedLearningEnabled=false
        val previous=ranker.learner.snapshot.weights.clone()
        repeat(3) { ranker.feedback("teh","the",emptyList()) }
        ranker.learner.awaitIdle(); assertArrayEquals(previous,ranker.learner.snapshot.weights,0f)
        dictionary.resetAdaptiveLearning().join(); ranker.learner.awaitIdle()
        assertTrue(dictionary.isWordInUserDictionary("zorbify"))
        assertTrue(dictionary.personalProfile.candidates("",emptyList()).isEmpty())
        assertTrue(ranker.learner.snapshot.weights.all { it==0f })
        assertTrue(ranker.learner.snapshot.touch.isEmpty())
        assertTrue(repo.getOfflineNGramPredictions(2,"i").isEmpty())
    }
    @Test fun nullableAndSyntheticSamplesNeverTrainAndConfirmedOffsetsReachScoring() {
        val learner=OnlineTypingLearner(owner); learner.clear(); learner.awaitIdle()
        val spatial=SpatialKeyProximityModel()
        learner.confirmTouches("hello","hello",List(5) { null },OnlineTypingLearner.DEFAULT_LAYOUT,spatial)
        learner.awaitIdle(); assertTrue(learner.snapshot.touch.isEmpty())
        val taps="hello".map { spatial.getKeyCentroid(it)!!.let { p -> PointF(p.x+.03f,p.y-.02f) } }
        repeat(12) { learner.confirmTouches("hello","hello",taps,OnlineTypingLearner.DEFAULT_LAYOUT,spatial) }
        learner.awaitIdle()
        assertEquals(.03f,learner.snapshot.touch["${OnlineTypingLearner.DEFAULT_LAYOUT}:h"]!!.dx,.001f)
        val learned=learner.touchLikelihood("hello",taps,OnlineTypingLearner.DEFAULT_LAYOUT,spatial)
        val uncalibrated=learner.touchLikelihood("hello",taps,OnlineTypingLearner.DEFAULT_LAYOUT,spatial,OnlineTypingLearner.Snapshot(0,FloatArray(4096),emptyMap()))
        assertTrue(learned>uncalibrated)
        assertEquals(0f,learner.touchLikelihood("hello",taps,"azerty",spatial),0f)
        learner.clear(); learner.awaitIdle()
    }
}
