package com.aistudio.typeright.util

/**
 * Trie data structure for fast dictionary lookups
 */
class TrieNode {
    val children = mutableMapOf<Char, TrieNode>()
    var isWord = false
    var frequency = 0
}

class TrieDictionary {
    private val root = TrieNode()
    
    /**
     * Insert a word with frequency into the trie
     */
    fun insert(word: String, frequency: Int = 1) {
        var node = root
        for (char in word.lowercase()) {
            node = node.children.getOrPut(char) { TrieNode() }
        }
        node.isWord = true
        node.frequency = frequency
    }
    
    /**
     * Search for exact word match
     */
    fun search(word: String): Boolean {
        val node = findNode(word)
        return node?.isWord ?: false
    }
    
    /**
     * Get all words with given prefix (O(k) where k is prefix length)
     */
    fun getWithPrefix(prefix: String, limit: Int = 10): List<String> {
        val results = mutableListOf<String>()
        val node = findNode(prefix) ?: return results
        
        dfs(node, prefix, results, limit)
        return results.sortedByDescending { 
            getFrequency(it)
        }.take(limit)
    }
    
    /**
     * Get frequency of a word
     */
    fun getFrequency(word: String): Int {
        return findNode(word)?.frequency ?: 0
    }
    
    private fun findNode(word: String): TrieNode? {
        var node = root
        for (char in word.lowercase()) {
            node = node.children[char] ?: return null
        }
        return node
    }
    
    private fun dfs(
        node: TrieNode,
        current: String,
        results: MutableList<String>,
        limit: Int
    ) {
        if (results.size >= limit) return
        if (node.isWord) results.add(current)
        
        for ((char, child) in node.children) {
            dfs(child, current + char, results, limit)
        }
    }
}
