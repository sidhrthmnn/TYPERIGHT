package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing statistical N-gram frequency occurrences
 * (Unigrams, Bigrams, Trigrams, Quadgrams) used for offline predictive typing.
 */
@Entity(
    tableName = "ngram_frequencies",
    indices = [
        Index(value = ["ngramOrder", "context"]),
        Index(value = ["ngramOrder", "context", "nextWord"], unique = true),
        Index(value = ["ngramOrder", "frequency"]),
        Index(value = ["nextWord"]),
        Index(value = ["lastUsedTimestamp"])
    ]
)
data class NGramFrequency(
    /**
     * Unique identifier composed of "$ngramOrder:$context:$nextWord".
     * Natural key prevents duplicate records and facilitates atomic replacement/updates.
     */
    @PrimaryKey
    val id: String,

    /**
     * Order of the N-gram:
     * 1 = Unigram (P(w))
     * 2 = Bigram (P(w2 | w1))
     * 3 = Trigram (P(w3 | w1, w2))
     * 4 = Quadgram (P(w4 | w1, w2, w3))
     */
    val ngramOrder: Int,

    /**
     * Preceding context words, space-separated and lowercase.
     * E.g., "" for unigram, "how" for bigram, "how are" for trigram.
     */
    val context: String,

    /**
     * Predicted following word (lowercase).
     */
    val nextWord: String,

    /**
     * Observation count / frequency.
     */
    val frequency: Int = 1,

    /**
     * Pre-calculated or interpolated conditional probability P(nextWord | context).
     */
    val probability: Float = 0f,

    /**
     * Unix timestamp of the most recent observation or usage.
     */
    val lastUsedTimestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun createId(ngramOrder: Int, context: String, nextWord: String): String {
            return "$ngramOrder:${context.trim().lowercase()}:${nextWord.trim().lowercase()}"
        }

        fun create(
            ngramOrder: Int,
            context: String,
            nextWord: String,
            frequency: Int = 1,
            probability: Float = 0f,
            timestamp: Long = System.currentTimeMillis()
        ): NGramFrequency {
            val cleanContext = context.trim().lowercase()
            val cleanWord = nextWord.trim().lowercase()
            return NGramFrequency(
                id = createId(ngramOrder, cleanContext, cleanWord),
                ngramOrder = ngramOrder,
                context = cleanContext,
                nextWord = cleanWord,
                frequency = frequency,
                probability = probability,
                lastUsedTimestamp = timestamp
            )
        }
    }
}
