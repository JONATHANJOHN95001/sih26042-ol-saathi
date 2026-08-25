package `in`.gov.tribalfln.data

import android.content.Context
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * LocalVectorDatabase — On-device vector store for offline semantic search.
 * Stores 384-dimensional embeddings (all-MiniLM-L6-v2 INT8) and performs
 * cosine similarity top-K retrieval. Zero network dependency.
 */
class LocalVectorDatabase(private val context: Context) {

    companion object {
        private const val TAG = "LocalVectorDB"
        private const val EMBEDDING_DIM = 384
        private const val MAX_ENTRIES = 10000
    }

    data class VectorEntry(
        val id: String,
        val text: String,
        val embedding: FloatArray,
        val metadata: Map<String, String> = emptyMap()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is VectorEntry) return false
            return id == other.id && text == other.text
        }
        override fun hashCode(): Int = id.hashCode() * 31 + text.hashCode()
    }

    data class SearchResult(
        val entry: VectorEntry,
        val score: Float
    )

    private val entries = mutableListOf<VectorEntry>()
    private val lock = Any()

    /**
     * Compute cosine similarity between two vectors of equal length.
     */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimensions must match: ${a.size} vs ${b.size}" }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    /**
     * Serialize a FloatArray to a ByteArray using little-endian encoding.
     */
    fun serializeVector(vector: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(vector.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { buf.putFloat(it) }
        return buf.array()
    }

    /**
     * Deserialize a ByteArray back to a FloatArray.
     */
    fun deserializeVector(data: ByteArray): FloatArray {
        val vector = FloatArray(data.size / 4)
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector)
        return vector
    }

    /**
     * Insert a vector entry into the database.
     */
    fun insert(entry: VectorEntry) {
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                Log.w(TAG, "Vector DB at capacity ($MAX_ENTRIES), dropping oldest")
                entries.removeAt(0)
            }
            entries.removeAll { it.id == entry.id }
            entries.add(entry)
        }
    }

    /**
     * Perform top-K cosine similarity search.
     */
    fun search(query: FloatArray, topK: Int = 5): List<SearchResult> {
        synchronized(lock) {
            return entries.map { entry ->
                SearchResult(entry, cosineSimilarity(query, entry.embedding))
            }
            .sortedByDescending { it.score }
            .take(topK)
        }
    }

    /**
     * Get total number of stored entries.
     */
    fun size(): Int = synchronized(lock) { entries.size }

    /**
     * Remove all entries.
     */
    fun clear() {
        synchronized(lock) { entries.clear() }
        Log.d(TAG, "Vector DB cleared")
    }

    /**
     * Close the database and release resources.
     */
    fun close() {
        clear()
        Log.d(TAG, "Vector DB closed")
    }
}
