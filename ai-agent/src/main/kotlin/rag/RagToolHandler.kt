package rag

import embedding.OllamaClient
import embedding.model.ScoredChunk
import embedding.service.VectorSearchService
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import org.slf4j.LoggerFactory

/**
 * Handler для RAG tool.
 * Предоставляет tool definition и выполняет поиск по базе знаний.
 */
class RagToolHandler(
    private val ollamaClient: OllamaClient,
    private val vectorSearchService: VectorSearchService
) {
    private val logger = LoggerFactory.getLogger(RagToolHandler::class.java)

    companion object {
        const val TOOL_NAME = "rag_search"
        const val MAX_CHARS_PER_CHUNK = 500
        const val DEFAULT_TOP_K = 5
        const val MAX_TOP_K = 10
        private const val EMBEDDING_MODEL = "nomic-embed-text"
    }

    /**
     * Создает tool definition для RAG поиска
     */
    fun createToolDefinition(): Tool {
        return Tool(
            name = TOOL_NAME,
            description = "Поиск по базе знаний проекта (coding guidelines, architecture patterns, примеры кода, best practices). " +
                    "Используй для получения контекста о стандартах кода, архитектуре проекта и похожих реализациях.",
            inputSchema = ToolSchema(
                buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Поисковый запрос. Примеры: 'coding standards', 'error handling patterns', 'API client architecture', 'security best practices'")
                        }
                        putJsonObject("topK") {
                            put("type", "number")
                            put("description", "Количество результатов для возврата (по умолчанию 5, максимум 10)")
                            put("default", DEFAULT_TOP_K)
                        }
                    }
                    putJsonArray("required") {
                        add("query")
                    }
                }
            )
        )
    }

    /**
     * Выполняет поиск по базе знаний
     *
     * @param query поисковый запрос
     * @param topK количество результатов (ограничено MAX_TOP_K)
     * @return отформатированные результаты поиска
     */
    suspend fun executeSearch(query: String, topK: Int = DEFAULT_TOP_K): String {
        return try {
            val limitedTopK = topK.coerceIn(1, MAX_TOP_K)

            logger.info("Executing RAG search: query='$query', topK=$limitedTopK")

            // Получаем embedding для запроса
            val queryEmbedding = ollamaClient.embedding(EMBEDDING_MODEL, query)
            logger.debug("Got query embedding with dimension: ${queryEmbedding.size}")

            // Выполняем поиск через VectorSearchService
            val results = vectorSearchService.searchSimilarChunks(
                queryEmbedding = queryEmbedding,
                queryText = query,
                topK = limitedTopK,
                useReranking = false
            )

            if (results.isEmpty()) {
                logger.warn("No results found for query: $query")
                return buildString {
                    appendLine("=== RAG Search Results ===")
                    appendLine("Query: \"$query\"")
                    appendLine("Found: 0 chunks")
                    appendLine()
                    appendLine("Нет результатов в базе знаний. Возможно документы еще не загружены.")
                }
            }

            logger.info("Found ${results.size} chunks for query: $query")
            formatResults(results, query)

        } catch (e: Exception) {
            logger.error("Error executing RAG search for query: $query", e)
            "Error performing RAG search: ${e.message}"
        }
    }

    /**
     * Форматирует результаты поиска для LLM
     *
     * @param chunks найденные фрагменты с релевантностью
     * @param query исходный запрос
     * @return отформатированная строка с результатами
     */
    private fun formatResults(chunks: List<ScoredChunk>, query: String): String {
        return buildString {
            appendLine("=== RAG Search Results ===")
            appendLine("Query: \"$query\"")
            appendLine("Found: ${chunks.size} chunks")
            appendLine()

            chunks.forEachIndexed { index, scoredChunk ->
                val chunkNumber = index + 1
                val relevance = String.format("%.2f", scoredChunk.score)

                appendLine("[$chunkNumber] Source: ${scoredChunk.chunk.documentName} | Relevance: $relevance")

                // Ограничиваем размер контента
                val content = if (scoredChunk.chunk.chunkText.length > MAX_CHARS_PER_CHUNK) {
                    scoredChunk.chunk.chunkText.take(MAX_CHARS_PER_CHUNK) + "\n--- (truncated at $MAX_CHARS_PER_CHUNK chars) ---"
                } else {
                    scoredChunk.chunk.chunkText
                }

                appendLine(content)
                appendLine()
            }

            appendLine("--- End of RAG Results ---")
        }
    }
}
