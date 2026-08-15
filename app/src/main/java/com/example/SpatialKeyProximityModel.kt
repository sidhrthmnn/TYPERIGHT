package com.example

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 1. Spatial Key-Proximity Model
 *
 * Models the soft keyboard physical hitbox geometry and computes a probability distribution
 * over keys for each tap coordinate (x, y) rather than simple nearest-key discrete matching.
 *
 * Uses a bivariate Gaussian distribution with configurable touch radius / spatial falloff (sigmaX, sigmaY).
 */
class SpatialKeyProximityModel(
    private val sigmaX: Float = 0.075f,
    private val sigmaY: Float = 0.090f
) {

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
     * Computes the probability density P(char | tapX, tapY) using a 2D Gaussian touch falloff.
     */
    fun getKeyProbability(char: Char, tapX: Float, tapY: Float): Float {
        val hitbox = keyHitboxes[char.lowercaseChar()] ?: return 0.001f
        val dx = (tapX - hitbox.centroid.x) / sigmaX
        val dy = (tapY - hitbox.centroid.y) / sigmaY
        val exponent = -0.5f * (dx * dx + dy * dy)
        return exp(exponent.coerceIn(-15f, 0f))
    }

    /**
     * Computes a full probability distribution over all keys for a single tap coordinate.
     * Normalized so that the sum of probabilities across the keyboard is 1.0.
     */
    fun getKeyDistribution(tapX: Float, tapY: Float): Map<Char, Float> {
        val rawScores = HashMap<Char, Float>()
        var sum = 0.0f
        for ((ch, hitbox) in keyHitboxes) {
            val dx = (tapX - hitbox.centroid.x) / sigmaX
            val dy = (tapY - hitbox.centroid.y) / sigmaY
            val prob = exp((-0.5f * (dx * dx + dy * dy)).coerceIn(-12f, 0f))
            rawScores[ch] = prob
            sum += prob
        }

        if (sum <= 0f) return rawScores
        return rawScores.mapValues { it.value / sum }
    }

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
     * Computes the overall spatial likelihood P(TapSequence | CandidateWord) = Product P(tap_i | char_i).
     * Returns a normalized probability score in [0.0, 1.0].
     */
    fun computeSpatialTouchLikelihood(candidateWord: String, tapPoints: List<PointF>?): Float {
        val cleanCandidate = candidateWord.lowercase().replace("'", "").trim()
        if (tapPoints == null || tapPoints.isEmpty()) {
            return 0.70f // Calibrated baseline when tap coordinates are not recorded
        }

        if (tapPoints.size != cleanCandidate.length) {
            val lengthDiff = kotlin.math.abs(tapPoints.size - cleanCandidate.length)
            return (0.70f - lengthDiff * 0.15f).coerceAtLeast(0.20f)
        }

        var totalLogLikelihood = 0.0f
        var validKeyCount = 0

        for (i in cleanCandidate.indices) {
            val char = cleanCandidate[i]
            val tap = tapPoints[i]
            val hitbox = keyHitboxes[char] ?: continue
            val dx = (tap.x - hitbox.centroid.x) / sigmaX
            val dy = (tap.y - hitbox.centroid.y) / sigmaY
            val logLikelihood = -0.5f * (dx * dx + dy * dy)
            totalLogLikelihood += logLikelihood.coerceIn(-10f, 0f)
            validKeyCount++
        }

        if (validKeyCount == 0) return 0.70f
        val avgLogLikelihood = totalLogLikelihood / validKeyCount
        return exp(avgLogLikelihood.coerceIn(-8f, 0f))
    }

    fun computeSpatialLikelihood(candidateWord: String, tapPoints: List<PointF>?): Float {
        return computeSpatialTouchLikelihood(candidateWord, tapPoints)
    }

    /**
     * Computes spatial-proximity weighted Damerau-Levenshtein edit distance between two strings.
     */
    fun computeSpatialEditDistance(s1: String, s2: String): Float {
        val w1 = s1.lowercase().replace("'", "")
        val w2 = s2.lowercase().replace("'", "")
        if (w1 == w2) return 0.0f

        val n = w1.length
        val m = w2.length
        val dp = Array(n + 1) { FloatArray(m + 1) }

        for (i in 0..n) dp[i][0] = i.toFloat()
        for (j in 0..m) dp[0][j] = j.toFloat()

        for (i in 1..n) {
            for (j in 1..m) {
                val c1 = w1[i - 1]
                val c2 = w2[j - 1]

                val subCost = getWeightedSubstitutionCost(c1, c2)

                dp[i][j] = minOf(
                    dp[i - 1][j] + 0.95f,        // deletion
                    dp[i][j - 1] + 0.95f,        // insertion
                    dp[i - 1][j - 1] + subCost   // substitution
                )

                // Transposition (e.g. teh -> the, adn -> and, woudl -> would)
                if (i > 1 && j > 1 && w1[i - 1] == w2[j - 2] && w1[i - 2] == w2[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 0.25f)
                }
            }
        }

        return dp[n][m]
    }
}
