package com.aistudio.typeright.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.typeright.domain.model.KeyboardState
import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.model.TextSuggestion
import com.aistudio.typeright.domain.usecase.GetPredictionsUseCase
import com.aistudio.typeright.domain.usecase.GetCorrectionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * ViewModel for keyboard state management
 */
@HiltViewModel
class KeyboardViewModel @Inject constructor(
    private val getPredictionsUseCase: GetPredictionsUseCase,
    private val getCorrectionsUseCase: GetCorrectionsUseCase
) : ViewModel() {
    
    private val _keyboardState = MutableStateFlow(KeyboardState())
    val keyboardState: StateFlow<KeyboardState> = _keyboardState.asStateFlow()
    
    private val _suggestions = MutableStateFlow<List<TextSuggestion>>(emptyList())
    val suggestions: StateFlow<List<TextSuggestion>> = _suggestions.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    fun updateText(text: String, cursorPosition: Int) {
        _keyboardState.update {
            it.copy(
                currentText = text,
                cursorPosition = cursorPosition
            )
        }
        
        // Fetch predictions
        fetchPredictions(text)
    }
    
    fun updateVisibility(isVisible: Boolean) {
        _keyboardState.update { it.copy(isVisible = isVisible) }
    }
    
    fun updateLanguage(language: String) {
        _keyboardState.update { it.copy(selectedLanguage = language) }
    }
    
    private fun fetchPredictions(text: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = getPredictionsUseCase(text)
                if (result is Result.Success) {
                    _suggestions.update { result.data }
                } else if (result is Result.Error) {
                    Timber.e(result.exception, "Error fetching predictions")
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
    
    fun getCorrections(word: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = getCorrectionsUseCase(word)
                if (result is Result.Success) {
                    _suggestions.update { result.data }
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
}
