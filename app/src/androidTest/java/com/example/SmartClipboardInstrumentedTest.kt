package com.example

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputContentInfo
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class SmartClipboardInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun chipsPasteOnlyTheCodeAndCanBeDismissedIndependently() {
        val code = SmartClipSuggestion("otp", code = "001234", timestamp = System.currentTimeMillis())
        val image = SmartClipSuggestion("image", image = Uri.parse("content://test/screenshot"), timestamp = System.currentTimeMillis())
        var pasted: String? = null
        val items = mutableStateOf(listOf(code, image))
        compose.setContent {
            MaterialTheme { Box(Modifier.fillMaxWidth().height(48.dp)) {
                SmartClipboardChips(items.value, Color.Blue, Color.Black, { pasted = it.code },
                    { dismissed -> items.value = items.value.filterNot { it.id == dismissed.id } })
            } }
        }
        compose.onNodeWithTag("smart_clip_paste_otp").performClick()
        compose.runOnIdle { assertEquals("001234", pasted) }
        compose.onNodeWithTag("smart_clip_close_otp").performClick()
        compose.onNodeWithText("001234").assertDoesNotExist()
        compose.onNodeWithTag("smart_clip_paste_image").assertExists()
        compose.onNodeWithTag("smart_clip_close_image").performClick()
        compose.onNodeWithText("Screenshot").assertDoesNotExist()
    }

    @Test fun liveClipboardUpdatesAndDismissalSurvivesKeyboardReopening() {
        compose.setContent { MaterialTheme { Text("Clipboard test") } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = SmartClipboardController(context, scope) { true }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            compose.runOnIdle {
                clipboard.setPrimaryClip(ClipData.newPlainText("Code", "Your OTP is 001234"))
                controller.start()
            }
            compose.waitUntil(10_000) { controller.suggestions.value.any { it.code == "001234" } }
            compose.runOnIdle {
                controller.dismiss(controller.suggestions.value.first { it.code != null })
                controller.stop(); controller.start()
            }
            compose.waitForIdle()
            assertFalse(controller.suggestions.value.any { it.code == "001234" })
            compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("Code", "Your OTP is 654321")) }
            compose.waitUntil(10_000) { controller.suggestions.value.any { it.code == "654321" } }
            compose.runOnIdle {
                val extras = android.os.PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
                clipboard.setPrimaryClip(ClipData.newPlainText("Private", "Your OTP is 987654").apply { description.extras = extras })
            }
            compose.waitUntil(10_000) { controller.suggestions.value.none { it.code != null } }
        } finally { compose.runOnIdle { controller.stop() }; scope.cancel() }
    }

    @Test fun recentScreenshotIsDiscoveredDismissedAndPastedAsGrantedContent() = runBlocking {
        compose.setContent { MaterialTheme { Text("Screenshot test") } }
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.READ_MEDIA_IMAGES)
        val uri = screenshotFixture()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val controller = SmartClipboardController(context, scope) { true }
        try {
            compose.runOnIdle { controller.start() }
            compose.waitUntil(10_000) { controller.suggestions.value.any { it.image == uri } }
            val item = controller.suggestions.value.first { it.image == uri }
            val prepared = ScreenshotPaste.prepare(context, item)
            assertEquals("${context.packageName}.fileprovider", prepared.authority)
            val bytes = context.contentResolver.openInputStream(prepared)!!.use { it.readBytes() }
            assertTrue(bytes.size > 8)
            assertArrayEquals(byteArrayOf(-119, 80, 78, 71), bytes.take(4).toByteArray())
            var received: InputContentInfo? = null
            var receivedFlags = 0
            val connection = object : BaseInputConnection(View(context), false) {
                override fun commitContent(inputContentInfo: InputContentInfo, flags: Int, opts: Bundle?): Boolean {
                    received = inputContentInfo; receivedFlags = flags; return true
                }
            }
            val info = EditorInfo().apply { EditorInfoCompat.setContentMimeTypes(this, arrayOf("image/*")) }
            val content = InputContentInfoCompat(prepared, android.content.ClipDescription("Screenshot", arrayOf("image/png")), null)
            assertTrue(InputConnectionCompat.commitContent(connection, info, content, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null))
            assertEquals(prepared, received!!.contentUri)
            assertEquals(InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, receivedFlags)
            compose.runOnIdle { controller.dismiss(item); controller.stop(); controller.start() }
            assertFalse(controller.recentScreenshots(System.currentTimeMillis()).any { it.image == uri })
            assertFalse(controller.recentScreenshots(System.currentTimeMillis() + 301_000).any { it.image == uri })
        } finally {
            compose.runOnIdle { controller.stop() }; scope.cancel()
            context.contentResolver.delete(uri, null, null)
        }
    }

    private fun screenshotFixture(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Screenshot_TypeRight_QA_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Screenshots/")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        resolver.openOutputStream(uri)!!.use { out ->
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.BLUE)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return uri
    }
}
