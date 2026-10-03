package com.example

import android.os.Handler
import android.view.inputmethod.*
import java.util.concurrent.atomic.AtomicBoolean

/** Debug harness bridge: Chromium owns a dedicated InputConnection handler, as in Binder dispatch. */
class ThreadedEditorConnection(private val target: InputConnection) : InputConnectionWrapper(target,false) {
    private val handler: Handler = requireNotNull(target.handler)
    private data class State(val before: String="",val after: String="",val selected: String?=null,val extracted: ExtractedText?=null)
    @Volatile private var state=State()
    private val refreshPending=AtomicBoolean()
    init { refresh() }
    private fun refresh() {
        if(!refreshPending.compareAndSet(false,true)) return
        handler.postDelayed({
            try { state=State(target.getTextBeforeCursor(20000,0)?.toString().orEmpty(),target.getTextAfterCursor(20000,0)?.toString().orEmpty(),target.getSelectedText(0)?.toString(),target.getExtractedText(ExtractedTextRequest(),0)) }
            finally { refreshPending.set(false) }
        },16)
    }
    private fun edit(action: () -> Unit): Boolean { handler.post { action() }; refresh(); return true }
    override fun getTextBeforeCursor(length: Int,flags: Int): CharSequence { refresh(); return state.before.takeLast(length) }
    override fun getTextAfterCursor(length: Int,flags: Int): CharSequence { refresh(); return state.after.take(length) }
    override fun getSelectedText(flags: Int): CharSequence? { refresh(); return state.selected }
    override fun getExtractedText(request: ExtractedTextRequest?,flags: Int): ExtractedText? { refresh(); return state.extracted }
    override fun setComposingText(text: CharSequence?,position: Int)=edit { target.setComposingText(text,position) }
    override fun finishComposingText()=edit { target.finishComposingText() }
    override fun setComposingRegion(start: Int,end: Int)=edit { target.setComposingRegion(start,end) }
    override fun commitText(text: CharSequence?,position: Int)=edit { target.commitText(text,position) }
    override fun deleteSurroundingText(before: Int,after: Int)=edit { target.deleteSurroundingText(before,after) }
    override fun beginBatchEdit()=edit { target.beginBatchEdit() }
    override fun endBatchEdit()=edit { target.endBatchEdit() }
}
