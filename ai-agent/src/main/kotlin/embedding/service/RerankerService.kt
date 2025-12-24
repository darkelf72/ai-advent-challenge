package embedding.service

import embedding.model.ScoredChunk
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Service for reranking document chunks using cross-encoder models
 * Uses HuggingFace Inference API with BAAI/bge-reranker-v2-m3 (multilingual, supports Russian)
 *
 * Alternative models:
 * - BAAI/bge-reranker-large (better quality, slower)
 * - BAAI/bge-reranker-base (faster, less accurate)
 */
class RerankerService(
    private val apiKey: String? = null,
    private val model: String = "BAAI/bge-reranker-v2-m3"
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    companion object {
        private const val HUGGINGFACE_API_URL = "https://router.huggingface.co/models"
        private const val RERANK_THRESHOLD = 0.5f  // Minimum reranker score to keep a result
    }

    @Serializable
    data class RerankRequest(
        val inputs: RerankInputs
    )

    @Serializable
    data class RerankInputs(
        val source_sentence: String,
        val sentences: List<String>
    )

    @Serializable
    data class RerankResponse(
        val scores: List<Float>? = null,
        val error: String? = null
    )

    /**
     * Rerank candidates using cross-encoder model
     * This provides more accurate relevance scores than just cosine similarity
     *
     * @param query User's query
     * @param candidates Initial candidates from vector search
     * @return Reranked and filtered list of chunks
     */
    suspend fun rerank(query: String, candidates: List<ScoredChunk>): List<ScoredChunk> {
        if (candidates.isEmpty()) {
            logger.warn("No candidates to rerank")
            return emptyList()
        }

        logger.info("Reranking ${candidates.size} candidates using model: $model")

        try {
            // Extract document texts for reranking
            val documentTexts = candidates.map { it.chunk.chunkText }

            // Call HuggingFace API
            val request = RerankRequest(
                inputs = RerankInputs(
                    source_sentence = query,
                    sentences = documentTexts
                )
            )

            val response: RerankResponse = client.post("$HUGGINGFACE_API_URL/$model") {
                contentType(ContentType.Application.Json)

                // Add API key if provided (for higher rate limits)
                apiKey?.let {
                    header("Authorization", "Bearer $it")
                }

                setBody(request)
            }.body()

            // Handle errors
            if (response.error != null) {
                logger.error("Reranker API error: ${response.error}")
                logger.warn("Falling back to original vector search scores")
                return candidates
            }

            val scores = response.scores
            if (scores == null || scores.size != candidates.size) {
                logger.error("Invalid reranker response: expected ${candidates.size} scores, got ${scores?.size}")
                return candidates
            }

            // Create new scored chunks with reranker scores
            val rerankedChunks = candidates.zip(scores).map { (chunk, score) ->
                ScoredChunk(chunk.chunk, score)
            }

            // Filter by threshold and sort
            val filtered = rerankedChunks
                .filter { it.score >= RERANK_THRESHOLD }
                .sortedByDescending { it.score }

            logger.info("Reranking complete: ${filtered.size}/${candidates.size} chunks passed threshold")

            // Log comparison of scores
            if (logger.isDebugEnabled) {
                logger.debug("Score comparison (vector → reranker):")
                candidates.zip(scores).take(5).forEachIndexed { index, (original, rerankScore) ->
                    logger.debug(
                        "  ${index + 1}. ${String.format("%.4f", original.score)} → ${String.format("%.4f", rerankScore)} " +
                                "- ${original.chunk.chunkText.take(80)}..."
                    )
                }
            }

            return filtered

        } catch (e: Exception) {
            logger.error("Error during reranking, falling back to original scores", e)
            return candidates
        }
    }

    /**
     * Alternative reranking using simple lexical matching as fallback
     * This is a lightweight alternative when API is unavailable
     */
    fun rerankWithLexicalMatch(query: String, candidates: List<ScoredChunk>): List<ScoredChunk> {
        logger.info("Using lexical matching reranker (fallback)")

        val queryTokens = tokenize(query)

        return candidates.map { scored ->
            val docTokens = tokenize(scored.chunk.chunkText)
            val matchCount = queryTokens.count { it in docTokens }
            val matchRatio = matchCount.toFloat() / queryTokens.size

            // Combine vector score with lexical match
            val combinedScore = (scored.score * 0.7f) + (matchRatio * 0.3f)

            ScoredChunk(scored.chunk, combinedScore)
        }.sortedByDescending { it.score }
    }

    private fun tokenize(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.length > 2 }  // Filter short words
            .toSet()
    }

    fun close() {
        client.close()
    }
}
