package com.example

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * 4. Context Model for Next-Word Prediction
 *
 * Interface for pluggable next-word context models.
 * Currently backed by an interpolated Trigram + Bigram + Unigram model with Katz backoff.
 *
 * --- ON-DEVICE LSTM UPGRADE ROADMAP ---
 * To upgrade to an on-device LSTM model (comparable to Gboard's production model):
 * - Architecture: Single-layer LSTM with projection (Vocabulary: ~10k tokens, Embedding dim: 96, LSTM units: 670, Projection dim: 96)
 * - Parameters: ~1.4M parameters
 * - Quantization: Post-training 8-bit dynamic range or INT8 full integer quantization via TFLite Converter
 * - Size: ~1.4MB .tflite flatbuffer file in app/src/main/assets/models/next_word_lstm.tflite
 * - Runtime: TensorFlow Lite Task Library (`org.tensorflow:tensorflow-lite-task-text`) running on CPU/NNAPI with <10ms inference latency.
 */
interface IContextLanguageModel {
    fun getProbability(word: String, contextWords: List<String>): Float
    fun predictNextWords(contextWords: List<String>, prefix: String = "", maxResults: Int = 5): List<String>
    fun observeSentence(sentence: String)
    fun trainOnCorpus(corpusText: String)
}

/**
 * High-performance Multi-Order N-gram Language Model with Jelinek-Mercer interpolation and Katz backoff.
 */
class NGramLanguageModel : IContextLanguageModel {

    // Trigram map: "word1 word2" -> Map(word3 -> frequency)
    private val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Bigram map: "word1" -> Map(word2 -> frequency)
    private val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Unigram map: "word" -> frequency
    private val unigrams = ConcurrentHashMap<String, Int>()

    private var totalUnigramCount: Long = 0L

    init {
        seedCommonNGrams()
    }

    /**
     * Seeds popular conversational bigrams and trigrams for high accuracy out of the box.
     */
    private fun seedCommonNGrams() {
        val commonTrigrams = listOf(
            Triple("how", "are", "you") to 1000,
            Triple("thank", "you", "so") to 900,
            Triple("you", "so", "much") to 880,
            Triple("let", "me", "know") to 850,
            Triple("looking", "forward", "to") to 800,
            Triple("forward", "to", "hearing") to 750,
            Triple("have", "a", "great") to 900,
            Triple("have", "a", "good") to 890,
            Triple("have", "a", "nice") to 850,
            Triple("a", "great", "day") to 850,
            Triple("a", "good", "time") to 800,
            Triple("on", "my", "way") to 900,
            Triple("what", "do", "you") to 850,
            Triple("do", "you", "think") to 800,
            Triple("do", "you", "know") to 780,
            Triple("do", "you", "want") to 790,
            Triple("where", "are", "you") to 800,
            Triple("when", "are", "you") to 750,
            Triple("i", "am", "going") to 800,
            Triple("i", "am", "doing") to 750,
            Triple("i", "am", "here") to 700,
            Triple("nice", "to", "meet") to 850,
            Triple("to", "meet", "you") to 850,
            Triple("hope", "you", "are") to 800,
            Triple("as", "soon", "as") to 850,
            Triple("soon", "as", "possible") to 850,
            Triple("in", "order", "to") to 750,
            Triple("at", "the", "same") to 700,
            Triple("the", "same", "time") to 700,
            Triple("out", "of", "the") to 650,
            Triple("one", "of", "the") to 700,
            Triple("by", "the", "way") to 800,
            Triple("if", "you", "have") to 700,
            Triple("if", "you", "can") to 680,
            Triple("can", "you", "please") to 800,
            Triple("please", "let", "me") to 780,
            Triple("see", "you", "later") to 850,
            Triple("see", "you", "soon") to 840,
            Triple("talk", "to", "you") to 800,
            Triple("to", "you", "later") to 800,
            Triple("sounds", "like", "a") to 750,
            Triple("sounds", "good", "to") to 720,
            Triple("take", "care", "of") to 700,
            Triple("would", "be", "great") to 820,
            Triple("it", "would", "be") to 830,
            Triple("i", "want", "to") to 880,
            Triple("need", "help", "with") to 750,
            Triple("can", "i", "get") to 790,
            Triple("check", "it", "out") to 780,
            Triple("ready", "to", "go") to 760
        )

        for ((triple, freq) in commonTrigrams) {
            addTrigram(triple.first, triple.second, triple.third, freq)
        }

        val commonBigrams = listOf(
            Pair("how", "are") to 1000,
            Pair("are", "you") to 1000,
            Pair("thank", "you") to 1000,
            Pair("you", "very") to 600,
            Pair("good", "morning") to 950,
            Pair("good", "night") to 900,
            Pair("good", "afternoon") to 800,
            Pair("good", "evening") to 750,
            Pair("good", "luck") to 800,
            Pair("good", "job") to 850,
            Pair("great", "job") to 800,
            Pair("great", "news") to 750,
            Pair("great", "idea") to 780,
            Pair("i", "am") to 1000,
            Pair("i", "will") to 950,
            Pair("i", "have") to 950,
            Pair("i", "would") to 850,
            Pair("i", "think") to 900,
            Pair("i", "know") to 850,
            Pair("i", "can") to 850,
            Pair("i", "don't") to 900,
            Pair("i", "love") to 800,
            Pair("i", "need") to 880,
            Pair("i", "want") to 870,
            Pair("you", "can") to 800,
            Pair("you", "are") to 900,
            Pair("you", "have") to 800,
            Pair("we", "are") to 850,
            Pair("we", "can") to 800,
            Pair("we", "will") to 800,
            Pair("we", "have") to 800,
            Pair("they", "are") to 800,
            Pair("let", "us") to 750,
            Pair("make", "sure") to 800,
            Pair("take", "a") to 750,
            Pair("look", "at") to 750,
            Pair("what", "is") to 850,
            Pair("what", "about") to 800,
            Pair("where", "is") to 800,
            Pair("who", "is") to 750,
            Pair("why", "not") to 750,
            Pair("call", "me") to 750,
            Pair("text", "me") to 750,
            Pair("send", "me") to 750,
            Pair("give", "me") to 750,
            Pair("tell", "me") to 750,
            Pair("no", "problem") to 850,
            Pair("no", "worries") to 850,
            Pair("of", "course") to 900,
            Pair("for", "sure") to 800,
            Pair("sounds", "good") to 820,
            Pair("let's", "go") to 810,
            Pair("don't", "worry") to 830,
            Pair("nice", "to") to 850,
            Pair("want", "to") to 880,
            Pair("going", "to") to 900,
            Pair("have", "a") to 890,
            Pair("need", "to") to 860,
            Pair("try", "to") to 800,
            Pair("able", "to") to 790
        )

        for ((pair, freq) in commonBigrams) {
            addBigram(pair.first, pair.second, freq)
        }
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
     * Learns N-grams dynamically from typed sentences.
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
                }
            }
        }
    }

    /**
     * Computes the language model probability P(Word | Context) using Katz backoff / Jelinek-Mercer interpolation:
     * P(w | w-2, w-1) = lambda3 * P_tri + lambda2 * P_bi + lambda1 * P_uni
     */
    override fun getProbability(word: String, contextWords: List<String>): Float {
        val target = word.lowercase().trim()
        if (target.isEmpty()) return 0.05f

        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val prev1 = cleanContext.lastOrNull()
        val prev2 = if (cleanContext.size >= 2) cleanContext[cleanContext.size - 2] else null

        var triProb = 0.0f
        var biProb = 0.0f
        var uniProb = 0.05f

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

        // Interpolation weights (0.50 Trigram, 0.35 Bigram, 0.15 Unigram)
        val interpolated = (0.50f * triProb) + (0.35f * biProb) + (0.15f * uniProb)
        return interpolated.coerceIn(0.05f, 1.0f)
    }

    /**
     * Predicts the next word candidates based on 1 or 2 context words and an optional prefix.
     */
    override fun predictNextWords(
        contextWords: List<String>,
        prefix: String,
        maxResults: Int
    ): List<String> {
        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val cleanPrefix = prefix.lowercase().trim()
        val candidatesWithScores = HashMap<String, Float>()

        // 1. Try Trigram prediction if we have at least 2 context words
        if (cleanContext.size >= 2) {
            val w1 = cleanContext[cleanContext.size - 2]
            val w2 = cleanContext.last()
            val triKey = "$w1 $w2"
            trigrams[triKey]?.let { map ->
                val total = map.values.sum().toFloat().coerceAtLeast(1f)
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        val prob = freq / total
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0f) + (prob * 0.60f)
                    }
                }
            }
        }

        // 2. Try Bigram prediction if we have at least 1 context word
        if (cleanContext.isNotEmpty()) {
            val w = cleanContext.last()
            bigrams[w]?.let { map ->
                val total = map.values.sum().toFloat().coerceAtLeast(1f)
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        val prob = freq / total
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0f) + (prob * 0.35f)
                    }
                }
            }
        }

        // Sort candidates by total score
        return candidatesWithScores.entries
            .sortedByDescending { it.value }
            .take(maxResults)
            .map { it.key }
    }
}
