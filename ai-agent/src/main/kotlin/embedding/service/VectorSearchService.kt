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
        private const val SIMILARITY_THRESHOLD = 0.4f  // Base similarity threshold (lowered for hybrid search with keyword boost)
        private const val MAX_CONTEXT_TOKENS = 2000  // Maximum tokens in context
    }

    /**
     * Search for most similar chunks to the query embedding with hybrid approach
     * Combines vector similarity with keyword matching for better accuracy
     * @param queryEmbedding Embedding vector of the user query
     * @param queryText Original query text for keyword extraction
     * @param topK Number of top results to return
     * @return List of scored chunks sorted by similarity (descending)
     */
    fun searchSimilarChunks(
        queryEmbedding: List<Float>,
        queryText: String = "",
        topK: Int = TOP_K_RESULTS
    ): List<ScoredChunk> {
        try {
            logger.info("Starting vector search, topK=$topK")

            // 1. Load all chunks from database
            val allChunks = getAllChunksFromDatabase()

            if (allChunks.isEmpty()) {
                logger.warn("No chunks found in database")
                return emptyList()
            }

            logger.info("Loaded ${allChunks.size} chunks from database")

            // 2. Extract keywords from query for hybrid search
            val keywords = if (queryText.isNotEmpty()) {
                extractKeywords(queryText)
            } else {
                emptyList()
            }

            if (keywords.isNotEmpty()) {
                logger.info("Extracted ${keywords.size} keywords: $keywords")
            }

            // 3. Calculate cosine similarity for each chunk with keyword boost
            val scoredChunks = allChunks.map { chunk ->
                val vectorSimilarity = cosineSimilarity(queryEmbedding, chunk.embedding)

                // Apply keyword boost if keywords match
                val keywordBoost = if (keywords.isNotEmpty()) {
                    calculateKeywordBoost(chunk.chunkText, keywords)
                } else {
                    1.0f
                }

                // Hybrid score: vector similarity + keyword boost
                val hybridScore = vectorSimilarity * keywordBoost

                ScoredChunk(chunk, hybridScore)
            }

            // 4. Filter by threshold and take topK
            val results = scoredChunks
                .filter { it.score >= SIMILARITY_THRESHOLD }
                .sortedByDescending { it.score }
                .take(topK)

            logger.info("Found ${results.size} relevant chunks (threshold=$SIMILARITY_THRESHOLD)")

            // Log top results with scores for debugging
            if (results.isNotEmpty()) {
                logger.info("Top results:")
                results.forEachIndexed { index, scored ->
                    logger.info("  ${index + 1}. [doc_${scored.chunk.id}] score=${String.format("%.4f", scored.score)} - ${scored.chunk.chunkText.take(100)}...")
                }
            }

            // Also log chunks that were close but didn't make the cut
            val almostMatches = scoredChunks
                .filter { it.score < SIMILARITY_THRESHOLD && it.score >= SIMILARITY_THRESHOLD - 0.1f }
                .sortedByDescending { it.score }
                .take(3)
            if (almostMatches.isNotEmpty()) {
                logger.debug("Almost matched (below threshold):")
                almostMatches.forEach { scored ->
                    logger.debug("  [doc_${scored.chunk.id}] score=${String.format("%.4f", scored.score)} - ${scored.chunk.chunkText.take(100)}...")
                }
            }

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

    /**
     * Extract meaningful keywords from query text
     * Filters out stop words and keeps nouns, names, and important terms
     */
    private fun extractKeywords(queryText: String): List<String> {
        // Russian stop words to filter out
        val stopWords = setOf(
            "в", "на", "и", "с", "по", "о", "к", "от", "из", "для", "у", "за", "под", "над",
            "при", "про", "через", "между", "перед", "после", "без", "до", "около", "вокруг",
            "что", "кто", "где", "когда", "как", "почему", "который", "какой", "чей", "чем",
            "это", "то", "тот", "этот", "весь", "всякий", "каждый", "любой", "другой", "такой",
            "а", "но", "же", "ли", "бы", "ведь", "вот", "только", "уже", "еще", "даже", "ни",
            "не", "нет", "да", "ну", "или", "либо", "тоже", "также", "зато", "однако",
            "быть", "был", "была", "было", "были", "есть", "суть", "может", "можно", "надо",
            "он", "она", "оно", "они", "его", "её", "их", "ему", "ей", "им", "него", "неё",
            "я", "ты", "мы", "вы", "мой", "твой", "наш", "ваш", "свой"
        )

        // Tokenize and filter
        return queryText.lowercase()
            .split(Regex("[\\s,;:.!?—]+"))
            .filter { word ->
                word.length > 2 && // Skip very short words
                        !stopWords.contains(word) &&
                        word.any { it.isLetter() } // Must contain letters
            }
            .distinct()
    }

    /**
     * Calculate keyword boost factor based on keyword matches
     * Returns multiplier: 1.0 (no boost) to 2.0 (max boost)
     */
    private fun calculateKeywordBoost(text: String, keywords: List<String>): Float {
        if (keywords.isEmpty()) return 1.0f

        val textLower = text.lowercase()
        var matchCount = 0

        for (keyword in keywords) {
            if (textLower.contains(keyword)) {
                matchCount++
            }
        }

        // Calculate boost: 1.0 + (0.3 * match percentage)
        // If all keywords match: 1.3x boost
        // If half keywords match: 1.15x boost
        val matchPercentage = matchCount.toFloat() / keywords.size
        val boost = 1.0f + (matchPercentage * 0.5f) // Up to 1.5x boost

        if (matchCount > 0) {
            logger.debug("Keyword boost: $matchCount/${keywords.size} keywords matched, boost=${String.format("%.2f", boost)}")
        }

        return boost
    }
}
