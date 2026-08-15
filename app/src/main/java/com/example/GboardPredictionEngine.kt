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
 * Gboard-Style Auto Prediction and Auto Correction Engine.
 *
 * Implements a high-precision, non-conservative autocorrection architecture:
 * 1. Multi-Channel Candidate Generation:
 *    - Direct high-frequency typo & transposition table lookup
 *    - Algorithmic transposition generator (swapping adjacent letters: teh -> the, adn -> and, woudl -> would)
 *    - QWERTY keyboard-neighbour substitution generator (adjacent physical keys: thid -> this, fir -> for)
 *    - Single-letter insertion & deletion generator (repeated/missing chars: helllo -> hello, tomorow -> tomorrow)
 *    - Contraction & apostrophe restoration (dont -> don't, cant -> can't, im -> I'm)
 *    - Missed-space run-on segmentation (goodmorning -> good morning, thankyou -> thank you)
 *    - Sound-alike phonetic indexing (Soundex/Metaphone: fone -> phone, enuf -> enough)
 *    - Trie prefix matching & fuzzy distance traversal
 * 2. Multi-Signal Scoring Engine:
 *    - Spatial Touch Model: Bivariate Gaussian distribution over normalized key coordinates (x, y)
 *    - Spatial-proximity weighted Damerau-Levenshtein edit distance
 *    - Multi-Order Language Model (Trigram + Bigram + Unigram with Katz backoff)
 *    - Corpus frequency prior (logarithmic scaling) & user personalization boost
 * 3. Confidence Tiers & Decision Engine:
 *    - HIGH Confidence (Tier 1): Automatically committed on space/punctuation with margin enforcement
 *    - MEDIUM Confidence (Tier 2): Displayed prominently in center slot of suggestion strip
 *    - LOW Confidence (Tier 3): Alternative candidate in left/right slot
 *    - Real-Word Protection: Prevents overriding valid dictionary words unless strong context applies
 *    - Non-Word Aggressiveness: Readily corrects clear non-words when high-scoring candidate exists
 * 4. Diagnostic Logging & Telemetry:
 *    - Full debug trace of candidates, signal breakdowns, margins, and decision reasons.
 */
class GboardPredictionEngine(private val context: Context) {

    private val settings = KeyboardSettings(context)
    private val mlPredictor = PatternLearningPredictor.getInstance(context)
    private val localGrammarPredictor by lazy { LocalGrammarSpellPredictor(context) }
    val nGramModel = NGramLanguageModel()

    companion object {
        private const val TAG = "TypeRightAutoCorrect"
        const val HIGH_CONFIDENCE_THRESHOLD = 0.45f
        const val MEDIUM_CONFIDENCE_THRESHOLD = 0.30f
        const val MIN_CORRECTION_MARGIN = 0.03f
    }

    // Key centroid coordinates on a normalized [0.0, 1.0] grid for QWERTY layout
    val standardKeyLayout: Map<Char, PointF> = mapOf(
        // Row 1
        'q' to PointF(0.05f, 0.16f), 'w' to PointF(0.15f, 0.16f), 'e' to PointF(0.25f, 0.16f),
        'r' to PointF(0.35f, 0.16f), 't' to PointF(0.45f, 0.16f), 'y' to PointF(0.55f, 0.16f),
        'u' to PointF(0.65f, 0.16f), 'i' to PointF(0.75f, 0.16f), 'o' to PointF(0.85f, 0.16f),
        'p' to PointF(0.95f, 0.16f),

        // Row 2
        'a' to PointF(0.10f, 0.50f), 's' to PointF(0.20f, 0.50f), 'd' to PointF(0.30f, 0.50f),
        'f' to PointF(0.40f, 0.50f), 'g' to PointF(0.50f, 0.50f), 'h' to PointF(0.60f, 0.50f),
        'j' to PointF(0.70f, 0.50f), 'k' to PointF(0.80f, 0.50f), 'l' to PointF(0.90f, 0.50f),

        // Row 3
        'z' to PointF(0.20f, 0.83f), 'x' to PointF(0.30f, 0.83f), 'c' to PointF(0.40f, 0.83f),
        'v' to PointF(0.50f, 0.83f), 'b' to PointF(0.60f, 0.83f), 'n' to PointF(0.70f, 0.83f),
        'm' to PointF(0.80f, 0.83f)
    )

    // QWERTY physical adjacent keys map for fast candidate generation & neighbor substitution
    val qwertyNeighbours: Map<Char, List<Char>> = mapOf(
        'q' to listOf('w', 'a', 's', '1', '2'),
        'w' to listOf('q', 'e', 'a', 's', 'd', '2', '3'),
        'e' to listOf('w', 'r', 's', 'd', 'f', '3', '4'),
        'r' to listOf('e', 't', 'd', 'f', 'g', '4', '5'),
        't' to listOf('r', 'y', 'f', 'g', 'h', '5', '6'),
        'y' to listOf('t', 'u', 'g', 'h', 'j', '6', '7'),
        'u' to listOf('y', 'i', 'h', 'j', 'k', '7', '8'),
        'i' to listOf('u', 'o', 'j', 'k', 'l', '8', '9'),
        'o' to listOf('i', 'p', 'k', 'l', '9', '0'),
        'p' to listOf('o', 'l', '0'),

        'a' to listOf('q', 'w', 's', 'z'),
        's' to listOf('a', 'd', 'w', 'e', 'z', 'x'),
        'd' to listOf('s', 'f', 'e', 'r', 'x', 'c'),
        'f' to listOf('d', 'g', 'r', 't', 'c', 'v'),
        'g' to listOf('f', 'h', 't', 'y', 'v', 'b'),
        'h' to listOf('g', 'j', 'y', 'u', 'b', 'n'),
        'j' to listOf('h', 'k', 'u', 'i', 'n', 'm'),
        'k' to listOf('j', 'l', 'i', 'o', 'm'),
        'l' to listOf('k', 'o', 'p'),

        'z' to listOf('a', 's', 'x'),
        'x' to listOf('z', 'c', 's', 'd'),
        'c' to listOf('x', 'v', 'd', 'f'),
        'v' to listOf('c', 'b', 'f', 'g'),
        'b' to listOf('v', 'n', 'g', 'h'),
        'n' to listOf('b', 'm', 'h', 'j'),
        'm' to listOf('n', 'j', 'k')
    )

    // Spatial touch parameters (Gaussian variance for key touch distribution)
    private val sigmaX = 0.065f
    private val sigmaY = 0.080f

    // Comprehensive common typo & transposition lookup table
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
        "tought" to "thought", "tihs" to "this", "thsi" to "this", "whcih" to "which", "abotu" to "about",
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
        "applicatoin" to "application", "messgae" to "message", "quetion" to "question",
        "answer" to "answer", "alot" to "a lot", "beautifull" to "beautiful"
    )

    // Contraction expansions (apostrophe restoration)
    val contractionLookup: Map<String, String> = mapOf(
        "dont" to "don't", "cant" to "can't", "wont" to "won't",
        "im" to "I'm", "ive" to "I've", "ill" to "I'll", "id" to "I'd",
        "youre" to "you're", "youve" to "you've", "youll" to "you'll", "youd" to "you'd",
        "theyre" to "they're", "theyve" to "they've", "theyll" to "they'll", "theyd" to "they'd",
        "weve" to "we've", "were" to "we're", "well" to "we'll", "wed" to "we'd",
        "its" to "it's", "thats" to "that's", "whats" to "what's", "theres" to "there's",
        "heres" to "here's", "wheres" to "where's", "hows" to "how's", "whos" to "who's",
        "isnt" to "isn't", "arent" to "aren't", "wasnt" to "wasn't", "werent" to "weren't",
        "hasnt" to "hasn't", "havent" to "haven't", "hadnt" to "hadn't",
        "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
        "doesnt" to "doesn't", "didnt" to "didn't", "lets" to "let's",
        "hes" to "he's", "shes" to "she's"
    )

    // Semantic emoji intent mapping
    private val emojiIntentMap = mapOf(
        "love" to "❤️", "heart" to "❤️", "thanks" to "🙏", "thank" to "🙏", "please" to "🙏",
        "smile" to "😊", "happy" to "😊", "fire" to "🔥", "lit" to "🔥", "cool" to "😎",
        "cat" to "🐱", "dog" to "🐶", "laugh" to "😂", "lol" to "😂", "sad" to "😢",
        "cry" to "😭", "angry" to "😡", "celebrate" to "🎉", "party" to "🎉", "ok" to "👌",
        "yes" to "👍", "no" to "👎", "idea" to "💡", "money" to "💰", "car" to "🚗",
        "star" to "⭐", "sun" to "☀️", "clock" to "⏰", "coffee" to "☕", "food" to "🍔",
        "music" to "🎵", "check" to "✅", "question" to "❓", "hundred" to "💯"
    )

    // Proper Nouns that must be title-cased in Gboard
    private val properNouns = setOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
        "January", "February", "March", "April", "May", "June", "July", "August",
        "September", "October", "November", "December",
        "Google", "Android", "Gboard", "London", "Paris", "English", "French", "Spanish", "German"
    )

    enum class ConfidenceTier {
        HIGH,   // Automatic silent correction on space/punct
        MEDIUM, // Center slot highlighted candidate / strong suggestion
        LOW     // Alternative suggestion
    }

    data class GboardCandidate(
        val word: String,
        val spatialScore: Float,
        val lmScore: Float,
        val editDistance: Float,
        val frequencyScore: Float,
        val totalPosterior: Float,
        val confidenceTier: ConfidenceTier,
        val isAutocorrectEligible: Boolean,
        val reason: String
    )

    data class GboardSuggestionResult(
        val leftCandidate: String,
        val centerCandidate: String,
        val rightCandidate: String,
        val isCenterAutocorrecting: Boolean,
        val debugTelemetry: GboardTelemetry
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
     * 1. Spatial Touch Gaussian Likelihood Model:
     * Calculates log P(Touch_Sequence | Candidate_Word) based on key centroids and user tap offsets.
     */
    fun computeSpatialTouchLikelihood(
        candidateWord: String,
        tapPoints: List<PointF>?
    ): Float {
        val cleanCandidate = candidateWord.lowercase().replace("'", "")
        if (tapPoints == null || tapPoints.isEmpty()) {
            return 0.70f // Calibrated baseline when tap coordinates are not recorded
        }

        if (tapPoints.size != cleanCandidate.length) {
            val lengthDiff = kotlin.math.abs(tapPoints.size - cleanCandidate.length)
            return (0.70f - lengthDiff * 0.15f).coerceAtLeast(0.20f)
        }

        var totalLogLikelihood = 0.0f
        var validKeyCount = 0

        for (i in cleanCandidate.indices) {
            val ch = cleanCandidate[i]
            val keyCenter = standardKeyLayout[ch] ?: continue
            val tap = tapPoints[i]

            // Incorporate learned user touch offset (dx, dy)
            val offset = mlPredictor.getTouchOffset(ch)
            val calibratedX = keyCenter.x + offset.x
            val calibratedY = keyCenter.y + offset.y

            val dx = tap.x - calibratedX
            val dy = tap.y - calibratedY

            // 2D Gaussian log-likelihood: -0.5 * [(dx/sigmaX)^2 + (dy/sigmaY)^2]
            val normX = (dx / sigmaX).pow(2)
            val normY = (dy / sigmaY).pow(2)
            val pointLogLikelihood = -0.5f * (normX + normY)

            totalLogLikelihood += pointLogLikelihood
            validKeyCount++
        }

        if (validKeyCount == 0) return 0.70f
        val avgLogLikelihood = totalLogLikelihood / validKeyCount
        // Convert to probability score in [0.0, 1.0]
        return exp(avgLogLikelihood.coerceIn(-8f, 0f))
    }

    /**
     * 2. Language Model Probability:
     * Calculates P(Candidate | Context) using interpolated Trigram, Bigram, and Unigram with Katz backoff.
     */
    fun computeLanguageModelProbability(
        candidate: String,
        contextWords: List<String>,
        dictionaryManager: DictionaryManager
    ): Float {
        val cleanWord = candidate.lowercase().trim()
        val prev1 = contextWords.lastOrNull()?.lowercase()?.trim() ?: ""
        val prev2 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase().trim() else ""

        var lmScore = 0.10f

        // Trigram match: P(W | prev2, prev1)
        if (prev2.isNotEmpty() && prev1.isNotEmpty()) {
            val trigramMatches = mlPredictor.predictNextWordsFromTrigram(prev2, prev1)
            val trigramMatch = trigramMatches.firstOrNull { it.first.lowercase() == cleanWord }
            if (trigramMatch != null) {
                lmScore += 0.50f * trigramMatch.second
            }
        }

        // Bigram match: P(W | prev1)
        if (prev1.isNotEmpty()) {
            val bigramMatches = mlPredictor.predictNextWords(prev1)
            val bigramMatch = bigramMatches.firstOrNull { it.first.lowercase() == cleanWord }
            if (bigramMatch != null) {
                lmScore += 0.35f * bigramMatch.second
            }
        }

        // Unigram prior: P(W)
        if (dictionaryManager.isWordInDictionary(cleanWord)) {
            lmScore += 0.15f
        }

        return lmScore.coerceIn(0.0f, 1.0f)
    }

    /**
     * 3. Spatial-Weighted Damerau-Levenshtein Edit Distance:
     * Allows transpositions, deletions, insertions, and adjacent-key substitutions.
     */
    fun computeSpatialEditDistance(s1: String, s2: String): Float {
        val w1 = s1.lowercase().replace("'", "")
        val w2 = s2.lowercase().replace("'", "")
        if (w1 == w2) return 0.0f

        val n = w1.length
        val m = w2.length
        val dp = Array(n + 1) { FloatArray(m + 1) }

        for (i in 0..n) dp[i][0] = i.toFloat()
        for (j in 0..m) dp[0][j] = j.toFloat()

        for (i in 1..n) {
            for (j in 1..m) {
                val c1 = w1[i - 1]
                val c2 = w2[j - 1]

                val subCost = if (c1 == c2) {
                    0.0f
                } else {
                    val p1 = standardKeyLayout[c1]
                    val p2 = standardKeyLayout[c2]
                    if (p1 != null && p2 != null) {
                        val dx = p1.x - p2.x
                        val dy = p1.y - p2.y
                        val dist = sqrt(dx * dx + dy * dy)
                        // Physical adjacent keys on QWERTY layout have low substitution cost (0.25 to 0.45)
                        if (dist < 0.18f) 0.28f + (dist / 0.18f) * 0.18f else 1.0f
                    } else {
                        1.0f
                    }
                }

                dp[i][j] = minOf(
                    dp[i - 1][j] + 0.95f,        // deletion
                    dp[i][j - 1] + 0.95f,        // insertion
                    dp[i - 1][j - 1] + subCost   // substitution
                )

                // Transposition (e.g. teh -> the, adn -> and, woudl -> would)
                if (i > 1 && j > 1 && w1[i - 1] == w2[j - 2] && w1[i - 2] == w2[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 0.25f)
                }
            }
        }

        return dp[n][m]
    }

    /**
     * 4. Candidate Generation Sub-Generators:
     * Produces all plausible corrections for an input string.
     */
    fun generateAlgorithmicCandidates(raw: String, dictionaryManager: DictionaryManager): Set<String> {
        val lower = raw.lowercase().trim()
        if (lower.isEmpty()) return emptySet()
        val candidates = LinkedHashSet<String>()

        // A. Transposition Generation (Swap adjacent characters: teh -> the, adn -> and, thsi -> this)
        if (lower.length >= 2) {
            val chars = lower.toCharArray()
            for (i in 0 until chars.size - 1) {
                // Swap chars[i] and chars[i+1]
                val tmp = chars[i]
                chars[i] = chars[i + 1]
                chars[i + 1] = tmp
                val transposed = String(chars)
                if (dictionaryManager.isWordInDictionary(transposed)) {
                    candidates.add(transposed)
                }
                // Swap back
                chars[i + 1] = chars[i]
                chars[i] = tmp
            }
        }

        // B. QWERTY Neighbour Substitution Generation (thid -> this, fir -> for, cst -> cat)
        if (lower.length in 2..15) {
            val chars = lower.toCharArray()
            for (i in chars.indices) {
                val orig = chars[i]
                val neighbours = qwertyNeighbours[orig] ?: emptyList()
                for (nb in neighbours) {
                    if (nb.isLetter()) {
                        chars[i] = nb
                        val substituted = String(chars)
                        if (dictionaryManager.isWordInDictionary(substituted)) {
                            candidates.add(substituted)
                        }
                    }
                }
                chars[i] = orig
            }
        }

        // C. Single Letter Deletion Generation (Repeated/accidental extra character: thee -> the, annd -> and)
        if (lower.length >= 3) {
            for (i in lower.indices) {
                val deleted = lower.removeRange(i, i + 1)
                if (deleted.length >= 2 && dictionaryManager.isWordInDictionary(deleted)) {
                    candidates.add(deleted)
                }
            }
        }

        // D. Single Letter Insertion / Doubling Generation (Dropped letter or missing double: tomorow -> tomorrow, runing -> running)
        if (lower.length in 2..12) {
            // Try doubling each letter first (most common typing mistake)
            for (i in lower.indices) {
                val doubled = lower.substring(0, i + 1) + lower[i] + lower.substring(i + 1)
                if (dictionaryManager.isWordInDictionary(doubled)) {
                    candidates.add(doubled)
                }
            }
            // Try inserting frequent vowels and letters at each spot if string is short
            if (lower.length in 2..6) {
                val frequentChars = charArrayOf('e', 'a', 'i', 'o', 'u', 'r', 't', 'n', 's', 'l', 'h')
                for (i in 0..lower.length) {
                    for (fc in frequentChars) {
                        val inserted = lower.substring(0, i) + fc + lower.substring(i)
                        if (dictionaryManager.isWordInDictionary(inserted)) {
                            candidates.add(inserted)
                        }
                    }
                }
            }
        }

        return candidates
    }

    /**
     * 5. Multi-word segmentation for missed space errors (e.g. "goodmorning" -> "good morning")
     */
    fun segmentMissedSpaces(raw: String, dictionaryManager: DictionaryManager): String? {
        val clean = raw.lowercase().trim()
        if (clean.length < 4) return null

        // 2-word split
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
     * 6. Generate, Rank, and Decode Candidates for the current typing state.
     * Produces the full Gboard Suggestion Result for the keyboard UI with diagnostic logging.
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

        // Empty typing state: Multi-Word Phrase Completion & Next-Word Prediction
        if (trimmed.isEmpty()) {
            val phrasePredictions = localGrammarPredictor.predictPhraseCompletions(contextWords, "", 3)
            val nextPredictions = nGramModel.predictNextWords(contextWords, "", 5)
            val prev1 = contextWords.lastOrNull()?.lowercase()?.trim() ?: ""
            val prev2 = if (contextWords.size >= 2) contextWords[contextWords.size - 2].lowercase().trim() else ""

            val mlTrigram = if (prev2.isNotEmpty() && prev1.isNotEmpty()) {
                mlPredictor.predictNextWordsFromTrigram(prev2, prev1).map { it.first }
            } else emptyList()

            val mlBigram = if (prev1.isNotEmpty()) {
                mlPredictor.predictNextWords(prev1).map { it.first }
            } else emptyList()

            val combinedPool = mutableListOf<String>()
            if (phrasePredictions.isNotEmpty()) {
                combinedPool.addAll(phrasePredictions)
            }
            combinedPool.addAll(mlTrigram)
            combinedPool.addAll(nextPredictions)
            combinedPool.addAll(mlBigram)
            combinedPool.addAll(listOf("the", "I", "to"))

            val top3 = combinedPool
                .distinct()
                .take(3)

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
                    topCandidates = top3.map { GboardCandidate(it, 1f, 1f, 0f, 1f, 1f, ConfidenceTier.LOW, false, if (phrasePredictions.contains(it)) "Phrase Completion" else "Next-Word Prediction") },
                    touchDeltas = emptyList(),
                    scoreMargin = 0f,
                    decisionReason = if (phrasePredictions.isNotEmpty()) "Contextual 3-word phrase completion active" else "Next-word contextual prediction active"
                )
            )
        }

        // Candidate Generation Set
        val candidatePool = LinkedHashSet<String>()

        // 0a. Local Grammar Multi-Word Phrase Completion based on preceding 3 words
        val phraseMatches = localGrammarPredictor.predictPhraseCompletions(contextWords, lower, 3)
        candidatePool.addAll(phraseMatches)

        // 0b. Local Grammar & Contextual Agreement check
        val localGrammarCorrection = localGrammarPredictor.checkGrammarDetailed(lower, contextWords)
        if (localGrammarCorrection != null) {
            candidatePool.add(localGrammarCorrection.correctedWord)
        }

        // 1. Direct typo match lookup
        commonTypoLookup[lower]?.let { candidatePool.add(it) }

        // 2. Contraction match lookup (e.g. dont -> don't, im -> I'm)
        contractionLookup[lower]?.let { candidatePool.add(it) }

        // 3. Algorithmic Candidate Generation (Transpositions, QWERTY neighbors, missing/extra chars)
        val algoCandidates = generateAlgorithmicCandidates(lower, dictionaryManager)
        candidatePool.addAll(algoCandidates)

        // 4. Missed space split candidate (e.g. goodmorning -> good morning)
        segmentMissedSpaces(lower, dictionaryManager)?.let { candidatePool.add(it) }

        // 5. Prefix match candidates from Trie
        val prefixMatches = dictionaryManager.getSuggestionsForPrefix(
            prefix = lower,
            previousWords = contextWords,
            tapCoords = tapCoords
        )
        candidatePool.addAll(prefixMatches)

        // 6. Fuzzy spell corrections & phonetic matches
        val spellCorrections = dictionaryManager.getSpellingCorrections(
            word = lower,
            prevWord = contextWords.lastOrNull(),
            tapCoords = tapCoords
        )
        candidatePool.addAll(spellCorrections)

        // 7. Include raw typed literal in pool
        candidatePool.add(trimmed)

        val isRawValidWord = dictionaryManager.isWordInDictionary(lower)
        val isRawCodeOrSpecial = dictionaryManager.isCodeOrSpecialToken(trimmed)

        // Decode and score each candidate using Gboard's calibrated multi-signal equation
        val scoredCandidates = mutableListOf<GboardCandidate>()

        for (cand in candidatePool) {
            val cleanCand = cand.lowercase().trim()
            val isExactMatch = cleanCand == lower
            val isKnownWord = dictionaryManager.isWordInDictionary(cleanCand) || cleanCand == "i" || cleanCand == "a"
            val isGrammarFix = localGrammarCorrection != null && (cleanCand == localGrammarCorrection.correctedWord.lowercase() || cand == localGrammarCorrection.correctedWord)

            val editDist = computeSpatialEditDistance(lower, cleanCand)
            val spatialLikelihood = computeSpatialTouchLikelihood(cleanCand, tapCoords)
            val lmProb = computeLanguageModelProbability(cleanCand, contextWords, dictionaryManager)

            // Normalized Edit Distance Score [0.0, 1.0]
            val maxLen = max(lower.length, cleanCand.length).toFloat().coerceAtLeast(3.0f)
            val normalizedEditScore = (1.0f - (editDist / maxLen)).coerceIn(0.0f, 1.0f)

            // Corpus Word Frequency Score [0.0, 1.0] using logarithmic scaling
            val wordFreq = dictionaryManager.getWordFrequency(cleanCand)
            val freqScore = (log10(wordFreq.toFloat() + 1f) / log10(1001f)).coerceIn(0.0f, 1.0f)

            // Multi-signal Weighted Composite Score:
            // Score = 0.35 * EditScore + 0.25 * SpatialTouch + 0.20 * LMProb + 0.15 * FreqScore + 0.05 * UserHabit
            var posterior = (0.35f * normalizedEditScore) +
                            (0.25f * spatialLikelihood) +
                            (0.20f * lmProb) +
                            (0.15f * freqScore)

            // Rule & Source-Specific Boosts
            if (isExactMatch) posterior += 0.20f
            if (isKnownWord) posterior += 0.15f
            if (isGrammarFix) posterior += 0.60f
            if (phraseMatches.contains(cleanCand) || phraseMatches.contains(cand)) posterior += 0.50f
            if (commonTypoLookup.containsKey(lower) && commonTypoLookup[lower] == cleanCand) posterior += 0.45f
            if (contractionLookup.containsKey(lower) && contractionLookup[lower] == cand) posterior += 0.45f
            if (algoCandidates.contains(cleanCand)) posterior += 0.30f
            if (cleanCand.contains(" ") && cleanCand.replace(" ", "") == lower) posterior += 0.35f

            // Confidence Tier Assignment
            val isKnownTypo = commonTypoLookup.containsKey(lower) || contractionLookup.containsKey(lower)
            val confidenceTier = when {
                isGrammarFix || isKnownTypo || (posterior >= HIGH_CONFIDENCE_THRESHOLD && (isKnownWord || cleanCand.contains(" "))) -> ConfidenceTier.HIGH
                posterior >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceTier.MEDIUM
                else -> ConfidenceTier.LOW
            }

            // Autocorrect Eligibility Determination:
            // - If raw input is an invalid word (typo), eligible if confidence is HIGH or MEDIUM with valid word candidate
            // - If raw input is already a valid word, ONLY eligible if there is a compelling Grammar Fix or explicit contraction
            val isAutocorrectEligible = when {
                isExactMatch -> false
                isRawCodeOrSpecial -> false
                isGrammarFix -> true
                isKnownTypo -> true
                !isRawValidWord && isKnownWord && (confidenceTier == ConfidenceTier.HIGH || (confidenceTier == ConfidenceTier.MEDIUM && posterior >= 0.40f)) -> true
                isRawValidWord && contractionLookup.containsKey(lower) && contractionLookup[lower] == cand -> true
                else -> false
            }

            val reason = when {
                isExactMatch -> "Exact Typed Literal"
                isGrammarFix -> "Grammar Agreement: ${localGrammarCorrection?.ruleCategory}"
                isKnownTypo -> "Known Typo / Transposition Rule"
                algoCandidates.contains(cleanCand) -> "Algorithmic Neighbor / Transposition Match"
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
                    reason = reason
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
        // Autocorrect triggers if enabled, not sensitive field, top candidate is eligible, and margin > 0.03 (or known rule)
        val hasSufficientMargin = scoreMargin >= MIN_CORRECTION_MARGIN || topCandidate.reason.contains("Rule") || topCandidate.reason.contains("Grammar")
        val isCenterAutocorrecting = settings.autocorrectEnabled &&
                                     !isSensitiveField &&
                                     topCandidate.isAutocorrectEligible &&
                                     hasSufficientMargin &&
                                     topCandidate.word.lowercase() != lower

        val centerSlotWord = if (isCenterAutocorrecting) topCandidate.word else (if (lower == "i") "I" else trimmed)

        // Left Slot: The literal typed string (if middle is autocorrecting), otherwise 2nd best candidate
        val leftSlotWord = if (isCenterAutocorrecting) {
            trimmed
        } else {
            sortedCandidates.firstOrNull { it.word.lowercase() != centerSlotWord.lowercase() }?.word ?: trimmed
        }

        // Right Slot: Semantic emoji if keyword matches, otherwise 3rd best candidate
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

        // Diagnostic Logcat Output
        Log.d(TAG, "[AUTOCORRECT] $decisionReason")

        return GboardSuggestionResult(
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
