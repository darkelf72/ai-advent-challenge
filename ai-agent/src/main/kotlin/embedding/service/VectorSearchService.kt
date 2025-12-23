package embedding.service

import embedding.model.DocumentChunk
import embedding.model.ScoredChunk
import embedding.repository.VectorStoreRepository
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Service for searching similar document chunks using vector embeddings
 * Uses cosine similarity for ranking
 */
class VectorSearchService(
    private val vectorStoreRepository: VectorStoreRepository
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val TOP_K_RESULTS = 5  // Number of most relevant chunks
        private const val SIMILARITY_THRESHOLD = 0.5f  // Minimum similarity threshold
        private const val MAX_CONTEXT_TOKENS = 2000  // Maximum tokens in context
    }

    /**
     * Search for most similar chunks to the query embedding
     * @param queryEmbedding Embedding vector of the user query
     * @param topK Number of top results to return
     * @return List of scored chunks sorted by similarity (descending)
     */
    fun searchSimilarChunks(queryEmbedding: List<Float>, topK: Int = TOP_K_RESULTS): List<ScoredChunk> {
        try {
            logger.info("Starting vector search, topK=$topK")

            // 1. Load all chunks from database
            val allChunks = getAllChunksFromDatabase()

            if (allChunks.isEmpty()) {
                logger.warn("No chunks found in database")
                return emptyList()
            }

            logger.info("Loaded ${allChunks.size} chunks from database")

            // 2. Calculate cosine similarity for each chunk
            val scoredChunks = allChunks.map { chunk ->
                val similarity = cosineSimilarity(queryEmbedding, chunk.embedding)
                ScoredChunk(chunk, similarity)
            }

            // 3. Filter by threshold and take topK
            val results = scoredChunks
                .filter { it.score >= SIMILARITY_THRESHOLD }
                .sortedByDescending { it.score }
                .take(topK)

            logger.info("Found ${results.size} relevant chunks (threshold=$SIMILARITY_THRESHOLD)")

            return results
        } catch (e: Exception) {
            logger.error("Error during vector search", e)
            return emptyList()
        }
    }

    /**
     * Build context from scored chunks with token limit
     * Ensures we don't exceed MAX_CONTEXT_TOKENS
     */
    fun buildContextWithTokenLimit(chunks: List<ScoredChunk>): String {
        val selectedChunks = mutableListOf<ScoredChunk>()
        var totalTokens = 0

        for (chunk in chunks) {
            if (totalTokens + chunk.chunk.tokenCount > MAX_CONTEXT_TOKENS) {
                logger.debug("Reached token limit, selected ${selectedChunks.size} chunks")
                break
            }
            selectedChunks.add(chunk)
            totalTokens += chunk.chunk.tokenCount
        }

        logger.info("Building context from ${selectedChunks.size} chunks, totalTokens=$totalTokens")

        return formatContext(selectedChunks)
    }

    /**
     * Format chunks as context block
     * Format: [doc_ID]\nText\n\n[doc_ID]\nText...
     */
    private fun formatContext(scoredChunks: List<ScoredChunk>): String {
        return scoredChunks.joinToString("\n\n") { scored ->
            "[doc_${scored.chunk.id}]\n${scored.chunk.chunkText}"
        }
    }

    /**
     * Build RAG prompt with user question and context
     */
    fun buildRagPrompt(userQuestion: String, context: String): String {
        return """
Ответь на вопрос, используя ТОЛЬКО информацию из раздела "Контекст".
Если ответа нет в контексте — напиши: "В предоставленном контексте нет информации для ответа."

Вопрос:
$userQuestion

Контекст:
$context

Требования к ответу:
- Используй только факты из контекста
- Не добавляй информацию от себя
- В конце ответа добавь раздел "Источники"
- В разделе "Источники" укажи ID использованных фрагментов
        """.trimIndent()
    }

    /**
     * Cosine similarity: cos(θ) = (A·B) / (||A|| × ||B||)
     * Returns value from -1 to 1, where 1 = perfect match
     */
    private fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.size != b.size) {
            logger.error("Vector dimension mismatch: a.size=${a.size}, b.size=${b.size}")
            return 0f
        }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) dotProduct / denominator else 0f
    }

    /**
     * Get all chunks from all documents in database
     */
    private fun getAllChunksFromDatabase(): List<DocumentChunk> {
        val documents = vectorStoreRepository.getAllDocuments()
        return documents.flatMap { doc ->
            vectorStoreRepository.getChunksByDocumentId(doc.id)
        }
    }
}
