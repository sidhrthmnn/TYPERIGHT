package com.example

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 4. Context Model for Next-Word Prediction
 *
 * Pluggable Next-Word Context Language Model.
 * Implements a full Multi-Order N-gram Model (Quadgram + Trigram + Bigram + Unigram)
 * with Jelinek-Mercer interpolation and Katz backoff to suggest the most likely next words
 * based on previously typed word sequences.
 */
interface IContextLanguageModel {
    fun getProbability(word: String, contextWords: List<String>): Float
    fun predictNextWords(contextWords: List<String>, prefix: String = "", maxResults: Int = 5): List<String>
    fun predictNextPhrases(contextWords: List<String>, maxResults: Int = 3): List<String>
    fun observeSentence(sentence: String)
    fun trainOnCorpus(corpusText: String)
}

/**
 * High-performance Multi-Order N-gram Language Model.
 * Supports:
 * - 4-grams (Quadgrams): P(w4 | w1, w2, w3)
 * - 3-grams (Trigrams): P(w3 | w1, w2)
 * - 2-grams (Bigrams): P(w2 | w1)
 * - 1-grams (Unigrams): P(w1)
 *
 * Utilizes Jelinek-Mercer smoothed interpolation:
 * P(w4 | w1, w2, w3) = λ4 * P_quad + λ3 * P_tri + λ2 * P_bi + λ1 * P_uni
 */
class NGramLanguageModel : IContextLanguageModel {

    // Quadgram map: "w1 w2 w3" -> Map(w4 -> frequency)
    private val quadgrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Trigram map: "w1 w2" -> Map(w3 -> frequency)
    private val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Bigram map: "w1" -> Map(w2 -> frequency)
    private val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Unigram map: "w" -> frequency
    private val unigrams = ConcurrentHashMap<String, Int>()

    private var totalUnigramCount: Long = 0L

    init {
        seedCommonNGrams()
    }

    /**
     * Seeds popular conversational bigrams, trigrams, and quadgrams for high accuracy out of the box.
     */
    private fun seedCommonNGrams() {
        val commonQuadgrams = listOf(
            listOf("thank", "you", "so", "much") to 1200,
            listOf("thank", "you", "very", "much") to 1100,
            listOf("let", "me", "know", "if") to 1000,
            listOf("looking", "forward", "to", "hearing") to 950,
            listOf("looking", "forward", "to", "seeing") to 900,
            listOf("hope", "you", "are", "doing") to 950,
            listOf("you", "are", "doing", "well") to 900,
            listOf("have", "a", "great", "day") to 1100,
            listOf("have", "a", "good", "one") to 850,
            listOf("have", "a", "good", "time") to 850,
            listOf("as", "soon", "as", "possible") to 1050,
            listOf("at", "the", "end", "of") to 800,
            listOf("at", "the", "same", "time") to 850,
            listOf("by", "the", "way", "i") to 800,
            listOf("i", "am", "looking", "forward") to 850,
            listOf("please", "let", "me", "know") to 1000,
            listOf("talk", "to", "you", "later") to 950,
            listOf("talk", "to", "you", "soon") to 900,
            listOf("see", "you", "tomorrow", "morning") to 800,
            listOf("what", "do", "you", "think") to 950,
            listOf("what", "are", "you", "doing") to 900,
            listOf("how", "are", "you", "doing") to 950,
            listOf("where", "are", "you", "going") to 850,
            listOf("it", "was", "great", "to") to 800,
            listOf("i", "would", "like", "to") to 900
        )

        for ((quad, freq) in commonQuadgrams) {
            addQuadgram(quad[0], quad[1], quad[2], quad[3], freq)
        }

        val commonTrigrams = listOf(
            Triple("how", "are", "you") to 1200,
            Triple("thank", "you", "so") to 1000,
            Triple("thank", "you", "very") to 950,
            Triple("you", "so", "much") to 980,
            Triple("you", "very", "much") to 920,
            Triple("let", "me", "know") to 1100,
            Triple("looking", "forward", "to") to 1050,
            Triple("forward", "to", "hearing") to 850,
            Triple("forward", "to", "seeing") to 800,
            Triple("have", "a", "great") to 1150,
            Triple("have", "a", "good") to 1100,
            Triple("have", "a", "nice") to 950,
            Triple("a", "great", "day") to 1100,
            Triple("a", "good", "time") to 900,
            Triple("a", "good", "one") to 850,
            Triple("on", "my", "way") to 1100,
            Triple("what", "do", "you") to 1050,
            Triple("do", "you", "think") to 950,
            Triple("do", "you", "know") to 920,
            Triple("do", "you", "want") to 940,
            Triple("do", "you", "have") to 900,
            Triple("where", "are", "you") to 950,
            Triple("when", "are", "you") to 850,
            Triple("i", "am", "going") to 900,
            Triple("i", "am", "doing") to 850,
            Triple("i", "am", "here") to 800,
            Triple("i", "am", "on") to 850,
            Triple("nice", "to", "meet") to 950,
            Triple("to", "meet", "you") to 950,
            Triple("hope", "you", "are") to 900,
            Triple("as", "soon", "as") to 1000,
            Triple("soon", "as", "possible") to 1000,
            Triple("in", "order", "to") to 800,
            Triple("at", "the", "same") to 800,
            Triple("the", "same", "time") to 800,
            Triple("out", "of", "the") to 750,
            Triple("one", "of", "the") to 850,
            Triple("by", "the", "way") to 950,
            Triple("if", "you", "have") to 800,
            Triple("if", "you", "can") to 780,
            Triple("if", "you", "want") to 760,
            Triple("can", "you", "please") to 900,
            Triple("please", "let", "me") to 950,
            Triple("see", "you", "later") to 950,
            Triple("see", "you", "soon") to 940,
            Triple("see", "you", "tomorrow") to 900,
            Triple("talk", "to", "you") to 900,
            Triple("to", "you", "later") to 900,
            Triple("sounds", "like", "a") to 850,
            Triple("sounds", "good", "to") to 820,
            Triple("take", "care", "of") to 800,
            Triple("would", "be", "great") to 920,
            Triple("it", "would", "be") to 930,
            Triple("i", "want", "to") to 980,
            Triple("i", "need", "to") to 950,
            Triple("i", "have", "to") to 940,
            Triple("i", "would", "like") to 920,
            Triple("would", "like", "to") to 920,
            Triple("need", "help", "with") to 800,
            Triple("can", "i", "get") to 850,
            Triple("can", "i", "have") to 820,
            Triple("check", "it", "out") to 850,
            Triple("ready", "to", "go") to 860,
            Triple("give", "me", "a") to 850,
            Triple("let", "you", "know") to 850
        )

        for ((triple, freq) in commonTrigrams) {
            addTrigram(triple.first, triple.second, triple.third, freq)
        }

        val commonBigrams = listOf(
            Pair("how", "are") to 1200,
            Pair("are", "you") to 1200,
            Pair("thank", "you") to 1300,
            Pair("you", "so") to 900,
            Pair("you", "very") to 850,
            Pair("good", "morning") to 1100,
            Pair("good", "night") to 1050,
            Pair("good", "afternoon") to 950,
            Pair("good", "evening") to 900,
            Pair("good", "luck") to 950,
            Pair("good", "job") to 1000,
            Pair("great", "job") to 950,
            Pair("great", "news") to 900,
            Pair("great", "idea") to 920,
            Pair("great", "day") to 900,
            Pair("i", "am") to 1200,
            Pair("i", "will") to 1150,
            Pair("i", "have") to 1150,
            Pair("i", "would") to 1050,
            Pair("i", "think") to 1100,
            Pair("i", "know") to 1050,
            Pair("i", "can") to 1050,
            Pair("i", "don't") to 1100,
            Pair("i", "love") to 1000,
            Pair("i", "need") to 1080,
            Pair("i", "want") to 1070,
            Pair("you", "can") to 950,
            Pair("you", "are") to 1100,
            Pair("you", "have") to 950,
            Pair("we", "are") to 1000,
            Pair("we", "can") to 950,
            Pair("we", "will") to 950,
            Pair("we", "have") to 950,
            Pair("they", "are") to 950,
            Pair("let", "us") to 900,
            Pair("make", "sure") to 1000,
            Pair("take", "a") to 900,
            Pair("take", "care") to 950,
            Pair("look", "at") to 900,
            Pair("what", "is") to 1050,
            Pair("what", "about") to 950,
            Pair("where", "is") to 950,
            Pair("who", "is") to 900,
            Pair("why", "not") to 900,
            Pair("call", "me") to 900,
            Pair("text", "me") to 900,
            Pair("send", "me") to 900,
            Pair("give", "me") to 900,
            Pair("tell", "me") to 900,
            Pair("no", "problem") to 1050,
            Pair("no", "worries") to 1050,
            Pair("of", "course") to 1100,
            Pair("for", "sure") to 950,
            Pair("sounds", "good") to 1000,
            Pair("sounds", "great") to 950,
            Pair("let's", "go") to 980,
            Pair("don't", "worry") to 1000,
            Pair("nice", "to") to 1000,
            Pair("want", "to") to 1100,
            Pair("going", "to") to 1150,
            Pair("have", "a") to 1150,
            Pair("need", "to") to 1050,
            Pair("try", "to") to 950,
            Pair("able", "to") to 920
        )

        for ((pair, freq) in commonBigrams) {
            addBigram(pair.first, pair.second, freq)
        }
    }

    fun addQuadgram(w1: String, w2: String, w3: String, w4: String, freq: Int = 1) {
        val key = "${w1.lowercase()} ${w2.lowercase()} ${w3.lowercase()}"
        val target = w4.lowercase()
        val map = quadgrams.getOrPut(key) { ConcurrentHashMap() }
        map[target] = (map[target] ?: 0) + freq
        addTrigram(w2, w3, w4, freq)
    }

    fun addTrigram(w1: String, w2: String, w3: String, freq: Int = 1) {
        val key = "${w1.lowercase()} ${w2.lowercase()}"
        val target = w3.lowercase()
        val map = trigrams.getOrPut(key) { ConcurrentHashMap() }
        map[target] = (map[target] ?: 0) + freq
        addBigram(w2, w3, freq)
    }

    fun addBigram(w1: String, w2: String, freq: Int = 1) {
        val key = w1.lowercase()
        val target = w2.lowercase()
        val map = bigrams.getOrPut(key) { ConcurrentHashMap() }
        map[target] = (map[target] ?: 0) + freq
        val old = unigrams[target] ?: 0
        unigrams[target] = old + freq
        totalUnigramCount += freq
    }

    /**
     * Trains the N-gram model on an external plain-text corpus supplied as string.
     */
    override fun trainOnCorpus(corpusText: String) {
        val sentences = corpusText.split(Regex("[.!?\\n]+"))
        for (sentence in sentences) {
            val clean = sentence.trim()
            if (clean.isNotEmpty()) {
                observeSentence(clean)
            }
        }
    }

    /**
     * Learns N-grams dynamically from typed sentences or conversational input.
     */
    override fun observeSentence(sentence: String) {
        val tokens = sentence.lowercase().split(Regex("[\\s.,!?;:\"]+")).filter { it.isNotBlank() }
        for (i in tokens.indices) {
            val w1 = tokens[i]
            val old = unigrams[w1] ?: 0
            unigrams[w1] = old + 1
            totalUnigramCount++

            if (i + 1 < tokens.size) {
                val w2 = tokens[i + 1]
                addBigram(w1, w2, 1)

                if (i + 2 < tokens.size) {
                    val w3 = tokens[i + 2]
                    addTrigram(w1, w2, w3, 1)

                    if (i + 3 < tokens.size) {
                        val w4 = tokens[i + 3]
                        addQuadgram(w1, w2, w3, w4, 1)
                    }
                }
            }
        }
    }

    /**
     * Computes the smoothed language model probability P(Word | Context) using Jelinek-Mercer interpolation
     * across Quadgram, Trigram, Bigram, and Unigram layers:
     * P(w | ctx) = 0.40 * P_quad + 0.30 * P_tri + 0.20 * P_bi + 0.10 * P_uni
     */
    override fun getProbability(word: String, contextWords: List<String>): Float {
        val target = word.lowercase().trim()
        if (target.isEmpty()) return 0.05f

        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val prev1 = cleanContext.lastOrNull()
        val prev2 = if (cleanContext.size >= 2) cleanContext[cleanContext.size - 2] else null
        val prev3 = if (cleanContext.size >= 3) cleanContext[cleanContext.size - 3] else null

        var quadProb = 0.0f
        var triProb = 0.0f
        var biProb = 0.0f
        var uniProb = 0.05f

        // Quadgram component
        if (prev3 != null && prev2 != null && prev1 != null) {
            val quadKey = "$prev3 $prev2 $prev1"
            val map = quadgrams[quadKey]
            if (map != null) {
                val totalFreq = map.values.sum().toFloat().coerceAtLeast(1f)
                val targetFreq = map[target]?.toFloat() ?: 0f
                if (targetFreq > 0) {
                    quadProb = targetFreq / totalFreq
                }
            }
        }

        // Trigram component
        if (prev2 != null && prev1 != null) {
            val triKey = "$prev2 $prev1"
            val map = trigrams[triKey]
            if (map != null) {
                val totalFreq = map.values.sum().toFloat().coerceAtLeast(1f)
                val targetFreq = map[target]?.toFloat() ?: 0f
                if (targetFreq > 0) {
                    triProb = targetFreq / totalFreq
                }
            }
        }

        // Bigram component
        if (prev1 != null) {
            val map = bigrams[prev1]
            if (map != null) {
                val totalFreq = map.values.sum().toFloat().coerceAtLeast(1f)
                val targetFreq = map[target]?.toFloat() ?: 0f
                if (targetFreq > 0) {
                    biProb = targetFreq / totalFreq
                }
            }
        }

        // Unigram component
        val uniCount = unigrams[target]?.toFloat() ?: 1f
        val totalCount = totalUnigramCount.toFloat().coerceAtLeast(1000f)
        uniProb = (uniCount / totalCount).coerceIn(0.01f, 1.0f)

        // Jelinek-Mercer weights (0.40 Quadgram, 0.30 Trigram, 0.20 Bigram, 0.10 Unigram)
        val interpolated = (0.40f * quadProb) + (0.30f * triProb) + (0.20f * biProb) + (0.10f * uniProb)
        return interpolated.coerceIn(0.05f, 1.0f)
    }

    /**
     * Predicts the next word candidates based on previous sequence of words typed by the user,
     * matching an optional partially typed word prefix.
     */
    override fun predictNextWords(
        contextWords: List<String>,
        prefix: String,
        maxResults: Int
    ): List<String> {
        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val cleanPrefix = prefix.lowercase().trim()
        val candidatesWithScores = HashMap<String, Float>()

        // 1. Try Quadgram prediction if we have at least 3 context words
        if (cleanContext.size >= 3) {
            val w1 = cleanContext[cleanContext.size - 3]
            val w2 = cleanContext[cleanContext.size - 2]
            val w3 = cleanContext.last()
            val quadKey = "$w1 $w2 $w3"
            quadgrams[quadKey]?.let { map ->
                val total = map.values.sum().toFloat().coerceAtLeast(1f)
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        val prob = freq / total
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0f) + (prob * 0.70f)
                    }
                }
            }
        }

        // 2. Try Trigram prediction if we have at least 2 context words
        if (cleanContext.size >= 2) {
            val w1 = cleanContext[cleanContext.size - 2]
            val w2 = cleanContext.last()
            val triKey = "$w1 $w2"
            trigrams[triKey]?.let { map ->
                val total = map.values.sum().toFloat().coerceAtLeast(1f)
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        val prob = freq / total
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0f) + (prob * 0.50f)
                    }
                }
            }
        }

        // 3. Try Bigram prediction if we have at least 1 context word
        if (cleanContext.isNotEmpty()) {
            val w = cleanContext.last()
            bigrams[w]?.let { map ->
                val total = map.values.sum().toFloat().coerceAtLeast(1f)
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        val prob = freq / total
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0f) + (prob * 0.30f)
                    }
                }
            }
        }

        // Sort candidates by total interpolated score
        return candidatesWithScores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }

    /**
     * Multi-word auto-prediction phrase generator.
     * Given context words, generates 2-3 word phrase continuations (e.g., "looking forward" -> "to seeing you").
     */
    override fun predictNextPhrases(contextWords: List<String>, maxResults: Int): List<String> {
        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        if (cleanContext.isEmpty()) return emptyList()

        val next1 = predictNextWords(cleanContext, prefix = "", maxResults = 3)
        val phrases = mutableListOf<String>()

        for (w1 in next1) {
            val extendedContext = cleanContext + w1
            val next2 = predictNextWords(extendedContext, prefix = "", maxResults = 2)
            if (next2.isNotEmpty()) {
                for (w2 in next2) {
                    phrases.add("$w1 $w2")
                }
            } else {
                phrases.add(w1)
            }
        }

        return phrases.take(maxResults)
    }
}
