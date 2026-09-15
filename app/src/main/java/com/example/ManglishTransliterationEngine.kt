package com.example

import android.content.Context
import java.util.Locale

/**
 * On-Device Manglish (Malayalam in Latin script) to Malayalam Script Transliteration Engine.
 *
 * Supports real-time phonetic syllable parsing, chillu letters, conjuncts (koottuksharangal),
 * and an extensive built-in dictionary of common Malayalam words and conversational vocabulary.
 */
class ManglishTransliterationEngine private constructor(private val context: Context) {

    val trie = MalayalamManglishTrie()

    companion object {
        private const val TAG = "ManglishTransliteration"

        @Volatile
        private var instance: ManglishTransliterationEngine? = null

        fun getInstance(context: Context): ManglishTransliterationEngine {
            return instance ?: synchronized(this) {
                instance ?: ManglishTransliterationEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * Transliterates a Manglish word or phrase to Malayalam script using the local Trie.
     * Returns a list of candidate suggestions:
     * 1. Primary Malayalam script conversion
     * 2. Manglish literal original
     * 3. Alternative/extended Malayalam forms
     */
    fun getTransliterationCandidates(input: String): List<String> {
        val clean = input.trim()
        if (clean.isEmpty()) return emptyList()

        val lower = clean.lowercase(Locale.ROOT)

        // 1. Trie exact match
        val exactTrieMatch = trie.findExact(lower)

        // 2. Trie prefix search
        val prefixPredictions = trie.searchPrefix(lower, maxResults = 3)

        // 3. Algorithmic phonetic transliteration as dynamic fallback
        val phoneticMalayalam = transliteratePhonetic(clean)

        val candidates = mutableListOf<String>()

        if (exactTrieMatch != null) {
            candidates.add(exactTrieMatch.malayalam)
            candidates.add(clean) // Manglish option
            if (exactTrieMatch.alternatives.isNotEmpty()) {
                candidates.addAll(exactTrieMatch.alternatives)
            }
            if (phoneticMalayalam != exactTrieMatch.malayalam && phoneticMalayalam.isNotBlank()) {
                candidates.add(phoneticMalayalam)
            }
            // Add top prefix extension if available
            prefixPredictions.firstOrNull { it.malayalam != exactTrieMatch.malayalam }?.let {
                candidates.add(it.malayalam)
            }
        } else if (prefixPredictions.isNotEmpty()) {
            val topPrefix = prefixPredictions.first()
            candidates.add(topPrefix.malayalam)
            candidates.add(clean)
            if (phoneticMalayalam.isNotBlank() && phoneticMalayalam != topPrefix.malayalam) {
                candidates.add(phoneticMalayalam)
            }
            prefixPredictions.drop(1).forEach {
                candidates.add(it.malayalam)
            }
        } else {
            // Fuzzy search in Trie for near matches
            val fuzzyMatches = trie.searchFuzzy(lower, maxDistance = 2, maxResults = 2)
            if (phoneticMalayalam.isNotBlank()) {
                candidates.add(phoneticMalayalam)
                candidates.add(clean)
                fuzzyMatches.forEach { match ->
                    if (!candidates.contains(match.malayalam)) {
                        candidates.add(match.malayalam)
                    }
                }
            } else if (fuzzyMatches.isNotEmpty()) {
                candidates.add(fuzzyMatches.first().malayalam)
                candidates.add(clean)
            } else {
                candidates.add(clean)
            }
        }

        return candidates.distinct().take(3)
    }

    /**
     * Query Trie directly for prefix predictions.
     */
    fun searchTrie(prefix: String, maxResults: Int = 5): List<MalayalamPrediction> {
        return trie.searchPrefix(prefix, maxResults)
    }

    /**
     * Algorithmic phonetic transliteration for Manglish into Malayalam script.
     */
    fun transliteratePhonetic(input: String): String {
        if (input.isBlank()) return ""
        val text = input.lowercase(Locale.ROOT)
        val sb = StringBuilder()
        var i = 0
        val len = text.length

        while (i < len) {
            // Match 4-char sequences
            if (i + 4 <= len) {
                val sub4 = text.substring(i, i + 4)
                when (sub4) {
                    "ngal" -> { sb.append("ങ്ങൾ"); i += 4; continue }
                    "njal" -> { sb.append("ഞങ്ങൾ"); i += 4; continue }
                }
            }

            // Match 3-char sequences
            if (i + 3 <= len) {
                val sub3 = text.substring(i, i + 3)
                when (sub3) {
                    "ch" -> {
                        // Lookahead for vowel
                        val (malayalam, consumed) = matchConsonantWithVowel("ച", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "sh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ശ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "zh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഴ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "th" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ത", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "dh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ധ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "bh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഭ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "ph" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഫ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "kh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഖ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "gh" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഘ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "nj" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ഞ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                    "ng" -> {
                        val (malayalam, consumed) = matchConsonantWithVowel("ങ", text, i + 2)
                        sb.append(malayalam)
                        i = consumed
                        continue
                    }
                }
            }

            // Match independent vowels at start or after non-consonant
            val isStartOrVowel = i == 0 || isVowel(text[i - 1])
            if (isStartOrVowel) {
                if (i + 2 <= len) {
                    val sub2 = text.substring(i, i + 2)
                    when (sub2) {
                        "aa" -> { sb.append("ആ"); i += 2; continue }
                        "ee" -> { sb.append("ഈ"); i += 2; continue }
                        "oo" -> { sb.append("ഊ"); i += 2; continue }
                        "ai" -> { sb.append("ഐ"); i += 2; continue }
                        "au", "ou" -> { sb.append("ഔ"); i += 2; continue }
                    }
                }
                when (text[i]) {
                    'a' -> { sb.append("അ"); i += 1; continue }
                    'i' -> { sb.append("ഇ"); i += 1; continue }
                    'u' -> { sb.append("ഉ"); i += 1; continue }
                    'e' -> { sb.append("എ"); i += 1; continue }
                    'o' -> { sb.append("ഒ"); i += 1; continue }
                }
            }

            // Match single consonants + following vowel sign
            val baseChar = when (text[i]) {
                'k' -> "ക"
                'g' -> "ഗ"
                'c' -> "ച"
                'j' -> "ജ"
                't' -> "ട"
                'd' -> "ദ"
                'n' -> if (i == len - 1) "ൻ" else "ന"
                'p' -> "പ"
                'b' -> "ബ"
                'm' -> if (i == len - 1) "ം" else "മ"
                'y' -> "യ"
                'r' -> if (i == len - 1) "ർ" else "ര"
                'l' -> if (i == len - 1) "ൽ" else "ല"
                'v', 'w' -> "വ"
                's' -> "സ"
                'h' -> "ഹ"
                else -> null
            }

            if (baseChar != null) {
                val (malayalam, consumed) = matchConsonantWithVowel(baseChar, text, i + 1)
                sb.append(malayalam)
                i = consumed
            } else {
                sb.append(text[i])
                i++
            }
        }

        return sb.toString()
    }

    private fun isVowel(c: Char): Boolean {
        return c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'
    }

    private fun matchConsonantWithVowel(baseConsonant: String, text: String, startIndex: Int): Pair<String, Int> {
        val len = text.length
        if (startIndex >= len) {
            // End of word: apply virama/chandrakkala unless it's already a chillu
            val result = if (baseConsonant.length == 1 && baseConsonant in "കഗചജടദപബയലവസഹ") {
                baseConsonant + "്"
            } else {
                baseConsonant
            }
            return Pair(result, startIndex)
        }

        // Check 2-char vowel signs (matras)
        if (startIndex + 2 <= len) {
            val sub2 = text.substring(startIndex, startIndex + 2)
            when (sub2) {
                "aa" -> return Pair(baseConsonant + "ാ", startIndex + 2)
                "ee" -> return Pair(baseConsonant + "ീ", startIndex + 2)
                "oo" -> return Pair(baseConsonant + "ൂ", startIndex + 2)
                "ai" -> return Pair(baseConsonant + "ൈ", startIndex + 2)
                "au", "ou" -> return Pair(baseConsonant + "ൗ", startIndex + 2)
            }
        }

        // Check 1-char vowel signs
        return when (text[startIndex]) {
            'a' -> Pair(baseConsonant, startIndex + 1)
            'i' -> Pair(baseConsonant + "ി", startIndex + 1)
            'u' -> Pair(baseConsonant + "ു", startIndex + 1)
            'e' -> Pair(baseConsonant + "െ", startIndex + 1)
            'o' -> Pair(baseConsonant + "ൊ", startIndex + 1)
            else -> Pair(baseConsonant + "്", startIndex)
        }
    }
}
