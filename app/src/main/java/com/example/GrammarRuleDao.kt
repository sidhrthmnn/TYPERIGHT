package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GrammarRuleDao {
    @Query("SELECT * FROM grammar_rules ORDER BY priority DESC, id ASC")
    fun getAllRules(): Flow<List<GrammarRuleEntity>>

    @Query("SELECT * FROM grammar_rules WHERE isEnabled = 1 ORDER BY priority DESC, id ASC")
    suspend fun getActiveRulesSync(): List<GrammarRuleEntity>

    @Query("SELECT COUNT(*) FROM grammar_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: GrammarRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<GrammarRuleEntity>)

    @Update
    suspend fun updateRule(rule: GrammarRuleEntity)

    @Query("DELETE FROM grammar_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM grammar_rules WHERE isUserCustom = 0")
    suspend fun clearDefaultRules()
}
