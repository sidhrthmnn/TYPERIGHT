package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import java.util.Locale
import java.util.PriorityQueue

/** Shared, read-only corpus: sorted words avoid allocating a second large trie per keyboard. */
class EnglishFrequencyLexicon private constructor(context: Context) {
    @Volatile var frequencies: Map<String, Int> = emptyMap()
        private set
    @Volatile private var words: List<String> = emptyList()
    @Volatile private var correctionIndex: CompactCorrectionIndex? = null
    private val indexLock = Any()
    @Volatile private var phonetics: Map<String, List<String>> = emptyMap()
    @Volatile var canonical: Set<String> = emptySet()
        private set
    val ready = scope.async {
        canonical = context.assets.open("dictionaries/english_canonical.txt").bufferedReader().useLines { it.toSet() }
        val loaded = context.assets.open("dictionaries/english_frequency.tsv").bufferedReader().useLines { lines ->
            lines.associate { line -> val (word, frequency) = line.split('\t'); word to frequency.toInt() }
        }
        // Subtitle frequencies describe usage, not spelling correctness (they contain typos).
        words = loaded.keys.filter { it in canonical }.sorted()
        frequencies = loaded
        ensureCorrectionIndex()
        phonetics = loaded.entries.asSequence().filter { it.key.length in 3..24 && it.key.all { c -> c in 'a'..'z' } }
            .sortedByDescending { it.value }.groupBy({ soundKey(it.key) }, { it.key }).mapValues { it.value.take(12) }
    }
    fun phoneticCandidates(word: String): List<String> = phonetics[soundKey(word)].orEmpty().filter { kotlin.math.abs(it.length - word.length) <= 2 }.take(8)

    fun frequency(word: String): Int = frequencies[word.lowercase(Locale.ROOT)] ?: 0

    fun prefix(prefix: String, limit: Int): List<String> {
        if (prefix.isBlank() || limit <= 0) return emptyList()
        val clean = prefix.lowercase(Locale.ROOT)
        val found = words.binarySearch(clean)
        var index = if (found >= 0) found else -found - 1
        val top = PriorityQueue<String>(compareBy<String> { frequency(it) }.thenByDescending { it })
        while (index < words.size && words[index].startsWith(clean)) {
            top.add(words[index++])
            if (top.size > limit) top.remove()
        }
        return top.sortedWith(compareByDescending<String> { frequency(it) }.thenBy { it })
    }

    // Build once on a background thread. Queries use curated corrections until it is ready.
    internal fun ensureCorrectionIndex(): CompactCorrectionIndex = correctionIndex ?: synchronized(indexLock) {
        correctionIndex ?: CompactCorrectionIndex(words, frequencies).also { engine ->
            correctionIndex = engine
        }
    }
    fun corrections(word: String, distance: Float, limit: Int): List<SymSpellCorrectionEngine.SuggestionItem> =
        correctionIndex?.lookup(word, distance, limit).orEmpty()

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        @Volatile private var instance: EnglishFrequencyLexicon? = null
        internal fun recognizes(word: String) = instance?.canonical?.contains(word.lowercase(Locale.ROOT)) == true
        internal fun soundKey(word: String): String {
            val normalized = word.lowercase(Locale.ROOT).replace("ph", "f").replace("ght", "t")
            val clean = if (normalized.startsWith("kn")) normalized.drop(1) else normalized
            if (clean.isEmpty()) return ""
            fun code(c: Char) = when (c) { 'b', 'f', 'p', 'v' -> '1'; 'c', 'g', 'j', 'k', 'q', 's', 'x', 'z' -> '2'; 'd', 't' -> '3'; 'l' -> '4'; 'm', 'n' -> '5'; 'r' -> '6'; else -> '0' }
            val result = StringBuilder().append(clean.first()); var last = code(clean.first())
            for (c in clean.drop(1)) { val next = code(c); if (next != '0' && next != last) result.append(next); last = next }
            return result.toString().padEnd(4, '0').take(4)
        }
        fun get(context: Context): EnglishFrequencyLexicon = instance ?: synchronized(this) {
            instance ?: EnglishFrequencyLexicon(context.applicationContext).also { instance = it }
        }
    }
}

/** Immutable prefix-delete postings packed into primitive longs (fingerprint + word ID).
 * Hash collisions only retrieve extra proposals: exact weighted edit distance always
 * validates them. No per-deletion strings, map nodes or boxed ID collections survive startup.
 */
internal class CompactCorrectionIndex(
    private val words: List<String>,
    private val frequencies: Map<String, Int>
) {
    private val scorer = SymSpellCorrectionEngine()
    private val deletes: LongArray
    init {
        var postings=LongArray(maxOf(16,words.size*24)); var count=0
        words.forEachIndexed { id, word ->
            scorer.getDeletes(word.take(7), 2).forEach { variant ->
                if(count==postings.size) postings=postings.copyOf(postings.size+postings.size/2)
                postings[count++]=(variant.hashCode().toLong() shl 32) or id.toLong()
            }
        }
        deletes=postings.copyOf(count)
        java.util.Arrays.sort(deletes)
    }
    fun lookup(input: String, maxDistance: Float, maxResults: Int): List<SymSpellCorrectionEngine.SuggestionItem> {
        val clean = input.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty() || clean.length > 32 || maxResults <= 0) return emptyList()
        val seen = java.util.BitSet(words.size)
        val order = compareBy<SymSpellCorrectionEngine.SuggestionItem> { it.distance }
            .thenByDescending { it.frequency }.thenBy { it.term }
        val best = PriorityQueue(order.reversed())
        scorer.getDeletes(clean.take(7), if (maxDistance <= 1f) 1 else 2).forEach { variant ->
            val hash=variant.hashCode()
            val position=deletes.binarySearch(hash.toLong() shl 32).let { if(it<0) -it-1 else it }
            var cursor=position
            while(cursor<deletes.size && (deletes[cursor] shr 32).toInt()==hash) {
                val id = deletes[cursor++].toInt()
                if (seen[id]) continue
                seen.set(id)
                val word = words[id]
                if (kotlin.math.abs(word.length - clean.length) * .95f > maxDistance + .35f) continue
                val distance = scorer.computeWeightedDamerauLevenshtein(clean, word)
                if (distance <= maxDistance + .35f) {
                    best.add(SymSpellCorrectionEngine.SuggestionItem(word, distance, frequencies[word] ?: 1))
                    if (best.size > maxResults) best.remove()
                }
            }
        }
        return best.sortedWith(order)
    }
}
