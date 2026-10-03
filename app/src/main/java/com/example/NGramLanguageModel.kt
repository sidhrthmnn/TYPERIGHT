package com.example

import java.util.concurrent.ConcurrentHashMap

/**
 * 4. Context Model for Next-Word Prediction
 *
 * Pluggable Next-Word Context Language Model.
 * Implements a full Multi-Order N-gram Model (Quadgram + Trigram + Bigram + Unigram)
 * with count-sensitive interpolation and lower-order backoff to suggest the most likely next words
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

    private val higherOrders = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()
    @Synchronized fun observeHigherOrder(word: String, context: List<String>) {
        for (size in 4..minOf(5, context.size)) {
            val key = context.takeLast(size).joinToString(" ") { it.lowercase(java.util.Locale.ROOT) }
            if (!higherOrders.containsKey(key) && higherOrders.size >= 4000) higherOrders.remove(higherOrders.keys.first())
            val following = higherOrders.getOrPut(key) { ConcurrentHashMap() }
            if (!following.containsKey(word) && following.size >= 32) following.remove(following.keys.first())
            following.merge(word.lowercase(java.util.Locale.ROOT), 1) { a, b -> minOf(1000, a + b) }
        }
        if (!bootstrapping) publish()
    }
    // Quadgram map: "w1 w2 w3" -> Map(w4 -> frequency)
    private val quadgrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Trigram map: "w1 w2" -> Map(w3 -> frequency)
    private val trigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Bigram map: "w1" -> Map(w2 -> frequency)
    private val bigrams = ConcurrentHashMap<String, ConcurrentHashMap<String, Int>>()

    // Unigram map: "w" -> frequency
    private val unigrams = ConcurrentHashMap<String, Int>()

    private val totalUnigramCount = java.util.concurrent.atomic.AtomicLong(0L)

    private val personalUnigrams = ConcurrentHashMap<String, Int>()
    private val personalTotal = java.util.concurrent.atomic.AtomicLong(0L)
    private var bootstrapping = true
    @Volatile private var frequencyBackoff: List<String> = emptyList()
    @Volatile private var baseFrequencies: Map<String, Int> = emptyMap()
    @Volatile private var baseTotal = 0L
    @Volatile private var baselineContexts: Map<String, Map<String,Int>> = emptyMap()
    private data class Snapshot(val bigrams: Map<String, Map<String,Int>>, val trigrams: Map<String,Map<String,Int>>,
        val quadgrams: Map<String,Map<String,Int>>, val higherOrders: Map<String,Map<String,Int>>,
        val unigrams: Map<String,Int>, val personalUnigrams: Map<String,Int>)
    @Volatile private var predictionSnapshot = Snapshot(emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptyMap(),emptyMap())
    @Volatile var version = 0L
        private set
    @Synchronized private fun publish() {
        fun freeze(table: ConcurrentHashMap<String, ConcurrentHashMap<String,Int>>): Map<String,Map<String,Int>> {
            while(table.size > 4000) table.remove(table.keys.first())
            table.values.forEach { while(it.size > 32) it.remove(it.keys.first()) }
            return table.mapValues { it.value.toMap() }
        }
        while(personalUnigrams.size > 2000) personalUnigrams.remove(personalUnigrams.keys.first())
        predictionSnapshot = Snapshot(freeze(bigrams),freeze(trigrams),freeze(quadgrams),freeze(higherOrders),unigrams.toMap(),personalUnigrams.toMap())
        version++
    }
    /** Probability evidence for the ranker, with up to five context words and count-aware backoff. */
    fun contextEvidence(word: String, context: List<String>): Float = scorer(context).evidence(word)

    /** Prepare the immutable context distributions once for all competing candidates.
     * Normalization, context keys and distribution totals do not depend on the target word. */
    internal class ContextScorer(private val frequencies: Map<String,Int>, private val personal: Map<String,Int>,
        private val total: Float, private val distributions: List<Triple<Map<String,Int>,Float,Float>>) {
        fun probability(word: String): Float {
            val target=word.lowercase(java.util.Locale.ROOT).trim()
            if(target.isEmpty()) return .000001f
            val unigram=(frequencies[target] ?: 0)+(personal[target] ?: 0)
            var result=(unigram.coerceAtLeast(1)/total).coerceIn(.000001f,1f)
            for((counts,observations,weight) in distributions)
                result=weight*((counts[target] ?: 0)/observations)+(1f-weight)*result
            return result.coerceIn(.000001f,1f)
        }
        fun evidence(word: String)=(probability(word)*8f).coerceIn(0f,1f)
    }

    internal fun scorer(contextWords: List<String>): ContextScorer {
        val state=predictionSnapshot
        val bundled=baselineContexts
        val context=contextWords.takeLast(5).map { it.lowercase(java.util.Locale.ROOT).trim() }.filter { it.isNotEmpty() }
        val keys=(1..context.size).map { context.takeLast(it).joinToString(" ") }
        val distributions=ArrayList<Triple<Map<String,Int>,Float,Float>>(15)
        fun add(counts: Map<String,Int>?) {
            if(counts.isNullOrEmpty()) return
            val observations=counts.values.sum().toFloat().coerceAtLeast(1f)
            distributions.add(Triple(counts,observations,observations/(observations+12f)))
        }
        fun learned(size: Int,key: String)=when(size) { 1 -> state.bigrams[key]; 2 -> state.trigrams[key]; 3 -> state.quadgrams[key]; else -> state.higherOrders[key] }
        keys.take(3).forEachIndexed { i,key -> add(learned(i+1,key)); add(bundled[key]) }
        keys.drop(3).forEachIndexed { i,key -> add(learned(i+4,key)) }
        keys.drop(3).forEach { key -> add(bundled[key]) }
        // Repeated personal choices remain the final evidence layer.
        keys.forEachIndexed { i,key -> if(key in bundled) add(learned(i+1,key)) }
        val seeded=baseTotal>0
        return ContextScorer(if(seeded) baseFrequencies else state.unigrams,if(seeded) state.personalUnigrams else emptyMap(),
            (if(seeded) baseTotal+personalTotal.get() else totalUnigramCount.get()).coerceAtLeast(1).toFloat(),distributions)
    }

    fun seedUnigramFrequencies(frequencies: Map<String, Int>) {
        // Share the immutable corpus; each keyboard keeps only its learned/curated counts.
        baseFrequencies = frequencies
        baseTotal = frequencies.values.sumOf { it.toLong() }
        version++
        frequencyBackoff = frequencies.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(200).map { it.key }
    }

    /** Immutable bundled conversational evidence, loaded once on the startup worker. */
    fun seedContextBaseline(contexts: Map<String, Map<String,Int>>) {
        baselineContexts = contexts
        version++
    }

    init {
        seedCommonNGrams()
        bootstrapping = false
        publish()
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
        map.merge(target, freq) { a, b -> a + b }
        addTrigram(w2, w3, w4, freq)
    }

    fun addTrigram(w1: String, w2: String, w3: String, freq: Int = 1) {
        val key = "${w1.lowercase()} ${w2.lowercase()}"
        val target = w3.lowercase()
        val map = trigrams.getOrPut(key) { ConcurrentHashMap() }
        map.merge(target, freq) { a, b -> a + b }
        addBigram(w2, w3, freq)
    }

    fun addBigram(w1: String, w2: String, freq: Int = 1) {
        val key = w1.lowercase()
        val target = w2.lowercase()
        val map = bigrams.getOrPut(key) { ConcurrentHashMap() }
        map.merge(target, freq) { a, b -> a + b }
        unigrams.merge(target, freq) { a, b -> a + b }
        totalUnigramCount.addAndGet(freq.toLong())
        if (!bootstrapping) {
            personalUnigrams.merge(target, freq) { a, b -> a + b }
            personalTotal.addAndGet(freq.toLong())
        }
        if (!bootstrapping) publish()
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
            unigrams.merge(w1, 1) { a, b -> a + b }
            totalUnigramCount.incrementAndGet()
            personalUnigrams.merge(w1, 1) { a, b -> a + b }
            personalTotal.incrementAndGet()

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
        publish()
    }

    /**
     * Computes the smoothed language model probability P(Word | Context) using Jelinek-Mercer interpolation
     * across Quadgram, Trigram, Bigram, and Unigram layers:
     * Each context weights its observed distribution by N / (N + 12),
     * backing off to the smoothed lower-order distribution.
     */
    override fun getProbability(word: String, contextWords: List<String>): Float {
        return scorer(contextWords).probability(word)
    }

    /**
     * Predicts the next word candidates based on previous sequence of words typed by the user,
     * matching an optional partially typed word prefix.
     */
    override fun predictNextWords(contextWords: List<String>, prefix: String, maxResults: Int): List<String> {
        val state = predictionSnapshot
        val bigrams=state.bigrams; val trigrams=state.trigrams; val quadgrams=state.quadgrams; val higherOrders=state.higherOrders
        val unigrams=state.unigrams; val personalUnigrams=state.personalUnigrams
        if (maxResults <= 0) return emptyList()
        val context = contextWords.takeLast(5).map { it.lowercase(java.util.Locale.ROOT).trim() }.filter { it.isNotEmpty() }
        val cleanPrefix = prefix.lowercase(java.util.Locale.ROOT).trim()
        val candidates = linkedSetOf<String>()
        for (size in 1..minOf(5,context.size)) candidates.addAll(baselineContexts[context.takeLast(size).joinToString(" ")]?.keys.orEmpty())
        for (size in 4..minOf(5, context.size)) candidates.addAll(higherOrders[context.takeLast(size).joinToString(" ")]?.keys.orEmpty())
        if (context.size >= 3) candidates.addAll(quadgrams[context.takeLast(3).joinToString(" ")]?.keys.orEmpty())
        if (context.size >= 2) candidates.addAll(trigrams[context.takeLast(2).joinToString(" ")]?.keys.orEmpty())
        if (context.isNotEmpty()) candidates.addAll(bigrams[context.last()]?.keys.orEmpty())
        candidates.addAll(frequencyBackoff.filter { it.startsWith(cleanPrefix) })
        // Personalized words remain discoverable even without a matching n-gram.
        candidates.addAll((if (baseTotal > 0) personalUnigrams else unigrams).keys.filter { it.startsWith(cleanPrefix) })
        val scoring=scorer(context)
        return candidates.asSequence().filter { it.startsWith(cleanPrefix) }
            .map { it to scoring.probability(it) }
            .sortedWith(compareByDescending<Pair<String, Float>> { it.second }.thenBy { it.first })
            .take(maxResults).map { it.first }.toList()
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
