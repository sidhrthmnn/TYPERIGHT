package com.example

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** Bounded local counts, never full messages. Reads on the typing path are entirely in memory. */
class PersonalTypingProfile internal constructor(context: Context) {
    private val owner = context.applicationContext
    private val prefs = owner.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val words = linkedMapOf<String, Int>()
    private val transitions = linkedMapOf<String, Int>()
    private val corrections = linkedMapOf<String, Int>()
    private val rejections = linkedMapOf<String, Int>()
    @Volatile var revision: Long = 0
        private set
    private val writer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "typing-profile").apply { isDaemon = true } }
    private var pending: ScheduledFuture<*>? = null
    private val writeLock = Any()

    data class Snapshot(val words: Map<String, Int>, val transitions: Map<String, Int>, val corrections: Map<String, Int>, val rejections: Map<String, Int>, val recency: Map<String,Float> = emptyMap())
    @Volatile private var snapshot = Snapshot(emptyMap(), emptyMap(), emptyMap(), emptyMap())
    private var resetEpoch = 0L
    val ready = kotlinx.coroutines.CompletableDeferred<Unit>()
    init { writer.execute {
        synchronized(this) { runCatching {
            if (resetEpoch != 0L) return@runCatching
            val json = JSONObject(prefs.getString("profile", "{}") ?: "{}")
            fun read(name: String, target: MutableMap<String, Int>, limit: Int) {
                val entries = json.optJSONArray(name) ?: return
                for (i in 0 until minOf(entries.length(), limit)) {
                    val pair = entries.getJSONArray(i)
                    target[pair.getString(0)] = pair.getInt(1).coerceIn(1, 1000)
                }
            }
            read("words", words, 2000); read("transitions", transitions, 4000); read("corrections", corrections, 500); read("rejections", rejections, 500)
        }.onFailure { words.clear(); transitions.clear(); corrections.clear(); rejections.clear() }
        publish()
        }
        ready.complete(Unit)
    } }

    @Synchronized fun observe(word: String, context: List<String>) {
        val clean = normalize(word)
        if (!isWord(clean)) return
        increment(words, clean, 2000)
        for (size in 1..minOf(5, context.size)) {
            val prior = context.takeLast(size).map(::normalize)
            if (prior.all(::isWord)) increment(transitions, "${prior.joinToString(" ")}|$clean", 4000)
        }
        scheduleWrite()
    }

    fun candidates(prefix: String, context: List<String>): List<String> =
        snapshot.words.keys.filter { it.startsWith(normalize(prefix)) }
            .sortedByDescending { boost(it, context) }.take(12)

    fun boost(word: String, context: List<String>): Float {
        val state = snapshot
        val clean = normalize(word)
        val uses = state.words[clean] ?: 0
        val matches = (1..minOf(5, context.size)).maxOfOrNull { size -> state.transitions["${context.takeLast(size).map(::normalize).joinToString(" ")}|$clean"] ?: 0 } ?: 0
        // A few repeated choices can beat generic corpus priors without replacing valid typed words.
        return (uses.coerceAtMost(12) * .015f + matches.coerceAtMost(8) * .10f).coerceAtMost(.9f)
    }

    /** Bounded recency snapshot from the last 64 vocabulary updates; no clock or message log. */
    fun recency(word: String): Float = snapshot.recency[normalize(word)] ?: 0f

    /** Called only after an explicit acceptance successfully changes the editor. */
    @Synchronized fun acceptPolish(original: String, polished: String, isKnown: (String) -> Boolean): List<String> {
        val before = tokens(original); val after = tokens(polished)
        if (before.isEmpty() || before.size > 200 || after.size > 200) return emptyList()
        // Token edit alignment handles inserted/deleted words without shifting subsequent typo pairs.
        val cost = Array(before.size + 1) { IntArray(after.size + 1) }
        for (i in before.indices) cost[i + 1][0] = i + 1
        for (j in after.indices) cost[0][j + 1] = j + 1
        for (i in before.indices) for (j in after.indices) {
            cost[i + 1][j + 1] = minOf(cost[i][j] + if (before[i] == after[j]) 0 else 1,
                cost[i][j + 1] + 1, cost[i + 1][j] + 1)
        }
        var i = before.size; var j = after.size
        val pairs = mutableListOf<Pair<Int, Int>>()
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + if (before[i - 1] == after[j - 1]) 0 else 1 -> {
                    if (before[i - 1] != after[j - 1]) pairs.add((i - 1) to (j - 1))
                    i--; j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> i--
                else -> j--
            }
        }
        val receipt = mutableListOf<String>()
        for ((a, b) in pairs) {
            val source = before[a]; val target = after[b]
            if (!plausibleTypo(source, target) || !isKnown(target)) continue
            val prior = before.take(a).takeLast(2)
            // Existing real words require repeated acceptance in the same context; never a global rewrite.
            val scope = if (isKnown(source)) prior.joinToString(" ").takeIf { prior.isNotEmpty() && prior.all(::isWord) } ?: continue else "*"
            val key = "$scope|$source|$target"
            increment(corrections, key, 500)
            val rejectedPair = "$source|$target"
            rejections[rejectedPair]?.let { count -> if (count <= 1) rejections.remove(rejectedPair) else rejections[rejectedPair] = count - 1 }
            receipt.add(key)
            observe(target, after.take(b).takeLast(2))
        }
        scheduleWrite()
        return receipt
    }

    fun correction(word: String, context: List<String>): String? {
        val corrections = snapshot.corrections
        val clean = normalize(word)
        if (!isWord(clean)) return null
        val scopes = listOf(context.takeLast(2).map(::normalize).joinToString(" "), "*")
        for (scope in scopes.distinct()) {
            val prefix = "$scope|$clean|"
            val options = corrections.filterKeys { it.startsWith(prefix) }.entries.sortedByDescending { it.value }
            val top = options.firstOrNull() ?: continue
            val total = options.sumOf { it.value }
            if (scope != "*" && top.value < 3) continue
            if (options.size > 1 && (top.value < 3 || top.value * 4 < total * 3)) continue
            return TypingPolicy.restoreCase(word, top.key.substringAfterLast('|'))
        }
        return null
    }

    @Synchronized fun retract(receipt: List<String>) {
        receipt.forEach { key ->
            val count = corrections[key] ?: return@forEach
            if (count <= 1) corrections.remove(key) else corrections[key] = count - 1
        }
        scheduleWrite()
    }

    @Synchronized fun reject(source: String, target: String) {
        val original = normalize(source); val replacement = normalize(target)
        corrections.keys.removeAll { it.endsWith("|$original|$replacement") }
        if (isWord(original) && isWord(replacement)) {
            increment(rejections, "$original|$replacement", 500)
            increment(words, original, 2000)
            words[original] = maxOf(3, words[original] ?: 0)
        }
        scheduleWrite()
    }

    fun isTrusted(word: String) = (snapshot.words[normalize(word)] ?: 0) >= 3
    fun rejectionPenalty(source: String, target: String): Float =
        ((snapshot.rejections["${normalize(source)}|${normalize(target)}"] ?: 0) * .8f).coerceAtMost(1f)
    fun acceptedEvidence(source: String, target: String, context: List<String>): Float {
        val corrections = snapshot.corrections
        val prior = context.takeLast(2).map(::normalize).joinToString(" ")
        return maxOf(corrections["*|${normalize(source)}|${normalize(target)}"] ?: 0,
            corrections["$prior|${normalize(source)}|${normalize(target)}"] ?: 0).coerceAtMost(5) / 5f
    }

    @Synchronized fun clear() { resetEpoch++; words.clear(); transitions.clear(); corrections.clear(); rejections.clear(); scheduleWrite() }

    private fun increment(map: MutableMap<String, Int>, key: String, limit: Int) {
        val value = ((map.remove(key) ?: 0) + 1).coerceAtMost(1000)
        map[key] = value
        while (map.size > limit) map.remove(map.keys.first())
    }

    private fun publish() {
        val recent=words.keys.toList().takeLast(64)
        snapshot = Snapshot(words.toMap(), transitions.toMap(), corrections.toMap(), rejections.toMap(), recent.mapIndexed { i, word -> word to (i+1f)/maxOf(1,recent.size) }.toMap())
    }
    private fun scheduleWrite() {
        publish()
        revision++
        pending?.cancel(false)
        pending = writer.schedule({ flush() }, 250, TimeUnit.MILLISECONDS)
    }

    internal fun flush() = synchronized(writeLock) {
        val payload = synchronized(this) {
            fun entries(map: Map<String, Int>) = JSONArray().apply { map.forEach { (key, count) -> put(JSONArray().put(key).put(count)) } }
            JSONObject().put("words", entries(words)).put("transitions", entries(transitions))
                .put("corrections", entries(corrections)).put("rejections", entries(rejections)).toString()
        }
        // Disk I/O never holds the profile lock used by keystroke-time reads and observations.
        prefs.edit().putString("profile", payload).commit()
        Unit
    }

    companion object {
        const val PREFS = "typeright_personal_learning"
        @Volatile private var instance: PersonalTypingProfile? = null
        fun get(context: Context): PersonalTypingProfile {
            val app = context.applicationContext
            return instance?.takeIf { it.owner === app } ?: synchronized(this) {
                instance?.takeIf { it.owner === app } ?: PersonalTypingProfile(app).also { instance = it }
            }
        }
        private fun normalize(word: String) = MultilingualLexicon.normalize(word)
        private fun isWord(word: String) = word.length in 1..32 && word.any(Char::isLetter) && word.all { TypingPolicy.isWordCharacter(it) && !it.isDigit() }
        private fun tokens(text: String) = text.split(Regex("\\s+")).filter(String::isNotBlank)
            .map { normalize(it.trim { c -> !TypingPolicy.isWordCharacter(c) }) }
        internal fun plausibleTypo(a: String, b: String): Boolean {
            if (!isWord(a) || !isWord(b) || minOf(a.length, b.length) < 3 || a == b) return false
            val d = Array(a.length + 1) { IntArray(b.length + 1) }
            for (i in 0..a.length) d[i][0] = i
            for (j in 0..b.length) d[0][j] = j
            for (i in 1..a.length) for (j in 1..b.length) {
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
            }
            return d[a.length][b.length] <= 2 && d[a.length][b.length].toFloat() / maxOf(a.length, b.length) <= .4f
        }
    }
}
