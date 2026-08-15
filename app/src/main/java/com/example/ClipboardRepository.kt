package com.example

import kotlinx.coroutines.flow.Flow

class ClipboardRepository(private val clipboardDao: ClipboardDao) {
    val allItems: Flow<List<ClipboardItem>> = clipboardDao.getAllItems()

    suspend fun insert(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        
        val existing = clipboardDao.getItemByText(trimmed)
        if (existing != null) {
            // Update timestamp and insert to bring it to top
            clipboardDao.insertItem(existing.copy(timestamp = System.currentTimeMillis()))
        } else {
            clipboardDao.insertItem(ClipboardItem(text = trimmed))
        }
    }

    suspend fun togglePin(item: ClipboardItem) {
        clipboardDao.updatePinned(item.id, !item.isPinned)
    }

    suspend fun delete(item: ClipboardItem) {
        clipboardDao.deleteItemById(item.id)
    }

    suspend fun clearUnpinned() {
        clipboardDao.clearUnpinned()
    }
}
