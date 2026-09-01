package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.repository.ClipboardRepository
import com.aistudio.typeright.domain.repository.ClipboardItem
import com.aistudio.typeright.data.local.datasource.LocalClipboardDataSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import java.util.UUID

/**
 * Implementation of clipboard repository
 */
class ClipboardRepositoryImpl @Inject constructor(
    private val localDataSource: LocalClipboardDataSource
) : ClipboardRepository {
    
    override suspend fun getClipboardHistory(limit: Int): Result<List<ClipboardItem>> = try {
        val history = localDataSource.getHistory(limit)
        Result.Success(history)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun pinItem(item: ClipboardItem): Result<Unit> = try {
        localDataSource.pinItem(item.id)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun unpinItem(id: String): Result<Unit> = try {
        localDataSource.unpinItem(id)
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getPinnedItems(): Result<List<ClipboardItem>> = try {
        val items = localDataSource.getPinnedItems()
        Result.Success(items)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override fun observeClipboardHistory(): Flow<List<ClipboardItem>> {
        return localDataSource.observeHistory(50)
    }
    
    override suspend fun clearHistory(): Result<Unit> = try {
        localDataSource.clearHistory()
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
}
