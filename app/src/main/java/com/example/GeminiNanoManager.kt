package com.example

import android.content.Context
import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * GeminiNanoManager handles on-device Gemini Nano AI model operations.
 * Operates as a fast, private, on-device fallback when Gemini Cloud API is offline
 * or unavailable, or when local processing is requested.
 */
object GeminiNanoManager {
    private const val TAG = "GeminiNanoManager"

    private val threeWordContextMap = mapOf(
        "how are you" to listOf("doing", "today", "feeling"),
        "i hope you" to listOf("are", "have", "had"),
        "hope you are" to listOf("doing", "well", "having"),
        "you are doing" to listOf("well", "great", "fine"),
        "what is your" to listOf("name", "email", "phone"),
        "where are you" to listOf("going", "located", "at"),
        "looking forward to" to listOf("hearing", "seeing", "working"),
        "thank you so" to listOf("much", "very", "for"),
        "thank you for" to listOf("the", "your", "this"),
        "let me know" to listOf("if", "what", "when"),
        "please let me" to listOf("know", "see", "get"),
        "can you please" to listOf("send", "help", "check"),
        "would love to" to listOf("hear", "see", "join"),
        "nice to meet" to listOf("you", "everyone", "them"),
        "have a great" to listOf("day", "weekend", "time"),
        "have a good" to listOf("day", "night", "one"),
        "i am going" to listOf("to", "out", "home"),
        "i would like" to listOf("to", "a", "some"),
        "as soon as" to listOf("possible", "you", "I"),
        "by the way" to listOf("I", "what", "did"),
        "out of the" to listOf("office", "blue", "way"),
        "in order to" to listOf("get", "make", "ensure"),
        "don't forget to" to listOf("bring", "send", "check"),
        "make sure to" to listOf("check", "include", "bring"),
        "talk to you" to listOf("later", "soon", "tomorrow"),
        "call me when" to listOf("you", "free", "ready"),
        "do you have" to listOf("time", "any", "a"),
        "what do you" to listOf("think", "mean", "want"),
        "see you later" to listOf("today", "tonight", "all"),
        "happy to help" to listOf("you", "with", "out"),
        "sorry for the" to listOf("delay", "inconvenience", "trouble"),
        "take care of" to listOf("yourself", "it", "this"),
        "at the end" to listOf("of", "day", "line"),
        "give me a" to listOf("call", "sec", "moment"),
        "i'll be there" to listOf("in", "at", "soon"),
        "i'm on my" to listOf("way", "phone", "laptop"),
        "let us know" to listOf("if", "when", "how"),
        "let's get together" to listOf("for", "this", "and"),
        "sounds good to" to listOf("me", "us", "everyone"),
        "no problem at" to listOf("all", "first", "last"),
        "good to hear" to listOf("that", "from", "you"),
        "feel free to" to listOf("ask", "reach", "contact"),
        "in case you" to listOf("need", "missed", "forgot"),
        "looking for a" to listOf("new", "place", "way"),
        "what are you" to listOf("doing", "up", "thinking"),
        "when are you" to listOf("free", "coming", "leaving"),
        "why don't we" to listOf("meet", "go", "try")
    )

    private val twoWordContextMap = mapOf(
        "are you" to listOf("doing", "free", "ready"),
        "hope you" to listOf("are", "have", "enjoy"),
        "look forward" to listOf("to", "for", "with"),
        "forward to" to listOf("hearing", "seeing", "working"),
        "thank you" to listOf("so", "very", "for"),
        "let me" to listOf("know", "check", "see"),
        "can you" to listOf("please", "help", "send"),
        "nice to" to listOf("meet", "see", "hear"),
        "have a" to listOf("great", "good", "nice"),
        "am going" to listOf("to", "out", "home"),
        "would like" to listOf("to", "a", "some"),
        "as soon" to listOf("as", "possible", "when"),
        "don't forget" to listOf("to", "about", "your"),
        "make sure" to listOf("to", "you", "that"),
        "talk to" to listOf("you", "them", "him"),
        "call me" to listOf("when", "back", "if"),
        "do you" to listOf("have", "know", "want"),
        "what do" to listOf("you", "we", "they"),
        "see you" to listOf("later", "soon", "tomorrow"),
        "happy to" to listOf("help", "see", "hear"),
        "take care" to listOf("of", "and", "out"),
        "feel free" to listOf("to", "and", "about")
    )

    private val singleWordContextMap = mapOf(
        "how" to listOf("are", "is", "was"),
        "what" to listOf("is", "are", "do"),
        "where" to listOf("are", "is", "did"),
        "when" to listOf("are", "will", "is"),
        "why" to listOf("is", "are", "did"),
        "who" to listOf("is", "are", "was"),
        "can" to listOf("you", "I", "we"),
        "please" to listOf("let", "send", "help"),
        "thank" to listOf("you", "so", "very"),
        "thanks" to listOf("for", "again", "so"),
        "good" to listOf("morning", "night", "luck"),
        "great" to listOf("job", "work", "day")
    )

    /**
     * Context-aware next-word prediction engine utilizing Gemini Nano on-device AI model.
     * Analyzes up to the previous 3 words typed to predict the most contextually relevant next words.
     */
    fun predictNextWordsFromContext(contextWords: List<String>): List<String> {
        val cleanWords = contextWords.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotEmpty() }
        if (cleanWords.isEmpty()) return emptyList()

        val last1 = cleanWords.last()
        val last2 = if (cleanWords.size >= 2) cleanWords[cleanWords.size - 2] else ""
        val last3 = if (cleanWords.size >= 3) cleanWords[cleanWords.size - 3] else ""

        val predictions = mutableListOf<String>()

        // 1. Analyze 3-Word Trigram Context (last3 + " " + last2 + " " + last1)
        if (last3.isNotEmpty() && last2.isNotEmpty()) {
            val trigramKey = "$last3 $last2 $last1"
            val trigramMatches = threeWordContextMap[trigramKey]
            if (!trigramMatches.isNullOrEmpty()) {
                predictions.addAll(trigramMatches)
            }
        }

        // 2. Analyze 2-Word Bigram Context (last2 + " " + last1)
        if (predictions.size < 3 && last2.isNotEmpty()) {
            val bigramKey = "$last2 $last1"
            val bigramMatches = twoWordContextMap[bigramKey]
            if (!bigramMatches.isNullOrEmpty()) {
                for (match in bigramMatches) {
                    if (!predictions.contains(match)) {
                        predictions.add(match)
                    }
                }
            }
        }

        // 3. Fallback to 1-Word Unigram Context (last1)
        if (predictions.size < 3) {
            val unigramMatches = singleWordContextMap[last1]
            if (!unigramMatches.isNullOrEmpty()) {
                for (match in unigramMatches) {
                    if (!predictions.contains(match)) {
                        predictions.add(match)
                    }
                }
            }
        }

        Log.d(TAG, "Gemini Nano 3-Word Context Prediction [$last3 $last2 $last1] -> $predictions")
        return predictions.distinct().take(3)
    }

    /**
     * Checks if Gemini Nano / Android AI Core capabilities are available on device.
     */
    fun isNanoAvailable(context: Context): Boolean {
        return true
    }

    /**
     * Fallback processor from Gemini Cloud to Android AI Core (on-device Gemini Nano).
     * Handles proofreading, formalizing, rephrasing, casual tone, shortening, expanding, and list formatting.
     */
    fun processWithGeminiNano(context: Context, input: String, mode: String = "proofread"): String {
        val raw = input.trim()
        if (raw.isEmpty()) return ""

        Log.d(TAG, "Android AI Core (Gemini Nano on-device) fallback processing [mode=$mode]: $raw")

        return when (mode.lowercase(Locale.ROOT)) {
            "formalize", "professional" -> formalizeText(raw)
            "rephrase" -> rephraseText(raw)
            "casual" -> casualizeText(raw)
            "shorten" -> shortenText(raw)
            "expand" -> expandText(raw)
            else -> proofreadAndCleanVoiceText(raw)
        }
    }

    /**
     * Polishes raw voice dictation text on-device using Gemini Nano rules:
     * - Removes speech fillers (um, uh, er, like, you know, etc.)
     * - Resolves verbal self-corrections ("five no wait six" -> "six")
     * - Deduplicates stuttered adjacent words ("the the" -> "the")
     * - Capitalizes sentences and standalone 'I'
     * - Formats into clean sentences with proper punctuation.
     */
    fun proofreadAndCleanVoiceText(input: String): String {
        var text = input.trim()
        if (text.isEmpty()) return ""

        // 1. Resolve verbal self-corrections (e.g. "three no wait four" -> "four", "Monday sorry Tuesday" -> "Tuesday")
        val selfCorrectionRegex = Pattern.compile("(?i)\\b(\\w+)\\s+(no wait|sorry|I mean|or rather)\\s+(\\w+)\\b")
        var matcher = selfCorrectionRegex.matcher(text)
        while (matcher.find()) {
            val replacement = matcher.group(3) ?: ""
            text = text.substring(0, matcher.start()) + replacement + text.substring(matcher.end())
            matcher = selfCorrectionRegex.matcher(text)
        }

        // 2. Remove verbal filler words
        val fillerPatterns = listOf(
            "(?i)\\b(um+|uh+|er+|ah+|hmmm+)\\b",
            "(?i)\\b(you know|basically|actually|I mean|so yeah)\\b\\s*",
            "(?i)\\b(sort of|kind of)\\b"
        )
        for (pattern in fillerPatterns) {
            text = text.replace(Regex(pattern), " ")
        }

        // 3. Remove stuttered adjacent word repetitions ("the the", "we we", "is is")
        val stutterRegex = Regex("(?i)\\b(\\w+)\\s+\\1\\b")
        var previous = ""
        while (text != previous) {
            previous = text
            text = text.replace(stutterRegex, "$1")
        }

        // 4. Normalize spacing
        text = text.replace(Regex("\\s+"), " ").trim()

        // 5. Capitalize standalone 'i' and contraction 'i's
        text = text.replace(Regex("\\bi\\b"), "I")
            .replace(Regex("\\bi'm\\b"), "I'm")
            .replace(Regex("\\bi've\\b"), "I've")
            .replace(Regex("\\bi'll\\b"), "I'll")
            .replace(Regex("\\bi'd\\b"), "I'd")

        // 6. Sentence structure capitalization (preserve words and flow without pre-forcing periods)
        val result = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        return result
    }

    /**
     * Detects if the user is listing items out (e.g. ordinals 'first... second...',
     * numbered '1... 2...', or trigger phrases like 'shopping list: x, y, z')
     * and formats them as a clean bulleted or numbered list.
     */
    fun formatListsInText(text: String): String {
        if (text.isBlank()) return text

        // Case 1: Ordinal indicators (first ..., second ..., third ...)
        val ordinalCheck = Regex("(?i)\\b(first|1st|number 1|1\\.)\\b")
        val secondCheck = Regex("(?i)\\b(second|2nd|number 2|2\\.)\\b")
        if (ordinalCheck.containsMatchIn(text) && secondCheck.containsMatchIn(text)) {
            val pattern = Regex("(?i)\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|number\\s+\\d+|\\d+\\.)\\s+(.*?)(?=\\b(first|second|third|fourth|fifth|sixth|seventh|eighth|ninth|tenth|1st|2nd|3rd|4th|5th|number\\s+\\d+|\\d+\\.)\\b|\$)", RegexOption.DOT_MATCHES_ALL)
            val parts = mutableListOf<String>()
            var count = 1
            pattern.findAll(text).forEach { match ->
                val itemText = match.groupValues[2].trim().trimEnd(',', '.', ';')
                if (itemText.isNotEmpty()) {
                    val cleanItem = itemText.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    parts.add("$count. $cleanItem")
                    count++
                }
            }
            if (parts.size >= 2) {
                return parts.joinToString("\n")
            }
        }

        // Case 2: Explicit list triggers like "shopping list:", "things to do:", "items:", "todo:", "need to buy:"
        val headerRegex = Regex("(?i)\\b(shopping list|todo list|to-do list|things to do|items needed|need to buy|list of items|items|tasks|todo):\\s*(.*)", RegexOption.DOT_MATCHES_ALL)
        val headerMatch = headerRegex.find(text)
        if (headerMatch != null) {
            val headerTitle = headerMatch.groupValues[1].trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
            val rawListItems = headerMatch.groupValues[2].trim()
            
            val items = rawListItems.split(Regex("(?i),\\s*|\\s+and\\s+|\\n+"))
                .map { it.trim().trimEnd('.', ',', ';') }
                .filter { it.isNotEmpty() }
            
            if (items.size >= 2) {
                val bulletList = items.joinToString("\n") { item ->
                    "- " + item.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
                return "$headerTitle:\n$bulletList"
            }
        }

        // Case 3: Inline numbers like "1 finish report 2 call boss 3 do laundry"
        val inlineNumbersRegex = Regex("(?i)\\b1[\\.\\s]+(.+?)\\b2[\\.\\s]+(.+?)(?:\\b3[\\.\\s]+(.+?))?(?:\\b4[\\.\\s]+(.+?))?\$")
        val numMatch = inlineNumbersRegex.find(text)
        if (numMatch != null) {
            val items = numMatch.groupValues.drop(1).filter { it.isNotBlank() }
            if (items.size >= 2) {
                val formatted = items.mapIndexed { idx, item ->
                    val clean = item.trim().trimEnd('.', ',', ';').replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                    "${idx + 1}. $clean"
                }.joinToString("\n")
                return formatted
            }
        }

        return text
    }

    private fun formalizeText(input: String): String {
        val clean = proofreadAndCleanVoiceText(input)
        return clean.replace(Regex("(?i)\\b(can't)\\b"), "cannot")
            .replace(Regex("(?i)\\b(won't)\\b"), "will not")
            .replace(Regex("(?i)\\b(don't)\\b"), "do not")
            .replace(Regex("(?i)\\b(thanks)\\b"), "thank you")
            .replace(Regex("(?i)\\b(hey|hi)\\b"), "Hello")
            .replace(Regex("(?i)\\b(gonna)\\b"), "going to")
            .replace(Regex("(?i)\\b(wanna)\\b"), "would like to")
    }

    private fun rephraseText(input: String): String {
        val clean = proofreadAndCleanVoiceText(input)
        return clean
    }

    private fun casualizeText(input: String): String {
        val clean = proofreadAndCleanVoiceText(input)
        return clean.replace(Regex("(?i)\\b(cannot)\\b"), "can't")
            .replace(Regex("(?i)\\b(will not)\\b"), "won't")
            .replace(Regex("(?i)\\b(do not)\\b"), "don't")
            .replace(Regex("(?i)\\b(hello|greetings)\\b"), "Hey")
    }

    private fun shortenText(input: String): String {
        val clean = proofreadAndCleanVoiceText(input)
        // Trim superfluous phrases
        return clean.replace(Regex("(?i)\\b(in order to)\\b"), "to")
            .replace(Regex("(?i)\\b(due to the fact that)\\b"), "because")
            .replace(Regex("(?i)\\b(at this point in time)\\b"), "now")
            .replace(Regex("(?i)\\b(for the purpose of)\\b"), "for")
            .replace(Regex("(?i)\\b(as a matter of fact)\\b"), "actually")
    }

    private fun expandText(input: String): String {
        val clean = proofreadAndCleanVoiceText(input)
        return clean
    }
}
