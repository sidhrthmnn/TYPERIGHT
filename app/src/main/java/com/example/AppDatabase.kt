package com.example

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ClipboardItem::class, 
        LearnedWord::class, 
        LearnedSwipePattern::class, 
        LearnedTouchOffset::class, 
        LearnedBigram::class,
        LearnedTrigram::class,
        GrammarRuleEntity::class
    ], 
    version = 5, 
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
    abstract fun learnedWordDao(): LearnedWordDao
    abstract fun patternLearningDao(): PatternLearningDao
    abstract fun grammarRuleDao(): GrammarRuleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "typeright_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
