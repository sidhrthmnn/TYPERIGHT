package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.model.TextSuggestion
import com.aistudio.typeright.domain.model.SuggestionType
import com.aistudio.typeright.domain.repository.PredictionRepository
import com.aistudio.typeright.data.local.datasource.LocalDictionaryDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.math.min

/**
 * Implementation of prediction repository with on-device ML model
 */
class PredictionRepositoryImpl @Inject constructor(
    private val dictionaryDataSource: LocalDictionaryDataSource
) : PredictionRepository {
    
    override suspend fun getNextWordPredictions(
        text: String,
        limit: Int
    ): Result<List<TextSuggestion>> = try {
        val words = text.trim().split("\\s+".toRegex())
        if (words.isEmpty()) {
            return Result.Success(emptyList())
        }
        
        val lastWord = words.last()
        val suggestions = dictionaryDataSource.getFrequentWords(limit * 2)
            .filter { it.word.startsWith(lastWord, ignoreCase = true) }
            .sortedByDescending { it.frequency }
            .take(limit)
            .mapIndexed { index, entry ->
                val confidence = 1f - (index / (limit + 1f))
                TextSuggestion(
                    text = entry.word,
                    confidence = confidence,
                    type = SuggestionType.PREDICTION
                )
            }
        
        Result.Success(suggestions)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getCompletionSuggestions(
        word: String,
        limit: Int
    ): Result<List<TextSuggestion>> = try {
        val matches = dictionaryDataSource.getPrefixMatches(word, limit)
            .sortedByDescending { it.frequency }
            .map { entry ->
                TextSuggestion(
                    text = entry.word,
                    confidence = calculateConfidence(word, entry.word),
                    type = SuggestionType.COMPLETION
                )
            }
        
        Result.Success(matches)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override fun streamPredictions(text: String): Flow<List<TextSuggestion>> = flow {
        try {
            val result = getNextWordPredictions(text)
            if (result is Result.Success) {
                emit(result.data)
            }
        } catch (e: Exception) {
            emit(emptyList())
        }
    }
    
    private fun calculateConfidence(input: String, match: String): Float {
        return min(1f, (input.length.toFloat() / match.length))
    }
}
