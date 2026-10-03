package com.example
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.ExtractedTextRequest
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditingInputConnectionDeviceTest {
    @get:Rule val services=ServiceTestRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun nativeComposeAndWebEditorsPreserveRapidTypingAndUndo() {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        val settings=KeyboardSettings(owner)
        val oldLearning=settings.personalizedLearningEnabled
        val oldHaptic=settings.hapticEnabled; val oldSound=settings.soundEnabled
        settings.personalizedLearningEnabled=false; settings.hapticEnabled=false; settings.soundEnabled=false
        try {
            services.bindService(Intent(owner,EditingTestIme::class.java))
            val service=EditingTestIme.active!!
            for(kind in listOf("native","compose","web")) {
                ActivityScenario.launch<EditingHostActivity>(Intent(owner,EditingHostActivity::class.java).putExtra("editor",kind)).use { scenario ->
                    var bound=false
                    var candidate: InputConnection?=null
                    var candidateInfo: EditorInfo?=null
                    repeat(60) {
                        if(!bound) scenario.onActivity { activity ->
                            if(candidate==null) {
                                if(kind=="web") {
                                    if(!activity.webReady) return@onActivity
                                    activity.focusWebEditor()
                                }
                                val info=EditorInfo()
                                val connection=activity.inputView()?.onCreateInputConnection(info)
                                // Chromium can return a connection before its DOM field is focused.
                                if(connection!=null && (kind!="web" || info.inputType and android.text.InputType.TYPE_MASK_CLASS == android.text.InputType.TYPE_CLASS_TEXT && info.initialSelEnd>=0)) {
                                    candidate=if(kind=="web") ThreadedEditorConnection(connection) else connection
                                    candidateInfo=info
                                }
                            }
                            val connection=candidate
                            if(connection!=null && (connection !is ThreadedEditorConnection || connection.hasSnapshot)) {
                                service.editorConnection=connection; service.editorInfo=candidateInfo!!
                                service.onStartInput(candidateInfo,false); service.lowercaseInput(); bound=true
                            }
                        }
                        if(!bound) SystemClock.sleep(50)
                    }
                    assertTrue("$kind input connection",bound)
                    DictionaryManager.getInstance(owner).correctionPipeline.clearCache()
                    instrumentation.runOnMainSync { "teh next buisness ".forEach { if(it==' ') service.onKeySpace() else service.onKeyText(it.toString()) } }
                    // The first editor deliberately types before startup asset loading.
                    // Warm editor checks retain the short publication deadline.
                    awaitText(service,"the next business ",kind,if(kind=="native") 30000 else 2000)
                    instrumentation.runOnMainSync { service.onKeyDelete() }
                    awaitText(service,"the next buisness",kind)
                    instrumentation.runOnMainSync {
                        service.editorConnection!!.finishComposingText()
                        service.editorConnection!!.setSelection(0,"the next buisness".length)
                        service.editorConnection!!.commitText("",1)
                    }
                    awaitText(service,"",kind)
                    instrumentation.runOnMainSync {
                        service.onStartInput(service.editorInfo,false); service.lowercaseInput()
                        DictionaryManager.getInstance(owner).correctionPipeline.clearCache()
                        "njan tomorow officeil varillla ".forEach { if(it==' ') service.onKeySpace() else service.onKeyText(it.toString()) }
                    }
                    awaitText(service,"njan tomorrow officeil varilla ",kind)
                    instrumentation.runOnMainSync { service.onKeyDelete() }
                    awaitText(service,"njan tomorrow officeil varillla",kind)
                    instrumentation.runOnMainSync { service.onFinishInput() }
                    service.editorConnection=null
                }
            }
        } finally { settings.personalizedLearningEnabled=oldLearning; settings.hapticEnabled=oldHaptic; settings.soundEnabled=oldSound }
    }
    @Test fun measureActualImeHandlersAndWorkerRanking() = runBlocking {
        val owner=ApplicationProvider.getApplicationContext<Context>()
        val settings=KeyboardSettings(owner); val old=settings.personalizedLearningEnabled
        val oldHaptic=settings.hapticEnabled; val oldSound=settings.soundEnabled
        settings.personalizedLearningEnabled=false; settings.hapticEnabled=false; settings.soundEnabled=false
        try {
            services.bindService(Intent(owner,EditingTestIme::class.java))
            val service=EditingTestIme.active!!
            val ranker=DictionaryManager.getInstance(owner).correctionPipeline
            withContext(Dispatchers.Default) { ranker.awaitDictionaries() }
            // JIT/geometry warmup is separate from measured worker samples; editing tests use cold caches.
            val words=listOf("finaly","libary","buisness","differnt","teh","recieve","varillla","njan","officeil")
            withContext(Dispatchers.Default) { repeat(90) { ranker.rank(words[it%words.size],listOf("njan","innu")) } }
            val worker=withContext(Dispatchers.Default) { (0 until 240).map { i ->
                val word=words[i%words.size]
                val start=System.nanoTime(); ranker.rank(word,listOf("njan","innu")); (System.nanoTime()-start)/1e6
            }.sorted() }
            val cached=mutableListOf<Double>()
            val endToEnd=mutableListOf<Double>()
            ActivityScenario.launch<EditingHostActivity>(Intent(owner,EditingHostActivity::class.java)).use { scenario ->
                scenario.onActivity { activity ->
                    val info=EditorInfo(); val editor=TimedConnection(activity.native!!.onCreateInputConnection(info)!!)
                    service.editorConnection=editor; service.editorInfo=info
                    service.onStartInput(info,false); service.lowercaseInput()
                    repeat(150) {
                        editor.nanos=0
                        val start=System.nanoTime(); service.onKeyText("x"); val elapsed=System.nanoTime()-start
                        endToEnd.add(elapsed/1e6); cached.add((elapsed-editor.nanos)/1e6)
                        if (it%8==7) service.onKeySpace()
                    }
                    service.onFinishInput(); service.editorConnection=null
                }
            }
            val p95=worker[(worker.size*.95).toInt()]; val main95=cached.sorted()[(cached.size*.95).toInt()]
            val editor95=endToEnd.sorted()[(endToEnd.size*.95).toInt()]
            android.util.Log.i("TypingImeMetrics","workerP95=$p95 imeExclusiveP95=$main95 endToEndP95=$editor95; 150 real callbacks")
            java.io.File(owner.filesDir,"typing-ime-metrics.json").writeText("{\"workerP95Ms\":$p95,\"imeExclusiveHandlerP95Ms\":$main95,\"nativeEditorEndToEndP95Ms\":$editor95}")
            assertTrue("Worker p95 $p95 ms",p95<=15)
            assertTrue("IME handling p95 $main95 ms",main95<2)
        } finally { settings.personalizedLearningEnabled=old; settings.hapticEnabled=oldHaptic; settings.soundEnabled=oldSound }
    }
    /** Actual native editor executes inline here; Android normally runs this work across Binder.
     * Subtract only delegated InputConnection work, never ranking/state updates/notifications. */
    private class TimedConnection(target: InputConnection): InputConnectionWrapper(target,false) {
        var nanos=0L
        private inline fun <T> timed(block: () -> T): T { val start=System.nanoTime(); try { return block() } finally { nanos+=System.nanoTime()-start } }
        override fun setComposingText(text: CharSequence?, position: Int)=timed { super.setComposingText(text,position) }
        override fun finishComposingText()=timed { super.finishComposingText() }
        override fun getTextBeforeCursor(length: Int,flags: Int)=timed { super.getTextBeforeCursor(length,flags) }
        override fun getTextAfterCursor(length: Int,flags: Int)=timed { super.getTextAfterCursor(length,flags) }
        override fun getSelectedText(flags: Int)=timed { super.getSelectedText(flags) }
        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int)=timed { super.getExtractedText(request,flags) }
        override fun commitText(text: CharSequence?,position: Int)=timed { super.commitText(text,position) }
        override fun deleteSurroundingText(before: Int,after: Int)=timed { super.deleteSurroundingText(before,after) }
        override fun beginBatchEdit()=timed { super.beginBatchEdit() }
        override fun endBatchEdit()=timed { super.endBatchEdit() }
    }
    private fun awaitText(service: EditingTestIme,expected: String,kind: String,timeoutMs: Long = 2000) {
        var actual=""
        var following=""; var selected=""
        val deadline=SystemClock.uptimeMillis()+timeoutMs
        while(SystemClock.uptimeMillis()<deadline) {
            instrumentation.runOnMainSync {
                val connection=service.editorConnection!!
                actual=connection.getTextBeforeCursor(1000,0)?.toString().orEmpty()
                following=connection.getTextAfterCursor(1000,0)?.toString().orEmpty()
                selected=connection.getSelectedText(0)?.toString().orEmpty()
            }
            // WebView can publish cursor zero before its queued select-all/delete
            // is complete. An empty prefix alone does not prove an empty editor.
            if(actual==expected && following.isEmpty() && selected.isEmpty()) return
            SystemClock.sleep(25)
        }
        assertEquals(kind,expected,actual)
        assertEquals("$kind following text","",following)
        assertEquals("$kind selected text","",selected)
    }
}
