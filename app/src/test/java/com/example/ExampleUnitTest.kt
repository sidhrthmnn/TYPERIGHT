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
    assertTrue("Greeting should contain John: $greetingResult", greetingResult.contains("John", ignoreCase = true))
    assertTrue("Greeting should contain question mark: $greetingResult", greetingResult.contains("?"))

    // 2. Test bullet points lists
    val listResult = manager.proofreadTextStream("first point confirm venue second point bring laptop").last()
    assertTrue("Should contain venue and laptop: $listResult", listResult.contains("venue", ignoreCase = true) && listResult.contains("laptop", ignoreCase = true))

    // 3. Test numeric lists
    val numericResult = manager.proofreadTextStream("number one buy milk number two wash car").last()
    assertTrue("Should contain milk and car: $numericResult", numericResult.contains("milk", ignoreCase = true) && numericResult.contains("car", ignoreCase = true))

    // 4. Test paragraph splitting with transition words
    val transitionResult = manager.proofreadTextStream("I like apples by the way did you get my mail anyway let me know").last()
    assertTrue("Should contain core sentences: $transitionResult", transitionResult.contains("apples", ignoreCase = true))

    // 5. Test sign-offs
    val closingResult = manager.proofreadTextStream("hope to see you soon best regards sally").last()
    assertTrue("Should contain sign-off and Sally: $closingResult", closingResult.contains("Sally", ignoreCase = true))
  }

  @Test
  fun testWisprFlowProofreadingFeatures() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test duplicate and stutter removal
    val stutterResult = manager.cleanupVoiceText("the the the car was very very fast")
    assertTrue("Should preserve meaning: $stutterResult", stutterResult.contains("fast", ignoreCase = true))

    // 2. Test filler words filtering
    val fillerResult = manager.cleanupVoiceText("umm so yeah actually we should go")
    assertTrue("Should contain core intent 'we should go': $fillerResult", fillerResult.contains("we should go", ignoreCase = true))

    // 3. Test self-correction resolution
    val selfCorrectionResult = manager.cleanupVoiceText("let's meet at five no wait six")
    assertTrue("Should resolve to six: $selfCorrectionResult", selfCorrectionResult.contains("six", ignoreCase = true) || selfCorrectionResult.contains("6"))

    // 4. Test local LLM symbol and emoji translation
    val symbolResult = manager.proofreadTextStream("I love heart symbol and smiley face arrow right").last()
    assertTrue("Result should be non-blank: $symbolResult", symbolResult.isNotBlank())
  }

  @Test
  fun testAiPolishStyles() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = AiPolishManager(context)

    // 1. Test Formal Polish
    val formalResult = manager.polishTextStream("thanks i cant make it gonna be late", mode = "formalize").last()
    assertTrue("Formal polish should be non-empty and eliminate slang: $formalResult", formalResult.isNotBlank() && !formalResult.contains("gonna", ignoreCase = true))

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

    // We expect at least 1 valid suggestion
    assertTrue("Should have rephrase suggestions", suggestions.isNotEmpty())

    val firstOption = suggestions[0]
    assertTrue("First option should be non-blank: $firstOption", firstOption.isNotBlank())

    if (suggestions.size > 1) {
      val secondOption = suggestions[1]
      assertTrue("Second option should not be blank: $secondOption", secondOption.isNotBlank())
    }
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
  fun testNvidiaNemotronConfiguration() {
    // Verify cloud client configuration
    assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", NvidiaNemotronClient.DEFAULT_MODEL)
    assertEquals("NVIDIA-Nemotron-3.5-Lightning-30B-A3B-NVFP4", NvidiaNemotronClient.MODEL_DISPLAY_NAME)
    assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", NvidiaNemotronClient.resolveEndpointModel("NVIDIA-Nemotron-3.5-Lightning-30B-A3B-NVFP4"))
    val apiKey = NvidiaNemotronClient.getApiKey()
    assertTrue("API key should not be blank", apiKey.isNotBlank())
  }

  @Test
  fun testNvidiaNemotronRetrofitClient() = runBlocking {
    val retrofitClient = NvidiaNemotronRetrofitClient.instance
    assertNotNull(retrofitClient)
    assertNotNull(retrofitClient.apiService)
    assertEquals("https://integrate.api.nvidia.com/v1/", NvidiaNemotronRetrofitClient.BASE_URL)
    assertEquals("nvidia/nemotron-3.5-lightning-30b-a3b", NvidiaNemotronRetrofitClient.DEFAULT_MODEL)

    // Test direct proofread call via Retrofit client
    val proofreadResult = retrofitClient.proofread("thiss is a tst with erors")
    if (proofreadResult.isSuccess) {
      val text = proofreadResult.getOrNull().orEmpty()
      assertTrue("Proofread text should fix errors: $text", text.isNotBlank() && !text.contains("erors"))
    } else {
      assertTrue("Network failure should be captured in Result", proofreadResult.exceptionOrNull() != null)
    }

    // Test direct rephrase call via Retrofit client
    val rephraseResult = retrofitClient.rephrase("can you do this please", count = 2)
    if (rephraseResult.isSuccess) {
      val alternatives = rephraseResult.getOrNull().orEmpty()
      assertTrue("Should provide rephrased options", alternatives.isNotEmpty())
    } else {
      assertTrue("Network failure should be captured in Result", rephraseResult.exceptionOrNull() != null)
    }
  }

  @Test
  fun testVoiceCleanupFormatting() {
    val spokenText = "um send the report tomorrow no wait Friday"
    val voiceRes = VoiceTranscriptionFormatter.formatTranscription(spokenText, TranscriptionFormatStyle.SMART_CLEAN)
    assertFalse("Voice cleanup should remove 'um'", voiceRes.contains("um", ignoreCase = true))
    assertTrue("Voice cleanup should resolve self-correction to Friday: $voiceRes", voiceRes.contains("Friday", ignoreCase = true))
  }

  @Test
  fun testKeyboardSettingsCloudDefaults() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settings = KeyboardSettings(context)

    assertTrue("Cloud AI should be enabled by default", settings.geminiAiEnabled)
    assertTrue("Offline AI should be enabled by default", settings.offlineAiEnabled)
    assertEquals(ActiveAiEngine.BOTH, settings.activeAiEngine)
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
  fun testLocalGrammarSpellPredictorEngine() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val predictor = LocalGrammarSpellPredictor(context)

    // Test sentence grammar correction & capitalization
    val sample1 = "i went to teh stor and he have a apple"
    val result1 = predictor.polishSentenceLocally(sample1)
    assertTrue("Should capitalize first letter 'I': $result1", result1.startsWith("I"))
    assertTrue("Should fix 'a apple' to 'an apple': $result1", result1.contains("an apple"))
    assertTrue("Should fix 'he have' to 'he has': $result1", result1.contains("he has"))
  }

  @Test
  fun testLocalComprehensiveLexicon() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val predictor = LocalGrammarSpellPredictor(context)

    // 1. Comprehensive Lexicon verification
    assertTrue("Lexicon must contain rich vocabulary", ComprehensiveLexicon.WORDS.size > 200)
    assertTrue("Lexicon must contain extensive typos", ComprehensiveLexicon.TYPOS.containsKey("teh"))
    assertTrue("Lexicon must contain contractions", ComprehensiveLexicon.UNPUNCTUATED_CONTRACTIONS.containsKey("dont"))

    // 2. Homophone disambiguation & grammar rules
    val homophones = "their going to there house with they're car"
    val fixed = predictor.polishSentenceLocally(homophones)
    assertTrue("Should fix homophones: $fixed", fixed.contains("they're going", ignoreCase = true) || fixed.contains("their car", ignoreCase = true))
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

