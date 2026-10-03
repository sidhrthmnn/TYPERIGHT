package com.example

/** Conservative validation: small word edits only, preserving protected tokens and separators. */
internal object MinimalContextEdit {
    private val token = Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*")
    private val negation = setOf("not", "no", "never", "dont", "don't", "cannot", "can't", "wont", "won't", "nahi", "illa", "varilla")
    fun isAllowed(original: String, replacement: String, dictionary: DictionaryManager): Boolean {
        if (original == replacement || !AiOutputValidator.isValid(original, replacement, PolishMode.PROOFREAD)) return false
        val before = token.findAll(original).toList(); val after = token.findAll(replacement).toList()
        if (before.size != after.size || before.isEmpty()) return false
        // Preserve all whitespace, punctuation and non-word data; allow one final sentence mark.
        fun separators(text: String) = token.replace(text, "#").trimEnd('.', '!', '?')
        if (separators(original) != separators(replacement)) return false
        var edits = 0
        for (i in before.indices) {
            val a = before[i].value; val b = after[i].value
            if (a == b) continue
            val lower = MultilingualLexicon.normalize(a)
            if (lower in negation || MultilingualLexicon.normalize(b) in negation ||
                lower in MultilingualLexicon.slang || RomanizedMalayalamLexicon.preservesLiteral(lower,before.take(i).map { it.value },before.drop(i+1).take(2).map { it.value }) || lower in MultilingualLexicon.romanizedMalayalam || lower in MultilingualLexicon.romanizedHindi ||
                dictionary.correctionPipeline.isProtectedPersonalWord(a) || (a.first().isUpperCase() && lower != MultilingualLexicon.normalize(b) && (i > 0 || !dictionary.gboardEngine.isKnownTypo(lower))) ||
                dictionary.isCodeOrSpecialToken(a) || CandidateRanker.editDistance(lower, MultilingualLexicon.normalize(b)) > 2f) return false
            val lexicon = MultilingualLexicon.get(dictionary.appContext)
            if (!dictionary.isWordInDictionary(lower) && lexicon.languages(lower).isEmpty()) return false
            if (!dictionary.isWordInDictionary(b) && lexicon.languages(b).isEmpty()) return false
            if (Character.UnicodeScript.of(a.first().code) != Character.UnicodeScript.of(b.first().code)) return false
            if (++edits > maxOf(1, before.size / 8)) return false
        }
        return edits > 0
    }
}
