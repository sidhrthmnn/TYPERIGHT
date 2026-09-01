package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.DictionaryEntry
import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.repository.DictionaryRepository
import com.aistudio.typeright.data.local.datasource.LocalDictionaryDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of dictionary repository
 */
class DictionaryRepositoryImpl @Inject constructor(
    private val localDataSource: LocalDictionaryDataSource
) : DictionaryRepository {
    
    override suspend fun addWord(entry: DictionaryEntry): Result<Unit> = try {
        localDataSource.addWord(entry)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun removeWord(word: String): Result<Unit> = try {
        localDataSource.removeWord(word)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun wordExists(word: String): Result<Boolean> = try {
        val exists = localDataSource.getWord(word) != null
        Result.Success(exists)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getCustomDictionary(): Result<List<DictionaryEntry>> = try {
        val words = localDataSource.getCustomWords(1000)
        Result.Success(words)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override fun observeDictionary(): Flow<List<DictionaryEntry>> {
        return localDataSource.observeDictionary()
    }
    
    override suspend fun updateTrendingWords(): Result<Unit> = try {
        // In production, fetch from API
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getFrequentWords(limit: Int): Result<List<DictionaryEntry>> = try {
        val words = localDataSource.getFrequentWords(limit)
        Result.Success(words)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
}
