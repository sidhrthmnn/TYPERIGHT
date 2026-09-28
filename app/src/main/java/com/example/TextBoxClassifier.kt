package com.example

import android.text.InputType
import android.view.inputmethod.EditorInfo

/**
 * Categorization of the text input field based on its EditorInfo attributes,
 * package context, inputType class/variation/flags, and IME action.
 */
enum class TextBoxCategory(val title: String) {
    CHAT("Chat & Messaging"),
    SEARCH("Search"),
    WEB_PAGE("Web Page"),
    URL_BAR("Web Address"),
    EMAIL("Email"),
    PASSWORD("Password & Sensitive"),
    NUMBER_PHONE("Number & Phone"),
    GENERAL("Text")
}

data class TextBoxClassification(
    val category: TextBoxCategory,
    val isSensitive: Boolean,
    val allowsPredictions: Boolean,
    val allowsAutocorrect: Boolean,
    val allowsAiPolish: Boolean,
    val isWeb: Boolean,
    val isSearch: Boolean,
    val isChat: Boolean,
    val isUrl: Boolean,
    val isEmail: Boolean,
    val defaultEmptySuggestions: List<String>
)

object TextBoxClassifier {

    val defaultClassification = TextBoxClassification(
        category = TextBoxCategory.GENERAL,
        isSensitive = false,
        allowsPredictions = true,
        allowsAutocorrect = true,
        allowsAiPolish = true,
        isWeb = false,
        isSearch = false,
        isChat = false,
        isUrl = false,
        isEmail = false,
        defaultEmptySuggestions = listOf("I", "the", "to")
    )

    fun classify(info: EditorInfo?): TextBoxClassification {
        if (info == null) return defaultClassification

        val inputType = info.inputType
        val inputClass = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION

        val pkg = (info.packageName ?: "").lowercase()
        val fieldName = (info.fieldName ?: "").lowercase()
        val hintText = (info.hintText?.toString() ?: "").lowercase()
        val label = (info.label?.toString() ?: "").lowercase()

        // 1. Password and Sensitive Credential Protection (HIGHEST PRIORITY)
        val isPassword = (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        )) || (inputClass == InputType.TYPE_CLASS_NUMBER && (
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ))

        if (isPassword) {
            return TextBoxClassification(
                category = TextBoxCategory.PASSWORD,
                isSensitive = true,
                allowsPredictions = false,
                allowsAutocorrect = false,
                allowsAiPolish = false,
                isWeb = false,
                isSearch = false,
                isChat = false,
                isUrl = false,
                isEmail = false,
                defaultEmptySuggestions = emptyList()
            )
        }

        // 2. Pure Numeric or Phone or Date/Time fields
        val isNumberPhone = (inputClass == InputType.TYPE_CLASS_NUMBER ||
                             inputClass == InputType.TYPE_CLASS_PHONE ||
                             inputClass == InputType.TYPE_CLASS_DATETIME) && inputClass != InputType.TYPE_CLASS_TEXT
        if (isNumberPhone) {
            return TextBoxClassification(
                category = TextBoxCategory.NUMBER_PHONE,
                isSensitive = false,
                allowsPredictions = false,
                allowsAutocorrect = false,
                allowsAiPolish = false,
                isWeb = false,
                isSearch = false,
                isChat = false,
                isUrl = false,
                isEmail = false,
                defaultEmptySuggestions = emptyList()
            )
        }

        val isBrowserPkg = pkg.contains("chrome") || pkg.contains("browser") ||
                           pkg.contains("firefox") || pkg.contains("opera") ||
                           pkg.contains("edge") || pkg.contains("duckduckgo") ||
                           pkg.contains("samsung") || pkg.contains("brave") ||
                           pkg.contains("kiwi") || pkg.contains("webview") ||
                           pkg.contains("chromium")

        val isWebVariation = variation == InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT

        // 3. Browser Address / URL Bar (Omnibox)
        val isBrowserOmnibox = isBrowserPkg && (
            variation == InputType.TYPE_TEXT_VARIATION_URI ||
            fieldName.contains("url") || fieldName.contains("address") ||
            fieldName.contains("location") || fieldName.contains("omnibox") ||
            fieldName.contains("url_bar") || fieldName.contains("address_bar")
        )
        val isGeneralUri = (inputClass == InputType.TYPE_CLASS_TEXT && variation == InputType.TYPE_TEXT_VARIATION_URI)
        val isUrlBar = isBrowserOmnibox || isGeneralUri

        if (isUrlBar) {
            return TextBoxClassification(
                category = TextBoxCategory.URL_BAR,
                isSensitive = false,
                allowsPredictions = true,
                allowsAutocorrect = false, // Don't corrupt web addresses
                allowsAiPolish = false,
                isWeb = isBrowserPkg,
                isSearch = false,
                isChat = false,
                isUrl = true,
                isEmail = false,
                defaultEmptySuggestions = listOf(".com", "www.", ".org")
            )
        }

        // 4. Email Address Input Fields
        val isEmail = (inputClass == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        )) || fieldName.contains("email") || hintText.contains("email") || label.contains("email")

        if (isEmail) {
            return TextBoxClassification(
                category = TextBoxCategory.EMAIL,
                isSensitive = false,
                allowsPredictions = true,
                allowsAutocorrect = false, // Don't autocorrect email usernames
                allowsAiPolish = false,
                isWeb = isBrowserPkg || isWebVariation,
                isSearch = false,
                isChat = false,
                isUrl = false,
                isEmail = true,
                defaultEmptySuggestions = listOf("@gmail.com", "@outlook.com", "@yahoo.com")
            )
        }

        // 5. Search Fields (Google Search in Chrome or web pages, search apps, in-app search bars)
        val isSearchAction = action == EditorInfo.IME_ACTION_SEARCH
        val isFilterVariation = variation == InputType.TYPE_TEXT_VARIATION_FILTER
        val isSearchPkg = pkg.contains("search") || pkg.contains("googlequicksearchbox")
        val isSearchHint = hintText.contains("search") || hintText.contains("find") ||
                           hintText.contains("google") || hintText.contains("query") ||
                           hintText.contains("ask") || hintText.startsWith("what ") ||
                           hintText.startsWith("how ")
        val isSearchField = fieldName.contains("search") || fieldName.contains("query") ||
                            fieldName == "q" || fieldName == "search_src_text"

        val isSearch = isSearchAction || isFilterVariation || isSearchPkg || isSearchHint || isSearchField

        if (isSearch) {
            return TextBoxClassification(
                category = TextBoxCategory.SEARCH,
                isSensitive = false,
                allowsPredictions = true,
                allowsAutocorrect = true,
                allowsAiPolish = true,
                isWeb = isBrowserPkg || isWebVariation,
                isSearch = true,
                isChat = false,
                isUrl = false,
                isEmail = false,
                defaultEmptySuggestions = listOf("how to", "what is", "best")
            )
        }

        // 6. Chat and Messaging Fields (Instant messengers, web chats, social replies)
        val isChatVariation = variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                              variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE
        val isSendAction = action == EditorInfo.IME_ACTION_SEND
        val isChatPkg = pkg.contains("whatsapp") || pkg.contains("telegram") ||
                        pkg.contains("messaging") || pkg.contains("discord") ||
                        pkg.contains("slack") || pkg.contains("facebook.orca") ||
                        pkg.contains("signal") || pkg.contains("viber") ||
                        pkg.contains("line") || pkg.contains("skype") ||
                        pkg.contains("teams") || pkg.contains("instagram") ||
                        pkg.contains("threads") || pkg.contains("twitter") ||
                        pkg.contains("x.android")
        val isChatHint = hintText.contains("message") || hintText.contains("chat") ||
                         hintText.contains("reply") || hintText.contains("say something") ||
                         hintText.contains("type a message") || hintText.contains("send a message") ||
                         hintText.contains("comment") || hintText.contains("write a message")

        val isChat = isChatVariation || isSendAction || isChatPkg || isChatHint

        if (isChat) {
            return TextBoxClassification(
                category = TextBoxCategory.CHAT,
                isSensitive = false,
                allowsPredictions = true,
                allowsAutocorrect = true,
                allowsAiPolish = true,
                isWeb = isBrowserPkg || isWebVariation,
                isSearch = false,
                isChat = true,
                isUrl = false,
                isEmail = false,
                defaultEmptySuggestions = listOf("Hey", "I'm", "Sure")
            )
        }

        // 7. General Web Page Text Box (Textareas, forms, forums, web comments in Chrome / WebViews)
        if (isBrowserPkg || isWebVariation) {
            return TextBoxClassification(
                category = TextBoxCategory.WEB_PAGE,
                isSensitive = false,
                allowsPredictions = true,
                allowsAutocorrect = true,
                allowsAiPolish = true,
                isWeb = true,
                isSearch = false,
                isChat = false,
                isUrl = false,
                isEmail = false,
                defaultEmptySuggestions = listOf("I", "the", "to")
            )
        }

        // 8. General Text Input (Notes, documents, regular text inputs)
        return defaultClassification
    }

    fun isSensitiveField(info: EditorInfo?): Boolean = classify(info).isSensitive
    fun isUrlField(info: EditorInfo?): Boolean = classify(info).isUrl
    fun isEmailField(info: EditorInfo?): Boolean = classify(info).isEmail
    fun isSearchField(info: EditorInfo?): Boolean = classify(info).isSearch
    fun isWebField(info: EditorInfo?): Boolean = classify(info).isWeb
    fun isChatField(info: EditorInfo?): Boolean = classify(info).isChat
}
