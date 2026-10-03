package com.example

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.sqrt

/** QWERTY geometry and spatial edit evidence. OnlineTypingLearner owns calibrated tap likelihoods. */
class SpatialKeyProximityModel {

    data class KeyHitbox(
        val char: Char,
        val centroid: PointF,
        val bounds: RectF
    )

    // Standard QWERTY key hitboxes normalized to [0.0, 1.0] coordinates
    private val keyHitboxes: Map<Char, KeyHitbox>

    init {
        val map = HashMap<Char, KeyHitbox>()
        // Row 1: q w e r t y u i o p (10 keys, width = 0.10, height = 0.25)
        val row1 = "qwertyuiop"
        val row1Y = 0.15f
        val keyW1 = 0.10f
        val keyH = 0.25f
        for (i in row1.indices) {
            val ch = row1[i]
            val x = i * keyW1 + keyW1 / 2f
            map[ch] = KeyHitbox(
                char = ch,
                centroid = PointF(x, row1Y),
                bounds = RectF(i * keyW1, row1Y - keyH / 2f, (i + 1) * keyW1, row1Y + keyH / 2f)
            )
        }

        // Row 2: a s d f g h j k l (9 keys, offset = 0.05, width = 0.10)
        val row2 = "asdfghjkl"
        val row2Y = 0.48f
        val offset2 = 0.05f
        for (i in row2.indices) {
            val ch = row2[i]
            val x = offset2 + i * keyW1 + keyW1 / 2f
            map[ch] = KeyHitbox(
                char = ch,
                centroid = PointF(x, row2Y),
                bounds = RectF(offset2 + i * keyW1, row2Y - keyH / 2f, offset2 + (i + 1) * keyW1, row2Y + keyH / 2f)
            )
        }

        // Row 3: z x c v b n m (7 keys, offset = 0.15, width = 0.10)
        val row3 = "zxcvbnm"
        val row3Y = 0.82f
        val offset3 = 0.15f
        for (i in row3.indices) {
            val ch = row3[i]
            val x = offset3 + i * keyW1 + keyW1 / 2f
            map[ch] = KeyHitbox(
                char = ch,
                centroid = PointF(x, row3Y),
                bounds = RectF(offset3 + i * keyW1, row3Y - keyH / 2f, offset3 + (i + 1) * keyW1, row3Y + keyH / 2f)
            )
        }

        keyHitboxes = map
    }

    /**
     * Returns the physical key centroid point for a character, or null if not in layout.
     */
    fun getKeyCentroid(char: Char): PointF? = keyHitboxes[char.lowercaseChar()]?.centroid

    /**
     * Calculates the physical Euclidean distance between two keys on the normalized keyboard.
     */
    fun getPhysicalKeyDistance(c1: Char, c2: Char): Float {
        val low1 = c1.lowercaseChar()
        val low2 = c2.lowercaseChar()
        if (low1 == low2) return 0f
        val p1 = keyHitboxes[low1]?.centroid ?: return 1.0f
        val p2 = keyHitboxes[low2]?.centroid ?: return 1.0f
        val dx = p1.x - p2.x
        val dy = p1.y - p2.y
        return sqrt(dx * dx + dy * dy)
    }

    /**
     * Calculates a non-uniform substitution cost between two characters based on key distance.
     * Adjacent keys have low substitution cost (e.g. 0.20 - 0.40), while distant keys cost 1.0.
     */
    fun getWeightedSubstitutionCost(c1: Char, c2: Char): Float {
        if (c1.lowercaseChar() == c2.lowercaseChar()) return 0.0f
        val dist = getPhysicalKeyDistance(c1, c2)
        return if (dist < 0.18f) {
            0.25f + (dist / 0.18f) * 0.20f // 0.25 to 0.45 for physical neighbors
        } else if (dist < 0.35f) {
            0.60f + (dist / 0.35f) * 0.25f // 0.60 to 0.85 for moderate distance
        } else {
            1.0f // distant keys
        }
    }

    /**
     * Computes spatial-proximity weighted Damerau-Levenshtein edit distance between two strings.
     */
    private val weightedScratch = ThreadLocal.withInitial { FloatArray(65 * 65) }
    private val latinCosts by lazy {
        FloatArray(26 * 26) { index -> getWeightedSubstitutionCost('a' + index / 26, 'a' + index % 26) }
    }
    fun computeSpatialEditDistance(s1: String, s2: String): Float = weightedEditDistance(s1, s2, 1f)

    /** Shared geometry scorer. Reuse worker storage rather than allocate a matrix per candidate. */
    internal fun weightedEditDistance(s1: String, s2: String, initialCost: Float = .95f): Float {
        val w1 = s1.lowercase(java.util.Locale.ROOT).replace("'", "")
        val w2 = s2.lowercase(java.util.Locale.ROOT).replace("'", "")
        if (w1 == w2) return 0f
        val n = w1.length; val m = w2.length
        if (n > 64 || m > 64) return maxOf(n, m).toFloat()
        val dp = requireNotNull(weightedScratch.get()); val stride = 65
        val costs = latinCosts
        for (i in 0..n) dp[i * stride] = i * initialCost
        for (j in 0..m) dp[j] = j * initialCost
        for (i in 1..n) for (j in 1..m) {
            val c1 = w1[i - 1]; val c2 = w2[j - 1]
            val sub = if (c1 == c2) 0f else if (c1 in 'a'..'z' && c2 in 'a'..'z') costs[(c1 - 'a') * 26 + (c2 - 'a')]
                else getWeightedSubstitutionCost(c1, c2)
            val cell = i * stride + j
            dp[cell] = minOf(dp[cell - stride] + .95f, dp[cell - 1] + .95f, dp[cell - stride - 1] + sub)
            if (i > 1 && j > 1 && c1 == w2[j - 2] && w1[i - 2] == c2) dp[cell] = minOf(dp[cell], dp[cell - 2 * stride - 2] + .25f)
        }
        return dp[n * stride + m]
    }
}
