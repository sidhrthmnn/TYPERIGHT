package com.example

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SmartClipboardTest {
    @Test fun copiedCodesPreserveLeadingZerosAndNeedUnambiguousContext() {
        assertEquals("001234", SmartClipboardPolicy.otp("001234"))
        assertEquals("123456", SmartClipboardPolicy.otp("Your verification code is 123456. Do not share it."))
        assertEquals("1234", SmartClipboardPolicy.otp("1234 is your login code"))
        assertNull(SmartClipboardPolicy.otp("Call me on 07700123456"))
        assertNull(SmartClipboardPolicy.otp("Order number 123456"))
        assertNull(SmartClipboardPolicy.otp("Code 123456 or 654321"))
        assertNull(SmartClipboardPolicy.otp("Date 2026-10-03"))
        assertNull(SmartClipboardPolicy.otp("Price 1234.56"))
    }
    @Test fun staleAndFutureSuggestionsExpire() {
        assertTrue(SmartClipboardPolicy.recent(1000, 121000, SmartClipboardPolicy.OTP_LIFETIME))
        assertFalse(SmartClipboardPolicy.recent(1000, 121001, SmartClipboardPolicy.OTP_LIFETIME))
        assertFalse(SmartClipboardPolicy.recent(5000, 1000, SmartClipboardPolicy.OTP_LIFETIME))
        assertFalse(SmartClipboardPolicy.recent(0, 1000, SmartClipboardPolicy.SCREENSHOT_LIFETIME))
    }
    @Test fun onlyScreenshotsAreSelectedAndNewCopiesHaveNewIdentities() {
        assertTrue(SmartClipboardPolicy.screenshot("Screenshot_20261003.png", "Pictures/"))
        assertTrue(SmartClipboardPolicy.screenshot("image.png", "Pictures/Screenshots/"))
        assertFalse(SmartClipboardPolicy.screenshot("holiday.jpg", "Pictures/Camera/"))
        assertNotEquals(SmartClipboardPolicy.identity("123456", 1000), SmartClipboardPolicy.identity("123456", 2000))
        assertFalse(SmartClipboardPolicy.identity("123456", 1000).contains("123456"))
    }
    @Test fun otpIsNeverPersistedAsClipboardHistory() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ClipboardRepository(AppDatabase.getDatabase(context).clipboardDao())
        repository.clearUnpinned()
        repository.insert("Your OTP is 001234")
        assertTrue(repository.allItems.first().isEmpty())
        repository.insert("A normal copied note")
        assertEquals("A normal copied note", repository.allItems.first().single().text)
    }
    @Test fun screenshotPasteRequiresAnAdvertisedCompatibleMimeType() {
        val editor = EditorInfo()
        assertFalse(ScreenshotPaste.supported(editor, "image/png"))
        EditorInfoCompat.setContentMimeTypes(editor, arrayOf("image/*"))
        assertTrue(ScreenshotPaste.supported(editor, "image/png"))
        EditorInfoCompat.setContentMimeTypes(editor, arrayOf("image/jpeg"))
        assertFalse(ScreenshotPaste.supported(editor, "image/png"))
    }
}
