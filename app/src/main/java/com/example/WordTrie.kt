package com.example

import java.util.PriorityQueue

/**
 * High-performance, memory-efficient Trie (Prefix Tree) data structure for
 * instant on-device word search, offline spell checking, and low-latency predictions.
 */
class WordTrie {

    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord: Boolean = false
        var word: String? = null
        var frequency: Int = 0
        var maxSubtreeFrequency: Int = 0
    }

    private val root = TrieNode()
    private var size = 0

    fun size(): Int = size

    /**
     * Inserts a word with a frequency score into the Trie.
     */
    fun insert(word: String, frequency: Int = 1) {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return

        var current = root
        current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)

        for (ch in lower) {
            current = current.children.getOrPut(ch) { TrieNode() }
            current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)
        }

        if (!current.isWord) {
            current.isWord = true
            current.word = word
            size++
        }
        current.frequency = maxOf(current.frequency, frequency)
    }

    /**
     * Quickly checks if exact word exists in Trie in O(K) time complexity.
     */
    fun contains(word: String): Boolean {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return false

        var current = root
        for (ch in lower) {
            current = current.children[ch] ?: return false
        }
        return current.isWord
    }

    /**
     * Alias for searchPrefix returning Pair of word and weight score.
     */
    fun searchPrefix(prefix: String, maxResults: Int = 10): List<Pair<String, Float>> {
        return findByPrefix(prefix, maxResults).map { Pair(it, 1.0f) }
    }

    /**
     * Alias for searchFuzzy returning Pair of word and weight score.
     */
    fun searchFuzzy(word: String, maxDist: Float = 2.0f, maxResults: Int = 10): List<Pair<String, Float>> {
        return findFuzzyMatches(word, maxDist.toInt().coerceAtLeast(1), maxResults).map { Pair(it, 1.0f) }
    }

    /**
     * Finds all words matching a prefix, sorted by frequency in O(K + M) time complexity.
     */
    fun findByPrefix(prefix: String, maxResults: Int = 10): List<String> {
        val lower = prefix.lowercase().trim()
        if (lower.isEmpty()) return emptyList()

        var current = root
        for (ch in lower) {
            current = current.children[ch] ?: return emptyList()
        }

        val results = mutableListOf<Pair<String, Int>>()
        collectWords(current, results)

        return results
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
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
     * Fast Trie-based Levenshtein search for offline spell check corrections within maxDistance.
     */
    fun findFuzzyMatches(word: String, maxDistance: Int = 2, maxResults: Int = 5): List<String> {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<String, Int>>()
        val currentRow = IntArray(lower.length + 1) { it }

        for ((ch, child) in root.children) {
            searchFuzzyRecursive(child, ch, lower, currentRow, results, maxDistance)
        }

        return results
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    private fun searchFuzzyRecursive(
        node: TrieNode,
        letter: Char,
        target: String,
        previousRow: IntArray,
        results: MutableList<Pair<String, Int>>,
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
            val score = node.frequency - (dist * 100)
            results.add(Pair(node.word!!, score))
        }

        if (minDistanceInRow <= maxDistance) {
            for ((nextLetter, child) in node.children) {
                searchFuzzyRecursive(child, nextLetter, target, currentRow, results, maxDistance)
            }
        }
    }
}
