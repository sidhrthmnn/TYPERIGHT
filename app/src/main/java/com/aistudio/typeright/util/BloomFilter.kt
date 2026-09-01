package com.aistudio.typeright.util

import kotlin.math.min

/**
 * Bloom filter for fast membership testing
 * Space-efficient probabilistic data structure
 */
class BloomFilter(
    private val size: Int = 10000,
    private val numHashes: Int = 3
) {
    private val bits = BooleanArray(size)
    
    /**
     * Add an element to the filter
     */
    fun add(element: String) {
        for (i in 0 until numHashes) {
            val hash = hash(element, i) % size
            bits[hash] = true
        }
    }
    
    /**
     * Check if element might be in the set (with false positive probability)
     */
    fun mightContain(element: String): Boolean {
        for (i in 0 until numHashes) {
            val hash = hash(element, i) % size
            if (!bits[hash]) return false
        }
        return true
    }
    
    private fun hash(element: String, seed: Int): Int {
        var hash = seed
        for (char in element) {
            hash = hash * 31 + char.code
        }
        return if (hash < 0) -hash else hash
    }
}
