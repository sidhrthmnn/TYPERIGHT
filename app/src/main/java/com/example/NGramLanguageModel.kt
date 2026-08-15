package com.example

import java.util.concurrent.ConcurrentHashMap

/**
 * N-gram Language Model (supporting Bigrams & Trigrams) for fast, context-aware
 * next-word prediction and sentence completion alongside WordTrie.
 */
class NGramLanguageModel {

    // Trigram map: "word1 word2" -> Map(word3 -> frequency)
    private val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Bigram map: "word1" -> Map(word2 -> frequency)
    private val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Unigram map: "word" -> frequency
    private val unigrams = ConcurrentHashMap<String, Int>()

    init {
        seedCommonNGrams()
    }

    /**
     * Seeds popular English conversational bigrams and trigrams for high accuracy out of the box.
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
        unigrams[target] = (unigrams[target] ?: 0) + freq
    }

    /**
     * Learns N-grams dynamically from typed sentences.
     */
    fun observeSentence(sentence: String) {
        val tokens = sentence.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        for (i in tokens.indices) {
            val w1 = tokens[i]
            unigrams[w1] = (unigrams[w1] ?: 0) + 1

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
     * Predicts the next word candidates based on 1 or 2 context words and an optional prefix.
     */
    fun predictNextWords(
        contextWords: List<String>,
        prefix: String = "",
        maxResults: Int = 5
    ): List<String> {
        val cleanContext = contextWords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val cleanPrefix = prefix.lowercase().trim()
        val candidatesWithScores = HashMap<String, Int>()

        // 1. Try Trigram prediction if we have at least 2 context words
        if (cleanContext.size >= 2) {
            val w1 = cleanContext[cleanContext.size - 2]
            val w2 = cleanContext.last()
            val triKey = "$w1 $w2"
            trigrams[triKey]?.let { map ->
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0) + (freq * 10)
                    }
                }
            }
        }

        // 2. Try Bigram prediction if we have at least 1 context word
        if (cleanContext.isNotEmpty()) {
            val w = cleanContext.last()
            bigrams[w]?.let { map ->
                for ((nextWord, freq) in map) {
                    if (cleanPrefix.isEmpty() || nextWord.startsWith(cleanPrefix)) {
                        candidatesWithScores[nextWord] = (candidatesWithScores[nextWord] ?: 0) + (freq * 3)
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
