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
                    repeat(60) {
                        if(!bound) scenario.onActivity { activity ->
                            val info=EditorInfo()
                            if(kind=="web") activity.focusWebEditor()
                            val connection=activity.inputView()?.onCreateInputConnection(info)
                            if(connection!=null) {
                                service.editorConnection=if(kind=="web") ThreadedEditorConnection(connection) else connection; service.editorInfo=info
                                service.onStartInput(info,false); service.lowercaseInput(); bound=true
                            }
                        }
                        if(!bound) SystemClock.sleep(50)
                    }
                    assertTrue("$kind input connection",bound)
                    DictionaryManager.getInstance(owner).correctionPipeline.clearCache()
                    instrumentation.runOnMainSync { "teh next buisness ".forEach { if(it==' ') service.onKeySpace() else service.onKeyText(it.toString()) } }
                    awaitText(service,"the next business ",kind)
                    instrumentation.runOnMainSync { service.onKeyDelete() }
                    awaitText(service,"the next buisness",kind)
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
            withContext(Dispatchers.Default) { repeat(60) { ranker.rank(listOf("finaly","libary","buisness","differnt","teh","recieve")[it%6]) } }
            val worker=withContext(Dispatchers.Default) { (0 until 240).map { i ->
                val word=listOf("finaly","libary","buisness","differnt","teh","recieve")[i%6]
                val start=System.nanoTime(); ranker.rank(word); (System.nanoTime()-start)/1e6
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
    private fun awaitText(service: EditingTestIme,expected: String,kind: String) {
        var actual=""
        repeat(80) {
            instrumentation.runOnMainSync { actual=service.editorConnection?.getTextBeforeCursor(1000,0)?.toString().orEmpty() }
            if(actual==expected) return
            SystemClock.sleep(25)
        }
        assertEquals(kind,expected,actual)
    }
}
