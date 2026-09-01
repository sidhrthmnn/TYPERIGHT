package com.aistudio.typeright.util

import kotlin.math.min

/**
 * Utility for handling common text operations
 */
object TextUtils {
    
    /**
     * Remove filler words from text
     */
    fun removeFillerWords(text: String): String {
        val fillers = listOf(
            "um", "uh", "like", "you know", "actually",
            "basically", "right", "okay", "so like"
        )
        
        var result = text
        for (filler in fillers) {
            result = result.replace(
                Regex("\\b$filler\\b", RegexOption.IGNORE_CASE),
                ""
            )
        }
        
        // Clean up extra spaces
        result = result.replace(Regex("\\s+"), " ").trim()
        return result
    }
    
    /**
     * Split text into words
     */
    fun getWords(text: String): List<String> {
        return text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
    
    /**
     * Get last word from text
     */
    fun getLastWord(text: String): String {
        val words = getWords(text)
        return if (words.isNotEmpty()) words.last() else ""
    }
    
    /**
     * Calculate similarity between two strings (0-1)
     */
    fun similarity(s1: String, s2: String): Float {
        val maxLength = max(s1.length, s2.length)
        if (maxLength == 0) return 1f
        
        val distance = levenshteinDistance(s1, s2)
        return 1f - (distance.toFloat() / maxLength)
    }
    
    /**
     * Calculate Levenshtein distance
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val costs = IntArray(s2.length + 1) { it }
        
        for (i in 1..s1.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..s2.length) {
                val nc = if (s1[i - 1] == s2[j - 1]) nw else minOf(nw, costs[j], costs[j - 1]) + 1
                nw = costs[j]
                costs[j] = nc
            }
        }
        
        return costs[s2.length]
    }
    
    private fun max(a: Int, b: Int) = if (a > b) a else b
}
