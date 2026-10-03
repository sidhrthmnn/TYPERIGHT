package com.example

import android.content.Context
import kotlinx.coroutines.*
import java.util.Locale
import kotlin.math.*

/** Shared immutable Latin Malayalam vocabulary, indexes and offline-trained language evidence. */
class RomanizedMalayalamLexicon private constructor(owner: Context) {
    data class Entry(val families: Set<String>, val frequency: Int, val attestations: Int, val conversational: Boolean) {
        val family: String get() = families.firstOrNull { it.startsWith("chat:") } ?: if(families.size==1) families.first() else families.sorted().joinToString("|")
    }
    private data class Snapshot(val entries: Map<String, Entry>, val words: List<String>, val frequent: List<String>, val conversational: List<String>,
        val index: CompactCorrectionIndex?, val phonetics: Map<String, List<String>>, val characters: Map<String, Pair<Float,Float>>,
        val contexts: Map<String, Map<String,Int>>, val version: Int)
    @Volatile private var state = Snapshot(emptyMap(),emptyList(),emptyList(),emptyList(),null,emptyMap(),emptyMap(),emptyMap(),0)
    private data class LookupKey(val word: String, val distance: Float, val limit: Int, val version: Int)
    private val proposals=object : LinkedHashMap<LookupKey,List<SymSpellCorrectionEngine.SuggestionItem>>(256,.75f,true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LookupKey,List<SymSpellCorrectionEngine.SuggestionItem>>?)=size>256
    }
    val version get() = state.version
    val entries get() = state.entries
    val contexts get() = state.contexts
    val ready = scope.async {
        val entries = owner.assets.open("dictionaries/manglish/lexicon.tsv").bufferedReader().useLines { lines ->
            lines.associate { line ->
                val p=line.split('\t')
                p[0] to Entry(p[1].split('|').toSet(),p[2].toInt(),p[3].toInt(),p[4] == "1")
            }
        }
        val characters = owner.assets.open("dictionaries/manglish/character-language.tsv").bufferedReader().useLines { lines ->
            lines.associate { line -> val p=line.split('\t'); p[0] to (p[1].toFloat() to p[2].toFloat()) }
        }
        val contexts = linkedMapOf<String, MutableMap<String,Int>>()
        owner.assets.open("dictionaries/manglish/context.tsv").bufferedReader().useLines { lines -> lines.forEach { line ->
            val p=line.split('\t'); contexts.getOrPut(p[0]) { linkedMapOf() }[p[1]]=p[2].toInt()
        } }
        val words=entries.keys.sorted()
        val frequencies=entries.mapValues { it.value.frequency }
        val phonetics=words.groupBy(::phoneticKey).mapValues { (_,forms) -> forms.sortedWith(compareByDescending<String> { frequencies[it] }.thenBy { it }).take(12) }
        val index=CompactCorrectionIndex(words,frequencies)
        state=Snapshot(entries,words,words.sortedWith(compareByDescending<String> { frequencies[it] }.thenBy { it }).take(32),
            words.filter { entries[it]?.conversational == true },index,phonetics,characters,contexts.mapValues { it.value.toMap() },1)
        publishedWords=entries.keys
    }
    fun contains(word: String) = normalize(word) in state.entries
    fun entry(word: String) = state.entries[normalize(word)]
    fun frequency(word: String) = entry(word)?.frequency ?: 0
    fun family(word: String) = entry(word)?.family
    fun frequent() = state.frequent
    fun prefix(input: String, limit: Int = 12): List<String> {
        val prefix=normalize(input); val snapshot=state
        if(prefix.isEmpty() || limit<=0) return emptyList()
        val position=snapshot.words.binarySearch(prefix).let { if(it<0) -it-1 else it }
        // Bounded collection; conversational words precede long formal vocabulary.
        return (snapshot.conversational.asSequence().filter { it.startsWith(prefix) } +
            snapshot.words.asSequence().drop(position).takeWhile { it.startsWith(prefix) }.take(256)).distinct()
            .sortedWith(compareByDescending<String> { snapshot.entries[it]?.conversational == true }
                .thenByDescending { snapshot.entries[it]?.frequency ?: 0 }.thenBy { it }).take(limit).toList()
    }
    fun corrections(word: String, distance: Float = 2f, limit: Int = 12): List<SymSpellCorrectionEngine.SuggestionItem> {
        val snapshot=state; val key=LookupKey(normalize(word),distance,limit,snapshot.version)
        synchronized(proposals) { proposals[key]?.let { return it } }
        val result=snapshot.index?.lookup(key.word,distance,limit).orEmpty()
        synchronized(proposals) { proposals[key]=result }
        return result
    }
    internal fun clearProposalCache() = synchronized(proposals) { proposals.clear() }
    fun phoneticCandidates(word: String) = state.phonetics[phoneticKey(normalize(word))].orEmpty()
        .filter { abs(it.length-word.length)<=2 }.take(8)

    /** Vowel length/digraph normalization retrieves proposals, never establishes valid spelling. */
    private fun phoneticKey(word: String) = word.replace("aa","a").replace("ee","i").replace("oo","u")
        .replace("sh","s").replace("ch","c").replace("th","t").replace("dh","d")
        .replace(repeatedCharacters,"$1")

    /** Mean log likelihood keeps long words from gaining certainty just from length. */
    fun characterMalayalamProbability(word: String): Float {
        val chars=state.characters
        val padded="^${normalize(word)}$"; var delta=0f; var count=0
        for(size in 3..4) for(i in 0..padded.length-size) {
            val score=chars[padded.substring(i,i+size)] ?: continue
            delta+=score.second-score.first; count++
        }
        if(count<2) return .5f
        return (1f/(1f+exp(-(delta/count*2f).coerceIn(-5f,5f)))).coerceIn(.02f,.98f)
    }

    /** Recognize productive English stems + Malayalam suffixes without treating all *il words as Malayalam. */
    fun suffix(word: String, isEnglishStem: (String)->Boolean): String? {
        val clean=normalize(word)
        return suffixes.firstOrNull { suffix -> clean.length-suffix.length>=3 && clean.endsWith(suffix) && isEnglishStem(clean.dropLast(suffix.length)) }
    }
    fun isProtected(word: String, isEnglish: (String)->Boolean): Boolean =
        entry(word)?.let { it.conversational || !isEnglish(normalize(word)) } == true || suffix(word,isEnglish)!=null

    companion object {
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        private val suffixes=listOf("inte","inu","ile","odu","ode","il","um")
        private val repeatedCharacters=Regex("([a-z])\\1+")
        @Volatile private var instance: RomanizedMalayalamLexicon?=null
        @Volatile private var publishedWords: Set<String> = emptySet()
        val knownWords: Set<String> get() = publishedWords
        /** Shared by sentence/polish guards; dictionary-known English homophones stay editable. */
        fun preservesLiteral(word: String, prior: List<String> = emptyList(), following: List<String> = emptyList()): Boolean {
            val lexicon=instance ?: return false
            val lower=normalize(word)
            if(lexicon.entry(lower)?.conversational==true || lexicon.suffix(lower,EnglishFrequencyLexicon::recognizes)!=null) return true
            if(!lexicon.isProtected(lower,EnglishFrequencyLexicon::recognizes)) return false
            val mlSpan=(prior.takeLast(5)+following.take(2)).any {
                lexicon.entry(it)?.let { entry -> entry.conversational || !EnglishFrequencyLexicon.recognizes(it) } == true
            }
            // English loans with clear spelling evidence can be edited in an
            // English span. This does not make unrecognized words English.
            return mlSpan || lower !in ComprehensiveLexicon.TYPOS && TypingPolicy.correction(lower)==null
        }
        fun get(context: Context): RomanizedMalayalamLexicon = instance ?: synchronized(this) {
            instance ?: RomanizedMalayalamLexicon(context.applicationContext).also { instance=it }
        }
        private fun normalize(word: String)=word.lowercase(Locale.ROOT).trim()
    }
}

/** Word/span evidence for code switching. No hard sentence-wide language selection. */
internal class TypingLanguageDetector(private val lexicon: RomanizedMalayalamLexicon, private val dictionary: DictionaryManager) {
    data class Evidence(val probabilities: Map<String,Float>, val malayalamContext: Boolean, val englishContext: Boolean)
    fun detect(word: String, prior: List<String>, following: List<String> = emptyList()): Evidence {
        val near=prior.takeLast(5)+following.take(2)
        val mlContext=near.any { lexicon.entry(it)?.let { e -> e.conversational || !dictionary.isWordInDictionary(it) } == true }
        val englishContext=prior.takeLast(2).size == 2 && prior.takeLast(2).all {
            dictionary.isWordInDictionary(it) && lexicon.entry(it)?.conversational != true
        }
        if(word.isEmpty()) {
            val last=prior.lastOrNull().orEmpty()
            val ml=if(lexicon.contains(last) && (!dictionary.isWordInDictionary(last) || lexicon.entry(last)?.conversational == true)) .8f else if(mlContext) .55f else .08f
            return Evidence(mapOf("en" to (1f-ml),"ml-Latn" to ml),mlContext,englishContext)
        }
        val entry=lexicon.entry(word); val enKnown=dictionary.isWordInDictionary(word)
        val scores=when {
            entry!=null && !enKnown -> mapOf("ml-Latn" to .96f,"en" to .02f,"und" to .02f)
            entry?.conversational == true && enKnown -> mapOf("ml-Latn" to (if(mlContext) .65f else .45f),"en" to (if(mlContext) .33f else .53f),"und" to .02f)
            enKnown -> mapOf("en" to .96f,"ml-Latn" to .02f,"und" to .02f)
            lexicon.suffix(word,dictionary::isWordInDictionary)!=null -> mapOf("ml-Latn" to .94f,"en" to .02f,"und" to .04f)
            else -> {
                val character=lexicon.characterMalayalamProbability(word)
                val ml=(character*.65f+(if(mlContext) .8f else .2f)*.35f).coerceIn(.10f,.90f)
                // Unknown tokens retain explicit uncertainty. Edit/lexical/touch evidence
                // still decides obvious typos; language alone cannot authorize replacement.
                mapOf("ml-Latn" to ml*.72f,"en" to (1f-ml)*.72f,"und" to .28f)
            }
        }
        return Evidence(scores,mlContext,englishContext)
    }
}
