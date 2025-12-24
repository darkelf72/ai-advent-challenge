package embedding.service

import embedding.model.DocumentChunk
import embedding.model.ScoredChunk
import embedding.repository.VectorStoreRepository
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Service for searching similar document chunks using vector embeddings
 * Uses cosine similarity for ranking, with optional cross-encoder reranking
 */
class VectorSearchService(
    private val vectorStoreRepository: VectorStoreRepository,
    private val rerankerService: RerankerService? = null  // Optional reranker
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
        private const val TOP_K_RESULTS = 5  // Number of most relevant chunks
        private const val TOP_K_BEFORE_RERANK = 20  // Fetch more candidates for reranking
        private const val SIMILARITY_THRESHOLD = 0.65f  // Minimum similarity threshold (increased for better precision)
        private const val MAX_CONTEXT_TOKENS = 2000  // Maximum tokens in context
    }

    /**
     * Search for most similar chunks to the query embedding
     * @param queryEmbedding Embedding vector of the user query
     * @param queryText Original query text (required for reranking)
     * @param topK Number of top results to return
     * @param useReranking Enable cross-encoder reranking (default: false, set to true to enable)
     * @return List of scored chunks sorted by similarity (descending)
     */
    suspend fun searchSimilarChunks(
        queryEmbedding: List<Float>,
        queryText: String? = null,
        topK: Int = TOP_K_RESULTS,
        useReranking: Boolean = false  // Set to TRUE to enable reranking, FALSE to use only vector search
    ): List<ScoredChunk> {
        try {
            logger.info("Starting vector search, topK=$topK, reranking=${useReranking}")

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

            // 3. Filter by threshold and get candidates
            val candidates = scoredChunks
                .filter { it.score >= SIMILARITY_THRESHOLD }
                .sortedByDescending { it.score }

            logger.info("Found ${candidates.size} candidates passing threshold ($SIMILARITY_THRESHOLD)")

            // 4. Apply reranking if enabled
            val results = if (useReranking && rerankerService != null && queryText != null) {
                logger.info("🔄 Applying cross-encoder reranking...")

                // Take more candidates for reranking (e.g., top 20)
                val candidatesForReranking = candidates.take(TOP_K_BEFORE_RERANK)

                if (candidatesForReranking.isEmpty()) {
                    logger.warn("No candidates to rerank")
                    emptyList()
                } else {
                    // Apply reranking
                    val rerankedResults = rerankerService.rerank(queryText, candidatesForReranking)

                    // Take final top K
                    rerankedResults.take(topK)
                }
            } else {
                if (useReranking && rerankerService == null) {
                    logger.warn("⚠️ Reranking requested but rerankerService is null. Using vector search only.")
                }
                if (useReranking && queryText == null) {
                    logger.warn("⚠️ Reranking requested but queryText is null. Using vector search only.")
                }

                // Standard vector search without reranking
                candidates.take(topK)
            }

            logger.info("Final results: ${results.size} chunks")

            // Log top results with scores for debugging
            if (results.isNotEmpty()) {
                logger.info("Top results:")
                results.forEachIndexed { index, scored ->
                    logger.info("  ${index + 1}. [doc_${scored.chunk.id}] score=${String.format("%.4f", scored.score)} - ${scored.chunk.chunkText.take(100)}...")
                }
            }

            // Also log chunks that were close but didn't make the cut
//            if (!useReranking) {
//                val almostMatches = scoredChunks
//                    .filter { it.score < SIMILARITY_THRESHOLD && it.score >= SIMILARITY_THRESHOLD - 0.1f }
//                    .sortedByDescending { it.score }
//                    .take(3)
//                if (almostMatches.isNotEmpty()) {
//                    logger.debug("Almost matched (below threshold):")
//                    almostMatches.forEach { scored ->
//                        logger.debug("  [doc_${scored.chunk.id}] score=${String.format("%.4f", scored.score)} - ${scored.chunk.chunkText.take(100)}...")
//                    }
//                }
//            }

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
