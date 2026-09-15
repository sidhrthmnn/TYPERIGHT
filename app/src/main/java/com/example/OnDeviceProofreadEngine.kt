package com.example

import android.content.Context
import android.graphics.PointF
import android.util.Log
import java.util.Locale

/**
 * Modern, Production-Grade On-Device Grammar, Spelling & Proofreading Engine.
 *
 * Implements a multi-pass on-device pipeline inspired by LanguageTool, SymSpell, and Gboard on-device NLP:
 * 1. Structural Tokenization & Punctuation Balancing
 * 2. Room Database Custom Rules Execution
 * 3. Deep Contextual Grammar & Agreement Matrix:
 *    - Subject-Verb Number & Person Agreement (he go -> he goes, they is -> they are, I has -> I have)
 *    - Modal & Auxiliary Verb Base Form Agreement (could of -> could have, did went -> did go, will had -> will have)
 *    - Indefinite Article Agreement (a apple -> an apple, a hour -> an hour, an university -> a university)
 *    - Confusion Set & Homophone Disambiguation (their/there/they're, your/you're, its/it's, then/than, lose/loose, accept/except)
 *    - Repeated Word Deduplication (the the -> the, and and -> and)
 *    - Run-on Word Splitting & Contraction Apostrophe Restoration (dont -> don't, alot -> a lot, infront -> in front)
 * 4. On-Device SymSpell & Phonetic Spell Autocorrection for Typo Detection
 * 5. Sentence Capitalization, Proper Noun Casing & Punctuation Refinement
 */
class OnDeviceProofreadEngine private constructor(private val context: Context) {

    private val slmProofreadEngine by lazy { SlmProofreadEngine.getInstance(context) }
    private val dictionaryManager by lazy { DictionaryManager(context) }
    private val localPredictor by lazy { LocalGrammarSpellPredictor(context) }
    private val db by lazy { AppDatabase.getDatabase(context) }
    private val grammarRuleDao by lazy { db.grammarRuleDao() }

    companion object {
        private const val TAG = "OnDeviceProofreadEngine"

        @Volatile
        private var instance: OnDeviceProofreadEngine? = null

        fun getInstance(context: Context): OnDeviceProofreadEngine {
            return instance ?: synchronized(this) {
                instance ?: OnDeviceProofreadEngine(context.applicationContext).also { instance = it }
            }
        }

        // Comprehensive common misspelling & phonetic typos dictionary (pre-indexed for zero-latency lookup)
        private val COMMON_TYPOS_MAP: Map<String, String> = mapOf(
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

        // Contractions map for instant expansion / apostrophe fixing
        private val CONTRACTION_RESTORE_MAP: Map<String, String> = mapOf(
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

        // Days of week and Months for proper noun capitalization
        private val PROPER_NOUNS = setOf(
            "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
            "january", "february", "march", "april", "may", "june", "july", "august",
            "september", "october", "november", "december", "english", "spanish", "french",
            "german", "chinese", "japanese", "korean", "american", "google", "android"
        )
    }

    /**
     * Executes complete on-device SLM sentence proofreading, grammar correction, and typo fixing.
     */
    suspend fun proofread(input: String): String {
        if (input.isBlank()) return input
        return slmProofreadEngine.proofread(input).proofreadText
    }

    /**
     * Executes detailed on-device SLM proofreading returning diagnostic corrections and explanations.
     */
    suspend fun proofreadDetailed(input: String, tone: String = "Proofread"): SlmProofreadEngine.SlmProofreadResult {
        return slmProofreadEngine.proofread(input, tone)
    }

    private fun normalizeStructuralText(input: String): String {
        var res = input.replace(Regex("[ \\t]+"), " ")
        // Fix space before punctuation (e.g. "hello , world !" -> "hello, world!")
        res = res.replace(Regex(" +([,.:;?!])"), "$1")
        return res.trim()
    }

    private suspend fun applyRoomRules(input: String): String {
        var res = input
        try {
            val rules = grammarRuleDao.getActiveRulesSync()
            for (rule in rules) {
                if (rule.pattern.isNotBlank() && rule.replacement.isNotBlank()) {
                    val regex = Regex("(?i)\\b" + Regex.escape(rule.pattern) + "\\b")
                    res = regex.replace(res, rule.replacement)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Note on Room rules execution: ${e.message}")
        }
        return res
    }

    private data class StaticGrammarRule(
        val regex: Regex,
        val transform: (MatchResult) -> String
    )

    /**
     * Deep rule-based grammar and semantic agreement pass.
     */
    private fun applyGrammarAndAgreementRules(input: String): String {
        var text = input

        val grammarRules = listOf(
            // Modal & Auxiliary Verb Agreement
            StaticGrammarRule(Regex("(?i)\\b(could|should|would|must|might)\\s+of\\b")) { match ->
                "${match.groupValues[1]} have"
            },
            StaticGrammarRule(Regex("(?i)\\b(did|didn't|did not)\\s+(went|saw|did|took|gave|came|made)\\b")) { match ->
                val aux = match.groupValues[1]
                val base = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "went" -> "go"; "saw" -> "see"; "did" -> "do"; "took" -> "take"
                    "gave" -> "give"; "came" -> "come"; "made" -> "make"; else -> match.groupValues[2]
                }
                "$aux $base"
            },
            StaticGrammarRule(Regex("(?i)\\b(will|won't|will not|shall)\\s+(went|saw|took|gave|came|had)\\b")) { match ->
                val aux = match.groupValues[1]
                val base = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "went" -> "go"; "saw" -> "see"; "took" -> "take"; "gave" -> "give"
                    "came" -> "come"; "had" -> "have"; else -> match.groupValues[2]
                }
                "$aux $base"
            },
            StaticGrammarRule(Regex("(?i)\\b(to)\\s+(went|saw|bought|ate|ran|wrote)\\b")) { match ->
                val base = when (match.groupValues[2].lowercase(Locale.ROOT)) {
                    "went" -> "go"; "saw" -> "see"; "bought" -> "buy"; "ate" -> "eat"
                    "ran" -> "run"; "wrote" -> "write"; else -> match.groupValues[2]
                }
                "to $base"
            },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+went\\b")) { "${it.groupValues[1]} gone" },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+saw\\b")) { "${it.groupValues[1]} seen" },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+did\\b")) { "${it.groupValues[1]} done" },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+wrote\\b")) { "${it.groupValues[1]} written" },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+ate\\b")) { "${it.groupValues[1]} eaten" },
            StaticGrammarRule(Regex("(?i)\\b(has|have|had)\\s+took\\b")) { "${it.groupValues[1]} taken" },

            // Subject-Verb Agreement (Singular 3rd person)
            StaticGrammarRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+have\\b")) { "${it.groupValues[1]} has" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+dont\\b")) { "${it.groupValues[1]} doesn't" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+don't\\b")) { "${it.groupValues[1]} doesn't" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it|someone|everyone|everybody|nobody|anyone|somebody)\\s+do\\b")) { "${it.groupValues[1]} does" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+go\\b")) { "${it.groupValues[1]} goes" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+want\\b")) { "${it.groupValues[1]} wants" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+like\\b")) { "${it.groupValues[1]} likes" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+need\\b")) { "${it.groupValues[1]} needs" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+think\\b")) { "${it.groupValues[1]} thinks" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+know\\b")) { "${it.groupValues[1]} knows" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+say\\b")) { "${it.groupValues[1]} says" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+are\\b")) { "${it.groupValues[1]} is" },
            StaticGrammarRule(Regex("(?i)\\b(he|she|it)\\s+were\\b")) { "${it.groupValues[1]} was" },

            // Subject-Verb Agreement (Plural / 1st person)
            StaticGrammarRule(Regex("(?i)\\bi\\s+has\\b")) { "I have" },
            StaticGrammarRule(Regex("(?i)\\bi\\s+is\\b")) { "I am" },
            StaticGrammarRule(Regex("(?i)\\bi\\s+are\\b")) { "I am" },
            StaticGrammarRule(Regex("(?i)\\bi\\s+were\\b")) { "I was" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+was\\b")) { "${it.groupValues[1]} were" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+has\\b")) { "${it.groupValues[1]} have" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+is\\b")) { "${it.groupValues[1]} are" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+does\\b")) { "${it.groupValues[1]} do" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+doesnt\\b")) { "${it.groupValues[1]} don't" },
            StaticGrammarRule(Regex("(?i)\\b(they|we|you)\\s+doesn't\\b")) { "${it.groupValues[1]} don't" },

            // Article Agreement (a vs an)
            StaticGrammarRule(Regex("(?i)\\ba\\s+(apple|orange|egg|elephant|ice|island|umbrella|uncle|hour|honest|honor|heir|email|idea|order|item|urgent|opportunity|example|artist|answer|engine)\\b")) { "an ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\ban\\s+(car|house|book|dog|cat|computer|phone|university|uniform|user|unique|european|one|person|friend|meeting|job|place)\\b")) { "a ${it.groupValues[1]}" },

            // Homophone & Confusion Sets Disambiguation
            StaticGrammarRule(Regex("(?i)\\byour\\s+(welcome|invited|going|doing|coming|late|right|awesome|amazing|ready|correct|great)\\b")) { "you're ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\byou're\\s+(car|house|phone|name|email|job|friend|family|turn|wallet|laptop|address|money)\\b")) { "your ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\btheir\\s+(going|coming|doing|here|awesome|ready|invited|happy|excited|playing|leaving)\\b")) { "they're ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\bthey're\\s+(house|car|dog|friend|family|job|time|money|place|room|parents)\\b")) { "their ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\b(is|are|was|were|over|right|been)\\s+their\\b")) { "${it.groupValues[1]} there" },
            StaticGrammarRule(Regex("(?i)\\bits\\s+(good|great|nice|ok|okay|a|an|the|going|working|done|not|too|very|time|ready)\\b")) { "it's ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\bit's\\s+(color|price|size|weight|battery|screen|camera|engine|status)\\b")) { "its ${it.groupValues[1]}" },
            StaticGrammarRule(Regex("(?i)\\b(better|more|less|faster|slower|bigger|smaller|easier|harder|rather|taller|shorter|older|younger|worse)\\s+then\\b")) { "${it.groupValues[1]} than" },
            StaticGrammarRule(Regex("(?i)\\b(and|since|until|back|if)\\s+than\\b")) { "${it.groupValues[1]} then" },
            StaticGrammarRule(Regex("(?i)\\b(to|will|can|cannot|can't|could|would|don't)\\s+loose\\b")) { "${it.groupValues[1]} lose" },
            StaticGrammarRule(Regex("(?i)\\b(to|will|can|please|must)\\s+except\\b")) { "${it.groupValues[1]} accept" },
            StaticGrammarRule(Regex("(?i)\\b(all|everyone|everything|nobody)\\s+accept\\b")) { "${it.groupValues[1]} except" },
            StaticGrammarRule(Regex("(?i)\\bfor\\s+all\\s+intensive\\s+purposes\\b")) { "for all practical purposes" },
            StaticGrammarRule(Regex("(?i)\\btake\\s+for\\s+granite\\b")) { "take for granted" },
            StaticGrammarRule(Regex("(?i)\\btaken\\s+for\\s+granite\\b")) { "taken for granted" },
            StaticGrammarRule(Regex("(?i)\\birregardless\\b")) { "regardless" },
            StaticGrammarRule(Regex("(?i)\\bsuppose\\s+to\\b")) { "supposed to" },
            StaticGrammarRule(Regex("(?i)\\buse\\s+to\\b")) { "used to" },

            // Quantifier / Plural Agreements
            StaticGrammarRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+childs\\b")) { "${it.groupValues[1]} children" },
            StaticGrammarRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+mans\\b")) { "${it.groupValues[1]} men" },
            StaticGrammarRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+womans\\b")) { "${it.groupValues[1]} women" },
            StaticGrammarRule(Regex("(?i)\\b(many|few|several|two|three|four|five|six|seven|eight|nine|ten)\\s+persons\\b")) { "${it.groupValues[1]} people" },
            StaticGrammarRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+year\\s+ago\\b")) { "${it.groupValues[1]} years ago" },
            StaticGrammarRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+month\\s+ago\\b")) { "${it.groupValues[1]} months ago" },
            StaticGrammarRule(Regex("(?i)\\b(two|three|four|five|six|seven|eight|nine|ten)\\s+day\\s+ago\\b")) { "${it.groupValues[1]} days ago" }
        )

        for (rule in grammarRules) {
            text = rule.regex.replace(text) { match -> rule.transform(match) }
        }

        return text
    }

    private fun removeRepeatedWords(input: String): String {
        // Removes duplicate adjacent words e.g. "the the", "and and", "is is"
        return input.replace(Regex("(?i)\\b(the|and|is|in|that|to|of|a|an|it|for|on|with|as|at|this|but|by|from|they|we|say|her|she|or|will|my|one|all|would|there|their|what|so|if|who|get|which|go|me|when|can|like|time|no|just|him|know|take|people|into|year|your|good|some|could|them|see|other|than|then|now|look|only|come|its|over|think|also|back|after|use|two|how|our|work|first|well|way|even|new|want|because)\\s+\\1\\b")) { match ->
            match.groupValues[1]
        }
    }

    /**
     * Iterates over tokens and fixes common spelling errors, missing contraction apostrophes,
     * and applies SymSpell fuzzy correction to isolated misspelled words.
     */
    private fun correctSentenceTokens(input: String): String {
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

                val lowerCore = core.lowercase(Locale.ROOT)

                // 1. Direct Contraction Restoration (e.g. dont -> don't, im -> I'm)
                val contractionFixed = CONTRACTION_RESTORE_MAP[lowerCore]
                if (contractionFixed != null) {
                    val fixedWithCase = matchCase(core, contractionFixed)
                    return@map "$leadingPunct$fixedWithCase$trailingPunct"
                }

                // 2. High-speed Common Typos Map
                val typoFixed = COMMON_TYPOS_MAP[lowerCore]
                if (typoFixed != null) {
                    val fixedWithCase = matchCase(core, typoFixed)
                    return@map "$leadingPunct$fixedWithCase$trailingPunct"
                }

                // 3. If word is already valid dictionary word or abbreviation, keep it
                if (dictionaryManager.isWordInDictionary(lowerCore) || dictionaryManager.isWordInUserDictionary(lowerCore)) {
                    return@map rawWord
                }

                // 4. On-Device SymSpell Fuzzy Spell Correction
                val symSpellCandidates = dictionaryManager.getSpellingCorrections(lowerCore)
                if (symSpellCandidates.isNotEmpty()) {
                    val bestCandidate = symSpellCandidates.first()
                    // Verify that the candidate is valid and close
                    if (bestCandidate != lowerCore) {
                        val fixedWithCase = matchCase(core, bestCandidate)
                        return@map "$leadingPunct$fixedWithCase$trailingPunct"
                    }
                }

                rawWord
            }

            correctedWords.joinToString(" ")
        }

        return processedLines.joinToString("\n")
    }

    /**
     * Capitalizes start of sentences, standalone pronoun 'I', proper nouns, and balances punctuation.
     */
    private fun polishSentencePunctuationAndCapitalization(input: String): String {
        if (input.isBlank()) return input

        var text = input

        // Capitalize standalone pronoun "i" -> "I"
        text = text.replace(Regex("(?<=\\s|^)i(?=[\\s.,!?;:'\"])"), "I")

        // Capitalize known proper nouns (e.g. monday -> Monday, january -> January)
        for (prop in PROPER_NOUNS) {
            text = text.replace(Regex("(?i)\\b$prop\\b")) { match ->
                match.value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            }
        }

        // Capitalize after sentence-ending punctuation (., !, ?)
        val sentenceSplitter = Regex("(?<=[.!?])\\s+")
        val sentences = text.split(sentenceSplitter)
        val capitalizedSentences = sentences.map { s ->
            val trimmed = s.trimStart()
            if (trimmed.isEmpty()) return@map s
            val leadingWhitespace = s.substring(0, s.length - trimmed.length)
            leadingWhitespace + trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        text = capitalizedSentences.joinToString(" ")

        // First character of entire text should be capitalized
        if (text.isNotEmpty() && text[0].isLowerCase()) {
            text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }

        // Add trailing period if single complete sentence with no ending punctuation
        if (text.isNotEmpty() && !text.endsWith(".") && !text.endsWith("!") && !text.endsWith("?") && !text.endsWith(":") && !text.contains("\n")) {
            val wordsCount = text.split("\\s+".toRegex()).size
            if (wordsCount >= 3) {
                // If it looks like a question
                val lower = text.lowercase(Locale.ROOT)
                val isQuestion = lower.startsWith("what ") || lower.startsWith("how ") || lower.startsWith("why ") ||
                        lower.startsWith("where ") || lower.startsWith("when ") || lower.startsWith("who ") ||
                        lower.startsWith("can you ") || lower.startsWith("could you ") || lower.startsWith("would you ") ||
                        lower.startsWith("do you ") || lower.startsWith("did you ") || lower.startsWith("is it ") ||
                        lower.startsWith("are you ")
                text += if (isQuestion) "?" else "."
            }
        }

        return text
    }

    /**
     * Checks and corrects an individual token for typos, contractions, or SymSpell candidates.
     */
    fun proofreadToken(rawToken: String): String? {
        if (rawToken.isBlank()) return null
        val lower = rawToken.lowercase(Locale.ROOT)

        val contraction = CONTRACTION_RESTORE_MAP[lower]
        if (contraction != null) {
            return matchCase(rawToken, contraction)
        }

        val typo = COMMON_TYPOS_MAP[lower]
        if (typo != null) {
            return matchCase(rawToken, typo)
        }

        if (dictionaryManager.isWordInDictionary(lower) || dictionaryManager.isWordInUserDictionary(lower)) {
            return rawToken
        }

        val candidates = dictionaryManager.getSpellingCorrections(lower)
        if (candidates.isNotEmpty()) {
            val best = candidates.first()
            if (best != lower) {
                return matchCase(rawToken, best)
            }
        }
        return rawToken
    }

    private fun matchCase(original: String, replacement: String): String {
        if (original.isEmpty() || replacement.isEmpty()) return replacement
        if (original.length > 1 && original.all { it.isUpperCase() }) {
            return replacement.uppercase(Locale.ROOT)
        }
        if (original[0].isUpperCase()) {
            return replacement.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return replacement
    }
}
