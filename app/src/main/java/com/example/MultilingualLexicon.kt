package com.example

import android.content.Context
import kotlinx.coroutines.*
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Immutable compact word tables; loading/index creation is confined to background workers. */
class MultilingualLexicon private constructor(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private class Table(val words: List<String>, val counts: IntArray) {
        val frequent = words.indices.sortedByDescending { counts[it] }.take(32).map { words[it] }
        @Volatile var index: CompactCorrectionIndex? = null
        fun frequency(word: String): Int = words.binarySearch(word).let { if (it < 0) 0 else counts[it] }
    }
    @Volatile private var tables: Map<String, Table> = emptyMap()
    private val hotIndexes = object : LinkedHashMap<String, Table>(6, .75f, true) {}
    private fun publish(language: String, table: Table, index: CompactCorrectionIndex) = synchronized(hotIndexes) {
        table.index = index; hotIndexes[language] = table
        while (hotIndexes.size > 6) { val oldest = hotIndexes.entries.first(); oldest.value.index = null; hotIndexes.remove(oldest.key) }
    }
    private val building = ConcurrentHashMap.newKeySet<String>()
    val ready = scope.async {
        val loaded = linkedMapOf<String, Table>()
        context.assets.list("dictionaries/multilingual").orEmpty().filter { it.endsWith(".tsv") }.forEach { file ->
            val values = context.assets.open("dictionaries/multilingual/$file").bufferedReader().useLines { lines ->
                lines.map { it.split('\t').let { parts -> parts[0] to parts[1].toInt() } }.toList().sortedBy { it.first }
            }
            loaded[file.removeSuffix(".tsv")] = Table(values.map { it.first }, IntArray(values.size) { values[it].second })
        }
        tables = loaded
    }
    suspend fun warm(languages: List<String>) {
        ready.await()
        languages.distinct().take(3).forEach { language ->
            val table = tables[language] ?: return@forEach
            synchronized(table) {
                if (table.index == null) publish(language, table, CompactCorrectionIndex(table.words, table.words.indices.associate { table.words[it] to table.counts[it] }))
            }
        }
    }
    fun languages(word: String): List<String> {
        val clean = lookupWord(word)
        return tables.filterValues { it.frequency(clean) > 0 }.keys.toList()
    }
    fun contains(word: String): Boolean {
        val clean = lookupWord(word)
        return tables.values.any { it.frequency(clean) > 0 }
    }
    fun frequent(language: String) = tables[language]?.frequent.orEmpty()
    fun frequency(word: String, language: String): Int = tables[language]?.frequency(lookupWord(word)) ?: 0
    fun prefix(prefix: String, languages: List<String>, limit: Int): List<String> {
        val clean = lookupWord(prefix)
        if (clean.length < 2) return emptyList()
        return languages.flatMap { language ->
            val table = tables[language] ?: return@flatMap emptyList<String>()
            val position = table.words.binarySearch(clean).let { if (it < 0) -it - 1 else it }
            table.words.asSequence().drop(position).takeWhile { it.startsWith(clean) }.take(128)
                .sortedByDescending { table.frequency(it) }.take(limit).toList()
        }.distinct().take(limit)
    }
    fun corrections(word: String, languages: List<String>, limit: Int): List<SymSpellCorrectionEngine.SuggestionItem> =
        languages.take(3).flatMap { language ->
            val table = tables[language] ?: return@flatMap emptyList()
            if (table.index == null && building.add(language)) scope.launch {
                publish(language, table, CompactCorrectionIndex(table.words, table.words.indices.associate { table.words[it] to table.counts[it] }))
                building.remove(language)
            }
            synchronized(hotIndexes) { hotIndexes[language] }
            table.index?.lookup(lookupWord(word), 2f, limit).orEmpty()
        }.distinctBy { it.term }.take(limit)

    companion object {
        @Volatile private var instance: MultilingualLexicon? = null
        fun get(context: Context) = instance ?: synchronized(this) {
            instance ?: MultilingualLexicon(context.applicationContext).also { instance = it }
        }
        // English and Latin Malayalam need neither ICU case folding nor Unicode
        // composition. Keep full normalization for every non-ASCII language.
        private fun lookupWord(word: String) = if(word.all { it.code<128 }) word.lowercase(Locale.ROOT)
            else Normalizer.normalize(android.icu.lang.UCharacter.foldCase(word.replace('’', '\''), true), Normalizer.Form.NFC)
        fun normalize(word: String) = if(word.all { it.code<128 }) word.lowercase(Locale.ROOT)
            else Normalizer.normalize(word.lowercase(Locale.ROOT).replace('’', '\''), Normalizer.Form.NFC)
        val romanizedMalayalam = setOf("njan", "njangal", "nee", "ningal", "avan", "aval", "nammal", "ente", "ninte", "enikku", "ninakku", "aanu", "alla", "aano", "undu", "illa", "varilla", "varum", "vannu", "pokum", "poyi", "pokan", "evide", "enth", "entha", "enthanu", "eppol", "inn", "innu", "nale", "naale", "sheri", "shari", "venam", "venda", "cheyyam", "cheyyum", "cheythu", "officil", "officeil", "veettil", "nattil", "enthina", "pinne", "kollam", "adipoli", "machane", "mone", "molu", "sugham", "sukham", "ippo", "ippol", "kazhinju", "alle", "koode", "koodi", "onnum", "nalla", "samayam", "ariyilla", "ariyaam", "parayu", "paranja", "okke", "kurachu")
        val romanizedHindi = setOf("main", "mein", "mai", "mera", "meri", "mere", "mujhe", "tum", "tumhara", "aap", "aapka", "hum", "ham", "hai", "hain", "ho", "tha", "thi", "nahi", "nahin", "kal", "aaj", "abhi", "kya", "kyun", "kaise", "kahan", "kab", "ka", "ki", "ke", "ko", "se", "par", "aur", "lekin", "bahut", "accha", "achha", "theek", "thik", "haan", "han", "ji", "yaar", "bhai", "dost", "jaunga", "jaungi", "jayega", "jaana", "jana", "aaunga", "karunga", "karenge", "karo", "karna", "karta", "krna", "kuch", "sab", "bas", "phir", "fir", "chalo", "chahiye", "milenge", "milte", "pata", "samajh", "gaya", "gayi")
        val slang = setOf("plz", "pls", "thx", "thanx", "lol", "lmao", "rofl", "brb", "idk", "imo", "imho", "tbh", "btw", "gud", "gonna", "wanna", "gotta", "kinda", "sorta", "dunno", "yup", "nah", "bro", "sis", "fam", "rn", "fr", "ngl", "omg", "omw", "ttyl", "ily", "ikr", "nope", "ok", "okay", "haha", "hehe", "yolo", "fyi", "asap", "api", "sdk", "gguf", "llm", "http", "https", "otp", "json", "sql", "html", "css", "id", "ui", "ux")
    }
}
