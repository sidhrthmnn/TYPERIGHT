package com.example

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * High-level manager orchestrating AI proofreading, styling, rephrasing, and voice transcript cleanup.
 * Delegates to LocalInferenceEngine following a strict confidence-evaluated, local-first pipeline.
 */
class AiPolishManager(private val context: Context) {
    private val inferenceEngine = LocalInferenceEngine.getInstance(context)
    private val dictionaryManager = DictionaryManager(context)

    companion object {
        private const val TAG = "AiPolishManager"
    }

    /**
     * Executes proofreading using the local-first confidence-evaluated AI pipeline.
     */
    suspend fun proofreadText(
        text: String,
        textContext: TextContext = TextContext(mode = PolishMode.PROOFREAD)
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val result = inferenceEngine.process(text, PolishMode.PROOFREAD, textContext)
        val duration = System.currentTimeMillis() - startTime

        val engineName = when (result.source) {
            AiSource.CLOUD -> AiExecutionLogger.ENGINE_GEMINI_CLOUD
            AiSource.LOCAL_MODEL -> AiExecutionLogger.ENGINE_OFFLINE_LOCAL
            AiSource.LOCAL_RULES -> AiExecutionLogger.ENGINE_OFFLINE_LOCAL
            AiSource.ORIGINAL -> AiExecutionLogger.ENGINE_OFFLINE_LOCAL
        }
        AiExecutionLogger.logAiAction(context, "Proofreading", engineName, text, result.text, duration)

        return@withContext result.text
    }

    /**
     * Executes voice dictation transcript cleanup.
     * Removes disfluencies, stutters, fillers, and spoken self-corrections.
     */
    suspend fun cleanupVoiceText(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val result = inferenceEngine.process(text, PolishMode.VOICE_CLEANUP, TextContext(mode = PolishMode.VOICE_CLEANUP))
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "Voice Cleanup", AiExecutionLogger.ENGINE_OFFLINE_LOCAL, text, result.text, duration)
        return@withContext result.text
    }

    private val localRambleFormatter = LocalRambleFormatter(context)

    /**
     * Executes intent-based Ramble Mode voice dictation processing 100% on-device (zero cloud).
     * Strips fillers, resolves live mid-sentence self-corrections, and processes trailing voice commands/intents.
     */
    suspend fun processRambleDictation(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val resultText = localRambleFormatter.formatRambleText(text)
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "Ramble Mode (Offline SLM)", AiExecutionLogger.ENGINE_OFFLINE_LOCAL, text, resultText, duration)
        return@withContext resultText
    }

    /**
     * Streams proofreading output chunk-by-chunk for live UI rendering.
     */
    fun proofreadTextStream(text: String): Flow<String> = flow {
        if (text.isBlank()) {
            emit("")
            return@flow
        }

        val startTime = System.currentTimeMillis()
        // If the text looks like a raw voice transcription with spoken bullet/numeric indicators or greetings,
        // format it rich and clean
        val richFormatted = formatRichSpokenText(text)
        val finalOutput = if (richFormatted.contains("\n") || richFormatted.contains("•") || richFormatted.contains("❤️") || richFormatted.contains("😊")) {
            richFormatted
        } else {
            val result = inferenceEngine.process(richFormatted, PolishMode.PROOFREAD)
            result.text
        }

        val duration = System.currentTimeMillis() - startTime
        AiExecutionLogger.logAiAction(context, "Proofreading (Stream)", AiExecutionLogger.ENGINE_OFFLINE_LOCAL, text, finalOutput, duration)

        streamWords(finalOutput)
    }

    /**
     * AI Polish text: transforms text into specified style/mode.
     */
    suspend fun polishText(text: String, mode: String = "formalize"): String {
        val polishMode = PolishMode.fromString(mode)
        return polishText(text, polishMode)
    }

    /**
     * AI Polish text with typed PolishMode.
     */
    suspend fun polishText(
        text: String,
        mode: PolishMode,
        textContext: TextContext = TextContext(mode = mode)
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val result = inferenceEngine.process(text, mode, textContext)
        val duration = System.currentTimeMillis() - startTime

        val engineName = if (result.source == AiSource.CLOUD) AiExecutionLogger.ENGINE_GEMINI_CLOUD else AiExecutionLogger.ENGINE_OFFLINE_LOCAL
        AiExecutionLogger.logAiAction(context, "AI Polish ($mode)", engineName, text, result.text, duration)

        return@withContext result.text
    }

    /**
     * Streams AI Polish output chunk-by-chunk.
     */
    fun polishTextStream(text: String, mode: String = "formalize"): Flow<String> = flow {
        if (text.isBlank()) {
            emit("")
            return@flow
        }

        val startTime = System.currentTimeMillis()
        val polishMode = PolishMode.fromString(mode)
        val result = inferenceEngine.process(text, polishMode)

        val duration = System.currentTimeMillis() - startTime
        val engineUsed = if (result.source == AiSource.CLOUD) AiExecutionLogger.ENGINE_GEMINI_CLOUD else AiExecutionLogger.ENGINE_OFFLINE_LOCAL
        AiExecutionLogger.logAiAction(context, "AI Polish ($mode Stream)", engineUsed, text, result.text, duration)

        streamWords(result.text)
    }

    /**
     * Suggests AI Polish style improvements (Professional, Casual, Rephrase) for text.
     */
    fun suggestImprovements(text: String): Flow<List<String>> = flow {
        if (text.isBlank()) {
            emit(emptyList())
            return@flow
        }

        val settings = KeyboardSettings(context)
        val offlineEnabled = settings.offlineAiEnabled
        val geminiEnabled = settings.geminiAiEnabled &&
                settings.supportTier != KeyboardSettings.TIER_3 &&
                settings.voiceInputMode != KeyboardSettings.VOICE_MODE_LOCAL

        if (!offlineEnabled && !geminiEnabled) {
            emit(emptyList())
            return@flow
        }

        if (geminiEnabled) {
            val (formalOpt, casualOpt, rephraseOpt) = try {
                val formal = GeminiApiClient.generatePolish(text, PolishMode.PROFESSIONAL)
                val casual = GeminiApiClient.generatePolish(text, PolishMode.CASUAL)
                val rephrase = GeminiApiClient.generatePolish(text, PolishMode.REPHRASE)
                Triple(formal, casual, rephrase)
            } catch (e: Throwable) {
                Triple(null, null, null)
            }

            val validCloudList = listOfNotNull(formalOpt, casualOpt, rephraseOpt).filter { it.isNotBlank() }.distinct()
            if (validCloudList.size >= 3) {
                emit(validCloudList)
                return@flow
            }
        }

        if (offlineEnabled) {
            emit(generateLocalStyleAlternatives(text))
        } else {
            emit(emptyList())
        }
    }

    private fun generateLocalStyleAlternatives(input: String): List<String> {
        val trimmed = input.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isEmpty()) return emptyList()

        val results = mutableListOf<String>()

        // 1. Professional
        val prof = inferenceEngine.polishSentenceLocally(trimmed).let {
            var s = it
            s = s.replace(Regex("\\bwant to\\b", RegexOption.IGNORE_CASE), "would like to")
            s = s.replace(Regex("\\bcan you\\b", RegexOption.IGNORE_CASE), "could you please")
            s = s.replace(Regex("\\bthanks\\b", RegexOption.IGNORE_CASE), "thank you")
            s = s.replace(Regex("\\babout\\b", RegexOption.IGNORE_CASE), "regarding")
            s.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() }
        }
        results.add(prof)

        // 2. Casual
        val cas = if (!trimmed.startsWith("Hey", ignoreCase = true) && !trimmed.startsWith("Hi", ignoreCase = true)) {
            "Hey! " + trimmed.replaceFirstChar { it.lowercase() }
        } else {
            "Hey! " + trimmed.replace(Regex("^(?:hey|hi)[!,.]?\\s*", RegexOption.IGNORE_CASE), "").replaceFirstChar { it.lowercase() }
        }
        if (cas != prof) results.add(cas)

        // 3. Concise
        val concise = trimmed.replace(Regex("\\bI was wondering if you could please\\b", RegexOption.IGNORE_CASE), "Could you")
            .replace(Regex("\\bjust wanted to\\b", RegexOption.IGNORE_CASE), "")
            .trim()
        if (concise.isNotEmpty() && !results.contains(concise)) results.add(concise)

        var idx = 1
        while (results.size < 3) {
            val base = results.firstOrNull() ?: trimmed
            val extra = if (idx == 1) "Inquiring regarding this: $trimmed" else "$base (Refined)"
            if (!results.contains(extra)) {
                results.add(extra)
            }
            idx++
        }

        return results.distinct().take(3)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<String>.streamWords(content: String) {
        val lines = content.split("\n")
        val currentText = StringBuilder()
        var isFirstLine = true
        for (line in lines) {
            if (!isFirstLine) {
                currentText.append("\n")
            }
            isFirstLine = false
            if (line.isEmpty()) {
                emit(currentText.toString())
                delay(30)
                continue
            }
            val words = line.split(" ").filter { it.isNotEmpty() }
            for (i in words.indices) {
                if (i > 0) {
                    currentText.append(" ")
                }
                currentText.append(words[i])
                emit(currentText.toString())
                delay(20)
            }
        }
        if (currentText.toString() != content) {
            emit(content)
        }
    }

    /**
     * Formats spoken dictation containing greetings, lists, sign-offs, and transition words.
     */
    private fun formatRichSpokenText(input: String): String {
        var text = WhisperCppBrain.whisperCleanAndPolish(input)
        if (text.isEmpty()) return ""

        // Symbol replacements
        val symbolCorrections = listOf(
            Regex("\\bheart\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "❤️",
            Regex("\\bheart\\b", RegexOption.IGNORE_CASE) to "❤️",
            Regex("\\bsmiley\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bsmiley\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bhappy\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bsad\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😢",
            Regex("\\bthumbs\\s+up\\b", RegexOption.IGNORE_CASE) to "👍",
            Regex("\\bthumbs\\s+down\\b", RegexOption.IGNORE_CASE) to "👎",
            Regex("\\barrow\\s+right\\b", RegexOption.IGNORE_CASE) to "→",
            Regex("\\barrow\\s+left\\b", RegexOption.IGNORE_CASE) to "←"
        )
        for ((regex, replacement) in symbolCorrections) {
            text = text.replace(regex, replacement)
        }

        // Greeting Header
        var greetingHeader = ""
        val greetingRegex = Regex(
            "^(hey|hi|hello|dear|yo|good morning|good afternoon|good evening|greetings)(?:\\s+([a-zA-Z]+))?(?:\\s+(?:there|everyone|all|team))?\\b",
            RegexOption.IGNORE_CASE
        )
        val greetingMatch = greetingRegex.find(text)
        if (greetingMatch != null) {
            val greetingWord = greetingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val name = greetingMatch.groupValues[2].trim()
            val formattedName = if (name.isNotEmpty()) " " + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } else ""
            greetingHeader = "$greetingWord$formattedName,\n\n"
            text = text.substring(greetingMatch.value.length).trim()
        }

        // Closing Footer
        var closingFooter = ""
        val closingRegex = Regex(
            "\\b(thanks|thank you|best regards|regards|sincerely|cheers|best|warmly|yours truly)(?:\\s+([a-zA-Z]+))?\\.?$",
            RegexOption.IGNORE_CASE
        )
        val closingMatch = closingRegex.find(text)
        if (closingMatch != null) {
            val closingWord = closingMatch.groupValues[1].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val name = closingMatch.groupValues[2].trim()
            val formattedName = if (name.isNotEmpty()) "\n" + name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } else ""
            closingFooter = "\n\n$closingWord,$formattedName"
            text = text.substring(0, closingMatch.range.first).trim()
        }

        // Transition split
        val transitionSplitRegex = Regex("\\s+\\b(by the way|anyway|on another note|however|furthermore|in addition|therefore)\\b", RegexOption.IGNORE_CASE)
        text = text.replace(transitionSplitRegex) { ". " + it.groupValues[1] }

        // Bullet & Numeric list formatting
        val bulletRegex = Regex("\\b(first point|second point|third point|point one|point two|point three|bullet one|bullet two)\\b", RegexOption.IGNORE_CASE)
        text = text.replace(bulletRegex) { "[BULLET]" }

        val numericRegex = Regex("\\b(number\\s+(?:one|two|three|four|five)|1\\.|2\\.|3\\.|4\\.|5\\.)\\b", RegexOption.IGNORE_CASE)
        text = text.replace(numericRegex) { "[NUMERIC]" }

        val delimiters = Regex("(?<=[.!?])\\s+|(?=\\[BULLET\\])|(?=\\[NUMERIC\\])")
        val segments = text.split(delimiters).map { it.trim() }.filter { it.isNotEmpty() }

        val processedSegments = mutableListOf<String>()
        var bulletIndex = 1

        for (segment in segments) {
            var isBullet = false
            var isNumeric = false
            var clean = segment

            if (clean.startsWith("[BULLET]")) {
                isBullet = true
                clean = clean.substring("[BULLET]".length).trim()
            } else if (clean.startsWith("[NUMERIC]")) {
                isNumeric = true
                clean = clean.substring("[NUMERIC]".length).trim()
            }

            clean = clean.replaceFirst(Regex("^[,.!?;:\\s]+"), "")
            if (clean.isEmpty()) continue

            clean = clean.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            if (clean.startsWith("By the way", ignoreCase = true) && !clean.startsWith("By the way,", ignoreCase = true)) {
                clean = clean.replaceFirst(Regex("^By the way\\b", RegexOption.IGNORE_CASE), "By the way,")
            }
            if (clean.startsWith("Anyway", ignoreCase = true) && !clean.startsWith("Anyway,", ignoreCase = true)) {
                clean = clean.replaceFirst(Regex("^Anyway\\b", RegexOption.IGNORE_CASE), "Anyway,")
            }

            val withoutTransition = clean.replace(Regex("^(?:by the way|anyway|however|furthermore)[!,.]?\\s*", RegexOption.IGNORE_CASE), "")
            val isQuestion = withoutTransition.lowercase().let {
                it.startsWith("how ") || it.startsWith("what ") || it.startsWith("why ") || it.startsWith("who ") ||
                        it.startsWith("where ") || it.startsWith("when ") || it.startsWith("can ") || it.startsWith("do ") ||
                        it.startsWith("did ") || it.startsWith("is ") || it.startsWith("are ") || it.startsWith("would ") ||
                        it.startsWith("could ") || it.startsWith("will ") || it.startsWith("should ") || it.startsWith("may ")
            }

            if (isQuestion && clean.endsWith(".")) {
                clean = clean.dropLast(1) + "?"
            } else if (!clean.endsWith(".") && !clean.endsWith("?") && !clean.endsWith("!")) {
                clean += if (isQuestion) "?" else "."
            }

            val prefix = when {
                isBullet -> "\n• "
                isNumeric -> "\n${bulletIndex++}. "
                (clean.startsWith("Anyway") || clean.startsWith("By the way")) && processedSegments.isNotEmpty() -> "\n\n"
                else -> " "
            }
            processedSegments.add(prefix + clean)
        }

        var body = processedSegments.joinToString("").trim()
        body = body.replace(Regex("\\s+([.,!?;:])"), "$1")
        body = body.replace(Regex("([.,!?;:])(?!\\s|\n|$)"), "$1 ")
        body = body.replace(Regex(" +"), " ")

        val finalResult = StringBuilder()
        if (greetingHeader.isNotEmpty()) finalResult.append(greetingHeader)
        finalResult.append(body)
        if (closingFooter.isNotEmpty()) finalResult.append(closingFooter)

        return finalResult.toString().trim().ifEmpty { input }
    }
}
