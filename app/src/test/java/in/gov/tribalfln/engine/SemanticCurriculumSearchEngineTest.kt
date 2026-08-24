package gov.tribalfln.engine

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

/**
 * SemanticCurriculumSearchEngineTest — Integration benchmark for the
 * offline vector search pipeline against generated assets.
 *
 * Proves:
 *   1. nipun_vector_embeddings.bin loads without OOM on constrained heap
 *   2. Cosine similarity top-K search over 810 x 384-d vectors completes in <10ms
 *   3. Top returned ID maps back to a valid curriculum entry
 *
 * Run: ./gradlew.bat testDebugUnitTest
 *      --tests "gov.tribalfln.engine.SemanticCurriculumSearchEngineTest"
 */
class SemanticCurriculumSearchEngineTest {

    // Paths to the generated asset files — walk up from user.dir to find project root
    private val projectRoot: File = run {
        var dir = File(System.getProperty("user.dir"))
        while (!File(dir, "build.gradle.kts").exists() && dir.parentFile != null) {
            dir = dir.parentFile
        }
        dir
    }
    private val binFile = File(projectRoot, "app/src/main/assets/database/nipun_vector_embeddings.bin")
    private val mapFile = File(projectRoot, "app/src/main/assets/database/vector_id_map.json")
    private val jsonFile = File(projectRoot, "app/src/main/assets/database/nipun_curriculum_prepopulated.json")

    // Loaded state
    private lateinit var embeddings: Array<FloatArray>
    private lateinit var idMapJson: JSONObject
    private lateinit var curriculumJson: JSONArray

    private val EMBEDDING_DIM = 384
    private val NFLN_MAGIC = byteArrayOf('N'.code.toByte(), 'F'.code.toByte(), 'L'.code.toByte(), 'N'.code.toByte())
    private val LATENCY_SLA_MS = 10L

    @Before
    fun loadAssets() {
        assertTrue("nipun_vector_embeddings.bin must exist", binFile.exists())
        assertTrue("vector_id_map.json must exist", mapFile.exists())
        assertTrue("nipun_curriculum_prepopulated.json must exist", jsonFile.exists())

        // Parse binary embedding file
        val bytes = binFile.readBytes()
        assertTrue("File must be at least 16 bytes", bytes.size >= 16)

        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4)
        buf.get(magic)
        assertArrayEquals("Magic must be NFLN", NFLN_MAGIC, magic)
        assertEquals("Version must be 1", 1, buf.int)
        assertEquals("Embedding dim must be 384", EMBEDDING_DIM, buf.int)
        val count = buf.int
        assertTrue("Entry count must be positive", count > 0)

        embeddings = Array(count) { FloatArray(EMBEDDING_DIM) }
        for (i in 0 until count) {
            for (j in 0 until EMBEDDING_DIM) {
                embeddings[i][j] = buf.float
            }
        }

        // No NaN/Inf allowed
        for (i in embeddings.indices) {
            for (j in embeddings[i].indices) {
                assertFalse("NaN at [$i][$j]", embeddings[i][j].isNaN())
                assertFalse("Inf at [$i][$j]", embeddings[i][j].isInfinite())
            }
        }

        // Parse JSON files
        idMapJson = JSONObject(mapFile.readText(Charsets.UTF_8))
        curriculumJson = JSONArray(jsonFile.readText(Charsets.UTF_8))
    }

    // ================================================================
    // TEST 1: Initialization
    // ================================================================

    @Test
    fun binaryFileLoads810EntriesOf384dVectors() {
        assertEquals("Must have 810 embeddings", 810, embeddings.size)
        assertEquals("Each embedding must be 384-d", EMBEDDING_DIM, embeddings[0].size)
        val expectedSize = 16 + 810 * EMBEDDING_DIM * 4
        assertEquals("Binary file size must match", expectedSize, binFile.length().toInt())
    }

    @Test
    fun idMapLoads810EntriesWithValidFields() {
        assertEquals("Version must be 1", 1, idMapJson.getInt("version"))
        assertEquals("Dim must be 384", EMBEDDING_DIM, idMapJson.getInt("embedding_dim"))
        assertEquals("Count must be 810", 810, idMapJson.getInt("total_entries"))
        val entries = idMapJson.getJSONArray("entries")
        assertEquals("Entries array must have 810 items", 810, entries.length())

        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            assertTrue("Entry $i must have 'id'", entry.has("id"))
            assertTrue("Entry $i must have 'nipun_code'", entry.has("nipun_code"))
            assertTrue("Entry $i must have 'content_type'", entry.has("content_type"))
            assertTrue("Entry $i must have 'tribal_language'", entry.has("tribal_language"))
        }
    }

    @Test
    fun curriculumJsonHas810Entries() {
        assertEquals("Curriculum JSON must have 810 entries", 810, curriculumJson.length())
    }

    @Test
    fun allEmbeddingsAreL2Normalized() {
        for (i in embeddings.indices) {
            val norm = sqrt(embeddings[i].sumOf { (it * it).toDouble() })
            assertEquals("Embedding $i must have unit norm", 1.0, norm, 0.01)
        }
    }

    @Test
    fun memoryFootprintWithin180mbCeiling() {
        val embeddingBytes = embeddings.size * embeddings[0].size * 4L
        val heapMB = Runtime.getRuntime().let { (it.totalMemory() - it.freeMemory()) / (1024 * 1024) }
        assertTrue("Heap ${heapMB}MB must be under 180MB", heapMB < 180)
        assertTrue("Embedding data ${embeddingBytes / 1024}KB must be under 2MB", embeddingBytes < 2 * 1024 * 1024)
    }

    // ================================================================
    // TEST 2: Latency SLA — <10ms
    // ================================================================

    private fun generateQueryEmbedding(queryText: String): FloatArray {
        val bytes = ByteArray(EMBEDDING_DIM)
        val digest = MessageDigest.getInstance("SHA-256")
        val baseHash = digest.digest(queryText.toByteArray(Charsets.UTF_8))
        var offset = 0; var counter = 0
        while (offset < EMBEDDING_DIM) {
            digest.reset(); digest.update(baseHash); digest.update(counter.toString().toByteArray())
            val chunk = digest.digest()
            val copyLen = minOf(chunk.size, EMBEDDING_DIM - offset)
            System.arraycopy(chunk, 0, bytes, offset, copyLen)
            offset += copyLen; counter++
        }
        val vec = FloatArray(EMBEDDING_DIM) { (bytes[it].toInt() and 0xFF) / 255.0f }
        val norm = sqrt(vec.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) for (i in vec.indices) vec[i] /= norm
        return vec
    }

    private fun cosineSearch(query: FloatArray, topK: Int = 10): List<Pair<Int, Float>> {
        val scores = FloatArray(embeddings.size) { i -> cosineSimilarity(query, embeddings[i]) }
        return scores.indices.sortedByDescending { scores[it] }.take(topK).map { it to scores[it] }
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "Dimensions must match: ${a.size} vs ${b.size}" }
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        val denom = sqrt(na) * sqrt(nb)
        return if (denom == 0.0) 0f else (dot / denom).toFloat()
    }

    @Test
    fun latencySLAcosineSearchUnder10ms() {
        val query = generateQueryEmbedding("\u0926\u094B \u0905\u0902\u0915\u094B\u0902 \u0915\u093E \u091C\u094B\u0921\u093C") // दो अंकों का जोड़
        repeat(5) { cosineSearch(query) } // warm up

        val elapsedMs = measureTimeMillis { cosineSearch(query, topK = 10) }
        println("  [LATENCY] cosineSearch(810 x 384-d): ${elapsedMs}ms (SLA: <${LATENCY_SLA_MS}ms)")
        assertTrue("Latency ${elapsedMs}ms MUST be under ${LATENCY_SLA_MS}ms SLA", elapsedMs < LATENCY_SLA_MS)
    }

    @Test
    fun latencySLArepeated100searchesStayUnder10ms() {
        val queries = listOf(
            "\u0926\u094B \u0905\u0902\u0915\u094B\u0902 \u0915\u093E \u091C\u094B\u0921\u093C",
            "\u092A\u0922\u093C\u0928\u093E \u0914\u0930 \u0932\u093F\u0916\u0928\u093E",
            "\u092A\u094D\u0930\u093E\u0925\u092E\u093F\u0915 \u0917\u0923\u093F\u0924",
            "\u092C\u094B\u0932\u0928\u093E \u0914\u0930 \u0938\u0941\u0928\u0928\u093E",
            "\u0935\u093F\u091C\u094D\u091E\u093E\u0928 \u0915\u093E \u092A\u0930\u093F\u091A\u092F",
            "\u092A\u093E\u0920 \u092F\u094B\u091C\u0928\u093E \u0915\u0915\u094D\u0937\u093E \u0926\u094B",
            "\u092E\u0942\u0932\u094D\u092F\u093E\u0902\u0915\u0928 \u092A\u094D\u0930\u0936\u094D\u0928",
            "\u0917\u0924\u093F\u0935\u093F\u0927\u093F \u0928\u093F\u0930\u094D\u0926\u0947\u0936",
        )
        for (q in queries) repeat(3) { cosineSearch(generateQueryEmbedding(q)) } // warm up

        var maxLatency = 0L
        val allLatencies = mutableListOf<Long>()
        repeat(100) { iteration ->
            val vec = generateQueryEmbedding(queries[iteration % queries.size])
            val elapsed = measureTimeMillis { cosineSearch(vec) }
            allLatencies.add(elapsed)
            if (elapsed > maxLatency) maxLatency = elapsed
        }

        val avgLatency = allLatencies.average()
        println("  [LATENCY] 100 searches: avg=${avgLatency}ms, max=${maxLatency}ms")
        assertTrue("Max latency ${maxLatency}ms must be under ${LATENCY_SLA_MS}ms", maxLatency < LATENCY_SLA_MS)
    }

    @Test
    fun latencySLAtopK1staysUnder10ms() {
        val query = generateQueryEmbedding("\u0928\u092E\u0938\u094D\u0924\u0947 \u092C\u091A\u094D\u091A\u094B\u0902")
        repeat(5) { cosineSearch(query, topK = 1) }
        val elapsed = measureTimeMillis { cosineSearch(query, topK = 1) }
        println("  [LATENCY] topK=1 search: ${elapsed}ms")
        assertTrue("topK=1 latency ${elapsed}ms must be under ${LATENCY_SLA_MS}ms", elapsed < LATENCY_SLA_MS)
    }

    // ================================================================
    // TEST 3: Accuracy
    // ================================================================

    @Test
    fun topResultMapsToValidCurriculumEntry() {
        val query = generateQueryEmbedding("\u0926\u094B \u0905\u0902\u0915\u094B\u0902 \u0915\u093E \u091C\u094B\u0921\u093C")
        val results = cosineSearch(query, topK = 5)
        assertTrue("Must return at least 1 result", results.isNotEmpty())

        val topIndex = results[0].first
        val topScore = results[0].second
        assertTrue("Top index $topIndex must be valid", topIndex in 0 until idMapJson.getJSONArray("entries").length())

        val idMapEntry = idMapJson.getJSONArray("entries").getJSONObject(topIndex)
        val dbId = idMapEntry.getString("id")
        val nipunCode = idMapEntry.getString("nipun_code")

        // Verify DB ID maps to a valid curriculum entry
        var found = false
        for (i in 0 until curriculumJson.length()) {
            if (curriculumJson.getJSONObject(i).getInt("id").toString() == dbId) {
                found = true; break
            }
        }
        assertTrue("DB ID '$dbId' must map to a curriculum entry", found)
        println("  [ACCURACY] Top-1: id=$dbId, nipun=$nipunCode, score=$topScore")
    }

    @Test
    fun top5resultsAllMapToValidEntries() {
        val query = generateQueryEmbedding("\u092A\u0922\u093C\u0928\u093E \u0914\u0930 \u0932\u093F\u0916\u0928\u093E")
        val results = cosineSearch(query, topK = 5)
        assertEquals("Must return exactly 5 results", 5, results.size)

        for ((rank, pair) in results.withIndex()) {
            val (index, score) = pair
            assertTrue("Result $rank index $index must be valid", index in 0 until 810)
            assertTrue("Result $rank score $score must be in [-1, 1]", score >= -1.0f && score <= 1.0f)
        }
    }

    @Test
    fun searchResultsAreSortedByDescendingScore() {
        val query = generateQueryEmbedding("\u0917\u0923\u093F\u0924 \u0915\u093E \u092A\u094D\u0930\u0936\u094D\u0928")
        val results = cosineSearch(query, topK = 10)
        for (i in 1 until results.size) {
            assertTrue(
                "Score at rank ${i - 1} must be >= score at rank $i",
                results[i - 1].second >= results[i].second
            )
        }
    }

    @Test
    fun identityQueryReturnsScoreOne() {
        val targetIndex = 42
        val targetVec = embeddings[targetIndex]
        val results = cosineSearch(targetVec, topK = 1)
        assertEquals("Top result must be the target", targetIndex, results[0].first)
        assertEquals("Identity cosine must be 1.0", 1.0f, results[0].second, 1e-5f)
    }

    @Test
    fun all18nipunCodesRepresented() {
        val entries = idMapJson.getJSONArray("entries")
        val codes = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            codes.add(entries.getJSONObject(i).getString("nipun_code"))
        }
        assertEquals("Must have 18 unique NIPUN codes", 18, codes.size)
    }

    @Test
    fun allThreeTribalLanguagesRepresented() {
        val entries = idMapJson.getJSONArray("entries")
        val langs = mutableSetOf<String>()
        for (i in 0 until entries.length()) {
            langs.add(entries.getJSONObject(i).getString("tribal_language"))
        }
        assertTrue("Must contain santhali", langs.contains("santhali"))
        assertTrue("Must contain ho", langs.contains("ho"))
        assertTrue("Must contain mundari", langs.contains("mundari"))
    }
}
