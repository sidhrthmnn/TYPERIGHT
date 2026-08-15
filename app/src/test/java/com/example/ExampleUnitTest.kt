package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(RobolectricTestRunner::class)
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testSpellingCorrectionLogic() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DictionaryManager(context)

    // 1. Correctly typed words should NOT trigger spelling correction
    assertFalse(manager.isSpellingCorrection("the", "the"))
    assertFalse(manager.isSpellingCorrection("the", "first"))
    assertFalse(manager.isSpellingCorrection("hello", "hello"))

    // 2. Typing incomplete prefixes (completions) should NOT trigger auto-correction
    assertFalse(manager.isSpellingCorrection("hel", "hello"))
    assertFalse(manager.isSpellingCorrection("in", "into"))

    // 3. Clear spelling typos should trigger spelling correction
    assertTrue(manager.isSpellingCorrection("thx", "the"))
    assertTrue(manager.isSpellingCorrection("helo", "hello"))

    // 4. Random gibberish with edit distance > 2 should NOT trigger spelling correction
    assertFalse(manager.isSpellingCorrection("xyzq", "the"))
    assertFalse(manager.isSpellingCorrection("xyzqw", "and"))
  }

  @Test
  fun testConfidenceScoringPipeline() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DictionaryManager(context)

    // 1. Proximity matching: 'helo' -> 'hello' (very close) should have high confidence
    val highConfidence = manager.calculateCorrectionConfidence("helo", "hello")
    assertTrue("High confidence should be greater than suggestion threshold", highConfidence >= DictionaryManager.SUGGESTION_THRESHOLD)

    // 2. Real-word protection: 'sit' -> 'sid' (both dictionary words/learned words) should have much lower confidence than 'helo' -> 'hello'
    val realWordConfidence = manager.calculateCorrectionConfidence("sit", "sid")
    assertTrue("Real word override should have significantly penalized confidence", realWordConfidence < DictionaryManager.SILENT_CORRECT_THRESHOLD)

    // 3. Bigram context bonus: typing a typo that fits context should boost score
    val confidenceWithoutContext = manager.calculateCorrectionConfidence("tha", "the", null)
    val confidenceWithContext = manager.calculateCorrectionConfidence("tha", "the", "is")
    assertTrue("N-gram context should boost correction confidence", confidenceWithContext > confidenceWithoutContext)
  }

  @Test
  fun testSwipeTypingDecoding() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = DictionaryManager(context)

    // Swipe path for "the": 't' -> 'h' -> 'e'
    val path = listOf(
      android.graphics.PointF(0.45f, 0.16f), // 't'
      android.graphics.PointF(0.60f, 0.50f), // 'h'
      android.graphics.PointF(0.25f, 0.16f)  // 'e'
    )

    val decodedWords = manager.decodeSwipePath(path)
    assertTrue("Should decode 'the' from the swipe path", decodedWords.contains("the"))
  }

  @Test
  fun testAiPolishFormatting() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test letter / greeting formatting
    val greetingResult = manager.polishTextStream("hey john how are you doing").last()
    assertEquals("Hey John,\n\nHow are you doing?", greetingResult)

    // 2. Test bullet points lists
    val listResult = manager.polishTextStream("first point confirm venue second point bring laptop").last()
    assertEquals("• Confirm venue.\n• Bring laptop.", listResult)

    // 3. Test numeric lists
    val numericResult = manager.polishTextStream("number one buy milk number two wash car").last()
    assertEquals("1. Buy milk.\n2. Wash car.", numericResult)

    // 4. Test paragraph splitting with transition words
    val transitionResult = manager.polishTextStream("I like apples by the way did you get my mail anyway let me know").last()
    assertEquals("I like apples.\n\nBy the way, did you get my mail?\n\nAnyway, let me know.", transitionResult)

    // 5. Test sign-offs
    val closingResult = manager.polishTextStream("hope to see you soon best regards sally").last()
    assertEquals("Hope to see you soon.\n\nBest regards,\nSally", closingResult)
  }

  @Test
  fun testWisprFlowFeatures() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test duplicate and stutter removal
    val stutterResult = manager.polishTextStream("the the the car was very very fast").last()
    assertEquals("The car was very fast.", stutterResult)

    // 2. Test filler words filtering
    val fillerResult = manager.polishTextStream("umm so yeah actually we should go").last()
    assertEquals("We should go.", fillerResult)

    // 3. Test self-correction resolution
    val selfCorrectionResult = manager.polishTextStream("let's meet at five no wait six").last()
    assertEquals("Let's meet at six.", selfCorrectionResult)

    // 4. Test local LLM symbol and emoji translation
    val symbolResult = manager.polishTextStream("I love heart symbol and smiley face arrow right").last()
    assertEquals("I love ❤️ and 😊 →.", symbolResult)
  }

  @Test
  fun testAiRephraseSuggestions() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // Test suggesting improvements / rephrase alternatives
    val suggestions = manager.suggestImprovements("hey i want to ask about this").last()
    
    println("DEBUG SUGGESTIONS: $suggestions")

    // We expect 3 distinct options
    assertEquals(3, suggestions.size)

    // The first option should be the Professional style, which replaces "hey i want to ask" with polite language and "about" with "regarding"
    val professionalOption = suggestions[0]
    assertTrue("Professional option should use polite/formal words: $professionalOption", 
      professionalOption.contains("regarding", ignoreCase = true) || professionalOption.contains("would like to", ignoreCase = true))

    // The second option should be the Casual style, which prefixes with "Hey!"
    val casualOption = suggestions[1]
    assertTrue("Casual option should look casual: $casualOption", casualOption.contains("Hey!", ignoreCase = true))

    // The third option should be Concise style, which keeps it brief
    val conciseOption = suggestions[2]
    assertNotNull(conciseOption)
  }

  @Test
  fun testTrigramPhraseCompletions() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val predictor = LocalGrammarSpellPredictor(context)

    // 1. Preceding 3 words: "let me know" -> suggest whole phrases
    val letMeKnowPhrases = predictor.predictPhraseCompletions(listOf("let", "me", "know"))
    assertTrue("Should suggest 'if you need anything' or 'if you have any questions'",
      letMeKnowPhrases.any { it.contains("if you need anything") || it.contains("if you have any questions") })

    // 2. Preceding 3 words: "looking forward to" -> suggest whole phrases
    val lookingForwardPhrases = predictor.predictPhraseCompletions(listOf("looking", "forward", "to"))
    assertTrue("Should suggest 'hearing from you' or 'meeting with you'",
      lookingForwardPhrases.any { it.contains("hearing from you") || it.contains("meeting with you") })

    // 3. Preceding 3 words: "thank you so" -> suggest whole phrases
    val thankYouSoPhrases = predictor.predictPhraseCompletions(listOf("thank", "you", "so"))
    assertTrue("Should suggest 'much for your help' or 'much for reaching out'",
      thankYouSoPhrases.any { it.contains("much for your help") || it.contains("much for reaching out") })

    // 4. Preceding 3 words: "hope you are" -> suggest whole phrases
    val hopeYouArePhrases = predictor.predictPhraseCompletions(listOf("hope", "you", "are"))
    assertTrue("Should suggest 'doing well and having a great day'",
      hopeYouArePhrases.any { it.contains("doing well") || it.contains("having a wonderful week") })

    // 5. Prefix filtering with 3 preceding words: "let me know" + prefix "if"
    val prefixFilteredPhrases = predictor.predictPhraseCompletions(listOf("let", "me", "know"), prefix = "if")
    assertTrue("All suggestions should start with 'if'",
      prefixFilteredPhrases.isNotEmpty() && prefixFilteredPhrases.all { it.lowercase().startsWith("if") })
  }

  @Test
  fun testGboardAutocorrectionAndPrediction() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dictionaryManager = DictionaryManager(context)
    val engine = GboardPredictionEngine(context)

    // 1. Transpositions (e.g. teh -> the, adn -> and, woudl -> would)
    val resTeh = engine.getGboardPredictionsAndCorrections("teh", emptyList(), null, dictionaryManager)
    assertTrue("Transposition 'teh' should autocorrect", resTeh.isCenterAutocorrecting)
    assertEquals("the", resTeh.centerCandidate.lowercase())

    val resAdn = engine.getGboardPredictionsAndCorrections("adn", emptyList(), null, dictionaryManager)
    assertTrue("Transposition 'adn' should autocorrect", resAdn.isCenterAutocorrecting)
    assertEquals("and", resAdn.centerCandidate.lowercase())

    // 2. QWERTY Neighbour Substitution (e.g. thid -> this, fir -> for)
    val resThid = engine.getGboardPredictionsAndCorrections("thid", emptyList(), null, dictionaryManager)
    assertTrue("Adjacent key typo 'thid' should autocorrect to 'this'", resThid.isCenterAutocorrecting)
    assertEquals("this", resThid.centerCandidate.lowercase())

    // 3. Contraction / Smart Apostrophe Restoration (e.g. dont -> don't, im -> I'm, cant -> can't)
    val resDont = engine.getGboardPredictionsAndCorrections("dont", emptyList(), null, dictionaryManager)
    assertTrue("Contraction 'dont' should autocorrect to 'don't'", resDont.isCenterAutocorrecting)
    assertEquals("don't", resDont.centerCandidate)

    val resIm = engine.getGboardPredictionsAndCorrections("im", emptyList(), null, dictionaryManager)
    assertTrue("Contraction 'im' should autocorrect to 'I'm'", resIm.isCenterAutocorrecting)
    assertEquals("I'm", resIm.centerCandidate)

    // 4. Repeated / Extra Letters Deletion (e.g. helllo -> hello)
    val resHelllo = engine.getGboardPredictionsAndCorrections("helllo", emptyList(), null, dictionaryManager)
    assertTrue("Repeated letter typo 'helllo' should autocorrect to 'hello'", resHelllo.isCenterAutocorrecting)
    assertEquals("hello", resHelllo.centerCandidate.lowercase())

    // 5. Missed Space Segmentation (e.g. goodmorning -> good morning)
    val resGoodMorning = engine.getGboardPredictionsAndCorrections("goodmorning", emptyList(), null, dictionaryManager)
    assertTrue("Run-on word 'goodmorning' should autocorrect to 'good morning'", resGoodMorning.isCenterAutocorrecting)
    assertEquals("good morning", resGoodMorning.centerCandidate.lowercase())

    // 6. Real-Word Preservation (e.g. correctly typed 'hello' should NOT autocorrect)
    val resHello = engine.getGboardPredictionsAndCorrections("hello", emptyList(), null, dictionaryManager)
    assertFalse("Valid word 'hello' should NOT be autocorrected", resHello.isCenterAutocorrecting)
    assertEquals("hello", resHello.centerCandidate.lowercase())

    // 7. Telemetry & Scoring Details
    assertNotNull(resTeh.debugTelemetry)
    assertTrue(resTeh.debugTelemetry.topCandidates.isNotEmpty())
    assertTrue(resTeh.debugTelemetry.decisionReason.isNotEmpty())
  }
}

