package com.aistudio.typeright.test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.aistudio.typeright.util.TrieDictionary

/**
 * Unit tests for Trie dictionary
 */
class TrieDictionaryTest {
    
    private val trie = TrieDictionary()
    
    @Test
    fun testInsertAndSearch() {
        trie.insert("hello")
        assertTrue(trie.search("hello"))
        assertFalse(trie.search("hell"))
    }
    
    @Test
    fun testPrefixMatching() {
        trie.insert("hello", 5)
        trie.insert("help", 3)
        trie.insert("herbal", 2)
        
        val results = trie.getWithPrefix("he")
        assertEquals(3, results.size)
        assertEquals("hello", results[0]) // highest frequency first
    }
    
    @Test
    fun testCaseInsensitivity() {
        trie.insert("Hello")
        assertTrue(trie.search("hello"))
        assertTrue(trie.search("HELLO"))
    }
}
