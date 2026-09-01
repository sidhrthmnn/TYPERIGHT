package com.aistudio.typeright.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aistudio.typeright.data.local.entity.DictionaryEntity
import com.aistudio.typeright.data.local.entity.ClipboardEntity
import com.aistudio.typeright.data.local.entity.HistoryEntity

/**
 * Room database for TypeRight keyboard
 */
@Database(
    entities = [
        DictionaryEntity::class,
        ClipboardEntity::class,
        HistoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class TypeRightDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun historyDao(): HistoryDao
}
