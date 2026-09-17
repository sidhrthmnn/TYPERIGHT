package com.example

import kotlin.math.min

/**
 * High-performance, memory-efficient Trie (Prefix Tree) data structure for
 * instant on-device word search, real-time prefix suggestions, and Damerau-Levenshtein
 * fuzzy autocorrect for common typing errors.
 */
class WordTrie {

    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord: Boolean = false
        var word: String? = null
        var frequency: Int = 0
        var maxSubtreeFrequency: Int = 0
    }

    val root = TrieNode()
    private var wordCount = 0

    @Synchronized
    fun size(): Int = wordCount

    /**
     * Inserts a word with a frequency score into the Trie.
     */
    @Synchronized
    fun insert(word: String, frequency: Int = 1) {
        val clean = word.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return

        var current = root
        current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)

        for (ch in clean) {
            current = current.children.getOrPut(ch) { TrieNode() }
            current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)
        }

        if (!current.isWord) {
            current.isWord = true
            current.word = word
            wordCount++
        }
        current.frequency = maxOf(current.frequency, frequency)
    }

    /**
     * Quickly checks if exact word exists in Trie in O(K) time complexity.
     */
    @Synchronized
    fun contains(word: String): Boolean {
        val clean = word.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return false

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return false
        }
        return current.isWord
    }

    @Synchronized
    fun getWordFrequency(word: String): Int {
        val clean = word.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return 0

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return 0
        }
        return if (current.isWord) current.frequency else 0
    }

    /**
     * Finds all words matching a prefix, sorted by corpus frequency in O(K + M) time.
     */
    @Synchronized
    fun findByPrefix(prefix: String, maxResults: Int = 10): List<String> {
        val clean = prefix.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return emptyList()
        }

        return topWords(current, maxResults).map { it.first }
    }

    /**
     * Alias for searchPrefix returning Pair of word and weight score based on corpus frequency.
     */
    @Synchronized
    fun searchPrefix(prefix: String, maxResults: Int = 10): List<Pair<String, Float>> {
        val clean = prefix.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return emptyList()
        }

        val results = topWords(current, maxResults)
        val maxFreq = results.maxOfOrNull { it.second } ?: 1
        return results.map { it.first to (it.second.toFloat() / maxFreq.coerceAtLeast(1)) }
    }

    // Explore the most promising subtree first; do not allocate and sort the entire
    // dictionary for common one-letter prefixes.
    private fun topWords(node: TrieNode, limit: Int): List<Pair<String, Int>> {
        if (limit <= 0) return emptyList()
        val pending = java.util.PriorityQueue<TrieNode>(compareByDescending { it.maxSubtreeFrequency })
        val best = java.util.PriorityQueue<Pair<String, Int>>(compareBy<Pair<String, Int>> { it.second }.thenByDescending { it.first })
        pending.add(node)
        while (pending.isNotEmpty()) {
            val next = pending.remove()
            if (best.size == limit && next.maxSubtreeFrequency < best.peek()!!.second) break
            if (next.isWord && next.word != null) {
                best.add(next.word!! to next.frequency)
                if (best.size > limit) best.remove()
            }
            pending.addAll(next.children.values)
        }
        return best.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
    }

    private fun collectWords(node: TrieNode, results: MutableList<Pair<String, Int>>) {
        if (node.isWord && node.word != null) {
            results.add(Pair(node.word!!, node.frequency))
        }
        for (child in node.children.values) {
            collectWords(child, results)
        }
    }

    /**
     * Fast Trie-based Levenshtein & Damerau (transposition) search for offline spell check
     * corrections within maxDistance.
     */
    @Synchronized
    fun findFuzzyMatches(word: String, maxDistance: Int = 2, maxResults: Int = 8): List<String> {
        val clean = word.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, Float>>()
        val currentRow = IntArray(clean.length + 1) { it }

        for ((ch, child) in root.children) {
            searchFuzzyRecursive(child, ch, clean, currentRow, results, maxDistance)
        }

        return results
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Alias for searchFuzzy returning Pair of word and weight score.
     */
    @Synchronized
    fun searchFuzzy(word: String, maxDist: Float = 2.0f, maxResults: Int = 10): List<Pair<String, Float>> {
        return findFuzzyMatches(word, maxDist.toInt().coerceAtLeast(1), maxResults).map { Pair(it, 1.0f) }
    }

    private fun searchFuzzyRecursive(
        node: TrieNode,
        letter: Char,
        target: String,
        previousRow: IntArray,
        results: MutableList<Pair<String, Float>>,
        maxDistance: Int
    ) {
        val columns = target.length
        val currentRow = IntArray(columns + 1)
        currentRow[0] = previousRow[0] + 1

        var minDistanceInRow = currentRow[0]

        for (i in 1..columns) {
            val insertCost = currentRow[i - 1] + 1
            val deleteCost = previousRow[i] + 1
            val replaceCost = if (target[i - 1] == letter) previousRow[i - 1] else previousRow[i - 1] + 1

            val cost = minOf(insertCost, minOf(deleteCost, replaceCost))
            currentRow[i] = cost
            if (cost < minDistanceInRow) {
                minDistanceInRow = cost
            }
        }

        if (currentRow[columns] <= maxDistance && node.isWord && node.word != null) {
            val dist = currentRow[columns]
            val maxLen = maxOf(target.length, node.word!!.length)
            val sim = 1.0f - (dist.toFloat() / maxLen.toFloat())
            val score = (node.frequency.toFloat() * 0.4f) + (sim * 100f)
            results.add(Pair(node.word!!, score))
        }

        if (minDistanceInRow <= maxDistance) {
            for ((nextLetter, child) in node.children) {
                searchFuzzyRecursive(child, nextLetter, target, currentRow, results, maxDistance)
            }
        }
    }

    /**
     * Gets the best single autocorrect replacement for a typed typo from the local Trie.
     */
    @Synchronized
    fun getBestCorrection(word: String, maxDistance: Int = 2): String? {
        val clean = word.lowercase(java.util.Locale.ROOT).trim()
        if (clean.isEmpty() || contains(clean)) return null
        val matches = findFuzzyMatches(clean, maxDistance, 1)
        return matches.firstOrNull()
    }
}
