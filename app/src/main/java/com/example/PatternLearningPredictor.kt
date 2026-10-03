package com.example

import android.content.Context
import android.graphics.PointF
import android.util.Log
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @Query("SELECT * FROM learned_swipe_patterns ORDER BY timestamp DESC LIMIT 256")
    suspend fun getAllSwipePatterns(): List<LearnedSwipePattern>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSwipePattern(pattern: LearnedSwipePattern)

    @Query("DELETE FROM learned_swipe_patterns WHERE word NOT IN (SELECT word FROM learned_swipe_patterns ORDER BY timestamp DESC LIMIT 256)")
    suspend fun pruneSwipePatterns()

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
    private val settings = KeyboardSettings(context)
    private val writes = Mutex()
    @Volatile private var resetGeneration = 0L

    private val swipeTemplates = LinkedHashMap<String, List<PointF>>()

    init {
        val generation=resetGeneration
        scope.launch { writes.withLock {
            // Legacy touch calibration never used confirmed intended letters.
            // Keep Room's schema for upgrades; the FTRL learner owns touch data.
            dao.clearTouchOffsets()
            dao.pruneSwipePatterns()
            val restored=dao.getAllSwipePatterns()
            if(generation==resetGeneration) synchronized(swipeTemplates) {
                restored.asReversed().forEach { record ->
                    val points=deserializePoints(record.pointsJson)
                    if(points.size==8) swipeTemplates[record.word.lowercase(Locale.ROOT)]=points
                }
            }
        } }
    }

    // ------------------------------------------
    // 1. Swipe Pattern Learning (K-Nearest Neighbors Template Matching)
    // ------------------------------------------

    /**
     * Train the swipe model with a successful user swipe path for a word.
     */
    fun learnSwipePattern(word: String, path: List<PointF>) {
        val cleanWord = word.lowercase(Locale.ROOT).trim()
        if (!settings.personalizedLearningEnabled || cleanWord.length !in 2..32 || path.size < 2) return
        val generation=resetGeneration
        val samples=path.map { PointF(it.x,it.y) }

        scope.launch {
            try {
                val downsampled = downsamplePath(samples, 8)
                writes.withLock {
                  if (generation==resetGeneration && settings.personalizedLearningEnabled && downsampled.size == 8) {
                    synchronized(swipeTemplates) {
                        swipeTemplates.remove(cleanWord)
                        swipeTemplates[cleanWord] = downsampled
                        while(swipeTemplates.size>256) swipeTemplates.remove(swipeTemplates.keys.first())
                    }
                    val serialized = serializePoints(downsampled)
                    dao.insertSwipePattern(LearnedSwipePattern(cleanWord, serialized))
                    dao.pruneSwipePatterns()
                  }
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
        if (!settings.personalizedLearningEnabled || path.size < 2) return emptyList()
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

    fun clearAllLearnedData(onComplete: (() -> Unit)? = null): kotlinx.coroutines.Job {
        resetGeneration++
        synchronized(swipeTemplates) { swipeTemplates.clear() }
        return scope.launch {
            try {
              writes.withLock {
                dao.clearSwipePatterns()
                dao.clearTouchOffsets()
                dao.clearBigrams()
                dao.clearTrigrams()

                synchronized(swipeTemplates) { swipeTemplates.clear() }
              }

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
