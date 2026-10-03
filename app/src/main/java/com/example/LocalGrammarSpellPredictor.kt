package com.example

import android.content.Context

/** Phrase proposals and explicit local proofreading, using the shared ranked correction policy. */
class LocalGrammarSpellPredictor(private val context: Context) {

    private val dictionaryManager by lazy { DictionaryManager.getInstance(context) }
    companion object {
        fun contextCandidates(word: String, previous: List<String>, following: List<String> = emptyList()): List<String> {
            val last = previous.lastOrNull().orEmpty()
            return when {
                word == "sea" && last in setOf("will", "can", "would", "could", "should") && previous.size >= 2 && previous[previous.size-2] in setOf("i", "you", "we", "they", "he", "she", "it") -> listOf("see")
                word == "their" && last == "over" && following.firstOrNull() in setOf("now", "today", "tomorrow", "soon") -> listOf("there")
                word == "there" && last in setOf("with", "in", "at", "of", "for") && following.firstOrNull() in setOf("house", "home", "office", "car", "family", "friends", "team") -> listOf("their")
                word == "has" && last in setOf("i", "you", "we", "they") -> listOf("have")
                word == "have" && last in setOf("he", "she", "it") -> listOf("has")
                word == "was" && last in setOf("you", "we", "they") -> listOf("were")
                word == "were" && last in setOf("i", "he", "she", "it") -> listOf("was")
                else -> emptyList()
            }
        }
    }

    private val trigramPhraseMap: Map<String, List<String>> = mapOf(
        // "let me know"
        "let:me:know" to listOf("if you need anything", "if you have any questions", "what you think", "when you're free", "if that works for you", "how it goes"),
        "please:let:me" to listOf("know if you have questions", "know what you think", "know if this works", "know your thoughts", "know when you arrive"),
        "let:us:know" to listOf("if you have any questions", "what you decide", "if you need help", "your availability"),

        // "looking forward to" / "look forward to"
        "looking:forward:to" to listOf("hearing from you", "meeting with you", "seeing you soon", "our conversation", "working together", "the event"),
        "look:forward:to" to listOf("hearing from you", "meeting with you", "seeing you soon", "our discussion", "working with you"),
        "i:am:looking" to listOf("forward to hearing from you", "forward to our meeting", "into this right now", "for a solution"),
        "i'm:looking:forward" to listOf("to hearing from you", "to seeing you soon", "to our meeting tomorrow", "to working together"),

        // "thank you so" / "thank you for" / "thanks for the"
        "thank:you:so" to listOf("much for your help", "much for reaching out", "much for your time", "much for everything", "much for the update"),
        "thank:you:for" to listOf("your quick response", "your time and help", "letting me know", "reaching out to me", "the information"),
        "thanks:for:the" to listOf("quick response", "update on this", "help and support", "great feedback", "heads up"),
        "thanks:so:much" to listOf("for your help", "for the update", "for reaching out", "for everything"),

        // "hope you are" / "hope you have" / "hope this email"
        "hope:you:are" to listOf("doing well and having a great day", "having a wonderful week", "having a great day", "doing well today", "enjoying your weekend"),
        "hope:you:have" to listOf("a wonderful day ahead", "a great weekend", "a safe trip", "a productive day", "a fantastic time"),
        "i:hope:you" to listOf("are doing well today", "have a wonderful day", "had a great weekend", "are feeling better"),
        "i:hope:this" to listOf("email finds you well", "message finds you well", "helps clarify things", "makes sense"),
        "hope:this:email" to listOf("finds you well and healthy", "finds you doing great", "helps with your project"),
        "hope:this:message" to listOf("finds you well", "is helpful for you"),

        // "as soon as" / "at your earliest"
        "as:soon:as" to listOf("possible", "you get a chance", "you are available", "you can", "you arrive"),
        "at:your:earliest" to listOf("convenience", "convenience please"),

        // "feel free to" / "don't hesitate to"
        "feel:free:to" to listOf("reach out anytime", "ask any questions", "contact me if needed", "let me know if you need help", "call me"),
        "don't:hesitate:to" to listOf("reach out if you have questions", "contact me anytime", "ask if you need anything", "let me know"),
        "do:not:hesitate" to listOf("to reach out anytime", "to contact me if needed", "to ask questions"),

        // "i would like" / "i would love"
        "i:would:like" to listOf("to follow up on this", "to know more about this", "to schedule a meeting", "to thank you for your help", "to confirm"),
        "i:would:love" to listOf("to hear your thoughts", "to catch up with you", "to join you for this", "to help you with this"),
        "would:you:like" to listOf("to meet up later", "to discuss this further", "me to help with that", "to join us"),
        "would:you:be" to listOf("available for a call", "able to help with this", "interested in meeting", "free tomorrow"),

        // "i will be" / "i will let"
        "i:will:be" to listOf("there in a few minutes", "available tomorrow morning", "happy to help with this", "right back", "out of office"),
        "i:will:let" to listOf("you know as soon as possible", "you know tomorrow", "you know what happens", "you know when I arrive"),
        "i:will:get" to listOf("back to you shortly", "right on it", "this done today"),

        // "do you have" / "can you please" / "could you please"
        "do:you:have" to listOf("time for a quick call", "any questions about this", "a minute to talk", "any availability this week", "the latest updates"),
        "do:you:know" to listOf("what time the meeting is", "if this is ready", "where we are meeting", "how this works"),
        "do:you:want" to listOf("to meet up today", "to discuss this now", "me to send the file"),
        "can:you:please" to listOf("send me the details", "let me know when you're free", "confirm if this works", "take a look at this", "help me with this"),
        "could:you:please" to listOf("provide more details", "let me know your thoughts", "send over the information", "confirm the time", "assist with this"),

        // "sorry for the" / "it was great" / "nice to meet"
        "sorry:for:the" to listOf("delay in getting back to you", "late response on this", "confusion earlier", "inconvenience caused", "trouble"),
        "apologize:for:the" to listOf("delay in replying", "inconvenience caused", "late response"),
        "it:was:great" to listOf("talking to you earlier", "meeting with you today", "catching up with you", "seeing you again", "speaking with you"),
        "it:was:a" to listOf("pleasure meeting you", "pleasure speaking with you", "great experience"),
        "nice:to:meet" to listOf("you in person", "you yesterday", "you as well"),
        "great:to:meet" to listOf("you today", "you yesterday", "you as well"),

        // "have a great" / "have a good" / "have a wonderful"
        "have:a:great" to listOf("rest of your day", "weekend ahead", "time at the event", "day ahead", "week"),
        "have:a:good" to listOf("one and take care", "day ahead", "time tomorrow", "night", "weekend"),
        "have:a:wonderful" to listOf("day ahead", "weekend with family", "time", "week ahead"),

        // "just wanted to" / "wanted to follow"
        "just:wanted:to" to listOf("check in with you", "follow up on our conversation", "say thank you for your help", "let you know that", "say hello"),
        "wanted:to:follow" to listOf("up on our discussion", "up regarding the project", "up with you today", "up on the email"),

        // "in case you" / "if you have" / "if you need"
        "in:case:you" to listOf("need anything else", "haven't seen this yet", "have any questions", "were wondering"),
        "if:you:have" to listOf("any questions please let me know", "any thoughts on this", "time for a quick chat", "a moment to talk"),
        "if:you:need" to listOf("any further assistance", "more information let me know", "any help with this"),
        "if:there:is" to listOf("anything else I can help with", "any update on this", "a better time to meet"),

        // "talk to you" / "see you soon" / "take care and"
        "talk:to:you" to listOf("later today", "soon and take care", "tomorrow morning", "next week"),
        "see:you:all" to listOf("tomorrow morning", "at the meeting", "there soon"),
        "see:you:tomorrow" to listOf("morning at the office", "at the same time", "for our meeting"),
        "take:care:and" to listOf("have a great day", "talk to you soon", "stay safe", "enjoy your weekend"),

        // "what do you" / "how is it" / "sounds like a" / "by the way"
        "what:do:you" to listOf("think about this idea", "want to do next", "recommend we do", "think of this"),
        "how:is:it" to listOf("going with the project", "going today", "looking for tomorrow"),
        "how:about:we" to listOf("meet tomorrow instead", "discuss this over a call", "catch up later this week"),
        "sounds:like:a" to listOf("great plan to me", "good idea to pursue", "solid plan", "great opportunity"),
        "sounds:good:to" to listOf("me let's do that", "me see you then", "me looking forward to it"),
        "by:the:way" to listOf("did you get a chance to see", "I wanted to mention that", "how did the meeting go"),
        "in:the:meantime" to listOf("please let me know", "feel free to reach out", "I will work on this"),
        "to:the:best" to listOf("of my knowledge", "of our ability"),
        "please:find:attached" to listOf("the requested document", "the updated file", "my resume for review", "the project report"),
        "i:am:writing" to listOf("to inquire about the position", "to follow up on my previous message", "to confirm our appointment")
    )

    private val bigramPhraseMap: Map<String, List<String>> = mapOf(
        "let:me" to listOf("know if you need anything", "know what you think", "check on this for you", "know if that works"),
        "thank:you" to listOf("so much for your help", "very much for your time", "for letting me know", "for the quick response"),
        "looking:forward" to listOf("to hearing from you", "to seeing you soon", "to our meeting", "to working together"),
        "look:forward" to listOf("to hearing from you", "to seeing you soon", "to working with you"),
        "feel:free" to listOf("to reach out anytime", "to ask any questions", "to contact me if needed"),
        "as:soon" to listOf("as possible", "as you can", "as you are ready"),
        "hope:you" to listOf("are doing well", "have a great day", "had a good weekend", "are having a wonderful week"),
        "please:let" to listOf("me know if that works", "us know what you think", "me know your thoughts"),
        "sorry:for" to listOf("the delay in responding", "the late reply", "the confusion earlier"),
        "nice:to" to listOf("meet you in person", "hear from you again", "see you today"),
        "have:a" to listOf("great rest of your day", "wonderful weekend", "safe trip", "great time"),
        "take:care" to listOf("and talk to you soon", "and have a great day", "and stay safe"),
        "see:you" to listOf("later today", "soon and take care", "tomorrow morning"),
        "on:my" to listOf("way right now", "way over there"),
        "would:you" to listOf("like to join us", "be available for a call", "mind taking a look"),
        "can:you" to listOf("please send me the details", "let me know when you're free", "give me a quick call"),
        "could:you" to listOf("please send over the file", "let me know if this works", "provide more details")
    )

    fun predictPhraseCompletions(
        previousWords: List<String>,
        prefix: String = "",
        maxResults: Int = 4
    ): List<String> {
        val cleanPrefix = prefix.lowercase().trim()
        val tokens = previousWords.map { it.lowercase().trim().replace(Regex("[^a-z']"), "") }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()

        val results = LinkedHashSet<String>()

        // 1. Primary Signal: Preceding THREE words trigram trigger (e.g. "let:me:know")
        if (tokens.size >= 3) {
            val p3 = tokens[tokens.size - 3]
            val p2 = tokens[tokens.size - 2]
            val p1 = tokens[tokens.size - 1]
            val triKey = "$p3:$p2:$p1"

            trigramPhraseMap[triKey]?.let { phrases ->
                for (phrase in phrases) {
                    if (cleanPrefix.isEmpty() || phrase.lowercase().startsWith(cleanPrefix)) {
                        results.add(phrase)
                    }
                }
            }
        }

        // 2. Secondary Signal: Preceding TWO words bigram trigger (e.g. "thank:you")
        if (tokens.size >= 2) {
            val p2 = tokens[tokens.size - 2]
            val p1 = tokens[tokens.size - 1]
            val biKey = "$p2:$p1"

            bigramPhraseMap[biKey]?.let { phrases ->
                for (phrase in phrases) {
                    if (cleanPrefix.isEmpty() || phrase.lowercase().startsWith(cleanPrefix)) {
                        results.add(phrase)
                    }
                }
            }
        }

        return (results + dictionaryManager.nGramModel.predictNextPhrases(previousWords.takeLast(5), maxResults)).distinct().filter { it.startsWith(cleanPrefix) }.take(maxResults)
    }

    fun polishSentenceLocally(sentence: String): String {
        if (sentence.isBlank()) return sentence
        if (sentence.contains("\n")) {
            return sentence.split("\n").joinToString("\n") { line ->
                if (line.isBlank()) line else polishSingleSentenceLocally(line)
            }
        }
        return polishSingleSentenceLocally(sentence)
    }

    private fun polishSingleSentenceLocally(sentence: String): String {
        val matches=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*").findAll(sentence).toList()
        val previous=mutableListOf<String>()
        var index=0
        val output=Regex("[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)*").replace(sentence) { match ->
            val following=matches.drop(++index).take(2).map { it.value.lowercase() }
            val ranked=dictionaryManager.correctionPipeline.rank(match.value,previous,following=following)
            val corrected=ranked.automatic ?: match.value
            previous.add(corrected.lowercase()); if(previous.size>5) previous.removeAt(0)
            corrected
        }
        return if(output.firstOrNull()?.isLowerCase()==true) output.replaceFirstChar { it.uppercase() } else output
    }
}
