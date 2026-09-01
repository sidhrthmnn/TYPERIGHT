package com.aistudio.typeright.di

import com.aistudio.typeright.data.repository.PredictionRepositoryImpl
import com.aistudio.typeright.data.repository.CorrectionRepositoryImpl
import com.aistudio.typeright.data.repository.DictionaryRepositoryImpl
import com.aistudio.typeright.data.repository.ClipboardRepositoryImpl
import com.aistudio.typeright.data.repository.PolishingRepositoryImpl
import com.aistudio.typeright.data.repository.VoiceRepositoryImpl
import com.aistudio.typeright.data.repository.ThemeRepositoryImpl
import com.aistudio.typeright.domain.repository.PredictionRepository
import com.aistudio.typeright.domain.repository.CorrectionRepository
import com.aistudio.typeright.domain.repository.DictionaryRepository
import com.aistudio.typeright.domain.repository.ClipboardRepository
import com.aistudio.typeright.domain.repository.PolishingRepository
import com.aistudio.typeright.domain.repository.VoiceRepository
import com.aistudio.typeright.domain.repository.ThemeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing repository dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Singleton
    @Binds
    abstract fun bindPredictionRepository(
        impl: PredictionRepositoryImpl
    ): PredictionRepository
    
    @Singleton
    @Binds
    abstract fun bindCorrectionRepository(
        impl: CorrectionRepositoryImpl
    ): CorrectionRepository
    
    @Singleton
    @Binds
    abstract fun bindDictionaryRepository(
        impl: DictionaryRepositoryImpl
    ): DictionaryRepository
    
    @Singleton
    @Binds
    abstract fun bindClipboardRepository(
        impl: ClipboardRepositoryImpl
    ): ClipboardRepository
    
    @Singleton
    @Binds
    abstract fun bindPolishingRepository(
        impl: PolishingRepositoryImpl
    ): PolishingRepository
    
    @Singleton
    @Binds
    abstract fun bindVoiceRepository(
        impl: VoiceRepositoryImpl
    ): VoiceRepository
    
    @Singleton
    @Binds
    abstract fun bindThemeRepository(
        impl: ThemeRepositoryImpl
    ): ThemeRepository
}
