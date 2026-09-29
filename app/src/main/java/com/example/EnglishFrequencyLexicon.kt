package com.example

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.PriorityQueue

/** Shared, read-only corpus: sorted words avoid allocating a second large trie per keyboard. */
class EnglishFrequencyLexicon private constructor(context: Context) {
    val frequencies: Map<String, Int> = context.assets.open("dictionaries/english_frequency.tsv")
        .bufferedReader().useLines { lines -> lines.associate { line ->
            val (word, frequency) = line.split('\t')
            word to frequency.toInt()
        } }
    private val words = frequencies.keys.sorted()
    @Volatile private var correctionIndex: CompactCorrectionIndex? = null
    private val indexLock = Any()
    init { scope.launch { ensureCorrectionIndex() } }

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
        fun get(context: Context): EnglishFrequencyLexicon = instance ?: synchronized(this) {
            instance ?: EnglishFrequencyLexicon(context.applicationContext).also { instance = it }
        }
    }
}

/** Immutable prefix-delete index. Primitive word IDs avoid a map and set per spelling variant. */
internal class CompactCorrectionIndex(
    private val words: List<String>,
    private val frequencies: Map<String, Int>
) {
    private class WordIds {
        var values = IntArray(2)
        var size = 0
        fun add(id: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = id
        }
    }
    private val scorer = SymSpellCorrectionEngine()
    private val deletes = HashMap<String, WordIds>()
    init {
        words.forEachIndexed { id, word ->
            scorer.getDeletes(word.take(5), 2).forEach { variant ->
                deletes.getOrPut(variant) { WordIds() }.add(id)
            }
        }
    }
    fun lookup(input: String, maxDistance: Float, maxResults: Int): List<SymSpellCorrectionEngine.SuggestionItem> {
        val clean = input.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty() || clean.length > 32 || maxResults <= 0) return emptyList()
        val seen = java.util.BitSet(words.size)
        val order = compareBy<SymSpellCorrectionEngine.SuggestionItem> { it.distance }
            .thenByDescending { it.frequency }.thenBy { it.term }
        val best = PriorityQueue(order.reversed())
        scorer.getDeletes(clean.take(5), 2).forEach { variant ->
            val ids = deletes[variant] ?: return@forEach
            for (i in 0 until ids.size) {
                val id = ids.values[i]
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
