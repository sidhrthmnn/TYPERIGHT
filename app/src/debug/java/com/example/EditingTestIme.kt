package com.example
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/** Debug-only harness runs real IME handlers against Android editors' own InputConnections. */
class EditingTestIme : TypeRightKeyboardService() {
    var editorConnection: InputConnection? = null
    var editorInfo = EditorInfo()
    fun lowercaseInput() { isShiftActive.value=false }
    override fun getCurrentInputConnection() = editorConnection
    override fun getCurrentInputEditorInfo() = editorInfo
    override fun onCreate() { super.onCreate(); active = this }
    override fun onDestroy() { active = null; super.onDestroy() }
    companion object { @Volatile var active: EditingTestIme? = null }
}
