package com.example

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 2 & 3. SymSpell-Style Bounded, Weighted Edit-Distance Correction Engine
 *
 * Employs Symmetric Delete Spelling Correction (SymSpell) to achieve sub-millisecond
 * fuzzy candidate lookup by precomputing word deletions (up to max edit distance 2)
 * instead of brute-force checking against every dictionary entry.
 *
 * Integrates with SpatialKeyProximityModel so that substitution costs are weighted
 * by physical QWERTY key distances.
 */
class SymSpellCorrectionEngine(
    private val spatialModel: SpatialKeyProximityModel = SpatialKeyProximityModel(),
    val maxEditDistance: Int = 2
) {

    data class SuggestionItem(
        val term: String,
        val distance: Float,
        val frequency: Int
    )

    // Map: deletionString -> Set of full terms that produce this deletion
    private val deletesMap = ConcurrentHashMap<String, MutableSet<String>>()
    
    // Map: term -> frequency
    private val wordFrequencyMap = ConcurrentHashMap<String, Int>()

    /**
     * Inserts a dictionary word and precomputes its deletion variants up to maxEditDistance.
     */
    fun insertWord(word: String, frequency: Int = 1) {
        val lower = word.lowercase().trim()
        if (lower.isEmpty()) return

        wordFrequencyMap[lower] = maxOf(wordFrequencyMap[lower] ?: 0, frequency)

        // Precompute deletes up to distance 2
        val deletes = getDeletes(lower, maxEditDistance)
        for (del in deletes) {
            val set = deletesMap.getOrPut(del) { ConcurrentHashMap.newKeySet() }
            set.add(lower)
        }
    }

    /**
     * Generates all deletion combinations of a string up to maxDistance.
     */
    fun getDeletes(word: String, maxDistance: Int): Set<String> {
        val results = HashSet<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(Pair(word, 0))

        while (queue.isNotEmpty()) {
            val (current, dist) = queue.removeFirst()
            results.add(current)
            if (dist < maxDistance) {
                for (i in current.indices) {
                    val next = current.substring(0, i) + current.substring(i + 1)
                    if (next.isNotEmpty() && !results.contains(next)) {
                        queue.add(Pair(next, dist + 1))
                    }
                }
            }
        }
        return results
    }

    /**
     * Look up correction candidates for an input word using SymSpell symmetric delete intersection.
     * Calculates spatial weighted Damerau-Levenshtein distance.
     */
    fun lookup(
        input: String,
        maxDistance: Float = 2.0f,
        maxResults: Int = 10
    ): List<SuggestionItem> {
        val lower = input.lowercase().trim()
        if (lower.isEmpty()) return emptyList()

        val candidates = HashSet<String>()
        val inputDeletes = getDeletes(lower, maxEditDistance)

        // 1. Direct dictionary match
        if (wordFrequencyMap.containsKey(lower)) {
            candidates.add(lower)
        }

        // 2. Symmetric delete intersection: Find all dictionary words sharing deletions
        for (del in inputDeletes) {
            val matchingWords = deletesMap[del]
            if (matchingWords != null) {
                candidates.addAll(matchingWords)
            }
        }

        // 3. Score candidates with weighted spatial Damerau-Levenshtein distance
        val scoredList = mutableListOf<SuggestionItem>()
        for (candidate in candidates) {
            val dist = computeWeightedDamerauLevenshtein(lower, candidate)
            if (dist <= maxDistance + 0.35f) {
                val freq = wordFrequencyMap[candidate] ?: 1
                scoredList.add(SuggestionItem(candidate, dist, freq))
            }
        }

        // Sort by distance ascending, then frequency descending
        return scoredList.sortedWith(
            compareBy<SuggestionItem> { it.distance }
                .thenByDescending { it.frequency }
        ).take(maxResults)
    }

    /**
     * Calculates bounded Damerau-Levenshtein distance weighted by physical key proximity.
     */
    fun computeWeightedDamerauLevenshtein(s1: String, s2: String): Float {
        val w1 = s1.lowercase().replace("'", "")
        val w2 = s2.lowercase().replace("'", "")
        if (w1 == w2) return 0.0f

        val n = w1.length
        val m = w2.length
        val dp = Array(n + 1) { FloatArray(m + 1) }

        for (i in 0..n) dp[i][0] = i * 0.95f
        for (j in 0..m) dp[0][j] = j * 0.95f

        for (i in 1..n) {
            for (j in 1..m) {
                val subCost = if (w1[i - 1] == w2[j - 1]) {
                    0.0f
                } else {
                    spatialModel.getWeightedSubstitutionCost(w1[i - 1], w2[j - 1])
                }

                dp[i][j] = minOf(
                    dp[i - 1][j] + 0.95f,       // deletion
                    dp[i][j - 1] + 0.95f,       // insertion
                    dp[i - 1][j - 1] + subCost  // weighted substitution
                )

                // Transposition check (e.g. teh -> the, adn -> and)
                if (i > 1 && j > 1 && w1[i - 1] == w2[j - 2] && w1[i - 2] == w2[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 0.25f)
                }
            }
        }

        return dp[n][m]
    }

    /**
     * Checks if a word is in the dictionary.
     */
    fun contains(word: String): Boolean = wordFrequencyMap.containsKey(word.lowercase().trim())

    /**
     * Gets word frequency.
     */
    fun getFrequency(word: String): Int = wordFrequencyMap[word.lowercase().trim()] ?: 0

    /**
     * Clear or reset user additions.
     */
    fun clear() {
        deletesMap.clear()
        wordFrequencyMap.clear()
    }
}
