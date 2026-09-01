package com.aistudio.typeright.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.repository.ThemeConfig
import com.aistudio.typeright.domain.repository.PresetTheme
import com.aistudio.typeright.domain.repository.ThemeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * ViewModel for theme management
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val themeRepository: ThemeRepository
) : ViewModel() {
    
    private val _currentTheme = MutableStateFlow(ThemeConfig())
    val currentTheme: StateFlow<ThemeConfig> = _currentTheme.asStateFlow()
    
    private val _presetThemes = MutableStateFlow<List<PresetTheme>>(emptyList())
    val presetThemes: StateFlow<List<PresetTheme>> = _presetThemes.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    init {
        loadTheme()
        loadPresets()
        observeTheme()
    }
    
    private fun loadTheme() {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = themeRepository.getTheme()
                if (result is Result.Success) {
                    _currentTheme.update { result.data }
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
    
    private fun loadPresets() {
        viewModelScope.launch {
            val result = themeRepository.getPresetThemes()
            if (result is Result.Success) {
                _presetThemes.update { result.data }
            }
        }
    }
    
    private fun observeTheme() {
        viewModelScope.launch {
            themeRepository.observeTheme().collect { theme ->
                _currentTheme.update { theme }
            }
        }
    }
    
    fun updateTheme(config: ThemeConfig) {
        viewModelScope.launch {
            val result = themeRepository.updateTheme(config)
            if (result is Result.Error) {
                Timber.e(result.exception, "Error updating theme")
            }
        }
    }
    
    fun applyPreset(themeId: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = themeRepository.applyPreset(themeId)
                if (result is Result.Error) {
                    Timber.e(result.exception, "Error applying preset")
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
}
