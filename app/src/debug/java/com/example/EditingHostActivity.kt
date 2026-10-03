package com.example
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

class EditingHostActivity : ComponentActivity() {
    var native: EditText? = null
    var web: WebView? = null
    var webReady = false
    var composeText by mutableStateOf("")
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        when(intent.getStringExtra("editor")) {
            "compose" -> setContent {
                val focus = remember { FocusRequester() }
                BasicTextField(composeText, { composeText=it }, Modifier.focusRequester(focus), keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(autoCorrect=true))
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
            "web" -> { web=WebView(this).apply {
                settings.javaScriptEnabled=true
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        webReady=true
                        view.requestFocus()
                        view.evaluateJavascript("document.getElementById('input').focus()",null)
                    }
                }
                loadDataWithBaseURL("https://example.test/", "<textarea autofocus id='input' style='width:90%;height:200px;font-size:20px'></textarea>", "text/html", "UTF-8", null)
                requestFocus()
            }; setContentView(web) }
            else -> { native=EditText(this).apply { inputType=InputType.TYPE_CLASS_TEXT; requestFocus() }; setContentView(native) }
        }
    }
    fun inputView(): View? {
        native?.let { return it }; web?.let { return it }
        fun find(view: View): View? {
            if(view.onCheckIsTextEditor()) return view
            if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
            return null
        }
        return find(window.decorView)
    }
    fun focusWebEditor() {
        val view=web ?: return
        if(!webReady) return
        val x=80f*resources.displayMetrics.density; val y=70f*resources.displayMetrics.density
        val time=android.os.SystemClock.uptimeMillis()
        for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
            val event=android.view.MotionEvent.obtain(time,time,action,x,y,0)
            view.dispatchTouchEvent(event); event.recycle()
        }
    }
}
