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
    private val dictionaryManager = DictionaryManager(context)

    companion object {
        private const val TAG = "AiPolishManager"
    }

    private val localRambleFormatter = LocalRambleFormatter(context)

    /**
     * Executes proofreading using Google Gemini Free API with local heuristic fallback.
     */
    suspend fun proofreadText(
        text: String,
        textContext: TextContext = TextContext(mode = PolishMode.PROOFREAD)
    ): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val result = try {
            GeminiApiClient.generatePolish(text, PolishMode.PROOFREAD)
        } catch (e: Exception) {
            null
        } ?: OnDeviceNeuralPolishEngine.getInstance(context).quickProofread(text)
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "Proofread", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, result, duration)
        return@withContext result
    }

    /**
     * Executes voice dictation transcript cleanup.
     * Removes disfluencies, stutters, fillers, and spoken self-corrections.
     */
    suspend fun cleanupVoiceText(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val result = localRambleFormatter.formatRambleText(text)
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "Voice Cleanup", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, result, duration)
        return@withContext result
    }

    /**
     * Executes intent-based Ramble Mode voice dictation processing.
     * Strips fillers, resolves live mid-sentence self-corrections, and processes trailing voice commands/intents.
     */
    suspend fun processRambleDictation(text: String): String = withContext(Dispatchers.Default) {
        if (text.isBlank()) return@withContext ""

        val startTime = System.currentTimeMillis()
        val resultText = localRambleFormatter.formatRambleText(text)
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "Ramble Mode (Gemini)", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, resultText, duration)
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
        val result = try {
            GeminiApiClient.generatePolish(text, PolishMode.PROOFREAD)
        } catch (e: Exception) {
            null
        } ?: OnDeviceNeuralPolishEngine.getInstance(context).quickProofread(text)
        val finalOutput = formatRichSpokenText(result)

        val duration = System.currentTimeMillis() - startTime
        AiExecutionLogger.logAiAction(context, "Proofreading (Stream)", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, finalOutput, duration)

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
        val result = try {
            GeminiApiClient.generatePolish(text, mode)
        } catch (e: Exception) {
            null
        } ?: OnDeviceNeuralPolishEngine.getInstance(context).polish(text, mode.name.lowercase()).polishedText
        val duration = System.currentTimeMillis() - startTime

        AiExecutionLogger.logAiAction(context, "AI Polish ($mode)", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, result, duration)

        return@withContext result
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
        val result = try {
            GeminiApiClient.generatePolish(text, polishMode)
        } catch (e: Exception) {
            null
        } ?: OnDeviceNeuralPolishEngine.getInstance(context).polish(text, polishMode.name.lowercase()).polishedText

        val duration = System.currentTimeMillis() - startTime
        AiExecutionLogger.logAiAction(context, "AI Polish ($mode Stream)", AiExecutionLogger.ENGINE_GEMINI_CLOUD, text, result, duration)

        streamWords(result)
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
        val geminiEnabled = settings.geminiAiEnabled && settings.supportTier != KeyboardSettings.TIER_3

        if (!geminiEnabled) {
            emit(emptyList())
            return@flow
        }

        val (formalOpt, casualOpt, rephraseOpt) = try {
            val formal = GeminiApiClient.generatePolish(text, PolishMode.PROFESSIONAL)
            val casual = GeminiApiClient.generatePolish(text, PolishMode.CASUAL)
            val rephrase = GeminiApiClient.generatePolish(text, PolishMode.REPHRASE)
            Triple(formal, casual, rephrase)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Triple(null, null, null)
        }

        val validCloudList = listOfNotNull(formalOpt, casualOpt, rephraseOpt).filter { it.isNotBlank() }.distinct()
        if (validCloudList.isNotEmpty()) {
            emit(validCloudList)
        } else {
            emit(generateLocalStyleAlternatives(text))
        }
    }

    private fun generateLocalStyleAlternatives(input: String): List<String> {
        val trimmed = input.replace(Regex("\\s+"), " ").trim()
        if (trimmed.isEmpty()) return emptyList()

        val neuralEngine = OnDeviceNeuralPolishEngine.getInstance(context)
        val variants = neuralEngine.generateStyleVariants(trimmed).toMutableList()

        if (variants.isEmpty()) {
            variants.add(trimmed)
        }

        return variants.distinct().take(3)
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

        // Symbol replacements (only when explicitly invoked as symbol/emoji)
        val symbolCorrections = listOf(
            Regex("\\b(?:red\\s+)?heart\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "❤️",
            Regex("\\bsmiley\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bhappy\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😊",
            Regex("\\bsad\\s+(?:face|emoji)\\b", RegexOption.IGNORE_CASE) to "😢",
            Regex("\\bthumbs\\s+up\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "👍",
            Regex("\\bthumbs\\s+down\\s+(?:symbol|emoji)\\b", RegexOption.IGNORE_CASE) to "👎",
            Regex("\\barrow\\s+right\\s+(?:symbol|sign)\\b", RegexOption.IGNORE_CASE) to "→",
            Regex("\\barrow\\s+left\\s+(?:symbol|sign)\\b", RegexOption.IGNORE_CASE) to "←"
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
