package com.aistudio.typeright.data.local.datasource

import com.aistudio.typeright.data.local.database.DictionaryDao
import com.aistudio.typeright.data.local.entity.DictionaryEntity
import com.aistudio.typeright.domain.model.DictionaryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Local data source for dictionary
 */
class LocalDictionaryDataSource @Inject constructor(
    private val dictionaryDao: DictionaryDao
) {
    suspend fun addWord(entry: DictionaryEntry) {
        dictionaryDao.insert(entry.toEntity())
    }
    
    suspend fun removeWord(word: String) {
        dictionaryDao.deleteByWord(word)
    }
    
    suspend fun getWord(word: String): DictionaryEntry? {
        return dictionaryDao.getWord(word)?.toDomain()
    }
    
    suspend fun getPrefixMatches(prefix: String, limit: Int): List<DictionaryEntry> {
        return dictionaryDao.getPrefixMatches(prefix, limit).map { it.toDomain() }
    }
    
    suspend fun getCustomWords(limit: Int): List<DictionaryEntry> {
        return dictionaryDao.getCustomWords(limit).map { it.toDomain() }
    }
    
    suspend fun getFrequentWords(limit: Int): List<DictionaryEntry> {
        return dictionaryDao.getFrequentWords(limit).map { it.toDomain() }
    }
    
    fun observeDictionary(limit: Int = 1000): Flow<List<DictionaryEntry>> {
        return dictionaryDao.observeDictionary(limit).map { list ->
            list.map { it.toDomain() }
        }
    }
    
    private fun DictionaryEntity.toDomain() = DictionaryEntry(
        word = word,
        frequency = frequency,
        language = language,
        isCustom = isCustom,
        lastUsed = lastUsed
    )
    
    private fun DictionaryEntry.toEntity() = DictionaryEntity(
        word = word,
        frequency = frequency,
        language = language,
        isCustom = isCustom,
        lastUsed = lastUsed
    )
}
