package com.example

import android.content.Context
import android.graphics.PointF
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.*

/** Pairwise FTRL-Proximal residual model. Only bounded features, never messages, reach disk. */
class OnlineTypingLearner internal constructor(private val owner: Context) {
    data class Features(val indices: IntArray, val values: FloatArray)
    data class Snapshot(val version: Long, val weights: FloatArray, val touch: Map<String, TouchStats>) {
        fun score(features: Features): Float = features.indices.indices.sumOf {
            (weights[features.indices[it]] * features.values[it]).toDouble()
        }.toFloat()
    }
    data class TouchStats(val count: Int, val dx: Float, val dy: Float, val vx: Float, val vy: Float)
    private val worker = Executors.newSingleThreadScheduledExecutor { Thread(it, "typing-learner").apply { isDaemon = true } }
    private val file = File(owner.noBackupFilesDir, "typing-ftrl-v1.bin")
    private val z = FloatArray(SIZE)
    private val n = FloatArray(SIZE)
    private val touch = linkedMapOf<String, TouchStats>()
    @Volatile private var generation = 0L
    private var pendingWrite: java.util.concurrent.ScheduledFuture<*>? = null
    @Volatile var snapshot = Snapshot(0, FloatArray(SIZE), emptyMap())
        private set
    init { val epoch = generation; worker.execute { if (generation == epoch) { load(); publish() } } }

    fun feedback(chosen: Features, alternative: Features, strength: Float = 1f) {
        if (!KeyboardSettings(owner).personalizedLearningEnabled) return
        val epoch = generation
        worker.execute {
            if (epoch != generation || !KeyboardSettings(owner).personalizedLearningEnabled) return@execute
            val delta = linkedMapOf<Int, Float>()
            chosen.indices.indices.forEach { delta.merge(chosen.indices[it], chosen.values[it], Float::plus) }
            alternative.indices.indices.forEach { delta.merge(alternative.indices[it], -alternative.values[it], Float::plus) }
            val w = weights()
            val logit = delta.entries.sumOf { (w[it.key] * it.value).toDouble() }.coerceIn(-15.0, 15.0)
            val error = ((1.0 / (1.0 + exp(-logit))) - 1).toFloat() * strength
            delta.forEach { (i, x) ->
                val g = error * x
                val sigma = (sqrt(n[i] + g * g) - sqrt(n[i])) / ALPHA
                z[i] += g - sigma * w[i]; n[i] += g * g
            }
            if (epoch == generation) { publish(); scheduleSave() }
        }
    }

    /** Only aligned, confirmed words with real samples train intended-key offsets. */
    fun confirmTouches(typed: String, intended: String, taps: List<PointF?>, layout: String, spatial: SpatialKeyProximityModel) {
        if (!KeyboardSettings(owner).personalizedLearningEnabled || layout != DEFAULT_LAYOUT || taps.size != typed.length) return
        // Insertions/deletions are ambiguous; transpositions and substitutions have clear intended keys.
        if (typed.length != intended.length) return
        val samples = taps.map { it?.let { p -> PointF(p.x, p.y) } }
        val epoch = generation
        worker.execute {
            if (epoch != generation || !KeyboardSettings(owner).personalizedLearningEnabled) return@execute
            intended.lowercase().forEachIndexed { i, char ->
                val p = samples[i] ?: return@forEachIndexed
                val center = spatial.getKeyCentroid(char) ?: return@forEachIndexed
                val dx = (p.x - center.x).coerceIn(-.15f, .15f)
                val dy = (p.y - center.y).coerceIn(-.20f, .20f)
                val key = "$layout:$char"
                val old = touch[key] ?: TouchStats(0, 0f, 0f, .0056f, .0081f)
                val count = minOf(old.count + 1, 1000)
                val rate = 1f / count
                val mx = old.dx + rate * (dx - old.dx); val my = old.dy + rate * (dy - old.dy)
                touch[key] = TouchStats(count, mx, my, (old.vx + rate * ((dx-old.dx)*(dx-mx)-old.vx)).coerceAtLeast(.001f), (old.vy + rate * ((dy-old.dy)*(dy-my)-old.vy)).coerceAtLeast(.001f))
            }
            while (touch.size > 128) touch.remove(touch.keys.first())
            if (epoch == generation) { publish(); scheduleSave() }
        }
    }
    fun touchLikelihood(word: String, taps: List<PointF?>?, layout: String, spatial: SpatialKeyProximityModel, state: Snapshot = snapshot): Float {
        if (taps == null || taps.size != word.length || layout != DEFAULT_LAYOUT) return 0f
        var sum = 0f; var count = 0
        word.forEachIndexed { i, c ->
            val tap = taps[i] ?: return@forEachIndexed
            val center = spatial.getKeyCentroid(c) ?: return@forEachIndexed
            val learned = state.touch["$layout:${c.lowercaseChar()}"]?.takeIf { it.count >= 5 }
            val dx = tap.x - center.x - (learned?.dx ?: 0f)
            val dy = tap.y - center.y - (learned?.dy ?: 0f)
            sum += exp(-.5f * (dx*dx/(learned?.vx ?: .0056f) + dy*dy/(learned?.vy ?: .0081f))); count++
        }
        return if (count == 0) 0f else sum/count
    }
    fun clear() {
        generation++
        snapshot = Snapshot(snapshot.version + 1, FloatArray(SIZE), emptyMap())
        worker.execute { z.fill(0f); n.fill(0f); touch.clear(); publish(); file.delete() }
    }
    internal fun awaitIdle() { worker.submit { pendingWrite?.cancel(false); save() }.get(10, TimeUnit.SECONDS) }
    private fun scheduleSave() {
        pendingWrite?.cancel(false)
        val epoch = generation
        pendingWrite = worker.schedule({ if (epoch == generation) save() }, 250, TimeUnit.MILLISECONDS)
    }
    private fun weights() = FloatArray(SIZE) { i ->
        if (abs(z[i]) <= L1) 0f else -(z[i] - sign(z[i]) * L1) / ((BETA + sqrt(n[i])) / ALPHA + L2)
    }
    private fun publish() { snapshot = Snapshot(snapshot.version + 1, weights(), touch.toMap()) }
    private fun save() {
        val tmp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(1); out.writeInt(SIZE)
                for (i in 0 until SIZE) { out.writeFloat(z[i]); out.writeFloat(n[i]) }
                out.writeInt(touch.size)
                touch.forEach { (k,v) -> out.writeUTF(k); out.writeInt(v.count); out.writeFloat(v.dx); out.writeFloat(v.dy); out.writeFloat(v.vx); out.writeFloat(v.vy) }
            }
            if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        }
    }
    private fun load() { runCatching {
        if (!file.exists()) return
        DataInputStream(file.inputStream().buffered()).use { input ->
            require(input.readInt() == 1 && input.readInt() == SIZE)
            for (i in 0 until SIZE) { z[i] = input.readFloat(); n[i] = input.readFloat(); require(z[i].isFinite() && n[i].isFinite() && n[i] >= 0) }
            repeat(input.readInt().also { require(it in 0..128) }) {
                val key = input.readUTF(); touch[key] = TouchStats(input.readInt(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
            }
        }
    }.onFailure { z.fill(0f); n.fill(0f); touch.clear(); file.delete() } }
    companion object {
        const val SIZE = 4096
        const val DEFAULT_LAYOUT = "qwerty-normalized-v1"
        private const val ALPHA = .25f; private const val BETA = 1f; private const val L1 = .02f; private const val L2 = .5f
        fun features(task: RankingTask, word: String, typed: String, prior: List<String>, language: String, numeric: FloatArray,
            recency: Float = 0f, variantFamily: String? = null, mixed: Boolean = false): Features {
            // Existing numeric slots and hash labels retain their meanings, preserving
            // compatible v1 weights. New evidence uses previously unassigned slots.
            require(numeric.size < 31)
            val labels = listOf("task:$task", "word:$task:$word", "edit:$typed>$word", "language:$language:$word", "last:${prior.lastOrNull()}:$word", "span:${prior.takeLast(2).joinToString(" ")}:$word") +
                listOfNotNull(variantFamily?.let { "variant:$task:$it:$word" },if(mixed) "mixed:$task:$language:$word" else null)
            val indices = IntArray(numeric.size + labels.size + 1) { if (it < numeric.size) it else if (it == numeric.size+labels.size) 31 else 32 + (labels[it - numeric.size].hashCode().and(Int.MAX_VALUE) % (SIZE - 32)) }
            return Features(indices, numeric + FloatArray(labels.size) { 1f } + recency)
        }
    }
}
