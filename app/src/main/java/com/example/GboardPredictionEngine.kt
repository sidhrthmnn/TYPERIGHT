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
import kotlin.math.roundToInt
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
    val spatialModel = SpatialKeyProximityModel()
    val symSpellEngine = SymSpellCorrectionEngine(spatialModel, maxEditDistance = 2)

    companion object {
        private const val TAG = "TypeRightAutoCorrect"

        // 6. Decoupled Confidence Gating Thresholds
        const val KEY_CORRECTION_THRESHOLD = 0.35f
        const val WHOLE_WORD_AUTOCORRECT_THRESHOLD = 0.45f
        // A correction should clearly beat the runner-up. Tiny score differences are
        // common for names, slang, and multilingual input and should stay suggestions.
        const val AUTOCORRECT_MARGIN = 0.12f
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
    ) + ComprehensiveLexicon.TYPOS

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
    ) + ComprehensiveLexicon.UNPUNCTUATED_CONTRACTIONS

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
        if (isSensitiveField) return GboardSuggestionResult("", "", "", false)
        val typed = rawTyped.trim()
        val lower = typed.lowercase(Locale.ROOT)
        val model = dictionaryManager.nGramModel
        val context = contextWords.takeLast(3).map { it.lowercase(Locale.ROOT) }
        if (typed.isEmpty()) {
            val learned = context.lastOrNull()?.let { mlPredictor.predictNextWords(it).map { pair -> pair.first } }.orEmpty()
            val pool = model.predictNextWords(context, "", 12) + learned +
                if (context.isEmpty()) listOf("I", "The", "Hi") else listOf("the", "to", "and", "you")
            val top = pool.filter { it.isNotEmpty() && it.all { c -> c.isLetter() || c == '\'' } }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedByDescending { model.getProbability(it.lowercase(Locale.ROOT), context) }
                .take(3)
            return GboardSuggestionResult(top.getOrElse(1) { "" }, top.getOrElse(0) { "" }, top.getOrElse(2) { "" }, false)
        }
        // Bounded work: never run sentence proofreading, exhaustive mutation generation,
        // multiple fuzzy engines, or network inference for each keystroke.
        if (typed.length > 32 || typed.any { !it.isLetter() && it != '\'' } ||
            dictionaryManager.isCodeOrSpecialToken(typed)) {
            return GboardSuggestionResult("", typed, "", false)
        }
        val direct = immediateCorrection(typed, dictionaryManager)
        val rawIsValid = dictionaryManager.isWordInDictionary(lower) || symSpellEngine.hasWord(lower)
        val matches = if (lower.length >= 3 && !rawIsValid) {
            symSpellEngine.lookup(lower, maxDistance = if (lower.length < 5) 1f else 2f, maxResults = 12)
        } else emptyList()
        val pool = linkedSetOf<String>()
        direct?.let { pool.add(it) }
        pool.addAll(dictionaryManager.wordTrie.findByPrefix(lower, 8))
        pool.addAll(model.predictNextWords(context, lower, 6))
        pool.addAll(matches.map { it.term })
        pool.add(typed)
        val candidates = pool.filter { it.isNotBlank() && (it == direct || it.none(Char::isWhitespace)) }
            .distinctBy { it.lowercase(Locale.ROOT) }.map { word ->
                val normalized = word.lowercase(Locale.ROOT)
                val literal = normalized == lower && word == typed
                val deterministic = direct != null && normalized == direct.lowercase(Locale.ROOT)
                val match = matches.firstOrNull { it.term == normalized }
                val distance = match?.distance ?: spatialModel.computeSpatialEditDistance(lower, normalized)
                val frequency = (log10(dictionaryManager.getWordFrequency(normalized).toFloat() + 1f) / 3f).coerceIn(0f, 1f)
                val probability = model.getProbability(normalized, context)
                val spatial = spatialModel.computeSpatialTouchLikelihood(normalized, tapCoords)
                val completion = normalized.startsWith(lower) && normalized.length > lower.length
                val score = if (deterministic) 2f else
                    0.55f * (1f - distance / maxOf(3, lower.length).toFloat()).coerceIn(0f, 1f) +
                    0.20f * frequency + 0.15f * probability + 0.10f * spatial +
                    (if (literal && rawIsValid) 0.6f else 0f) + (if (completion) 0.1f else 0f)
                val eligible = !literal && !completion && !dictionaryManager.isCorrectionSuppressed(typed, word) &&
                    (deterministic || (!rawIsValid && lower.length >= 4 && typed == lower &&
                        match != null && distance <= 1f && frequency >= 0.65f))
                GboardCandidate(TypingPolicy.restoreCase(typed, word), spatial, probability, distance,
                    frequency, score, if (eligible) ConfidenceTier.HIGH else ConfidenceTier.LOW,
                    eligible, if (deterministic) "Curated typo" else if (completion) "Completion" else "Dictionary candidate")
            }.sortedWith(compareByDescending<GboardCandidate> { it.totalPosterior }.thenBy { it.word })
        val best = candidates.firstOrNull()
        val margin = if (best != null) best.totalPosterior - (candidates.getOrNull(1)?.totalPosterior ?: 0f) else 0f
        val auto = settings.autocorrectEnabled && best?.isAutocorrectEligible == true &&
            (direct != null && best.word.equals(direct, true) || margin >= AUTOCORRECT_MARGIN)
        val center = best?.word ?: typed
        val left = if (!center.equals(typed, true)) typed else candidates.firstOrNull { !it.word.equals(center, true) }?.word.orEmpty()
        val right = candidates.firstOrNull { !it.word.equals(center, true) && !it.word.equals(left, true) }?.word.orEmpty()
        return GboardSuggestionResult(left, center, right, auto, GboardTelemetry(
            typed, context, candidates.take(5), emptyList(), margin,
            if (auto) "High confidence correction" else "Preserve typed text on commit"
        ))
    }

    /** Constant-time fallback for fast typists; follows exactly the same suppression policy. */
    fun immediateCorrection(typed: String, dictionaryManager: DictionaryManager): String? {
        if (!settings.autocorrectEnabled || dictionaryManager.isWordInUserDictionary(typed.lowercase(Locale.ROOT))) return null
        val correction = TypingPolicy.correction(typed) ?: return null
        return correction.takeUnless { dictionaryManager.isCorrectionSuppressed(typed, it) }
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
