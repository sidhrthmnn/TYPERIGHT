package com.example

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.sqrt

// ==========================================
// ROOM ENTITIES FOR MACHINE LEARNING ENGINE
// ==========================================

@Entity(tableName = "learned_swipe_patterns")
data class LearnedSwipePattern(
    @PrimaryKey val word: String,
    val pointsJson: String, // Normalized points format: "x1,y1,x2,y2,..."
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_touch_offsets")
data class LearnedTouchOffset(
    @PrimaryKey val char: String, // Single character, lowercase
    val dxSum: Float,
    val dySum: Float,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_bigrams")
data class LearnedBigram(
    @PrimaryKey val id: String, // Format: "prevWord:nextWord"
    val prevWord: String,
    val nextWord: String,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "learned_trigrams")
data class LearnedTrigram(
    @PrimaryKey val id: String, // Format: "prev2:prev1:nextWord"
    val prev2: String,
    val prev1: String,
    val nextWord: String,
    val count: Int,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// DATA ACCESS OBJECT (DAO)
// ==========================================

@Dao
interface PatternLearningDao {
    @Query("SELECT * FROM learned_swipe_patterns")
    suspend fun getAllSwipePatterns(): List<LearnedSwipePattern>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwipePattern(pattern: LearnedSwipePattern)

    @Query("SELECT * FROM learned_touch_offsets")
    suspend fun getAllTouchOffsets(): List<LearnedTouchOffset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTouchOffset(offset: LearnedTouchOffset)

    @Query("SELECT * FROM learned_bigrams")
    suspend fun getAllBigrams(): List<LearnedBigram>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBigram(bigram: LearnedBigram)

    @Query("SELECT * FROM learned_trigrams")
    suspend fun getAllTrigrams(): List<LearnedTrigram>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrigram(trigram: LearnedTrigram)

    @Query("DELETE FROM learned_swipe_patterns")
    suspend fun clearSwipePatterns()

    @Query("DELETE FROM learned_touch_offsets")
    suspend fun clearTouchOffsets()

    @Query("DELETE FROM learned_bigrams")
    suspend fun clearBigrams()

    @Query("DELETE FROM learned_trigrams")
    suspend fun clearTrigrams()
}

// ==========================================
// CORE MACHINE LEARNING PREDICTOR SYSTEM
// ==========================================

class PatternLearningPredictor private constructor(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val dao = database.patternLearningDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    // In-memory caches to guarantee sub-millisecond response times for real-time predictions
    private val swipeTemplates = HashMap<String, List<PointF>>()
    private val touchOffsets = HashMap<Char, PointF>() // Average offset dx, dy per key
    private val bigramCounts = HashMap<String, HashMap<String, Int>>() // prevWord -> (nextWord -> count)
    private val trigramCounts = HashMap<String, HashMap<String, Int>>() // "prev2:prev1" -> (nextWord -> count)

    init {
        loadDataFromDatabase()
    }

    private fun loadDataFromDatabase() {
        scope.launch {
            try {
                // 1. Load Swipe Templates
                val dbSwipe = dao.getAllSwipePatterns()
                synchronized(swipeTemplates) {
                    dbSwipe.forEach {
                        val points = deserializePoints(it.pointsJson)
                        if (points.isNotEmpty()) {
                            swipeTemplates[it.word.lowercase()] = points
                        }
                    }
                }

                // 2. Load Touch Offsets
                val dbTouch = dao.getAllTouchOffsets()
                synchronized(touchOffsets) {
                    dbTouch.forEach {
                        if (it.char.isNotEmpty() && it.count > 0) {
                            val charKey = it.char[0].lowercaseChar()
                            touchOffsets[charKey] = PointF(it.dxSum / it.count, it.dySum / it.count)
                        }
                    }
                }

                // 3. Load Bigrams
                val dbBigrams = dao.getAllBigrams()
                synchronized(bigramCounts) {
                    dbBigrams.forEach {
                        val innerMap = bigramCounts.getOrPut(it.prevWord.lowercase()) { HashMap() }
                        innerMap[it.nextWord.lowercase()] = it.count
                    }
                }

                // 4. Load Trigrams
                val dbTrigrams = dao.getAllTrigrams()
                synchronized(trigramCounts) {
                    dbTrigrams.forEach {
                        val key = "${it.prev2.lowercase()}:${it.prev1.lowercase()}"
                        val innerMap = trigramCounts.getOrPut(key) { HashMap() }
                        innerMap[it.nextWord.lowercase()] = it.count
                    }
                }
                
                Log.d("MLPredictor", "Successfully loaded ML caches. Swipes: ${swipeTemplates.size}, Touches: ${touchOffsets.size}, Bigrams: ${bigramCounts.size}, Trigrams: ${trigramCounts.size}")
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error loading ML models: ${e.message}", e)
            }
        }
    }

    // ------------------------------------------
    // 1. Swipe Pattern Learning (K-Nearest Neighbors Template Matching)
    // ------------------------------------------

    /**
     * Train the swipe model with a successful user swipe path for a word.
     */
    fun learnSwipePattern(word: String, path: List<PointF>) {
        val cleanWord = word.lowercase(Locale.ROOT).trim()
        if (cleanWord.isEmpty() || path.size < 2) return

        scope.launch {
            try {
                val downsampled = downsamplePath(path, 8)
                if (downsampled.size == 8) {
                    synchronized(swipeTemplates) {
                        swipeTemplates[cleanWord] = downsampled
                    }
                    val serialized = serializePoints(downsampled)
                    dao.insertSwipePattern(LearnedSwipePattern(cleanWord, serialized))
                }
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error learning swipe: ${e.message}")
            }
        }
    }

    /**
     * Predict matching words based on user swiping history templates (KNN).
     * Returns matching words sorted by highest similarity.
     */
    fun predictFromSwipePatterns(path: List<PointF>, confidenceThreshold: Float = 0.04f): List<Pair<String, Float>> {
        if (path.size < 2) return emptyList()
        val downsampledNew = downsamplePath(path, 8)
        if (downsampledNew.size != 8) return emptyList()

        val matches = ArrayList<Pair<String, Float>>()
        synchronized(swipeTemplates) {
            for ((word, template) in swipeTemplates) {
                val dist = calculatePathSimilarity(downsampledNew, template)
                // Normalize and filter based on strict threshold
                if (dist <= confidenceThreshold) {
                    val similarity = 1.0f - (dist / confidenceThreshold)
                    matches.add(Pair(word, similarity))
                }
            }
        }
        return matches.sortedByDescending { it.second }
    }

    // ------------------------------------------
    // 2. Typing Touch Offsets Learning (Online Mean Estimation)
    // ------------------------------------------

    /**
     * Learn the user's specific key press coordinate deviations to adapt the keyboard map.
     */
    fun learnTapPattern(char: Char, tapX: Float, tapY: Float, targetX: Float, targetY: Float) {
        val charKey = char.lowercaseChar()
        if (charKey !in 'a'..'z') return

        val dx = tapX - targetX
        val dy = tapY - targetY

        scope.launch {
            try {
                val dbRow = dao.getAllTouchOffsets().firstOrNull { it.char == charKey.toString() }
                
                val count = dbRow?.count ?: 0
                val dxSum = dbRow?.dxSum ?: 0f
                val dySum = dbRow?.dySum ?: 0f

                // Use a rolling memory limit to keep learning adaptive to grip/hand posture changes
                val newCount = (count + 1).coerceAtMost(50)
                val decay = if (count >= 50) 0.95f else 1.0f

                val newDxSum = (dxSum * decay) + dx
                val newDySum = (dySum * decay) + dy

                val updatedOffset = LearnedTouchOffset(
                    char = charKey.toString(),
                    dxSum = newDxSum,
                    dySum = newDySum,
                    count = newCount
                )

                dao.insertTouchOffset(updatedOffset)

                synchronized(touchOffsets) {
                    touchOffsets[charKey] = PointF(newDxSum / newCount, newDySum / newCount)
                }
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error learning tap pattern: ${e.message}")
            }
        }
    }

    /**
     * Get the learned average tap offset (dx, dy) for a physical key.
     */
    fun getTouchOffset(char: Char): PointF {
        val charKey = char.lowercaseChar()
        return synchronized(touchOffsets) {
            touchOffsets[charKey] ?: PointF(0f, 0f)
        }
    }

    // ------------------------------------------
    // 3. Next-Word Prediction (Markov Model with Maximum Likelihood Estimation)
    // ------------------------------------------

    /**
     * Learn next-word sequence patterns from user typing transitions.
     */
    fun learnBigram(prev: String, current: String) {
        val p = prev.lowercase(Locale.ROOT).trim()
        val c = current.lowercase(Locale.ROOT).trim()
        if (p.isEmpty() || c.isEmpty() || p.length < 2 || c.length < 2) return

        scope.launch {
            try {
                val id = "$p:$c"
                val existingList = dao.getAllBigrams()
                val match = existingList.firstOrNull { it.id == id }
                val newCount = (match?.count ?: 0) + 1

                val bigram = LearnedBigram(id = id, prevWord = p, nextWord = c, count = newCount)
                dao.insertBigram(bigram)

                synchronized(bigramCounts) {
                    val innerMap = bigramCounts.getOrPut(p) { HashMap() }
                    innerMap[c] = newCount
                }
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error learning bigram transition: ${e.message}")
            }
        }
    }

    /**
     * Predict the next most likely words using MLE probabilities calculated from learned bigrams.
     */
    fun predictNextWords(prevWord: String): List<Pair<String, Float>> {
        val p = prevWord.lowercase(Locale.ROOT).trim()
        if (p.isEmpty()) return emptyList()

        val counts = synchronized(bigramCounts) {
            bigramCounts[p]?.let { HashMap(it) }
        } ?: return emptyList()

        val totalTransitions = counts.values.sum().toFloat()
        if (totalTransitions == 0f) return emptyList()

        return counts.entries.map {
            val probability = it.value / totalTransitions
            Pair(it.key, probability)
        }.sortedByDescending { it.second }.take(3)
    }

    /**
     * Learn trigram 3-word sequence patterns (prev2 + prev1 -> current).
     */
    fun learnTrigram(prev2: String, prev1: String, current: String) {
        val p2 = prev2.lowercase(Locale.ROOT).trim()
        val p1 = prev1.lowercase(Locale.ROOT).trim()
        val c = current.lowercase(Locale.ROOT).trim()
        if (p2.isEmpty() || p1.isEmpty() || c.isEmpty()) return

        scope.launch {
            try {
                val key = "$p2:$p1"
                val id = "$key:$c"
                val existingList = dao.getAllTrigrams()
                val match = existingList.firstOrNull { it.id == id }
                val newCount = (match?.count ?: 0) + 1

                val trigram = LearnedTrigram(id = id, prev2 = p2, prev1 = p1, nextWord = c, count = newCount)
                dao.insertTrigram(trigram)

                synchronized(trigramCounts) {
                    val innerMap = trigramCounts.getOrPut(key) { HashMap() }
                    innerMap[c] = newCount
                }
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error learning trigram sequence: ${e.message}")
            }
        }
    }

    /**
     * Predict next words using 3-word trigram context (higher accuracy than bigrams).
     */
    fun predictNextWordsFromTrigram(prev2: String, prev1: String): List<Pair<String, Float>> {
        val p2 = prev2.lowercase(Locale.ROOT).trim()
        val p1 = prev1.lowercase(Locale.ROOT).trim()
        if (p2.isEmpty() || p1.isEmpty()) return emptyList()

        val key = "$p2:$p1"
        val counts = synchronized(trigramCounts) {
            trigramCounts[key]?.let { HashMap(it) }
        } ?: return emptyList()

        val totalTransitions = counts.values.sum().toFloat()
        if (totalTransitions == 0f) return emptyList()

        return counts.entries.map {
            val probability = it.value / totalTransitions
            Pair(it.key, probability)
        }.sortedByDescending { it.second }.take(3)
    }

    data class HabitLearningStats(
        val swipeTemplatesCount: Int = 0,
        val calibratedKeysCount: Int = 0,
        val learnedBigramsCount: Int = 0,
        val learnedTrigramsCount: Int = 0
    )

    fun getHabitLearningStats(): HabitLearningStats {
        return HabitLearningStats(
            swipeTemplatesCount = synchronized(swipeTemplates) { swipeTemplates.size },
            calibratedKeysCount = synchronized(touchOffsets) { touchOffsets.size },
            learnedBigramsCount = synchronized(bigramCounts) { bigramCounts.values.sumOf { it.size } },
            learnedTrigramsCount = synchronized(trigramCounts) { trigramCounts.values.sumOf { it.size } }
        )
    }

    fun clearAllLearnedData(onComplete: (() -> Unit)? = null) {
        scope.launch {
            try {
                dao.clearSwipePatterns()
                dao.clearTouchOffsets()
                dao.clearBigrams()
                dao.clearTrigrams()

                synchronized(swipeTemplates) { swipeTemplates.clear() }
                synchronized(touchOffsets) { touchOffsets.clear() }
                synchronized(bigramCounts) { bigramCounts.clear() }
                synchronized(trigramCounts) { trigramCounts.clear() }

                Log.i("MLPredictor", "All learned typing patterns and habit caches cleared.")
                onComplete?.invoke()
            } catch (e: Exception) {
                Log.e("MLPredictor", "Error clearing learned habit data: ${e.message}")
            }
        }
    }

    // ==========================================
    // MATHEMATICAL SUPPORT METHODS
    // ==========================================

    /**
     * Downsample a touch-coordinate path of arbitrary length to exactly targetSize points.
     */
    private fun downsamplePath(path: List<PointF>, targetSize: Int): List<PointF> {
        if (path.isEmpty()) return emptyList()
        if (path.size == 1) return List(targetSize) { PointF(path[0].x, path[0].y) }

        val distances = FloatArray(path.size - 1)
        var totalLength = 0f
        for (i in 0 until path.size - 1) {
            val dx = path[i + 1].x - path[i].x
            val dy = path[i + 1].y - path[i].y
            val d = sqrt(dx * dx + dy * dy)
            distances[i] = d
            totalLength += d
        }

        if (totalLength == 0f) {
            return List(targetSize) { PointF(path[0].x, path[0].y) }
        }

        val downsampled = ArrayList<PointF>(targetSize)
        downsampled.add(PointF(path[0].x, path[0].y))

        for (k in 1 until targetSize - 1) {
            val targetDist = (k.toFloat() / (targetSize - 1)) * totalLength
            var accumDist = 0f
            var interpolated = false
            for (i in distances.indices) {
                val segmentEnd = accumDist + distances[i]
                if (targetDist <= segmentEnd) {
                    val frac = if (distances[i] > 0f) (targetDist - accumDist) / distances[i] else 0f
                    val p1 = path[i]
                    val p2 = path[i + 1]
                    val ix = p1.x + frac * (p2.x - p1.x)
                    val iy = p1.y + frac * (p2.y - p1.y)
                    downsampled.add(PointF(ix, iy))
                    interpolated = true
                    break
                }
                accumDist = segmentEnd
            }
            if (!interpolated) {
                downsampled.add(PointF(path.last().x, path.last().y))
            }
        }
        downsampled.add(PointF(path.last().x, path.last().y))
        return downsampled
    }

    private fun calculatePathSimilarity(p1: List<PointF>, p2: List<PointF>): Float {
        if (p1.size != p2.size || p1.isEmpty()) return Float.MAX_VALUE
        var sum = 0f
        for (i in p1.indices) {
            val dx = p1[i].x - p2[i].x
            val dy = p1[i].y - p2[i].y
            sum += dx * dx + dy * dy
        }
        return sum / p1.size
    }

    private fun serializePoints(points: List<PointF>): String {
        return points.joinToString(",") { "${it.x}:${it.y}" }
    }

    private fun deserializePoints(str: String): List<PointF> {
        if (str.isEmpty()) return emptyList()
        return try {
            str.split(",").map {
                val parts = it.split(":")
                PointF(parts[0].toFloat(), parts[1].toFloat())
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PatternLearningPredictor? = null

        fun getInstance(context: Context): PatternLearningPredictor {
            return INSTANCE ?: synchronized(this) {
                val instance = PatternLearningPredictor(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
