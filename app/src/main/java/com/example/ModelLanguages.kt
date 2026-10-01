package com.example

/**
 * Registry of all 140+ natural languages officially supported by Google's Gemma 4 model.
 * Enables the user to select specific languages or all supported languages to be considered
 * during AI polish, grammar correction, and tone rewriting.
 */
data class ModelLanguage(
    val code: String,
    val name: String,
    val nativeName: String,
    val region: String,
    val isPopular: Boolean = false
)

object ModelLanguages {

    val REGION_ALL = "All"
    val REGION_POPULAR = "Popular"
    val REGION_INDIC = "South Asian / Indic"
    val REGION_EUROPEAN = "European"
    val REGION_EAST_ASIAN = "East & SE Asian"
    val REGION_MIDDLE_EAST = "Middle Eastern & Central Asian"
    val REGION_AFRICAN = "African"
    val REGION_AMERICAS_PACIFIC = "Americas & Pacific"

    val ALL: List<ModelLanguage> = listOf(
        // Popular / Major Global Languages
        ModelLanguage("en", "English", "English", REGION_POPULAR, isPopular = true),
        ModelLanguage("es", "Spanish", "Español", REGION_POPULAR, isPopular = true),
        ModelLanguage("fr", "French", "Français", REGION_POPULAR, isPopular = true),
        ModelLanguage("de", "German", "Deutsch", REGION_POPULAR, isPopular = true),
        ModelLanguage("it", "Italian", "Italiano", REGION_POPULAR, isPopular = true),
        ModelLanguage("pt", "Portuguese", "Português", REGION_POPULAR, isPopular = true),
        ModelLanguage("ru", "Russian", "Русский", REGION_POPULAR, isPopular = true),
        ModelLanguage("zh-Hans", "Chinese (Simplified)", "简体中文", REGION_POPULAR, isPopular = true),
        ModelLanguage("zh-Hant", "Chinese (Traditional)", "繁體中文", REGION_POPULAR, isPopular = true),
        ModelLanguage("ja", "Japanese", "日本語", REGION_POPULAR, isPopular = true),
        ModelLanguage("ko", "Korean", "한국어", REGION_POPULAR, isPopular = true),
        ModelLanguage("ar", "Arabic", "العربية", REGION_POPULAR, isPopular = true),
        ModelLanguage("hi", "Hindi", "हिन्दी", REGION_POPULAR, isPopular = true),
        ModelLanguage("ml", "Malayalam", "മലയാളം", REGION_POPULAR, isPopular = true),
        ModelLanguage("ta", "Tamil", "தமிழ்", REGION_POPULAR, isPopular = true),
        ModelLanguage("te", "Telugu", "తెలుగు", REGION_POPULAR, isPopular = true),
        ModelLanguage("kn", "Kannada", "ಕನ್ನಡ", REGION_POPULAR, isPopular = true),
        ModelLanguage("bn", "Bengali", "বাংলা", REGION_POPULAR, isPopular = true),
        ModelLanguage("mr", "Marathi", "मराठी", REGION_POPULAR, isPopular = true),
        ModelLanguage("gu", "Gujarati", "ગુજરાતી", REGION_POPULAR, isPopular = true),
        ModelLanguage("pa", "Punjabi", "ਪੰਜਾਬੀ", REGION_POPULAR, isPopular = true),
        ModelLanguage("ur", "Urdu", "اردو", REGION_POPULAR, isPopular = true),

        // South Asian / Indic Languages
        ModelLanguage("as", "Assamese", "অসমীয়া", REGION_INDIC),
        ModelLanguage("or", "Odia", "ଓଡ଼ିଆ", REGION_INDIC),
        ModelLanguage("ne", "Nepali", "नेपाली", REGION_INDIC),
        ModelLanguage("si", "Sinhala", "සිංහල", REGION_INDIC),
        ModelLanguage("sa", "Sanskrit", "संस्कृतम्", REGION_INDIC),
        ModelLanguage("mai", "Maithili", "मैथिली", REGION_INDIC),
        ModelLanguage("bho", "Bhojpuri", "भोजपुरी", REGION_INDIC),
        ModelLanguage("sd", "Sindhi", "سنڌي", REGION_INDIC),
        ModelLanguage("ks", "Kashmiri", "کٲشُر", REGION_INDIC),
        ModelLanguage("kok", "Konkani", "कोंकणी", REGION_INDIC),
        ModelLanguage("doi", "Dogri", "डोगरी", REGION_INDIC),
        ModelLanguage("mni", "Manipuri (Meitei)", "মৈতৈলোন্", REGION_INDIC),
        ModelLanguage("brx", "Bodo", "बड़ो", REGION_INDIC),
        ModelLanguage("sat", "Santali", "ᱥᱟᱱᱛᱟᱲᱤ", REGION_INDIC),
        ModelLanguage("dv", "Divehi", "ދިވެހި", REGION_INDIC),

        // East & Southeast Asian
        ModelLanguage("vi", "Vietnamese", "Tiếng Việt", REGION_EAST_ASIAN),
        ModelLanguage("th", "Thai", "ไทย", REGION_EAST_ASIAN),
        ModelLanguage("id", "Indonesian", "Bahasa Indonesia", REGION_EAST_ASIAN),
        ModelLanguage("ms", "Malay", "Bahasa Melayu", REGION_EAST_ASIAN),
        ModelLanguage("tl", "Tagalog (Filipino)", "Filipino", REGION_EAST_ASIAN),
        ModelLanguage("my", "Burmese", "မြန်မာ", REGION_EAST_ASIAN),
        ModelLanguage("km", "Khmer", "ខ្មែរ", REGION_EAST_ASIAN),
        ModelLanguage("lo", "Lao", "ລາວ", REGION_EAST_ASIAN),
        ModelLanguage("jv", "Javanese", "Basa Jawa", REGION_EAST_ASIAN),
        ModelLanguage("su", "Sundanese", "Basa Sunda", REGION_EAST_ASIAN),
        ModelLanguage("ceb", "Cebuano", "Bisaya", REGION_EAST_ASIAN),
        ModelLanguage("ilo", "Ilokano", "Ilokano", REGION_EAST_ASIAN),
        ModelLanguage("hil", "Hiligaynon", "Hiligaynon", REGION_EAST_ASIAN),
        ModelLanguage("war", "Waray", "Winaray", REGION_EAST_ASIAN),
        ModelLanguage("bo", "Tibetan", "བོད་སྐད་", REGION_EAST_ASIAN),

        // European Languages
        ModelLanguage("nl", "Dutch", "Nederlands", REGION_EUROPEAN),
        ModelLanguage("pl", "Polish", "Polski", REGION_EUROPEAN),
        ModelLanguage("uk", "Ukrainian", "Українська", REGION_EUROPEAN),
        ModelLanguage("cs", "Czech", "Čeština", REGION_EUROPEAN),
        ModelLanguage("sv", "Swedish", "Svenska", REGION_EUROPEAN),
        ModelLanguage("ro", "Romanian", "Română", REGION_EUROPEAN),
        ModelLanguage("el", "Greek", "Ελληνικά", REGION_EUROPEAN),
        ModelLanguage("hu", "Hungarian", "Magyar", REGION_EUROPEAN),
        ModelLanguage("bg", "Bulgarian", "Български", REGION_EUROPEAN),
        ModelLanguage("da", "Danish", "Dansk", REGION_EUROPEAN),
        ModelLanguage("fi", "Finnish", "Suomi", REGION_EUROPEAN),
        ModelLanguage("sk", "Slovak", "Slovenčina", REGION_EUROPEAN),
        ModelLanguage("no", "Norwegian", "Norsk", REGION_EUROPEAN),
        ModelLanguage("hr", "Croatian", "Hrvatski", REGION_EUROPEAN),
        ModelLanguage("sr", "Serbian", "Српски", REGION_EUROPEAN),
        ModelLanguage("bs", "Bosnian", "Bosanski", REGION_EUROPEAN),
        ModelLanguage("sl", "Slovenian", "Slovenščina", REGION_EUROPEAN),
        ModelLanguage("lt", "Lithuanian", "Lietuvių", REGION_EUROPEAN),
        ModelLanguage("lv", "Latvian", "Latviešu", REGION_EUROPEAN),
        ModelLanguage("et", "Estonian", "Eesti", REGION_EUROPEAN),
        ModelLanguage("ga", "Irish", "Gaeilge", REGION_EUROPEAN),
        ModelLanguage("cy", "Welsh", "Cymraeg", REGION_EUROPEAN),
        ModelLanguage("gd", "Scottish Gaelic", "Gàidhlig", REGION_EUROPEAN),
        ModelLanguage("eu", "Basque", "Euskara", REGION_EUROPEAN),
        ModelLanguage("ca", "Catalan", "Català", REGION_EUROPEAN),
        ModelLanguage("gl", "Galician", "Galego", REGION_EUROPEAN),
        ModelLanguage("sq", "Albanian", "Shqip", REGION_EUROPEAN),
        ModelLanguage("mk", "Macedonian", "Македонски", REGION_EUROPEAN),
        ModelLanguage("be", "Belarusian", "Беларуская", REGION_EUROPEAN),
        ModelLanguage("mt", "Maltese", "Malti", REGION_EUROPEAN),
        ModelLanguage("is", "Icelandic", "Íslenska", REGION_EUROPEAN),
        ModelLanguage("lb", "Luxembourgish", "Lëtzebuergesch", REGION_EUROPEAN),
        ModelLanguage("fo", "Faroese", "Føroyskt", REGION_EUROPEAN),
        ModelLanguage("rm", "Romansh", "Rumantsch", REGION_EUROPEAN),
        ModelLanguage("br", "Breton", "Brezhoneg", REGION_EUROPEAN),
        ModelLanguage("co", "Corsican", "Corsu", REGION_EUROPEAN),
        ModelLanguage("fy", "Frisian", "Frysk", REGION_EUROPEAN),
        ModelLanguage("oc", "Occitan", "Occitan", REGION_EUROPEAN),
        ModelLanguage("la", "Latin", "Latina", REGION_EUROPEAN),
        ModelLanguage("eo", "Esperanto", "Esperanto", REGION_EUROPEAN),

        // Middle Eastern & Central Asian
        ModelLanguage("tr", "Turkish", "Türkçe", REGION_MIDDLE_EAST),
        ModelLanguage("fa", "Persian (Farsi)", "فارسی", REGION_MIDDLE_EAST),
        ModelLanguage("he", "Hebrew", "עברית", REGION_MIDDLE_EAST),
        ModelLanguage("ku", "Kurdish", "Kurdî", REGION_MIDDLE_EAST),
        ModelLanguage("ps", "Pashto", "پښتو", REGION_MIDDLE_EAST),
        ModelLanguage("az", "Azerbaijani", "Azərbaycan", REGION_MIDDLE_EAST),
        ModelLanguage("kk", "Kazakh", "Қазақша", REGION_MIDDLE_EAST),
        ModelLanguage("uz", "Uzbek", "Oʻzbekcha", REGION_MIDDLE_EAST),
        ModelLanguage("tk", "Turkmen", "Türkmençe", REGION_MIDDLE_EAST),
        ModelLanguage("ky", "Kyrgyz", "Кыргызча", REGION_MIDDLE_EAST),
        ModelLanguage("tg", "Tajik", "Тоҷикӣ", REGION_MIDDLE_EAST),
        ModelLanguage("mn", "Mongolian", "Монгол", REGION_MIDDLE_EAST),
        ModelLanguage("hy", "Armenian", "Հայերեն", REGION_MIDDLE_EAST),
        ModelLanguage("ka", "Georgian", "ქართული", REGION_MIDDLE_EAST),
        ModelLanguage("tt", "Tatar", "Татарча", REGION_MIDDLE_EAST),
        ModelLanguage("ug", "Uyghur", "ئۇيغۇرچە", REGION_MIDDLE_EAST),
        ModelLanguage("yi", "Yiddish", "ייִדיש", REGION_MIDDLE_EAST),

        // African Languages
        ModelLanguage("sw", "Swahili", "Kiswahili", REGION_AFRICAN),
        ModelLanguage("am", "Amharic", "አማርኛ", REGION_AFRICAN),
        ModelLanguage("yo", "Yoruba", "Èdè Yorùbá", REGION_AFRICAN),
        ModelLanguage("ig", "Igbo", "Asụsụ Igbo", REGION_AFRICAN),
        ModelLanguage("ha", "Hausa", "Harshen Hausa", REGION_AFRICAN),
        ModelLanguage("zu", "Zulu", "isiZulu", REGION_AFRICAN),
        ModelLanguage("xh", "Xhosa", "isiXhosa", REGION_AFRICAN),
        ModelLanguage("af", "Afrikaans", "Afrikaans", REGION_AFRICAN),
        ModelLanguage("so", "Somali", "Soomaaliga", REGION_AFRICAN),
        ModelLanguage("om", "Oromo", "Oromoo", REGION_AFRICAN),
        ModelLanguage("ti", "Tigrinya", "ትግርኛ", REGION_AFRICAN),
        ModelLanguage("mg", "Malagasy", "Malagasy", REGION_AFRICAN),
        ModelLanguage("sn", "Shona", "chiShona", REGION_AFRICAN),
        ModelLanguage("st", "Sesotho", "Sesotho", REGION_AFRICAN),
        ModelLanguage("ny", "Chichewa", "Chichewa", REGION_AFRICAN),
        ModelLanguage("rw", "Kinyarwanda", "Ikinyarwanda", REGION_AFRICAN),
        ModelLanguage("lg", "Luganda", "Oluganda", REGION_AFRICAN),
        ModelLanguage("ln", "Lingala", "Lingála", REGION_AFRICAN),
        ModelLanguage("wo", "Wolof", "Wolof", REGION_AFRICAN),
        ModelLanguage("tn", "Tswana", "Setswana", REGION_AFRICAN),
        ModelLanguage("ff", "Fulah", "Fulfulde", REGION_AFRICAN),

        // Americas & Pacific
        ModelLanguage("haw", "Hawaiian", "ʻŌlelo Hawaiʻi", REGION_AMERICAS_PACIFIC),
        ModelLanguage("mi", "Maori", "Te Reo Māori", REGION_AMERICAS_PACIFIC),
        ModelLanguage("sm", "Samoan", "Gagana Sāmoa", REGION_AMERICAS_PACIFIC),
        ModelLanguage("to", "Tongan", "Lea Faka-Tonga", REGION_AMERICAS_PACIFIC),
        ModelLanguage("fj", "Fijian", "Na Vosa Vakaviti", REGION_AMERICAS_PACIFIC),
        ModelLanguage("ht", "Haitian Creole", "Kreyòl Ayisyen", REGION_AMERICAS_PACIFIC),
        ModelLanguage("qu", "Quechua", "Runa Simi", REGION_AMERICAS_PACIFIC),
        ModelLanguage("gn", "Guarani", "Avañe'ẽ", REGION_AMERICAS_PACIFIC),
        ModelLanguage("ay", "Aymara", "Aymar aru", REGION_AMERICAS_PACIFIC),
        ModelLanguage("nv", "Navajo", "Diné bizaad", REGION_AMERICAS_PACIFIC),
        ModelLanguage("kl", "Greenlandic", "Kalaallisut", REGION_AMERICAS_PACIFIC)
    )

    val ALL_CODES: Set<String> by lazy { ALL.map { it.code }.toSet() }

    val DEFAULT_SELECTED_CODES: Set<String> = setOf("en")

    fun findByCode(code: String): ModelLanguage? {
        return ALL.firstOrNull { it.code.equals(code, ignoreCase = true) }
    }

    fun search(query: String, filterRegion: String = REGION_ALL): List<ModelLanguage> {
        val q = query.trim().lowercase()
        return ALL.filter { lang ->
            val matchesRegion = when (filterRegion) {
                REGION_ALL -> true
                REGION_POPULAR -> lang.isPopular
                else -> lang.region.equals(filterRegion, ignoreCase = true)
            }
            val matchesQuery = q.isEmpty() ||
                lang.name.lowercase().contains(q) ||
                lang.nativeName.lowercase().contains(q) ||
                lang.code.lowercase().contains(q)
            matchesRegion && matchesQuery
        }
    }
}
