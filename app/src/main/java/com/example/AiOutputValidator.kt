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
        "here is", "here's", "sure", "certainly", "i have corrected", "i've corrected",
        "corrected text", "polished text", "note:", "output:", "result:",
        "here is the", "here's the", "the corrected", "the polished"
    )

    /**
     * Sanitizes candidate output by removing accidental markdown code fences,
     * wrapping quotation marks, or leading AI preamble.
     */
    fun sanitize(candidate: String, originalInput: String = ""): String {
        var clean = candidate.trim()
        if (clean.isEmpty()) return ""

        // Strip markdown code fences (``` or ```text ... ```)
        if (clean.startsWith("```")) {
            clean = clean.replace(Regex("^```[a-zA-Z]*\\s*\n?"), "")
            clean = clean.replace(Regex("\n?```$"), "").trim()
        }

        // Strip outer enclosing quotes if original was not enclosed in quotes
        val originalEnclosed = (originalInput.startsWith("\"") && originalInput.endsWith("\"")) ||
                (originalInput.startsWith("“") && originalInput.endsWith("”")) ||
                (originalInput.startsWith("'") && originalInput.endsWith("'"))
        if (!originalEnclosed) {
            if ((clean.startsWith("\"") && clean.endsWith("\"")) || (clean.startsWith("“") && clean.endsWith("”"))) {
                clean = clean.substring(1, clean.length - 1).trim()
            } else if (clean.startsWith("'") && clean.endsWith("'") && clean.length > 2) {
                clean = clean.substring(1, clean.length - 1).trim()
            }
        }

        // Strip known commentary header lines if followed by newline
        val lines = clean.lines()
        if (lines.size > 1) {
            val firstLineLower = lines[0].lowercase().trim()
            if (COMMENTARY_PREFIXES.any { firstLineLower.startsWith(it) }) {
                clean = lines.drop(1).joinToString("\n").trim()
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
        if (origTrim.isEmpty()) return true

        // 2. Reject obvious AI chat commentary
        val lower = candTrim.lowercase()
        for (prefix in COMMENTARY_PREFIXES) {
            if (lower.startsWith(prefix) && (lower.contains(":") || lower.contains("\n") || lower.length > prefix.length + 15)) {
                return false
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
        // but for PROOFREAD numbers must strictly match.
        if (mode == PolishMode.PROOFREAD) {
            val origNumbers = extractMatches(origTrim, NUMBER_REGEX)
            val candNumbers = extractMatches(candTrim, NUMBER_REGEX)
            if (origNumbers.isNotEmpty() && origNumbers != candNumbers) {
                // If numbers were altered or deleted in PROOFREAD mode, reject
                return false
            }
        }

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
                // Shorten is expected to reduce length, but not balloon
                if (origLen >= 30 && candLen > origLen * 1.2) return false
            }
            PolishMode.EXPAND -> {
                // Expand is expected to add details, but not drop almost everything
                if (origLen >= 20 && candLen < (origLen * 0.5)) return false
            }
            else -> {
                // Other modes: shouldn't arbitrarily drop > 45% of content
                if (origLen >= 25 && candLen < (origLen * 0.55)) return false
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
