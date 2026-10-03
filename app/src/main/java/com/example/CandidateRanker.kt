package com.example

import android.content.Context
import android.graphics.PointF
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.abs

enum class CorrectionPhase { KEYSTROKE, WORD_BOUNDARY }
enum class CandidateOrigin { LITERAL, DICTIONARY, TYPO, PHONETIC, PERSONAL, ACCEPTED, CONTEXT, COMPLETION }
data class RankedCandidate(val word: String, val score: Float, val posterior: Float, val distance: Float,
    val keyboard: Float, val frequency: Float, val context: Float, val personal: Float,
    val accepted: Float, val rejected: Float, val language: String, val origins: Set<CandidateOrigin>)
data class RankedCorrection(val original: String, val candidates: List<RankedCandidate>, val confidence: Float,
    val margin: Float, val tier: ConfidenceTier, val language: String, val protected: Boolean) {
    val best get() = candidates.firstOrNull()
    val automatic: String? get() = best?.word?.takeIf { tier == ConfidenceTier.HIGH && it != original }
    val suggestion: String get() = if (tier == ConfidenceTier.LOW) original else best?.word ?: original
}

/** The sole correction decision maker. Call rank on a worker; the IME may only read cached results. */
class CandidateRanker(private val context: Context, private val dictionary: DictionaryManager) {
    private val settings = KeyboardSettings(context)
    private val multilingual = MultilingualLexicon.get(context)
    private val spatial = dictionary.gboardEngine.spatialModel
    private val profile = dictionary.personalProfile
    private val phonetics = mapOf("fone" to "phone", "fonetic" to "phonetic", "enuf" to "enough", "nite" to "night", "kwik" to "quick", "becoz" to "because", "frend" to "friend")
    private data class Key(val word: String, val context: List<String>, val taps: Int, val revision: Long, val sensitivity: String, val learning: Boolean)
    private val cache = object : LinkedHashMap<Key, RankedCorrection>(96, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RankedCorrection>?) = size > 96
    }
    private fun key(word: String, words: List<String>, taps: List<PointF>?) = Key(word, words.takeLast(5),
        taps?.takeIf { it.isNotEmpty() }?.fold(1) { hash, p -> 31 * hash + (p.x * 1000).toInt() + 37 * (p.y * 1000).toInt() } ?: 0,
        profile.revision, settings.autocorrectSensitivity, settings.personalizedLearningEnabled)
    fun isProtectedPersonalWord(word: String): Boolean = dictionary.isWordInUserDictionary(word) || dictionary.isContactWord(word) || dictionary.isBlocked(word) || (settings.personalizedLearningEnabled && profile.isTrusted(word) && !dictionary.isRecognizedInAnyLanguage(word))
    fun cached(word: String, words: List<String>, taps: List<PointF>?): RankedCorrection? {
        if (dictionary.isContactWord(word) || dictionary.isBlocked(word)) return null
        if (isProtectedPersonalWord(word) && dictionary.learnedCorrection(word, words) == null) return null
        return synchronized(cache) {
            cache[key(word, words, taps)]?.takeUnless { result ->
                result.best?.word?.let { dictionary.isBlocked(it) || dictionary.isCorrectionSuppressed(word, it) } == true
            }
        }
    }
    suspend fun awaitDictionaries(languages: List<String> = emptyList()) { multilingual.warm(languages); EnglishFrequencyLexicon.get(context).ready.await() }

    fun nextWords(context: List<String>, limit: Int = 3): List<String> {
        val prior = context.takeLast(5)
        val lang = prior.lastOrNull()?.let { language(MultilingualLexicon.normalize(it), prior.dropLast(1)) } ?: "en"
        val native = when (lang) {
            "ml-Latn" -> listOf("ente", "enikku", "aanu", "alla", "varum", "nale", "nalla", "venam")
            "hi-Latn" -> listOf("hai", "nahi", "kal", "aaj", "kya", "karna", "chahiye", "yaar")
            "en", "und" -> emptyList()
            else -> multilingual.frequent(lang)
        }
        val pool = dictionary.personalCandidates("", prior) + native + dictionary.nGramModel.predictNextWords(prior, "", 12) +
            if (prior.isEmpty()) listOf("I", "The", "Hi") else listOf("the", "to", "and", "you")
        return pool.filter { it.isNotBlank() && !dictionary.isBlocked(it) }.distinctBy(MultilingualLexicon::normalize)
            .sortedByDescending { word ->
                dictionary.nGramModel.getProbability(word, prior) * 2f + dictionary.personalBoost(word, prior) +
                    (if (word in native) .50f else 0f) + (ln(multilingual.frequency(word, lang) + 1f) / 20f).coerceAtMost(.4f)
            }.take(limit)
    }

    private fun language(word: String, prior: List<String>): String {
        if (word in MultilingualLexicon.romanizedMalayalam) return "ml-Latn"
        if (word in MultilingualLexicon.romanizedHindi && (!dictionary.isWordInDictionary(word) || prior.any { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "hi", "par", "se", "fir", "bas") })) return "hi-Latn"
        if (dictionary.isWordInDictionary(word) && prior.lastOrNull() in setOf("i", "you", "we", "they", "he", "she", "it", "will", "would", "could", "should", "can", "the", "and", "a", "an", "with", "over")) return "en"
        val choices = multilingual.languages(word)
        // A foreign word's own span wins over English typo rules when neighbouring words support it.
        choices.firstOrNull { lang -> prior.takeLast(5).any { !dictionary.isWordInDictionary(it) && multilingual.frequency(it, lang) > 30 } }?.let { return it }
        if (word.any { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }) return choices.firstOrNull() ?: when (Character.UnicodeScript.of(word.first { it.isLetter() && Character.UnicodeScript.of(it.code) != Character.UnicodeScript.LATIN }.code)) {
            Character.UnicodeScript.DEVANAGARI -> "hi"; Character.UnicodeScript.MALAYALAM -> "ml"
            Character.UnicodeScript.TAMIL -> "ta"; Character.UnicodeScript.TELUGU -> "te"
            Character.UnicodeScript.BENGALI -> "bn"; Character.UnicodeScript.ARABIC -> "ar"
            Character.UnicodeScript.CYRILLIC -> "ru"; Character.UnicodeScript.GREEK -> "el"
            Character.UnicodeScript.HANGUL -> "ko"; Character.UnicodeScript.HAN -> "zh_cn"
            Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> "ja"
            Character.UnicodeScript.HEBREW -> "he"; Character.UnicodeScript.THAI -> "th"
            else -> "und"
        }
        if (dictionary.isWordInDictionary(word) || typo(word) != null) return "en"
        return choices.firstOrNull() ?: "und"
    }
    private fun typo(word: String): String? = if (word in MultilingualLexicon.slang || word in MultilingualLexicon.romanizedMalayalam || word in MultilingualLexicon.romanizedHindi) null
        else dictionary.gboardEngine.typoProposal(word)

    fun rank(typed: String, contextWords: List<String> = emptyList(), taps: List<PointF>? = null,
             phase: CorrectionPhase = CorrectionPhase.WORD_BOUNDARY): RankedCorrection {
        val lower = MultilingualLexicon.normalize(typed)
        val prior = contextWords.takeLast(5).map(MultilingualLexicon::normalize)
        val lang = language(lower, prior)
        val mlSpan = prior.any { it in MultilingualLexicon.romanizedMalayalam }
        val hiSpan = prior.any { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "par", "se", "hi") }
        val mixed = mlSpan || hiSpan
        val accepted = dictionary.learnedCorrection(typed, prior)
        val protected = lower.length !in 1..32 || typed.any { !TypingPolicy.isWordCharacter(it) } ||
            dictionary.isCodeOrSpecialToken(typed) || dictionary.isBlocked(typed) ||
            lower in MultilingualLexicon.slang || lower in MultilingualLexicon.romanizedMalayalam ||
            (lower in MultilingualLexicon.romanizedHindi && (hiSpan || !dictionary.isWordInDictionary(lower))) ||
            (mlSpan && !dictionary.isWordInDictionary(lower) && lower.endsWith("il")) ||
            (dictionary.isWordInUserDictionary(lower) && accepted == null) || dictionary.isContactWord(lower) ||
            (settings.personalizedLearningEnabled && profile.isTrusted(lower) && !dictionary.isRecognizedInAnyLanguage(lower) && accepted == null) ||
            (typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typo(lower) == null && accepted == null) ||
            (typed.firstOrNull()?.isUpperCase() == true && !dictionary.isWordInDictionary(lower) && typo(lower) == null && accepted == null)
        if (protected) return RankedCorrection(typed, emptyList(), 0f, 0f, ConfidenceTier.LOW, lang, true)
        val known = if (lang == "en") dictionary.isWordInDictionary(lower) && !dictionary.gboardEngine.isKnownTypo(lower)
            else multilingual.frequency(lower, lang) > 0
        val evidence = linkedMapOf<String, MutableSet<CandidateOrigin>>()
        fun add(word: String?, origin: CandidateOrigin) {
            if (word.isNullOrBlank() || dictionary.isBlocked(word) || dictionary.isCorrectionSuppressed(typed, word)) return
            evidence.getOrPut(word.lowercase(Locale.ROOT)) { linkedSetOf() }.add(origin)
        }
        add(lower, CandidateOrigin.LITERAL)
        add(accepted, CandidateOrigin.ACCEPTED)
        if (lang in setOf("en", "und")) add(typo(lower), CandidateOrigin.TYPO)
        if (lang == "en" && !mixed) LocalGrammarSpellPredictor.contextCandidates(lower, prior).forEach { add(it, CandidateOrigin.CONTEXT) }
        if (!known && lower.length >= 2) {
            dictionary.findDictionaryCorrections(lower, if (lower.length <= 3) 1f else 2f, 16).forEach { add(it.term, CandidateOrigin.DICTIONARY) }
            val languages = (listOf(lang) + prior.filter { !dictionary.isWordInDictionary(it) && it !in MultilingualLexicon.romanizedMalayalam && it !in MultilingualLexicon.romanizedHindi }.flatMap { multilingual.languages(it) }).filter { it != "en" && it != "und" }.distinct().take(3)
            multilingual.corrections(lower, languages, 8).forEach { add(it.term, CandidateOrigin.DICTIONARY) }
            add(phonetics[lower], CandidateOrigin.PHONETIC)
            if (lang in setOf("en", "und")) dictionary.phoneticCandidates(lower).forEach { add(it, CandidateOrigin.PHONETIC) }
        }
        dictionary.personalCandidates(lower, prior).forEach { add(it, CandidateOrigin.PERSONAL) }
        dictionary.findWordsWithPrefix(lower, 5).filter { it.length > lower.length }.forEach { add(it, CandidateOrigin.COMPLETION) }
        multilingual.prefix(lower, listOf(lang), 4).forEach { if (it != lower) add(it, CandidateOrigin.COMPLETION) }
        val rawTap = if (taps?.size == lower.length) spatial.computeSpatialTouchLikelihood(lower, taps) else 0f
        val scored = evidence.entries.take(32).map { (word, sources) ->
            val literal = CandidateOrigin.LITERAL in sources
            val target = word.replace(" ", "")
            val distance = editDistance(lower, target)
            val keyboardDistance = spatial.computeSpatialEditDistance(lower, target)
            val touch = if (taps?.size == target.length) spatial.computeSpatialTouchLikelihood(target, taps) else rawTap
            val freq = maxOf(dictionary.getWordFrequency(word), multilingual.frequency(word, lang))
            val frequency = (ln(freq + 1f) / 12f).coerceIn(0f, 1f)
            val probability = dictionary.nGramModel.contextEvidence(word, prior)
            val personal = dictionary.personalBoost(word, prior)
            val acceptedScore = if (settings.personalizedLearningEnabled) profile.acceptedEvidence(typed, word, prior) else 0f
            val rejected = if (settings.personalizedLearningEnabled) profile.rejectionPenalty(typed, word) else 0f
            val casePenalty = if (word.any(Char::isUpperCase) && typed.none(Char::isUpperCase)) .1f else 0f
            val score = if (literal) (if (known) 2.6f else 1.45f) + personal + frequency * .15f else
                1.15f * (1f - distance / maxOf(3, lower.length).toFloat()).coerceIn(0f, 1f) +
                .30f * (1f - keyboardDistance / maxOf(3, lower.length)).coerceIn(0f, 1f) + .40f * frequency +
                .55f * probability + .35f * personal + .4f * (touch - rawTap) +
                (if (CandidateOrigin.TYPO in sources) 1.55f else 0f) +
                (if (CandidateOrigin.PHONETIC in sources) .25f else 0f) +
                (if (CandidateOrigin.ACCEPTED in sources) 1.8f + acceptedScore else 0f) +
                (if (CandidateOrigin.CONTEXT in sources) 1.85f else 0f) -
                rejected * 3f - casePenalty - (if (CandidateOrigin.COMPLETION in sources && sources.size == 1) .35f else 0f) -
                (if (lang !in setOf("en", "und") && multilingual.frequency(word, lang) == 0) .8f else 0f)
            RankedCandidate(TypingPolicy.restoreCase(typed, typo(lower)?.takeIf { it.equals(word, true) } ?: if (word in setOf("i", "i'm", "i'll", "i've", "i'd")) word.replaceFirstChar { it.uppercase() } else word), score, 0f, distance, keyboardDistance, frequency,
                probability, personal, acceptedScore, rejected, lang, sources)
        }.sortedByDescending { it.score }
        val best = scored.firstOrNull() ?: return RankedCorrection(typed, emptyList(), 0f, 0f, ConfidenceTier.LOW, lang, false)
        val total = scored.sumOf { exp(((it.score - best.score) / .18f).toDouble()) }
        val candidates = scored.map { it.copy(posterior = (exp(((it.score - best.score) / .18f).toDouble()) / total).toFloat()) }
        val margin = best.score - (scored.getOrNull(1)?.score ?: best.score)
        val confidence = candidates.first().posterior
        val completion = best.word.lowercase(Locale.ROOT).startsWith(lower) && best.word.length > typed.length
        val highEvidence = CandidateOrigin.TYPO in best.origins || CandidateOrigin.ACCEPTED in best.origins ||
            (!known && best.distance <= 1f && taps?.size == lower.length && spatial.computeSpatialTouchLikelihood(best.word, taps) > rawTap + .25f)
        val highThreshold = when (settings.autocorrectSensitivity) { KeyboardSettings.SENSITIVITY_MILD -> .995f; KeyboardSettings.SENSITIVITY_AGGRESSIVE -> .97f; else -> .99f }
        val tier = when {
            best.word == typed -> ConfidenceTier.LOW
            confidence >= highThreshold && margin >= .40f && highEvidence && best.rejected == 0f &&
                (!known || CandidateOrigin.ACCEPTED in best.origins) && (!completion || CandidateOrigin.TYPO in best.origins || CandidateOrigin.ACCEPTED in best.origins) -> ConfidenceTier.HIGH
            confidence >= .50f && margin >= .05f -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }
        val result = RankedCorrection(typed, candidates, confidence, margin, tier, lang, false)
        synchronized(cache) { cache[key(typed, contextWords, taps)] = result }
        return result
    }

    companion object {
        /** Damerau-Levenshtein: insertion, deletion, substitution, repeated letters and transposition. */
        private val editScratch = ThreadLocal.withInitial { IntArray(65 * 65) }
        fun editDistance(a: String, b: String): Float {
            if (a.length > 64 || b.length > 64) return maxOf(a.length, b.length).toFloat()
            val d = requireNotNull(editScratch.get()); val stride = 65
            for (i in 0..a.length) d[i * stride] = i
            for (j in 0..b.length) d[j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                val cell = i * stride + j
                d[cell] = minOf(d[cell - stride] + 1, d[cell - 1] + 1, d[cell - stride - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[cell] = minOf(d[cell], d[cell - 2 * stride - 2] + 1)
            }
            return d[a.length * stride + b.length].toFloat()
        }
    }
}
