package com.example

import android.content.Context
import android.graphics.PointF
import java.util.Locale
import kotlin.math.*

enum class CorrectionPhase { KEYSTROKE, WORD_BOUNDARY }
enum class RankingTask { CORRECTION, PREFIX, NEXT_WORD }
enum class CandidateOrigin { LITERAL, DICTIONARY, TYPO, PHONETIC, PERSONAL, ACCEPTED, CONTEXT, COMPLETION }
data class RankedCandidate(val word: String, val score: Float, val posterior: Float, val distance: Float,
    val keyboard: Float, val frequency: Float, val context: Float, val personal: Float,
    val accepted: Float, val rejected: Float, val language: String, val origins: Set<CandidateOrigin>,
    val features: OnlineTypingLearner.Features? = null)
data class RankedCorrection(val original: String, val candidates: List<RankedCandidate>, val confidence: Float,
    val margin: Float, val tier: ConfidenceTier, val language: String, val protected: Boolean, val autoEligible: Boolean = false) {
    val best get() = candidates.firstOrNull()
    val automatic: String? get() = best?.word?.takeIf { tier == ConfidenceTier.HIGH && it != original }
    val suggestion: String get() = if (tier == ConfidenceTier.LOW) original else best?.word ?: original
}

/** One worker-only pipeline. IME callbacks only read already ranked immutable results. */
class CandidateRanker(private val owner: Context, private val dictionary: DictionaryManager) {
    private val settings = KeyboardSettings(owner)
    private val multilingual = MultilingualLexicon.get(owner)
    private val english = EnglishFrequencyLexicon.get(owner)
    private val spatial by lazy { dictionary.gboardEngine.spatialModel }
    private val profile = dictionary.personalProfile
    val learner = OnlineTypingLearner(owner)
    private val baseline by lazy { owner.assets.open("dictionaries/ranker-baseline.tsv").bufferedReader().useLines { lines ->
        lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.substringAfter('\t').toFloat() }.toList().toFloatArray()
    } }
    private data class Key(val task: RankingTask, val word: String, val context: List<String>, val following: List<String>,
        val taps: Int, val language: String, val layout: String, val vocabulary: Int, val model: Long, val contextModel: Long, val revision: Long,
        val sensitivity: String, val learning: Boolean)
    private val cache = object : LinkedHashMap<Key, RankedCorrection>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RankedCorrection>?) = size > 128
    }
    private fun key(task: RankingTask, word: String, words: List<String>, taps: List<PointF?>?, following: List<String>, layout: String): Key {
        val prior = words.takeLast(5).map(MultilingualLexicon::normalize)
        val language = languageProbabilities(MultilingualLexicon.normalize(word.ifEmpty { prior.lastOrNull().orEmpty() }), if (word.isEmpty()) prior.dropLast(1) else prior).maxByOrNull { it.value }?.key ?: "und"
        return Key(task, word, prior, following.take(2), taps?.fold(1) { h,p -> 31*h + (p?.let { (it.x*1000).toInt() + 37*(it.y*1000).toInt() } ?: -1) } ?: 0,
            language, layout, english.canonical.size + dictionary.vocabularyVersion + UserDictionaryRepository.getInstance(owner).vocabularyVersion, learner.snapshot.version, dictionary.nGramModel.version, profile.revision,
            settings.autocorrectSensitivity, settings.personalizedLearningEnabled)
    }
    fun clearCache() = synchronized(cache) { cache.clear() }
    fun isProtectedPersonalWord(word: String): Boolean = dictionary.isWordInUserDictionary(word) || dictionary.isContactWord(word) || dictionary.isBlocked(word) ||
        (settings.personalizedLearningEnabled && profile.isTrusted(word) && !dictionary.isRecognizedInAnyLanguage(word))
    fun cached(word: String, words: List<String>, taps: List<PointF?>?, layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection? {
        if (dictionary.isContactWord(word) || dictionary.isBlocked(word)) return null
        val prior = words.takeLast(5).map(MultilingualLexicon::normalize)
        val touchKey = taps?.fold(1) { h,p -> 31*h + (p?.let { (it.x*1000).toInt()+37*(it.y*1000).toInt() } ?: -1) } ?: 0
        return synchronized(cache) { cache.entries.firstOrNull { (k,_) -> k.task == RankingTask.CORRECTION && k.word == word && k.context == prior && k.following.isEmpty() && k.taps == touchKey && k.layout == layout && k.model == learner.snapshot.version && k.contextModel == dictionary.nGramModel.version && k.revision == profile.revision && k.vocabulary == english.canonical.size+dictionary.vocabularyVersion+UserDictionaryRepository.getInstance(owner).vocabularyVersion && k.learning == settings.personalizedLearningEnabled && k.sensitivity == settings.autocorrectSensitivity }?.value?.takeUnless {
            it.best?.word?.let { target -> dictionary.isBlocked(target) || dictionary.isCorrectionSuppressed(word, target) } == true
        } }
    }
    suspend fun awaitDictionaries(languages: List<String> = emptyList()) { dictionary.ready.await(); profile.ready.await(); multilingual.warm(languages); english.ready.await(); baseline }

    /** Span probabilities remove dependence on dictionary load order. */
    internal fun languageProbabilities(word: String, prior: List<String>): Map<String, Float> {
        if (word in MultilingualLexicon.romanizedMalayalam) return mapOf("ml-Latn" to 1f)
        val hindiSpan = prior.any { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "hi", "par", "se", "fir", "bas") }
        if (word in MultilingualLexicon.romanizedHindi && (hindiSpan || !dictionary.isWordInDictionary(word))) return mapOf("hi-Latn" to 1f)
        val scores = linkedMapOf<String, Float>()
        val latin = word.all { !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
        if (latin) scores["en"] = if (dictionary.isWordInDictionary(word) || typo(word) != null) 6f else 2f
        multilingual.languages(word).forEach { lang ->
            val neighbours = prior.takeLast(5).sumOf { if (!dictionary.isWordInDictionary(it)) ln(multilingual.frequency(it, lang)+1.0) else 0.0 }.toFloat()
            scores[lang] = ln(multilingual.frequency(word, lang)+1f)/6f + neighbours/3f + if (latin) 0f else 4f
        }
        if (scores.isEmpty()) return mapOf("und" to 1f)
        val max = scores.values.max(); val total = scores.values.sumOf { exp((it-max).toDouble()) }
        return scores.mapValues { (exp((it.value-max).toDouble())/total).toFloat() }
    }
    private fun typo(word: String): String? = if (word in MultilingualLexicon.slang || word in MultilingualLexicon.romanizedMalayalam || word in MultilingualLexicon.romanizedHindi) null else dictionary.gboardEngine.typoProposal(word)
    fun nextWords(context: List<String>, limit: Int = 3): List<String> = rankTask("", context, null, RankingTask.NEXT_WORD).candidates.take(limit).map { it.word }
    fun nextPhrases(context: List<String>): List<String> =
        (dictionary.localGrammarPredictor.predictPhraseCompletions(context) + dictionary.nGramModel.predictNextPhrases(context,6))
            .distinct().filter { !dictionary.isBlocked(it) }.sortedByDescending { phrase ->
                val first=phrase.substringBefore(' ')
                dictionary.nGramModel.contextEvidence(first,context)+dictionary.personalBoost(first,context)
            }.take(3)
    fun prefix(typed: String, context: List<String>, taps: List<PointF?>? = null, layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection = rankTask(typed, context, taps, RankingTask.PREFIX, layout = layout)
    fun rank(typed: String, contextWords: List<String> = emptyList(), taps: List<PointF?>? = null,
        phase: CorrectionPhase = CorrectionPhase.WORD_BOUNDARY, following: List<String> = emptyList(), layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection = rankTask(typed, contextWords, taps, RankingTask.CORRECTION, following, layout)

    private fun rankTask(typed: String, contextWords: List<String>, taps: List<PointF?>?, task: RankingTask,
        following: List<String> = emptyList(), layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection {
        val requestKey = key(task, typed, contextWords, taps, following, layout)
        val model = learner.snapshot
        val lower = MultilingualLexicon.normalize(typed); val prior = requestKey.context; val lang = requestKey.language
        val mixed = prior.any { it in MultilingualLexicon.romanizedMalayalam || it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "par", "se", "hi") }
        val accepted = dictionary.learnedCorrection(typed, prior)
        val protected = typed.isNotEmpty() && (lower.length !in 1..32 || typed.any { !TypingPolicy.isWordCharacter(it) } || dictionary.isCodeOrSpecialToken(typed) ||
            dictionary.isBlocked(typed) || dictionary.isContactWord(typed) || lower in MultilingualLexicon.slang || lower in MultilingualLexicon.romanizedMalayalam ||
            lower.any { it.code > 127 } && multilingual.contains(lower) ||
            lower in MultilingualLexicon.romanizedHindi && (mixed || !dictionary.isWordInDictionary(lower)) ||
            mixed && !dictionary.isWordInDictionary(lower) && lower.endsWith("il") || isProtectedPersonalWord(lower) && accepted == null ||
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typo(lower) == null && accepted == null ||
            typed.firstOrNull()?.isUpperCase() == true && !dictionary.isWordInDictionary(lower) && typo(lower) == null && accepted == null)
        if (protected && task == RankingTask.CORRECTION) return RankedCorrection(typed, emptyList(), 0f, 0f, ConfidenceTier.LOW, lang, true)
        val known = if (lang == "en") dictionary.isWordInDictionary(lower) && !dictionary.gboardEngine.isKnownTypo(lower) else multilingual.frequency(lower, lang) > 0
        val evidence = linkedMapOf<String, MutableSet<CandidateOrigin>>()
        fun add(word: String?, origin: CandidateOrigin) {
            if (word.isNullOrBlank() || dictionary.isBlocked(word) || settings.profanityFilterEnabled && dictionary.isProfane(word) || dictionary.isCorrectionSuppressed(typed, word)) return
            evidence.getOrPut(word.lowercase(Locale.ROOT)) { linkedSetOf() }.add(origin)
        }
        if (task != RankingTask.NEXT_WORD) add(lower, CandidateOrigin.LITERAL)
        if (task == RankingTask.CORRECTION) {
            add(accepted, CandidateOrigin.ACCEPTED)
            if (lang in setOf("en", "und")) add(typo(lower), CandidateOrigin.TYPO)
            if (lang in setOf("en", "und")) add(mapOf("fone" to "phone", "enuf" to "enough", "nite" to "night", "frend" to "friend")[lower], CandidateOrigin.PHONETIC)
            if (lang == "en" && !mixed) LocalGrammarSpellPredictor.contextCandidates(lower, prior, following).forEach { add(it, CandidateOrigin.CONTEXT) }
            if (!known && lower.length >= 2) {
                if (lang in setOf("en", "und")) dictionary.findDictionaryCorrections(lower, if (lower.length <= 4) 1f else 2f, 20).forEach { add(it.term, CandidateOrigin.DICTIONARY) }
                multilingual.corrections(lower, listOf(lang).filter { it !in setOf("en", "und") }, 10).forEach { add(it.term, CandidateOrigin.DICTIONARY) }
                if (lang in setOf("en", "und")) dictionary.phoneticCandidates(lower).forEach { add(it, CandidateOrigin.PHONETIC) }
                if (lang in setOf("en", "und")) add(mapOf("fone" to "phone", "enuf" to "enough", "nite" to "night", "frend" to "friend")[lower], CandidateOrigin.PHONETIC)
            }
        } else {
            dictionary.personalCandidates(lower, prior).forEach { add(it, CandidateOrigin.PERSONAL) }
            UserDictionaryRepository.getInstance(owner).cachedCandidates(lower).forEach { add(it, CandidateOrigin.PERSONAL) }
            dictionary.nGramModel.predictNextWords(prior, lower, 16).forEach { add(it, CandidateOrigin.CONTEXT) }
            if (task == RankingTask.PREFIX) {
                dictionary.findWordsWithPrefix(lower, 12).forEach { add(it, CandidateOrigin.COMPLETION) }
                multilingual.prefix(lower, listOf(lang), 8).forEach { add(it, CandidateOrigin.COMPLETION) }
            } else {
                val native = when (lang) { "ml-Latn" -> listOf("ente", "enikku", "aanu", "alla", "varum", "nale"); "hi-Latn" -> listOf("hai", "nahi", "kal", "aaj", "kya", "karna"); else -> multilingual.frequent(lang) }
                native.forEach { add(it, CandidateOrigin.DICTIONARY) }
                (if (prior.isEmpty()) listOf("I", "The", "Hi") else listOf("the", "to", "and", "you")).forEach { add(it, CandidateOrigin.DICTIONARY) }
            }
        }
        val rawTap = learner.touchLikelihood(lower, taps, layout, spatial, model)
        val scored = evidence.entries.take(48).map { (word,sources) ->
            val literal = CandidateOrigin.LITERAL in sources; val target = word.replace(" ", "")
            val distance = if (task == RankingTask.NEXT_WORD) 0f else editDistance(lower, target)
            val kd = if (task == RankingTask.NEXT_WORD) 0f else spatial.computeSpatialEditDistance(lower,target)
            val frequency = (ln(maxOf(dictionary.getWordFrequency(word), multilingual.frequency(word,lang))+1f)/12f).coerceIn(0f,1f)
            val probability = if (CandidateOrigin.CONTEXT in sources && task == RankingTask.CORRECTION) 1f else dictionary.nGramModel.contextEvidence(word,prior)
            val personal = maxOf(dictionary.personalBoost(word,prior), if (task != RankingTask.CORRECTION && UserDictionaryRepository.getInstance(owner).isCustomWord(word)) .9f else 0f)
            val positive = if (settings.personalizedLearningEnabled) profile.acceptedEvidence(typed,word,prior) else 0f
            val rejected = if (settings.personalizedLearningEnabled) profile.rejectionPenalty(typed,word) else 0f
            val touch = learner.touchLikelihood(target,taps,layout,spatial,model) - rawTap
            val targetKnown = dictionary.isWordInDictionary(word) || multilingual.frequency(word,lang) > 0 || CandidateOrigin.TYPO in sources || CandidateOrigin.ACCEPTED in sources
            val numeric = floatArrayOf(if(literal)1f else 0f, if(targetKnown && (!literal || known))1f else 0f,
                distance, (1f-distance/maxOf(3,lower.length)).coerceAtLeast(0f), kd/maxOf(3,lower.length), frequency, probability, personal, positive, rejected, touch,
                if(CandidateOrigin.TYPO in sources)1f else 0f, if(CandidateOrigin.CONTEXT in sources && task == RankingTask.CORRECTION)1f else 0f,
                if(CandidateOrigin.COMPLETION in sources && sources.size == 1)1f else 0f,
                lower.commonPrefixWith(target).length.toFloat()/maxOf(1,lower.length),
                lower.commonSuffixWith(target).length.toFloat()/maxOf(1,lower.length),
                if (target.length > lower.length && isSubsequence(lower,target)) 1f else 0f,
                if (lower != target && collapseRepeats(lower) == collapseRepeats(target)) 1f else 0f)
            val features = OnlineTypingLearner.features(task,word,lower,prior,lang,numeric, if(settings.personalizedLearningEnabled) profile.recency(word) else 0f)
            val score = if(task == RankingTask.CORRECTION) numeric.indices.sumOf { (numeric[it]*baseline[it]).toDouble() }.toFloat() +
                (if(settings.personalizedLearningEnabled) model.score(features).coerceIn(-2f,2f) else 0f) else
                probability*2f + frequency*.3f + personal*2f + (if(settings.personalizedLearningEnabled) model.score(features).coerceIn(-3f,3f) else 0f) - (if(literal).25f else 0f) +
                (if(lang == "ml-Latn" && word in MultilingualLexicon.romanizedMalayalam || lang == "hi-Latn" && word in MultilingualLexicon.romanizedHindi) .5f else 0f)
            val restored = TypingPolicy.restoreCase(typed, if(word in setOf("i", "i'm", "i'll", "i've", "i'd")) word.replaceFirstChar { it.uppercase() } else word)
            RankedCandidate(restored,score,0f,distance,kd,frequency,probability,personal,positive,rejected,lang,sources,features)
        }.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.word })
        val best = scored.firstOrNull() ?: return RankedCorrection(typed,emptyList(),0f,0f,ConfidenceTier.LOW,lang,false)
        val total = scored.sumOf { exp(((it.score-best.score)/TEMPERATURE).toDouble()) }
        val candidates = scored.map { it.copy(posterior=(exp(((it.score-best.score)/TEMPERATURE).toDouble())/total).toFloat()) }
        val margin = best.score - (scored.getOrNull(1)?.score ?: best.score); val confidence = candidates.first().posterior
        val completion = best.word.lowercase(Locale.ROOT).startsWith(lower) && best.word.length > typed.length
        val contextual = CandidateOrigin.CONTEXT in best.origins
        val strong = CandidateOrigin.ACCEPTED in best.origins || contextual || CandidateOrigin.TYPO in best.origins ||
            (!known && best.distance <= 2 && lower.length >= 4 && dictionary.isWordInDictionary(best.word))
        val nearest = candidates.filter { CandidateOrigin.LITERAL !in it.origins && it.distance <= best.distance && dictionary.isWordInDictionary(it.word) }
        val distinctEditEvidence = nearest.size <= 1 ||
            // Apostrophe restoration has exact literal letter evidence, unlike word completion.
            (best.word.contains('\'') && best.word.replace("'", "").equals(lower,true)) ||
            (best.distance <= 1 && best.word.length == lower.length && best.keyboard <= .55f && nearest.filter { it.word.length == lower.length && it.word != best.word }.all { best.frequency - it.frequency >= .15f }) ||
            (isSubsequence(lower,best.word.lowercase()) && lower.firstOrNull() == best.word.firstOrNull() && lower.takeLast(2) == best.word.lowercase().takeLast(2) && nearest.count { isSubsequence(lower,it.word.lowercase()) && lower.takeLast(2) == it.word.lowercase().takeLast(2) } == 1) ||
            (singleTransposition(lower,best.word.lowercase()) && nearest.count { singleTransposition(lower,it.word.lowercase()) } == 1) ||
            (lower.length > best.word.length && lower.zipWithNext().zipWithNext().any { (a,b) -> a.first == a.second && a.second == b.second } && collapseRepeats(lower) == collapseRepeats(best.word.lowercase()))
        val high = when(settings.autocorrectSensitivity) { KeyboardSettings.SENSITIVITY_MILD -> .999f; KeyboardSettings.SENSITIVITY_AGGRESSIVE -> .985f; else -> HIGH_THRESHOLD }
        val eligible = task == RankingTask.CORRECTION && best.word != typed && strong && best.rejected == 0f &&
            (distinctEditEvidence || contextual || CandidateOrigin.ACCEPTED in best.origins) &&
            (!known || contextual || CandidateOrigin.ACCEPTED in best.origins) &&
            (!completion || CandidateOrigin.TYPO in best.origins || CandidateOrigin.ACCEPTED in best.origins)
        val tier = when {
            task != RankingTask.CORRECTION || best.word == typed -> ConfidenceTier.LOW
            typed == "i" && best.word == "I" -> ConfidenceTier.HIGH
            confidence >= high && eligible -> ConfidenceTier.HIGH
            confidence >= .5f && margin > .05f -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }
        val result = RankedCorrection(typed,candidates,confidence,margin,tier,lang,false,eligible)
        synchronized(cache) { cache[requestKey] = result }
        return result
    }
    fun feedback(typed: String, chosen: String, context: List<String>, strength: Float = 1f, task: RankingTask = RankingTask.CORRECTION) {
        if (!settings.personalizedLearningEnabled) return
        val result = rankTask(typed,context,null,task)
        val winner = result.candidates.firstOrNull { it.word.equals(chosen,true) } ?: return
        val alternative = result.candidates.firstOrNull { !it.word.equals(chosen,true) } ?: return
        learner.feedback(winner.features ?: return, alternative.features ?: return,strength)
    }
    fun recordRejection(source: String, target: String) {
        val ranked = synchronized(cache) { cache.entries.lastOrNull { it.key.word.equals(source,true) }?.value } ?: return
        val literal = ranked.candidates.firstOrNull { it.word.equals(source,true) }?.features ?: return
        val replacement = ranked.candidates.firstOrNull { it.word.equals(target,true) }?.features ?: return
        learner.feedback(literal,replacement,2f)
    }
    /** Worker feedback after accepted polish; receipts contain bounded token pairs, never messages. */
    fun acceptedPolish(receipts: List<String>) {
        if (!settings.personalizedLearningEnabled) return
        for (receipt in receipts) {
            val parts = receipt.split('|')
            if (parts.size != 3) continue
            val prior = if(parts[0] == "*") emptyList() else parts[0].split(' ')
            feedback(parts[1],parts[2],prior,2f)
        }
    }
    companion object {
        const val HIGH_THRESHOLD = .99f
        // Frozen by tools/calibrate_typing_ranker.py using the word-disjoint calibration partition.
        const val TEMPERATURE = .20f
        private fun isSubsequence(a: String,b: String): Boolean { var i=0; for(c in b) if(i<a.length && a[i]==c) i++; return i==a.length }
        private fun collapseRepeats(word: String) = word.filterIndexed { i,c -> i == 0 || word[i-1] != c }
        private fun singleTransposition(a: String,b: String): Boolean {
            if(a.length != b.length) return false
            val different = a.indices.filter { a[it] != b[it] }
            return different.size == 2 && different[1] == different[0]+1 && a[different[0]] == b[different[1]] && a[different[1]] == b[different[0]]
        }
        private val editScratch = ThreadLocal.withInitial { IntArray(65*65) }
        fun editDistance(a: String,b: String): Float {
            if(a.length > 64 || b.length > 64) return maxOf(a.length,b.length).toFloat()
            val d = requireNotNull(editScratch.get()); val stride = 65
            for(i in 0..a.length) d[i*stride]=i
            for(j in 0..b.length) d[j]=j
            for(i in 1..a.length) for(j in 1..b.length) {
                val cell=i*stride+j
                d[cell]=minOf(d[cell-stride]+1,d[cell-1]+1,d[cell-stride-1]+if(a[i-1]==b[j-1])0 else 1)
                if(i>1 && j>1 && a[i-1]==b[j-2] && a[i-2]==b[j-1]) d[cell]=minOf(d[cell],d[cell-2*stride-2]+1)
            }
            return d[a.length*stride+b.length].toFloat()
        }
    }
}
