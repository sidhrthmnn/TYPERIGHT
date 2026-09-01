package com.aistudio.typeright.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aistudio.typeright.data.local.entity.DictionaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for dictionary operations
 */
@Dao
interface DictionaryDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: DictionaryEntity)
    
    @Update
    suspend fun update(entity: DictionaryEntity)
    
    @Delete
    suspend fun delete(entity: DictionaryEntity)
    
    @Query("DELETE FROM dictionary WHERE word = :word")
    suspend fun deleteByWord(word: String)
    
    @Query("SELECT * FROM dictionary WHERE word = :word")
    suspend fun getWord(word: String): DictionaryEntity?
    
    @Query("SELECT * FROM dictionary WHERE word LIKE :prefix || '%' LIMIT :limit")
    suspend fun getPrefixMatches(prefix: String, limit: Int): List<DictionaryEntity>
    
    @Query("SELECT * FROM dictionary WHERE isCustom = 1 ORDER BY lastUsed DESC LIMIT :limit")
    suspend fun getCustomWords(limit: Int): List<DictionaryEntity>
    
    @Query("SELECT * FROM dictionary ORDER BY frequency DESC LIMIT :limit")
    suspend fun getFrequentWords(limit: Int): List<DictionaryEntity>
    
    @Query("SELECT * FROM dictionary ORDER BY lastUsed DESC LIMIT :limit")
    fun observeDictionary(limit: Int): Flow<List<DictionaryEntity>>
    
    @Query("SELECT COUNT(*) FROM dictionary")
    suspend fun getCount(): Int
}
