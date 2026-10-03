package com.example

import java.util.regex.Pattern

/**
 * Validates AI and local inference outputs to prevent hallucinations,
 * unwanted commentary, dropped URLs/numbers, or malformed text.
 */
object AiOutputValidator {

    private val URL_REGEX = Pattern.compile("https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+", Pattern.CASE_INSENSITIVE)
    private val EMAIL_REGEX = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", Pattern.CASE_INSENSITIVE)
    private val NUMBER_REGEX = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b")

    // Common AI chat commentary prefixes to reject/strip
    private val COMMENTARY_PREFIXES = listOf(
        "here is how we change the tone",
        "here is how to change the tone",
        "here's how we change the tone",
        "here's how to change the tone",
        "here is how",
        "here's how",
        "here is",
        "here's",
        "sure",
        "certainly",
        "absolutely",
        "i have corrected",
        "i've corrected",
        "i have rewritten",
        "i've rewritten",
        "corrected text",
        "polished text",
        "revised text",
        "note:",
        "output:",
        "result:",
        "here is the",
        "here's the",
        "the corrected",
        "the polished",
        "the revised",
        "in a professional tone",
        "in a casual tone"
    )

    // Regex patterns targeting conversational preamble variations (inline and multiline)
    private val PREAMBLE_REGEXES = listOf(
        // "Here is/are how we/to change the tone..."
        Regex("""(?i)^(?:here\s+(?:is|are)|here's)\s+how\s+(?:we|to|you\s+can)\s+(?:change|adjust|shift|improve|rewrite)\s+the\s+(?:tone|style)[^:\n]*[:\-\s]*"""),
        // "Here is/are / Here's [a/the] [revised/polished/edited/formal/professional/casual/new] [text/version/sentence/response/result/draft/message]..."
        Regex("""(?i)^(?:here\s+(?:is|are)|here's)\s+(?:a\s+|the\s+)?(?:revised|polished|edited|updated|formal|professional|casual|concise|new|rewritten|corrected)\s+(?:text|version|sentence|response|result|draft|message|option)[^:\n]*[:\-\s]*"""),
        // "Here is/are / Here's what/the..."
        Regex("""(?i)^(?:here\s+(?:is|are)|here's)\s+(?:the\s+)?(?:result|output|correction|change|rewrite)[^:\n]*[:\-\s]*"""),
        // "Sure! / Certainly! [here is...]"
        Regex("""(?i)^(?:sure(?:thing)?|certainly|absolutely|of\s+course)[\s,!.-]*(?:here(?:\s+is|'s)[^:\n]*)?[:\-\s]*"""),
        // "I have / I've rewritten / corrected / polished / changed the tone..."
        Regex("""(?i)^(?:i\s+have|i've)\s+(?:rewritten|corrected|polished|revised|edited|changed\s+the\s+tone\s+of)[^:\n]*[:\-\s]*"""),
        // "In a / With a [professional/casual/...] tone:"
        Regex("""(?i)^(?:in\s+a\s+|with\s+a\s+)?(?:professional|business|casual|friendly|formal|polite|concise)\s+tone\s*[:\-]\s*"""),
        // "Revised/Polished/Corrected text/version:"
        Regex("""(?i)^(?:revised|polished|corrected|edited|formal|casual|clean(?:ed)?)\s+(?:text|version|draft|output|result)\s*[:\-]\s*"""),
        // "Output: / Result: / Correction:"
        Regex("""(?i)^(?:output|result|response|correction)\s*[:\-]\s*""")
    )

    // Trailing explanation or signoff patterns
    private val TRAILING_COMMENTARY_REGEX = Regex(
        """(?is)\n+\s*(?:\(?note\s*:|explanation\s*:|hope\s+this\s+helps|let\s+me\s+know\s+if|changes\s+made\s*:).*$"""
    )

    /**
     * Sanitizes candidate output by removing accidental markdown code fences,
     * wrapping quotation marks, leading AI preamble, or trailing notes.
     */
    fun sanitize(candidate: String, originalInput: String = ""): String {
        var clean = candidate.trim()
        if (clean.isEmpty()) return ""

        // 1. Strip reasoning tags if model output contains <think>...</think> or stray </think>
        clean = clean.replace(Regex("(?s)<think>.*?</think>"), "").trim()
        clean = clean.replace(Regex("</?think>"), "").trim()

        // 2. Strip markdown code fences (``` or ```text ... ```)
        if (clean.startsWith("```")) {
            clean = clean.replace(Regex("^```[a-zA-Z]*\\s*\n?"), "")
            clean = clean.replace(Regex("\n?```$"), "").trim()
        }

        // 3. Strip trailing commentary (e.g. "\n\nNote: ...", "\n\nHope this helps!") if original didn't have it
        val origLower = originalInput.lowercase()
        if (!origLower.contains("note:") && !origLower.contains("explanation:") && !origLower.contains("hope this helps")) {
            clean = clean.replace(TRAILING_COMMENTARY_REGEX, "").trim()
        }

        // 4. Strip leading conversational preamble / filler (both inline and multi-line)
        // Repeat up to 5 times to handle stacked preambles (e.g., "Sure! Here is how we change the tone:\n\n...")
        var changed = true
        var loopCount = 0
        while (changed && loopCount < 5 && clean.isNotEmpty()) {
            changed = false
            loopCount++

            // Check regex patterns first
            for (pattern in PREAMBLE_REGEXES) {
                val match = pattern.find(clean)
                if (match != null && match.range.first == 0) {
                    val matchedText = match.value
                    // Only strip if original input didn't start with this exact phrase
                    if (!originalInput.trim().lowercase().startsWith(matchedText.trim().lowercase())) {
                        val remaining = clean.substring(match.range.last + 1).trim()
                        if (remaining.isNotEmpty()) {
                            clean = remaining
                            changed = true
                            break
                        }
                    }
                }
            }

            if (!changed) {
                // Check line-by-line: if first line starts with a commentary prefix and is followed by newline or colon
                val lines = clean.lines()
                if (lines.size > 1) {
                    val firstLine = lines[0].trim()
                    val firstLineLower = firstLine.lowercase()
                    if (COMMENTARY_PREFIXES.any { firstLineLower.startsWith(it) }) {
                        if (!originalInput.trim().lowercase().startsWith(firstLineLower)) {
                            clean = lines.drop(1).joinToString("\n").trim()
                            changed = true
                        }
                    }
                }
            }
        }

        // 5. Strip outer enclosing quotes if original was not enclosed in quotes
        val originalEnclosed = (originalInput.startsWith("\"") && originalInput.endsWith("\"")) ||
                (originalInput.startsWith("“") && originalInput.endsWith("”")) ||
                (originalInput.startsWith("'") && originalInput.endsWith("'"))
        if (!originalEnclosed && clean.length >= 2) {
            if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("“") && clean.endsWith("”"))) {
                clean = clean.substring(1, clean.length - 1).trim()
            } else if (clean.startsWith("'") && clean.endsWith("'") && clean.length > 2) {
                clean = clean.substring(1, clean.length - 1).trim()
            }
        }

        return clean
    }

    /**
     * Validates whether candidate output is safe and high-quality according to mode rules.
     * Returns true if candidate passes all validation rules, false otherwise.
     */
    fun isValid(original: String, candidate: String, mode: PolishMode): Boolean {
        val origTrim = original.trim()
        val candTrim = candidate.trim()

        // 1. Never accept empty results for non-empty input
        if (candTrim.isEmpty()) {
            return origTrim.isEmpty()
        }
        if (origTrim.isEmpty()) return false

        // Transliteration and slang are intentional vocabulary, not English misspellings.
        val sourceTokens = Regex("[\\p{L}\\p{M}]+").findAll(origTrim).map { MultilingualLexicon.normalize(it.value) }.toList()
        val targetTokens = Regex("[\\p{L}\\p{M}]+").findAll(candTrim).map { MultilingualLexicon.normalize(it.value) }.toList()
        val hiSpan = sourceTokens.count { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "hi", "par", "se", "fir", "bas") } >= 2
        val literals = sourceTokens.filter { it in MultilingualLexicon.romanizedMalayalam || (hiSpan && it in MultilingualLexicon.romanizedHindi) ||
            (mode == PolishMode.PROOFREAD && it in MultilingualLexicon.slang) }
        if (literals.distinct().any { token -> targetTokens.count { it == token } < literals.count { it == token } }) return false

        // 2. Reject obvious AI chat commentary if unstripped
        val lower = candTrim.lowercase()
        val origLower = origTrim.lowercase()
        for (prefix in COMMENTARY_PREFIXES) {
            if (lower.startsWith(prefix) && (lower.contains(":") || lower.contains("\n") || lower.length > prefix.length + 15)) {
                if (!origLower.startsWith(prefix)) {
                    return false
                }
            }
        }

        // 3. Reject malformed markdown artifacts
        if (candTrim.contains("```")) return false

        // 4. URL Preservation: All URLs present in input must be retained verbatim
        val origUrls = extractMatches(origTrim, URL_REGEX)
        if (origUrls.isNotEmpty()) {
            val candUrls = extractMatches(candTrim, URL_REGEX)
            for (url in origUrls) {
                if (!candUrls.contains(url)) {
                    return false
                }
            }
        }

        // 5. Email Preservation: All Emails present in input must be retained verbatim
        val origEmails = extractMatches(origTrim, EMAIL_REGEX)
        if (origEmails.isNotEmpty()) {
            val candEmails = extractMatches(candTrim, EMAIL_REGEX)
            for (email in origEmails) {
                if (!candEmails.contains(email)) {
                    return false
                }
            }
        }

        // 6. Number Preservation (for PROOFREAD, POLISH, PROFESSIONAL, CASUAL, SHORTEN, EXPAND, REPHRASE)
        // VOICE_CLEANUP can resolve spoken self-corrections like "five no wait six" -> "6",
        // but other editing modes preserve all original numbers.
        if (mode != PolishMode.VOICE_CLEANUP && mode != PolishMode.RAMBLE) {
            val origNumbers = extractMatches(origTrim, NUMBER_REGEX)
            val candNumbers = extractMatches(candTrim, NUMBER_REGEX)
            if (origNumbers != candNumbers) {
                // If numbers were altered or deleted in an editing mode, reject
                return false
            }
        }

        // Preserve refusals during proofreading and keep questions as questions.
        if (mode == PolishMode.PROOFREAD) {
            val negations = Regex("""\b(?:not|never|no|without|cannot|dont|doesnt|didnt|cant|wont|shouldnt|wouldnt|couldnt|isnt|arent|wasnt|werent|havent|hasnt|hadnt|mustnt|neednt|shant|aint|[a-z]+n['’]t)\b""", RegexOption.IGNORE_CASE)
            if (negations.findAll(origTrim).count() != negations.findAll(candTrim).count()) return false
        }
        if (mode !in setOf(PolishMode.VOICE_CLEANUP, PolishMode.RAMBLE) &&
            origTrim.endsWith("?") && !candTrim.endsWith("?")) return false

        // 7. Length and Hallucination Check
        val origLen = origTrim.length
        val candLen = candTrim.length

        when (mode) {
            PolishMode.VOICE_CLEANUP, PolishMode.RAMBLE -> {
                // Voice cleanup and Ramble mode can prune substantial spoken fillers, stutters, verbal self-corrections, and trailing commands
                if (candLen == 0 && origLen > 0) return false
            }
            PolishMode.PROOFREAD -> {
                // Proofreading should never drastically shrink or balloon the text
                if (origLen >= 20) {
                    if (candLen < (origLen * 0.65)) return false // Dropped > 35%
                    if (candLen > (origLen * 1.60)) return false // Grew > 60%
                }
            }
            PolishMode.SHORTEN -> {
                // Shorten is expected to reduce length, but not balloon or drop to empty
                if (candLen == 0 && origLen > 0) return false
                if (origLen >= 30 && candLen > origLen * 1.35) return false
            }
            PolishMode.EXPAND -> {
                // Expand is expected to add details, but not drop almost everything
                if (origLen >= 20 && candLen < (origLen * 0.40)) return false
            }
            PolishMode.PROFESSIONAL, PolishMode.CASUAL, PolishMode.REPHRASE, PolishMode.POLISH, PolishMode.AUTO_FORMAT -> {
                if (candLen == 0 && origLen > 0) return false
                if (origLen >= 25 && candLen < (origLen * 0.40)) return false
            }
            else -> {
                if (candLen == 0 && origLen > 0) return false
                if (origLen >= 25 && candLen < (origLen * 0.40)) return false
            }
        }

        return true
    }

    private fun extractMatches(text: String, pattern: Pattern): List<String> {
        val matches = mutableListOf<String>()
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            val matched = matcher.group().trimEnd('.', ',', '!', '?', ';', ':', ')', ']')
            matches.add(matched)
        }
        return matches
    }
}
