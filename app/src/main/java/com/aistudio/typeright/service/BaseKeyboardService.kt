package com.aistudio.typeright.service

import android.inputmethodservice.InputMethodService
import android.view.View
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Base IME service with lifecycle management
 */
@AndroidEntryPoint
abstract class BaseKeyboardService : InputMethodService() {
    
    protected abstract fun createKeyboardView(): View
    
    override fun onCreateInputView(): View? {
        return try {
            createKeyboardView()
        } catch (e: Exception) {
            Timber.e(e, "Error creating keyboard view")
            null
        }
    }
    
    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Timber.d("onStartInputView: restarting=$restarting")
    }
    
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        Timber.d("onFinishInputView: finishingInput=$finishingInput")
    }
    
    protected fun getInputConnection() = currentInputConnection
    
    protected fun getEditorInfo() = currentInputEditorInfo
}
