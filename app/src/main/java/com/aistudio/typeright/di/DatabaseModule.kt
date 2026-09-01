package com.aistudio.typeright.di

import android.content.Context
import androidx.room.Room
import com.aistudio.typeright.data.local.database.TypeRightDatabase
import com.aistudio.typeright.data.local.database.DictionaryDao
import com.aistudio.typeright.data.local.database.ClipboardDao
import com.aistudio.typeright.data.local.database.HistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Singleton
    @Provides
    fun provideTypeRightDatabase(
        @ApplicationContext context: Context
    ): TypeRightDatabase {
        return Room.databaseBuilder(
            context,
            TypeRightDatabase::class.java,
            "typeright.db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }
    
    @Singleton
    @Provides
    fun provideDictionaryDao(database: TypeRightDatabase): DictionaryDao {
        return database.dictionaryDao()
    }
    
    @Singleton
    @Provides
    fun provideClipboardDao(database: TypeRightDatabase): ClipboardDao {
        return database.clipboardDao()
    }
    
    @Singleton
    @Provides
    fun provideHistoryDao(database: TypeRightDatabase): HistoryDao {
        return database.historyDao()
    }
}
