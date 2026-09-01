package com.example

import android.content.Context
import android.graphics.PointF
import android.util.Log
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Detailed breakdown of intermediate scores contributing to a candidate's posterior ranking.
 */
data class ScoreBreakdown(
    val spatialScore: Float,
    val frequencyScore: Float,
    val contextScore: Float,
    val editDistanceScore: Float,
    val personalScore: Float,
    val totalScore: Float
)

/**
 * Gboard-Style Candidate Representation with full diagnostic transparency.
 */
data class GboardCandidate(
    val word: String,
    val spatialScore: Float,
    val lmScore: Float,
    val editDistance: Float,
    val frequencyScore: Float,
    val totalPosterior: Float,
    val confidenceTier: ConfidenceTier,
    val isAutocorrectEligible: Boolean,
    val reason: String,
    val scoreBreakdown: ScoreBreakdown = ScoreBreakdown(
        spatialScore = spatialScore,
        frequencyScore = frequencyScore,
        contextScore = lmScore,
        editDistanceScore = (1.0f - (editDistance / maxOf(3f, word.length.toFloat()))).coerceIn(0f, 1f),
        personalScore = if (reason.contains("User") || reason.contains("Personal")) 0.85f else 0.20f,
        totalScore = totalPosterior
    )
)

enum class ConfidenceTier {
    HIGH,   // Tier 1: Auto-applied on space/punctuation with margin enforcement
    MEDIUM, // Tier 2: Displayed in center prominent slot of suggestion strip
    LOW     // Tier 3: Displayed in left/right secondary suggestion slots
}

data class GboardSuggestionResult(
    val leftCandidate: String,
    val centerCandidate: String,
    val rightCandidate: String,
    val isCenterAutocorrecting: Boolean,
    val debugTelemetry: GboardTelemetry? = null
)

data class GboardTelemetry(
    val rawInput: String,
    val contextWords: List<String>,
    val topCandidates: List<GboardCandidate>,
    val touchDeltas: List<Float>,
    val scoreMargin: Float,
    val decisionReason: String
)

/**
 * Production-grade Gboard-Style Prediction and Autocorrection Engine.
 *
 * Implements:
 * 1. Spatial Key-Proximity Model (Bivariate Gaussian & Hitbox geometry)
 * 2. Frequency-weighted Trie & User Dictionary Prefix Lookup
 * 3. SymSpell-style Precomputed-Deletion Weighted Edit-Distance Lookup (capped at distance 2)
 * 4. Katz Backoff Multi-Order Context N-Gram Language Model
 * 5. Candidate Scorer combining spatial likelihood, language model, corpus frequency, and user habit
 * 6. Decoupled Confidence Gating with distinct thresholds for key correction, whole-word autocorrect, word completion, and next-word prediction
 * 7. Personalization Hooks (learning accepted terms & suppressing rejected/undone corrections)
 * 8. Comprehensive UX details (auto-capitalization, one-tap/backspace undo, sensitive field suppression)
 */
class GboardPredictionEngine(private val context: Context) {

    private val settings = KeyboardSettings(context)
    private val mlPredictor = PatternLearningPredictor.getInstance(context)
    private val localGrammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    val tfLiteModel = TfLiteCorrectionModel.getInstance(context)
    val nGramModel = NGramLanguageModel()
    val spatialModel = SpatialKeyProximityModel()
    val symSpellEngine = SymSpellCorrectionEngine(spatialModel, maxEditDistance = 2)

    private val predictionCache = object : LinkedHashMap<String, GboardSuggestionResult>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, GboardSuggestionResult>?): Boolean {
            return size > 128
        }
    }

    companion object {
        private const val TAG = "TypeRightAutoCorrect"

        // 6. Decoupled Confidence Gating Thresholds
        const val KEY_CORRECTION_THRESHOLD = 0.35f
        const val WHOLE_WORD_AUTOCORRECT_THRESHOLD = 0.45f
        const val AUTOCORRECT_MARGIN = 0.03f
        const val WORD_COMPLETION_THRESHOLD = 0.25f
        const val NEXT_WORD_PREDICTION_THRESHOLD = 0.20f
    }

    // High-frequency typo and transposition table
    val commonTypoLookup: Map<String, String> = mapOf(
        "teh" to "the", "yhe" to "the", "hte" to "the", "tha" to "the", "tht" to "that",
        "taht" to "that", "tgat" to "that", "yhat" to "that", "tath" to "that",
        "adn" to "and", "nad" to "and", "annd" to "and", "smd" to "and",
        "recieve" to "receive", "recieved" to "received", "recieving" to "receiving", "recive" to "receive",
        "woudl" to "would", "wodul" to "would", "shoudl" to "should", "coudl" to "could",
        "cud" to "could", "yesturday" to "yesterday", "tommorow" to "tomorrow", "tomorow" to "tomorrow",
        "goverment" to "government", "occured" to "occurred", "definately" to "definitely",
        "definetly" to "definitely", "beautifull" to "beautiful", "seperate" to "separate", "untill" to "until",
        "accommodate" to "accommodate", "accomodate" to "accommodate", "wierd" to "weird", "belive" to "believe",
        "truely" to "truly", "mispell" to "misspell", "writting" to "writing",
        "speling" to "spelling", "grammer" to "grammar", "keybord" to "keyboard",
        "mye" to "my", "tyep" to "type", "wrk" to "work", "wrd" to "word",
        "appl" to "apple", "prdct" to "predict", "wnat" to "want", "watn" to "want",
        "ehst" to "what", "waht" to "what", "whta" to "what", "alredy" to "already",
        "alwasy" to "always", "beacuse" to "because", "becuase" to "because",
        "comming" to "coming", "realy" to "really", "thier" to "their", "theri" to "their",
        "tought" to "thought", "tihs" to "this", "thsi" to "this", "thid" to "this", "fir" to "for", "whcih" to "which", "abotu" to "about",
        "peopel" to "people", "poeple" to "people", "jsut" to "just", "juts" to "just",
        "knwo" to "know", "themselfs" to "themselves", "wich" to "which", "widht" to "width",
        "acording" to "according", "beleive" to "believe", "rember" to "remember",
        "frind" to "friend", "freind" to "friend", "mkae" to "make", "amke" to "make",
        "liek" to "like", "lkie" to "like", "godo" to "good", "helo" to "hello", "helllo" to "hello",
        "hw" to "how", "hwo" to "how", "yu" to "you", "yuo" to "you", "oyu" to "you",
        "thx" to "thanks", "pls" to "please", "plz" to "please", "tks" to "thanks",
        "gonna" to "going to", "wanna" to "want to", "gotta" to "got to",
        "dontknow" to "don't know", "goodmorning" to "good morning", "goodnight" to "good night",
        "thankyou" to "thank you", "thanksalot" to "thanks a lot", "howareyou" to "how are you",
        "seeyou" to "see you", "loveyou" to "love you", "letsgo" to "let's go",
        "withyou" to "with you", "goingto" to "going to", "wantto" to "want to",
        "infront" to "in front", "atleast" to "at least", "alot" to "a lot",
        "embarass" to "embarrass", "neccessary" to "necessary", "necesary" to "necessary",
        "unfortunatly" to "unfortunately", "probaly" to "probably", "probly" to "probably",
        "familar" to "familiar", "guarentee" to "guarantee", "schedual" to "schedule",
        "intresting" to "interesting", "differant" to "different", "experiance" to "experience",
        "fone" to "phone", "enuf" to "enough", "nite" to "night", "thru" to "through",
        "calender" to "calendar", "restarant" to "restaurant", "restaraunt" to "restaurant",
        "runing" to "running", "begining" to "beginning", "priviledge" to "privilege",
        "fomr" to "from", "frm" to "from", "somthing" to "something", "anyting" to "anything",
        "evning" to "evening", "mornign" to "morning", "computre" to "computer",
        "applicatoin" to "application", "messgae" to "message", "quetion" to "question"
    )

    // Contraction expansions (apostrophe restoration)
    val contractionLookup: Map<String, String> = mapOf(
        "dont" to "don't", "cant" to "can't", "wont" to "won't",
        "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
        "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
        "hes" to "he's", "shes" to "she's", "its" to "it's", "theyre" to "they're",
        "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
        "weve" to "we've", "were" to "we're", "well" to "we'll", "wed" to "we'd",
        "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't",
        "hadnt" to "hadn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "couldnt" to "couldn't",
        "thats" to "that's", "whats" to "what's", "heres" to "here's", "theres" to "there's",
        "wheres" to "where's", "hows" to "how's", "lets" to "let's"
    )

    // Emoji shortcut predictions
    val emojiIntentMap: Map<String, String> = mapOf(
        "love" to "❤️", "heart" to "💖", "happy" to "😊", "smile" to "😄",
        "laugh" to "😂", "lol" to "🤣", "cool" to "😎", "fire" to "🔥",
        "lit" to "🔥", "clap" to "👏", "party" to "🎉", "sad" to "😢",
        "cry" to "😭", "angry" to "😡", "coffee" to "☕", "beer" to "🍺",
        "pizza" to "🍕", "cake" to "🎂", "sun" to "☀️", "star" to "⭐",
        "dog" to "🐶", "cat" to "🐱", "car" to "🚗", "plane" to "✈️",
        "money" to "💰", "music" to "🎵", "yes" to "👍", "ok" to "👌",
        "hi" to "👋", "hello" to "👋", "bye" to "👋", "sleep" to "😴"
    )

    private val properNouns = setOf(
        "I", "I'm", "I've", "I'll", "I'd", "Sunday", "Monday", "Tuesday",
        "Wednesday", "Thursday", "Friday", "Saturday", "January", "February",
        "March", "April", "May", "June", "July", "August", "September",
        "October", "November", "December", "Google", "Android", "America"
    )

    /**
     * Algorithmic candidate generator for transpositions, adjacent QWERTY substitutions, deletions, and insertions.
     */
    fun generateAlgorithmicCandidates(raw: String, dictionaryManager: DictionaryManager): Set<String> {
        val lower = raw.lowercase().trim()
        if (lower.isEmpty()) return emptySet()
        val candidates = LinkedHashSet<String>()

        // 1. Fast SymSpell bounded edit-distance lookup (distance <= 2)
        val symSpellMatches = symSpellEngine.lookup(lower, maxDistance = 2.0f)
        for (match in symSpellMatches) {
            candidates.add(match.term)
        }

        // 2. Adjacent Transpositions (teh -> the, adn -> and, woudl -> would)
        if (lower.length >= 2) {
            val chars = lower.toCharArray()
            for (i in 0 until chars.size - 1) {
                val temp = chars[i]
                chars[i] = chars[i + 1]
                chars[i + 1] = temp
                val transposed = String(chars)
                if (dictionaryManager.isWordInDictionary(transposed)) {
                    candidates.add(transposed)
                }
                chars[i + 1] = chars[i]
                chars[i] = temp
            }
        }

        // 3. Single-letter deletions (helllo -> hello, annd -> and)
        if (lower.length >= 3) {
            for (i in lower.indices) {
                val deleted = lower.removeRange(i, i + 1)
                if (deleted.length >= 2 && dictionaryManager.isWordInDictionary(deleted)) {
                    candidates.add(deleted)
                }
            }
        }

        // 4. Single-letter insertions & doubling (tomorow -> tomorrow, runing -> running)
        if (lower.length in 2..12) {
            for (i in lower.indices) {
                val doubled = lower.substring(0, i + 1) + lower[i] + lower.substring(i + 1)
                if (dictionaryManager.isWordInDictionary(doubled)) {
                    candidates.add(doubled)
                }
            }
        }

        return candidates
    }

    /**
     * Missed space segmentation (goodmorning -> good morning, thankyou -> thank you)
     */
    fun segmentMissedSpaces(raw: String, dictionaryManager: DictionaryManager): String? {
        val clean = raw.lowercase().trim()
        if (clean.length < 4) return null

        for (i in 2 until clean.length - 1) {
            val left = clean.substring(0, i)
            val right = clean.substring(i)

            val leftValid = (left == "i" || left == "a" || dictionaryManager.isWordInDictionary(left))
            val rightValid = (right == "i" || right == "a" || dictionaryManager.isWordInDictionary(right))

            if (leftValid && rightValid) {
                val lStr = if (left == "i") "I" else left
                val rStr = if (right == "i") "I" else right
                return "$lStr $rStr"
            }
        }
        return null
    }

    /**
     * Generate, Rank, and Decode Candidates for the current typing state.
     * Combines dictionary frequency, spatial tap likelihood, and context n-gram probability into a ranked list.
     */
    fun getGboardPredictionsAndCorrections(
        rawTyped: String,
        contextWords: List<String>,
        tapCoords: List<PointF>?,
        dictionaryManager: DictionaryManager,
        isSensitiveField: Boolean = false
    ): GboardSuggestionResult {
        val trimmed = rawTyped.trim()
        val lower = trimmed.lowercase()

        val cacheKey = "$lower|${contextWords.takeLast(2).joinToString(",")}|${tapCoords?.size ?: 0}|$isSensitiveField"
        synchronized(predictionCache) {
            val cached = predictionCache[cacheKey]
            if (cached != null) return cached
        }

        // Empty typing state: Next-Word Prediction and Phrase Completion (N-Gram + Phrase Predictor)
        if (trimmed.isEmpty()) {
            val phrasePredictions = localGrammarPredictor.predictPhraseCompletions(contextWords, "", 3)
            val nGramNextPredictions = nGramModel.predictNextWords(contextWords, "", 8)
            val nGramNextPhrases = nGramModel.predictNextPhrases(contextWords, 3)
            val prev1 = contextWords.lastOrNull()?.lowercase()?.trim() ?: ""
            val prev2 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase().trim() else ""

            val mlTrigram = if (prev2.isNotEmpty() && prev1.isNotEmpty()) {
                mlPredictor.predictNextWordsFromTrigram(prev2, prev1).map { it.first }
            } else emptyList()

            val mlBigram = if (prev1.isNotEmpty()) {
                mlPredictor.predictNextWords(prev1).map { it.first }
            } else emptyList()

            val combinedPool = mutableListOf<String>()
            if (phrasePredictions.isNotEmpty()) combinedPool.addAll(phrasePredictions)
            if (nGramNextPhrases.isNotEmpty()) combinedPool.addAll(nGramNextPhrases)
            combinedPool.addAll(nGramNextPredictions)
            combinedPool.addAll(mlTrigram)
            combinedPool.addAll(mlBigram)
            combinedPool.addAll(listOf("the", "I", "to"))

            val top3 = combinedPool.distinct().take(3)
            val left = top3.getOrElse(0) { "the" }
            val center = top3.getOrElse(1) { "to" }
            val right = top3.getOrElse(2) { "and" }

            return GboardSuggestionResult(
                leftCandidate = left,
                centerCandidate = center,
                rightCandidate = right,
                isCenterAutocorrecting = false,
                debugTelemetry = GboardTelemetry(
                    rawInput = "",
                    contextWords = contextWords,
                    topCandidates = top3.map {
                        GboardCandidate(it, 1f, 1f, 0f, 1f, 1f, ConfidenceTier.LOW, false, "N-Gram Next-Word Prediction")
                    },
                    touchDeltas = emptyList(),
                    scoreMargin = 0f,
                    decisionReason = "Next-word contextual prediction active"
                )
            )
        }

        // Candidate Generation Set
        val candidatePool = LinkedHashSet<String>()

        // 1. TFLite Neural Sequence & Word Transformation Model
        val tfliteWordCandidate = try {
            val res = tfLiteModel.correctText(lower).trim()
            if (res.isNotEmpty() && res.lowercase() != lower) res else null
        } catch (_: Exception) { null }
        if (tfliteWordCandidate != null) {
            candidatePool.add(tfliteWordCandidate)
        }

        val tfliteContextCandidates = mutableListOf<String>()
        if (contextWords.isNotEmpty()) {
            try {
                val ctxBigram = "${contextWords.last()} $lower"
                val tfliteSeqFix = tfLiteModel.correctText(ctxBigram).trim()
                if (tfliteSeqFix.isNotEmpty() && tfliteSeqFix.lowercase() != ctxBigram.lowercase()) {
                    val tokens = tfliteSeqFix.split(Regex("\\s+"))
                    if (tokens.isNotEmpty()) {
                        val lastToken = tokens.last()
                        tfliteContextCandidates.add(lastToken)
                        candidatePool.add(lastToken)
                        if (tokens.size > 1) {
                            candidatePool.add(tfliteSeqFix)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. SymSpell Bounded Edit-Distance Dictionary Lookup (distance <= 2)
        val symSpellMatches = symSpellEngine.lookup(lower, maxDistance = 2.0f, maxResults = 12)
        for (match in symSpellMatches) {
            candidatePool.add(match.term)
        }

        // 3. Multi-Order N-Gram Language Model Candidates (Quadgram, Trigram, Bigram, Unigram)
        val nGramPredictions = nGramModel.predictNextWords(contextWords, prefix = lower, maxResults = 8)
        candidatePool.addAll(nGramPredictions)

        // 4. Local Grammar Multi-Word Phrase Completion & Grammar Check
        val phraseMatches = localGrammarPredictor.predictPhraseCompletions(contextWords, lower, 3)
        candidatePool.addAll(phraseMatches)

        val localGrammarCorrection = localGrammarPredictor.checkGrammarDetailed(lower, contextWords)
        if (localGrammarCorrection != null) {
            candidatePool.add(localGrammarCorrection.correctedWord)
        }

        // 5. Direct typo & Contraction lookups
        commonTypoLookup[lower]?.let { candidatePool.add(it) }
        contractionLookup[lower]?.let { candidatePool.add(it) }

        // 6. Algorithmic Candidates (Transpositions, insertions, deletions)
        val algoCandidates = generateAlgorithmicCandidates(lower, dictionaryManager)
        candidatePool.addAll(algoCandidates)

        // 7. Missed space split candidate (e.g. goodmorning -> good morning)
        segmentMissedSpaces(lower, dictionaryManager)?.let { candidatePool.add(it) }

        // 8. Prefix match candidates and fuzzy matches from local Trie
        val prefixMatches = dictionaryManager.getSuggestionsForPrefix(
            prefix = lower,
            previousWords = contextWords,
            tapCoords = tapCoords
        )
        candidatePool.addAll(prefixMatches)
        candidatePool.addAll(dictionaryManager.wordTrie.findByPrefix(lower, 10))
        candidatePool.addAll(dictionaryManager.wordTrie.findFuzzyMatches(lower, maxDistance = 2, maxResults = 10))

        // 9. Fuzzy spell corrections & phonetic matches
        val spellCorrections = dictionaryManager.getSpellingCorrections(
            word = lower,
            prevWord = contextWords.lastOrNull(),
            tapCoords = tapCoords
        )
        candidatePool.addAll(spellCorrections)

        // 10. Include raw typed literal in pool
        candidatePool.add(trimmed)

        val isRawValidWord = dictionaryManager.isWordInDictionary(lower) || symSpellEngine.hasWord(lower)
        val isRawCodeOrSpecial = dictionaryManager.isCodeOrSpecialToken(trimmed)

        // Decode and score each candidate using unified multi-signal equation:
        // [TFLite Neural + SymSpell Spatial Dictionary + N-Gram Language Model + Corpus Frequency]
        val scoredCandidates = mutableListOf<GboardCandidate>()

        for (cand in candidatePool) {
            val cleanCand = cand.lowercase().trim()
            val isExactMatch = cleanCand == lower
            val isKnownWord = dictionaryManager.isWordInDictionary(cleanCand) || symSpellEngine.hasWord(cleanCand) || cleanCand == "i" || cleanCand == "a"
            val isGrammarFix = localGrammarCorrection != null && (cleanCand == localGrammarCorrection.correctedWord.lowercase() || cand == localGrammarCorrection.correctedWord)
            val isTfLiteMatch = (tfliteWordCandidate != null && cleanCand == tfliteWordCandidate.lowercase()) ||
                    tfliteContextCandidates.any { it.lowercase() == cleanCand }
            val symSpellMatch = symSpellMatches.firstOrNull { it.term == cleanCand }

            // 1. Spatial-Weighted Edit Distance (using SymSpell metric if present, else spatial model)
            val editDist = if (symSpellMatch != null) {
                symSpellMatch.distance
            } else {
                spatialModel.computeSpatialEditDistance(lower, cleanCand)
            }

            // 2. Spatial Tap Coordinate Likelihood
            val spatialLikelihood = spatialModel.computeSpatialTouchLikelihood(cleanCand, tapCoords)

            // 3. Language Model Probability (Smoothed Multi-Order N-Gram)
            val lmProb = nGramModel.getProbability(cleanCand, contextWords)

            // 4. Normalized Edit Distance Score [0.0, 1.0]
            val maxLen = max(lower.length, cleanCand.length).toFloat().coerceAtLeast(3.0f)
            val normalizedEditScore = (1.0f - (editDist / maxLen)).coerceIn(0.0f, 1.0f)

            // 5. Corpus Word Frequency Score [0.0, 1.0]
            val wordFreq = dictionaryManager.getWordFrequency(cleanCand)
            val freqScore = (log10(wordFreq.toFloat() + 1f) / log10(1001f)).coerceIn(0.0f, 1.0f)

            // Multi-signal Weighted Composite Score:
            // Score = 0.35 * EditScore + 0.25 * SpatialTouch + 0.20 * LMProb + 0.15 * FreqScore + 0.05 * UserHabit
            var posterior: Float = (0.35f * normalizedEditScore) +
                            (0.25f * spatialLikelihood) +
                            (0.20f * lmProb) +
                            (0.15f * freqScore)

            // Dynamic Signal Boosts
            if (isExactMatch) posterior += 0.20f
            if (isKnownWord) posterior += 0.25f
            if (isTfLiteMatch) posterior += 0.50f
            if (symSpellMatch != null) posterior += (0.30f * (1.0f - (symSpellMatch.distance / 2.5f).coerceIn(0f, 1f)))
            if (nGramPredictions.contains(cleanCand)) posterior += 0.25f
            if (isGrammarFix) posterior += 0.60f
            if (phraseMatches.contains(cleanCand) || phraseMatches.contains(cand)) posterior += 0.50f
            if (commonTypoLookup.containsKey(lower) && commonTypoLookup[lower] == cleanCand) posterior += 0.55f
            if (contractionLookup.containsKey(lower) && contractionLookup[lower] == cand) posterior += 0.55f
            if (algoCandidates.contains(cleanCand)) posterior += 0.30f
            if (cleanCand.contains(" ") && cleanCand.replace(" ", "") == lower) posterior += 0.35f

            // Confidence Tier Assignment based on decoupled thresholds
            val isKnownTypo = commonTypoLookup.containsKey(lower) || contractionLookup.containsKey(lower)
            val isWholeWordHigh = posterior >= WHOLE_WORD_AUTOCORRECT_THRESHOLD && (isKnownWord || cleanCand.contains(" "))
            val confidenceTier = when {
                isGrammarFix || isKnownTypo || (isTfLiteMatch && isKnownWord) || isWholeWordHigh -> ConfidenceTier.HIGH
                posterior >= KEY_CORRECTION_THRESHOLD -> ConfidenceTier.MEDIUM
                else -> ConfidenceTier.LOW
            }

            // Autocorrect Eligibility Determination:
            // When user types a typo/invalid word, automatically correct to top valid dictionary candidate
            val isMediumAutocorrect = confidenceTier == ConfidenceTier.MEDIUM && posterior >= 0.35f
            val isAutocorrectEligible = when {
                isExactMatch -> false
                isRawCodeOrSpecial -> false
                isGrammarFix -> true
                isKnownTypo -> true
                isTfLiteMatch && isKnownWord -> true
                !isRawValidWord && isKnownWord && (confidenceTier == ConfidenceTier.HIGH || isMediumAutocorrect || editDist <= 2.0f) -> true
                isRawValidWord && contractionLookup.containsKey(lower) && contractionLookup[lower] == cand -> true
                else -> false
            }

            val reason = when {
                isExactMatch -> "Exact Typed Literal"
                isTfLiteMatch -> "TFLite Neural Model Correction"
                isGrammarFix -> "Grammar Agreement: ${localGrammarCorrection?.ruleCategory}"
                isKnownTypo -> "Known Typo / Transposition Rule"
                symSpellMatch != null -> "SymSpell Dictionary Match"
                nGramPredictions.contains(cleanCand) -> "N-Gram Language Model Prediction"
                algoCandidates.contains(cleanCand) -> "Spatial Neighbor Match"
                isAutocorrectEligible -> "High-Confidence Autocorrect"
                else -> "Candidate Suggestion"
            }

            scoredCandidates.add(
                GboardCandidate(
                    word = restoreCasing(trimmed, cand),
                    spatialScore = spatialLikelihood,
                    lmScore = lmProb,
                    editDistance = editDist,
                    frequencyScore = freqScore,
                    totalPosterior = posterior,
                    confidenceTier = confidenceTier,
                    isAutocorrectEligible = isAutocorrectEligible,
                    reason = reason,
                    scoreBreakdown = ScoreBreakdown(
                        spatialScore = spatialLikelihood,
                        frequencyScore = freqScore,
                        contextScore = lmProb,
                        editDistanceScore = normalizedEditScore,
                        personalScore = if (dictionaryManager.isWordInUserDictionary(cleanCand)) 0.85f else 0.20f,
                        totalScore = posterior
                    )
                )
            )
        }

        // Sort descending by posterior score
        val sortedCandidates = scoredCandidates.sortedByDescending { it.totalPosterior }

        // Top candidate and margin calculation
        val topCandidate = sortedCandidates.firstOrNull() ?: GboardCandidate(trimmed, 1f, 1f, 0f, 1f, 1f, ConfidenceTier.LOW, false, "Literal")
        val secondCandidate = sortedCandidates.getOrNull(1)
        val scoreMargin = if (secondCandidate != null) (topCandidate.totalPosterior - secondCandidate.totalPosterior) else 1.0f

        // Autocorrect decision with margin enforcement:
        val hasSufficientMargin = scoreMargin >= AUTOCORRECT_MARGIN || 
                                  topCandidate.reason.contains("Rule") || 
                                  topCandidate.reason.contains("Grammar") ||
                                  topCandidate.reason.contains("Typo") ||
                                  (!isRawValidWord && topCandidate.isAutocorrectEligible)
        val isCenterAutocorrecting = settings.autocorrectEnabled &&
                                     !isSensitiveField &&
                                     topCandidate.isAutocorrectEligible &&
                                     hasSufficientMargin &&
                                     topCandidate.word.lowercase() != lower

        val centerSlotWord = if (isCenterAutocorrecting) topCandidate.word else (if (lower == "i") "I" else trimmed)

        // Left Slot: Literal typed string (if middle is autocorrecting) or 2nd best candidate
        val leftSlotWord = if (isCenterAutocorrecting) {
            trimmed
        } else {
            sortedCandidates.firstOrNull { it.word.lowercase() != centerSlotWord.lowercase() }?.word ?: trimmed
        }

        // Right Slot: Semantic emoji or 3rd best candidate
        val emojiMatch = emojiIntentMap[lower]
        val rightSlotWord = emojiMatch ?: (
            sortedCandidates.firstOrNull {
                it.word.lowercase() != centerSlotWord.lowercase() && it.word.lowercase() != leftSlotWord.lowercase()
            }?.word ?: "and"
        )

        val decisionReason = if (isCenterAutocorrecting) {
            "Autocorrect [${trimmed} -> ${centerSlotWord}] (Score: %.2f, Margin: %.2f, Tier: %s, Reason: %s)".format(
                topCandidate.totalPosterior, scoreMargin, topCandidate.confidenceTier, topCandidate.reason
            )
        } else {
            "Preserve Literal [${trimmed}] (Center: ${centerSlotWord}, TopScore: %.2f)".format(topCandidate.totalPosterior)
        }

        Log.d(TAG, "[AUTOCORRECT] $decisionReason")

        val result = GboardSuggestionResult(
            leftCandidate = leftSlotWord,
            centerCandidate = centerSlotWord,
            rightCandidate = rightSlotWord,
            isCenterAutocorrecting = isCenterAutocorrecting,
            debugTelemetry = GboardTelemetry(
                rawInput = trimmed,
                contextWords = contextWords,
                topCandidates = sortedCandidates.take(5),
                touchDeltas = tapCoords?.map { sqrt(it.x * it.x + it.y * it.y) } ?: emptyList(),
                scoreMargin = scoreMargin,
                decisionReason = decisionReason
            )
        )

        synchronized(predictionCache) {
            predictionCache[cacheKey] = result
        }

        return result
    }

    private fun restoreCasing(original: String, target: String): String {
        if (original.isEmpty() || target.isEmpty()) return target
        if (properNouns.contains(target)) return target
        if (original.all { it.isUpperCase() }) return target.uppercase()
        if (original[0].isUpperCase()) {
            return target.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        return target
    }
}
