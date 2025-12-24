package embedding.rag

import embedding.OllamaClient
import embedding.service.VectorSearchService
import org.slf4j.LoggerFactory

/**
 * Ollama implementation of RAG client
 * Uses Ollama for embeddings and VectorSearchService for similarity search
 */
class OllamaRagClient(
    private val ollamaClient: OllamaClient,
    private val vectorSearchService: VectorSearchService
) : RagClient {
    private val logger = LoggerFactory.getLogger(this::class.java)

    companion object {
//        private const val EMBEDDING_MODEL = "nomic-embed-text"
        private const val EMBEDDING_MODEL = "zylonai/multilingual-e5-large"
    }

    override suspend fun getEmbedding(text: String): List<Float> {
        return try {
            ollamaClient.embedding(EMBEDDING_MODEL, text)
        } catch (e: Exception) {
            logger.error("Failed to get embedding from Ollama", e)
            throw RagException("Failed to get embedding: ${e.message}", e)
        }
    }

    override suspend fun augmentPromptWithContext(userPrompt: String): String {
        try {
            logger.info("Augmenting prompt with RAG context")

            // Check if reranking is enabled
            val useReranking = System.getenv("USE_RERANKING")?.toBoolean() ?: false
            logger.debug("Reranking enabled: $useReranking")

            // 1. Get embedding for user query
            val queryEmbedding = getEmbedding(userPrompt)
            logger.debug("Got query embedding, dimension=${queryEmbedding.size}")

            // 2. Search for similar chunks with optional reranking
            val similarChunks = vectorSearchService.searchSimilarChunks(
                queryEmbedding = queryEmbedding,
                queryText = userPrompt,  // Pass query text for reranking
                useReranking = useReranking  // Enable/disable reranking via env variable
            )

            if (similarChunks.isEmpty()) {
                logger.warn("No similar chunks found, returning original prompt")
                return userPrompt
            }

            logger.info("Found ${similarChunks.size} similar chunks")

            // 3. Build context with token limit
            val context = vectorSearchService.buildContextWithTokenLimit(similarChunks)

            if (context.isBlank()) {
                logger.warn("Context is empty, returning original prompt")
                return userPrompt
            }

            // 4. Build RAG prompt
            val augmentedPrompt = vectorSearchService.buildRagPrompt(userPrompt, context)

            logger.info("Successfully augmented prompt with context (${context.length} chars)")

            return augmentedPrompt
        } catch (e: Exception) {
            logger.error("Failed to augment prompt with context, using original prompt", e)
            // On error, return original prompt to ensure service continues working
            return userPrompt
        }
    }
}

/**
 * Exception thrown when RAG operations fail
 */
class RagException(message: String, cause: Throwable? = null) : Exception(message, cause)
