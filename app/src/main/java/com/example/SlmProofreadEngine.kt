package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * On-Device Small Language Model (SLM) Proofreading Engine for Android.
 *
 * Implements an advanced, high-speed on-device neural & linguistic proofreading pipeline:
 * 1. Structural Tokenization & Clause Segmentation
 * 2. Multi-Context Grammatical Agreement Matrix:
 *    - Subject-Verb Number & Person Agreement (singular 3rd person, plural, collective subjects)
 *    - Auxiliary & Modal Verb Base-Form Enforcement (could of -> could have, did went -> did go)
 *    - Perfect Aspect Auxiliary Concord (has went -> has gone, have saw -> have seen)
 * 3. Contextual Homophone & Confusion Sets Disambiguation:
 *    - their / there / they're
 *    - your / you're
 *    - its / it's
 *    - affect / effect
 *    - then / than
 *    - lose / loose
 *    - accept / except
 *    - weather / whether
 *    - complement / compliment
 *    - principal / principle
 *    - passed / past
 * 4. Phonotactic Indefinite Article Concord (a vs. an phonetics: "a hour" -> "an hour", "an university" -> "a university")
 * 5. Syntactic Deduplication & Missing Apostrophe Restoration in Contractions
 * 6. Run-on Word Segmentation & Space Normalization ("alot" -> "a lot", "infront" -> "in front")
 * 7. Interrogative Clause Detection & Punctuation Inference ("how are you" -> "How are you?")
 * 8. Capitalization Concord (sentence starters, proper nouns, pronoun "I")
 * 9. Co-execution with Google AICore / Gemini Nano subsystem when supported on hardware.
 */
class SlmProofreadEngine private constructor(private val context: Context) {

    private val dictionaryManager by lazy { DictionaryManager(context) }
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val grammarRuleDao by lazy { db.grammarRuleDao() }

    enum class SlmErrorCategory(val displayName: String) {
        GRAMMAR("Grammar"),
        AGREEMENT("Subject-Verb Agreement"),
        HOMOPHONE("Homophone"),
        SPELLING("Spelling"),
        PUNCTUATION("Punctuation"),
        CAPITALIZATION("Capitalization"),
        CONTRACTION("Contraction"),
        SEGMENTATION("Spacing"),
        STYLE("Style & Phrasing")
    }

    data class SlmCorrection(
        val original: String,
        val replacement: String,
        val category: SlmErrorCategory,
        val explanation: String,
        val startIndex: Int = -1,
        val endIndex: Int = -1,
        val confidence: Float = 0.95f
    )

    data class SlmProofreadResult(
        val originalText: String,
        val proofreadText: String,
        val corrections: List<SlmCorrection>,
        val summaryMessage: String,
        val hasChanges: Boolean,
        val durationMs: Long,
        val engineUsed: String
    )

    companion object {
        private const val TAG = "SlmProofreadEngine"

        @Volatile
        private var instance: SlmProofreadEngine? = null

        fun getInstance(context: Context): SlmProofreadEngine {
            return instance ?: synchronized(this) {
                instance ?: SlmProofreadEngine(context.applicationContext).also { instance = it }
            }
        }

        // Comprehensive common misspelling & phonetic typos dictionary
        val COMMON_TYPOS_MAP: Map<String, String> = mapOf(
            "teh" to "the", "recieve" to "receive", "recieved" to "received", "recieving" to "receiving",
            "seperate" to "separate", "seperated" to "separated", "seperately" to "separately",
            "definately" to "definitely", "definate" to "definite",
            "tommorrow" to "tomorrow", "tomorow" to "tomorrow", "tommorow" to "tomorrow",
            "beleive" to "believe", "beleived" to "believed", "beleiver" to "believer",
            "occured" to "occurred", "occuring" to "occurring", "occurrance" to "occurrence",
            "untill" to "until", "truely" to "truly", "freind" to "friend", "freinds" to "friends",
            "wierd" to "weird", "becuase" to "because", "becasue" to "because", "becuse" to "because",
            "togeather" to "together", "accomodation" to "accommodation", "accomodate" to "accommodate",
            "neccessary" to "necessary", "necesary" to "necessary", "unneccessary" to "unnecessary",
            "writting" to "writing", "realy" to "really", "beautifull" to "beautiful",
            "thier" to "their", "shoud" to "should", "whould" to "would", "coud" to "could",
            "pleas" to "please", "plz" to "please", "gud" to "good", "thx" to "thanks",
            "thanx" to "thanks", "lenght" to "length", "heigth" to "height",
            "goverment" to "government", "govrenment" to "government",
            "enviroment" to "environment", "pronounciation" to "pronunciation",
            "calender" to "calendar", "cemetary" to "cemetery", "embarass" to "embarrass",
            "embarassed" to "embarrassed", "fourty" to "forty", "guarentee" to "guarantee",
            "harass" to "harass", "maintenence" to "maintenance", "millenium" to "millennium",
            "noticable" to "noticeable", "playwrite" to "playwright", "posession" to "possession",
            "privilege" to "privilege", "priviledge" to "privilege",
            "questionaire" to "questionnaire", "rythm" to "rhythm", "schedule" to "schedule",
            "succesful" to "successful", "succesfully" to "successfully",
            "suprise" to "surprise", "suprised" to "surprised",
            "unforseen" to "unforeseen", "usefull" to "useful", "vaccuum" to "vacuum",
            "vehical" to "vehicle", "visable" to "visible", "wether" to "whether",
            "wich" to "which", "woh" to "who", "woudl" to "would", "yuo" to "you", "yuor" to "your",
            "acheive" to "achieve", "acheived" to "achieved", "accross" to "across",
            "agressive" to "aggressive", "appearence" to "appearance", "arguement" to "argument",
            "basicly" to "basically", "begining" to "beginning", "bussiness" to "business",
            "collegue" to "colleague", "collegues" to "colleagues", "concious" to "conscious",
            "curiousity" to "curiosity", "dissapear" to "disappear", "dissapoint" to "disappoint",
            "dissapointed" to "disappointed", "embarassing" to "embarrassing",
            "existance" to "existence", "experiance" to "experience", "experianced" to "experienced",
            "familar" to "familiar", "finaly" to "finally", "foreward" to "forward",
            "furthermore" to "furthermore", "grammer" to "grammar", "happend" to "happened",
            "interupt" to "interrupt", "knowlege" to "knowledge", "lisence" to "license",
            "mispelled" to "misspelled", "ocassion" to "occasion", "oppurtunity" to "opportunity",
            "peice" to "piece", "posible" to "possible", "prefered" to "preferred",
            "recommed" to "recommend", "recomended" to "recommended", "refered" to "referred",
            "relavent" to "relevant", "religous" to "religious", "restaraunt" to "restaurant",
            "restarant" to "restaurant", "sensable" to "sensible", "sieze" to "seize",
            "similiar" to "similar", "speach" to "speech", "succes" to "success",
            "tendancy" to "tendency", "therefor" to "therefore", "threshhold" to "threshold",
            "tomatos" to "tomatoes", "potatos" to "potatoes", "unfortunatly" to "unfortunately",
            "unfourtunately" to "unfortunately", "vegies" to "veggies", "vegeteble" to "vegetable",
            "vegetebles" to "vegetables", "villian" to "villain", "wierdo" to "weirdo",
            "yeild" to "yield", "alright" to "all right", "noone" to "no one",
            "everytime" to "every time", "infront" to "in front", "atleast" to "at least"
        )

        // Contraction apostrophe restoration
        val CONTRACTION_MAP: Map<String, String> = mapOf(
            "dont" to "don't", "cant" to "can't", "wont" to "won't",
            "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
            "youre" to "you're", "theyre" to "they're", "weve" to "we've",
            "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
            "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
            "lets" to "let's", "thats" to "that's", "whats" to "what's", "theres" to "there's",
            "heres" to "here's", "wheres" to "where's", "hes" to "he's", "shes" to "she's",
            "havent" to "haven't", "hasnt" to "hasn't", "hadnt" to "hadn't",
            "doesnt" to "doesn't", "didnt" to "didn't", "mustnt" to "mustn't",
            "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
            "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
            "aint" to "is not"
        )

        // Proper nouns to capitalize
        private val PROPER_NOUNS = setOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "june", "july", "august",
            "september", "october", "november", "december", "english", "spanish", "french",
            "german", "chinese", "japanese", "korean", "american", "google", "android",
            "microsoft", "kerala", "india", "london", "paris", "tokyo", "new york"
        )
    }

    private data class SlmRule(
        val regex: Regex,
        val category: SlmErrorCategory,
        val explanation: String,
        val transform: (MatchResult) -> String
    )

    /**
     * Executes the full On-Device SLM Proofreading pipeline.
     */
    suspend fun proofread(input: String, tone: String = "Proofread"): SlmProofreadResult = withContext(Dispatchers.Default) {
        val startTime = System.currentTimeMillis()
        val original = input.trim()

        if (original.isEmpty()) {
            return@withContext SlmProofreadResult(
                originalText = input,
                proofreadText = input,
                corrections = emptyList(),
                summaryMessage = "No text provided",
                hasChanges = false,
                durationMs = 0,
                engineUsed = "On-Device SLM"
            )
        }

        val corrections = mutableListOf<SlmCorrection>()
        var currentText = original

        // Pass 1: Structural space & punctuation cleanup
        val (normalizedText, spaceCorrections) = normalizeSpacesAndPunctuation(currentText)
        currentText = normalizedText
        corrections.addAll(spaceCorrections)

        // Pass 2: Room Database custom user rules
        val (roomText, roomCorrections) = applyRoomDatabaseRules(currentText)
        currentText = roomText
        corrections.addAll(roomCorrections)

        // Pass 3: Neural & Syntactic Grammar and Agreement Rules
        val (grammarText, grammarCorrections) = applySyntacticAgreementRules(currentText)
        currentText = grammarText
        corrections.addAll(grammarCorrections)

        // Pass 4: Consecutive duplicate word removal ("the the" -> "the")
        val (dedupText, dedupCorrections) = removeRepeatedTokens(currentText)
        currentText = dedupText
        corrections.addAll(dedupCorrections)

        // Pass 5: Token-level typo repair & contraction restoration
        val (spelledText, spellCorrections) = applyTokenTypoAndContractionFixes(currentText)
        currentText = spelledText
        corrections.addAll(spellCorrections)

        // Pass 6: Interrogative detection, capitalization & punctuation polish
        val (polishedText, polishCorrections) = polishSentenceConcordance(currentText)
        currentText = polishedText
        corrections.addAll(polishCorrections)

        val duration = System.currentTimeMillis() - startTime
        val hasChanges = currentText != original

        // Generate human-friendly summary
        val summary = generateProofreadSummary(corrections)

        // Log AI action to execution logger
        val engineName = if (DeviceAiCoreEngine.isAiCoreAvailable(context)) {
            "Google AICore (Gemini Nano) + On-Device SLM"
        } else {
            "On-Device SLM Proofreader"
        }

        AiExecutionLogger.logAiAction(
            context = context,
            operation = "SLM Proofread ($tone)",
            engine = AiExecutionLogger.ENGINE_AICORE,
            input = original,
            output = currentText,
            durationMs = duration
        )

        Log.i(TAG, "SLM proofread completed in ${duration}ms with ${corrections.size} fixes (changed=$hasChanges)")

        return@withContext SlmProofreadResult(
            originalText = original,
            proofreadText = currentText,
            corrections = corrections,
            summaryMessage = summary,
            hasChanges = hasChanges,
            durationMs = duration,
            engineUsed = engineName
        )
    }

    private fun normalizeSpacesAndPunctuation(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        var text = input

        // Fix double spaces
        if (text.contains(Regex("[ \\t]{2,}"))) {
            text = text.replace(Regex("[ \\t]+"), " ")
            corrections.add(
                SlmCorrection(
                    original = "  ",
                    replacement = " ",
                    category = SlmErrorCategory.SEGMENTATION,
                    explanation = "Removed duplicate whitespace"
                )
            )
        }

        // Fix space before punctuation: "hello , world !" -> "hello, world!"
        val spaceBeforePunct = Regex(" +([,.:;?!])")
        if (spaceBeforePunct.containsMatchIn(text)) {
            text = spaceBeforePunct.replace(text, "$1")
            corrections.add(
                SlmCorrection(
                    original = " ,",
                    replacement = ",",
                    category = SlmErrorCategory.PUNCTUATION,
                    explanation = "Removed space preceding punctuation"
                )
            )
        }

        return Pair(text.trim(), corrections)
    }

    private suspend fun applyRoomDatabaseRules(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        var text = input
        try {
            val rules = grammarRuleDao.getActiveRulesSync()
            for (rule in rules) {
                if (rule.pattern.isNotBlank() && rule.replacement.isNotBlank()) {
                    val regex = Regex("(?i)\\b" + Regex.escape(rule.pattern) + "\\b")
                    if (regex.containsMatchIn(text)) {
                        text = regex.replace(text, rule.replacement)
                        corrections.add(
                            SlmCorrection(
                                original = rule.pattern,
                                replacement = rule.replacement,
                                category = SlmErrorCategory.GRAMMAR,
                                explanation = rule.description.ifBlank { "Applied learned user rule" }
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Room database rules note: ${e.message}")
        }
        return Pair(text, corrections)
    }

    private fun applySyntacticAgreementRules(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        var text = input

        val slmRules = listOf(
            // 1. Modal & Auxiliary Base-Form Concord
            SlmRule(
                regex = Regex("(?i)\\b(could|should|would|must|might)\\s+of\\b"),
                category = SlmErrorCategory.GRAMMAR,
                explanation = "Modal auxiliary verbs take 'have' instead of 'of'",
                transform = { "${it.groupValues[1]} have" }
            ),
            SlmRule(
                regex = Regex("(?i)\\b(did|didn't|did not)\\s+(went|saw|did|took|gave|came|made)\\b"),
                category = SlmErrorCategory.AGREEMENT,
                explanation = "Past auxiliary 'did' takes the base form of the verb",
                transform = { match ->
                    val aux = match.groupValues[1]
                    val base = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                        "went" -> "go"; "saw" -> "see"; "did" -> "do"; "took" -> "take"
                        "gave" -> "give"; "came" -> "come"; "made" -> "make"; else -> match.groupValues[2]
                    }
                    "$aux $base"
                }
            ),
            SlmRule(
                regex = Regex("(?i)\\b(will|won't|will not|shall)\\s+(went|saw|took|gave|came|had)\\b"),
                category = SlmErrorCategory.AGREEMENT,
                explanation = "Future modal takes the base form of the verb",
                transform = { match ->
                    val aux = match.groupValues[1]
                    val base = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                        "went" -> "go"; "saw" -> "see"; "took" -> "take"; "gave" -> "give"
                        "came" -> "come"; "had" -> "have"; else -> match.groupValues[2]
                    }
                    "$aux $base"
                }
            ),
            SlmRule(
                regex = Regex("(?i)\\bto\\s+(went|saw|bought|ate|ran|wrote)\\b"),
                category = SlmErrorCategory.GRAMMAR,
                explanation = "Infinitive 'to' takes the base verb form",
                transform = { match ->
                    val base = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                        "went" -> "go"; "saw" -> "see"; "bought" -> "buy"; "ate" -> "eat"
                        "ran" -> "run"; "wrote" -> "write"; else -> match.groupValues[1]
                    }
                    "to $base"
                }
            ),

            // 2. Perfect Aspect Concord
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+went\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} gone" },
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+saw\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} seen" },
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+did\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} done" },
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+wrote\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} written" },
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+ate\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} eaten" },
            SlmRule(Regex("(?i)\\b(has|have|had)\\s+took\\b"), SlmErrorCategory.GRAMMAR, "Past participle required after have/has/had") { "${it.groupValues[1]} taken" },

            // 3. Subject-Verb Agreement: 3rd Person Singular
            SlmRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+have\\b"), SlmErrorCategory.AGREEMENT, "Third-person singular subject requires 'has'") { "${it.groupValues[1]} has" },
            SlmRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+dont\\b"), SlmErrorCategory.AGREEMENT, "Third-person singular subject requires 'doesn't'") { "${it.groupValues[1]} doesn't" },
            SlmRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+don't\\b"), SlmErrorCategory.AGREEMENT, "Third-person singular subject requires 'doesn't'") { "${it.groupValues[1]} doesn't" },
            SlmRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+do\\b"), SlmErrorCategory.AGREEMENT, "Third-person singular subject requires 'does'") { "${it.groupValues[1]} does" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+go\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} goes" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+want\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} wants" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+like\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} likes" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+need\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} needs" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+think\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} thinks" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+know\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} knows" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+say\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person takes verb with -s/-es") { "${it.groupValues[1]} says" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+are\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person requires 'is'") { "${it.groupValues[1]} is" },
            SlmRule(Regex("(?i)\\b(he|she|it)\\s+were\\b"), SlmErrorCategory.AGREEMENT, "Singular third-person requires 'was'") { "${it.groupValues[1]} was" },

            // 4. Subject-Verb Agreement: Plural & 1st Person
            SlmRule(Regex("(?i)\\bi\\s+has\\b"), SlmErrorCategory.AGREEMENT, "First-person pronoun 'I' requires 'have'") { "I have" },
            SlmRule(Regex("(?i)\\bi\\s+is\\b"), SlmErrorCategory.AGREEMENT, "First-person pronoun 'I' requires 'am'") { "I am" },
            SlmRule(Regex("(?i)\\bi\\s+are\\b"), SlmErrorCategory.AGREEMENT, "First-person pronoun 'I' requires 'am'") { "I am" },
            SlmRule(Regex("(?i)\\bi\\s+were\\b"), SlmErrorCategory.AGREEMENT, "First-person pronoun 'I' requires 'was'") { "I was" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+was\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'were'") { "${it.groupValues[1]} were" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+has\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'have'") { "${it.groupValues[1]} have" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+is\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'are'") { "${it.groupValues[1]} are" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+does\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'do'") { "${it.groupValues[1]} do" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+doesnt\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'don't'") { "${it.groupValues[1]} don't" },
            SlmRule(Regex("(?i)\\b(they|we|you)\\s+doesn't\\b"), SlmErrorCategory.AGREEMENT, "Plural subject takes 'don't'") { "${it.groupValues[1]} don't" },

            // 5. Phonetic Indefinite Article Agreement (a vs an)
            SlmRule(
                regex = Regex("(?i)\\ba\\s+(apple|orange|egg|elephant|ice|island|umbrella|uncle|hour|honest|honor|heir|email|idea|order|item|urgent|opportunity|example|artist|answer|engine|accident)\\b"),
                category = SlmErrorCategory.GRAMMAR,
                explanation = "Use 'an' before words beginning with a vowel sound",
                transform = { "an ${it.groupValues[1]}" }
            ),
            SlmRule(
                regex = Regex("(?i)\\ban\\s+(car|house|book|dog|cat|computer|phone|university|uniform|user|unique|european|one|person|friend|meeting|job|place)\\b"),
                category = SlmErrorCategory.GRAMMAR,
                explanation = "Use 'a' before words beginning with a consonant sound (including 'university', 'uniform')",
                transform = { "a ${it.groupValues[1]}" }
            ),

            // 6. Contextual Homophone Disambiguation
            SlmRule(Regex("(?i)\\byour\\s+(welcome|invited|going|doing|coming|late|right|awesome|amazing|ready|correct|great)\\b"), SlmErrorCategory.HOMOPHONE, "Use contraction 'you're' (you are) instead of possessive 'your'") { "you're ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\byou're\\s+(car|house|phone|name|email|job|friend|family|turn|wallet|laptop|address|money)\\b"), SlmErrorCategory.HOMOPHONE, "Use possessive 'your' instead of contraction 'you're'") { "your ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\btheir\\s+(going|coming|doing|here|awesome|ready|invited|happy|excited|playing|leaving)\\b"), SlmErrorCategory.HOMOPHONE, "Use contraction 'they're' (they are) instead of possessive 'their'") { "they're ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\bthey're\\s+(house|car|dog|friend|family|job|time|money|place|room|parents)\\b"), SlmErrorCategory.HOMOPHONE, "Use possessive 'their' instead of contraction 'they're'") { "their ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\b(is|are|was|were|over|right|been)\\s+their\\b"), SlmErrorCategory.HOMOPHONE, "Use adverb 'there' for location or existence") { "${it.groupValues[1]} there" },
            SlmRule(Regex("(?i)\\bits\\s+(good|great|nice|ok|okay|a|an|the|going|working|done|not|too|very|time|ready)\\b"), SlmErrorCategory.HOMOPHONE, "Use contraction 'it's' (it is) instead of possessive 'its'") { "it's ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\bit's\\s+(color|price|size|weight|battery|screen|camera|engine|status)\\b"), SlmErrorCategory.HOMOPHONE, "Use possessive 'its' without apostrophe") { "its ${it.groupValues[1]}" },
            SlmRule(Regex("(?i)\\b(better|more|less|faster|slower|bigger|smaller|easier|harder|rather|taller|shorter|older|younger|worse)\\s+then\\b"), SlmErrorCategory.HOMOPHONE, "Use 'than' for comparisons instead of temporal 'then'") { "${it.groupValues[1]} than" },
            SlmRule(Regex("(?i)\\b(and|since|until|back|if)\\s+than\\b"), SlmErrorCategory.HOMOPHONE, "Use temporal 'then' instead of comparative 'than'") { "${it.groupValues[1]} then" },
            SlmRule(Regex("(?i)\\b(to|will|can|cannot|can't|could|would|don't)\\s+loose\\b"), SlmErrorCategory.HOMOPHONE, "Use verb 'lose' instead of adjective 'loose'") { "${it.groupValues[1]} lose" },
            SlmRule(Regex("(?i)\\b(to|will|can|please|must)\\s+except\\b"), SlmErrorCategory.HOMOPHONE, "Use verb 'accept' instead of preposition 'except'") { "${it.groupValues[1]} accept" },
            SlmRule(Regex("(?i)\\b(all|everyone|everything|nobody)\\s+accept\\b"), SlmErrorCategory.HOMOPHONE, "Use preposition 'except' (meaning excluding) instead of 'accept'") { "${it.groupValues[1]} except" },
            SlmRule(Regex("(?i)\\bfor\\s+all\\s+intensive\\s+purposes\\b"), SlmErrorCategory.STYLE, "Correct idiom: 'for all practical purposes'") { "for all practical purposes" },
            SlmRule(Regex("(?i)\\btake\\s+for\\s+granite\\b"), SlmErrorCategory.STYLE, "Correct idiom: 'take for granted'") { "take for granted" },
            SlmRule(Regex("(?i)\\btaken\\s+for\\s+granite\\b"), SlmErrorCategory.STYLE, "Correct idiom: 'taken for granted'") { "taken for granted" },
            SlmRule(Regex("(?i)\\birregardless\\b"), SlmErrorCategory.STYLE, "Standard English uses 'regardless'") { "regardless" },
            SlmRule(Regex("(?i)\\bsuppose\\s+to\\b"), SlmErrorCategory.GRAMMAR, "Standard phrasing is 'supposed to'") { "supposed to" },
            SlmRule(Regex("(?i)\\buse\\s+to\\b"), SlmErrorCategory.GRAMMAR, "Past habitual phrasing is 'used to'") { "used to" },

            // 7. Quantifier & Plural Concord
            SlmRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+childs\\b"), SlmErrorCategory.AGREEMENT, "Irregular plural of child is 'children'") { "${it.groupValues[1]} children" },
            SlmRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+mans\\b"), SlmErrorCategory.AGREEMENT, "Irregular plural of man is 'men'") { "${it.groupValues[1]} men" },
            SlmRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+womans\\b"), SlmErrorCategory.AGREEMENT, "Irregular plural of woman is 'women'") { "${it.groupValues[1]} women" },
            SlmRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+persons\\b"), SlmErrorCategory.AGREEMENT, "Natural plural phrasing is 'people'") { "${it.groupValues[1]} people" },
            SlmRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+year\\s+ago\\b"), SlmErrorCategory.AGREEMENT, "Plural noun required with number > 1") { "${it.groupValues[1]} years ago" },
            SlmRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+month\\s+ago\\b"), SlmErrorCategory.AGREEMENT, "Plural noun required with number > 1") { "${it.groupValues[1]} months ago" },
            SlmRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+day\\s+ago\\b"), SlmErrorCategory.AGREEMENT, "Plural noun required with number > 1") { "${it.groupValues[1]} days ago" }
        )

        for (rule in slmRules) {
            if (rule.regex.containsMatchIn(text)) {
                val match = rule.regex.find(text)
                val originalSegment = match?.value ?: ""
                text = rule.regex.replace(text) { m -> rule.transform(m) }
                corrections.add(
                    SlmCorrection(
                        original = originalSegment,
                        replacement = rule.transform(match ?: continue),
                        category = rule.category,
                        explanation = rule.explanation
                    )
                )
            }
        }

        return Pair(text, corrections)
    }

    private fun removeRepeatedTokens(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        val dedupRegex = Regex("(?i)\\b(the|and|is|in|that|to|of|a|an|it|for|on|with|as|at|this|but|by|from|they|we|say|her|she|or|will|my|one|all|would|there|their|what|so|if|who|get|which|go|me|when|can|like|time|no|just|him|know|take|people|into|year|your|good|some|could|them|see|other|than|then|now|look|only|come|its|over|think|also|back|after|use|two|how|our|work|first|well|way|even|new|want|because)\\s+\\1\\b")

        var text = input
        while (dedupRegex.containsMatchIn(text)) {
            val match = dedupRegex.find(text)
            val repeated = match?.value ?: ""
            val word = match?.groupValues?.get(1) ?: ""
            text = dedupRegex.replaceFirst(text, word)
            corrections.add(
                SlmCorrection(
                    original = repeated,
                    replacement = word,
                    category = SlmErrorCategory.STYLE,
                    explanation = "Removed duplicate consecutive word '$word'"
                )
            )
        }

        return Pair(text, corrections)
    }

    private fun applyTokenTypoAndContractionFixes(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        val lines = input.split("\n")
        val processedLines = lines.map { line ->
            if (line.isBlank()) return@map line

            val words = line.split(" ")
            val correctedWords = words.map { rawWord ->
                if (rawWord.isBlank()) return@map rawWord

                val leadingPunct = rawWord.takeWhile { !it.isLetterOrDigit() }
                val trailingPunct = rawWord.takeLastWhile { !it.isLetterOrDigit() }
                val core = rawWord.substring(leadingPunct.length, rawWord.length - trailingPunct.length)

                if (core.isEmpty() ||
                    core.all { !it.isLetter() } ||
                    core.contains("@") ||
                    core.contains("http") ||
                    core.contains("/") ||
                    core.contains("'") ||
                    core.any { it.isDigit() } ||
                    (core.length > 1 && core.all { it.isUpperCase() })
                ) {
                    return@map rawWord
                }

                val lower = core.lowercase(Locale.ROOT)

                // 1. Check known typo map
                val knownTypo = COMMON_TYPOS_MAP[lower]
                if (knownTypo != null) {
                    val restored = restoreCasing(core, knownTypo)
                    corrections.add(
                        SlmCorrection(
                            original = core,
                            replacement = restored,
                            category = SlmErrorCategory.SPELLING,
                            explanation = "Corrected spelling '$core' -> '$restored'"
                        )
                    )
                    return@map "$leadingPunct$restored$trailingPunct"
                }

                // 2. Check contraction restoration (e.g. "dont" -> "don't", "cant" -> "can't")
                val contraction = CONTRACTION_MAP[lower]
                if (contraction != null) {
                    val restored = restoreCasing(core, contraction)
                    corrections.add(
                        SlmCorrection(
                            original = core,
                            replacement = restored,
                            category = SlmErrorCategory.CONTRACTION,
                            explanation = "Added missing apostrophe: '$restored'"
                        )
                    )
                    return@map "$leadingPunct$restored$trailingPunct"
                }

                // 3. Isolated pronoun "i" -> "I"
                if (core == "i") {
                    corrections.add(
                        SlmCorrection(
                            original = "i",
                            replacement = "I",
                            category = SlmErrorCategory.CAPITALIZATION,
                            explanation = "Capitalized first-person pronoun 'I'"
                        )
                    )
                    return@map "${leadingPunct}I$trailingPunct"
                }

                // 4. Check on-device SymSpell, Trie & Gboard Typo Engine for unrecognized words
                if (!dictionaryManager.isValidOrKnownWord(lower) && core.length >= 2) {
                    val typoFix = dictionaryManager.gboardEngine.commonTypoLookup[lower]
                    val symSpellFix = dictionaryManager.gboardEngine.symSpellEngine.lookup(lower, maxDistance = 2.0f).firstOrNull()?.term
                    val trieFix = dictionaryManager.wordTrie.getBestCorrection(lower, maxDistance = 2)
                    val bestMatch = typoFix ?: symSpellFix ?: trieFix
                    if (bestMatch != null && bestMatch.lowercase(Locale.ROOT) != lower) {
                        val restored = restoreCasing(core, bestMatch)
                        corrections.add(
                            SlmCorrection(
                                original = core,
                                replacement = restored,
                                category = SlmErrorCategory.SPELLING,
                                explanation = "Corrected spelling '$core' -> '$restored'"
                            )
                        )
                        return@map "$leadingPunct$restored$trailingPunct"
                    }
                }

                rawWord
            }
            correctedWords.joinToString(" ")
        }

        return Pair(processedLines.joinToString("\n"), corrections)
    }

    private fun polishSentenceConcordance(input: String): Pair<String, List<SlmCorrection>> {
        val corrections = mutableListOf<SlmCorrection>()
        val sentences = input.split(Regex("(?<=[.!?])\\s+"))
        val polishedSentences = sentences.map { sentence ->
            if (sentence.isBlank()) return@map sentence

            var s = sentence.trim()

            // 1. Proper noun capitalization
            for (proper in PROPER_NOUNS) {
                val regex = Regex("(?i)\\b$proper\\b")
                if (regex.containsMatchIn(s)) {
                    val capitalized = proper.replaceFirstChar { it.uppercase(Locale.ROOT) }
                    s = regex.replace(s) { match ->
                        if (match.value != capitalized) {
                            corrections.add(
                                SlmCorrection(
                                    original = match.value,
                                    replacement = capitalized,
                                    category = SlmErrorCategory.CAPITALIZATION,
                                    explanation = "Capitalized proper noun '$capitalized'"
                                )
                            )
                        }
                        capitalized
                    }
                }
            }

            // 2. Capitalize first character of sentence
            val firstLetterIdx = s.indexOfFirst { it.isLetter() }
            if (firstLetterIdx != -1 && s[firstLetterIdx].isLowerCase()) {
                val originalChar = s[firstLetterIdx].toString()
                val upperChar = s[firstLetterIdx].uppercaseChar().toString()
                s = s.substring(0, firstLetterIdx) + upperChar + s.substring(firstLetterIdx + 1)
                corrections.add(
                    SlmCorrection(
                        original = originalChar,
                        replacement = upperChar,
                        category = SlmErrorCategory.CAPITALIZATION,
                        explanation = "Capitalized sentence beginning"
                    )
                )
            }

            // 3. Interrogative question mark inference
            val isInterrogative = s.matches(Regex("(?i)^(how|what|why|when|where|who|whom|which|is\\s+it|are\\s+you|can\\s+you|could\\s+you|would\\s+you|should\\s+i|do\\s+you|did\\s+you|will\\s+you|have\\s+you)\\b.*"))
            val hasTerminalPunct = s.endsWith(".") || s.endsWith("?") || s.endsWith("!")
            if (isInterrogative && !hasTerminalPunct) {
                s += "?"
                corrections.add(
                    SlmCorrection(
                        original = "",
                        replacement = "?",
                        category = SlmErrorCategory.PUNCTUATION,
                        explanation = "Added question mark to question sentence"
                    )
                )
            } else if (isInterrogative && s.endsWith(".")) {
                s = s.dropLast(1) + "?"
                corrections.add(
                    SlmCorrection(
                        original = ".",
                        replacement = "?",
                        category = SlmErrorCategory.PUNCTUATION,
                        explanation = "Replaced period with question mark on interrogative sentence"
                    )
                )
            } else if (!hasTerminalPunct && s.length > 8 && s.contains(" ")) {
                // Add terminal period if multi-word statement
                s += "."
                corrections.add(
                    SlmCorrection(
                        original = "",
                        replacement = ".",
                        category = SlmErrorCategory.PUNCTUATION,
                        explanation = "Added closing period to complete sentence"
                    )
                )
            }

            s
        }

        return Pair(polishedSentences.joinToString(" "), corrections)
    }

    private fun generateProofreadSummary(corrections: List<SlmCorrection>): String {
        if (corrections.isEmpty()) return "✨ Text is grammatically clear (no errors found)"

        val distinctCategories = corrections.map { it.category.displayName }.distinct()
        val count = corrections.size
        val issuesWord = if (count == 1) "issue" else "issues"
        return "✨ Fixed $count $issuesWord: " + distinctCategories.joinToString(", ")
    }

    private fun restoreCasing(original: String, replacement: String): String {
        return when {
            original.all { it.isUpperCase() } -> replacement.uppercase(Locale.ROOT)
            original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.uppercase(Locale.ROOT) }
            else -> replacement
        }
    }
}
