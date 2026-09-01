package com.aistudio.typeright.data.repository

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.repository.ThemeRepository
import com.aistudio.typeright.domain.repository.ThemeConfig
import com.aistudio.typeright.domain.repository.PresetTheme
import com.aistudio.typeright.domain.repository.KeyboardHeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Implementation of theme repository
 */
class ThemeRepositoryImpl @Inject constructor() : ThemeRepository {
    
    private val _themeState = MutableStateFlow(ThemeConfig())
    
    override suspend fun getTheme(): Result<ThemeConfig> {
        return Result.Success(_themeState.value)
    }
    
    override suspend fun updateTheme(config: ThemeConfig): Result<Unit> = try {
        _themeState.value = config
        Result.Success(Unit)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override suspend fun getPresetThemes(): Result<List<PresetTheme>> {
        val presets = listOf(
            PresetTheme(
                id = "light",
                name = "Clean Light",
                config = ThemeConfig(isDarkMode = false)
            ),
            PresetTheme(
                id = "dark",
                name = "Midnight Dark",
                config = ThemeConfig(isDarkMode = true, primaryColor = 0xFF1F1F1F)
            ),
            PresetTheme(
                id = "amoled",
                name = "AMOLED Black",
                config = ThemeConfig(isDarkMode = true, primaryColor = 0xFF000000)
            ),
            PresetTheme(
                id = "forest",
                name = "Forest Green",
                config = ThemeConfig(isDarkMode = true, primaryColor = 0xFF1B5E20, accentColor = 0xFF81C784)
            ),
            PresetTheme(
                id = "ocean",
                name = "Ocean Blue",
                config = ThemeConfig(isDarkMode = true, primaryColor = 0xFF0D47A1, accentColor = 0xFF64B5F6)
            )
        )
        return Result.Success(presets)
    }
    
    override suspend fun applyPreset(themeId: String): Result<Unit> = try {
        val presets = when (val result = getPresetThemes()) {
            is Result.Success -> result.data
            else -> return Result.Error(Exception("Failed to load presets"))
        }
        
        val preset = presets.find { it.id == themeId }
            ?: return Result.Error(Exception("Theme not found"))
        
        return updateTheme(preset.config)
    } catch (e: Exception) {
        Result.Error(e, e.message)
    }
    
    override fun observeTheme(): Flow<ThemeConfig> {
        return _themeState.asStateFlow()
    }
}
