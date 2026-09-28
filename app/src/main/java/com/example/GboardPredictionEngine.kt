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
        "teh" to "the", "yhe" to "the", "hte" to "the", "tht" to "that",
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
        "tought" to "thought", "tihs" to "this", "thsi" to "this", "thid" to "this", "whcih" to "which", "abotu" to "about",
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
        "im" to "I'm", "ive" to "I've",
        "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
        "hes" to "he's", "shes" to "she's", "theyre" to "they're",
        "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
        "weve" to "we've",
        "didnt" to "didn't", "doesnt" to "doesn't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "hasnt" to "hasn't", "havent" to "haven't",
        "hadnt" to "hadn't", "wouldnt" to "wouldn't", "shouldnt" to "shouldn't", "couldnt" to "couldn't",
        "thats" to "that's", "whats" to "what's", "heres" to "here's", "theres" to "there's",
        "wheres" to "where's", "hows" to "how's"
    ) + ComprehensiveLexicon.UNPUNCTUATED_CONTRACTIONS

    private val ambiguousRealWords = setOf("were", "well", "ill", "its", "lets", "wed", "id", "your", "their", "fir", "hello")

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
     * Context-aware ambiguity resolution for words that are valid dictionary words
     * but frequently typed as unpunctuated contractions (e.g. "ill" -> "I'll", "well" -> "we'll", "id" -> "I'd", "lets" -> "let's", "its" -> "it's").
     */
    fun resolveContextualAmbiguity(word: String, contextWords: List<String>): String? {
        if (contextWords.isEmpty()) return null
        val lower = word.lowercase(Locale.ROOT)
        if (!ambiguousRealWords.contains(lower)) return null
        val prevWord = contextWords.lastOrNull()?.lowercase(Locale.ROOT) ?: return null
        if (prevWord.isEmpty()) return null
        val resolved = when (lower) {
            "ill" -> if (prevWord == "i") "I'll" else null
            "well" -> if (prevWord == "we") "we'll" else null
            "id" -> if (prevWord in listOf("i", "if", "that", "what", "how", "when", "why")) "I'd" else null
            "lets" -> if (prevWord in listOf("so", "and", "then", "now", "okay", "ok", "yes", "well")) "let's" else null
            "its" -> if (prevWord in listOf("think", "believe", "know", "guess", "sure", "because", "said", "say", "thought")) "it's" else null
            else -> null
        }
        return resolved?.let { restoreCasing(word, it) }
    }

    /**
     * Missed space segmentation (goodmorning -> good morning, thankyou -> thank you, alot -> a lot)
     */
    fun segmentMissedSpaces(raw: String, dictionaryManager: DictionaryManager): String? {
        val clean = raw.lowercase(Locale.ROOT).trim()
        if (clean.length < 4) return null

        // 1. If the word itself is an established valid dictionary word, NEVER split it!
        if (dictionaryManager.isWordInDictionary(clean) ||
            dictionaryManager.isWordInUserDictionary(clean) ||
            symSpellEngine.hasWord(clean)) {
            return null
        }

        // 2. Whitelist common digital run-together tokens that are always intended to be split
        val forcedSplitMap = mapOf(
            "alot" to "a lot", "infront" to "in front", "atleast" to "at least",
            "aswell" to "as well", "ofcourse" to "of course", "thankyou" to "thank you",
            "goodmorning" to "good morning", "goodnight" to "good night", "howareyou" to "how are you",
            "seeyou" to "see you", "loveyou" to "love you", "letsgo" to "let's go",
            "withyou" to "with you", "goingto" to "going to", "wantto" to "want to",
            "bytheway" to "by the way", "nevermind" to "never mind", "eachother" to "each other",
            "allright" to "all right", "noone" to "no one", "cantwait" to "can't wait",
            "dontworry" to "don't worry", "dontknow" to "don't know", "rightnow" to "right now",
            "takecare" to "take care", "goodluck" to "good luck", "havefun" to "have fun",
            "howmuch" to "how much", "howmany" to "how many", "thanksalot" to "thanks a lot"
        )
        // Only split explicit whitelisted run-together phrases; never arbitrarily split words or typos
        return forcedSplitMap[clean]
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
            val top = pool.filter { it.isNotEmpty() && !dictionaryManager.isBlocked(it) && it.all { c -> c.isLetter() || c == '\'' } }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .sortedByDescending { model.getProbability(it.lowercase(Locale.ROOT), context) }
                .take(3)
            return GboardSuggestionResult(top.getOrElse(1) { "" }, top.getOrElse(0) { "" }, top.getOrElse(2) { "" }, false)
        }
        // Bounded work: never run sentence proofreading, exhaustive mutation generation,
        // multiple fuzzy engines, or network inference for each keystroke.
        if (typed.length > 32 || typed.any { !it.isLetter() && it != '\'' } ||
            dictionaryManager.isCodeOrSpecialToken(typed)) {
            return GboardSuggestionResult("", if (!dictionaryManager.isBlocked(typed)) typed else "", "", false)
        }
        val direct = immediateCorrection(typed, dictionaryManager)?.takeIf { !dictionaryManager.isBlocked(it) }
        val contextualDirect = if (direct == null) resolveContextualAmbiguity(lower, context)?.takeIf { !dictionaryManager.isBlocked(it) } else null
        val effectiveDirect = direct ?: contextualDirect

        val rawIsValid = (dictionaryManager.isWordInDictionary(lower) || symSpellEngine.hasWord(lower)) && !dictionaryManager.isBlocked(lower)
        val matches = if (lower.length >= 2 && !rawIsValid) {
            symSpellEngine.lookup(lower, maxDistance = if (lower.length <= 3) 1f else 2f, maxResults = 16).filter { !dictionaryManager.isBlocked(it.term) }
        } else emptyList()
        val googleSpellChecker = GoogleDeviceSpellChecker.getInstance(this.context)
        val deviceSpellMatches = if (lower.length >= 3 && !rawIsValid) {
            googleSpellChecker.getSpellCheckSuggestions(lower).filter { !dictionaryManager.isBlocked(it) }
        } else emptyList()
        val pool = linkedSetOf<String>()
        effectiveDirect?.let { pool.add(it) }
        pool.addAll(deviceSpellMatches)
        pool.addAll(dictionaryManager.wordTrie.findByPrefix(lower, 8).filter { !dictionaryManager.isBlocked(it) })
        pool.addAll(model.predictNextWords(context, lower, 6).filter { !dictionaryManager.isBlocked(it) })
        pool.addAll(matches.map { it.term })
        if (!dictionaryManager.isBlocked(typed)) {
            pool.add(typed)
        }

        val sensitivity = settings.autocorrectSensitivity
        val minMargin = when (sensitivity) {
            KeyboardSettings.SENSITIVITY_MILD -> 0.16f
            KeyboardSettings.SENSITIVITY_AGGRESSIVE -> 0.03f
            else -> 0.08f
        }
        val maxDist = when (sensitivity) {
            KeyboardSettings.SENSITIVITY_MILD -> 1.2f
            KeyboardSettings.SENSITIVITY_AGGRESSIVE -> 2.4f
            else -> 2.1f
        }

        val candidates = pool.filter { it.isNotBlank() && !dictionaryManager.isBlocked(it) && (it == effectiveDirect || it.none(Char::isWhitespace)) }
            .distinctBy { it.lowercase(Locale.ROOT) }.map { word ->
                val normalized = word.lowercase(Locale.ROOT)
                val literal = normalized == lower && word == typed
                val deterministic = effectiveDirect != null && normalized == effectiveDirect.lowercase(Locale.ROOT)
                val isGoogleSpellMatch = deviceSpellMatches.any { it.equals(normalized, ignoreCase = true) }
                val match = matches.firstOrNull { it.term == normalized }
                val distance = match?.distance ?: spatialModel.computeSpatialEditDistance(lower, normalized)
                val frequency = (log10(dictionaryManager.getWordFrequency(normalized).toFloat() + 1f) / 3f).coerceIn(0f, 1f)
                val probability = model.getProbability(normalized, context)
                val spatial = spatialModel.computeSpatialTouchLikelihood(normalized, tapCoords)
                val completion = normalized.startsWith(lower) && normalized.length > lower.length
                val score = if (deterministic) 2f else
                    0.45f * (1f - distance / maxOf(3, lower.length).toFloat()).coerceIn(0f, 1f) +
                    0.25f * frequency + 0.20f * probability + 0.10f * spatial +
                    (if (literal && rawIsValid) 0.5f else 0f) + (if (completion) 0.05f else 0f) +
                    (if (isGoogleSpellMatch) 0.45f else 0f)
                val eligible = !literal && (!completion || deterministic) && !dictionaryManager.isCorrectionSuppressed(typed, word) && !dictionaryManager.isBlocked(word) &&
                    (deterministic || isGoogleSpellMatch || (!rawIsValid && lower.length >= 2 && distance <= maxDist && frequency >= 0.04f))
                GboardCandidate(TypingPolicy.restoreCase(typed, word), spatial, probability, distance,
                    frequency, score, if (eligible) ConfidenceTier.HIGH else ConfidenceTier.LOW,
                    eligible, if (deterministic) "Curated typo" else if (isGoogleSpellMatch) "Google Spellcheck" else if (completion) "Completion" else "Dictionary candidate")
            }.sortedWith(compareByDescending<GboardCandidate> { it.totalPosterior }.thenBy { it.word })
        val best = candidates.firstOrNull()
        val margin = if (best != null) best.totalPosterior - (candidates.getOrNull(1)?.totalPosterior ?: 0f) else 0f
        val isAmbiguousOrRealWord = (ambiguousRealWords.contains(lower) && contextualDirect == null) || rawIsValid

        val completions = if (lower.length >= 2) dictionaryManager.wordTrie.findByPrefix(lower, 4).filter { it.length > lower.length && !dictionaryManager.isBlocked(it) } else emptyList()
        val maxCompletionFreq = completions.maxOfOrNull { dictionaryManager.getWordFrequency(it) } ?: 0
        val bestTermFreq = best?.word?.let { dictionaryManager.getWordFrequency(it.lowercase(Locale.ROOT)) } ?: 0
        val completionSuppresses = when (sensitivity) {
            KeyboardSettings.SENSITIVITY_AGGRESSIVE -> false
            KeyboardSettings.SENSITIVITY_MILD -> completions.isNotEmpty()
            else -> completions.isNotEmpty() && maxCompletionFreq > bestTermFreq * 2
        }

        val auto = settings.autocorrectEnabled && best?.isAutocorrectEligible == true &&
            !dictionaryManager.isBlocked(best!!.word) &&
            (effectiveDirect != null || (!isAmbiguousOrRealWord && !completionSuppresses && margin >= minMargin))
        val center = if (auto && !dictionaryManager.isBlocked(best!!.word)) {
            best.word
        } else if (rawIsValid && !dictionaryManager.isBlocked(typed)) {
            typed
        } else {
            best?.word?.takeIf { !dictionaryManager.isBlocked(it) } ?: if (!dictionaryManager.isBlocked(typed)) typed else ""
        }
        val left = if (!center.equals(typed, true) && !dictionaryManager.isBlocked(typed)) {
            typed
        } else {
            candidates.firstOrNull { !it.word.equals(center, true) && !dictionaryManager.isBlocked(it.word) }?.word.orEmpty()
        }
        val right = candidates.firstOrNull { !it.word.equals(center, true) && !it.word.equals(left, true) && !dictionaryManager.isBlocked(it.word) }?.word.orEmpty()
        return GboardSuggestionResult(left, center, right, auto, GboardTelemetry(
            typed, context, candidates.take(5), emptyList(), margin,
            if (auto) "High confidence correction" else "Preserve typed text on commit"
        ))
    }

    /** Fast synchronous candidate evaluation for space/punctuation commit */
    fun getBestAutocorrectCandidate(
        typed: String,
        contextWords: List<String>,
        dictionaryManager: DictionaryManager,
        tapCoords: List<PointF>? = null
    ): String? {
        if (!settings.autocorrectEnabled || typed.length < 2 || typed.length > 32) return null
        if (typed.any { !it.isLetter() && it != '\'' } || dictionaryManager.isCodeOrSpecialToken(typed)) return null

        val lower = typed.lowercase(Locale.ROOT)
        if (dictionaryManager.isBlocked(lower)) return null
        // 1. Contraction lookups (dont -> don't, cant -> can't, shouldve -> should've)
        contractionLookup[lower]?.let {
            val restored = restoreCasing(typed, it)
            if (!dictionaryManager.isCorrectionSuppressed(typed, restored) && !dictionaryManager.isBlocked(restored)) return restored
        }

        // 2. Contextual ambiguity resolution
        resolveContextualAmbiguity(lower, contextWords)?.let {
            val restored = restoreCasing(typed, it)
            if (!dictionaryManager.isCorrectionSuppressed(typed, restored) && !dictionaryManager.isBlocked(restored)) return restored
        }

        // 3. If user typed an already valid word, preserve it! Never split valid words
        if (dictionaryManager.isWordInDictionary(lower) || dictionaryManager.isWordInUserDictionary(lower) || symSpellEngine.hasWord(lower)) {
            return null
        }

        // 4. Common typos & missed space segmentation for invalid tokens
        immediateCorrection(typed, dictionaryManager)?.let { if (!dictionaryManager.isBlocked(it)) return it }

        // 5. Symmetric deletion / edit distance candidate scoring
        val sensitivity = settings.autocorrectSensitivity
        val maxDist = when (sensitivity) {
            KeyboardSettings.SENSITIVITY_MILD -> if (lower.length <= 3) 1.0f else 1.2f
            KeyboardSettings.SENSITIVITY_AGGRESSIVE -> if (lower.length <= 3) 1.5f else 2.4f
            else -> if (lower.length <= 3) 1.0f else 2.0f
        }
        val minScoreThreshold = when (sensitivity) {
            KeyboardSettings.SENSITIVITY_MILD -> 0.60f
            KeyboardSettings.SENSITIVITY_AGGRESSIVE -> 0.35f
            else -> 0.45f
        }

        val matches = symSpellEngine.lookup(lower, maxDistance = maxDist, maxResults = 16).filter { !dictionaryManager.isBlocked(it.term) }
        if (matches.isEmpty()) return null

        val context = contextWords.takeLast(2).map { it.lowercase(Locale.ROOT) }
        val model = dictionaryManager.nGramModel

        val scored = matches.map { match ->
            val term = match.term
            val freq = (log10(dictionaryManager.getWordFrequency(term).toFloat() + 1f) / 3f).coerceIn(0.05f, 1f)
            val prob = model.getProbability(term, context)
            val distScore = (1.0f - (match.distance / maxOf(3, lower.length).toFloat())).coerceIn(0f, 1f)
            val spatial = spatialModel.computeSpatialTouchLikelihood(term, tapCoords)
            val score = 0.45f * distScore + 0.30f * freq + 0.15f * prob + 0.10f * spatial
            term to score
        }.sortedByDescending { it.second }

        val best = scored.firstOrNull() ?: return null
        if (best.second < minScoreThreshold) return null

        val restored = restoreCasing(typed, best.first)
        return if (!dictionaryManager.isCorrectionSuppressed(typed, restored) && !dictionaryManager.isBlocked(restored)) restored else null
    }

    /** Constant-time fallback for fast typists; follows exactly the same suppression policy. */
    fun immediateCorrection(typed: String, dictionaryManager: DictionaryManager): String? {
        if (!settings.autocorrectEnabled || dictionaryManager.isWordInUserDictionary(typed.lowercase(Locale.ROOT)) || dictionaryManager.isBlocked(typed)) return null
        val lower = typed.lowercase(Locale.ROOT)
        if (ambiguousRealWords.contains(lower)) return null
        
        // 1. Standalone single letter 'i' -> 'I'
        if (typed == "i") {
            return if (!dictionaryManager.isCorrectionSuppressed("i", "I") && !dictionaryManager.isBlocked("I")) "I" else null
        }
        
        // 2. High-confidence Contraction expansions (e.g. dont -> don't, cant -> can't, im -> I'm)
        contractionLookup[lower]?.let { contraction ->
            val restored = restoreCasing(typed, contraction)
            if (!dictionaryManager.isCorrectionSuppressed(typed, restored) && !dictionaryManager.isBlocked(restored)) {
                return restored
            }
        }
        
        // 3. High-frequency common typos & transpositions (e.g. teh -> the, becuase -> because, recieve -> receive)
        commonTypoLookup[lower]?.let { typoFix ->
            val restored = restoreCasing(typed, typoFix)
            if (!dictionaryManager.isCorrectionSuppressed(typed, restored) && !dictionaryManager.isBlocked(restored)) {
                return restored
            }
        }

        // 4. Missed-space run-together phrases (e.g. thankyou -> thank you, goodmorning -> good morning)
        segmentMissedSpaces(lower, dictionaryManager)?.let { spaced ->
            if (!dictionaryManager.isCorrectionSuppressed(typed, spaced) && !dictionaryManager.isBlocked(spaced)) {
                return spaced
            }
        }
        
        // 5. Curated typing policy direct corrections
        val direct = TypingPolicy.correction(typed)
        if (direct != null && !dictionaryManager.isCorrectionSuppressed(typed, direct) && !dictionaryManager.isBlocked(direct)) {
            return direct
        }
        
        // 6. Google device spell check suggestions
        if (lower.length >= 3 && !dictionaryManager.isWordInDictionary(lower)) {
            val googleSpell = GoogleDeviceSpellChecker.getInstance(this.context).getSpellCheckSuggestions(lower)
            if (googleSpell.isNotEmpty()) {
                val candidate = restoreCasing(typed, googleSpell.first())
                if (!candidate.contains(" ") && !dictionaryManager.isCorrectionSuppressed(typed, candidate) && !dictionaryManager.isBlocked(candidate)) {
                    return candidate
                }
            }
        }
        return null
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
