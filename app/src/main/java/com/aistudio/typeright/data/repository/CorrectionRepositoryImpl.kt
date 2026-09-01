package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.model.TextSuggestion
import com.aistudio.typeright.domain.model.SuggestionType
import com.aistudio.typeright.domain.repository.CorrectionRepository
import com.aistudio.typeright.domain.repository.SpellingCheckResult
import com.aistudio.typeright.domain.repository.SpellingError
import com.aistudio.typeright.data.local.datasource.LocalDictionaryDataSource
import javax.inject.Inject

/**
 * Implementation of correction repository with Levenshtein distance algorithm
 */
class CorrectionRepositoryImpl @Inject constructor(
    private val dictionaryDataSource: LocalDictionaryDataSource
) : CorrectionRepository {
    
    override suspend fun getAutoCorrections(
        word: String,
        limit: Int
    ): Result<List<TextSuggestion>> = try {
        val allWords = dictionaryDataSource.getFrequentWords(500)
        
        val corrections = allWords
            .map { entry ->
                val distance = levenshteinDistance(word.lowercase(), entry.word.lowercase())
                Pair(entry, distance)
            }
            .filter { (_, distance) -> distance in 1..3 } // 1-3 character distance
            .sortedBy { (_, distance) -> distance }
            .take(limit)
            .map { (entry, distance) ->
                val confidence = 1f - (distance / 4f)
                TextSuggestion(
                    text = entry.word,
                    confidence = confidence.coerceIn(0f, 1f),
                    type = SuggestionType.CORRECTION
                )
            }
        
        Result.Success(corrections)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun checkSpelling(text: String): Result<SpellingCheckResult> = try {
        val words = text.split("\\s+".toRegex())
        val errors = mutableListOf<SpellingError>()
        
        words.forEachIndexed { index, word ->
            val cleanWord = word.replace("[^a-zA-Z]".toRegex(), "")
            if (cleanWord.isNotEmpty()) {
                val exists = dictionaryDataSource.getWord(cleanWord) != null
                if (!exists && !isCommonMisspelling(cleanWord)) {
                    val suggestions = when (val result = getAutoCorrections(cleanWord, 3)) {
                        is Result.Success -> result.data.map { it.text }
                        else -> emptyList()
                    }
                    
                    val startIndex = text.indexOf(word)
                    errors.add(
                        SpellingError(
                            word = word,
                            startIndex = startIndex,
                            endIndex = startIndex + word.length,
                            suggestions = suggestions
                        )
                    )
                }
            }
        }
        
        Result.Success(SpellingCheckResult(text, errors))
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun detectAndFixTypos(text: String): Result<String> = try {
        var result = text
        val checkResult = when (val check = checkSpelling(text)) {
            is Result.Success -> check.data
            else -> return Result.Error(Exception("Spelling check failed"))
        }
        
        checkResult.errors.forEach { error ->
            if (error.suggestions.isNotEmpty()) {
                result = result.replaceFirst(error.word, error.suggestions[0])
            }
        }
        
        Result.Success(result)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    /**
     * Calculate Levenshtein distance between two strings
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
    
    private fun isCommonMisspelling(word: String): Boolean {
        return word.length < 2 || word.matches("[0-9]{1,}".toRegex())
    }
}
