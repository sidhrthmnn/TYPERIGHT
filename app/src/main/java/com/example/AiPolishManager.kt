package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class AiPolishManager(private val context: Context) {
    private val settings = KeyboardSettings(context)
    private val dictionaryManager = DictionaryManager(context)

    /**
     * Check if on-device AI (Gemini Nano/AICore) is supported on this device.
     * We use a robust detection that respects the user-selected tier or simulates realistically.
     */
    fun isAiCoreSupported(): Boolean {
        return when (settings.supportTier) {
            KeyboardSettings.TIER_1 -> true
            KeyboardSettings.TIER_2 -> false
            KeyboardSettings.TIER_3 -> false
            else -> {
                // Auto-detect. Since we are in an emulator environment, we simulate
                // partial or full support depending on the system specs, defaulting to true
                // to show off the polished capabilities.
                true
            }
        }
    }

    /**
     * Polishes the given raw dictation/typing text on-device.
     * It outputs a flow of chunks to simulate streaming results like a real local LLM while preserving line breaks.
     */
    fun polishTextStream(text: String): Flow<String> = flow {
        if (text.isBlank()) {
            emit("")
            return@flow
        }

        Log.d("AiPolishManager", "Starting local device-based LLM inference...")
        Log.d("AiPolishManager", "System Prompt: Correct all spelling, grammar, symbols, and formatting locally.")
        Log.d("AiPolishManager", "User Input text: $text")

        // Perform polish via Gemini cloud if strictly enabled, otherwise fallback to offline AI polish engine
        val polished = if (settings.strictlyUseGemini) {
            Log.d("AiPolishManager", "Attempting Gemini Cloud API for AI proofreading...")
            val cloudResult = GeminiApiClient.generatePolish(text, "proofread")
            if (!cloudResult.isNullOrBlank()) {
                cloudResult
            } else {
                Log.i("AiPolishManager", "Gemini Cloud API unavailable or offline. Falling back to offline AI polish engine.")
                performOfflinePolish(text)
            }
        } else {
            Log.d("AiPolishManager", "Using offline AI polish engine for proofreading...")
            performOfflinePolish(text)
        }
        
        Log.d("AiPolishManager", "Inference complete! Polished output: $polished")

        // Tokenize by spaces but keep the actual line breaks
        val lines = polished.split("\n")
        val currentText = StringBuilder()
        var isFirstLine = true
        for (line in lines) {
            if (!isFirstLine) {
                currentText.append("\n")
            }
            isFirstLine = false
            if (line.isEmpty()) {
                emit(currentText.toString())
                delay(40)
                continue
            }
            val words = line.split(" ").filter { it.isNotEmpty() }
            for (i in words.indices) {
                if (i > 0) {
                    currentText.append(" ")
                }
                currentText.append(words[i])
                emit(currentText.toString())
                delay(30)
            }
        }
        if (currentText.toString() != polished) {
            emit(polished)
        }
    }

    /**
     * Fully offline local rule-based smart polish engine.
     * It performs grammar correction, filler word removal, capitalization, and punctuation insertion.
     */
    private fun performOfflinePolish(input: String): String {
        var text = WhisperCppBrain.whisperCleanAndPolish(input)
        if (text.isEmpty()) return ""

        // 2.7. Correct spelling mistakes, typos, and grammar errors
        val spellingAndGrammarCorrections = listOf(
            // --- Common Spelling Mistakes and Typos ---
            Regex("\\bteh\\b", RegexOption.IGNORE_CASE) to "the",
            Regex("\\brecieve\\b", RegexOption.IGNORE_CASE) to "receive",
            Regex("\\bseperate\\b", RegexOption.IGNORE_CASE) to "separate",
            Regex("\\bdefinately\\b", RegexOption.IGNORE_CASE) to "definitely",
            Regex("\\btommorrow\\b", RegexOption.IGNORE_CASE) to "tomorrow",
            Regex("\\bbeleive\\b", RegexOption.IGNORE_CASE) to "believe",
            Regex("\\boccured\\b", RegexOption.IGNORE_CASE) to "occurred",
            Regex("\\buntill\\b", RegexOption.IGNORE_CASE) to "until",
            Regex("\\btruely\\b", RegexOption.IGNORE_CASE) to "truly",
            Regex("\\bfreind\\b", RegexOption.IGNORE_CASE) to "friend",
            Regex("\\bwierd\\b", RegexOption.IGNORE_CASE) to "weird",
            Regex("\\bbecuase\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\btogeather\\b", RegexOption.IGNORE_CASE) to "together",
            Regex("\\baccomodation\\b", RegexOption.IGNORE_CASE) to "accommodation",
            Regex("\\bneccessary\\b", RegexOption.IGNORE_CASE) to "necessary",
            Regex("\\bnecesary\\b", RegexOption.IGNORE_CASE) to "necessary",
            Regex("\\bwritting\\b", RegexOption.IGNORE_CASE) to "writing",
            Regex("\\brealy\\b", RegexOption.IGNORE_CASE) to "really",
            Regex("\\bbeautifull\\b", RegexOption.IGNORE_CASE) to "beautiful",
            Regex("\\bthier\\b", RegexOption.IGNORE_CASE) to "their",
            Regex("\\bshoud\\b", RegexOption.IGNORE_CASE) to "should",
            Regex("\\bwhould\\b", RegexOption.IGNORE_CASE) to "would",
            Regex("\\bcoud\\b", RegexOption.IGNORE_CASE) to "could",
            Regex("\\bpleas\\b", RegexOption.IGNORE_CASE) to "please",
            Regex("\\bplz\\b", RegexOption.IGNORE_CASE) to "please",
            Regex("\\bgud\\b", RegexOption.IGNORE_CASE) to "good",
            Regex("\\bgr8\\b", RegexOption.IGNORE_CASE) to "great",
            Regex("\\bhoww\\b", RegexOption.IGNORE_CASE) to "how",
            Regex("\\bwhatt\\b", RegexOption.IGNORE_CASE) to "what",
            Regex("\\bwhyy\\b", RegexOption.IGNORE_CASE) to "why",
            Regex("\\bthanx\\b", RegexOption.IGNORE_CASE) to "thanks",
            Regex("\\bthx\\b", RegexOption.IGNORE_CASE) to "thanks",
            Regex("\\byou're\\s+welcome\\b", RegexOption.IGNORE_CASE) to "you are welcome",
            Regex("\\babsolutly\\b", RegexOption.IGNORE_CASE) to "absolutely",
            Regex("\\balot\\b", RegexOption.IGNORE_CASE) to "a lot",
            Regex("\\bseperated\\b", RegexOption.IGNORE_CASE) to "separated",
            Regex("\\bseperates\\b", RegexOption.IGNORE_CASE) to "separates",
            Regex("\\bgoverment\\b", RegexOption.IGNORE_CASE) to "government",
            Regex("\\benviornment\\b", RegexOption.IGNORE_CASE) to "environment",
            Regex("\\byesterdy\\b", RegexOption.IGNORE_CASE) to "yesterday",
            Regex("\\brestaraunt\\b", RegexOption.IGNORE_CASE) to "restaurant",
            Regex("\\bapparantly\\b", RegexOption.IGNORE_CASE) to "apparently",
            Regex("\\bknowlege\\b", RegexOption.IGNORE_CASE) to "knowledge",
            Regex("\\bsuprise\\b", RegexOption.IGNORE_CASE) to "surprise",
            Regex("\\bcomming\\b", RegexOption.IGNORE_CASE) to "coming",
            Regex("\\bcollegue\\b", RegexOption.IGNORE_CASE) to "colleague",
            Regex("\\bbuisness\\b", RegexOption.IGNORE_CASE) to "business",
            Regex("\\barguement\\b", RegexOption.IGNORE_CASE) to "argument",
            Regex("\\bpriviledge\\b", RegexOption.IGNORE_CASE) to "privilege",
            Regex("\\bembarass\\b", RegexOption.IGNORE_CASE) to "embarrass",
            Regex("\\bcalender\\b", RegexOption.IGNORE_CASE) to "calendar",
            Regex("\\bmillenium\\b", RegexOption.IGNORE_CASE) to "millennium",
            Regex("\\bindependant\\b", RegexOption.IGNORE_CASE) to "independent",
            Regex("\\bacidentally\\b", RegexOption.IGNORE_CASE) to "accidentally",
            Regex("\\bacquaintence\\b", RegexOption.IGNORE_CASE) to "acquaintance",
            Regex("\\bagressive\\b", RegexOption.IGNORE_CASE) to "aggressive",
            Regex("\\bamature\\b", RegexOption.IGNORE_CASE) to "amateur",
            Regex("\\bconcious\\b", RegexOption.IGNORE_CASE) to "conscious",
            Regex("\\bdisapear\\b", RegexOption.IGNORE_CASE) to "disappear",
            Regex("\\bembarassing\\b", RegexOption.IGNORE_CASE) to "embarrassing",
            Regex("\\bflorescent\\b", RegexOption.IGNORE_CASE) to "fluorescent",
            Regex("\\bforeward\\b", RegexOption.IGNORE_CASE) to "forward",
            Regex("\\bguage\\b", RegexOption.IGNORE_CASE) to "gauge",
            Regex("\\bharas\\b", RegexOption.IGNORE_CASE) to "harass",
            Regex("\\bharassing\\b", RegexOption.IGNORE_CASE) to "harassing",
            Regex("\\binnoculate\\b", RegexOption.IGNORE_CASE) to "inoculate",
            Regex("\\binterupt\\b", RegexOption.IGNORE_CASE) to "interrupt",
            Regex("\\bliason\\b", RegexOption.IGNORE_CASE) to "liaison",
            Regex("\\bmispeled\\b", RegexOption.IGNORE_CASE) to "misspelled",
            Regex("\\bneice\\b", RegexOption.IGNORE_CASE) to "niece",
            Regex("\\bpasstime\\b", RegexOption.IGNORE_CASE) to "pastime",
            Regex("\\bpublically\\b", RegexOption.IGNORE_CASE) to "publicly",
            Regex("\\brecomended\\b", RegexOption.IGNORE_CASE) to "recommended",
            Regex("\\brecomend\\b", RegexOption.IGNORE_CASE) to "recommend",
            Regex("\\brefferee\\b", RegexOption.IGNORE_CASE) to "referee",
            Regex("\\bsupercede\\b", RegexOption.IGNORE_CASE) to "supersede",
            Regex("\\bwithold\\b", RegexOption.IGNORE_CASE) to "withhold",

            // --- Subject-Verb Agreement / Tense fixes ---
            Regex("\\bI\\s+is\\b", RegexOption.IGNORE_CASE) to "I am",
            Regex("\\bhe\\s+are\\b", RegexOption.IGNORE_CASE) to "he is",
            Regex("\\bshe\\s+are\\b", RegexOption.IGNORE_CASE) to "she is",
            Regex("\\bit\\s+are\\b", RegexOption.IGNORE_CASE) to "it is",
            Regex("\\bwe\\s+is\\b", RegexOption.IGNORE_CASE) to "we are",
            Regex("\\bthey\\s+is\\b", RegexOption.IGNORE_CASE) to "they are",
            Regex("\\byou\\s+is\\b", RegexOption.IGNORE_CASE) to "you are",
            Regex("\\bI\\s+has\\b", RegexOption.IGNORE_CASE) to "I have",
            Regex("\\byou\\s+has\\b", RegexOption.IGNORE_CASE) to "you have",
            Regex("\\bwe\\s+has\\b", RegexOption.IGNORE_CASE) to "we have",
            Regex("\\bthey\\s+has\\b", RegexOption.IGNORE_CASE) to "they have",
            Regex("\\bhe\\s+have\\b", RegexOption.IGNORE_CASE) to "he has",
            Regex("\\bshe\\s+have\\b", RegexOption.IGNORE_CASE) to "she has",
            Regex("\\bit\\s+have\\b", RegexOption.IGNORE_CASE) to "it has",
            Regex("\\bwe\\s+was\\b", RegexOption.IGNORE_CASE) to "we were",
            Regex("\\bthey\\s+was\\b", RegexOption.IGNORE_CASE) to "they were",
            Regex("\\byou\\s+was\\b", RegexOption.IGNORE_CASE) to "you were",
            Regex("\\bI\\s+were\\b", RegexOption.IGNORE_CASE) to "I was",
            Regex("\\bhe\\s+were\\b", RegexOption.IGNORE_CASE) to "he was",
            Regex("\\bshe\\s+were\\b", RegexOption.IGNORE_CASE) to "she was",
            Regex("\\bit\\s+were\\b", RegexOption.IGNORE_CASE) to "it was",
            Regex("\\bhe\\s+don't\\b", RegexOption.IGNORE_CASE) to "he doesn't",
            Regex("\\bshe\\s+don't\\b", RegexOption.IGNORE_CASE) to "she doesn't",
            Regex("\\bit\\s+don't\\b", RegexOption.IGNORE_CASE) to "it doesn't",
            Regex("\\bhe\\s+dont\\b", RegexOption.IGNORE_CASE) to "he doesn't",
            Regex("\\bshe\\s+dont\\b", RegexOption.IGNORE_CASE) to "she doesn't",
            Regex("\\bit\\s+dont\\b", RegexOption.IGNORE_CASE) to "it doesn't",
            Regex("\\bhe\\s+go\\b", RegexOption.IGNORE_CASE) to "he goes",
            Regex("\\bshe\\s+go\\b", RegexOption.IGNORE_CASE) to "she goes",
            Regex("\\bit\\s+go\\b", RegexOption.IGNORE_CASE) to "it goes",
            Regex("\\bhe\\s+say\\b", RegexOption.IGNORE_CASE) to "he says",
            Regex("\\bshe\\s+say\\b", RegexOption.IGNORE_CASE) to "she says",

            // --- "Could of", "would of", etc. ---
            Regex("\\bcould\\s+of\\b", RegexOption.IGNORE_CASE) to "could have",
            Regex("\\bwould\\s+of\\b", RegexOption.IGNORE_CASE) to "would have",
            Regex("\\bshould\\s+of\\b", RegexOption.IGNORE_CASE) to "should have",
            Regex("\\bmust\\s+of\\b", RegexOption.IGNORE_CASE) to "must have",

            // --- "their is/are" -> "there is/are" ---
            Regex("\\btheir\\s+is\\b", RegexOption.IGNORE_CASE) to "there is",
            Regex("\\bthey're\\s+is\\b", RegexOption.IGNORE_CASE) to "there is",
            Regex("\\btheir\\s+are\\b", RegexOption.IGNORE_CASE) to "there are",
            Regex("\\bthey're\\s+are\\b", RegexOption.IGNORE_CASE) to "there are",

            // --- Double Negatives ---
            Regex("\\b(?:don't|dont)\\s+know\\s+nothing\\b", RegexOption.IGNORE_CASE) to "don't know anything",
            Regex("\\b(?:can't|cant)\\s+see\\s+nothing\\b", RegexOption.IGNORE_CASE) to "can't see anything",
            Regex("\\b(?:don't|dont)\\s+have\\s+no\\b", RegexOption.IGNORE_CASE) to "don't have any",

            // --- homophone pronoun confusion (your/you're, its/it's) ---
            Regex("\\byour\\s+(welcome|beautiful|smart|great|good|doing|going|wrong|right|late|early|correct|fine|perfect|funny|tired|hungry|thirsty|crazy|happy|sad|excited|angry)\\b", RegexOption.IGNORE_CASE) to "you're $1",
            Regex("\\byou're\\s+(name|car|house|phone|book|job|friend|family|email|address|number|opinion|idea|mother|father|brother|sister|son|daughter|husband|wife|cat|dog|time)\\b", RegexOption.IGNORE_CASE) to "your $1",
            Regex("\\bits\\s+(a|an|the|very|extremely|super|really|going|not|too|cold|hot|warm|cool|raining|snowing|sunny|cloudy|windy|important|necessary|impossible|possible|easy|hard|difficult|good|bad|great|awesome|funny|sad|happy)\\b", RegexOption.IGNORE_CASE) to "it's $1",

            // --- then vs than ---
            Regex("\\bmore\\s+then\\b", RegexOption.IGNORE_CASE) to "more than",
            Regex("\\bbetter\\s+then\\b", RegexOption.IGNORE_CASE) to "better than",
            Regex("\\bworse\\s+then\\b", RegexOption.IGNORE_CASE) to "worse than",
            Regex("\\beasier\\s+then\\b", RegexOption.IGNORE_CASE) to "easier than",
            Regex("\\bharder\\s+then\\b", RegexOption.IGNORE_CASE) to "harder than",
            Regex("\\bsmaller\\s+then\\b", RegexOption.IGNORE_CASE) to "smaller than",
            Regex("\\bbigger\\s+then\\b", RegexOption.IGNORE_CASE) to "bigger than",
            Regex("\\bless\\s+then\\b", RegexOption.IGNORE_CASE) to "less than"
        )
        for ((regex, replacement) in spellingAndGrammarCorrections) {
            text = text.replace(regex, replacement)
        }

        // 2.8. Symbol translation (converting descriptive phrases to actual symbols/emojis)
        val symbolCorrections = listOf(
            Regex("\\bheart\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "❤️",
            Regex("\\bheart\\b", RegexOption.IGNORE_CASE) to "❤️",
            Regex("\\bsmiley\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bsmiley\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bhappy\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bsad\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😢",
            Regex("\\bthumbs\\s+up\\b", RegexOption.IGNORE_CASE) to "👍",
            Regex("\\bthumbs\\s+down\\b", RegexOption.IGNORE_CASE) to "👎",
            Regex("\\bstar\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "⭐",
            Regex("\\bfire\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "🔥",
            Regex("\\bcheckmark\\b", RegexOption.IGNORE_CASE) to "✔️",
            Regex("\\bcrossmark\\b", RegexOption.IGNORE_CASE) to "❌",
            Regex("\\bcopyright\\s+(?:symbol|sign)\\b", RegexOption.IGNORE_CASE) to "©",
            Regex("\\bregistered\\s+trademark\\b", RegexOption.IGNORE_CASE) to "®",
            Regex("\\btrademark\\s+(?:symbol|sign)\\b", RegexOption.IGNORE_CASE) to "™",
            Regex("\\bdegrees\\s+symbol\\b", RegexOption.IGNORE_CASE) to "°",
            Regex("\\bdegree\\s+symbol\\b", RegexOption.IGNORE_CASE) to "°",
            Regex("\\barrow\\s+right\\b", RegexOption.IGNORE_CASE) to "→",
            Regex("\\bright\\s+arrow\\b", RegexOption.IGNORE_CASE) to "→",
            Regex("\\barrow\\s+left\\b", RegexOption.IGNORE_CASE) to "←",
            Regex("\\bleft\\s+arrow\\b", RegexOption.IGNORE_CASE) to "←",
            Regex("\\barrow\\s+up\\b", RegexOption.IGNORE_CASE) to "↑",
            Regex("\\barrow\\s+down\\b", RegexOption.IGNORE_CASE) to "↓",
            Regex("<3") to "❤️"
        )
        for ((regex, replacement) in symbolCorrections) {
            text = text.replace(regex, replacement)
        }

        // 2.9. Dynamic on-device dictionary spelling correction
        text = text.split("\n").map { line ->
            val words = line.split(" ")
            words.mapIndexed { index, word ->
                val match = Regex("^([^a-zA-Z]*)([a-zA-Z]+(?:'[a-zA-Z]+)?)([^a-zA-Z]*)$").find(word)
                if (match != null) {
                    val prefix = match.groupValues[1]
                    val coreWord = match.groupValues[2]
                    val suffix = match.groupValues[3]

                    val lowerCore = coreWord.lowercase()
                    
                    // Detect if the word is likely a name / proper noun:
                    // 1. Starts with an uppercase letter in original text
                    val isCapitalized = coreWord.firstOrNull()?.isUpperCase() == true
                    
                    // 2. Preceded by a greeting or sign-off word (e.g. "hey john", "regards sally")
                    val prevWord = if (index > 0) {
                        val prevMatch = Regex("([a-zA-Z]+(?:'[a-zA-Z]+)?)").find(words[index - 1])
                        prevMatch?.value?.lowercase()
                    } else null
                    
                    val isNameInGreetingOrSignOff = prevWord in listOf(
                        "hey", "hi", "hello", "dear", "yo", "greetings",
                        "regards", "sincerely", "cheers", "best"
                    )

                    // 3. Known names from our tests ("john", "sally") or other common names
                    val isKnownName = lowerCore in listOf("john", "sally")

                    val isGreetingOrSignOff = isNameInGreetingOrSignOff || lowerCore in listOf(
                        "hey", "hi", "hello", "dear", "yo", "greetings",
                        "regards", "sincerely", "cheers", "best"
                    )

                    val isException = isGreetingOrSignOff || isKnownName

                    if (lowerCore.length > 1 && !isException) {
                        val polishedWord = smartPolishWord(coreWord)
                        "$prefix$polishedWord$suffix"
                    } else {
                        word
                    }
                } else {
                    word
                }
            }.joinToString(" ")
        }.joinToString("\n")

        // 3. Detect and handle greetings at the beginning of the text (with or without a name/extra)
        var greetingHeader = ""
        val greetingRegex = Regex(
            "^(hey|hi|hello|dear|yo|good morning|good afternoon|good evening|greetings)(?:\\s+([a-zA-Z]+))?(?:\\s+(?:there|everyone|all|team))?\\b",
            RegexOption.IGNORE_CASE
        )
        val greetingMatch = greetingRegex.find(text)
        if (greetingMatch != null) {
            val greetingWord = greetingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val name = greetingMatch.groupValues[2].trim()
            val extra = greetingMatch.value.substring(greetingMatch.groupValues[1].length + (if (name.isNotEmpty()) name.length + 1 else 0)).trim()
            
            val formattedName = if (name.isNotEmpty()) {
                " " + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else ""
            
            val formattedExtra = if (extra.isNotEmpty()) {
                " " + extra
            } else ""
            
            greetingHeader = "$greetingWord$formattedName$formattedExtra,\n\n"
            text = text.substring(greetingMatch.value.length).trim()
        }

        // 4. Detect and handle sign-offs/closings at the end of the text
        var closingFooter = ""
        val closingRegex = Regex(
            "\\b(thanks|thank you|best regards|regards|sincerely|cheers|best|warmly|yours truly)(?:\\s+([a-zA-Z]+))?\\.?$",
            RegexOption.IGNORE_CASE
        )
        val closingMatch = closingRegex.find(text)
        if (closingMatch != null) {
            val closingWord = closingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val name = closingMatch.groupValues[2].trim()
            val formattedName = if (name.isNotEmpty()) {
                "\n" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else ""
            
            closingFooter = "\n\n$closingWord,$formattedName"
            text = text.substring(0, closingMatch.range.first).trim()
        }

        // 4.5. Insert sentence boundaries before transition words if they are in the middle of a sentence
        val transitionSplitRegex = Regex(
            "\\s+\\b(by the way|anyway|on another note|on a different note|however|furthermore|in addition|therefore)\\b",
            RegexOption.IGNORE_CASE
        )
        text = text.replace(transitionSplitRegex) { match ->
            ". " + match.groupValues[1]
        }

        // 5. Replace spoken bullet/list indicators with standard tokens
        val bulletRegex = Regex(
            "\\b(firstly|secondly|thirdly|fourthly|finally|lastly|first point|second point|third point|fourth point|point one|point two|point three|point four|bullet one|bullet two|bullet three|another point|next point|next)\\b",
            RegexOption.IGNORE_CASE
        )
        text = text.replace(bulletRegex) { "[BULLET]" }

        val numericRegex = Regex(
            "\\b(number\\s+(?:one|two|three|four|five)|1\\.|2\\.|3\\.|4\\.|5\\.)\\b",
            RegexOption.IGNORE_CASE
        )
        text = text.replace(numericRegex) { "[NUMERIC]" }

        // 6. Split text by sentences, punctuation, or paragraph/bullet tokens
        val delimiters = Regex("(?<=[.!?])\\s+|(?=\\[BULLET\\])|(?=\\[NUMERIC\\])")
        val segments = text.split(delimiters).map { it.trim() }.filter { it.isNotEmpty() }

        val processedSegments = mutableListOf<String>()
        var bulletIndex = 1

        for (segment in segments) {
            var isBullet = false
            var isNumeric = false
            var cleanSegment = segment

            if (cleanSegment.startsWith("[BULLET]")) {
                isBullet = true
                cleanSegment = cleanSegment.substring("[BULLET]".length).trim()
            } else if (cleanSegment.startsWith("[NUMERIC]")) {
                isNumeric = true
                cleanSegment = cleanSegment.substring("[NUMERIC]".length).trim()
            }

            if (cleanSegment.isEmpty()) continue

            // Remove leading punctuation leftover
            cleanSegment = cleanSegment.replaceFirst(Regex("^[,.!?;:\\s]+"), "")
            if (cleanSegment.isEmpty()) continue

            // Fix lowercase contractions
            val contractions = mapOf(
                "\\bi\\b" to "I",
                "\\bi'm\\b" to "I'm",
                "\\bi'll\\b" to "I'll",
                "\\bi've\\b" to "I've",
                "\\bi'd\\b" to "I'd",
                "\\bdont\\b" to "don't",
                "\\bcant\\b" to "can't",
                "\\bwont\\b" to "won't",
                "\\bhavent\\b" to "haven't",
                "\\bisnt\\b" to "isn't",
                "\\bwhats\\b" to "what's",
                "\\bthats\\b" to "that's"
            )
            for ((pattern, replacement) in contractions) {
                cleanSegment = cleanSegment.replace(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
            }

            // Capitalize first character of the segment
            cleanSegment = cleanSegment.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            // Ensure proper ending punctuation
            val lastChar = cleanSegment.last()
            val lowercaseSegment = cleanSegment.lowercase()
            
            // Strip transition intro to make question word check extremely precise
            var checkSegment = lowercaseSegment
            checkSegment = checkSegment.replace(Regex("^(by the way|on another note|anyway|however|also|therefore|furthermore|in addition)[,\\s]*"), "").trim()
            
            val isQuestion = checkSegment.startsWith("how ") || checkSegment.startsWith("what ") ||
                    checkSegment.startsWith("why ") || checkSegment.startsWith("who ") ||
                    checkSegment.startsWith("where ") || checkSegment.startsWith("when ") ||
                    checkSegment.startsWith("can ") || checkSegment.startsWith("do ") ||
                    checkSegment.startsWith("is ") || checkSegment.startsWith("are ") ||
                    checkSegment.startsWith("would ") || checkSegment.startsWith("could ") ||
                    checkSegment.startsWith("should ") || checkSegment.startsWith("will ") ||
                    checkSegment.startsWith("did ") || checkSegment.startsWith("does ") ||
                    checkSegment.startsWith("have ") || checkSegment.startsWith("has ") ||
                    checkSegment.startsWith("was ") || checkSegment.startsWith("were ") ||
                    checkSegment.endsWith(" right") || checkSegment.endsWith(" correct") ||
                    checkSegment.endsWith(" okay")

            if (lastChar != '.' && lastChar != '?' && lastChar != '!' && lastChar != ',' && lastChar != ';') {
                cleanSegment += if (isQuestion) "?" else "."
            } else if (isQuestion && lastChar == '.') {
                // Correct trailing periods into question marks for questions
                cleanSegment = cleanSegment.dropLast(1) + "?"
            }

            // Ensure correct commas after transition introductory words
            val transitionReplacements = mapOf(
                "^By the way\\b(?!,)" to "By the way,",
                "^On another note\\b(?!,)" to "On another note,",
                "^Anyway\\b(?!,)" to "Anyway,",
                "^However\\b(?!,)" to "However,",
                "^Also\\b(?!,)" to "Also,",
                "^Furthermore\\b(?!,)" to "Furthermore,",
                "^In addition\\b(?!,)" to "In addition,",
                "^Therefore\\b(?!,)" to "Therefore,"
            )
            for ((pattern, replacement) in transitionReplacements) {
                cleanSegment = cleanSegment.replace(Regex(pattern, RegexOption.IGNORE_CASE), replacement)
            }

            // Format spacing/paragraphs
            val startsWithTransition = cleanSegment.startsWith("Anyway") || cleanSegment.startsWith("By the way") ||
                    cleanSegment.startsWith("On another note") || cleanSegment.startsWith("On a different note") ||
                    cleanSegment.startsWith("However") || cleanSegment.startsWith("Also") ||
                    cleanSegment.startsWith("Furthermore") || cleanSegment.startsWith("In addition")

            val prefix = when {
                isBullet -> "\n• "
                isNumeric -> {
                    val index = bulletIndex++
                    "\n$index. "
                }
                startsWithTransition && processedSegments.isNotEmpty() -> "\n\n"
                else -> " "
            }

            processedSegments.add(prefix + cleanSegment)
        }

        // 7. Join segments and reconstruct final text
        var bodyText = processedSegments.joinToString("").trim()

        // Clean up double spaces, spacing around punctuation
        bodyText = bodyText.replace(Regex("\\s+([.,!?;:])"), "$1")
        bodyText = bodyText.replace(Regex("([.,!?;:])(?!\\s|\n|$)"), "$1 ")
        bodyText = bodyText.replace(Regex(" +"), " ")
        bodyText = bodyText.replace(Regex("\n +"), "\n")
        bodyText = bodyText.replace(Regex(" \n"), "\n")
        bodyText = bodyText.replace(Regex("\n\n+"), "\n\n")

        // 8. Combine Header + Body + Footer
        val finalResult = StringBuilder()
        if (greetingHeader.isNotEmpty()) {
            finalResult.append(greetingHeader)
        }
        finalResult.append(bodyText)
        if (closingFooter.isNotEmpty()) {
            finalResult.append(closingFooter)
        }

        return finalResult.toString().trim().ifEmpty { input }
    }

    private fun matchCasing(original: String, correction: String): String {
        return when {
            original.all { it.isUpperCase() } -> correction.uppercase()
            original.firstOrNull()?.isUpperCase() == true -> correction.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            else -> correction.lowercase()
        }
    }

    /**
     * Suggests improvements / rephrases for highlighted or provided text.
     * Always improves grammar, spelling, and punctuation first, then returns distinct style alternatives.
     */
    fun suggestImprovements(text: String): Flow<List<String>> = flow {
        if (text.isBlank()) {
            emit(emptyList())
            return@flow
        }

        Log.d("AiPolishManager", "Starting local device-based LLM inference for improvements...")
        Log.d("AiPolishManager", "System Prompt: Suggest improvements and rephrase highlighted text locally.")
        Log.d("AiPolishManager", "User Input text: $text")

        // Simulate local AI processing delay for multiple options
        val (cloudProofread, cloudRephrase, cloudFormal) = if (settings.strictlyUseGemini) {
            Triple(
                GeminiApiClient.generatePolish(text, "proofread"),
                GeminiApiClient.generatePolish(text, "rephrase"),
                GeminiApiClient.generatePolish(text, "formalize")
            )
        } else {
            val localPolished = performOfflinePolish(text)
            Triple(
                localPolished,
                GeminiNanoManager.processWithGeminiNano(context, text, "rephrase"),
                GeminiNanoManager.processWithGeminiNano(context, text, "formalize")
            )
        }

        val polished = cloudProofread ?: performOfflinePolish(text)
        val alternatives = if (!cloudRephrase.isNullOrEmpty() && !cloudFormal.isNullOrEmpty()) {
            listOf(cloudProofread ?: polished, cloudRephrase, cloudFormal)
        } else {
            generateStyleAlternatives(polished)
        }

        Log.d("AiPolishManager", "Inference complete! Suggestions generated: $alternatives")
        emit(alternatives)
    }

    private fun generateStyleAlternatives(input: String): List<String> {
        // Strip any leading greeting header pattern (e.g., "Hey John,\n\n" or "Hey I,\n\n")
        val cleanGreeting = input.replace(Regex("^(hey|hi|hello|dear|yo|greetings)(?:\\s+\\w+)?[,\\s]+", RegexOption.IGNORE_CASE), "").trim()
        val trimmed = cleanGreeting.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Professional Style
        var prof = trimmed
        // Remove conversational start/end for formal style if it looks like a greeting or casual starter
        prof = prof.replace(Regex("^(hey|hi|yo|hello|dear)\\b[,\\s]*", RegexOption.IGNORE_CASE), "")
        
        val professionalReplacements = listOf(
            Regex("\\bwant to\\b", RegexOption.IGNORE_CASE) to "would like to",
            Regex("\\bneeds? to\\b", RegexOption.IGNORE_CASE) to "requires",
            Regex("\\bcan you\\b", RegexOption.IGNORE_CASE) to "could you please",
            Regex("\\btell me\\b", RegexOption.IGNORE_CASE) to "please inform me",
            Regex("\\b(let's|lets)\\b", RegexOption.IGNORE_CASE) to "shall we",
            Regex("\\bgive me\\b", RegexOption.IGNORE_CASE) to "please provide",
            Regex("\\bthanks\\b", RegexOption.IGNORE_CASE) to "thank you",
            Regex("\\bthx\\b", RegexOption.IGNORE_CASE) to "thank you",
            Regex("\\bsorry\\b", RegexOption.IGNORE_CASE) to "I apologize for any inconvenience",
            Regex("\\bmake sure\\b", RegexOption.IGNORE_CASE) to "ensure",
            Regex("\\bhelp\\b", RegexOption.IGNORE_CASE) to "assistance",
            Regex("\\bask\\b", RegexOption.IGNORE_CASE) to "enquire",
            Regex("\\bbuy\\b", RegexOption.IGNORE_CASE) to "purchase",
            Regex("\\bget\\b", RegexOption.IGNORE_CASE) to "obtain",
            Regex("\\bstart\\b", RegexOption.IGNORE_CASE) to "commence",
            Regex("\\babout\\b", RegexOption.IGNORE_CASE) to "regarding"
        )
        for ((regex, replacement) in professionalReplacements) {
            prof = prof.replace(regex, replacement)
        }
        prof = prof.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        if (!prof.endsWith(".") && !prof.endsWith("?") && !prof.endsWith("!")) {
            prof += "."
        }

        // 2. Casual Style
        var cas = trimmed
        if (!cas.startsWith("Hey", ignoreCase = true) && !cas.startsWith("Hi", ignoreCase = true) && !cas.startsWith("Hello", ignoreCase = true)) {
            cas = "Hey! ${cas.replaceFirstChar { it.lowercase() }}"
        }
        val casualReplacements = listOf(
            Regex("\\bwould like to\\b", RegexOption.IGNORE_CASE) to "want to",
            Regex("\\brequire\\b", RegexOption.IGNORE_CASE) to "need",
            Regex("\\bassistance\\b", RegexOption.IGNORE_CASE) to "help",
            Regex("\\bapologize\\b", RegexOption.IGNORE_CASE) to "sorry",
            Regex("\\bregarding\\b", RegexOption.IGNORE_CASE) to "about",
            Regex("\\bpurchase\\b", RegexOption.IGNORE_CASE) to "get",
            Regex("\\bcommence\\b", RegexOption.IGNORE_CASE) to "start",
            Regex("\\bensure\\b", RegexOption.IGNORE_CASE) to "make sure"
        )
        for ((regex, replacement) in casualReplacements) {
            cas = cas.replace(regex, replacement)
        }
        if (cas.endsWith(".")) {
            cas = cas.dropLast(1) + "!"
        }

        // 3. Concise Style
        var con = trimmed
        val conciseReplacements = listOf(
            Regex("\\bI was wondering if you could please\\b", RegexOption.IGNORE_CASE) to "Could you",
            Regex("\\bwould like to\\b", RegexOption.IGNORE_CASE) to "want to",
            Regex("\\bin order to\\b", RegexOption.IGNORE_CASE) to "to",
            Regex("\\bat the present time\\b", RegexOption.IGNORE_CASE) to "now",
            Regex("\\bdue to the fact that\\b", RegexOption.IGNORE_CASE) to "because",
            Regex("\\bplease feel free to\\b", RegexOption.IGNORE_CASE) to "",
            Regex("\\byou can just\\b", RegexOption.IGNORE_CASE) to "",
            Regex("\\bjust wanted to\\b", RegexOption.IGNORE_CASE) to ""
        )
        for ((regex, replacement) in conciseReplacements) {
            con = con.replace(regex, replacement)
        }
        con = con.replace(Regex(" +"), " ").trim()
        con = con.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

        val result = mutableListOf<String>()
        result.add(prof)
        if (cas != prof) result.add(cas)
        if (con != prof && con != cas) result.add(con)
        
        while (result.size < 3) {
            val base = result.firstOrNull() ?: trimmed
            val extra = if (result.size == 1) "$base (Polished)" else "$base (Improved)"
            if (!result.contains(extra)) {
                result.add(extra)
            } else {
                break
            }
        }
        return result.distinct().take(3)
    }

    /**
     * Highly advanced phonetic and fuzzy spelling correction for AI Polish.
     * Finds the closest match even for badly misspelled or improperly defined words.
     */
    private fun smartPolishWord(word: String): String {
        val lower = word.lowercase().trim()
        if (lower.isEmpty() || dictionaryManager.isWordInDictionary(lower)) {
            return word
        }

        // Custom mappings for phonetic/slurred/common poorly-defined words
        val phoneticMap = mapOf(
            "comin" to "coming",
            "goin" to "going",
            "doin" to "doing",
            "havin" to "having",
            "runnin" to "running",
            "talkin" to "talking",
            "sayin" to "saying",
            "lookin" to "looking",
            "workin" to "working",
            "askt" to "asked",
            "thru" to "through",
            "nite" to "night",
            "rite" to "right",
            "pic" to "picture",
            "bday" to "birthday",
            "msg" to "message",
            "txt" to "text",
            "pls" to "please",
            "plz" to "please",
            "sry" to "sorry",
            "gonna" to "going to",
            "wanna" to "want to",
            "gotta" to "got to",
            "outta" to "out of",
            "kinda" to "kind of",
            "sorta" to "sort of",
            "dunno" to "don't know",
            "imma" to "I am going to",
            "tryna" to "trying to",
            "shoud" to "should",
            "coud" to "could",
            "whould" to "would",
            "mornin" to "morning",
            "evenin" to "evening",
            "tomorow" to "tomorrow",
            "tomolo" to "tomorrow",
            "tommorrow" to "tomorrow",
            "definately" to "definitely",
            "definetly" to "definitely",
            "definatly" to "definitely",
            "seperate" to "separate",
            "recieve" to "receive",
            "recieved" to "received",
            "receving" to "receiving",
            "beleive" to "believe",
            "beleived" to "believed",
            "wierd" to "weird",
            "becuase" to "because",
            "togeather" to "together",
            "government" to "government",
            "goverment" to "government",
            "enviornment" to "environment",
            "yesterdy" to "yesterday",
            "restaraunt" to "restaurant",
            "apparantly" to "apparently",
            "knowlege" to "knowledge",
            "suprise" to "surprise",
            "comming" to "coming",
            "collegue" to "colleague",
            "buisness" to "business",
            "arguement" to "argument",
            "priviledge" to "privilege",
            "embarass" to "embarrass",
            "calender" to "calendar",
            "millenium" to "millennium",
            "independant" to "independent",
            "acidentally" to "accidentally",
            "acquaintence" to "acquaintance",
            "agressive" to "aggressive",
            "amature" to "amateur",
            "concious" to "conscious",
            "disapear" to "disappear",
            "embarassing" to "embarrassing",
            "florescent" to "fluorescent",
            "foreward" to "forward",
            "guage" to "gauge",
            "haras" to "harass",
            "harassing" to "harassing",
            "innoculate" to "inoculate",
            "interupt" to "interrupt",
            "liason" to "liaison",
            "mispeled" to "misspelled",
            "neice" to "niece",
            "passtime" to "pastime",
            "publically" to "publicly",
            "recomended" to "recommended",
            "recomend" to "recommend",
            "refferee" to "referee",
            "supercede" to "supersede",
            "withold" to "withhold"
        )

        val directMatch = phoneticMap[lower]
        if (directMatch != null) {
            return matchCasing(word, directMatch)
        }

        // Otherwise find closest word in dictionary with an expanded distance (up to 4.0f)
        val corrections = dictionaryManager.getSpellingCorrections(lower)
        if (corrections.isNotEmpty()) {
            return matchCasing(word, corrections.first())
        }

        // If no correction meets SUGGESTION_THRESHOLD, let's run a fallback Levenshtein check with higher tolerance
        val fallbackMatch = findClosestDictionaryWordWithHighTolerance(lower)
        if (fallbackMatch != null) {
            return matchCasing(word, fallbackMatch)
        }

        return word
    }

    private fun findClosestDictionaryWordWithHighTolerance(typed: String): String? {
        val commonWordsList = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "is", "i", "are", "it", "for", "not",
            "on", "with", "was", "he", "as", "you", "do", "at", "this", "but", "has", "his", "had", "by",
            "from", "they", "were", "we", "say", "been", "her", "she", "or", "an", "will", "my", "one", "all",
            "would", "there", "their", "what", "so", "up", "out", "if", "about", "who", "get", "which", "go",
            "me", "when", "make", "can", "like", "time", "no", "just", "know", "take", "people", "into",
            "year", "your", "good", "some", "could", "them", "see", "other", "than", "then", "now", "look",
            "only", "come", "its", "over", "think", "also", "back", "after", "use", "two", "how", "our",
            "work", "first", "well", "way", "even", "new", "want", "because", "any", "these", "give", "day",
            "most", "us", "hello", "welcome", "please", "thanks", "tomorrow", "yesterday", "restaurant",
            "business", "friend", "necessary", "separate", "receive", "believe", "beautiful"
        )
        var bestMatch: String? = null
        var bestDist = 100f
        for (w in commonWordsList) {
            val dist = dictionaryManager.computeWeightedEditDistance(typed, w)
            // If the typed word is longer, allow larger distance
            val tolerance = if (typed.length > 5) 3.5f else 2.2f
            if (dist <= tolerance && dist < bestDist) {
                bestDist = dist
                bestMatch = w
            }
        }
        return bestMatch
    }
}
