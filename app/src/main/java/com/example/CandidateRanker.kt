package com.example

import android.content.Context
import android.graphics.PointF
import java.util.Locale
import kotlin.math.*

enum class RankingTask { CORRECTION, PREFIX, NEXT_WORD, SWIPE }
enum class CandidateOrigin { LITERAL, DICTIONARY, TYPO, PHONETIC, PERSONAL, ACCEPTED, CONTEXT, COMPLETION, GESTURE }
data class RankedCandidate(val word: String, val score: Float, val posterior: Float, val distance: Float,
    val keyboard: Float, val frequency: Float, val context: Float, val personal: Float,
    val accepted: Float, val rejected: Float, val language: String, val origins: Set<CandidateOrigin>,
    val features: OnlineTypingLearner.Features? = null, val languageProbability: Float = 0f,
    val variantFamily: String? = null, val baselineFeatures: FloatArray? = null)
data class RankedCorrection(val original: String, val candidates: List<RankedCandidate>, val confidence: Float,
    val margin: Float, val tier: ConfidenceTier, val language: String, val protected: Boolean, val autoEligible: Boolean = false,
    val languageProbabilities: Map<String,Float> = emptyMap(), val vocabularyVersion: Int = 0, val languageModelVersion: Int = 0) {
    val best get() = candidates.firstOrNull()
    val automatic: String? get() = best?.word?.takeIf { tier == ConfidenceTier.HIGH && it != original }
    val suggestion: String get() = if (tier == ConfidenceTier.LOW) original else best?.word ?: original
}

/** One worker-only pipeline. IME callbacks only read already ranked immutable results. */
class CandidateRanker(private val owner: Context, private val dictionary: DictionaryManager) {
    private val settings = KeyboardSettings(owner)
    private val multilingual = MultilingualLexicon.get(owner)
    private val english = EnglishFrequencyLexicon.get(owner)
    private val manglish = RomanizedMalayalamLexicon.get(owner)
    private val languages = TypingLanguageDetector(manglish,dictionary)
    private val spatial by lazy { dictionary.gboardEngine.spatialModel }
    private val profile = dictionary.personalProfile
    val learner = OnlineTypingLearner(owner)
    private val baseline by lazy { owner.assets.open("dictionaries/ranker-baseline.tsv").bufferedReader().useLines { lines ->
        lines.filter { !it.startsWith("#") && it.isNotBlank() }.map { it.substringAfter('\t').toFloat() }.toList().toFloatArray()
    } }
    private val calibration by lazy { owner.assets.open("dictionaries/ranker-calibration.tsv").bufferedReader().useLines { lines ->
        lines.associate { line -> val p=line.split('\t'); p[0] to (p[1].toFloat() to p[2].toFloat()) }
    } }
    private data class Key(val task: RankingTask, val word: String, val context: List<String>, val following: List<String>,
        val taps: Int, val language: String, val layout: String, val vocabulary: Int, val model: Long, val contextModel: Long, val revision: Long,
        val sensitivity: String, val learning: Boolean, val script: Boolean, val bilingual: Int, val profanity: Boolean)
    private val cache = object : LinkedHashMap<Key, RankedCorrection>(128, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, RankedCorrection>?) = size > 128
    }
    private data class SpellingPair(val typed: String,val target: String,val layout: String)
    private data class SpellingEvidence(val distance: Float,val keyboard: Float,val prefix: Float,val suffix: Float,
        val insertion: Float,val repeated: Float)
    // Only invariant spelling/geometry evidence is reused. Context, touches,
    // language, vocabulary and personal scoring always use the current snapshot.
    private val spellingEvidence=object : LinkedHashMap<SpellingPair,SpellingEvidence>(2048,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<SpellingPair,SpellingEvidence>?)=size>2048
    }
    private fun spelling(typed: String,target: String,layout: String): SpellingEvidence {
        val key=SpellingPair(typed,target,layout)
        synchronized(spellingEvidence) { spellingEvidence[key]?.let { return it } }
        val result=SpellingEvidence(editDistance(typed,target),spatial.computeSpatialEditDistance(typed,target),
            typed.commonPrefixWith(target).length.toFloat()/maxOf(1,typed.length),
            typed.commonSuffixWith(target).length.toFloat()/maxOf(1,typed.length),
            if(target.length>typed.length && isSubsequence(typed,target))1f else 0f,
            if(typed!=target && collapseRepeats(typed)==collapseRepeats(target))1f else 0f)
        synchronized(spellingEvidence) { spellingEvidence[key]=result }
        return result
    }
    private fun key(task: RankingTask, word: String, prior: List<String>, taps: List<PointF?>?, following: List<String>, layout: String, language: String): Key {
        return Key(task, word, prior, following.take(2), taps?.fold(1) { h,p -> 31*h + (p?.let { (it.x*1000).toInt() + 37*(it.y*1000).toInt() } ?: -1) } ?: 0,
            language, layout, english.canonical.size + dictionary.vocabularyVersion + UserDictionaryRepository.getInstance(owner).vocabularyVersion, learner.snapshot.version, dictionary.nGramModel.version, profile.revision,
            settings.autocorrectSensitivity, settings.personalizedLearningEnabled,settings.isMalayalamScriptMode,manglish.version,settings.profanityFilterEnabled)
    }
    fun clearCache() {
        synchronized(cache) { cache.clear() }
        synchronized(spellingEvidence) { spellingEvidence.clear() }
    }
    fun isProtectedPersonalWord(word: String): Boolean = dictionary.isWordInUserDictionary(word) || dictionary.isContactWord(word) || dictionary.isBlocked(word) ||
        (settings.personalizedLearningEnabled && profile.isTrusted(word) && (!dictionary.isWordInDictionary(word) || dictionary.gboardEngine.isKnownTypo(word)))
    fun cached(word: String, words: List<String>, taps: List<PointF?>?, layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection? {
        if (dictionary.isContactWord(word) || dictionary.isBlocked(word)) return null
        val prior = words.takeLast(5).map(MultilingualLexicon::normalize)
        val touchKey = taps?.fold(1) { h,p -> 31*h + (p?.let { (it.x*1000).toInt()+37*(it.y*1000).toInt() } ?: -1) } ?: 0
        return synchronized(cache) { cache.entries.firstOrNull { (k,_) -> k.task == RankingTask.CORRECTION && k.word == word && k.context == prior && k.following.isEmpty() && k.taps == touchKey && k.layout == layout && k.model == learner.snapshot.version && k.contextModel == dictionary.nGramModel.version && k.revision == profile.revision && k.vocabulary == english.canonical.size+dictionary.vocabularyVersion+UserDictionaryRepository.getInstance(owner).vocabularyVersion && k.learning == settings.personalizedLearningEnabled && k.sensitivity == settings.autocorrectSensitivity && k.bilingual == manglish.version && k.script == settings.isMalayalamScriptMode && k.profanity == settings.profanityFilterEnabled }?.value?.takeUnless {
            it.best?.word?.let { target -> dictionary.isBlocked(target) || dictionary.isCorrectionSuppressed(word, target) } == true
        } }
    }
    suspend fun awaitDictionaries(languages: List<String> = emptyList()) {
        dictionary.ready.await(); dictionary.typingAssetsReady.await(); profile.ready.await(); multilingual.warm(languages); english.ready.await(); baseline; calibration
    }

    /** Span probabilities remove dependence on dictionary load order. */
    private fun languageProbabilities(word: String, prior: List<String>, following: List<String> = emptyList(), evidence: TypingLanguageDetector.Evidence = languages.detect(word,prior,following)): Map<String, Float> {
        val hindiSpan = prior.any { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "hi", "par", "se", "fir", "bas") }
        if (word.isEmpty() && hindiSpan && prior.lastOrNull() in MultilingualLexicon.romanizedHindi) return mapOf("hi-Latn" to .9f, "en" to .1f)
        if (word in MultilingualLexicon.romanizedHindi && (hindiSpan || !dictionary.isWordInDictionary(word))) return mapOf("hi-Latn" to 1f)
        val scores = linkedMapOf<String, Float>()
        val latin = word.all { !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }
        if(latin) {
            evidence.probabilities.forEach { (lang,p) -> scores[lang]=ln(p.coerceAtLeast(.001f)) }
        }
        multilingual.languages(word).forEach { lang ->
            val neighbours = prior.takeLast(5).sumOf { if (!dictionary.isWordInDictionary(it)) ln(multilingual.frequency(it, lang)+1.0) else 0.0 }.toFloat()
            // Other Latin-script dictionaries provide competing evidence on the
            // same log-probability scale, rather than overpowering English just
            // because their raw frequencies are positive (e.g. Spanish "sea").
            scores[lang] = ln(multilingual.frequency(word, lang)+1f)/6f + neighbours/3f +
                if(latin) (if(dictionary.isWordInDictionary(word)) -6f else -2f) else 4f
        }
        if (scores.isEmpty()) return mapOf("und" to 1f)
        val max = scores.values.max(); val total = scores.values.sumOf { exp((it-max).toDouble()) }
        return scores.mapValues { (exp((it.value-max).toDouble())/total).toFloat() }
    }
    private fun typo(word: String): String? = if (word in MultilingualLexicon.slang || word in MultilingualLexicon.romanizedHindi) null else dictionary.gboardEngine.typoProposal(word)
    fun nextWords(context: List<String>, limit: Int = 3): List<String> = rankTask("", context, null, RankingTask.NEXT_WORD).candidates.take(limit).map { it.word }
    fun nextPhrases(context: List<String>): List<String> =
        (dictionary.localGrammarPredictor.predictPhraseCompletions(context) + dictionary.nGramModel.predictNextPhrases(context,6))
            .distinct().filter { !dictionary.isBlocked(it) }.sortedByDescending { phrase ->
                val first=phrase.substringBefore(' ')
                dictionary.nGramModel.contextEvidence(first,context)+dictionary.personalBoost(first,context)
            }.take(3)
    fun prefix(typed: String, context: List<String>, taps: List<PointF?>? = null, layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection = rankTask(typed, context, taps, RankingTask.PREFIX, layout = layout)
    fun rank(typed: String, contextWords: List<String> = emptyList(), taps: List<PointF?>? = null,
        following: List<String> = emptyList(), layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection = rankTask(typed, contextWords, taps, RankingTask.CORRECTION, following, layout)

    private fun rankTask(typed: String, contextWords: List<String>, taps: List<PointF?>?, task: RankingTask,
        following: List<String> = emptyList(), layout: String = OnlineTypingLearner.DEFAULT_LAYOUT): RankedCorrection {
        val lower = MultilingualLexicon.normalize(typed); val prior = contextWords.takeLast(5).map(MultilingualLexicon::normalize)
        val languageEvidence=languages.detect(lower,prior,following)
        val probabilities=languageProbabilities(lower,prior,following,languageEvidence)
        val lang=probabilities.maxByOrNull { it.value }?.key ?: "und"
        val requestKey = key(task, typed, prior, taps, following, layout,lang)
        val model = learner.snapshot
        val learningEnabled=settings.personalizedLearningEnabled
        val filterProfanity=settings.profanityFilterEnabled
        val mixed = languageEvidence.malayalamContext || prior.any { it in MultilingualLexicon.romanizedHindi && it !in setOf("main", "par", "se", "hi") }
        val latin=lower.all { it.code<128 }
        val accepted = dictionary.learnedCorrection(typed, prior)
        // Imported romanizations include English loans ("helo", "mesage").
        // Preserve them in Malayalam spans; clear English spelling evidence can
        // correct a loan outside those spans. Authored variants and suffixes
        // remain protected regardless of sentence language.
        val manglishLiteral = manglish.isProtected(lower,dictionary::isWordInDictionary) &&
            (manglish.entry(lower)?.conversational == true || languageEvidence.malayalamContext || typo(lower) == null)
        val protected = typed.isNotEmpty() && (lower.length !in 1..32 || typed.any { !TypingPolicy.isWordCharacter(it) } || dictionary.isCodeOrSpecialToken(typed) ||
            dictionary.isBlocked(typed) || dictionary.isContactWord(typed) || lower in MultilingualLexicon.slang ||
            manglishLiteral && (languageEvidence.malayalamContext || accepted==null || manglish.family(lower)==manglish.family(accepted)) ||
            lower.any { it.code > 127 } && multilingual.contains(lower) ||
            lower in MultilingualLexicon.romanizedHindi && (mixed || !dictionary.isWordInDictionary(lower)) ||
            isProtectedPersonalWord(lower) && accepted == null ||
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } && typo(lower) == null && accepted == null ||
            typed.firstOrNull()?.isUpperCase() == true && !dictionary.isWordInDictionary(lower) && typo(lower) == null && accepted == null)
        if (protected && task == RankingTask.CORRECTION) return RankedCorrection(typed, emptyList(), 0f, 0f, ConfidenceTier.LOW, lang, true)
        val known = if(latin) (dictionary.isWordInDictionary(lower) && !dictionary.gboardEngine.isKnownTypo(lower)) || manglishLiteral
            else multilingual.frequency(lower,lang)>0
        val evidence = linkedMapOf<String, MutableSet<CandidateOrigin>>()
        fun add(word: String?, origin: CandidateOrigin) {
            if (word.isNullOrBlank() || dictionary.isBlocked(word) || filterProfanity && dictionary.isProfane(word) || dictionary.isCorrectionSuppressed(typed, word)) return
            evidence.getOrPut(word.lowercase(Locale.ROOT)) { linkedSetOf() }.add(origin)
        }
        if (task != RankingTask.NEXT_WORD) add(lower, CandidateOrigin.LITERAL)
        if (task == RankingTask.CORRECTION) {
            add(accepted, CandidateOrigin.ACCEPTED)
            if(latin) add(typo(lower),CandidateOrigin.TYPO)
            if(latin && (languageEvidence.probabilities["en"] ?: 0f)>=.9f && (!mixed || languageEvidence.englishContext))
                LocalGrammarSpellPredictor.contextCandidates(lower, prior, following).forEach { add(it,CandidateOrigin.CONTEXT) }
            if (!known && lower.length >= 2) {
                if(latin) dictionary.findDictionaryCorrections(lower,if(lower.length<=4)1f else 2f,20).forEach { add(it.term,CandidateOrigin.DICTIONARY) }
                if(latin && (languageEvidence.malayalamContext || (probabilities["ml-Latn"] ?: 0f)>.40f)) {
                    manglish.corrections(lower,if(lower.length<=4)1f else 2f,16).forEach { add(it.term,CandidateOrigin.DICTIONARY) }
                    manglish.phoneticCandidates(lower).forEach { add(it,CandidateOrigin.PHONETIC) }
                }
                multilingual.corrections(lower,listOf(lang).filter { it !in setOf("en","und","ml-Latn") },10).forEach { add(it.term,CandidateOrigin.DICTIONARY) }
                if(latin) dictionary.phoneticCandidates(lower).forEach { add(it,CandidateOrigin.PHONETIC) }
            }
        } else {
            dictionary.personalCandidates(lower, prior).forEach { add(it, CandidateOrigin.PERSONAL) }
            UserDictionaryRepository.getInstance(owner).cachedCandidates(lower).forEach { add(it, CandidateOrigin.PERSONAL) }
            dictionary.nGramModel.predictNextWords(prior, lower, 16).forEach { add(it, CandidateOrigin.CONTEXT) }
            if (task == RankingTask.PREFIX) {
                dictionary.findWordsWithPrefix(lower, 12).forEach { add(it, CandidateOrigin.COMPLETION) }
                if(latin) manglish.prefix(lower,12).forEach { add(it,CandidateOrigin.COMPLETION) }
                multilingual.prefix(lower,listOf(lang).filter { it!="ml-Latn" },8).forEach { add(it,CandidateOrigin.COMPLETION) }
            } else {
                val native = when (lang) { "ml-Latn" -> manglish.frequent(); "hi-Latn" -> listOf("hai", "nahi", "kal", "aaj", "kya", "karna"); else -> multilingual.frequent(lang) }
                native.forEach { add(it, CandidateOrigin.DICTIONARY) }
                if(languageEvidence.malayalamContext) manglish.frequent().take(12).forEach { add(it,CandidateOrigin.DICTIONARY) }
                (if (prior.isEmpty()) listOf("I", "The", "Hi") else listOf("the", "to", "and", "you")).forEach { add(it, CandidateOrigin.DICTIONARY) }
            }
        }
        val rawTap = learner.touchLikelihood(lower, taps, layout, spatial, model)
        val contextScorer=dictionary.nGramModel.scorer(prior)
        val scored = evidence.entries.take(48).map { (word,sources) ->
            val candidateLanguage=if(latin) when {
                dictionary.isWordInDictionary(word) && manglish.entry(word)?.conversational!=true -> "en"
                manglish.contains(word) -> "ml-Latn"
                else -> lang
            } else lang
            val literal = CandidateOrigin.LITERAL in sources; val target = word.replace(" ", "")
            val spelling=spelling(lower,target,layout)
            val distance = if (task == RankingTask.NEXT_WORD) 0f else spelling.distance
            val kd = if (task == RankingTask.NEXT_WORD) 0f else spelling.keyboard
            val frequency = (ln(maxOf(dictionary.getWordFrequency(word),multilingual.frequency(word,lang),manglish.frequency(word))+1f)/12f).coerceIn(0f,1f)
            val probability = if (CandidateOrigin.CONTEXT in sources && task == RankingTask.CORRECTION) 1f else contextScorer.evidence(word)
            val personal = maxOf(dictionary.personalBoost(word,prior), if (task != RankingTask.CORRECTION && UserDictionaryRepository.getInstance(owner).isCustomWord(word)) .9f else 0f)
            val positive = if (learningEnabled) profile.acceptedEvidence(typed,word,prior) else 0f
            val rejected = if (learningEnabled) profile.rejectionPenalty(typed,word) else 0f
            val touch = learner.touchLikelihood(target,taps,layout,spatial,model) - rawTap
            val targetKnown = dictionary.isWordInDictionary(word) || manglish.contains(word) || multilingual.frequency(word,lang) > 0 || CandidateOrigin.TYPO in sources || CandidateOrigin.ACCEPTED in sources
            val numeric = floatArrayOf(if(literal)1f else 0f, if(targetKnown && (!literal || known))1f else 0f,
                distance, (1f-distance/maxOf(3,lower.length)).coerceAtLeast(0f), kd/maxOf(3,lower.length), frequency, probability, personal, positive, rejected, touch,
                if(CandidateOrigin.TYPO in sources)1f else 0f, if(CandidateOrigin.CONTEXT in sources && task == RankingTask.CORRECTION)1f else 0f,
                if(CandidateOrigin.COMPLETION in sources && sources.size == 1)1f else 0f,
                spelling.prefix,spelling.suffix,spelling.insertion,spelling.repeated,
                probabilities[candidateLanguage] ?: 0f,
                if(literal && manglish.contains(lower))1f else 0f,
                if(languageEvidence.malayalamContext && candidateLanguage=="ml-Latn")1f else 0f)
            val features = OnlineTypingLearner.features(task,word,lower,prior,candidateLanguage,numeric,
                if(learningEnabled) profile.recency(word) else 0f,manglish.family(word),mixed)
            val score = if(task == RankingTask.CORRECTION) numeric.indices.sumOf { (numeric[it]*baseline.getOrElse(it) { 0f }).toDouble() }.toFloat() +
                (if(learningEnabled) model.score(features).coerceIn(-2f,2f) else 0f) else
                probability*2f + frequency*.3f + personal*2f + (if(learningEnabled) model.score(features).coerceIn(-3f,3f) else 0f) - (if(literal).25f else 0f) +
                (probabilities[candidateLanguage] ?: 0f)*.5f
            val restored = TypingPolicy.restoreCase(typed, if(word in setOf("i", "i'm", "i'll", "i've", "i'd")) word.replaceFirstChar { it.uppercase() } else word)
            RankedCandidate(restored,score,0f,distance,kd,frequency,probability,personal,positive,rejected,candidateLanguage,sources,features,
                probabilities[candidateLanguage] ?: 0f,manglish.family(word),numeric)
        }.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.word })
        val best = scored.firstOrNull() ?: return RankedCorrection(typed,emptyList(),0f,0f,ConfidenceTier.LOW,lang,false)
        val (temperature, threshold) = calibration[best.language] ?: calibration.getValue("en")
        val total = scored.sumOf { exp(((it.score-best.score)/temperature).toDouble()) }
        val candidates = scored.map { it.copy(posterior=(exp(((it.score-best.score)/temperature).toDouble())/total).toFloat()) }
        val margin = best.score - (scored.getOrNull(1)?.score ?: best.score); val confidence = candidates.first().posterior
        val completion = best.word.lowercase(Locale.ROOT).startsWith(lower) && best.word.length > typed.length
        val contextual = CandidateOrigin.CONTEXT in best.origins
        val strong = CandidateOrigin.ACCEPTED in best.origins || contextual || CandidateOrigin.TYPO in best.origins ||
            (!known && best.distance <= 2 && lower.length >= 4 && (dictionary.isWordInDictionary(best.word) || manglish.contains(best.word)))
        val nearest = candidates.filter { CandidateOrigin.LITERAL !in it.origins && it.distance <= best.distance && (dictionary.isWordInDictionary(it.word) || manglish.contains(it.word)) }
        val distinctEditEvidence = if(best.language=="ml-Latn") {
            // Romanization length and doubled consonants can change meaning.
            // A frequency gap cannot authorize switching between equally close
            // native-word families, even when their ending keys are adjacent.
            nearest.all { it.variantFamily!=null && it.variantFamily==best.variantFamily }
        } else nearest.size <= 1 ||
            // Apostrophe restoration has exact literal letter evidence, unlike word completion.
            (best.word.contains('\'') && best.word.replace("'", "").equals(lower,true)) ||
            (best.distance <= 1 && best.word.length == lower.length && best.keyboard <= .55f && nearest.filter { it.word.length == lower.length && it.word != best.word }.all { best.frequency - it.frequency >= .15f }) ||
            (isSubsequence(lower,best.word.lowercase()) && lower.firstOrNull() == best.word.firstOrNull() && lower.takeLast(2) == best.word.lowercase().takeLast(2) && nearest.count { isSubsequence(lower,it.word.lowercase()) && lower.takeLast(2) == it.word.lowercase().takeLast(2) } == 1) ||
            (singleTransposition(lower,best.word.lowercase()) && nearest.count { singleTransposition(lower,it.word.lowercase()) } == 1) ||
            (lower.length > best.word.length && lower.zipWithNext().zipWithNext().any { (a,b) -> a.first == a.second && a.second == b.second } && collapseRepeats(lower) == collapseRepeats(best.word.lowercase()))
        val high = when(settings.autocorrectSensitivity) { KeyboardSettings.SENSITIVITY_MILD -> maxOf(.999f,threshold); KeyboardSettings.SENSITIVITY_AGGRESSIVE -> minOf(.985f,threshold); else -> threshold }
        val eligible = task == RankingTask.CORRECTION && best.word != typed && strong && best.rejected == 0f &&
            (distinctEditEvidence || contextual || CandidateOrigin.ACCEPTED in best.origins || CandidateOrigin.TYPO in best.origins) &&
            (!known || contextual || CandidateOrigin.ACCEPTED in best.origins) &&
            (!completion || CandidateOrigin.ACCEPTED in best.origins)
        val tier = when {
            task != RankingTask.CORRECTION || best.word == typed -> ConfidenceTier.LOW
            typed == "i" && best.word == "I" -> ConfidenceTier.HIGH
            confidence >= high && eligible -> ConfidenceTier.HIGH
            confidence >= .5f && margin > .05f -> ConfidenceTier.MEDIUM
            else -> ConfidenceTier.LOW
        }
        val result = RankedCorrection(typed,candidates,confidence,margin,tier,lang,false,eligible,probabilities,manglish.version,manglish.version)
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
    /** Gesture decoder supplies geometry proposals; final vocabulary/context/personal scoring is here. */
    internal fun rankSwipe(proposals: List<SwipeProposal>, context: List<String>, learningAllowed: Boolean = true): List<RankedCandidate> {
        val prior=context.takeLast(5).map(MultilingualLexicon::normalize)
        val evidence=languageProbabilities("",prior)
        val model=learner.snapshot
        val contextScorer=dictionary.nGramModel.scorer(prior)
        return proposals.asSequence().filter { !dictionary.isBlocked(it.word) }.map { proposal ->
            val word=proposal.word.lowercase(Locale.ROOT)
            val lang=if(manglish.contains(word) && (!dictionary.isWordInDictionary(word) || manglish.entry(word)?.conversational==true)) "ml-Latn" else "en"
            val frequency=(ln(maxOf(dictionary.getWordFrequency(word),manglish.frequency(word))+1f)/12f).coerceIn(0f,1f)
            val probability=contextScorer.evidence(word)
            val personal=if(learningAllowed) dictionary.personalBoost(word,prior) else 0f
            val numeric=FloatArray(21).also { it[1]=1f; it[4]=proposal.geometry; it[5]=frequency; it[6]=probability; it[7]=personal; it[10]=proposal.template; it[18]=evidence[lang] ?: 0f }
            val features=OnlineTypingLearner.features(RankingTask.SWIPE,word,"",prior,lang,numeric,
                if(learningAllowed && settings.personalizedLearningEnabled) profile.recency(word) else 0f,manglish.family(word),prior.any(manglish::contains))
            val score=-proposal.geometry*12f+probability*.8f+frequency*.45f+personal*.8f+
                (evidence[lang] ?: 0f)*.15f+proposal.template*.4f+
                if(learningAllowed && settings.personalizedLearningEnabled) model.score(features).coerceIn(-1f,1f) else 0f
            RankedCandidate(word,score,0f,0f,proposal.geometry,frequency,probability,personal,0f,0f,lang,setOf(CandidateOrigin.GESTURE),features,evidence[lang] ?: 0f,manglish.family(word),numeric)
        }.sortedWith(compareByDescending<RankedCandidate> { it.score }.thenBy { it.word }).take(5).toList()
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

internal data class SwipeProposal(val word: String, val geometry: Float, val template: Float = 0f)
