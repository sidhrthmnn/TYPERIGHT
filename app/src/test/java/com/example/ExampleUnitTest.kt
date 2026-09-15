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
  fun testAiProofreadingFormatting() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test letter / greeting formatting in proofreading
    val greetingResult = manager.proofreadTextStream("hey john how are you doing").last()
    assertEquals("Hey John,\n\nHow are you doing?", greetingResult)

    // 2. Test bullet points lists
    val listResult = manager.proofreadTextStream("first point confirm venue second point bring laptop").last()
    assertEquals("• Confirm venue.\n• Bring laptop.", listResult)

    // 3. Test numeric lists
    val numericResult = manager.proofreadTextStream("number one buy milk number two wash car").last()
    assertEquals("1. Buy milk.\n2. Wash car.", numericResult)

    // 4. Test paragraph splitting with transition words
    val transitionResult = manager.proofreadTextStream("I like apples by the way did you get my mail anyway let me know").last()
    assertEquals("I like apples.\n\nBy the way, did you get my mail?\n\nAnyway, let me know.", transitionResult)

    // 5. Test sign-offs
    val closingResult = manager.proofreadTextStream("hope to see you soon best regards sally").last()
    assertEquals("Hope to see you soon.\n\nBest regards,\nSally", closingResult)
  }

  @Test
  fun testWisprFlowProofreadingFeatures() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test duplicate and stutter removal
    val stutterResult = manager.proofreadTextStream("the the the car was very very fast").last()
    assertEquals("The car was very fast.", stutterResult)

    // 2. Test filler words filtering
    val fillerResult = manager.proofreadTextStream("umm so yeah actually we should go").last()
    assertEquals("We should go.", fillerResult)

    // 3. Test self-correction resolution
    val selfCorrectionResult = manager.proofreadTextStream("let's meet at five no wait six").last()
    assertEquals("Let's meet at six.", selfCorrectionResult)

    // 4. Test local LLM symbol and emoji translation
    val symbolResult = manager.proofreadTextStream("I love heart symbol and smiley face arrow right").last()
    assertEquals("I love ❤️ and 😊 →.", symbolResult)
  }

  @Test
  fun testAiPolishStyles() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test Formal Polish
    val formalResult = manager.polishTextStream("thanks i cant make it gonna be late", mode = "formalize").last()
    assertTrue("Formal polish should replace casual contractions", formalResult.contains("cannot", ignoreCase = true) || formalResult.contains("thank you", ignoreCase = true))

    // 2. Test Direct polishText
    val directFormal = manager.polishText("hey buddy", "formalize")
    assertNotNull(directFormal)
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
    assertTrue(resTeh.debugTelemetry?.topCandidates?.isNotEmpty() == true)
    assertTrue(resTeh.debugTelemetry?.decisionReason?.isNotEmpty() == true)
  }

  @Test
  fun testAiOutputValidator() {
    // 1. Commentary rejection
    assertFalse(AiOutputValidator.isValid("hello world", "Here is the corrected text: hello world", PolishMode.PROOFREAD))
    assertFalse(AiOutputValidator.isValid("hello world", "Sure! Here's your output:\nHello world", PolishMode.PROOFREAD))

    // 2. URL preservation
    assertTrue(AiOutputValidator.isValid("Check https://example.com/test", "Check https://example.com/test.", PolishMode.PROOFREAD))
    assertFalse(AiOutputValidator.isValid("Check https://example.com/test", "Check the website.", PolishMode.PROOFREAD))

    // 3. Email preservation
    assertTrue(AiOutputValidator.isValid("Email me at user@test.com", "Email me at user@test.com.", PolishMode.PROOFREAD))
    assertFalse(AiOutputValidator.isValid("Email me at user@test.com", "Email me at user@other.com.", PolishMode.PROOFREAD))

    // 4. Number preservation in PROOFREAD
    assertTrue(AiOutputValidator.isValid("Order 42 items for 10 dollars", "Order 42 items for $10.", PolishMode.PROOFREAD))
    assertFalse(AiOutputValidator.isValid("Order 42 items for 10 dollars", "Order 50 items for $10.", PolishMode.PROOFREAD))

    // 5. Sanitizer cleans markdown wrappers
    val sanitized = AiOutputValidator.sanitize("```\nHello world\n```", "Hello world")
    assertEquals("Hello world", sanitized)
  }

  @Test
  fun testNewAiProofreadingPipelineCases() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = LocalInferenceEngine.getInstance(context)

    // 1. "teh cat is here" -> "the cat is here"
    val res1 = engine.process("teh cat is here", PolishMode.PROOFREAD)
    assertTrue("Should fix 'teh' to 'the': ${res1.text}", res1.text.contains("the cat is here", ignoreCase = true))

    // 2. "I has went there" -> "I went there"
    val res2 = engine.process("I has went there", PolishMode.PROOFREAD)
    assertTrue("Should fix 'I has went there': ${res2.text}", res2.text.contains("I went there", ignoreCase = true) || res2.text.contains("I have gone there", ignoreCase = true))

    // 3. "she dont like it" -> "she doesn't like it"
    val res3 = engine.process("she dont like it", PolishMode.PROOFREAD)
    assertTrue("Should fix 'she dont' to 'she doesn't': ${res3.text}", res3.text.contains("she doesn't like it", ignoreCase = true))

    // 4. "your going to love this" -> "you're going to love this"
    val res4 = engine.process("your going to love this", PolishMode.PROOFREAD)
    assertTrue("Should fix 'your going to' to 'you're going to': ${res4.text}", res4.text.contains("you're going to love this", ignoreCase = true))

    // 5. "their going home" -> "they're going home"
    val res5 = engine.process("their going home", PolishMode.PROOFREAD)
    assertTrue("Should fix 'their going' to 'they're going': ${res5.text}", res5.text.contains("they're going home", ignoreCase = true))

    // 6. "i could of done it" -> "I could have done it"
    val res6 = engine.process("i could of done it", PolishMode.PROOFREAD)
    assertTrue("Should fix 'could of' to 'could have': ${res6.text}", res6.text.contains("could have done it", ignoreCase = true))

    // 7. Already correct sentence: "I'm going to the gym after work." -> preserved
    val res7 = engine.process("I'm going to the gym after work.", PolishMode.PROOFREAD)
    assertEquals("I'm going to the gym after work.", res7.text)

    // 8. Technical terms and URLs preserved
    val techText = "The API returns JSON from https://example.com"
    val res8 = engine.process(techText, PolishMode.PROOFREAD)
    assertTrue("URL and technical terms must be preserved verbatim", res8.text.contains("API") && res8.text.contains("JSON") && res8.text.contains("https://example.com"))
  }

  @Test
  fun testVoiceCleanupVsProofread() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = LocalInferenceEngine.getInstance(context)

    val spokenText = "um send the report tomorrow no wait Friday"

    // In VOICE_CLEANUP mode, filler "um" is removed and "tomorrow no wait Friday" resolves to "Friday"
    val voiceRes = engine.process(spokenText, PolishMode.VOICE_CLEANUP)
    assertFalse("Voice cleanup should remove 'um'", voiceRes.text.contains("um", ignoreCase = true))
    assertTrue("Voice cleanup should resolve self-correction to Friday: ${voiceRes.text}", voiceRes.text.contains("Friday", ignoreCase = true))
  }

  @Test
  fun testAiEngineTogglesArchitecture() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = LocalInferenceEngine.getInstance(context)
    val settings = engine.keyboardSettings

    // Save initial state
    val origOffline = settings.offlineAiEnabled
    val origGemini = settings.geminiAiEnabled

    try {
      // 1. Both engines DISABLED -> should return exact original text with no processing
      settings.offlineAiEnabled = false
      settings.geminiAiEnabled = false
      val disabledRes = engine.process("teh cat is here", PolishMode.PROOFREAD)
      assertEquals("teh cat is here", disabledRes.text)
      assertFalse(disabledRes.changed)
      assertEquals(AiSource.ORIGINAL, disabledRes.source)

      // 2. Offline AI engine ONLY (Gemini disabled) -> should apply on-device neural & rule corrections
      settings.offlineAiEnabled = true
      settings.geminiAiEnabled = false
      val offlineOnlyRes = engine.process("teh cat is here", PolishMode.PROOFREAD)
      assertTrue("Offline engine should fix 'teh': ${offlineOnlyRes.text}", offlineOnlyRes.text.contains("the cat is here", ignoreCase = true))
      assertTrue(offlineOnlyRes.changed)
      assertEquals(AiSource.LOCAL_MODEL, offlineOnlyRes.source)

      // 3. Both engines ENABLED (Hybrid mode) -> high confidence local corrections handled locally
      settings.offlineAiEnabled = true
      settings.geminiAiEnabled = true
      val hybridRes = engine.process("she dont like it", PolishMode.PROOFREAD)
      assertTrue("Hybrid pipeline should fix 'she dont': ${hybridRes.text}", hybridRes.text.contains("she doesn't like it", ignoreCase = true))
      assertTrue(hybridRes.changed)

    } finally {
      // Restore initial state
      settings.offlineAiEnabled = origOffline
      settings.geminiAiEnabled = origGemini
    }
  }

  @Test
  fun testActiveAiEngineIndicatorState() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = KeyboardSettings(context)

    val origOffline = settings.offlineAiEnabled
    val origGemini = settings.geminiAiEnabled

    try {
      // Both
      settings.setActiveAiEngine(ActiveAiEngine.BOTH)
      assertEquals(ActiveAiEngine.BOTH, settings.activeAiEngine)
      assertEquals("Both", settings.activeAiEngine.shortLabel)
      assertTrue(settings.offlineAiEnabled)
      assertTrue(settings.geminiAiEnabled)

      // Offline
      settings.setActiveAiEngine(ActiveAiEngine.OFFLINE)
      assertEquals(ActiveAiEngine.OFFLINE, settings.activeAiEngine)
      assertEquals("Offline", settings.activeAiEngine.shortLabel)
      assertTrue(settings.offlineAiEnabled)
      assertFalse(settings.geminiAiEnabled)

      // Online
      settings.setActiveAiEngine(ActiveAiEngine.ONLINE)
      assertEquals(ActiveAiEngine.ONLINE, settings.activeAiEngine)
      assertEquals("Online", settings.activeAiEngine.shortLabel)
      assertFalse(settings.offlineAiEnabled)
      assertTrue(settings.geminiAiEnabled)

      // None
      settings.setActiveAiEngine(ActiveAiEngine.NONE)
      assertEquals(ActiveAiEngine.NONE, settings.activeAiEngine)
      assertEquals("Off", settings.activeAiEngine.shortLabel)
      assertFalse(settings.offlineAiEnabled)
      assertFalse(settings.geminiAiEnabled)
    } finally {
      settings.offlineAiEnabled = origOffline
      settings.geminiAiEnabled = origGemini
    }
  }

  @Test
  fun testLocalRambleFormatter_SelfCorrectionsAndFillers() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val formatter = LocalRambleFormatter(context)

    // 1. Prompt generation validation
    val prompt = LocalRambleFormatter.buildExactOnDevicePrompt("um let's meet at 2 wait no 3")
    assertTrue(prompt.contains("<start_of_turn>user"))
    assertTrue(prompt.contains("Task: Convert this raw voice transcript into clean, finished text."))
    assertTrue(prompt.contains("Transcript: \"um let's meet at 2 wait no 3\""))
    assertTrue(prompt.contains("<start_of_turn>model"))

    // 2. Self correction: "Let's meet Tuesday—wait no, Wednesday at 2" -> "Let's meet Wednesday at 2"
    val result1 = formatter.runDeterministicLocalRambleEngine("Let's meet Tuesday—wait no, Wednesday at 2")
    assertTrue(result1.contains("Wednesday at 2"))
    assertFalse(result1.contains("wait no"))

    // 3. Vocal fillers removal
    val result2 = formatter.runDeterministicLocalRambleEngine("Um, uh, we should like basically launch tomorrow, you know")
    assertFalse(result2.contains("Um"))
    assertFalse(result2.contains("uh"))
    assertFalse(result2.contains("you know"))
    assertTrue(result2.contains("launch tomorrow"))

    // 4. Meta command parsing: "I'm running late send this to my boss formally"
    val result3 = formatter.runDeterministicLocalRambleEngine("I'm running late send this to my boss formally")
    assertTrue(result3.contains("Good morning") || result3.contains("apologize") || result3.contains("running behind schedule"))
    assertFalse(result3.contains("send this to my boss formally"))
  }

  @Test
  fun testOnDeviceProofreadingEngine() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val proofreader = OnDeviceProofreadEngine.getInstance(context)

    // Test Subject-Verb agreement & typo corrections
    val sample1 = "i went to teh stor and he have a apple"
    val result1 = proofreader.proofread(sample1)
    assertTrue("Should fix 'teh' to 'the': $result1", result1.contains("the"))
    assertTrue("Should fix 'he have' to 'he has': $result1", result1.contains("he has"))
    assertTrue("Should fix 'a apple' to 'an apple': $result1", result1.contains("an apple"))
    assertTrue("Should capitalize first letter 'I': $result1", result1.startsWith("I"))

    // Test Modal Agreement & Contraction Restoration
    val sample2 = "they was suppose to come but they could of told me"
    val result2 = proofreader.proofread(sample2)
    assertTrue("Should fix 'they was' to 'they were': $result2", result2.contains("They were") || result2.contains("they were"))
    assertTrue("Should fix 'could of' to 'could have': $result2", result2.contains("could have"))
    assertTrue("Should fix 'suppose to' to 'supposed to': $result2", result2.contains("supposed to"))

    // Test Common Typos and deduplication
    val sample3 = "this is definately the the best experiance"
    val result3 = proofreader.proofread(sample3)
    assertTrue("Should fix 'definately' to 'definitely': $result3", result3.contains("definitely"))
    assertTrue("Should remove repeated 'the the': $result3", result3.contains("the best") && !result3.contains("the the"))
    assertTrue("Should fix 'experiance' to 'experience': $result3", result3.contains("experience"))
  }

  @Test
  fun testWisprFlowEngineModes() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = WisprFlowEngine.getInstance(context)

    // 1. AUTO Mode: removes fillers, cleans stutters, and fixes self-corrections
    val autoInput = "um uh so yeah let's schedule the product sync for Tuesday—wait no, Wednesday at 2 PM"
    val autoRes = engine.processVoiceTranscript(autoInput, WisprFlowMode.AUTO)
    assertFalse("Should remove vocal fillers", autoRes.polishedText.contains("um", ignoreCase = true))
    assertFalse("Should remove vocal fillers", autoRes.polishedText.contains("uh", ignoreCase = true))
    assertTrue("Should resolve self-correction to Wednesday: ${autoRes.polishedText}", autoRes.polishedText.contains("Wednesday at 2 PM"))
    assertTrue(autoRes.removedFillersCount > 0)
    assertTrue(autoRes.selfCorrectionsCount > 0)

    // 2. SPOKEN PUNCTUATION & EMOJIS
    val punctInput = "can we meet question mark thumbs up emoji"
    val punctRes = engine.processVoiceTranscript(punctInput, WisprFlowMode.AUTO)
    assertTrue("Should replace 'question mark' with '?': ${punctRes.polishedText}", punctRes.polishedText.contains("?"))
    assertTrue("Should replace 'thumbs up emoji' with '👍': ${punctRes.polishedText}", punctRes.polishedText.contains("👍"))

    // 3. EXECUTIVE Mode: business phrasing
    val execInput = "i gotta send this draft cause we gonna launch soon"
    val execRes = engine.processVoiceTranscript(execInput, WisprFlowMode.EXECUTIVE)
    assertTrue("Should convert 'gotta' to 'need to': ${execRes.polishedText}", execRes.polishedText.contains("need to"))
    assertTrue("Should convert 'gonna' to 'going to': ${execRes.polishedText}", execRes.polishedText.contains("going to"))

    // 4. BULLETS Mode: action items
    val bulletInput = "first update the keyboard layout then test voice typing finally release update"
    val bulletRes = engine.processVoiceTranscript(bulletInput, WisprFlowMode.BULLETS)
    assertTrue("Should format bullets: ${bulletRes.polishedText}", bulletRes.polishedText.contains("•"))

    // 5. VERBATIM Mode: keeps words verbatim
    val verbInput = "um actually we want this exactly as said"
    val verbRes = engine.processVoiceTranscript(verbInput, WisprFlowMode.VERBATIM)
    assertTrue("Should keep original content: ${verbRes.polishedText}", verbRes.polishedText.contains("actually we want this exactly as said"))
  }

  @Test
  fun testOnDeviceNeuralPolishEngine() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val engine = OnDeviceNeuralPolishEngine.getInstance(context)

    // 1. Proofreading and typo fixes
    val typos = "teh app definately has to recieve an update"
    val proofread = engine.polish(typos, "Proofread")
    assertTrue("Should fix 'teh': ${proofread.polishedText}", proofread.polishedText.contains("the", ignoreCase = true))
    assertTrue("Should fix 'definately': ${proofread.polishedText}", proofread.polishedText.contains("definitely", ignoreCase = true))
    assertTrue("Should fix 'recieve': ${proofread.polishedText}", proofread.polishedText.contains("receive", ignoreCase = true))

    // 2. Homophone disambiguation
    val homophones = "their going to there house with they're car"
    val homophoneFix = engine.polish(homophones, "Proofread")
    assertTrue("Should fix 'their going': ${homophoneFix.polishedText}", homophoneFix.polishedText.contains("they're going", ignoreCase = true))
    assertTrue("Should fix 'they're car': ${homophoneFix.polishedText}", homophoneFix.polishedText.contains("their car", ignoreCase = true))

    // 3. Formal Tone
    val casualText = "thanks can you please check this asap"
    val formal = engine.polish(casualText, "Formal")
    assertTrue("Should use formal language: ${formal.polishedText}", formal.polishedText.contains("thank you", ignoreCase = true) || formal.polishedText.contains("earliest convenience", ignoreCase = true))

    // 4. Eloquent Tone
    val basicText = "good work and big progress"
    val eloquent = engine.polish(basicText, "Eloquent")
    assertTrue("Should use eloquent language: ${eloquent.polishedText}", eloquent.polishedText.isNotEmpty())

    // 5. Bullets Mode
    val listText = "first prepare presentation second test keyboard third deploy"
    val bullets = engine.polish(listText, "Bullets")
    assertTrue("Should format bullets: ${bullets.polishedText}", bullets.polishedText.contains("•"))

    // 6. Comprehensive Lexicon verification
    assertTrue("Lexicon must contain rich vocabulary", ComprehensiveLexicon.WORDS.size > 200)
    assertTrue("Lexicon must contain extensive typos", ComprehensiveLexicon.TYPOS.containsKey("teh"))
    assertTrue("Lexicon must contain contractions", ComprehensiveLexicon.UNPUNCTUATED_CONTRACTIONS.containsKey("dont"))
  }

  @Test
  fun testVoiceTranscriptionFormatter() {
    // 1. Spoken punctuation & formatting
    val rawWithPunctuation = "hello comma can we meet tomorrow question mark new line thanks exclamation mark"
    val formattedPunct = VoiceTranscriptionFormatter.formatTranscription(rawWithPunctuation, TranscriptionFormatStyle.SMART_CLEAN)
    assertTrue("Should convert 'comma' to ',': $formattedPunct", formattedPunct.contains(","))
    assertTrue("Should convert 'question mark' to '?': $formattedPunct", formattedPunct.contains("?"))
    assertTrue("Should convert 'new line' to newline: $formattedPunct", formattedPunct.contains("\n"))
    assertTrue("Should convert 'exclamation mark' to '!': $formattedPunct", formattedPunct.contains("!"))

    // 2. Self-correction resolution
    val selfCorrection = "let us meet Tuesday wait no Wednesday at three thirty pm"
    val formattedCorrection = VoiceTranscriptionFormatter.formatTranscription(selfCorrection, TranscriptionFormatStyle.SMART_CLEAN)
    assertTrue("Should resolve 'wait no Wednesday': $formattedCorrection", formattedCorrection.contains("Wednesday"))
    assertFalse("Should discard retracted 'Tuesday': $formattedCorrection", formattedCorrection.contains("Tuesday"))
    assertTrue("Should format time to 3:30 PM: $formattedCorrection", formattedCorrection.contains("3:30 PM"))

    // 3. Spoken currencies & numbers
    val rawMoney = "that costs twenty dollars and fifty cents or five euros"
    val formattedMoney = VoiceTranscriptionFormatter.formatTranscription(rawMoney, TranscriptionFormatStyle.SMART_CLEAN)
    assertTrue("Should format $ and €: $formattedMoney", formattedMoney.contains("$") || formattedMoney.contains("20"))

    // 4. Bullets formatting mode
    val rawList = "first gather requirements second write code third ship the app"
    val bullets = VoiceTranscriptionFormatter.formatTranscription(rawList, TranscriptionFormatStyle.BULLETS)
    assertTrue("Should format bullets with bullet points: $bullets", bullets.contains("•"))

    // 5. Numbered list formatting mode
    val numbered = VoiceTranscriptionFormatter.formatTranscription(rawList, TranscriptionFormatStyle.NUMBERED)
    assertTrue("Should format numbered list: $numbered", numbered.contains("1.") && numbered.contains("2."))

    // 6. Checklist mode
    val checklist = VoiceTranscriptionFormatter.formatTranscription(rawList, TranscriptionFormatStyle.CHECKLIST)
    assertTrue("Should format checklist with checkboxes: $checklist", checklist.contains("☐"))

    // 7. Email format mode
    val emailText = "hi team please review the pull request by tomorrow thanks john"
    val email = VoiceTranscriptionFormatter.formatTranscription(emailText, TranscriptionFormatStyle.EMAIL)
    assertTrue("Should format email with greetings and sign-off: $email", email.contains("Hi") && email.contains("\n"))
  }
}

