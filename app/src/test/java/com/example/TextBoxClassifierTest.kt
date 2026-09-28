package com.example

import android.text.InputType
import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TextBoxClassifierTest {

    @Test
    fun testGoogleSearchInChrome() {
        val info = EditorInfo().apply {
            packageName = "com.android.chrome"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            hintText = "Search Google or type a URL"
            fieldName = "q"
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.SEARCH, classification.category)
        assertTrue("Google Search must allow predictions", classification.allowsPredictions)
        assertFalse("Google Search must not be sensitive", classification.isSensitive)
        assertTrue(classification.isSearch)
        assertTrue(classification.isWeb)
        assertFalse(classification.isUrl)
    }

    @Test
    fun testChatBoxInWebPage() {
        val info = EditorInfo().apply {
            packageName = "com.android.chrome"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEND
            hintText = "Type a message"
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.CHAT, classification.category)
        assertTrue("Web chat must allow predictions", classification.allowsPredictions)
        assertFalse("Web chat must not be sensitive", classification.isSensitive)
        assertTrue(classification.isChat)
        assertTrue(classification.isWeb)
    }

    @Test
    fun testGenericWebPageTextAreaWithNoSuggestionsFlag() {
        val info = EditorInfo().apply {
            packageName = "com.android.chrome"
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.WEB_PAGE, classification.category)
        assertTrue("Web page textarea must allow predictions despite NO_SUGGESTIONS flag", classification.allowsPredictions)
        assertFalse("Web page textarea must not be sensitive", classification.isSensitive)
        assertTrue(classification.isWeb)
    }

    @Test
    fun testPasswordProtection() {
        val info = EditorInfo().apply {
            packageName = "com.android.chrome"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.PASSWORD, classification.category)
        assertFalse("Passwords must never allow predictions", classification.allowsPredictions)
        assertTrue("Passwords must be sensitive", classification.isSensitive)
        assertFalse("Passwords must never allow autocorrect", classification.allowsAutocorrect)
        assertFalse("Passwords must never allow AI polish", classification.allowsAiPolish)
    }

    @Test
    fun testChromeAddressBarOmnibox() {
        val info = EditorInfo().apply {
            packageName = "com.android.chrome"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            fieldName = "url_bar"
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.URL_BAR, classification.category)
        assertTrue("URL bar allows domain suggestions", classification.allowsPredictions)
        assertFalse("URL bar must not autocorrect words", classification.allowsAutocorrect)
        assertTrue(classification.isUrl)
    }

    @Test
    fun testEmailField() {
        val info = EditorInfo().apply {
            packageName = "com.google.android.gm"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.EMAIL, classification.category)
        assertTrue("Email fields allow completions", classification.allowsPredictions)
        assertFalse("Email fields must not autocorrect usernames", classification.allowsAutocorrect)
        assertTrue(classification.isEmail)
    }

    @Test
    fun testNativeChatApp() {
        val info = EditorInfo().apply {
            packageName = "com.whatsapp"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE
            imeOptions = EditorInfo.IME_ACTION_SEND
        }

        val classification = TextBoxClassifier.classify(info)
        assertEquals(TextBoxCategory.CHAT, classification.category)
        assertTrue(classification.allowsPredictions)
        assertTrue(classification.allowsAutocorrect)
        assertTrue(classification.allowsAiPolish)
    }
}
