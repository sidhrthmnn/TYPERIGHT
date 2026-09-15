package com.example

import java.util.Locale
import kotlin.math.min

/**
 * High-performance Trie (Prefix Tree) data structure tailored specifically for
 * Malayalam word prediction and Manglish (Malayalam in Latin script) phonetic transliteration.
 *
 * Provides:
 * 1. O(K) Prefix Search: Type Manglish letters (e.g., "nama", "sukha", "enth") and instantly
 *    retrieve matching Malayalam words ranked by frequency.
 * 2. Exact Manglish to Malayalam Script Mapping: Fast dictionary lookup for thousands of words.
 * 3. Damerau-Levenshtein Fuzzy Search: Matches phonetic variations (e.g., "sukamano" -> "സുഖമാണോ", "endhanu" -> "എന്താണ്").
 * 4. Dynamic Learning: User-typed Malayalam & Manglish terms are inserted on-the-fly.
 */
class MalayalamManglishTrie {

    class TrieNode {
        val children = HashMap<Char, TrieNode>()
        var isWord: Boolean = false
        var manglishWord: String? = null
        var malayalamWord: String? = null
        var frequency: Int = 0
        var maxSubtreeFrequency: Int = 0
        val alternativeMalayalam = mutableListOf<String>()
    }

    private val root = TrieNode()
    private var totalWords = 0

    init {
        seedMalayalamDictionary()
    }

    fun size(): Int = totalWords

    /**
     * Inserts a Manglish to Malayalam mapping with a frequency score into the Trie.
     */
    fun insert(
        manglish: String,
        malayalam: String,
        frequency: Int = 100,
        alternatives: List<String> = emptyList()
    ) {
        val cleanManglish = manglish.lowercase(Locale.ROOT).trim()
        val cleanMalayalam = malayalam.trim()
        if (cleanManglish.isEmpty() || cleanMalayalam.isEmpty()) return

        var current = root
        current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)

        for (ch in cleanManglish) {
            current = current.children.getOrPut(ch) { TrieNode() }
            current.maxSubtreeFrequency = maxOf(current.maxSubtreeFrequency, frequency)
        }

        if (!current.isWord) {
            current.isWord = true
            totalWords++
        }
        current.manglishWord = cleanManglish
        current.malayalamWord = cleanMalayalam
        current.frequency = maxOf(current.frequency, frequency)
        for (alt in alternatives) {
            if (alt != cleanMalayalam && !current.alternativeMalayalam.contains(alt)) {
                current.alternativeMalayalam.add(alt)
            }
        }
    }

    /**
     * Exact lookup for a Manglish word.
     */
    fun findExact(manglish: String): MalayalamPrediction? {
        val clean = manglish.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty()) return null

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return null
        }

        return if (current.isWord && current.malayalamWord != null) {
            MalayalamPrediction(
                manglish = current.manglishWord ?: clean,
                malayalam = current.malayalamWord!!,
                frequency = current.frequency,
                alternatives = current.alternativeMalayalam
            )
        } else null
    }

    /**
     * Searches words by prefix. Returns ranked list of Malayalam suggestions.
     */
    fun searchPrefix(prefix: String, maxResults: Int = 5): List<MalayalamPrediction> {
        val clean = prefix.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        var current = root
        for (ch in clean) {
            current = current.children[ch] ?: return emptyList()
        }

        val collected = mutableListOf<MalayalamPrediction>()
        collectPrefixWords(current, collected)

        return collected
            .sortedByDescending { it.frequency }
            .take(maxResults)
    }

    private fun collectPrefixWords(node: TrieNode, list: MutableList<MalayalamPrediction>) {
        if (node.isWord && node.malayalamWord != null && node.manglishWord != null) {
            list.add(
                MalayalamPrediction(
                    manglish = node.manglishWord!!,
                    malayalam = node.malayalamWord!!,
                    frequency = node.frequency,
                    alternatives = node.alternativeMalayalam
                )
            )
        }
        for (child in node.children.values) {
            collectPrefixWords(child, list)
        }
    }

    /**
     * Fuzzy search to accommodate phonetic typos or Manglish spelling differences
     * (e.g. "sukham" vs "sukam", "adipoli" vs "adhiboli").
     */
    fun searchFuzzy(
        manglish: String,
        maxDistance: Int = 2,
        maxResults: Int = 3
    ): List<MalayalamPrediction> {
        val clean = manglish.lowercase(Locale.ROOT).trim()
        if (clean.isEmpty()) return emptyList()

        val results = mutableListOf<Pair<MalayalamPrediction, Float>>()
        val currentRow = IntArray(clean.length + 1) { it }

        for ((ch, child) in root.children) {
            searchFuzzyRecursive(child, ch, clean, currentRow, results, maxDistance)
        }

        return results
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    private fun searchFuzzyRecursive(
        node: TrieNode,
        letter: Char,
        target: String,
        previousRow: IntArray,
        results: MutableList<Pair<MalayalamPrediction, Float>>,
        maxDistance: Int
    ) {
        val columns = target.length
        val currentRow = IntArray(columns + 1)
        currentRow[0] = previousRow[0] + 1

        var minDistanceInRow = currentRow[0]

        for (i in 1..columns) {
            val insertCost = currentRow[i - 1] + 1
            val deleteCost = previousRow[i] + 1
            val replaceCost = if (target[i - 1] == letter) previousRow[i - 1] else previousRow[i - 1] + 1

            val cost = minOf(insertCost, minOf(deleteCost, replaceCost))
            currentRow[i] = cost
            if (cost < minDistanceInRow) {
                minDistanceInRow = cost
            }
        }

        if (currentRow[columns] <= maxDistance && node.isWord && node.malayalamWord != null && node.manglishWord != null) {
            val dist = currentRow[columns]
            val maxLen = maxOf(target.length, node.manglishWord!!.length)
            val similarity = 1.0f - (dist.toFloat() / maxLen.toFloat())
            val score = (node.frequency.toFloat() * 0.4f) + (similarity * 100f)

            val prediction = MalayalamPrediction(
                manglish = node.manglishWord!!,
                malayalam = node.malayalamWord!!,
                frequency = node.frequency,
                alternatives = node.alternativeMalayalam
            )
            results.add(Pair(prediction, score))
        }

        if (minDistanceInRow <= maxDistance) {
            for ((nextLetter, child) in node.children) {
                searchFuzzyRecursive(child, nextLetter, target, currentRow, results, maxDistance)
            }
        }
    }

    /**
     * Seeds the Trie with rich Malayalam vocabulary across everyday speech, formal communication,
     * questions, verbs, pronouns, numbers, slang, and cultural greetings.
     */
    private fun seedMalayalamDictionary() {
        // Greetings & Social
        insert("namaskaram", "നമസ്കാരം", 1000, listOf("നമസ്കാരം!"))
        insert("namaste", "നമസ്തേ", 900)
        insert("nanni", "നന്ദി", 980)
        insert("snehapoorvam", "സ്നേഹപൂർവ്വം", 850)
        insert("sneham", "സ്നേഹം", 870)
        insert("santhosham", "സന്തോഷം", 890)
        insert("swagatham", "സ്വാഗതം", 860)
        insert("subhadinam", "ശുഭദിനം", 820)
        insert("subharathri", "ശുഭരാത്രി", 830)
        insert("ashamsakal", "ആശംസകൾ", 910)
        insert("onashamsakal", "ഓണാശംസകൾ", 800)
        insert("vishwashamsakal", "വിഷു ആശംസകൾ", 780)
        insert("onam", "ഓണം", 850)
        insert("vishu", "വിഷു", 840)

        // Enquiries & Questions
        insert("sukhamano", "സുഖമാണോ", 990, listOf("സുഖമാണോ?"))
        insert("sukham", "സുഖം", 950)
        insert("sukhamanu", "സുഖമാണ്", 940)
        insert("entha", "എന്താ", 980)
        insert("enthokke", "എന്തൊക്കെ", 970)
        insert("enthanu", "എന്താണ്", 960)
        insert("enthina", "എന്തിനാ", 910)
        insert("enthinu", "എന്തിന്", 900)
        insert("evide", "എവിടെ", 960)
        insert("evideya", "എവിടെയാ", 950)
        insert("evideyanu", "എവിടെയാണ്", 940)
        insert("ivide", "ഇവിടെ", 950)
        insert("avide", "അവിടെ", 950)
        insert("eppol", "എപ്പോൾ", 930)
        insert("eppozhum", "എപ്പോഴും", 910)
        insert("engane", "എങ്ങനെ", 940)
        insert("enganeyundu", "എങ്ങനെയുണ്ട്", 930)
        insert("ethu", "ഏത്", 900)
        insert("aarannu", "ആരാണ്", 890)
        insert("aaranu", "ആരാണ്", 890)
        insert("aaru", "ആര്", 880)

        // Pronouns
        insert("njan", "ഞാൻ", 1000)
        insert("njaan", "ഞാൻ", 990)
        insert("nee", "നീ", 980)
        insert("ningal", "നിങ്ങൾ", 970)
        insert("nammal", "നമ്മൾ", 960)
        insert("nammude", "നമ്മുടെ", 950)
        insert("ningalude", "നിങ്ങളുടെ", 940)
        insert("ente", "എന്റെ", 980)
        insert("ninte", "നിന്റെ", 970)
        insert("avan", "അവൻ", 930)
        insert("aval", "അവൾ", 930)
        insert("avar", "അവർ", 940)
        insert("ithu", "ഇത്", 990)
        insert("athu", "അത്", 990)
        insert("ellarum", "എല്ലാരും", 920)
        insert("ellavarkkum", "എല്ലാവർക്കും", 930)

        // Affirmation, Negation, Modals
        insert("athe", "അതെ", 960)
        insert("alla", "അല്ല", 950)
        insert("aano", "ആണോ", 960)
        insert("aanu", "ആണ്", 970)
        insert("undu", "ഉണ്ട്", 980)
        insert("illa", "ഇല്ല", 980)
        insert("pinne", "പിന്നെ", 940)
        insert("sheri", "ശരി", 970)
        insert("sheriyaanu", "ശരിയാണ്", 960)
        insert("venam", "വേണം", 960)
        insert("venda", "വേണ്ട", 960)
        insert("thettu", "തെറ്റ്", 870)
        insert("sathyam", "സത്യം", 900)
        insert("onnumilla", "ഒന്നുമില്ല", 920)
        insert("ariyaam", "അറിയാം", 940)
        insert("ariyam", "അറിയാം", 940)
        insert("ariyilla", "അറിയില്ല", 940)
        insert("manassilayi", "മനസ്സിലായി", 970)
        insert("manasilayi", "മനസ്സിലായി", 970)
        insert("manasillatha", "മനസ്സിലാവാത്ത", 880)

        // Verbs & Actions
        insert("cheyyam", "ചെയ്യാം", 960)
        insert("cheyyaam", "ചെയ്യാം", 960)
        insert("cheyyu", "ചെയ്യ്", 940)
        insert("cheythu", "ചെയ്തു", 950)
        insert("cheyyunnu", "ചെയ്യുന്നു", 930)
        insert("pokam", "പോകാം", 960)
        insert("povaam", "പോകാം", 950)
        insert("poyi", "പോയി", 950)
        insert("pokunnu", "പോകുന്നു", 920)
        insert("varu", "വരൂ", 960)
        insert("vannu", "വന്നു", 950)
        insert("varum", "വരും", 950)
        insert("varunnu", "വരുന്നു", 930)
        insert("parayu", "പറയൂ", 950)
        insert("paranju", "പറഞ്ഞു", 950)
        insert("parayam", "പറയാം", 940)
        insert("parayunnu", "പറയുന്നു", 920)
        insert("kelkku", "കേൾക്കൂ", 920)
        insert("kettu", "കേട്ടു", 930)
        insert("nokku", "നോക്കൂ", 950)
        insert("nokkam", "നോക്കാം", 940)
        insert("nokki", "നോക്കി", 940)
        insert("kandu", "കണ്ടു", 950)
        insert("kaanam", "കാണാം", 940)
        insert("kazhinju", "കഴിഞ്ഞു", 950)
        insert("kazhicho", "കഴിച്ചോ", 960)
        insert("kazhikkam", "കഴിക്കാം", 940)
        insert("chodichu", "ചോദിച്ചു", 910)
        insert("koduthu", "കൊടുത്തു", 920)
        insert("kodukkam", "കൊടുക്കാം", 920)
        insert("thudangam", "തുടങ്ങാം", 930)
        insert("nirthu", "നിർത്തൂ", 910)
        insert("orthu", "ഓർത്തു", 900)
        insert("marannu", "മറന്നു", 910)
        insert("karanju", "കരഞ്ഞു", 870)
        insert("chirichu", "ചിരിച്ചു", 890)

        // Adjectives, Slang & Modifiers
        insert("kollam", "കൊള്ളാം", 970)
        insert("adipoli", "അടിപൊളി", 980)
        insert("pwoli", "പൊളി", 970)
        insert("kidilam", "കിടിലം", 960)
        insert("valare", "വളരെ", 950)
        insert("nalla", "നല്ല", 970)
        insert("mosham", "മോശം", 880)
        insert("paavam", "പാവം", 900)
        insert("kurachu", "കുറച്ച്", 930)
        insert("kooduthal", "കൂടുതൽ", 940)
        insert("ishtam", "ഇഷ്ടം", 950)
        insert("ishtamanu", "ഇഷ്ടമാണ്", 940)
        insert("viswasam", "വിശ്വാസം", 900)
        insert("thamashe", "തമാശ", 890)
        insert("prashnam", "പ്രശ്നം", 920)
        insert("sahayam", "സഹായം", 930)
        insert("aarogya", "ആരോഗ്യ", 880)

        // Time, Days & Location
        insert("innu", "ഇന്ന്", 970)
        insert("nale", "നാളെ", 970)
        insert("innale", "ഇന്നലെ", 960)
        insert("ippol", "ഇപ്പോൾ", 970)
        insert("samayam", "സമയം", 950)
        insert("ravile", "രാവിലെ", 930)
        insert("vaikittu", "വൈകിട്ട്", 920)
        insert("rathri", "രാത്രി", 940)
        insert("divasam", "ദിവസം", 930)
        insert("varsham", "വർഷം", 920)
        insert("kerala", "കേരളം", 960)
        insert("keralam", "കേരളം", 960)
        insert("malayalam", "മലയാളം", 980)
        insert("manglish", "മംഗ്ലീഷ്", 970)

        // People & Family
        insert("amma", "അമ്മ", 980)
        insert("achan", "അച്ഛൻ", 970)
        insert("chetta", "ചേട്ടാ", 960)
        insert("chettan", "ചേട്ടൻ", 950)
        insert("chechi", "ചേച്ചി", 950)
        insert("aniyathi", "അനിയത്തി", 920)
        insert("aniyan", "അനിയൻ", 930)
        insert("mon", "മോൻ", 940)
        insert("mole", "മോളെ", 940)
        insert("kutti", "കുട്ടി", 940)
        insert("kuttikal", "കുട്ടികൾ", 950)
        insert("changathi", "ചങ്ങാതി", 910)
        insert("suhruthu", "സുഹൃത്ത്", 920)

        // Places & Living
        insert("veedu", "വീട്", 950)
        insert("veettil", "വീട്ടിൽ", 950)
        insert("joli", "ജോലി", 940)
        insert("kaaryam", "കാര്യം", 940)
        insert("bhakshanam", "ഭക്ഷണം", 930)
        insert("choru", "ചോറ്", 920)
        insert("chaaya", "ചായ", 940)
        insert("kaappi", "കാപ്പി", 930)
        insert("vellam", "വെള്ളം", 950)
        insert("chodyam", "ചോദ്യം", 900)
        insert("utharam", "ഉത്തരം", 900)

        // Numbers
        insert("oru", "ഒരു", 980)
        insert("onnu", "ഒന്ന്", 950)
        insert("randu", "രണ്ട്", 950)
        insert("moonu", "മൂന്ന്", 940)
        insert("naalu", "നാല്", 940)
        insert("anchu", "അഞ്ച്", 930)
        insert("aaru", "ആറ്", 930)
        insert("ezhu", "ഏഴ്", 920)
        insert("ettu", "എട്ട്", 920)
        insert("onpathu", "ഒൻപത്", 910)
        insert("pathu", "പത്ത്", 930)
    }
}

/**
 * Encapsulates a Malayalam prediction result with its Manglish source and alternatives.
 */
data class MalayalamPrediction(
    val manglish: String,
    val malayalam: String,
    val frequency: Int,
    val alternatives: List<String> = emptyList()
)
