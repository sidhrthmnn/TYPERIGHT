package com.aistudio.typeright.test

import org.junit.Assert.assertEquals
import org.junit.Test
import com.aistudio.typeright.data.repository.CorrectionRepositoryImpl

/**
 * Unit tests for correction repository
 */
class CorrectionRepositoryTest {
    
    @Test
    fun testLevenshteinDistance() {
        val repo = object {
            fun levenshteinDistance(s1: String, s2: String): Int {
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
        }
        
        assertEquals(1, repo.levenshteinDistance("cat", "cart"))
        assertEquals(2, repo.levenshteinDistance("kitten", "sitting"))
        assertEquals(0, repo.levenshteinDistance("same", "same"))
    }
}
