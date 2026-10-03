package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class EnglishManglishTest {
    @Test fun latinMixedTypingCorrectsTyposAndPreservesVariantsAndSuffixes() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        KeyboardSettings(owner).personalizedLearningEnabled=false
        val dictionary=DictionaryManager.getInstance(owner); val ranker=dictionary.correctionPipeline
        ranker.awaitDictionaries()
        assertEquals("tomorrow",ranker.rank("tomorow",listOf("Njan")).automatic)
        assertEquals("varilla",ranker.rank("varillla",listOf("Njan","tomorrow","officeil")).automatic)
        for(word in listOf("nale","naale","sheri","shari","officeil","officil","meetinginu","meetingil","projectinte","helo","fone","Siddharth","Anjali","lol","idk","myVariable","user_name","user@email.com","42")) {
            assertNull(word,ranker.rank(word,listOf("njan","innu")).automatic)
        }
        val service=PredictiveTextSuggestionService(owner,dictionary)
        val mixed=service.fetchSuggestions(TextInputBufferState(activePrefix="mee",previousWords=listOf("njan","innu")))
        assertTrue(mixed.suggestionsList.isNotEmpty())
        assertTrue(mixed.suggestionsList.all { it.all { c -> c.code<128 } })
        assertEquals("see",ranker.rank("sea",listOf("njan","innu","i","will")).automatic)
        assertNull(ranker.rank("sea",listOf("the","blue")).automatic)
    }
    @Test fun polishProtectionUsesTheSameLanguageSpans() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        val dictionary=DictionaryManager.getInstance(owner); dictionary.correctionPipeline.awaitDictionaries()
        assertTrue(AiOutputValidator.isValid("send the mesage","Send the message",PolishMode.PROOFREAD))
        assertFalse(AiOutputValidator.isValid("njan nale meetinginu varilla","I will come to the meeting tomorrow",PolishMode.PROOFREAD))
        assertFalse(AiOutputValidator.isValid("njan shari ennu paranju","njan sheri ennu paranju",PolishMode.PROOFREAD))
        assertTrue(RomanizedMalayalamLexicon.preservesLiteral("meetinginu"))
        assertNotEquals(RomanizedMalayalamLexicon.get(owner).family("meetinginu"),RomanizedMalayalamLexicon.get(owner).family("meetingil"))
        assertEquals(RomanizedMalayalamLexicon.get(owner).family("nale"),RomanizedMalayalamLexicon.get(owner).family("naale"))
        assertFalse(MinimalContextEdit.isAllowed("njan shari ennu paranju","njan sheri ennu paranju",dictionary))
    }
    @Test fun languageVariantLearningPersistsAndUndoSuppressesImmediately() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>(); val settings=KeyboardSettings(owner)
        settings.personalizedLearningEnabled=true
        val dictionary=DictionaryManager.getInstance(owner); val ranker=dictionary.correctionPipeline; ranker.awaitDictionaries()
        ranker.learner.clear(); ranker.learner.awaitIdle()
        val prior=listOf("njan","innu")
        val original=ranker.prefix("sh",prior).candidates
        val chosen=original.last { it.word in setOf("shari","sheri") }
        repeat(24) { ranker.feedback("sh",chosen.word,prior,task=RankingTask.PREFIX); ranker.learner.awaitIdle() }
        val learned=ranker.prefix("sh",prior).candidates.first { it.word==chosen.word }
        assertTrue(learned.score>chosen.score)
        val restored=OnlineTypingLearner(owner); restored.awaitIdle()
        assertArrayEquals(ranker.learner.snapshot.weights,restored.snapshot.weights,0f)
        dictionary.suppressCorrection("varillla","varilla")
        assertNull(ranker.rank("varillla",prior).automatic)
        settings.personalizedLearningEnabled=false
        val before=ranker.learner.snapshot.weights.clone()
        ranker.feedback("sh","sheri",prior,task=RankingTask.PREFIX); ranker.learner.awaitIdle()
        assertArrayEquals(before,ranker.learner.snapshot.weights,0f)
        dictionary.resetAdaptiveLearning().join(); ranker.learner.awaitIdle()
        assertTrue(ranker.learner.snapshot.weights.all { it==0f })
        assertTrue(dictionary.personalProfile.candidates("",prior).isEmpty())
    }
    @Test fun legacyScriptSettingsNormalizeAndEnglishStaysLatin() {
        val owner=ApplicationProvider.getApplicationContext<Context>(); val settings=KeyboardSettings(owner)
        for(value in listOf("ml","Malayalam","മലയാളം")) {
            settings.sharedPreferences.edit().putString(KeyboardSettings.KEY_KEYBOARD_LANGUAGE,value).commit()
            assertEquals(KeyboardSettings.LANGUAGE_MALAYALAM_SCRIPT,settings.keyboardLanguage)
            assertTrue(settings.isMalayalamScriptMode)
        }
        settings.keyboardLanguage=KeyboardSettings.LANGUAGE_ENGLISH
        assertFalse(settings.isMalayalamScriptMode)
    }
    @Test fun manglishSwipeUsesVocabularyGeometryAndJointRanking() = runBlocking(Dispatchers.Default) {
        val owner=ApplicationProvider.getApplicationContext<Context>(); val dictionary=DictionaryManager.getInstance(owner)
        dictionary.correctionPipeline.awaitDictionaries()
        val path="njan".map { dictionary.gboardEngine.spatialModel.getKeyCentroid(it)!! }
        assertEquals("njan",dictionary.decodeSwipePath(path,previousWords=listOf("innu"),learningAllowed=false).first())
    }
}
