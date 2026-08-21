package com.example

import kotlin.math.min

/**
 * Robust Levenshtein Distance & Damerau-Levenshtein Auto-Correction Engine.
 *
 * Compares user input against the local dictionary to compute exact minimal edit operations
 * (insertions, deletions, substitutions, and transpositions) to provide highly accurate
 * spelling corrections for typing errors.
 */
object LevenshteinAutoCorrector {

    data class LevenshteinMatch(
        val word: String,
        val distance: Int,
        val similarityScore: Float,
        val frequency: Int = 0
    )

    /**
     * Standard Levenshtein Distance Algorithm (O(N * M) time, O(min(N, M)) space optimization).
     * Computes the minimum number of single-character edits (insertions, deletions, or substitutions)
     * required to change string [s1] into string [s2].
     */
    fun computeLevenshteinDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()

        if (str1 == str2) return 0
        if (str1.isEmpty()) return str2.length
        if (str2.isEmpty()) return str1.length

        val n = str1.length
        val m = str2.length

        // Space-optimized DP with two rows
        var previousRow = IntArray(m + 1) { it }
        var currentRow = IntArray(m + 1)

        for (i in 1..n) {
            currentRow[0] = i
            val char1 = str1[i - 1]

            for (j in 1..m) {
                val char2 = str2[j - 1]
                val cost = if (char1 == char2) 0 else 1

                currentRow[j] = min(
                    previousRow[j] + 1,      // Deletion from str1
                    min(
                        currentRow[j - 1] + 1,  // Insertion into str1
                        previousRow[j - 1] + cost // Substitution
                    )
                )
            }

            val temp = previousRow
            previousRow = currentRow
            currentRow = temp
        }

        return previousRow[m]
    }

    /**
     * Full Damerau-Levenshtein Distance Algorithm.
     * In addition to insertions, deletions, and substitutions, counts adjacent character
     * transpositions (e.g., "teh" -> "the", "wiht" -> "with") as a single edit step (distance = 1).
     */
    fun computeDamerauLevenshteinDistance(s1: String, s2: String): Int {
        val str1 = s1.lowercase().trim()
        val str2 = s2.lowercase().trim()

        if (str1 == str2) return 0
        if (str1.isEmpty()) return str2.length
        if (str2.isEmpty()) return str1.length

        val n = str1.length
        val m = str2.length

        val dp = Array(n + 1) { IntArray(m + 1) }

        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (str1[i - 1] == str2[j - 1]) 0 else 1

                dp[i][j] = min(
                    dp[i - 1][j] + 1,       // Deletion
                    min(
                        dp[i][j - 1] + 1,   // Insertion
                        dp[i - 1][j - 1] + cost // Substitution
                    )
                )

                // Transposition check
                if (i > 1 && j > 1 &&
                    str1[i - 1] == str2[j - 2] &&
                    str1[i - 2] == str2[j - 1]
                ) {
                    dp[i][j] = min(dp[i][j], dp[i - 2][j - 2] + 1)
                }
            }
        }

        return dp[n][m]
    }

    /**
     * Calculates a normalized similarity coefficient between 0.0 (completely distinct) and 1.0 (exact match).
     */
    fun computeNormalizedSimilarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val distance = computeDamerauLevenshteinDistance(s1, s2)
        return (1.0f - (distance.toFloat() / maxLen.toFloat())).coerceIn(0.0f, 1.0f)
    }

    /**
     * Finds the closest dictionary candidates for a misspelled input word using Levenshtein distance.
     *
     * @param input The user's typed word (potentially misspelled).
     * @param dictionary List of valid dictionary words with optional frequency weighting.
     * @param maxDistance Maximum edit distance threshold (default: 2 edits).
     * @param maxResults Maximum number of corrections to return.
     */
    fun findClosestWords(
        input: String,
        dictionary: List<DictionaryManager.WordFrequency>,
        maxDistance: Int = 2,
        maxResults: Int = 5
    ): List<LevenshteinMatch> {
        val cleanInput = input.lowercase().trim()
        if (cleanInput.isEmpty()) return emptyList()

        val inputLen = cleanInput.length
        val matches = mutableListOf<LevenshteinMatch>()

        for (item in dictionary) {
            val candidate = item.word.lowercase()
            val candidateLen = candidate.length

            // Quick length filter: if length difference exceeds maxDistance, edit distance must exceed maxDistance
            if (kotlin.math.abs(inputLen - candidateLen) > maxDistance) continue

            // Quick prefix match optimization
            val firstCharSame = cleanInput[0] == candidate[0]
            val effectiveMaxDistance = if (firstCharSame) maxDistance else min(maxDistance, 1)

            val distance = computeDamerauLevenshteinDistance(cleanInput, candidate)

            if (distance <= effectiveMaxDistance) {
                val maxLen = maxOf(inputLen, candidateLen)
                val similarity = if (maxLen > 0) (1.0f - (distance.toFloat() / maxLen.toFloat())) else 1.0f
                matches.add(
                    LevenshteinMatch(
                        word = item.word,
                        distance = distance,
                        similarityScore = similarity,
                        frequency = item.frequency
                    )
                )
            }
        }

        // Rank by smallest edit distance first, then highest corpus frequency, then similarity
        return matches.sortedWith(
            compareBy<LevenshteinMatch> { it.distance }
                .thenByDescending { it.frequency }
                .thenByDescending { it.similarityScore }
        ).take(maxResults)
    }

    /**
     * Traverses a Trie data structure with bounded Levenshtein pruning for ultra-fast lookup (<1ms).
     */
    fun searchTrieLevenshtein(
        trieRoot: DictionaryManager.TrieNode,
        target: String,
        maxDistance: Int = 2
    ): List<LevenshteinMatch> {
        val cleanTarget = target.lowercase().trim()
        if (cleanTarget.isEmpty()) return emptyList()

        val results = mutableListOf<LevenshteinMatch>()
        val initialRow = IntArray(cleanTarget.length + 1) { it }

        for ((char, child) in trieRoot.children) {
            searchTrieRecursive(
                node = child,
                char = char,
                word = char.toString(),
                previousRow = initialRow,
                target = cleanTarget,
                maxDistance = maxDistance,
                results = results
            )
        }

        return results.sortedWith(
            compareBy<LevenshteinMatch> { it.distance }
                .thenByDescending { it.frequency }
        )
    }

    private fun searchTrieRecursive(
        node: DictionaryManager.TrieNode,
        char: Char,
        word: String,
        previousRow: IntArray,
        target: String,
        maxDistance: Int,
        results: MutableList<LevenshteinMatch>
    ) {
        val columns = target.length + 1
        val currentRow = IntArray(columns)
        currentRow[0] = previousRow[0] + 1

        var minRowValue = currentRow[0]

        for (c in 1 until columns) {
            val insertCost = currentRow[c - 1] + 1
            val deleteCost = previousRow[c] + 1
            val replaceCost = if (target[c - 1] == char) previousRow[c - 1] else previousRow[c - 1] + 1

            currentRow[c] = min(insertCost, min(deleteCost, replaceCost))
            if (currentRow[c] < minRowValue) {
                minRowValue = currentRow[c]
            }
        }

        // If the current row's minimum cost exceeds maxDistance, prune this Trie branch!
        if (minRowValue > maxDistance) return

        // If this node represents a complete dictionary word and distance <= maxDistance, record it!
        if (node.isWord && currentRow[target.length] <= maxDistance) {
            val dist = currentRow[target.length]
            val maxLen = maxOf(target.length, word.length)
            val sim = 1.0f - (dist.toFloat() / maxLen.toFloat())
            results.add(
                LevenshteinMatch(
                    word = node.word ?: word,
                    distance = dist,
                    similarityScore = sim,
                    frequency = node.frequency
                )
            )
        }

        // Recurse into children
        for ((nextChar, nextChild) in node.children) {
            searchTrieRecursive(
                node = nextChild,
                char = nextChar,
                word = word + nextChar,
                previousRow = currentRow,
                target = target,
                maxDistance = maxDistance,
                results = results
            )
        }
    }
}
