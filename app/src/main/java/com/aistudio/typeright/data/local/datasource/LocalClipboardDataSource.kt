package com.aistudio.typeright.data.local.datasource

import com.aistudio.typeright.data.local.database.ClipboardDao
import com.aistudio.typeright.data.local.entity.ClipboardEntity
import com.aistudio.typeright.domain.repository.ClipboardItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Local data source for clipboard
 */
class LocalClipboardDataSource @Inject constructor(
    private val clipboardDao: ClipboardDao
) {
    suspend fun addItem(item: ClipboardItem) {
        clipboardDao.insert(item.toEntity())
    }
    
    suspend fun pinItem(id: String) {
        val items = clipboardDao.getHistory(1)
        items.find { it.id == id }?.let {
            clipboardDao.update(it.copy(isPinned = true))
        }
    }
    
    suspend fun unpinItem(id: String) {
        val items = clipboardDao.getHistory(1000)
        items.find { it.id == id }?.let {
            clipboardDao.update(it.copy(isPinned = false))
        }
    }
    
    suspend fun getHistory(limit: Int): List<ClipboardItem> {
        return clipboardDao.getHistory(limit).map { it.toDomain() }
    }
    
    suspend fun getPinnedItems(): List<ClipboardItem> {
        return clipboardDao.getPinnedItems().map { it.toDomain() }
    }
    
    fun observeHistory(limit: Int): Flow<List<ClipboardItem>> {
        return clipboardDao.observeHistory(limit).map { list ->
            list.map { it.toDomain() }
        }
    }
    
    suspend fun clearHistory() {
        clipboardDao.clearHistory()
    }
    
    private fun ClipboardEntity.toDomain() = ClipboardItem(
        id = id,
        text = text,
        timestamp = timestamp,
        isPinned = isPinned,
        category = category
    )
    
    private fun ClipboardItem.toEntity() = ClipboardEntity(
        id = id,
        text = text,
        timestamp = timestamp,
        isPinned = isPinned,
        category = category
    )
}
