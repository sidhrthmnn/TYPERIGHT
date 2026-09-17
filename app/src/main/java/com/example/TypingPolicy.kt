package com.example

import java.util.Locale

/** Small, deterministic rules shared by the asynchronous decoder and immediate commit path. */
object TypingPolicy {
    // Deliberately excludes real words (were, well, ill, its) and conversational slang.
    private val unambiguousCorrections = mapOf(
        "teh" to "the", "hte" to "the", "adn" to "and", "nad" to "and",
        "taht" to "that", "thta" to "that", "thsi" to "this", "tihs" to "this",
        "wiht" to "with", "wih" to "with", "whcih" to "which", "thid" to "this",
        "goodmorning" to "good morning",
        "helo" to "hello", "helllo" to "hello", "pleas" to "please", "plese" to "please",
        "becuase" to "because", "beacuse" to "because", "becase" to "because",
        "recieve" to "receive", "recieved" to "received", "receieve" to "receive",
        "definately" to "definitely", "defintely" to "definitely",
        "tommorow" to "tomorrow", "tomorow" to "tomorrow", "tommorrow" to "tomorrow",
        "freind" to "friend", "freinds" to "friends", "woudl" to "would",
        "coudl" to "could", "shoudl" to "should", "yuo" to "you", "oyu" to "you",
        "dont" to "don't", "doesnt" to "doesn't", "didnt" to "didn't",
        "cant" to "can't", "couldnt" to "couldn't", "wouldnt" to "wouldn't",
        "shouldnt" to "shouldn't", "isnt" to "isn't", "arent" to "aren't",
        "wasnt" to "wasn't", "werent" to "weren't", "havent" to "haven't",
        "hasnt" to "hasn't", "hadnt" to "hadn't", "youre" to "you're",
        "theyre" to "they're", "ive" to "I've", "im" to "I'm"
    )

    fun correction(word: String): String? {
        if (word == "i") return "I"
        val replacement = unambiguousCorrections[word.lowercase(Locale.ROOT)] ?: return null
        return restoreCase(word, replacement)
    }

    fun restoreCase(original: String, replacement: String): String = when {
        original.length > 1 && original.all { !it.isLetter() || it.isUpperCase() } -> replacement.uppercase(Locale.ROOT)
        original.firstOrNull()?.isUpperCase() == true -> replacement.replaceFirstChar { it.titlecase(Locale.ROOT) }
        else -> replacement
    }

    fun isWordCharacter(char: Char): Boolean = char.isLetterOrDigit() || char == '\'' || char == '’' ||
        Character.getType(char) == Character.NON_SPACING_MARK.toInt() ||
        Character.getType(char) == Character.COMBINING_SPACING_MARK.toInt()

    fun shouldInsertPeriod(before: String, enabled: Boolean, elapsedMillis: Long): Boolean =
        enabled && elapsedMillis in 1..700 && before.length >= 2 &&
            before.last() == ' ' && before[before.lastIndex - 1].isLetterOrDigit()

    /** Number of UTF-16 units in the last user-visible character, including joined emoji. */
    fun lastCharacterLength(text: String): Int {
        if (text.isEmpty()) return 0
        val breaks = android.icu.text.BreakIterator.getCharacterInstance(Locale.ROOT)
        breaks.setText(text)
        return text.length - breaks.preceding(text.length).coerceAtLeast(0)
    }
}
