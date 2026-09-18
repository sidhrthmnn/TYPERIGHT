package com.example

import java.util.Locale
import java.util.regex.Pattern

/**
 * Formatting styles available for voice transcription.
 */
enum class TranscriptionFormatStyle(
    val title: String,
    val icon: String,
    val description: String
) {
    SMART_CLEAN("Smart Polish", "⚡", "Cleans fillers, fixes punctuation, capitalization, numbers, and self-corrections."),
    BULLETS("Bullet Points", "•", "Structures spoken thoughts into clean, indented bullet points."),
    NUMBERED("Numbered List", "1.", "Formats sequential items into ordered 1, 2, 3 numbered steps."),
    EMAIL("Email Format", "✉️", "Formats speech into greeting, clear paragraphs, and sign-off."),
    EXECUTIVE("Executive", "👔", "Polishes tone for formal business correspondence."),
    CONCISE("Concise", "✂️", "Eliminates redundancy and verbose conversational padding."),
    CHECKLIST("Checklist", "☐", "Formats actionable thoughts into to-do checklist items."),
    CASUAL("Casual Chat", "💬", "Natural friendly conversational tone with emoji polish."),
    VERBATIM("Clean Verbatim", "✍️", "Keeps exact words spoken with perfect punctuation and casing.")
}

/**
 * VoiceTranscriptionFormatter provides real-time streaming speech cleaning,
 * spoken punctuation resolution, number/currency conversion, mid-sentence self-correction repair,
 * acronym capitalization, and rich multi-format layout transformations.
 */
object VoiceTranscriptionFormatter {

    // Common vocal fillers, stutter hesitation markers
    private val FILLER_REGEX = Regex(
        "(?i)\\b(um+|uh+|er+|ah+|like|you know|sort of|kind of|basically|literally|so yeah|i mean|right)\\b[,]?\\s*"
    )

    private val EXTENDED_FILLER_PATTERNS = listOf(
        Regex("(?i)\\b(you know what i mean|at the end of the day|to be honest|if you know what i'm saying)\\b[,]?\\s*"),
        Regex("(?i)\\b(i guess|i suppose|as a matter of fact)\\b[,]?\\s*")
    )

    // Spoken Punctuation to Symbols
    private val SPOKEN_PUNCTUATION_PATTERNS = listOf(
        Regex("(?i)\\b(period|full stop)\\b") to ".",
        Regex("(?i)\\bcomma\\b") to ",",
        Regex("(?i)\\bquestion mark\\b") to "?",
        Regex("(?i)\\b(exclamation mark|exclamation point)\\b") to "!",
        Regex("(?i)\\b(new line|next line)\\b") to "\n",
        Regex("(?i)\\b(new paragraph|next paragraph|paragraph break)\\b") to "\n\n",
        Regex("(?i)\\bcolon\\b") to ":",
        Regex("(?i)\\bsemicolon\\b") to ";",
        Regex("(?i)\\b(em dash|dash|hyphen)\\b") to " — ",
        Regex("(?i)\\b(ellipsis|dot dot dot|triple dot)\\b") to "...",
        Regex("(?i)\\b(open quotes?|open quote|begin quote|quote)\\b") to "\"",
        Regex("(?i)\\b(close quotes?|close quote|end quote|unquote)\\b") to "\"",
        Regex("(?i)\\b(open parenthesis|open paren|left paren)\\b") to "(",
        Regex("(?i)\\b(close parenthesis|close paren|right paren)\\b") to ")",
        Regex("(?i)\\b(open bracket|left bracket)\\b") to "[",
        Regex("(?i)\\b(close bracket|right bracket)\\b") to "]",
        Regex("(?i)\\b(slash|forward slash)\\b") to "/",
        Regex("(?i)\\b(backslash)\\b") to "\\",
        Regex("(?i)\\b(at sign|at symbol)\\s*([a-zA-Z0-9_]+)") to "@$2",
        Regex("(?i)\\b(hashtag|hash tag|pound sign)\\s*([a-zA-Z0-9_]+)") to "#$2",
        Regex("(?i)\\b(percent|percentage)\\s*(?:sign)?\\b") to "%",
        Regex("(?i)\\b(ampersand|and sign|and symbol)\\b") to "&",
        Regex("(?i)\\b(asterisk|star symbol)\\b") to "*",
        Regex("(?i)\\b(plus sign)\\b") to "+",
        Regex("(?i)\\b(minus sign)\\b") to "-",
        Regex("(?i)\\b(equals sign|equal sign)\\b") to "=",
        Regex("(?i)\\b(degrees?|degree sign)\\b") to "°",
        Regex("(?i)\\b(bullet point|bullet)\\b") to "\n• ",
        Regex("(?i)\\b(todo item|to do item|task item)\\b") to "\n[ ] "
    )

    // Spoken Emojis (require explicit emoji/symbol keyword)
    private val SPOKEN_EMOJIS = listOf(
        Regex("(?i)\\b(thumbs? up)\\s+(?:emoji|symbol)\\b") to "👍",
        Regex("(?i)\\b(thumbs? down)\\s+(?:emoji|symbol)\\b") to "👎",
        Regex("(?i)\\b(?:red\\s+)?heart\\s+(?:emoji|symbol)\\b") to "❤️",
        Regex("(?i)\\b(smiling face emoji|smiley face emoji|smile emoji|smiley emoji)\\b") to "😊",
        Regex("(?i)\\b(laughing face emoji|crying laughing emoji|joy emoji)\\b") to "😂",
        Regex("(?i)\\b(fire emoji|flame emoji|fire symbol)\\b") to "🔥",
        Regex("(?i)\\b(rocket emoji|rocket ship emoji)\\b") to "🚀",
        Regex("(?i)\\b(party popper emoji|celebration emoji|party emoji)\\b") to "🎉",
        Regex("(?i)\\b(check mark|checkmark)\\s+(?:emoji|symbol)\\b") to "✅",
        Regex("(?i)\\b(cross mark|red x)\\s+(?:emoji|symbol)\\b") to "❌",
        Regex("(?i)\\b(sparkles?)\\s+emoji\\b") to "✨",
        Regex("(?i)\\b(thinking face emoji|thinking emoji)\\b") to "🤔",
        Regex("(?i)\\b(folded hands emoji|praying hands emoji|prayer emoji)\\b") to "🙏",
        Regex("(?i)\\b(waving hand emoji|wave emoji)\\b") to "👋",
        Regex("(?i)\\b(eyes emoji|eye emoji)\\b") to "👀",
        Regex("(?i)\\b(arrow right sign|arrow right symbol)\\b") to "→",
        Regex("(?i)\\b(arrow left sign|arrow left symbol)\\b") to "←"
    )

    // Spoken Numbers to Digits
    private val WORD_TO_DIGIT = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13", "fourteen" to "14",
        "fifteen" to "15", "sixteen" to "16", "seventeen" to "17", "eighteen" to "18", "nineteen" to "19",
        "twenty" to "20", "thirty" to "30", "forty" to "40", "fifty" to "50",
        "sixty" to "60", "seventy" to "70", "eighty" to "80", "ninety" to "90",
        "hundred" to "100", "thousand" to "1000", "million" to "1000000"
    )

    // Tech acronyms and proper casing
    private val ACRONYMS = setOf(
        "AI", "API", "UI", "UX", "URL", "USA", "UK", "CEO", "CTO", "COO", "CFO",
        "LLM", "SLM", "PR", "FAQ", "ASAP", "DIY", "ETA", "OK", "TV", "SMS", "GPS",
        "WIFI", "IOS", "HTML", "CSS", "JSON", "SDK", "ID", "PDF", "RAM", "CPU", "GPU"
    )

    private val PROPER_NOUNS = mapOf(
        "google" to "Google", "android" to "Android", "apple" to "Apple",
        "iphone" to "iPhone", "ipad" to "iPad", "macbook" to "MacBook",
        "microsoft" to "Microsoft", "windows" to "Windows", "github" to "GitHub",
        "git" to "Git", "slack" to "Slack", "zoom" to "Zoom", "whatsapp" to "WhatsApp",
        "youtube" to "YouTube", "instagram" to "Instagram", "twitter" to "Twitter",
        "linkedin" to "LinkedIn", "typeright" to "TypeRight", "wispr" to "Wispr",
        "monday" to "Monday", "tuesday" to "Tuesday", "wednesday" to "Wednesday",
        "thursday" to "Thursday", "friday" to "Friday", "saturday" to "Saturday",
        "sunday" to "Sunday", "january" to "January", "february" to "February",
        "march" to "March", "april" to "April", "may" to "May", "june" to "June",
        "july" to "July", "august" to "August", "september" to "September",
        "october" to "October", "november" to "November", "december" to "December"
    )

    // Unpunctuated contractions restoration
    private val CONTRACTIONS = mapOf(
        "im" to "I'm", "dont" to "don't", "cant" to "can't", "wont" to "won't",
        "youre" to "you're", "theyre" to "they're", "weve" to "we've", "theyve" to "they've",
        "isnt" to "isn't", "arent" to "aren't", "didnt" to "didn't", "hasnt" to "hasn't",
        "havent" to "haven't", "wasnt" to "wasn't", "werent" to "weren't",
        "couldnt" to "couldn't", "shouldnt" to "shouldn't", "wouldnt" to "wouldn't",
        "couldve" to "could've", "shouldve" to "should've", "wouldve" to "would've",
        "thats" to "that's", "whats" to "what's", "theres" to "there's", "heres" to "here's",
        "lets" to "let's", "ive" to "I've", "id" to "I'd", "ill" to "I'll"
    )

    /**
     * Primary entry point for real-time transcription formatting.
     * Takes raw streaming voice text and returns pristine formatted text based on selected style.
     */
    fun formatTranscription(
        rawText: String,
        style: TranscriptionFormatStyle = TranscriptionFormatStyle.SMART_CLEAN
    ): String {
        if (rawText.isBlank()) return ""

        // 1. Initial base cleanup: Spoken punctuation, symbols, numbers, currencies
        var text = processSpokenPunctuationAndSymbols(rawText)
        text = processSpokenCurrenciesAndNumbers(text)

        // 2. Style-specific layout transformation
        return when (style) {
            TranscriptionFormatStyle.SMART_CLEAN -> formatSmartClean(text)
            TranscriptionFormatStyle.BULLETS -> formatAsBullets(text)
            TranscriptionFormatStyle.NUMBERED -> formatAsNumbered(text)
            TranscriptionFormatStyle.EMAIL -> formatAsEmail(text)
            TranscriptionFormatStyle.EXECUTIVE -> formatAsExecutive(text)
            TranscriptionFormatStyle.CONCISE -> formatAsConcise(text)
            TranscriptionFormatStyle.CHECKLIST -> formatAsChecklist(text)
            TranscriptionFormatStyle.CASUAL -> formatAsCasual(text)
            TranscriptionFormatStyle.VERBATIM -> formatCleanVerbatim(text)
        }
    }

    /**
     * Replaces spoken punctuation words and symbols.
     */
    fun processSpokenPunctuationAndSymbols(input: String): String {
        var text = input

        for ((pattern, replacement) in SPOKEN_PUNCTUATION_PATTERNS) {
            text = pattern.replace(text, replacement)
        }

        for ((pattern, emoji) in SPOKEN_EMOJIS) {
            text = pattern.replace(text, emoji)
        }

        // Web and email domains
        text = text
            .replace(Regex("(?i)\\bdot\\s+com\\b"), ".com")
            .replace(Regex("(?i)\\bdot\\s+org\\b"), ".org")
            .replace(Regex("(?i)\\bdot\\s+net\\b"), ".net")
            .replace(Regex("(?i)\\bdot\\s+io\\b"), ".io")
            .replace(Regex("(?i)\\bdot\\s+ai\\b"), ".ai")
            .replace(Regex("(?i)\\bw\\s+w\\s+w\\s+dot\\b"), "www.")

        return text
    }

    /**
     * Converts spoken currencies, times, percentages, and measurement units to standard notation.
     */
    fun processSpokenCurrenciesAndNumbers(input: String): String {
        var text = input

        // Spoken time with words: e.g. "three thirty pm", "three 30 pm", "five o'clock pm", "five pm"
        val hourWords = mapOf(
            "one" to "1", "two" to "2", "three" to "3", "four" to "4",
            "five" to "5", "six" to "6", "seven" to "7", "eight" to "8",
            "nine" to "9", "ten" to "10", "eleven" to "11", "twelve" to "12"
        )
        val minuteWords = mapOf(
            "fifteen" to "15", "twenty" to "20", "twenty five" to "25",
            "thirty" to "30", "thirty five" to "35", "forty" to "40",
            "forty five" to "45", "fifty" to "50", "fifty five" to "55"
        )
        for ((hWord, hDigit) in hourWords) {
            for ((mWord, mDigit) in minuteWords) {
                text = text.replace(Regex("(?i)\\b$hWord\\s+$mWord\\s*(am|pm)\\b"), "$hDigit:$mDigit $1")
            }
            text = text.replace(Regex("(?i)\\b$hWord\\s+(\\d{2})\\s*(am|pm)\\b"), "$hDigit:$1 $2")
            text = text.replace(Regex("(?i)\\b$hWord\\s*(?:o'clock)?\\s*(am|pm)\\b"), "$hDigit:00 $1")
        }

        // Times formatting: "3:30 pm" -> "3:30 PM", "5 pm" -> "5:00 PM"
        text = text.replace(Regex("(?i)(?<!:)\\b(\\d{1,2})\\s*(?:o'clock)?\\s*(am|pm)\\b"), "$1:00 $2")
        text = Regex("(?i)\\b(\\d{1,2}):(\\d{2})\\s*(am|pm)\\b").replace(text) { match ->
            "${match.groupValues[1]}:${match.groupValues[2]} ${match.groupValues[3].uppercase(Locale.ROOT)}"
        }

        // Spoken numbers before common units
        for ((word, digit) in WORD_TO_DIGIT) {
            val unitPattern = Regex("(?i)\\b$word\\s+(dollars?|bucks|euros?|percent|pm|am|hours?|minutes?|seconds?|days?|weeks?|months?|years?|miles?|km|kilometers?|gb|mb)\\b")
            text = text.replace(unitPattern, "$digit $1")
        }

        // Currency: "twenty five dollars" -> "$25", "ten bucks" -> "$10"
        text = Regex("(?i)\\b(?:dollar|dollars)\\s*(?:sign)?\\s*(\\d+)").replace(text) { match ->
            "$${match.groupValues[1]}"
        }
        text = Regex("(?i)\\b(\\d+)\\s*(?:dollars?|bucks)\\b").replace(text) { match ->
            "$${match.groupValues[1]}"
        }
        text = Regex("(?i)\\b(\\d+)\\s*(?:euros?)\\b").replace(text) { match ->
            "€${match.groupValues[1]}"
        }
        text = Regex("(?i)\\b(\\d+)\\s*(?:pounds?)\\b").replace(text) { match ->
            "£${match.groupValues[1]}"
        }

        // Currency with cents: "ten dollars and fifty cents" -> "$10.50"
        text = Regex("(?i)\\b(\\d+)\\s+dollars\\s+and\\s+(\\d+)\\s+cents\\b").replace(text) { match ->
            "$${match.groupValues[1]}.${match.groupValues[2]}"
        }

        // Percentages: "fifteen percent" -> "15%"
        text = text.replace(Regex("(?i)\\b(\\d+)\\s*(?:percent|percentage)\\b"), "$1%")

        // Ordinals for lists: "number one" -> "1.", "step one" -> "Step 1:"
        text = text
            .replace(Regex("(?i)\\bnumber\\s+one\\b"), "1.")
            .replace(Regex("(?i)\\bnumber\\s+two\\b"), "2.")
            .replace(Regex("(?i)\\bnumber\\s+three\\b"), "3.")
            .replace(Regex("(?i)\\bnumber\\s+four\\b"), "4.")
            .replace(Regex("(?i)\\bnumber\\s+five\\b"), "5.")
            .replace(Regex("(?i)\\bstep\\s+one\\b"), "Step 1:")
            .replace(Regex("(?i)\\bstep\\s+two\\b"), "Step 2:")
            .replace(Regex("(?i)\\bstep\\s+three\\b"), "Step 3:")

        return text
    }

    /**
     * Resolves conversational self-corrections (e.g. "Tuesday wait no Wednesday" -> "Wednesday").
     */
    fun resolveSelfCorrections(input: String): String {
        var text = input

        // 1. Spoken retraction: "scratch that", "delete that", "never mind that"
        text = text.replace(Regex("(?i)(?:.+?)[,.]?\\s*(?:scratch that|delete that|never mind that)[.]?\\s*"), "")

        // 2. Phrase replacements: "at 2 wait no 3" -> "at 3"
        text = text.replace(Regex("(?i)\\b(\\w+)[—–\\-\\s]+(?:wait no|no wait|actually wait|sorry no|no)\\s*,?\\s*(\\w+(?:\\s+at\\s+\\d+(?:\\s*(?:am|pm))?)?)"), "$2")
        text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually let's make it|actually make it|let's make it instead)\\s+(\\d+.*)"), "Let's meet at $1")
        text = text.replace(Regex("(?i)(?:.+?)\\s+(?:actually|no wait|sorry|or rather|i mean)\\s+(let's|can we|please|we should|i will|make it)\\s+(.+)"), "$1 $2")
        text = text.replace(Regex("(?i)\\b(.+?)\\s+(?:no wait|sorry i mean|i mean|or rather|actually wait)\\s+(.+)"), "$2")

        return text
    }

    /**
     * Strips vocal fillers and hesitation markers.
     */
    fun removeVocalFillers(input: String): String {
        var text = FILLER_REGEX.replace(input, "")
        for (pattern in EXTENDED_FILLER_PATTERNS) {
            text = pattern.replace(text, "")
        }
        // Remove stutter duplicates: "the the car" -> "the car"
        text = text.replace(Regex("(?i)\\b(\\w+)(?:\\s+\\1\\b)+"), "$1")
        return text
    }

    /**
     * Restores contractions, capitalizes acronyms, proper nouns, and sentence starts.
     */
    fun polishGrammarAndCapitalization(input: String): String {
        if (input.isBlank()) return ""

        val tokens = input.split(Regex("(?<=\\s)|(?=\\s)|(?<=[.,!?;:\n\"()\\[\\]])|(?=[.,!?;:\n\"()\\[\\]])"))
        val sb = StringBuilder()

        for (token in tokens) {
            val lower = token.lowercase(Locale.ROOT)
            when {
                CONTRACTIONS.containsKey(lower) -> {
                    sb.append(CONTRACTIONS[lower])
                }
                ACRONYMS.contains(token.uppercase(Locale.ROOT)) && token.length in 2..4 -> {
                    sb.append(token.uppercase(Locale.ROOT))
                }
                PROPER_NOUNS.containsKey(lower) -> {
                    sb.append(PROPER_NOUNS[lower])
                }
                else -> {
                    sb.append(token)
                }
            }
        }

        var result = sb.toString()
        result = cleanPunctuationAndSpacing(result)
        return result
    }

    /**
     * Smart Clean: Combines filler removal, self-correction repair, punctuation formatting,
     * and proper capitalization.
     */
    fun formatSmartClean(input: String): String {
        var text = resolveSelfCorrections(input)
        text = removeVocalFillers(text)
        text = polishGrammarAndCapitalization(text)
        return text
    }

    /**
     * Formats spoken text into clean, structured bullet points.
     */
    fun formatAsBullets(input: String): String {
        val cleaned = formatSmartClean(input)
        if (cleaned.isBlank()) return ""

        val clauses = splitIntoClauses(cleaned)
        if (clauses.isEmpty()) return cleaned

        return clauses.joinToString("\n") { clause ->
            val trimmed = clause.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val formatted = if (!trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?")) "$trimmed." else trimmed
            "• $formatted"
        }
    }

    /**
     * Formats spoken text into ordered numbered steps.
     */
    fun formatAsNumbered(input: String): String {
        val cleaned = formatSmartClean(input)
        if (cleaned.isBlank()) return ""

        val clauses = splitIntoClauses(cleaned)
        if (clauses.isEmpty()) return cleaned

        return clauses.mapIndexed { idx, clause ->
            val trimmed = clause.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val formatted = if (!trimmed.endsWith(".") && !trimmed.endsWith("!") && !trimmed.endsWith("?")) "$trimmed." else trimmed
            "${idx + 1}. $formatted"
        }.joinToString("\n")
    }

    /**
     * Formats spoken text into an actionable checklist.
     */
    fun formatAsChecklist(input: String): String {
        val cleaned = formatSmartClean(input)
        if (cleaned.isBlank()) return ""

        val clauses = splitIntoClauses(cleaned)
        if (clauses.isEmpty()) return cleaned

        return clauses.joinToString("\n") { clause ->
            val trimmed = clause.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            "☐ $trimmed"
        }
    }

    /**
     * Formats spoken thoughts into a professional email with greeting, body paragraphs, and sign-off.
     */
    fun formatAsEmail(input: String): String {
        var text = formatSmartClean(input)
        if (text.isBlank()) return ""

        // Extract Greeting
        var greetingHeader = ""
        val greetingRegex = Regex(
            "^(hey|hi|hello|dear|good morning|good afternoon|good evening|greetings)(?:\\s+([a-zA-Z]+))?(?:\\s+(?:there|everyone|all|team))?\\b[,:]?",
            RegexOption.IGNORE_CASE
        )
        val greetingMatch = greetingRegex.find(text)
        if (greetingMatch != null) {
            val greetingWord = greetingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val name = greetingMatch.groupValues[2].trim()
            val formattedName = if (name.isNotEmpty()) " " + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } else ""
            greetingHeader = "$greetingWord$formattedName,\n\n"
            text = text.substring(greetingMatch.value.length).trim()
        } else {
            greetingHeader = "Hello,\n\n"
        }

        // Extract Closing
        var closingFooter = ""
        val closingRegex = Regex(
            "\\b(thanks|thank you|best regards|regards|sincerely|cheers|best|warmly|yours truly)(?:\\s+([a-zA-Z]+))?\\.?$",
            RegexOption.IGNORE_CASE
        )
        val closingMatch = closingRegex.find(text)
        if (closingMatch != null) {
            val closingWord = closingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val name = closingMatch.groupValues[2].trim()
            val formattedName = if (name.isNotEmpty()) "\n" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() } else ""
            closingFooter = "\n\n$closingWord,$formattedName"
            text = text.substring(0, closingMatch.range.first).trim()
        } else {
            closingFooter = "\n\nBest regards,"
        }

        // Break body into paragraphs
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val bodyBuilder = StringBuilder()
        var currentPara = StringBuilder()

        for ((idx, s) in sentences.withIndex()) {
            if (currentPara.isNotEmpty()) currentPara.append(" ")
            currentPara.append(s.trim())

            // Create new paragraph after 2-3 sentences or strong transition
            val hasTransition = s.contains(Regex("(?i)\\b(in addition|furthermore|by the way|also|moving on)\\b"))
            if ((idx > 0 && idx % 2 == 1) || hasTransition) {
                if (bodyBuilder.isNotEmpty()) bodyBuilder.append("\n\n")
                bodyBuilder.append(currentPara.toString())
                currentPara = StringBuilder()
            }
        }
        if (currentPara.isNotEmpty()) {
            if (bodyBuilder.isNotEmpty()) bodyBuilder.append("\n\n")
            bodyBuilder.append(currentPara.toString())
        }

        val body = bodyBuilder.toString().ifEmpty { text }
        return "$greetingHeader$body$closingFooter"
    }

    /**
     * Executive / Formal Business formatting: elevates wording, expands contractions, removes slang.
     */
    fun formatAsExecutive(input: String): String {
        var text = formatSmartClean(input)

        // Expand contractions
        text = text
            .replace(Regex("(?i)\\bcan't\\b"), "cannot")
            .replace(Regex("(?i)\\bdon't\\b"), "do not")
            .replace(Regex("(?i)\\bwon't\\b"), "will not")
            .replace(Regex("(?i)\\bgonna\\b"), "going to")
            .replace(Regex("(?i)\\bwanna\\b"), "would like to")
            .replace(Regex("(?i)\\bgotta\\b"), "need to")
            .replace(Regex("(?i)\\bkinda\\b"), "somewhat")
            .replace(Regex("(?i)\\basap\\b"), "at your earliest convenience")
            .replace(Regex("(?i)\\bthanks\\b"), "thank you")
            .replace(Regex("(?i)\\banyway\\b"), "nevertheless")
            .replace(Regex("(?i)\\bhaha|lol\\b"), "")

        if (text.contains("running late", ignoreCase = true)) {
            text = text.replace(Regex("(?i)I'm running late[.]?"), "I apologize for the delay, but I am currently running behind schedule.")
        }

        return cleanPunctuationAndSpacing(text)
    }

    /**
     * Concise formatting: removes conversational padding and redundancy.
     */
    fun formatAsConcise(input: String): String {
        var text = formatSmartClean(input)

        val verboseReplacements = listOf(
            Regex("(?i)\\bin order to\\b") to "to",
            Regex("(?i)\\bdue to the fact that\\b") to "because",
            Regex("(?i)\\bat this point in time\\b") to "now",
            Regex("(?i)\\bfor the purpose of\\b") to "for",
            Regex("(?i)\\bi wanted to let you know that\\b") to "",
            Regex("(?i)\\bi am writing to inform you that\\b") to "",
            Regex("(?i)\\bas a matter of fact\\b") to "",
            Regex("(?i)\\bneedless to say\\b") to "",
            Regex("(?i)\\bneed to make sure that\\b") to "must",
            Regex("(?i)\\bhas the ability to\\b") to "can"
        )

        for ((regex, rep) in verboseReplacements) {
            text = regex.replace(text, rep)
        }

        return cleanPunctuationAndSpacing(text)
    }

    /**
     * Casual chat formatting: friendly tone with warm emojis.
     */
    fun formatAsCasual(input: String): String {
        var text = formatSmartClean(input)

        // Soften formal sign-offs and phrases
        text = text
            .replace(Regex("(?i)\\bdo not hesitate to contact\\b"), "hit me up")
            .replace(Regex("(?i)\\bat your earliest convenience\\b"), "whenever you can")

        return cleanPunctuationAndSpacing(text)
    }

    /**
     * Clean Verbatim: preserves the user's spoken words faithfully while guaranteeing
     * proper capitalization, spacing, and punctuation.
     */
    fun formatCleanVerbatim(input: String): String {
        return cleanPunctuationAndSpacing(input)
    }

    /**
     * Splits text into logical thoughts or list clauses.
     */
    private fun splitIntoClauses(text: String): List<String> {
        val splitRegex = Regex(
            "[.!?\\n]+|\\b(?:first|then|next|second|third|fourth|finally|additionally|also|point one|point two)\\b\\s*,?"
        )
        return text.split(splitRegex)
            .map { it.trim().trimStart('•', '-', '1', '2', '3', '4', '5', '.', ' ') }
            .filter { it.length > 2 }
    }

    /**
     * Standardizes whitespace and punctuation.
     */
    fun cleanPunctuationAndSpacing(input: String): String {
        var s = input.replace(Regex("[ \\t]+"), " ").trim()

        // Clean up spaces before punctuation marks
        s = s.replace(Regex("\\s+([.,!?;:])"), "$1")

        // Ensure space after punctuation (except if followed by newline, digit, or already end)
        s = s.replace(Regex("([.,!?;:])([a-zA-Z])"), "$1 $2")

        // Fix interior spacing for quotes: " hello " -> "hello"
        s = s.replace(Regex("\"\\s+([^\"]+?)\\s+\""), "\"$1\"")

        // Fix interior spacing for parentheses: ( hello ) -> (hello)
        s = s.replace(Regex("\\(\\s+([^()]+?)\\s+\\)"), "($1)")

        if (s.isEmpty()) return ""

        // Sentence-case formatting
        val sb = StringBuilder()
        var capitalizeNext = true
        for (i in s.indices) {
            val c = s[i]
            if (capitalizeNext && c.isLetter()) {
                sb.append(c.uppercaseChar())
                capitalizeNext = false
            } else {
                sb.append(c)
                if (c == '.' || c == '!' || c == '?' || c == '\n') {
                    capitalizeNext = true
                }
            }
        }

        var result = sb.toString().trim()

        // Append trailing period if not already punctuated or formatted as a list
        val isList = result.contains("\n• ") || result.contains("\n1. ") || result.contains("☐ ") || result.startsWith("• ") || result.startsWith("1. ") || result.startsWith("☐ ")
        if (!isList && !result.endsWith(".") && !result.endsWith("!") && !result.endsWith("?") &&
            !result.endsWith("•") && !result.endsWith("\"") && !result.endsWith(")")
        ) {
            result += "."
        }

        return result
    }
}
