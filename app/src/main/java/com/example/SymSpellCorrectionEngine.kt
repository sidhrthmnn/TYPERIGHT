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
    val maxEditDistance: Int = 2,
    private val prefixLength: Int = 7
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
     * Checks if a word exists directly in the dictionary.
     */
    fun hasWord(word: String): Boolean {
        return wordFrequencyMap.containsKey(word.lowercase().trim())
    }

    /**
     * Inserts a dictionary word and precomputes its deletion variants up to maxEditDistance.
     */
    fun insertWord(word: String, frequency: Int = 1) {
        val lower = word.lowercase().trim()
        if (lower.isEmpty() || lower.length > 32) return

        val existing = wordFrequencyMap.putIfAbsent(lower, frequency)
        if (existing != null) {
            wordFrequencyMap[lower] = maxOf(existing, frequency)
            return
        }
        // Prefix deletions bound memory; candidates still use full-word edit distance.
        val deletes = getDeletes(lower.take(prefixLength), maxEditDistance)
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
        results.add(word)

        while (queue.isNotEmpty()) {
            val (current, dist) = queue.removeFirst()
            results.add(current)
            if (dist < maxDistance) {
                for (i in current.indices) {
                    val next = current.substring(0, i) + current.substring(i + 1)
                    if (next.isNotEmpty() && results.add(next)) {
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
        if (lower.isEmpty() || lower.length > 32 || maxResults <= 0) return emptyList()

        val candidates = HashSet<String>()
        val inputDeletes = getDeletes(lower.take(prefixLength), maxEditDistance)

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
            if (kotlin.math.abs(candidate.length - lower.length) * .95f > maxDistance + .35f) continue
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
    fun computeWeightedDamerauLevenshtein(s1: String, s2: String): Float = spatialModel.weightedEditDistance(s1, s2)

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
