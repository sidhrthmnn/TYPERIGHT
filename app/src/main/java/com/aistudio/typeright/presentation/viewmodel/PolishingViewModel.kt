package com.aistudio.typeright.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.model.ToneStyle
import com.aistudio.typeright.domain.usecase.TransformToneUseCase
import com.aistudio.typeright.domain.usecase.CheckSpellingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

/**
 * ViewModel for text polishing and tone transformation
 */
@HiltViewModel
class PolishingViewModel @Inject constructor(
    private val transformToneUseCase: TransformToneUseCase,
    private val checkSpellingUseCase: CheckSpellingUseCase
) : ViewModel() {
    
    private val _toneResult = MutableStateFlow<String?>(null)
    val toneResult: StateFlow<String?> = _toneResult.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    fun transformTone(text: String, style: ToneStyle) {
        viewModelScope.launch {
            _isLoading.update { true }
            _error.update { null }
            try {
                val result = transformToneUseCase(text, style)
                when (result) {
                    is Result.Success -> _toneResult.update { result.data }
                    is Result.Error -> {
                        Timber.e(result.exception, "Error transforming tone")
                        _error.update { result.message ?: "Unknown error" }
                    }
                    is Result.Loading -> {}
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
    
    fun checkSpelling(text: String) {
        viewModelScope.launch {
            _isLoading.update { true }
            try {
                val result = checkSpellingUseCase(text)
                when (result) {
                    is Result.Success -> {
                        Timber.i("Spelling check complete. Errors: ${result.data.errors.size}")
                    }
                    is Result.Error -> {
                        Timber.e(result.exception, "Error checking spelling")
                        _error.update { result.message ?: "Spelling check failed" }
                    }
                    is Result.Loading -> {}
                }
            } finally {
                _isLoading.update { false }
            }
        }
    }
    
    fun clearResults() {
        _toneResult.update { null }
        _error.update { null }
    }
}
